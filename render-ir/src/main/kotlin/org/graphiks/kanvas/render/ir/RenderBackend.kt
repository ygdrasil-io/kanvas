package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.color.ColorSpace

/** Backend-neutral planning and submission port. */
public interface RenderBackend<P : Any> {
    public fun plan(scene: SceneSnapshot, target: RenderTargetDescriptor): RenderPlanResult<P>

    public fun submit(plan: P): RenderSubmission
}

/** Logical output target; a backend resolves it to its own surface or image. */
public data class RenderTargetDescriptor(
    public val extent: SceneExtent,
    public val colorSpace: ColorSpace,
    public val label: String? = null,
) : CanonicalValue {
    init { require(label == null || label.isNotBlank()) { "RenderTargetDescriptor.label must not be blank" } }
    override val canonicalId: CanonicalId = canonicalId(
        "render-target-v1", extent.canonicalId.value, colorSpace.name,
        colorSpace.transferFunction.name, colorSpace.gamut.name, label.orEmpty(),
    )
}

/** A stable public category for a renderer diagnostic. */
@JvmInline
public value class RenderDiagnosticCode(public val value: String) {
    init { require(value.isNotBlank()) { "RenderDiagnosticCode.value must not be blank" } }
}

public enum class RenderDiagnosticDomain { SCENE, TARGET, RESOURCE, CAPABILITY, EXECUTION }

public enum class RenderDiagnosticSeverity { INFO, WARNING, ERROR }

/** Public diagnostic that intentionally carries no backend exception or native type. */
public data class RenderDiagnostic(
    public val code: RenderDiagnosticCode,
    public val domain: RenderDiagnosticDomain,
    public val severity: RenderDiagnosticSeverity,
    public val message: String,
) {
    init { require(message.isNotBlank()) { "RenderDiagnostic.message must not be blank" } }
}

/** Result of semantic planning before a backend has allocated or submitted anything. */
public sealed interface RenderPlanResult<out P : Any> {
    public data class Ready<P : Any>(public val plan: P) : RenderPlanResult<P>

    public class GapNotMigrated(diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing> {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }

    public class GapOnPromotedScope(diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing> {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }

    public class InvalidScene(diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing> {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }

    public class ResourceLimitExceeded(diagnostics: List<RenderDiagnostic>) : RenderPlanResult<Nothing> {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }
}

/** Stable public submission identity. */
@JvmInline
public value class SubmissionId(public val value: Long) {
    init { require(value > 0L) { "SubmissionId.value must be positive" } }
}

/** A submitted backend plan that can be awaited without exposing backend internals. */
public interface RenderSubmission {
    public val id: SubmissionId

    public suspend fun await(): RenderExecutionResult
}

/** Terminal public execution outcomes. */
public sealed interface RenderExecutionResult {
    public data object Completed : RenderExecutionResult

    public class UnsupportedCapability(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }

    public class InvalidPlan(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }

    public class ResourceLimitExceeded(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }

    public class DeviceFailure(diagnostics: List<RenderDiagnostic>) : RenderExecutionResult {
        public val diagnostics: List<RenderDiagnostic> = diagnostics.snapshotDiagnostics()
    }
}

private fun List<RenderDiagnostic>.snapshotDiagnostics(): List<RenderDiagnostic> {
    require(isNotEmpty()) { "A failed render result must include at least one diagnostic" }
    return toList()
}
