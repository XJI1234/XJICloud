package com.xjicloud.xjicloud_mobile

import org.junit.Assert.*
import org.junit.Test

class FusionAuthSessionTest {
    private val request = FusionAuthRequest("server-sdk-token", "FA-test", "100001", true)

    private class Reply : FusionAuthReply {
        val tokens = mutableListOf<String>()
        val errors = mutableListOf<String>()
        override fun success(verifyToken: String) { tokens.add(verifyToken) }
        override fun error(code: String) { errors.add(code) }
    }

    private class Sdk : FusionSdkSession {
        lateinit var events: FusionSdkEvents
        var initialized = 0
        var started = 0
        var closed = 0
        val completed = mutableListOf<Boolean>()
        override fun initialize(request: FusionAuthRequest, events: FusionSdkEvents) {
            initialized++
            this.events = events
        }
        override fun start() { started++ }
        override fun complete(success: Boolean) { completed.add(success) }
        override fun close() { closed++ }
    }

    private class Harness {
        val sdks = mutableListOf<Sdk>()
        val timers = mutableListOf<() -> Unit>()
        var cancelledTimers = 0
        val session = FusionAuthSession(
            { Sdk().also(sdks::add) },
            FusionTimeout { task -> timers.add(task); { cancelledTimers++ } },
        )
        val sdk get() = sdks.last()
    }

    @Test fun refusesMissingPrivacyBeforeConstructingSdk() {
        val h = Harness(); val reply = Reply()
        h.session.authenticate(request.copy(privacyAccepted = false), reply)
        assertEquals(listOf("privacy_required"), reply.errors)
        assertTrue(h.sdks.isEmpty())
    }

    @Test fun validatesTokensAndOnlyAllowsLoginTemplateBeforeInitializing() {
        val h = Harness()
        for (bad in listOf(request.copy(authToken = ""), request.copy(schemeCode = ""), request.copy(templateId = "100002"))) {
            val reply = Reply(); h.session.authenticate(bad, reply)
            assertEquals(listOf("fusion_unavailable"), reply.errors)
        }
        assertTrue(h.sdks.isEmpty())
    }

    @Test fun startsOnlyAfterReadyAndNeverReturnsInitializationCredential() {
        val h = Harness(); val reply = Reply()
        h.session.authenticate(request, reply)
        assertEquals(1, h.sdk.initialized); assertEquals(0, h.sdk.started)
        h.sdk.events.ready(); h.sdk.events.ready()
        assertEquals(1, h.sdk.started); assertTrue(reply.tokens.isEmpty())
        h.sdk.events.token("unified-sdk-result")
        assertEquals(listOf("unified-sdk-result"), reply.tokens)
        assertEquals(0, h.sdk.closed)
    }

    @Test fun rejectsConcurrentAuthenticationIncludingWhileBackendVerifies() {
        val h = Harness(); val first = Reply(); val second = Reply(); val third = Reply()
        h.session.authenticate(request, first)
        h.session.authenticate(request, second)
        assertEquals(listOf("fusion_busy"), second.errors)
        h.sdk.events.ready(); h.sdk.events.token("verify-token")
        h.session.authenticate(request, third)
        assertEquals(listOf("fusion_busy"), third.errors)
        assertEquals(1, h.sdks.size)
    }

    @Test fun cancelResolvesPendingExactlyOnceAndLateCallbacksCannotAffectReplacement() {
        val h = Harness(); val first = Reply(); val next = Reply()
        h.session.authenticate(request, first); val old = h.sdk
        h.session.cancel(); h.session.cancel()
        assertEquals(listOf("cancelled"), first.errors); assertEquals(1, old.closed)
        h.session.authenticate(request, next)
        old.events.ready(); old.events.token("late"); old.events.unavailable(); h.timers.first().invoke()
        assertTrue(next.errors.isEmpty()); assertTrue(next.tokens.isEmpty()); assertEquals(0, h.sdk.closed)
        h.sdk.events.ready(); h.sdk.events.token("new")
        assertEquals(listOf("new"), next.tokens)
    }

    @Test fun sdkCancelIsDistinctFromUnavailableAndIsNotLogin() {
        val h = Harness(); val cancelled = Reply()
        h.session.authenticate(request, cancelled); h.sdk.events.cancelled()
        assertEquals(listOf("cancelled"), cancelled.errors); assertEquals(1, h.sdk.closed)
        val unavailable = Reply()
        h.session.authenticate(request, unavailable); h.sdk.events.unavailable()
        assertEquals(listOf("fusion_unavailable"), unavailable.errors); assertTrue(unavailable.tokens.isEmpty())
    }

