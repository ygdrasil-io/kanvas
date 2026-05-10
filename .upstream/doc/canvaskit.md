# CanvasKit

`modules/canvaskit/` packages Skia for the browser as a single
**WebAssembly module** plus a JavaScript glue layer. It exposes
`SkCanvas`, `SkPath`, `SkPaint`, `SkSurface`, `SkImage`, the GPU
backends (WebGL via Ganesh, WebGPU via Graphite/Dawn),
[Skottie](skottie.md), [SkParagraph](skparagraph.md), the SkSL
runtime effects, and an optional debugger — all from one `npm`
package, `canvaskit-wasm`. The output is conceptually "Skia in the
browser, with the same surface area as the native API" rather than
an HTML5-canvas wrapper, although a compatibility shim
(`htmlcanvas/`) is provided.

## Layout

| File / dir | Role |
|------------|------|
| `canvaskit_bindings.cpp` | Main Embind translation unit — wraps `SkCanvas`, `SkPath`, `SkPaint`, `SkSurface`, `SkImage`, gradients, image filters, runtime effects, fonts |
| `paragraph_bindings.cpp`, `paragraph.js` | [SkParagraph](skparagraph.md) bindings |
| `skottie_bindings.cpp`, `skottie.js` | [Skottie](skottie.md) bindings |
| `bidi_bindings.cpp`, `bidi.js` | SkUnicode / BiDi exposure |
| `gm_bindings.cpp`, `gm.js` | GM test runner (used internally by Fiddle) |
| `debugger_bindings.cpp`, `debugger.js` | SKP picture debugger |
| `viewer_bindings.cpp` | Slug viewer for `.skp` / `.svg` |
| `WasmCommon.h` | Shared helpers: `JS` macros, `Uint8Array` <-> `SkData` conversions |
| `interface.js`, `color.js`, `font.js`, `matrix.js`, `memory.js`, `pathops.js`, `rt_shader.js` | JS-side helpers and convenience constructors |
| `webgl.js`, `webgpu.js`, `cpu.js` | Backend-specific surface factories |
| `htmlcanvas/` | A partial drop-in replacement for the HTML `<canvas>` `CanvasRenderingContext2D` API |
| `npm_build/` | NPM package layout: `package.json`, TypeScript types, examples, README |
| `tests/` | Karma-driven Chrome unit tests |
| `Makefile`, `compile.sh`, `compile_gm.sh` | Drives the Emscripten build (`make release`, `make debug`, `make local-example`) |
| `BUILD.gn`, `BUILD.bazel`, `canvaskit.gni` | GN / Bazel build files; feature flags (`no_skottie`, `no_font`, `no_paragraph`, …) |
| `externs.js`, `preamble.js`, `postamble.js`, `release.js` | Closure compiler externs and emitted-module wrapping |

## Build pipeline

CanvasKit compiles with **Emscripten** (`emsdk` is auto-fetched by
`bin/activate-emsdk`). `compile.sh` invokes `gn gen` with WASM
toolchain settings, then `ninja` builds the static library and links
it into a single `canvaskit.wasm` plus a `canvaskit.js` glue file.
The Makefile wraps common configurations:

```
make release          # full release build
make debug            # smaller, with assertions
make local-example    # builds and serves npm_build/example.html
```

Per-feature flags shrink the binary substantially:

```
./compile.sh no_skottie no_font no_paragraph
```

A no-text, no-Skottie release build is roughly half the size of the
default. See `compile.sh` and `BUILD.gn` for the full flag list.

The output lands in `out/canvaskit_*` and is then assembled into
`npm_build/` for publication to npm as `canvaskit-wasm`.

## Bindings shape — `canvaskit_bindings.cpp`

The bindings use **Embind** (`emscripten/bind.h`). For each Skia C++
class exposed to JS, an `EMSCRIPTEN_BINDINGS(name)` block declares
the constructors, methods, and field accessors visible from
JavaScript. The translation unit pulls in essentially every public
header from `include/core/`, `include/effects/`, `include/encode/`,
plus selected `src/` internals where the bindings need to peek
(`src/core/SkPathPriv.h`, `src/image/SkImage_Base.h`, …).

Conventions used throughout:

