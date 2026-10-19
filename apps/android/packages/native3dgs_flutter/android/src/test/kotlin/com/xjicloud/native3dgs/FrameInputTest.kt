package com.xjicloud.native3dgs

import org.junit.Assert.*
import org.junit.Test

class FrameInputTest {
    @Test fun motionDeltasAreMergedAndDeliveredOnlyOncePerFrame() {
        var scheduled = 0
        val delivered = mutableListOf<Pair<String, List<Double>>>()
        val input = FrameInput({ scheduled++ }, {}, { command, values -> delivered.add(command to values) })
        input.add("orbit", listOf(2.0, 3.0))
        input.add("orbit", listOf(4.0, -1.0))
        input.add("dolly", listOf(0.25))
        assertTrue(delivered.isEmpty())
        assertEquals(1, scheduled)
        input.flush()
        assertEquals(listOf("orbit" to listOf(6.0, 2.0), "dolly" to listOf(0.25)), delivered)
    }
    @Test fun pauseClearsPendingFrameAndCannotDeliverStaleInput() {
        var cancelled = 0
        val delivered = mutableListOf<String>()
        val input = FrameInput({}, { cancelled++ }, { command, _ -> delivered.add(command) })
        input.add("pan", listOf(2.0, 3.0))
        input.clear(); input.clear(); input.flush()
        assertEquals(1, cancelled)
        assertTrue(delivered.isEmpty())
    }
}
