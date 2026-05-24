# Codec Pure Kotlin Roadmap

Tracking document for the migration of Kanvas codecs toward pure Kotlin
implementations without long-term AWT/ImageIO/JNI dependencies.

GitHub issues are currently disabled on this repository, so this document is the
source of truth for roadmap status. Keep PR numbers next to completed or active
items.

## Goal

- Keep the public `SkCodec` contract stable.
- Keep AWT/ImageIO isolated in `codec-*-imageio` and `codec-all-awt`.
- Make `codec-all-kotlin` complete enough to become the production codec bundle.
- Remove `java.awt`, `javax.imageio`, and `java.desktop` from production codec
  dependency paths.

## Current Status

- [x] Split codec into modular backends: #743
- [x] Add pure Kotlin BMP codec: #778
- [x] Add pure Kotlin PNG codec: #779
- [x] Add pure Kotlin GIF codec: #780
- [x] Add pure Kotlin JPEG grayscale codec: #782
- [x] Add PNG palette support: #783
- [x] Add JPEG color 4:4:4 support: #784
- [x] Improve GIF disposal handling: #785
- [x] Add GIF robustness tests: #787
- [x] Add PNG packed and grayscale support: #788
- [x] Add JPEG subsampling support: #789
- [x] Add WebP metadata codec: #790
- [x] Add JPEG metadata and restart support: #791
- [x] Add ICO Kotlin integration tests: #792
- [x] Add PNG 16-bit and iCCP support: #793
- [x] Add simple VP8L WebP decoding: #794
- [x] Add BMP RLE decoding: #797
- [ ] Parse progressive JPEG metadata: #798
- [ ] Add VP8L normal Huffman parsing: #799
- [ ] Add PNG Adam7 interlace decoding: #800

## Phase 1 - Shared Guardrails

- [ ] Add a CI or Gradle guard that fails if pure Kotlin codec modules depend on
  `java.awt`, `javax.imageio`, or `java.desktop`.
- [ ] Add a shared fixture/helper package for pure Kotlin codec tests.
- [ ] Add comparison tests that run the same fixtures against `codec-all-awt` and
  `codec-all-kotlin`.
- [ ] Add dispatch-order tests for the assembled Kotlin bundle.
- [ ] Add negative fixtures for truncated data, invalid magic, invalid sizes, and
  duplicate or misplaced metadata blocks.

## Phase 2 - PNG

Done:

- [x] Base RGB/RGBA path: #779
- [x] Palette support: #783
- [x] Packed grayscale and palette bit depths: #788
- [x] 16-bit output and iCCP parsing: #793
- [ ] Adam7 interlace: #800

Remaining:

- [ ] Add real ICC profile fixtures and assert parsed `getICCProfile()`.
- [ ] Complete `tRNS` for grayscale and RGB.
- [ ] Add `getPixels` conversions when requested `SkImageInfo.colorType` differs
  from the codec's natural output.
- [ ] Expand Adam7 coverage across color types and bit depths.
- [ ] Add strict chunk-order and duplicate-chunk rejection tests.
- [ ] Decide support level for `gAMA`, `cHRM`, and `sRGB` intent chunks.

## Phase 3 - JPEG

Done:

- [x] Grayscale baseline decode: #782
- [x] Color 4:4:4 decode: #784
- [x] 4:2:2 and 4:2:0 subsampling: #789
- [x] EXIF orientation, APP2 ICC assembly, and restart markers: #791
- [ ] Progressive metadata parsing: #798

Remaining:

- [ ] Decode progressive JPEG pixels.
- [ ] Add pixel-level EXIF orientation tests for all 8 origins.
- [ ] Add real parseable ICC profile fixture.
- [ ] Support CMYK/YCCK.
- [ ] Harden restart marker validation and malformed marker handling.
- [ ] Add `getPixels` conversion coverage.
- [ ] Add real-world lossy tolerance fixtures.

## Phase 4 - GIF

Done:

- [x] GIF parser, LZW, palettes, transparency, frame support: #780
- [x] Disposal handling improvements: #785
- [x] Robustness tests: #787

Remaining:

- [ ] Add real multi-frame fixtures for delay, frame rect, transparency, and
  disposal 0/1/2/3.
- [ ] Add loop-count/Netscape extension coverage if needed by public API.
- [ ] Add more truncated sub-block and malformed extension tests.
- [ ] Evaluate lazy frame decode vs current eager decode for large GIFs.

## Phase 5 - BMP, WBMP, ICO

Done:

- [x] BMP BI_RGB with palettes and 24/32 bpp: #778
- [x] ICO registry integration and embedded PNG/DIB tests: #792
- [x] BMP RLE4/RLE8 decoding: #797

Remaining:

- [ ] Add BMP bitfields support, including 16 bpp 555/565.
- [ ] Add BMP V4/V5 masks and best-effort ICC handling.
- [ ] Add ICO AND mask legacy support.
- [ ] Expand ICO entry selection tests across size, bit depth, PNG, and DIB.
- [ ] Add WBMP overflow and truncated VLQ hardening tests.

## Phase 6 - WebP

Done:

- [x] RIFF/WEBP metadata, VP8X, VP8L, VP8 dimensions: #790
- [x] Simple VP8L literal-only pixel path: #794
- [ ] VP8L normal Huffman parsing: #799

Remaining:

- [ ] VP8L LZ77/copy lengths.
- [ ] VP8L color cache.
- [ ] VP8L transforms: predictor, color transform, subtract green, color
  indexing.
- [ ] VP8L real lossless fixtures.
- [ ] VP8 lossy boolean arithmetic decoder.
- [ ] VP8 lossy macroblocks, prediction, IDCT/WHT, loop filter.
- [ ] VP8/VP8L alpha chunk handling.
- [ ] VP8X ICC/EXIF/XMP extraction.
- [ ] Animated WebP in a separate `codec-animated` follow-up.

## Phase 7 - Android And Animated Wrappers

- [ ] Verify `SkAndroidCodec` against `codec-all-kotlin`.
- [ ] Add downsample, crop, and `ByteBuffer` tests over PNG/JPEG/GIF/BMP Kotlin.
- [ ] Clarify animated ownership between format codecs and `codec-animated`.
- [ ] Add animated GIF contract tests through `SkAnimatedImage`.
- [ ] Add WebP animation only after still WebP pixels are substantially complete.

## Phase 8 - Kotlin Bundle Switch

Switch criteria:

- [ ] `./gradlew :codec-all-kotlin:test :codec-all-kotlin:jar` passes.
- [ ] `./gradlew :cpu-raster:test --tests '*codec*'` passes with
  `codec-all-kotlin`.
- [ ] No production module on the Kotlin codec path depends on `java.awt`,
  `javax.imageio`, or `java.desktop`.
- [ ] AWT/ImageIO remains only in `codec-all-awt`, `codec-*-imageio`, or test and
  tooling code.
- [ ] Existing GM/integration tests pass at the same similarity threshold as the
  AWT-backed path.

## Suggested Next Parallel Batch

- [ ] PNG: real ICC fixtures plus grayscale/RGB `tRNS`.
- [ ] JPEG: first progressive pixel decode slice.
- [ ] WebP: VP8L LZ77/copy lengths.
- [ ] ICO/BMP: ICO AND mask or BMP bitfields.

