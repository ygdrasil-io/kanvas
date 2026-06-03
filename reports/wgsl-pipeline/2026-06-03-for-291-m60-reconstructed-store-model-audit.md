# FOR-291 M60 Reconstructed Store Model Audit

Linear: `FOR-291`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`

Exact gap: `none`

## Result

FOR-291 audits why the FOR-286/FOR-288 reconstructed model predicts a red
store on the 59 `draw_oval_outer_boundary` pixels while the merged FOR-290
expanded root runtime trace observes no red root write.

The failing reconstruction assumption is that the FOR-286 bounded
`SkBitmapDevice.drawPathWithMaskFilter.A8.srcInPayload` + `kSrcOver`
store model is an actual root write chronology for those pixels. FOR-290
shows the same root coordinates are written by `SkBitmap.eraseColor` and
then by a root `SkBitmapDevice.dispatchBlend` with white `kSrc` payload.
No runtime red root store is observed.

| Measure | Value |
|---|---:|
| Target pixels | 59 |
| Coordinate matches | 59 |
| Reconstructed non-zero mask pixels | 59 |
| Reconstructed full clip coverage pixels | 59 |
| Runtime dispatch coverage matches | 59 |
| Reconstructed red write pixels | 59 |
| Runtime red root write events | 0 |
| Runtime white dispatch write pixels | 59 |
| Source payload mismatches | 59 |
| Blend mode mismatches | 59 |
| Runtime root events | 118 |
| Runtime temporary events | 0 |

## Compared Inputs

| Input | Finding |
|---|---|
| Source blur | FOR-286/FOR-288: 59/59 partial non-zero blur mask alpha on target pixels; FOR-290 root events do not carry blur-mask fields. |
| Mask | FOR-286/FOR-288: 59/59 non-zero mask support on the same target pixels. |
| Coverage / clip | FOR-286/FOR-288: 59/59 full active-AA clip coverage; FOR-290 root dispatch coverage is also 255 for 59/59 pixels. |
| Coordinates | The derived 59 target root coordinates match exactly across FOR-286/FOR-288 and FOR-290. |
| Blend mode | FOR-286 reconstructs kSrcOver; FOR-290 root dispatch observes kSrc for 59/59 pixels. |
| Source payload | FOR-286 reconstructs red SrcIn payload for 59/59 pixels; FOR-290 root dispatch observes white source payload for 59/59 pixels. |
| Device / layer | FOR-290 observed 118/118 events on root and 0 temporary events under its root-dimension trace; FOR-286 has no runtime device/layer evidence for the reconstructed red store. |

## Anchor Pixel

| Field | Reconstructed | Runtime root dispatch |
|---|---|---|
| Pixel | `99,89` | `99,89` |
| Source | `[255, 0, 0, 250]` | `[255, 255, 255, 255]` |
| Mode | `SkBlendMode.kSrcOver` | `kSrc` |
| Coverage | 255 | 255 |
| Written | `[255, 5, 5, 255]` | `[255, 255, 255, 255]` |
| Branch | `SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)` | `SkBitmapDevice.blend.kSrc.setPixel(out)` |

## Runtime Counts

| Event source | Events |
|---|---:|
| `SkBitmap.eraseColor` | 59 |
| `SkBitmapDevice.dispatchBlend` | 59 |

| Runtime dispatch mode | Pixels |
|---|---:|
| `kSrc` | 59 |

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, or setPixel
behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-reconstructed-store-model-audit-for291.json`
