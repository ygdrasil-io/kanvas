# M60 Path AA Budget Audit

Date: 2026-05-31
Ticket: FOR-6
Milestone: M60 Coverage & Path AA Expansion

## Scope

This is a doc-only audit of current Path AA and coverage guardrails against
the M60 initial budgets in
`.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`.

No support row is promoted by this report. Existing generated evidence and
expected-unsupported rows remain unchanged.

Follow-up implementation in FOR-7 adds selector diagnostics for the missing
M60 budget dimensions. This report remains the audit baseline and records the
reason-code taxonomy used by that implementation.

## Sources Read

- `AGENTS.md`
- `.upstream/target/skia-like-realtime-renderer-target.md`
- `.upstream/specs/skia-like-realtime/README.md`
- `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- FOR-6 scope notes

## Current Owner Map

| Area | Owner files/classes | Existing tests/evidence |
|---|---|---|
| WebGPU coverage route selection and edge budget | `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt`: `WEBGPU_PATH_AA_EDGE_BUDGET`, `WebGpuPathCoverageFacts`, `WebGpuCoveragePlanSelector` | `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelectorTest.kt` edge-overflow, stroke-primitive, mask/atlas selector, clip refusal tests |
| Stable coverage/geometry reason taxonomy | `render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt`: `StandardCoverageReason`, `StandardGeometryReason`, `ClipStackBreadthMatrix` | `render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageContractsTest.kt` asserts reason-code stability and fallback bridging |
| CPU descriptor path metrics | `render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageMigrationHarness.kt`: `PathCoverageFixture`, `CpuDescriptorExecutionMetrics` | `render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageMigrationHarnessTest.kt`; `kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapPathCoverageOracleTest.kt` |
| Current CPU path rasterization, path effects, stroke widths | `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt`, `kanvas-skia/src/main/kotlin/org/skia/foundation/SkPaint.kt`, `kanvas-skia/src/main/kotlin/org/skia/foundation/SkDashPathEffect.kt` | `kanvas-skia/src/test/kotlin/org/skia/foundation/SkStroker*Test.kt`, `kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapPathCoverageOracleTest.kt`, WebGPU GM/cross-backend inventory tests |
| Generated MEP/M57 Path AA evidence | `reports/wgsl-pipeline/scenes/generated/m57-path-aa-clip-micro-promotion.json` and Path AA artifact route/stats JSON files | Preserves `coverage.edge-count-exceeded`, `coverage.stroke-outline-edge-count-exceeded`, and complex clip refusals |

## Budget Mapping

| M60 budget | Current implementation status | Current code/test evidence | Gap vs M60 | Stable fallback reason |
|---|---|---|---|---|
| Path verb count <= 96 verbs | Metrics existed in CPU descriptors; FOR-7 wires a WebGPU selector guard when `pathVerbCount` is available in route facts. | `GeometryCoverageContracts.kt` defines `PathVerbSlice`; `GeometryCoverageMigrationHarness.kt` records `pathVerbCount`; FOR-7 adds `WEBGPU_PATH_AA_VERB_BUDGET`. | Production rows must verify the fact is present before claiming verb-budget support. | `coverage.verb-budget-exceeded`. |
| Coverage edge count <= 256 edges | Implemented for AA WebGPU path selection. `WEBGPU_PATH_AA_EDGE_BUDGET = 256`; if `plan.aa && edgeCount > 256`, the selector refuses unless an explicit stroke primitive or mask/atlas route is enabled. | `WebGpuCoveragePlanSelector.kt`; `WebGpuCoveragePlanSelectorTest.kt` asserts `WEBGPU_PATH_AA_EDGE_BUDGET + 1` refuses with `coverage.edge-count-exceeded`; `m57-path-aa-clip-micro-promotion.json` reports `edgeBudget: 256`; `path-aa-edge-budget-boundary/stats.json` preserves 46 edge-count-exceeded rows / 50 expected-unsupported rows. | Covered as a stable guard, but current generated route details do not provide exact edge counts for every historical GM row. | Existing reason: `coverage.edge-count-exceeded`. |
| Cubic subdivision segments <= 16 per cubic | CPU rasterizers flatten adaptively; FOR-7 adds WebGPU-side `maxCubicSegmentsPerCubic` diagnostics for the direct path lowering route. | `SkBitmapDevice.flattenCubic`; `SkDashPathEffect.flattenCubic`; FOR-7 adds `WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET`. | Needs production evidence for representative cubic rows before support promotion. | `coverage.cubic-segment-budget-exceeded`. |
| Stroke width range 0.5 px to 64 px | No M60 guard found. `SkPaint.strokeWidth` accepts `0f` as hairline and any non-negative value; existing tests and GMs include hairlines, thin strokes, and wide stress cases outside the M60 range. | `SkPaint.strokeWidth`; `SkBitmapDevice.strokeRectAA`; `SkStroker*Test`; WebGPU GM tests mention widths such as 0, 0.25, 0.125, 120, and broader stress cases. | Add WebGPU/Path AA route refusal for widths `< 0.5`, hairline `0`, and widths `> 64` before claiming bounded stroke support. | Proposed new reason: `coverage.stroke-width-budget-exceeded`; keep existing broad stress rows under `coverage.edge-count-exceeded` until stroke-width diagnostics are wired. |
| Dash interval count <= 8 intervals | `SkDashPathEffect.Make` validates even size >= 2 and non-negative intervals; FOR-7 carries direct `SkDashPathEffect` interval count through PathEffect recursion for diagnostics. Compose/sum path effects still need separate provenance before counting nested dash intervals. | `SkDashPathEffect.Make`; WebGPU PathEffect dispatch; `path-aa-dashing-edge-budget` artifacts and M57 preserved rows use `coverage.edge-count-exceeded`. | Do not claim broad dash support from direct interval diagnostics; nested path effects remain follow-up scope. | `coverage.dash-budget-exceeded`. |
| Clip stack depth <= 4 nested clips | Clip lowering exists as summarized `ClipInteraction`; no numeric depth field or `<= 4` guard found. Supported WebGPU cases are device rect and analytic shape clips; arbitrary AA clips and unlowerable stacks refuse. | `ClipInteraction`; `ClipStackBreadthMatrix`; `WebGpuCoveragePlanSelector.unsupportedClipReason`; selector tests assert `coverage.arbitrary-aa-clip-unsupported` and `geometry.clip-stack-unsupported`. | Add retained clip-stack depth metadata before enforcing M60 depth. The current model can refuse shape families, not numeric nesting depth. | Proposed new reason for numeric overflow: `coverage.clip-depth-exceeded`; keep existing `coverage.arbitrary-aa-clip-unsupported` and `geometry.clip-stack-unsupported` for non-depth clip families. |
| Device-space bounds <= 2048 x 2048 px | Bounds are carried on geometry and coverage plans; FOR-7 computes direct path device bounds for Path AA budget diagnostics. Existing 2048 references elsewhere were not Path AA refusal policy. | `GeometryBounds`, `CoveragePlan.AnalyticRect`, `CoveragePlan.AlphaMask`, `CoveragePlan.CoverageAtlas`; FOR-7 adds `WEBGPU_PATH_AA_DEVICE_BOUNDS_BUDGET`. | Inverse fill and clip-expanded semantics need explicit review before counting these diagnostics as broad bounds support. | `coverage.bounds-budget-exceeded`. |

## Taxonomy Findings

The stable coverage taxonomy currently covers:

- `coverage.edge-count-exceeded`
- `coverage.stroke-outline-edge-count-exceeded`
- `coverage.arbitrary-aa-clip-unsupported`
- `coverage.stencil-cover-unavailable`
- `coverage.span-runs-unsupported`
- `coverage.alpha-mask-unsupported`
- `coverage.atlas-policy-unavailable`
- `coverage.glyph-mask-dependency-unavailable`

The stable geometry taxonomy currently covers:

- `geometry.clip-stack-unsupported`
- `geometry.path-effect-unsupported`
- `geometry.stroke-degenerate`
- `geometry.unsupported-perspective`
- `geometry.nonfinite-input`
- `geometry.compute-tessellation-not-enabled`

The M60-specific missing budget reasons are:

- `coverage.verb-budget-exceeded`
- `coverage.cubic-segment-budget-exceeded`
- `coverage.stroke-width-budget-exceeded`
- `coverage.dash-budget-exceeded`
- `coverage.clip-depth-exceeded`
- `coverage.bounds-budget-exceeded`

Only `coverage.edge-count-exceeded` is both specified by M60 and already
implemented as a numeric WebGPU Path AA guard.

## Review Notes

- The existing 256-edge guard must stay intact. Raising it requires
  generated evidence and sprint review.
- The stroke primitive and mask/atlas bypasses in `WebGpuCoveragePlanSelector`
  are explicit route toggles, not broad Path AA support.
- Current M57 generated evidence reports a 90-edge AA clip micro-slice under
  the 256-edge budget and preserves broader Path AA refusals. It does not
  establish the new M60 verb, cubic, stroke-width, dash, clip-depth, or bounds
  budgets.
- No report/index update was required for this doc-only ticket.

## Validation

Doc-only validation expected:

```text
rtk rg -n "WEBGPU_PATH_AA_EDGE_BUDGET|coverage.edge-count-exceeded|PathCoverageFixture|pathVerbCount|segmentCount|SkDashPathEffect|strokeWidth|ClipStackBreadthMatrix" ...
rtk git diff --check
```
