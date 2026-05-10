# Third-Party Dependencies

Skia vendors its third-party dependencies under `third_party/` rather
than relying on system installations. Each dependency lives in its own
directory with a Skia-authored `BUILD.gn` and `BUILD.bazel` that wraps
the upstream sources (which themselves usually sit in
`third_party/externals/<name>/`, kept in sync via DEPS / Bazel
mirrors). This means a Skia build is hermetic given a checkout: there
is no `apt install libpng-dev` step.

This document is a guided tour of the most relevant deps and where
they show up in the rest of the codebase. The full list is in
`third_party/` itself.

| Dep | Used by | Skia surface |
| --- | --- | --- |
| `zlib` | every encoder/decoder, PDF, SKP | `SkData`, deflate streams |
| `libpng` | PNG codec | [Image Decoders](image-decoders.md) / [Encoders](image-encoders.md) |
| `libjpeg-turbo` | JPEG codec | [Image Decoders](image-decoders.md) |
| `libwebp` | WebP codec (still + animated) | [Image Decoders](image-decoders.md) / [Animated Images](animated-images.md) |
| `libavif` | AVIF codec | [Image Decoders](image-decoders.md), HDR via [HDR & Gainmaps](hdr-and-gainmaps.md) |
| `libgav1` | AV1 frame decoding (AVIF) | underneath `libavif` |
| `libjxl` | JPEG-XL codec | [Image Decoders](image-decoders.md) |
| `wuffs` | safe codec parsers (BMP, GIF, NIE, …) | `SkWuffsCodec`, `convert-to-nia` tool |
| `dng_sdk`, `piex` | RAW (DNG) | `SkRawCodec` |
| `etc1` | ETC1 compressed textures | GPU upload paths |
| `libyuv` | colour conversion fast paths | software pipeline, JPEG/WebP |
| `freetype2` | font outline rasterizer | [Text & Fonts](text-and-fonts.md) — `SkFontHost_FreeType` |
| `harfbuzz` | text shaping | [SkShaper](skshaper.md) |
| `icu`, `icu4x`, `icu_bidi` | Unicode tables, BiDi, segmentation | [SkUnicode](skunicode.md) |
| `libgrapheme` | minimal grapheme/segmentation fallback | [SkUnicode](skunicode.md) ICU-less builds |
| `expat` | XML parsing | SVG, FontConfig |
| `brotli` | brotli decompression | woff2 / web fonts |
| `dawn` | WebGPU implementation | [Graphite Backend](graphite-backend.md) (Dawn target) |
| `d3d12allocator` | D3D12 memory pools | [Direct3D Backend](backend-direct3d.md) |
| `spirv-cross` | SPIR-V → MSL/HLSL/GLSL | shader codegen for Metal / D3D ports |
| `angle2` | OpenGL ES → desktop GL emulation | testing on macOS / Windows |
| `vello` | experimental compute renderer | research backend exploration |
| `imgui` | immediate-mode UI | `tools/viewer` overlays |
| `perfetto` | Android tracing protocol | trace bridge — see [Capture & Debugging](capture-and-debugging.md) |
| `oboe` | low-latency Android audio | Skottie audio playback |
| `highway` | portable SIMD (Google Highway) | scattered SIMD hot paths |
| `delaunator` | Delaunay triangulation | mesh generation in `SkSGScene` and tools |
| `lua` | embedded scripting | optional fiddle / DSL hooks |
| `cpu-features` | CPU feature detection on ARM/x86 | runtime SIMD dispatch |
| `native_app_glue` | Android NDK app glue | sample apps |

The aggregator file `third_party/third_party.gni` and the per-dep
`BUILD.gn` / `BUILD.bazel` files form the integration surface. Skia
builds also tolerate **system** copies of some libs through
`skia_use_system_*` GN args (e.g. `skia_use_system_libpng=true`) —
useful for distros packaging `libskia.so` against their existing
shared libs.

---

## Codecs

The image-codec deps are activated via GN args (`skia_use_libpng_decode`,
`skia_use_libjpeg_turbo_decode`, `skia_use_libwebp_decode`,
`skia_use_libavif`, `skia_use_libjxl_decode`) and surface as concrete
`SkCodec` subclasses behind the public API in
[Image Decoders](image-decoders.md). The encoder counterparts
(`skia_use_libpng_encode`, `_libjpeg_turbo_encode`, `_libwebp_encode`,
`_libjxl_encode`) feed [Image Encoders](image-encoders.md).

