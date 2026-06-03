# FOR-292 M60 Source Payload Derivation Audit

Linear: `FOR-292`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `RECONSTRUCTED_PAYLOAD_DRAW_MAPPING_MISMATCH`

Exact gap: `none`

## Result

FOR-292 traces the derivation chain behind the FOR-286/FOR-288 red
payload reconstruction and compares it to the FOR-290/FOR-291 runtime root
chain. The first demonstrated divergence is draw mapping: the
reconstruction maps the 59 pixels to the red blurred `drawRRect` A8
`SrcIn` payload path, while the comparable runtime root dispatch is the
white `drawRect` path using `kSrc`.

This keeps the FOR-291 result intact: the root runtime trace still has 0
red writes and 59 white `kSrc` dispatch payloads. The blend-mode and
mask-filter branch mismatches are consequences of comparing two different
draw paths, not the earliest demonstrated cause.

| Measure | Value |
|---|---:|
| Target pixels | 59 |
| Coordinate matches | 59 |
| Coverage matches | 59 |
| Reconstructed red SrcIn payload pixels | 59 |
| Reconstructed kSrcOver pixels | 59 |
| Reconstructed mask-filter payload pixels | 59 |
| Runtime white kSrc payload pixels | 59 |
| Runtime root dispatch pixels | 59 |
| Runtime temporary events | 0 |
| Draw mapping mismatches | 59 |
| Layer source mismatches | 0 |
| Blend mode mismatches | 59 |
| Mask-filter branch mismatches | 59 |
| Source payload mismatches | 59 |
| Reconstructed alpha min | 6 |
| Reconstructed alpha max | 251 |

## Reconstructed Chain

- BlurredClippedCircleGM draws the red oval with maskFilter=SkBlurMaskFilter.Make.
- The paint has colorFilter=Blend(SK_ColorRED,kSrcIn) and default kSrcOver blend mode.
- SkBitmapDevice.drawPathWithMaskFilter enters the A8 srcInPayload branch.
- maskedA is derived from the blurred mask alpha and paint alpha.
- applyColorFilter turns the masked source into a red RGBA payload.
- dispatchBlend feeds blend.kSrcOver; active-AA coverage is 255 for the 59 pixels.
- blend.kSrcOver.partialSrc.setPixel(out) reconstructs a red-tinted write.

## Runtime Root Chain

- The root trace sees SkBitmap.eraseColor for each target pixel.
- The comparable root dispatch source is SkBitmapDevice.dispatchBlend.
- The runtime dispatch payload is white RGBA with mode kSrc for 59/59 pixels.
- The branch is blend.kSrc.setPixel(out), with no red root write and no temporary event.

## Anchor Pixel

| Field | Reconstructed payload chain | Runtime root chain |
|---|---|---|
| Pixel | `99,89` | `99,89` |
| Mapped draw | `BlurredClippedCircleGM.c.drawRRect(rr, paint)` | `BlurredClippedCircleGM.c.drawRect(clipRect1, whitePaint)` |
| Source | `[255, 0, 0, 250]` | `[255, 255, 255, 255]` |
| Mode | `SkBlendMode.kSrcOver` | `kSrc` |
| Coverage | 255 | 255 |
| Branch | `SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)` | `SkBitmapDevice.blend.kSrc.setPixel(out)` |
| Written | `[255, 5, 5, 255]` | `[255, 255, 255, 255]` |

## Runtime And Reconstruction Counts

| Runtime dispatch mode | Pixels |
|---|---:|
| `kSrc` | 59 |

| Reconstructed branch | Pixels |
|---|---:|
| `SkBitmapDevice.blend.kSrcOver.partialSrc.setPixel(out)` | 59 |

## Preserved Decisions

- FOR-288 classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`
- FOR-289 decision: `PROOF_INSUFFICIENT_RUNTIME_WRITE_CHRONOLOGY_GAP`
- FOR-290 decision: `NO_RUNTIME_RED_ROOT_STORE_FOUND`
- FOR-291 decision: `RECONSTRUCTION_SOURCE_PAYLOAD_MISMATCH`

M60 remains `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop fallback remains `image-filter.crop-input-nonnull-prepass-required`. No
production renderer, threshold, GPU/WebGPU, fallback, blend, or setPixel
behavior changed.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-source-payload-derivation-audit-for292.json`
