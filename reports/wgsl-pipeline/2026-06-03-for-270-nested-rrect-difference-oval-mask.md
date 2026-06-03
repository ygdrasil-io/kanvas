# FOR-270 Nested RRect Difference-Oval Mask

Linear: `FOR-270`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

The bounded correction applies the active analytic clip shape during the
WebGPU blur final composite. The route remains `webgpu.coverage.nested-rrect-clip.expected-unsupported` with fallback
`coverage.nested-clip-visual-parity-below-threshold` because the strict support threshold is not met.

## Before/After

| Measure | FOR-269 baseline | FOR-270 |
|---|---:|---:|
| GPU/reference similarity | 71.22 | 98.48 |
| GPU >32 deltas in `outside_clip_removed_difference_oval` | 246288 | 36 |
| GPU removed-oval share of >32 deltas | 92.912221% | 1.254793% |

## Residual

| Comparison | Dominant >32 delta zone | Share | Max delta |
|---|---|---:|---:|
| GPU/reference | `blurred_content_envelope` | 100.0% | 57 |
| CPU/reference | `blurred_content_envelope` | 100.0% | 237 |

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest
rtk python3 scripts/validate_for270_nested_rrect_difference_oval_mask.py
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/nested-rrect-zone-mask-audit-for270.json`
