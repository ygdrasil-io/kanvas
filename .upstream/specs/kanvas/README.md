# Kanvas API Specs

Status: Draft
Date: 2026-07-01
Target: Public API surface of the Kanvas Kotlin facade — the Skia-level drawing API backed by a programmable GPU pipeline.

This pack captures the complete Kanvas Kotlin API surface: core types, Paint/effects, Path geometry, Canvas drawing commands, GPU pipeline interface, diagnostics/refusal policy, Surface rendering loop, DSL builders, export encoding bridge, and Picture/recording.

These specs implement the design approved in `docs/superpowers/specs/2026-06-30-kanvas-reboot-design.md`. For the GPU backend execution model, see `../gpu-renderer/`.

## Source Of Truth

- Parent design: `docs/superpowers/specs/2026-06-30-kanvas-reboot-design.md`
- GPU renderer architecture: `../gpu-renderer/`
- Upstream target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
- WGSL specification: https://www.w3.org/TR/WGSL/

## Hard Constraints

1. Do not port Ganesh or Graphite. Kanvas is a native Kotlin API.
2. Types never carry a "Kanvas" prefix. GPU abstractions use `GPU` (not `Gpu`).
3. `SkRuntimeEffect` is a compatibility facade backed by registered Kotlin/WGSL implementations.
4. Pipeline is programmable — `ShaderModule`, `RenderPipeline`, `RenderPass`, `RuntimeEffect` are public.
5. Refusals use structured diagnostics (`FATAL` / `DEGRADE` / `WARN`), not silent dropping.
6. The API is immutable-by-default. `Paint`, `Rect`, `RRect`, `Point`, `Color` are immutable data/value classes.
7. DSL layer is syntax sugar wrapping the core API — all DSL operations must be expressible without DSL.

## Accepted Kernel Decisions

1. **Module**: `:kanvas`, package `org.graphiks.kanvas` — in-place refactoring, not a new module.
2. **Style**: Hybrid — immutable data classes with `copy()` for one-liners, DSL builders for complex scenes.
3. **Error policy**: Exceptions for API misuse (null paint, zero-size surface); Diagnostics for GPU capability gaps.
4. **Image encoding**: `toPng()` via `ServiceLoader` on `:codec:api` — no hard codec dependency in `:kanvas`.
5. **Font/text**: Font management remains in `:font`. Kanvas carries `KanvasTypeface` (resource path) and `TextBlob` (pre-shaped glyph runs).

## Spec Index

| Spec | Purpose |
|------|---------|
| `00-architecture-overview.md` | Module structure, package layout, naming conventions, data flow |
| `01-core-types.md` | Color, Point, Size, Rect, RRect, Matrix33, ColorSpace |
| `02-paint-and-effects.md` | Paint, BlendMode, Shader, ColorFilter, MaskFilter, PathEffect, ImageFilter, Blender |
| `03-path-and-geometry.md` | Path, FillType, PathVerb, ClipStack |
| `04-canvas-and-drawing.md` | Canvas, DisplayOp, drawing methods, state, transforms, clips |
| `05-gpu-pipeline.md` | GPUContext, RenderPass, RenderPipeline, ShaderModule, RuntimeEffect, UniformBlock |
| `06-diagnostics-and-refusals.md` | Diagnostic, Diagnostics, refusal levels, error policy |
| `07-surface-and-rendering.md` | Surface, RenderResult, RenderStats, ColorSpace handoff, toPng() |
| `08-dsl-and-extensions.md` | @KanvasDsl, PathScope, PaintScope, CanvasScope, ShaderScopes, operators |
| `09-image-and-text.md` | Image, ColorType, ColorSpace, TextBlob, KanvasTypeface, KanvasGlyphRun |
| `10-picture-and-recording.md` | Picture, PictureRecorder, Canvas.drawPicture, DisplayOp.DrawPicture |

## Target Shape