- **Memory transit** — pixel buffers, encoded blobs, font data, and
  vertex arrays cross the JS/WASM boundary as `Uint8Array` views over
  WASM heap memory. `WasmCommon.h` provides `toBytes` / `toSkData`
  helpers.
- **Numeric arrays** — points, matrices (`SkM44`), colours
  (`SkColor4f`), and rects use plain `Float32Array` / `Float64Array`
  buffers; `matrix.js` and `color.js` provide constructors.
- **Lifetime** — every Skia ref-counted object becomes an Embind
  handle that JS must `delete()` explicitly. The `SkPicture` /
  `SkSurface` factories return such handles.
- **JS shims** — many ergonomic features (typed-array builders,
  named-colour lookup, `MakeCanvasSurface`, `HTMLCanvas` shim, the
  paragraph builder DSL) live in the corresponding `.js` files and
  are baked into the final module by Closure.

## Backends

CanvasKit can be built three ways, controlled by the
`CK_ENABLE_WEBGL` / `CK_ENABLE_WEBGPU` defines:

- **CPU only** (`cpu.js`) — `MakeSWCanvasSurface`, raster surface
  backed by an `<canvas>` element's `ImageData`.
- **WebGL via Ganesh** (`webgl.js`) — `MakeWebGLCanvasSurface`,
  builds a `GrDirectContext` over a WebGL2 / WebGL1 context. See
  [Ganesh Backend](ganesh-backend.md) for the underlying Skia GPU
  pipeline.
- **WebGPU via Graphite + Dawn** (`webgpu.js`) — `MakeGPUCanvasSurface`,
  newer path; builds a `skgpu::graphite::Context` over a `GPUDevice`.
  See [Graphite Backend](graphite-backend.md) and
  [Dawn / WebGPU](backend-dawn.md) for details.

## Higher-level bindings

- **Skottie** (`skottie_bindings.cpp` -> `CanvasKit.MakeAnimation(json)`)
  exposes `Animation::seek(t)` and `Animation::render(canvas)` for
  Lottie playback in the browser. See [Skottie](skottie.md).
- **SkParagraph** (`paragraph_bindings.cpp` ->
  `CanvasKit.ParagraphBuilder.Make(style, fontMgr)`) exposes the full
  paragraph layout and styling API. See
  [SkParagraph](skparagraph.md).
- **HTML Canvas shim** (`htmlcanvas/`) implements a subset of the W3C
  `CanvasRenderingContext2D` on top of `SkCanvas`, useful for
  drop-in porting of canvas-based JS code.
- **SKP debugger** (`debugger_bindings.cpp` -> `debugger.js`) loads
  recorded `.skp` pictures and steps through draws — the same engine
  behind the `viewer` tool.

## TypeScript types & npm package

`npm_build/types/` ships hand-curated `.d.ts` files matching the
public Embind surface. The npm `package.json` exposes both the
"raw" `canvaskit.js` ESM loader and an HTML-canvas-compatible entry,
plus example HTML files (`example.html`, `extra.html`,
`paragraphs.html`, `bidi.html`, `shaping.html`).

## Source map

| File | Role |
|------|------|
| `skia-main/modules/canvaskit/canvaskit_bindings.cpp` | The 7 KLOC Embind file — entry point for everything not in a sub-module |
| `skia-main/modules/canvaskit/skottie_bindings.cpp` | Skottie wrapper |
| `skia-main/modules/canvaskit/paragraph_bindings.cpp` | SkParagraph wrapper |
| `skia-main/modules/canvaskit/interface.js` | Top-level JS API surface, glues handles to ergonomic functions |
| `skia-main/modules/canvaskit/Makefile`, `compile.sh` | Drives the Emscripten build with feature flags |

## Cross-references

- [Skottie](skottie.md) — the animation player surfaced as
  `CanvasKit.MakeAnimation`.
- [SkParagraph](skparagraph.md) — paragraph layout exposed via
  `CanvasKit.ParagraphBuilder`.
- [Graphite Backend](graphite-backend.md) — backs the WebGPU surface.
- [Dawn / WebGPU](backend-dawn.md) — Dawn implements WebGPU on the
  browser side; CanvasKit's WebGPU build talks to Dawn through the
  W3C WebGPU JS API.
- [Ganesh Backend](ganesh-backend.md) — backs the WebGL surface.
- [Runtime Effects](runtime-effects.md) — SkSL shaders compiled at
  runtime from JS.
