package com.xjicloud.native3dgs

import android.content.Context
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.native3dgs.sdk.Native3dgsEngine
import org.native3dgs.sdk.EngineException

/** Loaded only after the Android capability check has succeeded. */
internal class SdkEngine(context: Context, scope: CoroutineScope) : EnginePort {
    private val appContext = context.applicationContext
    private val engine = try { Native3dgsEngine(appContext) }
        catch (error: EngineException) { throw EngineFailure(error.code, error) }
    private val leases = mutableMapOf<Long, InputLease>()
    private inline fun <T> sdk(block: () -> T): T = try { block() }
        catch (error: EngineException) { throw EngineFailure(error.code, error) }
    private fun releaseExcept(request: Long? = null) = synchronized(leases) {
        val old = leases.keys.filter { it != request }
        old.forEach { leases.remove(it)?.release() }
    }
    override val state = engine.state.map { s ->
        if (s.phase.name == "Ready" || s.phase.name == "Failed") releaseExcept(s.activeRequestId)
        RenderState(s.phase.name, s.requestId,
        s.activeRequestId, s.sceneCount, s.surfaceGeneration, s.error,
        if (s.error == 0) null else sdkFailureMessage(s.error))
    }.stateIn(scope, SharingStarted.WhileSubscribed(), RenderState())
    override suspend fun open(uri: String): Long {
        val lease = InputLease.acquire(appContext, Uri.parse(uri))
        try {
            val request = sdk { engine.open(lease.uri) }
            synchronized(leases) { leases[request] = lease }
            return request
        } catch (error: Exception) { lease.release(); throw error }
    }
    override fun cancel(requestId: Long) {
        sdk { engine.cancel(requestId) }
        if (engine.state.value.activeRequestId != requestId) synchronized(leases) { leases.remove(requestId)?.release() }
    }
    override fun attach(surface: Any) = sdk { engine.attach(surface as Surface) }
    override fun resize(generation: Long, width: Int, height: Int) = sdk { engine.resize(generation, width, height) }
    override fun detach(generation: Long) = sdk { engine.detach(generation) }
    override fun closeScene() { sdk { engine.closeScene() }; releaseExcept() }
    override suspend fun closeAndWait() { sdk { engine.closeAndWait() }; releaseExcept() }
    override fun camera(command: String, values: List<Double>) {
        require(values.all { it.isFinite() })
        fun value(index: Int) = values.getOrElse(index) { throw IllegalArgumentException("Missing camera value") }
        when (command) {
            "orbit" -> sdk { engine.orbit(value(0), value(1)) }
            "pan" -> sdk { engine.pan(value(0), value(1)) }
            "dolly" -> sdk { engine.dolly(value(0)) }
            "look" -> sdk { engine.look(value(0), value(1)) }
            "fly" -> sdk { engine.fly(value(0), value(1), value(2), value(3)) }
            "fit" -> sdk { engine.fit() }
            "reset" -> sdk { engine.reset() }
            "fixed" -> sdk { engine.fixedMode() }
            "free" -> sdk { engine.freeMode() }
            "flipAxes" -> sdk { engine.flipAxes(value(0) != 0.0, value(1) != 0.0, value(2) != 0.0) }
            else -> throw IllegalArgumentException("Unknown camera command")
        }
    }
}