```
User Code
    │
    ▼
┌──────────────────────────────────────────┐
│  DSL Layer (PathScope, PaintScope, ...)  │  ← syntax sugar
├──────────────────────────────────────────┤
│  Canvas API (draws, state, transforms)   │  ← recording API
├──────────────────────────────────────────┤
│  DisplayOp sealed hierarchy              │  ← internal command buffer
├──────────────────────────────────────────┤
│  Picture / PictureRecorder               │  ← record + playback (caching)
├──────────────────────────────────────────┤
│  PipelineCompiler → RenderPass[]         │  ← GPU compilation (internal)
├──────────────────────────────────────────┤
│  Surface.render() → RenderResult         │  ← execution + diagnostics
└──────────────────────────────────────────┘
    │
    ▼
GPUContext / RenderPass (public interfaces)
    │
    ▼
WebGPU backend (:gpu-renderer)
```

## Gap Analysis (2026-07-01 vs Skia upstream)

The following Skia API surface areas are not yet covered by Kanvas. Items marked with `★` are candidates for the immediate next implementation wave; items without are deferred to later waves.

### Implemented (parity or near-parity)

| Domain | Status |
|--------|--------|
| Canvas core draws (drawRect/RRect/Path/Image/ImageRect/Text + extensions) | Done |
| Paint (14 fields) | Done |
| Shader (10 subtypes: 4 gradients, Image, Blend, RuntimeEffect, WithLocalMatrix, WithColorFilter) | Done |
| ColorFilter (7 subtypes) | Done |
| MaskFilter (1: Blur) | Done |
| PathEffect (3: Dash, Corner, Discrete) | Done |
| ImageFilter (5: Blur, DropShadow, ColorFilter, Compose, Blend) | Done |
| Blender (2: Mode, Arithmetic) | Done |
| BlendMode (29 values) | Done |
| Geometry (Path, PathVerb, FillType, ClipStack, Rect, RRect, Matrix33) | Done |
| Surface / RenderResult / RenderConfig / Diagnostics | Done |
| Core types (Color, Point, Size, Rect, RRect, Matrix33) | Done |

### Gap Matrix

