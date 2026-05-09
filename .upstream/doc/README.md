# Skia Upstream — Documentation Index

This directory documents the Skia 2D graphics library as vendored under
[`skia-main/`](../../skia-main/). The upstream is the
[`google/skia`](https://github.com/google/skia) repository (branch `main`),
which Skia uses in lieu of `master`.

The index is organized to mirror the way Skia is layered: foundational types
and the public drawing API at the top, followed by primitive families
(shaders, filters, images, text), then the rendering implementations
(CPU and GPU), shading language, document backends, higher-level modules,
platform integration, and finally tooling and infrastructure.

Every section below points to a dedicated file. Files are stubs that will be
filled in over time; the table-of-contents here is the canonical entry point.

---

## Part I — Foundations

| Topic | Description |
|---|---|
| [Core Architecture](core-architecture.md) | Foundational types: `SkRefCnt`, `SkData`, `SkStream`, `SkSpan`, arena/block allocators, error handling, and the cross-cutting utilities under `src/base/` and `include/private/`. |
| [Canvas & Recording API](canvas-and-recording.md) | `SkCanvas`, `SkPicture`, `SkPictureRecorder`, `SkDrawable`, the no-draw / N-way / overdraw / paint-filter canvas variants, and the recorder abstraction shared by CPU and Graphite. |
| [Geometry & Math](geometry-and-math.md) | Geometric primitives — `SkPoint`, `SkRect`, `SkRRect`, `SkRegion`, `SkPath`, `SkPathBuilder`, `SkMatrix`, `SkM44`, `SkVertices`, `SkRSXform`, plus Bézier / cubic helpers. |
| [Paint, Color & Blending](paint-color-and-blending.md) | `SkPaint` (the configuration object for every draw), `SkColor`/`SkColor4f`, `SkColorFilter`, `SkBlender`, `SkBlendMode`, alpha types and coverage. |
| [Color Management](color-management.md) | `SkColorSpace`, color types, ICC profiles, gamut/transfer-function handling, and how Skia threads color spaces through draws. |
| [Surface & Output Targets](surface-and-output.md) | `SkSurface` and the various places pixels can land — raster, GPU (Ganesh/Graphite), and externally-managed backends. |

## Part II — Drawing Primitives & Effects

| Topic | Description |
|---|---|
| [Shaders](shaders.md) | `SkShader` and its implementations: image, gradient (linear/radial/sweep/conical), Perlin noise, picture, color, blend, runtime, gainmap, and local-matrix wrappers. |
| [Image Filters & Mask Filters](image-filters-and-mask-filters.md) | `SkImageFilters` DAG (blur, drop shadow, displacement, lighting, morphology, etc.) and `SkMaskFilter` (blur, emboss, table, shader-mask). |
| [Path Effects](path-effects.md) | `SkPathEffect` family — dash, corner, discrete, trim, 1D/2D path effects — that mutates path geometry before rasterization. |
| [Runtime Effects](runtime-effects.md) | `SkRuntimeEffect`: user-authored shaders, color filters, and blenders written in SkSL and compiled at runtime. |

## Part III — Images & Pixel Data

| Topic | Description |
|---|---|
| [Bitmap, Pixmap & Image](bitmap-pixmap-image.md) | The pixel-container hierarchy: `SkPixmap` (view), `SkBitmap` (mutable), `SkImage` (immutable), `SkPixelRef`, `SkImageInfo`, plus image generators. |
| [Image Decoders](image-decoders.md) | Built-in codecs in `src/codec/` — JPEG, PNG (libpng + Rust), WebP, GIF, BMP, ICO, WBMP, AVIF, JPEG-XL, RAW — and `SkAndroidCodec`. |
| [Image Encoders](image-encoders.md) | Encoders in `src/encode/` for PNG (libpng + Rust), JPEG, WebP, plus ICC and JPEG-gainmap encoding. |
| [Animated Images](animated-images.md) | `SkAnimatedImage`, frame-holding, animation codec metadata, and YUVA pixmap support. |
| [HDR & Gainmaps](hdr-and-gainmaps.md) | HDR metadata, `SkGainmapShader`, AGTM (Adaptive Gainmap Tone Mapping), and the JPEG-gainmap encoder/decoder pipeline. |

## Part IV — Text & Typography

| Topic | Description |
|---|---|
| [Text & Fonts](text-and-fonts.md) | `SkFont`, `SkTypeface`, `SkFontMgr`, `SkTextBlob`, glyph runs, scaler contexts, and the SFNT helpers in `src/sfnt/`. |
| [SkParagraph](skparagraph.md) | Module providing paragraph layout (line breaking, BiDi, styles) on top of SkShaper. |
| [SkShaper](skshaper.md) | Text-shaping front-end wrapping HarfBuzz / CoreText / system shapers. |
| [SkUnicode](skunicode.md) | Unicode services (script, BiDi, line breaking) with ICU and ICU4X backends. |

## Part V — Paths & CPU Rendering

| Topic | Description |
|---|---|
| [Path Operations](path-operations.md) | Boolean path operations and helpers in `src/pathops/` and `modules/pathops/` — intersection math, coincidence, segments, simplify. |
| [CPU Rendering Pipeline](cpu-rendering-pipeline.md) | Raster pipeline (`SkRasterPipeline`), blitters, scan conversion, anti-aliasing (`SkAAClip`, analytic edges), dithering, and the SIMD `src/opts/` kernels. |

## Part VI — GPU Rendering

| Topic | Description |
|---|---|
| [GPU Overview](gpu-overview.md) | Concepts shared by both backends in `src/gpu/`: atlases, blend formulas, swizzles, resource keys, async readback, tiled-texture utilities. |
| [Ganesh Backend](ganesh-backend.md) | The legacy GPU backend (`src/gpu/ganesh/`) — `GrDirectContext`, ops, draw atoms, render tasks, GLSL effects. |
| [Graphite Backend](graphite-backend.md) | The modern GPU backend (`src/gpu/graphite/`) — `Recorder`/`Recording`, render passes, compute, precompile, persistent pipeline storage. |
| [GPU Tessellation](gpu-tessellation.md) | Shared tessellator (`src/gpu/tessellate/`) used by both Ganesh and Graphite — Wang's formula, patch writers, stroke iteration, middle-out triangulation. |

## Part VII — GPU Backends

| Topic | Description |
|---|---|
| [OpenGL / GLES](backend-opengl.md) | `gl/` backend code under Ganesh, GL interfaces, and platform-specific GL contexts. |
| [Vulkan](backend-vulkan.md) | Vulkan support in both Ganesh and Graphite, plus VMA integration and external-memory handling. |
| [Metal](backend-metal.md) | Apple Metal backend (`mtl/`) used on macOS and iOS. |
| [Direct3D](backend-direct3d.md) | Direct3D 12 backend (`d3d/`) under Ganesh. |
| [Dawn / WebGPU](backend-dawn.md) | Graphite's Dawn backend, used to target WebGPU and as a portability layer. |

## Part VIII — Shading Language

| Topic | Description |
|---|---|
| [SkSL Shading Language](sksl-shading-language.md) | The SkSL compiler in `src/sksl/` — lexer, IR, analysis, transforms, and code generators that emit GLSL, Metal, SPIR-V, WGSL, and HLSL. |

## Part IX — Document Backends

| Topic | Description |
|---|---|
| [PDF Backend](pdf-backend.md) | PDF document generation in `src/pdf/` — font subsetting, gradient shaders, tagged PDF, JPEG passthrough. |
| [XPS Backend](xps-backend.md) | XPS document generation in `src/xps/` (Windows). |
| [SVG Canvas](svg-canvas.md) | `SkSVGCanvas` (`src/svg/`) — an `SkCanvas` that writes SVG markup. |

## Part X — Higher-Level Modules

| Topic | Description |
|---|---|
| [SVG Module](svg-module.md) | SVG parsing and rendering (`modules/svg/`), distinct from the SVG-output canvas. |
| [Skottie (Lottie)](skottie.md) | Lottie / Bodymovin animation player (`modules/skottie/`) built on top of SkSG. |
| [SkSG Scene Graph](sksg-scene-graph.md) | Retained scene-graph used by Skottie and the SVG renderer (`modules/sksg/`). |
| [SkResources](skresources.md) | Resource-provider abstraction (`modules/skresources/`) for fetching images, fonts, and external data referenced by animations. |
| [SkCMS](skcms.md) | Stand-alone color-management library (`modules/skcms/`). |
| [CanvasKit](canvaskit.md) | WebAssembly bindings (`modules/canvaskit/`) exposing Skia, Skottie, and SkParagraph to JavaScript. |

## Part XI — Platform & Utilities

| Topic | Description |
|---|---|
| [Platform Ports](platform-ports.md) | OS glue in `src/ports/` — file I/O, font hosts (FreeType, CoreText, DirectWrite, Fontations), discardable memory, image generators (CG, NDK, WIC). |
| [Android Integration](android-integration.md) | Android-specific helpers in `include/android/` and `src/android/` — hardware buffers, framework utilities, animated-image surface. |
| [Utilities](utilities.md) | Helpers in `src/utils/` and `include/utils/` — `SkParse`, `SkShadowUtils`, `SkCustomTypeface`, `SkCamera`, `SkPaintFilterCanvas`, `SkOrderedFontMgr`. |

## Part XII — Tooling & Infrastructure

| Topic | Description |
|---|---|
| [Capture & Debugging](capture-and-debugging.md) | `SkCapture`, `SkCaptureCanvas`, the audit trail, trace/event tracer, and debugger bindings. |
| [Developer Tools](developer-tools.md) | Tools shipped under `tools/` — Viewer, Fiddle, skdiff, skpbench, skslc, sksl-minify, skqp, skp tools, GPU/Graphite/Ganesh tools. |
| [Testing & Quality](testing-and-quality.md) | Test surfaces — `dm/` (golden test runner), `tests/` (unit tests), `gm/` (golden masters), `bench/`, and `fuzz/`. |
| [Build System](build-system.md) | The dual GN + Bazel build (`gn/`, `BUILD.gn`, `BUILD.bazel`, `bazel/`, `build_overrides/`, `toolchain/`, `buildtools/`). |
| [Third-Party Dependencies](third-party-deps.md) | Vendored dependencies in `third_party/` and how Skia consumes them (libpng, libjpeg-turbo, FreeType, HarfBuzz, ICU, libwebp, etc.). |

---

## How this index was built

The structure above was derived by walking the upstream tree under
[`skia-main/`](../../skia-main/) — primarily `include/`, `src/`, `modules/`,
and `tools/` — and grouping closely-related directories. Public API headers
in `include/` define the surface area; their implementations in `src/` are
linked from the corresponding section.
