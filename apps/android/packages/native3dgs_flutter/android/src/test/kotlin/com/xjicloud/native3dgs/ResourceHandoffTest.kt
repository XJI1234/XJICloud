package com.xjicloud.native3dgs

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceHandoffTest {
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { pending.addLast(block) }
        fun runPending() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }
    @Test fun cancellationWhileReturningCompletedCopyDeletesTheUnclaimedFile() = runTest {
        val file = File.createTempFile("native3dgs-handoff-", ".bin")
        val io = QueuedDispatcher()
        var callerReceived = false
        try {
            val caller = launch {
                acquireWithHandoff(io, { resource: File -> resource.delete() }) {
                    file.writeText("completed model copy")
                    file
                }
                callerReceived = true
            }
            runCurrent() // Caller suspends; keep its return dispatcher parked.
            io.runPending() // IO finishes, result is queued back to caller.
            assertTrue(file.exists())
            caller.cancel()
            runCurrent()
            assertFalse(callerReceived)
            assertFalse("Completed copy must have an owner even during return dispatch", file.exists())
        } finally { file.delete() }
    }
    @Test fun cancellationAtFinalReadEofAlsoReleasesCopy() = runTest {
        val file = File.createTempFile("native3dgs-eof-", ".bin")
        val io = QueuedDispatcher()
        try {
            val caller = launch {
                acquireWithHandoff(io, { resource: File -> resource.delete() }) {
                    file.writeText("EOF")
                    currentCoroutineContext().cancel() // Final read returned EOF after cancellation.
                    file
                }
            }
            runCurrent(); io.runPending(); runCurrent()
            assertTrue(caller.isCancelled)
            assertFalse(file.exists())
        } finally { file.delete() }
    }
    @Test fun successfulHandoffLeavesReleaseToTheCaller() = runTest {
        val file = File.createTempFile("native3dgs-owned-", ".bin")
        try {
            val result = acquireWithHandoff(StandardTestDispatcher(testScheduler),
                { resource: File -> resource.delete() }) { file }
            assertSame(file, result)
            assertTrue(file.exists())
        } finally { file.delete() }
    }
}
