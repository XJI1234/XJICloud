package com.xjicloud.xjicloud_mobile

internal data class FusionAuthRequest(
    val authToken: String,
    val schemeCode: String,
    val templateId: String,
    val privacyAccepted: Boolean,
)

internal interface FusionAuthReply {
    fun success(verifyToken: String)
    fun error(code: String)
}

internal interface FusionSdkEvents {
    fun ready()
    fun token(verifyToken: String)
    fun unavailable()
    fun cancelled()
    fun finished()
}

internal interface FusionSdkSession {
    fun initialize(request: FusionAuthRequest, events: FusionSdkEvents)
    fun start()
    fun complete(success: Boolean)
    fun close()
}

internal fun interface FusionTimeout {
    fun schedule(block: () -> Unit): () -> Unit
}

internal class FusionAuthSession(
    private val createSdk: () -> FusionSdkSession,
    private val timeout: FusionTimeout,
) {
    private class Active(
        val epoch: Long,
        val sdk: FusionSdkSession,
        var reply: FusionAuthReply?,
        var started: Boolean = false,
        var tokenDelivered: Boolean = false,
        var cancelTimeout: () -> Unit = {},
    )

    private var epoch = 0L
    private var active: Active? = null
    private var disposed = false

    // All entry points and SDK events are serialized by the bridge's main handler.
    fun authenticate(request: FusionAuthRequest, reply: FusionAuthReply) {
        if (!request.privacyAccepted) { reply.error("privacy_required"); return }
        if (disposed) { reply.error("fusion_unavailable"); return }
        if (active != null) { reply.error("fusion_busy"); return }
        if (request.authToken.isBlank() || request.authToken.length > 32768 ||
            request.schemeCode.isBlank() || request.schemeCode.length > 128 || request.templateId != "100001") {
            reply.error("fusion_unavailable"); return
        }
        val sdk = try { createSdk() } catch (_: Exception) {
            reply.error("fusion_unavailable"); return
        } catch (_: LinkageError) {
            reply.error("fusion_unavailable"); return
        }
        val current = Active(++epoch, sdk, reply)
        active = current
        current.cancelTimeout = timeout.schedule { end(current.epoch, "fusion_unavailable") }
        val events = object : FusionSdkEvents {
            override fun ready() {
                val state = current(current.epoch) ?: return
                if (state.started) return
                state.started = true
                if (!invokeSdk { state.sdk.start() }) end(state.epoch, "fusion_unavailable")
            }

            override fun token(verifyToken: String) {
                val state = current(current.epoch) ?: return
                if (state.tokenDelivered) return
                if (!state.started || verifyToken.isBlank() || verifyToken.length > 32768) {
                    end(state.epoch, "fusion_unavailable"); return
                }
                state.tokenDelivered = true
                val response = state.reply
                state.reply = null
                response?.success(verifyToken)
            }

            override fun unavailable() = end(current.epoch, "fusion_unavailable")
            override fun cancelled() = end(current.epoch, "cancelled")
            override fun finished() {
                val state = current(current.epoch) ?: return
                // The SDK may finish its UI graph immediately after delivering the
                // unified token. Flutter still owns the pending backend verification;
                // keep this epoch busy until complete() or the bounded timeout.
                if (!state.tokenDelivered) end(state.epoch, "fusion_unavailable")
            }
        }
        if (!invokeSdk { sdk.initialize(request, events) }) end(current.epoch, "fusion_unavailable")
    }

    fun complete(success: Boolean) {
        val state = active ?: return
        if (!state.tokenDelivered) { end(state.epoch, "fusion_unavailable"); return }
        // Clear ownership before SDK calls: continue may synchronously emit finish.
        active = null
        state.cancelTimeout()
        try { invokeSdk { state.sdk.complete(success) } }
        finally { invokeSdk { state.sdk.close() } }
    }

    fun cancel() { active?.let { end(it.epoch, "cancelled") } }

    fun dispose() {
        disposed = true
        cancel()
    }

    private fun current(expectedEpoch: Long): Active? = active?.takeIf { it.epoch == expectedEpoch }

    private fun end(expectedEpoch: Long, error: String) {
        val state = current(expectedEpoch) ?: return
        active = null
        state.cancelTimeout()
        val response = state.reply
        state.reply = null
        // Cleanup is best effort after ownership is invalidated; never leak native exception text.
        try { invokeSdk { state.sdk.close() } }
        finally { response?.error(error) }
    }

    private fun invokeSdk(block: () -> Unit): Boolean = try {
        block(); true
    } catch (_: Exception) { false }
    catch (_: LinkageError) { false }
}
