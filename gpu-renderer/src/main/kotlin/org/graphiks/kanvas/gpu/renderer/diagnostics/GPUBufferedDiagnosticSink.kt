package org.graphiks.kanvas.gpu.renderer.diagnostics

/** Concrete diagnostic sink that buffers diagnostics in-memory for tests and PM evidence collection. */
class GPUBufferedDiagnosticSink : GPUDiagnosticSink {
    private val _diagnostics = mutableListOf<GPUDiagnostic>()

    val diagnostics: List<GPUDiagnostic> get() = _diagnostics.toList()

    val hasTerminal: Boolean get() = _diagnostics.any { it.isTerminal }

    override fun emit(diagnostic: GPUDiagnostic) {
        _diagnostics.add(diagnostic)
    }

    /** Produces a deterministic diagnostic dump for PM evidence or test assertions. */
    fun dump(): GPUDiagnosticDump = GPUDiagnosticDump(
        schemaVersion = 1,
        diagnostics = _diagnostics.toList(),
    )

    /** Clears all buffered diagnostics, typically between frames or passes. */
    fun reset() {
        _diagnostics.clear()
    }
}
