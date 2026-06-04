# FOR-302 M60 Analytic Clip Model Reconciliation

Linear: `FOR-302`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT`

Exact gap/result: `FOR-293 treated the analytic surviving difference-clip coverage as enough for red root visibility. FOR-301 proves that the runtime `SkAAClip` path operand is full over the 59 original targets, so `kDifference` computes zero result coverage there.`

## Result

FOR-302 reconciles FOR-293's analytic model with the FOR-301 runtime
`SkAAClip` trace. The correction is audit-only: FOR-302 uses the traced
runtime path operand and applies `parent * (255 - pathCoverage) / 255` for
`clipRRect(kDifference)`. It does not change normal rendering, `SkAAClip.op`,
CTM handling, blend, `setPixel`, GPU/WebGPU routing, thresholds, or M60
support status.

| Decision | Applied |
|---|---|
| `M60_ANALYTIC_MODEL_CLIP_POLARITY_FIX_APPLIED` | True |
| `M60_ANALYTIC_MODEL_PIXEL_CENTER_FIX_APPLIED` | False |
| `M60_ANALYTIC_MODEL_CTM_OR_BOUNDS_FIX_APPLIED` | False |
| `M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT` | True |
| `M60_ANALYTIC_RUNTIME_CONTRADICTION_STILL_AMBIGUOUS` | False |

## Component Comparison

| Component | Pixels | Bounds | FOR-293 cov>0 | Runtime path cov=255 | Runtime result cov=0 | FOR-293/result mismatches | Runtime formula/result exact |
|---|---:|---|---:|---:|---:|---:|---:|
| `original-59-targets` | 59 | `{'left': 94, 'top': 84, 'right': 105, 'bottom': 94, 'rightInclusive': 104, 'bottomInclusive': 93}` | 59 | 59 | 59 | 59 | 59 |
| `candidate-minus-runtime-002` | 3293 | `{'left': 18, 'top': 18, 'right': 262, 'bottom': 262, 'rightInclusive': 261, 'bottomInclusive': 261}` | 3293 | 3281 | 3281 | 3293 | 3293 |
| `red-runtime-000` | 2275 | `{'left': 158, 'top': 4, 'right': 434, 'bottom': 39, 'rightInclusive': 433, 'bottomInclusive': 38}` | 2275 | 0 | 0 | 224 | 2275 |
| `red-runtime-001` | 2275 | `{'left': 158, 'top': 553, 'right': 434, 'bottom': 588, 'rightInclusive': 433, 'bottomInclusive': 587}` | 2275 | 0 | 0 | 224 | 2275 |
| `red-runtime-002` | 2270 | `{'left': 4, 'top': 158, 'right': 39, 'bottom': 434, 'rightInclusive': 38, 'bottomInclusive': 433}` | 2270 | 0 | 0 | 224 | 2270 |
| `red-runtime-003` | 2268 | `{'left': 553, 'top': 158, 'right': 588, 'bottom': 434, 'rightInclusive': 587, 'bottomInclusive': 433}` | 2268 | 0 | 0 | 224 | 2268 |

## Hypothesis Checks

| Hypothesis | Outcome | Evidence |
|---|---|---|
| Clip polarity / difference alpha merge | audit fix applied | Runtime parent/path/result probes satisfy `parent * (255 - pathCoverage) / 255` on every probed pixel. |
| Pixel center | rejected | Inner-bounds center and top-left variants still keep all 59 original pixels nonzero. |
| CTM or bounds | rejected | FOR-301 reports CTM `scale(2)` and path bounds `{'left': 16, 'top': 16, 'right': 576, 'bottom': 576}`. |
| Rounding | rejected as primary cause | Original targets differ at full coverage: FOR-293 surviving clip is 255, runtime path is 255, runtime result is 0. |
| Runtime contradiction | reconciled | Runtime SkAAClip is internally consistent; the contradiction is in the audit model assumption. |

## Original 59 Variant Control

| Variant | Result zero pixels | Result nonzero pixels | Result full pixels |
|---|---:|---:|---:|
| Inner bounds 4x4 | 0 | 59 | 59 |
| Inner bounds center binary | 0 | 59 | 59 |
| Inner bounds top-left binary | 0 | 59 | 59 |
| Outer draw bounds 4x4 control | 34 | 25 | 17 |

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-analytic-clip-model-reconciliation-for302.json`
