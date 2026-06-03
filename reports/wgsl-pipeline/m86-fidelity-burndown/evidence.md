# M86 Fidelity Burn-Down Wave 2

Linear: FOR-102, FOR-164, FOR-165, FOR-166, FOR-167, FOR-168

## Summary

M86 turns the cumulative M66 GM/reference wave into an explicit fidelity burn-down queue. It ranks the next PM-visible candidates, preserves support/refusal rows, classifies visual diffs by root cause, and keeps CPU-oracle rows out of Skia-fidelity accounting.

No global visual threshold was weakened. No renderer visual fix is claimed in this sprint without before/after rendered artifacts.

## Counters

| Counter | Value |
|---|---:|
| Ranked candidates | 19 |
| Support rows | 16 |
| Expected unsupported rows | 3 |
| Classified rows | 7 |
| Skia-comparable support rows | 6 |

## Family Split

| Family | Rows |
|---|---:|
| Bitmap/image sampling | 2 |
| Image filters | 3 |
| Paint/blend/color | 3 |
| Path AA / coverage | 5 |
| Runtime effects | 1 |
| Text/glyphs | 4 |
| Transforms/layers | 1 |

## Reference Kind Split

| referenceKind | Rows |
|---|---:|
| `cpu-oracle` | 7 |
| `skia-upstream` | 6 |
| `test-oracle` | 6 |

## Ranked Candidate Rows

| Row | Family | Status | Reference | CPU | GPU | Root cause |
|---|---|---|---|---:|---:|---|
| `m66-crop-image-filter-nonnull-prepass-skia` | Image filters | `pass` | `skia-upstream` | 84.88% | 98.44% | `filter.bounds-prepass` |
| `m66-image-filter-compose-cf-matrix-transform-oracle` | Image filters | `pass` | `test-oracle` | 100.00% | 100.00% | `none` |
| `m66-aaclip-bounded-grid-skia` | Path AA / coverage | `pass` | `skia-upstream` | 97.00% | 98.83% | `coverage.aa-grid-threshold` |
| `m66-analytic-aa-convex-cpu-oracle` | Path AA / coverage | `pass` | `cpu-oracle` | 100.00% | 100.00% | `none` |
| `m66-clip-rect-difference-skia` | Path AA / coverage | `pass` | `skia-upstream` | 100.00% | 84.44% | `coverage.edge-delta` |
| `m66-path-aa-stroke-primitive-oracle` | Path AA / coverage | `pass` | `test-oracle` | 90.21% | 91.81% | `coverage.stroke-raster-delta` |
| `m66-image-filter-crop-prepass-refusal` | Image filters | `expected-unsupported` | `cpu-oracle` | 0.00% | n/a | `filter.picture-prepass-required` |
| `m66-path-aa-dashing-edge-budget-refusal` | Path AA / coverage | `expected-unsupported` | `cpu-oracle` | 0.00% | n/a | `coverage.edge-count-exceeded` |
| `m66-bitmap-rect-nearest-skia` | Bitmap/image sampling | `pass` | `skia-upstream` | 100.00% | 100.00% | `none` |
| `m66-bitmap-subset-local-matrix-repeat-skia` | Bitmap/image sampling | `pass` | `skia-upstream` | 100.00% | 100.00% | `none` |
| `m66-linear-gradient-kplus-oracle` | Paint/blend/color | `pass` | `test-oracle` | 100.00% | 100.00% | `none` |
| `m66-src-over-alpha-stack-oracle` | Paint/blend/color | `pass` | `test-oracle` | 100.00% | 100.00% | `none` |
| `m66-sweep-gradient-path-clamp-oracle` | Paint/blend/color | `pass` | `test-oracle` | 100.00% | 100.00% | `none` |
| `m66-runtime-effect-simple-descriptor-oracle` | Runtime effects | `pass` | `test-oracle` | 100.00% | 100.00% | `none` |
| `m66-scaled-rects-transform-stack-skia` | Transforms/layers | `pass` | `skia-upstream` | 100.00% | 100.00% | `none` |
| `m66-font-kerning-style-cpu-oracle` | Text/glyphs | `pass` | `cpu-oracle` | 100.00% | 100.00% | `none` |
| `m66-font-latin-outline-cpu-oracle` | Text/glyphs | `pass` | `cpu-oracle` | 100.00% | 100.00% | `none` |
| `m66-font-positioned-glyph-run-cpu-oracle` | Text/glyphs | `pass` | `cpu-oracle` | 100.00% | 100.00% | `none` |
| `m66-font-complex-shaping-refusal` | Text/glyphs | `expected-unsupported` | `cpu-oracle` | 100.00% | n/a | `glyph.complex-shaping-dependency` |

## Remediation Targets

| Row | Why it matters | Next action |
|---|---|---|
| `m66-clip-rect-difference-skia` | GPU passes only under a family-specific 80.0 threshold; keep support but track coverage edge delta before raising fidelity confidence. | Implement or tighten rectangular difference coverage parity, then raise the row threshold with before/after artifacts. |
| `m66-crop-image-filter-nonnull-prepass-skia` | GPU route is close, CPU/reference parity is weak because filter bounds/prepass behavior is still provisional. | Make crop bounds/prepass semantics explicit and regenerate CPU/GPU/reference diffs before counting broader image-filter fidelity. |
| `m66-path-aa-stroke-primitive-oracle` | The row is useful PM evidence but is test-oracle, not Skia-comparable, and still has visible AA/stroke raster deltas. | Attach Skia reference or promote a narrower stroke primitive with higher parity; keep CPU-oracle rows out of Skia fidelity score. |

## Readiness Accounting

Weighted readiness remains **67.75%**.

No new generated support row, Skia-comparable row, runtime capability, or measured gate denominator changed; M86 improves fidelity operability and next-fix selection.

## Validation

```bash
./gradlew --no-daemon pipelineM86FidelityBurndown pipelineSceneDashboardGate pipelinePmBundle
python3 -m json.tool reports/wgsl-pipeline/m86-fidelity-burndown/evidence.json >/dev/null
git diff --check
```