    @Test fun completePassesRealBackendResultAndReleasesSceneIdempotently() {
        for (pass in listOf(true, false)) {
            val h = Harness(); val reply = Reply()
            h.session.authenticate(request, reply); h.sdk.events.ready(); h.sdk.events.token("unified")
            h.sdk.events.token("duplicate")
            h.session.complete(pass); h.session.complete(!pass)
            assertEquals(listOf(pass), h.sdk.completed); assertEquals(1, h.sdk.closed)
            assertEquals(listOf("unified"), reply.tokens); assertTrue(reply.errors.isEmpty())
            assertEquals(1, h.cancelledTimers)
        }
    }

    @Test fun timeoutAndDisposeRejectPendingAndReleaseSdkWithoutDoubleDelivery() {
        val h = Harness(); val reply = Reply()
        h.session.authenticate(request, reply); h.timers.single().invoke(); h.session.dispose()
        assertEquals(listOf("fusion_unavailable"), reply.errors); assertEquals(1, h.sdk.closed)
        val detached = Reply(); h.session.authenticate(request, detached)
        assertEquals(listOf("fusion_unavailable"), detached.errors); assertEquals(1, h.sdks.size)
    }

    @Test fun destroyWhileWaitingForBackendDoesNotReplyTwice() {
        val h = Harness(); val reply = Reply()
        h.session.authenticate(request, reply); h.sdk.events.ready(); h.sdk.events.token("unified")
        h.session.dispose(); h.sdk.events.token("late"); h.sdk.events.cancelled()
        assertEquals(listOf("unified"), reply.tokens); assertTrue(reply.errors.isEmpty())
        assertEquals(1, h.sdk.closed)
    }

    @Test fun templateFinishAfterTokenKeepsSceneOwnedUntilBackendCompletes() {
        val h = Harness(); val reply = Reply(); val replacement = Reply()
        h.session.authenticate(request, reply); h.sdk.events.ready(); h.sdk.events.token("unified")
        h.sdk.events.finished()
        h.session.authenticate(request, replacement)
        assertEquals(listOf("fusion_busy"), replacement.errors)
        assertEquals(1, h.sdks.size); assertEquals(0, h.sdk.closed)
        assertEquals(listOf("unified"), reply.tokens); assertTrue(reply.errors.isEmpty())
        h.session.complete(true)
        assertEquals(listOf(true), h.sdk.completed); assertEquals(1, h.sdk.closed)
    }

    @Test fun templateFinishWithoutTokenFailsClosedAndReleases() {
        val h = Harness(); val reply = Reply()
        h.session.authenticate(request, reply); h.sdk.events.ready(); h.sdk.events.finished()
        assertEquals(listOf("fusion_unavailable"), reply.errors)
        assertTrue(reply.tokens.isEmpty()); assertEquals(1, h.sdk.closed)
    }

    @Test fun templateFinishWhileWaitingForBackendRemainsBoundedByTimeout() {
        val h = Harness(); val reply = Reply()
        h.session.authenticate(request, reply); h.sdk.events.ready(); h.sdk.events.token("unified")
        h.sdk.events.finished(); h.timers.single().invoke()
        assertEquals(1, h.sdk.closed); assertEquals(listOf("unified"), reply.tokens)
        assertTrue(reply.errors.isEmpty())
        h.session.complete(true)
        assertTrue(h.sdk.completed.isEmpty()); assertEquals(1, h.sdk.closed)
    }

    @Test fun unexpectedEarlyOrEmptyTokenFailsClosed() {
        for (token in listOf("early", "")) {
            val h = Harness(); val reply = Reply()
            h.session.authenticate(request, reply)
            if (token.isEmpty()) h.sdk.events.ready()
            h.sdk.events.token(token)
            assertEquals(listOf("fusion_unavailable"), reply.errors); assertTrue(reply.tokens.isEmpty())
        }
    }

    @Test fun synchronousNativeLoadingFailureAndCleanupFailureAreSanitized() {
        val reply = Reply()
        val session = FusionAuthSession({ object : FusionSdkSession {
            override fun initialize(request: FusionAuthRequest, events: FusionSdkEvents) { throw UnsatisfiedLinkError("sensitive") }
            override fun start() = Unit
            override fun complete(success: Boolean) = Unit
            override fun close() { throw IllegalStateException("sensitive") }
        } }, FusionTimeout { {} })
        session.authenticate(request, reply)
        assertEquals(listOf("fusion_unavailable"), reply.errors)
        session.complete(false); session.cancel(); session.dispose()
    }
}
