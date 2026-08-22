package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent CPU pixel oracle; it is never a GPU upload or product-render fallback. */
fun interface CpuOracle {
    fun render(width: Int, height: Int): ByteArray
}
