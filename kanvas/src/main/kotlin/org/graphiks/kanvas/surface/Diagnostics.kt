package org.graphiks.kanvas.surface

/**
 * Mutable collector for [Diagnostic] entries produced during rendering.
 *
 * Tracks counts per severity level and exposes an immutable snapshot of all entries.
 * Use [fatal], [degrade], and [warn] to record issues; call [summary] for a
 * one-line overview.
 */
class Diagnostics {
    private val _entries = mutableListOf<Diagnostic>()

    /** Immutable snapshot of all recorded diagnostics, in insertion order. */
    val entries: List<Diagnostic> get() = _entries.toList()

    /** Number of [DiagnosticLevel.FATAL] entries. */
    val fatalCount: Int get() = _entries.count { it.level == DiagnosticLevel.FATAL }

    /** Number of [DiagnosticLevel.DEGRADE] entries. */
    val degradeCount: Int get() = _entries.count { it.level == DiagnosticLevel.DEGRADE }

    /** Number of [DiagnosticLevel.WARN] entries. */
    val warnCount: Int get() = _entries.count { it.level == DiagnosticLevel.WARN }

    /** True when no diagnostics have been recorded. */
    val isEmpty: Boolean get() = _entries.isEmpty()

    /** True when at least one [DiagnosticLevel.FATAL] entry exists. */
    val hasFatal: Boolean get() = fatalCount > 0

    /**
     * Record a fatal diagnostic.
     * @see Diagnostic
     */
    fun fatal(code: String, operation: String, reason: String, suggestion: String? = null) {
        _entries.add(Diagnostic(DiagnosticLevel.FATAL, code, operation, reason, suggestion, _entries.size))
    }

    /**
     * Record a degrade diagnostic.
     * @see Diagnostic
     */
    fun degrade(code: String, operation: String, reason: String, suggestion: String? = null) {
        _entries.add(Diagnostic(DiagnosticLevel.DEGRADE, code, operation, reason, suggestion, _entries.size))
    }

    /**
     * Record a warning diagnostic.
     * @see Diagnostic
     */
    fun warn(code: String, operation: String, reason: String, suggestion: String? = null) {
        _entries.add(Diagnostic(DiagnosticLevel.WARN, code, operation, reason, suggestion, _entries.size))
    }

    /**
     * One-line count summary of all recorded diagnostics.
     * @return a string such as "Diagnostics: FATAL=0, DEGRADE=2, WARN=1"
     */
    fun summary(): String = "Diagnostics: FATAL=$fatalCount, DEGRADE=$degradeCount, WARN=$warnCount"
}
