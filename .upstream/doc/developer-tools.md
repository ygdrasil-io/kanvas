# Developer Tools

Skia ships a sprawling `tools/` directory with everything needed to
interactively render, diff, profile, fuzz, and post-mortem its outputs.
This document is a tour of the most important entry points and how they
fit together. Test infrastructure proper (DM, GMs, benches) is covered
in [Testing & Quality](testing-and-quality.md); the capture surface
that several of these tools consume lives in
[Capture & Debugging](capture-and-debugging.md).

| Tool | Source | Purpose |
| --- | --- | --- |
| `viewer` | `tools/viewer/` | interactive slide-based renderer / debugger across every backend |
| `fiddle` | `tools/fiddle/` | sandboxed C++ snippet runner that backs `fiddle.skia.org` |
| `debugger` | `tools/debugger/` | desktop step-through `.skp` debugger |
| `skiaserve` | `tools/skiaserve/` (older) | HTTP-fronted debugger |
| `skdiff` | `tools/skdiff/` | image-vs-image diff with HTML report |
| `image_diff_metric` | `tools/image_diff_metric.cpp` | scalar perceptual diff |
| `skpbench` | `tools/skpbench/` | wall-clock SKP playback benchmark, primarily Android |
| `skpinfo`, `skp_parser` | `tools/skpinfo.cpp`, `tools/skp_parser.cpp` | SKP introspection |
| `dump_record` | `tools/dump_record.cpp` | print every op recorded into an `SkPicture` |
| `skslc` | `tools/skslc/` | offline SkSL → backend-IR compiler |
| `sksl-minify` | `tools/sksl-minify/` | minify SkSL for runtime-effect inclusion |
| `sksltrace` | `tools/sksltrace/` | replay SkSL trace records |
| `skqp` | `tools/skqp/` | Skia Quality Program — Android conformance harness |
| `lottiecap`, `lottie-web-perf`, `skottie2movie` | `tools/lottiecap/`, `tools/lottie-web-perf/`, `tools/skottie2movie.cpp` | Lottie / Skottie playback + perf |
| `skui`, `sk_app`, `window` | `tools/skui/`, `tools/sk_app/`, `tools/window/` | windowing / event abstractions used by `viewer` and friends |
| Backend helpers | `tools/ganesh/`, `tools/graphite/`, `tools/gpu/` | per-backend shims (context factories, pipeline cache utilities) |

Most tools share helpers from the top of `tools/` itself —
`ToolUtils`, `Resources`, `CrashHandler`, `flags/CommandLineFlags`,
`HashAndEncode`, `MSKPPlayer`, `SkSharingProc`, `UrlDataManager`,
`DecodeUtils`/`EncodeUtils`. New tools are expected to reuse those
rather than reinvent argument parsing or PNG I/O.

---

## Viewer — `tools/viewer/`

The Viewer is the central interactive harness. It is a single binary
that loads "slides" — one per `.cpp` in the directory (`3DSlide.cpp`,
`AndroidShadowsSlide.cpp`, `AnimatedImageSlide.cpp`,
`SkSLDebuggerSlide.cpp`, `MSKPSlide.cpp`, `SkpSlide.cpp`, …) — and
lets a developer flip through them while:

- toggling backend ([CPU](cpu-rendering-pipeline.md),
  [Ganesh](ganesh-backend.md) over OpenGL/Vulkan/Metal/D3D, or
  [Graphite](graphite-backend.md) over Dawn/Metal/Vulkan),
- changing color space, MSAA count, dithering, gamma, and surface
  pixel format,
- recording an `.skp` of the current frame, or replaying one captured
  via [Capture & Debugging](capture-and-debugging.md),
- attaching the SkSL debugger / inspector and stepping through a
  runtime effect.

The windowing layer is `tools/sk_app/` (with `Window_*` per-platform
backends) plus `tools/skui/` event types and `tools/window/`.

## Fiddle — `tools/fiddle/`

