# Spec 05: BlendPlan, Fallbacks, And Diagnostics

Status: Accepted
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`

## M24 Acceptance Evidence

Accepted on 2026-05-27 for the scope covered by the M24 conformance gate.

Evidence links:

- PR #1142 / `12684fb7259644bb2932e930026c7134177e1964`: `pipelineConformance`.
- PR #1143 / `637e42344a335504bfe8d95b63351dfc40ebd872`: PM convergence report.
- PR #1144 / `2035b455535e35452097154d9b5d0f05eea8a866`: report regeneration fix.

Acceptance is limited to the implemented and tested families named in the
conformance report. Future shader, blend, runtime-effect, or migration families
must add their own evidence before default promotion.


## Purpose

Make backend decisions explicit. A generated pipeline may specialize, use a
compatibility path, or refuse, but it must not silently degrade behavior.

## BlendPlan

`BlendPlan` selects how WebGPU handles a blend mode.

Current plan kinds:

- `FixedFunction`: WebGPU render-pipeline blend state is sufficient.
- `ShaderLayerComposite`: the mode requires shader/layer composition or
  destination sampling.
- `RefuseDiagnostic`: the generated path must not execute the mode.

Direct draw and layer-composite paths can have different allowlists. For
example, a blend mode may be fixed-function for direct drawing but require a
layer composite when used as a saveLayer composition mode.

## Fixed-Function Allowlist

Fixed-function modes are allowlist-only. A mode is included only when tests
prove the WebGPU state matches CPU reference behavior for the relevant input
domain.

An implementation ticket adding a mode must include:

- the WebGPU blend factors/state;
- CPU reference comparison;
- alpha and non-opaque destination fixture;
- generated or handwritten path affected;
- fallback behavior for unsupported contexts.

## Shader/Layer Composite Plan

Modes that need destination color, non-native equations, or multi-pass
composition must route through an explicit shader/layer plan.

The plan must name:

- whether it reads destination from an intermediate texture;
- whether it requires a saveLayer-like pass;
- color-space and alpha domain of the intermediate;
- additional resource lifetime rules;
- fallback/refusal behavior if the resource path is unavailable.

## FallbackPlan

`FallbackPlan` records compatibility at the IR or backend boundary.

Required behavior:

- `CpuShadeRow` names why legacy CPU shader execution is required.
- `HandwrittenGpuCompat` names the shader id and why generated WGSL is not
  selected.
- `ExplicitLayerOrReadbackCompat` names the required layer/readback reason.
- `RefuseDiagnostic` names the stable refusal reason.

Fallback reasons must be stable enough for tests and Linear evidence. Human
wording can improve, but the identifying reason should not change casually.

## Diagnostic Reason Taxonomy

Use stable prefixes in new diagnostics:

| Prefix | Meaning |
|---|---|
| `parser.` | WGSL parser or reflection failure. |
| `pipeline.ir.` | Unsupported semantic IR shape. |
| `cpu.scalar.` | CPU scalar backend refusal. |
| `cpu.vector.` | Vector API unavailable, disabled, or rejected. |
| `gpu.generated.` | Generated WGSL path refusal. |
| `gpu.pipeline-key.` | Invalid or unclassified pipeline key axis. |
| `blend.` | Unsupported or incompatible blend behavior. |
| `runtime-effect.` | Runtime-effect registration or implementation miss. |
| `migration.` | Compatibility route retained or retirement blocked. |

Geometry and coverage diagnostics use the separate taxonomy in
`.upstream/specs/geometry-coverage/05-fallback-diagnostics.md`.

## Required Dumps

Developer-facing dumps should include:

- `KanvasPipelineIR` dump;
- fallback plan;
- selected CPU kernel id or GPU route id;
- generated WGSL module id;
- parser/reflection diagnostics;
- reflected layout dump;
- pipeline key dump;
- blend plan;
- cache telemetry snapshot when relevant;
- migration selected path and retained fallback reason.

PM-facing evidence can summarize these fields, but implementation review needs
the raw diagnostic artifact or test assertion.

Specs 01, 03, and 04 define local dump producers. This section is the canonical
checklist for what an end-to-end review artifact must be able to collect.

## Unsupported Behavior

Unsupported generated paths must prefer correctness over opportunistic GPU
execution.

Allowed outcomes:

- execute through a verified compatibility path;
- use legacy CPU path when the issue explicitly allows it;
- refuse with stable diagnostic.

Forbidden outcomes:

- silently dropping coverage, color filter, color space, or blend behavior;
- changing blend mode to a visually similar mode;
- switching to readback/layer composition without reporting it;
- using a handwritten path while claiming generated coverage.

## Tests

Required tests:

- unit tests for BlendPlan allowlists;
- unsupported blend diagnostic tests;
- fallback dump snapshot tests;
- generated-path refusal tests for at least one unsupported feature;
- migration tests proving retained compatibility is named.

## Acceptance Criteria

- Every fallback branch names a stable reason.
- Unsupported generated paths are asserted in tests.
- Blend modes are never selected by default outside the allowlist.
- Diagnostics map back to Linear acceptance criteria.
