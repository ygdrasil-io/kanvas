---
id: "KFONT-M11-006"
title: "Add `GPUTextSubRunPlan` splitting tests"
status: "proposed"
milestone: "M11"
priority: "P1"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M11-003", "KFONT-M11-004", "KFONT-M11-005"]
legacy_gate: null
---

# KFONT-M11-006 - Add `GPUTextSubRunPlan` splitting tests

## PM Note

Ce ticket sert à livrer "Add `GPUTextSubRunPlan` splitting tests" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M11: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Typed GPU Handoff` slice until "Add `GPUTextSubRunPlan` splitting tests" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add `GPUTextSubRunPlan` splitting tests" within `gpu-api` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `text.gpu.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M11 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM11006Plan(
    val input: DrawTextRunPayload,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM11006Executor {
    fun execute(plan: KFontM11006Plan): GPUTextRoutePlan
    fun refusal(code: String = "text.gpu.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `text.gpu.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `GPU-gated` until all evidence and validation criteria are satisfied.

## Required Evidence

- Typed `DrawTextRun` or GPU route dump.
- No-`Sk*` leakage or unsupported route refusal test.
- GPU/WGSL evidence when a GPU route is promoted.
- Classification remains `GPU-gated` until all evidence is attached.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `text.gpu.*` diagnostic and keep the ticket classified as `GPU-gated`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Add GPUTextSubRunPlan splitting tests`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
