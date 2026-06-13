package org.graphiks.kanvas.gpu.renderer.diagnostics

/** Canonical lowercase diagnostic reason code. */
@JvmInline
value class GPUDiagnosticCode(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUDiagnosticCode.value must not be blank" }
    }
}

/** Diagnostic domain segment such as material, resource, or wgsl. */
enum class GPUDiagnosticDomain {
    /** Generic diagnostics domain. */
    Diagnostics,
    /** Telemetry and evidence observations. */
    Telemetry,
    /** Capability and adapter/device facts. */
    Capabilities,
    /** Captured target and render state facts. */
    State,
    /** Color-management facts. */
    Color,
    /** Coordinate, transform, and bounds facts. */
    Coordinates,
    /** Command normalization and capture facts. */
    Commands,
    /** Material-source and paint-pipeline facts. */
    Materials,
    /** Runtime-effect registry and descriptor facts. */
    RuntimeEffects,
    /** Geometry, path, coverage, and atlas facts. */
    Geometry,
    /** Vertices and mesh-like facts. */
    Vertices,
    /** Clip stack and clip execution facts. */
    Clips,
    /** Destination-read facts. */
    Destination,
    /** Layer planning facts. */
    Layers,
    /** Filter graph facts. */
    Filters,
    /** Image decode/upload facts. */
    Images,
    /** Text and glyph artifact facts. */
    Text,
    /** WGSL module, binding, and reflection facts. */
    WGSL,
    /** Payload gathering and packing facts. */
    Payloads,
    /** Pipeline key and creation facts. */
    Pipelines,
    /** Routing decisions and refusal facts. */
    Routing,
    /** Draw analysis facts. */
    Analysis,
    /** Recording and task-list facts. */
    Recording,
    /** Pass construction facts. */
    Passes,
    /** Resource materialization facts. */
    Resources,
    /** Execution and submission facts. */
    Execution,
    /** Validation and evidence facts. */
    Validation,
}

/** Severity assigned to diagnostics for reporting and gates. */
enum class GPUDiagnosticSeverity {
    /** Informational evidence that does not affect routing. */
    Info,
    /** Recoverable issue that keeps the route legal. */
    Warning,
    /** Route-blocking issue that should produce a visible refusal. */
    Error,
    /** Terminal issue that invalidates the current renderer operation. */
    Fatal,
}

/** Structured diagnostic emitted by GPU renderer planning or execution. */
data class GPUDiagnostic(
    val code: GPUDiagnosticCode,
    val domain: GPUDiagnosticDomain,
    val severity: GPUDiagnosticSeverity,
    val message: String,
    val facts: Map<String, String> = emptyMap(),
    val isTerminal: Boolean = severity == GPUDiagnosticSeverity.Error || severity == GPUDiagnosticSeverity.Fatal,
    val isRetryable: Boolean = false,
)

/** Sink that collects diagnostics without owning route decisions. */
interface GPUDiagnosticSink {
    /** Emits one diagnostic to the owning collector. */
    fun emit(diagnostic: GPUDiagnostic): Unit = TODO("Wire GPUDiagnosticSink to recorder, validation, or PM evidence collection")
}

/** Deterministic diagnostic dump used by tests and PM evidence. */
data class GPUDiagnosticDump(
    val schemaVersion: Int,
    val diagnostics: List<GPUDiagnostic>,
    val aliasesApplied: List<String> = emptyList(),
    val summaryFacts: Map<String, String> = emptyMap(),
)