`wuffs` is used for **memory-safe** parsers — Skia routes BMP, GIF,
and NIE through Wuffs-generated C code rather than hand-written C++.
This is a hardening measure: a fuzzer hit on Wuffs is bounded by the
Wuffs ABI, not by Skia state.

`libgav1` is the AV1 decoder Skia uses inside `libavif`; `dng_sdk` /
`piex` together implement RAW. `libyuv` provides accelerated
colour-conversion routines that JPEG, WebP, and the codec
post-processing path call into.

## Text stack

`freetype2` is the cross-platform glyph rasterizer (`SkFontHost_FreeType`),
used on Linux, Android, and the headless test path; macOS/iOS use
CoreText and Windows uses DirectWrite (see
[Platform Ports](platform-ports.md)).

`harfbuzz` is the shaper for [SkShaper](skshaper.md). It runs glyph
substitution + positioning given a Unicode buffer, returning a vector
of glyph IDs and offsets.

`icu` provides the Unicode database, BiDi algorithm, line-break
detection, and case mapping used by [SkUnicode](skunicode.md). Skia
also experimentally vendors `icu4x` (the Rust rewrite) and a tiny BiDi
core in `icu_bidi`. `libgrapheme` is a fallback grapheme segmentation
library used in builds compiled without ICU. `brotli` is needed for
WOFF2 web-font decompression.

## GPU / Graphics

`dawn` is the WebGPU implementation behind the Dawn variant of the
[Graphite Backend](graphite-backend.md); it is also what
[CanvasKit](canvaskit.md) targets for browser GPU. `d3d12allocator`
(VMA-style pool allocator from AMD) underpins
[Direct3D Backend](backend-direct3d.md) memory management.
`spirv-cross` translates the SPIR-V emitted by [SkSL](sksl-shading-language.md)
into MSL / HLSL / GLSL for Metal / D3D / compatibility paths.
`angle2` provides GL ES on top of D3D / Metal so the Ganesh GL
backend can be tested on platforms that lack a native GLES driver.
`vello` is an experimental compute-based path renderer that Skia
contributors use as a research target.

`spirv-tools` (referenced from `build_overrides/spirv_tools.gni`) and
`vulkan-headers` / `vulkan-tools` (likewise via override files) are
exposed but not vendored under `third_party/` itself.

## Tooling and runtime support

`imgui` powers the overlays in `tools/viewer`. `perfetto` is the
Android tracing protocol used when the [`SkEventTracer`](capture-and-debugging.md)
implementation is the Perfetto bridge. `oboe` is Google's low-latency
Android audio library used by Skottie when soundtrack playback is
enabled. `highway` is Google Highway (portable SIMD), used in
selected hot paths. `lua` is the embedded scripting runtime used by
the `lua` example fiddles. `expat` parses XML for SVG and FontConfig.

## Vendoring conventions

- Upstream sources live under `third_party/<name>/` (or
  `third_party/externals/<name>/` for those tracked via DEPS).
- Skia owns the build files at the top of each dep directory
  (`BUILD.gn`, `BUILD.bazel`) and any glue (e.g.
  `third_party/freetype2/include/freetype-android/`).
- Bazel `external/` overlays in `bazel/external/` translate the
  upstream Bazel module (when one exists) into Skia's macros.
- Pinned versions are managed in `bazel/deps.json` and
  `buildtools/deps_revisions.gni`; `tools/git-sync-deps` syncs the GN
  side, while Bazel pulls from `MODULE.bazel`.
- `LICENSE` files for each dep are aggregated by automation for
  redistribution compliance.

## See also

- [Build System](build-system.md) — how GN and Bazel both pull these in.
- [Image Decoders](image-decoders.md) / [Image Encoders](image-encoders.md) — primary consumer of the codec deps.
- [Text & Fonts](text-and-fonts.md), [SkShaper](skshaper.md), [SkUnicode](skunicode.md) — consumers of FreeType / HarfBuzz / ICU.
- [Graphite Backend](graphite-backend.md) — Dawn target.
- [Direct3D Backend](backend-direct3d.md) — D3D12 allocator.
