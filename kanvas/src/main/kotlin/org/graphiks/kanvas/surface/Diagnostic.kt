package org.graphiks.kanvas.surface

data class Diagnostic(
    val level: DiagnosticLevel,
    val code: String,
    val operation: String,
    val reason: String,
    val suggestion: String? = null,
    val index: Int = -1,
)
