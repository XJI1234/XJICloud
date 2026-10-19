package com.xjicloud.native3dgs

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSessionTest {
    private class FakeEngine : EnginePort {
        override val state = MutableStateFlow(RenderState())
        val cancelled = mutableListOf<Long>()
        val detached = mutableListOf<Long>()
        var suspendedCancelled = false
        var suspendOpen = false
        var closed = false
        var detachFails = false
        var cancelFails = false
        var resultState: RenderState? = null
        override suspend fun open(uri: String): Long {
            if (suspendOpen) try { awaitCancellation() } finally { suspendedCancelled = true }
            resultState?.let { state.value = it }
            return 42
        }
        override fun cancel(requestId: Long) { cancelled.add(requestId); if (cancelFails) error("stale request") }
        override fun attach(surface: Any) = 1L
        override fun resize(generation: Long, width: Int, height: Int) {}
        override fun detach(generation: Long) { detached.add(generation); if (detachFails) error("invalid surface") }
        override fun closeScene() {}
        override fun camera(command: String, values: List<Double>) {}
        override suspend fun closeAndWait() { closed = true }
    }
    @Test fun duplicateSurfaceDestroyedDetachesOnlyOnce() = runTest {
        val engine = FakeEngine()
        val session = EngineSession(engine, this, StandardTestDispatcher(testScheduler))
        session.attach(Any(), 100, 100)
        session.detach(); session.detach()
        assertEquals(listOf(1L), engine.detached)
        session.shutdown(); runCurrent()
    }
    @Test fun cancelSuspendedOpenCancelsCoroutineNotRequestId() = runTest {
        val engine = FakeEngine().apply { suspendOpen = true }
        val session = EngineSession(engine, this, StandardTestDispatcher(testScheduler))
        session.open(1, "content://a"); runCurrent()
        session.cancel(1); runCurrent()
        assertTrue(engine.suspendedCancelled)
        assertTrue(engine.cancelled.isEmpty())
        session.shutdown(); runCurrent()
    }
    @Test fun cancelReturnedRequestUsesNativeIdAndReplacementIsIsolated() = runTest {
        val engine = FakeEngine()
        val session = EngineSession(engine, this, StandardTestDispatcher(testScheduler))
        session.open(1, "content://a"); runCurrent()
        session.open(2, "content://b"); runCurrent()
        assertEquals(listOf(42L), engine.cancelled)
        session.cancel(1)
        assertEquals(listOf(42L), engine.cancelled)
        session.cancel(2)
        assertEquals(listOf(42L, 42L), engine.cancelled)
        session.shutdown(); runCurrent()
        assertTrue(engine.closed)
    }
    @Test fun capabilityRejectsBeforeConstructingEngine() {
        assertFalse(Capability.check(35, listOf("x86_64"), 0x00401000).supported)
        assertFalse(Capability.check(28, listOf("arm64-v8a"), 0x00401000).supported)
        assertFalse(Capability.check(35, listOf("arm64-v8a"), 0x00400000).supported)
        assertTrue(Capability.check(35, listOf("arm64-v8a"), 0x00401000).supported)
        assertFalse(Capability.check(35, listOf("arm64-v8a"), 0x00401000, 16384).supported)
    }
    @Test fun failedSnapshotAtOpenReturnRemainsFailed() = runTest {
        val engine = FakeEngine().apply { resultState = RenderState(phase = "Failed", requestId = 42, error = 12) }
        val session = EngineSession(engine, this, StandardTestDispatcher(testScheduler))
        session.open(1, "content://bad"); runCurrent()
        assertEquals("Failed", session.state.value.render.phase)
        assertEquals(12, session.state.value.render.error)
        session.shutdown(); runCurrent()
    }
    @Test fun detachFailureStillClosesEngine() = runTest {
        val engine = FakeEngine().apply { detachFails = true }
        val session = EngineSession(engine, this, StandardTestDispatcher(testScheduler))
        session.attach(Any(), 100, 100)
        session.shutdown(); runCurrent()
        assertTrue(engine.closed)
        assertEquals("Stopped", session.state.value.render.phase)
    }
    @Test fun staleNativeCancellationDoesNotBlockNewOpenOrShutdown() = runTest {
        val engine = FakeEngine().apply { cancelFails = true }
        val session = EngineSession(engine, this, StandardTestDispatcher(testScheduler))
        session.open(1, "content://a"); runCurrent()
        session.open(2, "content://b"); runCurrent()
        assertEquals(2L, session.state.value.operationId)
        assertEquals("Uploading", session.state.value.render.phase)
        session.shutdown(); runCurrent()
        assertTrue(engine.closed)
    }
    @Test fun rawSdkFailureCodeIsPreservedWithoutExposingItsMessage() = runTest {
        val session = EngineSession(FakeEngine(), this, StandardTestDispatcher(testScheduler))
        session.failure(EngineFailure(5, IllegalStateException("content://private/model")))
        assertEquals(5, session.state.value.render.error)
        assertFalse(session.state.value.render.message!!.contains("content://"))
        session.shutdown(); runCurrent()
    }
    @Test fun explicitCancellationFailureIsReportedToCaller() = runTest {
        val engine = FakeEngine().apply { cancelFails = true }
        val session = EngineSession(engine, this, StandardTestDispatcher(testScheduler))
        session.open(1, "content://a"); runCurrent()
        assertThrows(IllegalStateException::class.java) { session.cancel(1) }
        assertEquals("Failed", session.state.value.render.phase)
        session.shutdown(); runCurrent()
    }
}
