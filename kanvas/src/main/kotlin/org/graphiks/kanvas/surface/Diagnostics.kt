package org.graphiks.kanvas.surface

class Diagnostics {
    private val _entries = mutableListOf<Diagnostic>()
    val entries: List<Diagnostic> get() = _entries.toList()
    val fatalCount: Int get() = _entries.count { it.level == DiagnosticLevel.FATAL }
    val degradeCount: Int get() = _entries.count { it.level == DiagnosticLevel.DEGRADE }
    val warnCount: Int get() = _entries.count { it.level == DiagnosticLevel.WARN }
    val isEmpty: Boolean get() = _entries.isEmpty()
    val hasFatal: Boolean get() = fatalCount > 0

    fun fatal(code: String, operation: String, reason: String, suggestion: String? = null) {
        _entries.add(Diagnostic(DiagnosticLevel.FATAL, code, operation, reason, suggestion, _entries.size))
    }
    fun degrade(code: String, operation: String, reason: String, suggestion: String? = null) {
        _entries.add(Diagnostic(DiagnosticLevel.DEGRADE, code, operation, reason, suggestion, _entries.size))
    }
    fun warn(code: String, operation: String, reason: String, suggestion: String? = null) {
        _entries.add(Diagnostic(DiagnosticLevel.WARN, code, operation, reason, suggestion, _entries.size))
    }
    fun summary(): String = "Diagnostics: FATAL=$fatalCount, DEGRADE=$degradeCount, WARN=$warnCount"
}