| # | Skia Domain | Missing API | Priority | Status |
|---|-------------|-------------|----------|--------|
| P1 | Canvas | `drawPicture` — draw a recorded Picture | ★ Wave 1 | Spec in `10-picture-and-recording.md` |
| P2 | Recording | `Picture` — immutable snapshot of captured DisplayOps | ★ Wave 1 | Spec in `10-picture-and-recording.md` |
| P3 | Recording | `PictureRecorder` — begin/finish recording to Picture | ★ Wave 1 | Spec in `10-picture-and-recording.md` |
| C1 | Canvas | `drawPoints`, `drawPoint` — point primitives | Deferred | — |
| C2 | Canvas | `drawDRRect` — double rounded rectangle | Deferred | — |
| C3 | Canvas | `drawImageNine`, `drawImageLattice` — 9-patch / lattice draw | Deferred | — |
| C4 | Canvas | `drawColor` / `clear` — fill entire canvas with color | Deferred | — |
| C5 | Canvas | `quickReject`, `isClipEmpty`, `isClipRect` — culling queries | Deferred | — |
| C6 | Canvas | `readPixels` / `writePixels` — pixel access from canvas | Deferred | — |
| C7 | Canvas | `drawVertices`, `drawMesh`, `drawAtlas`, `drawPatch`, `drawAnnotation` | Deferred | — |
| C8 | Canvas | `drawDrawable` — drawable interface | Deferred | — |
| D1 | Geometry | `SkPathMeasure` — path measurement (length, tangent, segment) | Deferred | — |
| D2 | Geometry | Path ops (union, intersect, difference, xor, simplify) | Deferred | — |
| D3 | Geometry | Path queries (`isConvex`, `isOval`, `isRRect`, `isRect`, `isLine`, `isInterpolatable`) | Deferred | — |
| E1 | Effects | `Sk1DPathEffect`, `Sk2DPathEffect`, `TrimPathEffect` | Deferred | — |
| E2 | Shader | `PerlinNoise` / `FractalNoise` — procedural noise shaders | Deferred | — |
| E3 | Shader | `CoordClamp` — coordinate clamping | Deferred | — |
| E4 | ColorFilter | `HSLAMatrix`, `Lerp`, `HighContrast`, `LumaColorFilter`, `OverdrawColorFilter` | Deferred | — |
| E5 | MaskFilter | `SkShaderMaskFilter`, `SkTableMaskFilter` | Deferred | — |
| E6 | ImageFilter | Lighting (6 variants), Magnifier, MatrixConvolution, DisplacementMap, Dilate/Erode, Offset, Tile, Merge, Crop | Deferred | — |
| E7 | ImageFilter | Shader-based image filter | Deferred | — |
| R1 | Runtime | `RuntimeEffect.makeShader/makeColorFilter/makeBlender` — stub, blocked by wgsl4k | Deferred | Blocked |
| R2 | Runtime | `RuntimeEffect.compile()` — returns Result.failure | Deferred | Blocked |
| R3 | Runtime | `RuntimeEffect.registered()` — returns null | Deferred | — |
| R4 | Pipeline | All 7 `RenderPipeline` constants use placeholder shaders (`return vec4f();`) | Deferred | — |
| I1 | Image | `Image.decode()` — placeholder (returns 0x0 image) | Deferred | — |
| I2 | Image | Image encode beyond PNG (JPEG, WebP) | Deferred | — |
| S1 | Serialize | `Picture.serialize()`, `Picture.MakeFromData()` | Deferred | Blocked by image encode |
| T1 | Text | `SkFont`, `SkFontMgr`, full SkTypeface | Delegated | `:font` module |
| T2 | Text | Text shaping (Unicode→glyph), Bidi, kerning, GPOS/GSUB | Delegated | `:font` module |
| T3 | Text | `TextBlob.makeFromString`, `makeFromRSXform`, `bounds()`, `serialize()` | Delegated | `:font` module |
| G1 | Region | `SkRegion` — boolean operations on rectangular regions | Deferred | — |
| G2 | Shader | `makeWithWorkingColorSpace`, color space interpolation | Hardcoded sRGB | Deferred |
| G3 | Document | PDF, XPS, SVG canvas backends | Out of scope | — |
| G4 | GPU | Graphite / Ganesh GPU backends | Out of scope | Arch decision |

## Status Policy

- All specs start as `Draft`.
- A spec moves to `Accepted` when: (a) the design is approved, (b) implementation evidence exists, (c) conformance diagnostics pass.
- Editorial clarifications (typos, cross-reference fixes) do not require re-review.
- Structural changes to contracts require re-review against the parent design doc.

## Current Out-Of-Scope Decisions

The following are explicitly NOT in Kanvas scope:

- **Canvas**: `drawVertices`, `drawAtlas`, `drawDrawable`, `drawPatch`, `drawAnnotation`, `drawDRRect`, `drawImageNine`, `drawImageLattice`, `drawColor`/`clear`, `quickReject`, `readPixels`/`writePixels`
- **Geometry**: `SkRegion`, `SkPathMeasure`, path boolean operations
- **Effects**: `Sk1DPathEffect`, `Sk2DPathEffect`, `TrimPathEffect`, `PerlinNoise`/`FractalNoise`, Lighting/Morphology/Displacement ImageFilters
- **Text**: `SkFont` / `SkFontMgr` (delegated to `:font`), text shaping (delegated to `:font`)
- **Document**: PDF, XPS, SVG canvas backends
- **GPU**: Ganesh and Graphite backends (architectural decision)

Items being promoted to in-scope for Wave 1:
- `drawPicture`, `Picture`, `PictureRecorder` (spec in `10-picture-and-recording.md`)