`fiddle` runs untrusted C++ snippets in a sandbox by linking them
against `fiddle_main.cpp` (which sets up an `SkCanvas`, runs `draw()`,
encodes the result) and shelling out to per-backend stubs
(`egl_context.cpp`, `null_context.cpp`, …). It is the engine behind
`fiddle.skia.org` and behind the executable examples sprinkled through
`include/`. `tools/fiddle/all_examples.cpp` /
`make_all_examples_cpp.py` aggregate the in-header `Example()` blocks
for batch compilation.

## Debugger / skiaserve

`tools/debugger/` is a Qt-free C++ command-step debugger that consumes
the `SkPicture` stream produced by [`SkCaptureCanvas`](capture-and-debugging.md).
It exposes per-op breakdown, matrix/clip/paint state, and overdraw +
GPU op visualisations. Older versions shipped as `skiaserve`, an HTTP
service hitting the same core. The browser-side debugger reuses the
same data via `modules/canvaskit/debugger_bindings.cpp` (see
[CanvasKit](canvaskit.md)).

## skdiff & image_diff_metric — `tools/skdiff/`

`skdiff` walks two directories of PNGs, classifies pixel deltas
(identical / nearly-identical / weighted-by-channel / different-pixels
/ different-sizes / different-other) and emits an HTML report
(`skdiff_html.cpp`) that buckets results for review. `image_diff_metric`
is a single-shot scalar version used in continuous-integration scripts
where a yes/no answer is sufficient.

## SKP tools — `tools/skp*`, `dump_record`

- `skpinfo` prints metadata (version, cull rect, drawable count) of a
  `.skp` file.
- `skp_parser` walks the op stream and is the in-tree reference for how
  to read `SkPicture` files outside Skia itself.
- `dump_record` records into an `SkPictureRecorder`, then prints every
  op — useful when `.skp` round-tripping isn't required.
- `tools/skp/page_sets/` lists the URLs and capture instructions that
  produce Skia's reference SKP corpus.

## skpbench — `tools/skpbench/`

A Python-driven harness that runs an SKP on a phone (`_adb.py`,
`_hardware_pixel*.py`, `_hardware_nexus_6p.py`) and reports
per-sample wall-clock numbers. It exists separately from `bench/`
because it specifically targets Android device-level reproducibility,
including thermal management hooks per device profile.

## SkSL toolchain — `tools/skslc/`, `tools/sksl-minify/`, `tools/sksltrace/`

- `skslc` is the offline SkSL compiler — the same compiler embedded in
  Skia, just exposed as a CLI that emits Metal / SPIR-V / GLSL / HLSL /
  WGSL. It's how the prebuilt runtime-effect blobs in
  `src/sksl/generated/` are produced. See
  [SkSL](sksl-shading-language.md).
- `sksl-minify` (`SkSLMinify.cpp`) shrinks a runtime-effect `.sksl`
  file by stripping comments / whitespace and renaming locals — used
  on shaders embedded in product binaries.
- `sksltrace` replays `.sksltrace` records produced by the SkSL
  debugger so they can be diffed across backends.

## SkQP — `tools/skqp/`

The Skia Quality Program builds an Android APK that runs a fixed
subset of GMs and unit tests against the device's GPU drivers. Pass /
fail thresholds are tracked per device, and SkQP is a gate for Android
GPU certification.

## Backend / platform helpers

- `tools/ganesh/` — Ganesh-only context factories, shader-cache
  inspectors used by `viewer` and `dm`.
- `tools/graphite/` — Graphite equivalents (`Recorder` priv accessors,
  PrecompileTest helpers).
- `tools/gpu/` — backend-agnostic context factory plumbing
  (`GrContextFactory`, `MemoryCache`, `TestOps`).
- `tools/fonts/`, `tools/text/` — embedded test typefaces and font
  configuration for headless testing.

## See also

- [Capture & Debugging](capture-and-debugging.md) — what most of these tools consume.
- [Testing & Quality](testing-and-quality.md) — DM, gm/, tests/, bench/, fuzz/.
- [SkSL Shading Language](sksl-shading-language.md) — what `skslc` and `sksl-minify` operate on.
- [CanvasKit](canvaskit.md) — the WASM debugger UI that mirrors `tools/debugger`.
