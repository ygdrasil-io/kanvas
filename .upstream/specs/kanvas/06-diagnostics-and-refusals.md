# Diagnostics and Refusal Policy

Status: Draft
Date: 2026-07-01

## Purpose

Defines the structured diagnostic system for GPU capability gaps. Refusals are collected during pipeline compilation and returned as part of `RenderResult`. This replaces silent dropping of unsupported features.

## Contracts

### Diagnostic

```kotlin
data class Diagnostic(
    val level: DiagnosticLevel,
    val code: String,
    val operation: String,
    val reason: String,
    val suggestion: String? = null,
    val index: Int = -1,
)
```

- `level`: severity (see below)
- `code`: machine-readable identifier (e.g., `"unsupported_blend"`, `"degraded_stroke"`)
- `operation`: which draw command triggered it (`"DrawRect"`, `"DrawPath"`, ...)
- `reason`: human-readable explanation
- `suggestion`: optional resolution hint (`"Use BlendMode.SRC_OVER instead"`)
- `index`: position in the DisplayList (for debugging)

### DiagnosticLevel

```kotlin
enum class DiagnosticLevel { FATAL, DEGRADE, WARN }
```

| Level | Meaning | Behavior |
|-------|---------|----------|
| `FATAL` | Operation impossible, no rendering | Op skipped, pixel unaffected |
| `DEGRADE` | Rendered with fallback | Op replaced by approximation (e.g., stroke→fill) |
| `WARN` | Rendered but quality loss | Op rendered but degraded (e.g., AA disabled) |

### Diagnostics

```kotlin
class Diagnostics {
    val entries: List<Diagnostic>
    val fatalCount: Int
    val degradeCount: Int
    val warnCount: Int
    val isEmpty: Boolean
    val hasFatal: Boolean

    fun fatal(code: String, operation: String, reason: String, suggestion: String? = null)
    fun degrade(code: String, operation: String, reason: String, suggestion: String? = null)
    fun warn(code: String, operation: String, reason: String, suggestion: String? = null)
    fun summary(): String
}
```

- Accumulator pattern — diagnostics are added during `PipelineCompiler.compile()`
- `summary()` returns: `"Diagnostics: FATAL=N, DEGRADE=N, WARN=N"`

### Error Policy

| Situation | Mechanism |
|-----------|-----------|
| GPU feature not supported | `Diagnostic` DEGRADE — op skipped or approximated |
| Missing code path | `Diagnostic` FATAL — op skipped |
| GPU hardware error (device lost, OOM) | Exception |
| API validation (size=0, null paint) | Exception (pre-render) |
| Quality loss (AA disabled, mipmaps missing) | `Diagnostic` WARN |

## Non-Goals

- Diagnostic severity is not user-configurable — fixed three-level system
- No automatic CPU fallback — refusals are reported, not transparently handled
- No diagnostic suppression API — all refusals are always collected
