# M60 Sprint Report And Readiness Accounting

Date: 2026-06-01
Linear: `FOR-10`
Epic: `FOR-5`
Milestone: M60 Coverage & Path AA Expansion

## Result

M60 is ready to close as evidence work, not as a broad Path AA support
promotion. The sprint added M60 budget diagnostics and two visible generated
Path AA rows, but both new rows are `expected-unsupported` on WebGPU. The
published active-target readiness remains approximately 25%; the support
`pass` count is unchanged.

The sprint preserves the current architecture rules:

- no Ganesh or Graphite port;
- no SkSL compiler, IR, or VM rebuild;
- WebGPU remains the GPU backend;
- broad Path AA, stroke cap/join, nested clip, dash, hairline, complex clip,
  and edge-budget increases remain non-claims until row-specific support
  evidence exists.

## Linear And PR Summary

| Issue | Outcome | PR | Evidence |
|---|---|---|---|
| `FOR-6` M60-1 audit current Path AA budgets and refusal taxonomy | Done. Audited M60 numeric budgets, owner files, tests, gaps, and stable fallback reasons. No support row promoted. | [#1286](https://github.com/ygdrasil-io/kanvas/pull/1286) | `reports/wgsl-pipeline/2026-05-31-m60-path-aa-budget-audit.md` |
| `FOR-7` M60-2 add M60 Path AA budget diagnostics to route evidence | Done. Added/validated budget names and route/refusal diagnostics while preserving `coverage.edge-count-exceeded`. | [#1286](https://github.com/ygdrasil-io/kanvas/pull/1286) | `pipelineSceneDashboardGate`; M60 route diagnostics in generated rows |
| `FOR-8` M60-3 promote one bounded stroke/cap/join Path AA scene | Done as explicit refusal. Added `m60-bounded-stroke-cap-join` with CPU-oracle evidence and stable WebGPU refusal. | [#1288](https://github.com/ygdrasil-io/kanvas/pull/1288) | `reports/wgsl-pipeline/2026-06-01-m60-stroke-cap-join-path-aa-promotion.md`; `reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json` |
| `FOR-9` M60-4 promote one bounded nested clip Path AA scene | Done as explicit refusal. Added `m60-bounded-nested-rrect-clip` with Skia-upstream reference evidence and stable WebGPU refusal. | [#1287](https://github.com/ygdrasil-io/kanvas/pull/1287) | `reports/wgsl-pipeline/2026-06-01-m60-nested-clip-path-aa-promotion.md`; `reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json` |
| `FOR-10` M60-5 sprint report and readiness accounting | Done by this report. README score left unchanged because no verified support denominator changed enough to alter the published score. | Closure PR | This report |

## Dashboard And Gate Counters

Validation source: `build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.md`
from `rtk ./gradlew --no-daemon pipelineM60NestedClipPathAaPromotionPack
pipelineSceneDashboardGate pipelinePmBundle`.

| Counter | Value |
|---|---:|
| Dashboard rows | 63 |
| `pass` rows | 47 |
| `expected-unsupported` rows | 16 |
| `tracked-gap` rows | 0 |
| `fail` rows | 0 |
| Generated evidence rows | 61 |
| Static evidence rows | 2 |
| Adapter-backed rows | 43 |
| Inventory-derived rows | 35 |
| Gate failures | 0 |

M60 changed the dashboard from the M59/M57 closeout shape of 61 rows, 47
`pass`, and 14 `expected-unsupported` to 63 rows, 47 `pass`, and 16
`expected-unsupported`. That is a visibility and refusal-contract improvement,
not a support increase.

The gate allowlist now includes the two M60 fallback reasons:

| Row | Status | Fallback |
|---|---|---|
| `m60-bounded-stroke-cap-join` | `expected-unsupported` | `coverage.stroke-cap-join-selector-diagnostics-unavailable` |
| `m60-bounded-nested-rrect-clip` | `expected-unsupported` | `coverage.nested-clip-visual-parity-below-threshold` |

The M59 performance release gate remains unchanged in the PM bundle:
7 selected rows, 7 pass rows, 14 measured blocking lanes, 0 not-measured rows,
and 0 blocking failures. M60 did not add a new measured performance/cache/frame
gate.

## Readiness Accounting

README currently reports active-target readiness as approximately 25%, based on
the new target denominators rather than manual sprint estimates. M60 does not
justify raising that published value.

| Area | Weight | Before M60 | After M60 | Score decision |
|---|---:|---:|---:|---|
| Rendering feature breadth | 30% | 2/10 feature families | 2/10 feature families | No new family is supported; Path AA received diagnostics and refusal rows inside the existing family. |
| Skia-like fidelity | 20% | 25/100 selected GM/reference rows | 25/100 support/fidelity rows for published scoring | `FOR-9` adds a Skia-referenced refusal and `FOR-8` is CPU-oracle only; neither is a verified support/fidelity pass. |
| Real-time runtime | 20% | 1/10 runtime capabilities | 1/10 runtime capabilities | No frame loop, invalidation, live controls, or runtime telemetry added. |
| Performance and cache readiness | 15% | 7/20 measured gates | 7/20 measured gates | No M60 measured gate added; M59 selected gate remains the latest measured release gate. |
| PM/demo operability | 15% | 7/20 PM/release artifacts | 7/20 PM/release artifacts for published scoring | This sprint report improves reviewability but does not add a new live/hosted/demo-operability capability. |

Published weighted readiness stays approximately 25%. The exact dashboard
support numerator stays 47 `pass` rows, and the two M60 rows are counted only
as expected unsupported evidence.

If a later accounting pass chooses to count every selected refusal row toward a
separate "evidence inventory" numerator, it must name that denominator
explicitly and keep it separate from support readiness. Under the current README
model, M60 is not a score increase.

## Non-Claims

M60 does not claim:

- WebGPU support for stroke cap/join Path AA;
- WebGPU support for nested rrect clip Path AA;
- broad Path AA, broad `aaclip`, dash, hairline, stroke-outline, complex clip,
  inverse clip, shader clip, perspective clip, or large clipped path support;
- any higher WebGPU edge budget;
- Skia-like fidelity progress from the `reference.cpu-oracle` stroke/cap/join
  row;
- new real-time runtime capability;
- new measured performance/cache/frame readiness.

## README Decision

README was not updated. The current active-target section already says the new
target starts at approximately 25% and that the score is denominator-based.
Changing it for M60 would risk implying a support increase from two
`expected-unsupported` rows. The correct PM message is that M60 added visible
refusal evidence and diagnostics while preserving the published score.

## Validation

Commands run:

```text
rtk ./gradlew --no-daemon pipelineM60NestedClipPathAaPromotionPack pipelineSceneDashboardGate pipelinePmBundle
rtk jq '{total:(.scenes|length), statuses:(.scenes|map(.status)|group_by(.)|map({(.[0]):length})|add), m60:(.scenes|map(select(.id|test("m60")))|map({id,status,source,gpuStatus:.gpu.status,fallback:.gpu.route.fallbackReason, generation:.generation.derivationTask}))}' build/reports/wgsl-pipeline-scenes/data/scenes.json
```

Results:

- Gradle validation succeeded.
- `pipelineSceneDashboardGate` reported 63 total rows, 47 `pass`, 16
  `expected-unsupported`, 0 failures.
- `pipelinePmBundle` generated
  `build/reports/wgsl-pipeline-pm-bundle/manifest.json` with the same dashboard
  counters and unchanged M59 performance release-gate counters.
