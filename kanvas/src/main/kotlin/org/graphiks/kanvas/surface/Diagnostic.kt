package org.graphiks.kanvas.surface

/**
 * A single diagnostic entry produced during rendering.
 *
 * Each [Diagnostic] captures a specific issue at a given severity [level], along with
 * a machine-readable [code], the [operation] that triggered it, a human-readable
 * [reason], an optional [suggestion] for remediation, and an auto-incrementing
 * [index] reflecting the order in which it was recorded.
 *
 * @see Diagnostics
 */
data class Diagnostic(
    val level: DiagnosticLevel,
    val code: String,
    val operation: String,
    val reason: String,
    val suggestion: String? = null,
    val index: Int = -1,
)
