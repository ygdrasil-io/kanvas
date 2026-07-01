package org.graphiks.kanvas.surface

/**
 * Severity level for a [Diagnostic].
 *
 * Ordered from most to least severe:
 * - [FATAL] — the operation cannot proceed.
 * - [DEGRADE] — the operation completed but with reduced quality or performance.
 * - [WARN] — a noteworthy condition that did not affect the result.
 */
enum class DiagnosticLevel { FATAL, DEGRADE, WARN }
