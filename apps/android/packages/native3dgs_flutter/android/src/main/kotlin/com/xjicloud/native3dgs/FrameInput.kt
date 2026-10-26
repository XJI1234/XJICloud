package com.xjicloud.native3dgs

/** Main-thread input accumulator; Android schedules flush with Choreographer. */
internal class FrameInput(private val post: () -> Unit, private val cancel: () -> Unit,
    private val deliver: (String, List<Double>) -> Unit) {
    private val deltas = linkedMapOf<String, DoubleArray>()
    private var scheduled = false
    fun add(command: String, values: List<Double>) {
        require(command in setOf("orbit", "pan", "look", "dolly"))
        require(values.all { it.isFinite() })
        val accumulated = deltas.getOrPut(command) { DoubleArray(values.size) }
        require(accumulated.size == values.size)
        values.forEachIndexed { index, value -> accumulated[index] += value }
        if (!scheduled) { scheduled = true; post() }
    }
    fun flush() {
        if (!scheduled) return
        scheduled = false
        val frame = deltas.mapValues { it.value.toList() }
        deltas.clear()
        frame.forEach { (command, values) -> deliver(command, values) }
    }
    fun clear() {
        if (scheduled) cancel()
        scheduled = false
        deltas.clear()
    }
}
