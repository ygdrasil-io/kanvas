package org.graphiks.kanvas.surface.gpu

/**
 * Counting probe for native GPU effects.
 *
 * Separately tracks allocations, queue writes, command encodings,
 * submits, and fallbacks so tests can assert zero-side-effect refusals
 * and exact resource accounting.
 */
class NativeEffectProbe {

    data class Counts(
        val allocations: Int = 0,
        val queueWrites: Int = 0,
        val commandEncodings: Int = 0,
        val submits: Int = 0,
        val fallbacks: Int = 0,
        val renderPasses: Int = 0,
        val computePasses: Int = 0,
        val copyOperations: Int = 0,
    ) {
        val isZero: Boolean get() =
            allocations == 0 && queueWrites == 0 && commandEncodings == 0 &&
                submits == 0 && fallbacks == 0 && renderPasses == 0 &&
                computePasses == 0 && copyOperations == 0

        companion object {
            val ZERO = Counts()
        }
    }

    private var _counts = Counts()
    val counts: Counts get() = _counts

    fun recordAllocation() { _counts = _counts.copy(allocations = _counts.allocations + 1) }

    fun recordQueueWrite() { _counts = _counts.copy(queueWrites = _counts.queueWrites + 1) }

    fun recordCommandEncoding() { _counts = _counts.copy(commandEncodings = _counts.commandEncodings + 1) }

    fun recordSubmit() { _counts = _counts.copy(submits = _counts.submits + 1) }

    fun recordFallback(reason: String? = null) { _counts = _counts.copy(fallbacks = _counts.fallbacks + 1) }

    fun recordRenderPass() { _counts = _counts.copy(renderPasses = _counts.renderPasses + 1) }

    fun recordComputePass() { _counts = _counts.copy(computePasses = _counts.computePasses + 1) }

    fun recordCopyOperation() { _counts = _counts.copy(copyOperations = _counts.copyOperations + 1) }

    fun reset() { _counts = Counts() }
}
