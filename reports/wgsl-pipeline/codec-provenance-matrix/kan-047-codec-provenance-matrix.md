# KAN-047 Codec Provenance Matrix

Status: `pass`

KAN-047 records codec provenance without changing renderer code, codec runtime,
thresholds, JNI, ImageIO/AWT usage, or animated scene support.

## Summary

| Metric | Value |
| --- | ---: |
| Scene rows | `6` |
| Deterministic fixture/surface rows | `3` |
| Real codec decode scene rows | `1` |
| Dependency-gated scene rows | `2` |
| Portable codec decode formats | `7` |
| Dependency-gated format rows | `4` |
| Stub codec pass rows | `0` |
| Fixture rows claiming codec decode | `0` |
| Scene rows missing provenance | `0` |

## Scene Provenance

| Scene | Status | Format | Decoder | Origin | Decode result | Reason |
| --- | --- | --- | --- | --- | --- | --- |
| paint.bitmap-rect.nearest.fixture.v1 | pass | raw-rgba8888-fixture | none | in-repo-deterministic-fixture | fixture-no-codec-decode | none |
| bitmap-shader-repeat-tile | pass | raw-rgba8-programmatic-image | none | in-test-programmatic-image | fixture-no-codec-decode | none |
| bitmap-subset-local-matrix-repeat | pass | PNG | codec-png-kotlin | images/color_wheel.png | kSuccess | none |
| d54-skia-gm-image | expected-unsupported | surface-snapshot-with-png-reference-artifact | none | generated-skia-gm-surface-snapshot | generated-surface-no-codec-decode | image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required |
| animated-image-gm-stoplight-webp | dependency-gated | WebP | codec-webp-kotlin | images/stoplight_h.webp | scene-pipeline-disabled | codec.animated-frame-unsupported |
| animated-image-gm-flight-gif | dependency-gated | GIF | codec-gif-kotlin | images/flightAnim.gif | scene-pipeline-disabled | codec.animated-frame-unsupported |

## Codec Formats

| Format | Status | Decoder | Decode result | Reason |
| --- | --- | --- | --- | --- |
| PNG | supported | codec-png-kotlin | kSuccess-for-covered-real-fixtures | none |
| JPEG | supported | codec-jpeg-kotlin | kSuccess-for-covered-real-fixtures | none |
| GIF | supported | codec-gif-kotlin | kSuccess-for-covered-real-fixtures | none |
| BMP | supported | codec-bmp-kotlin | kSuccess-for-covered-real-fixtures | none |
| WBMP | supported | codec-wbmp-kotlin | kSuccess-for-covered-real-fixtures | none |
| ICO / CUR | supported | codec-ico-kotlin | delegates-to-selected-payload-decoder | none |
| WebP | supported | codec-webp-kotlin | kSuccess-or-kUnimplemented-by-covered-encoding | none |
| AVIF | dependency-gated | codec-extended | stub-returns-null | codec.decoder-unavailable |
| JPEG XL | dependency-gated | codec-extended | stub-returns-null | codec.decoder-unavailable |
| RAW | dependency-gated | codec-extended | stub-returns-null | codec.decoder-unavailable |
| video | dependency-gated | codec-extended | throws-STUB.FFMPEG | codec.decoder-unavailable |

## Claim Guards

- No stub codec renders a scene pass.
- Deterministic fixtures stay distinct from real `Codec` decode.
- `bitmap-subset-local-matrix-repeat` cites `codec-png-kotlin`, but its support
  claim remains bounded bitmap sampling, not broad codec or color-managed decode.
- Animated WebP/GIF scene rows remain dependency-gated via
  `codec.animated-frame-unsupported`.
- AVIF, JPEG XL, RAW, and video remain dependency-gated via
  `codec.decoder-unavailable`.

## Non-Claims

- KAN-047 does not add renderer, shader, threshold, codec runtime, JNI, ImageIO/AWT, or animated scene support.
- Fixture-backed bitmap scene passes remain renderer/sampling evidence, not broad codec decode support.
- Real codec decode evidence stays tied to the documented pure Kotlin codec matrix and real-image fixture corpus.
- Extended AVIF, JPEG XL, RAW, and video surfaces remain dependency-gated stubs with codec.decoder-unavailable.
