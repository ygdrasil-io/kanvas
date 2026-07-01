# Kanvas API Specs

Status: Draft
Date: 2026-07-01
Target: Public API surface of the Kanvas Kotlin facade — a professional-grade 2D drawing API backed by a programmable GPU pipeline.

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
3. `RuntimeEffect` is a compatibility facade backed by registered Kotlin/WGSL implementations.
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

## Scope Summary

Kanvas is a recording 2D drawing API backed by a programmable GPU pipeline. It covers the following surface area; everything else is intentionally excluded.

### In Scope

| Domain | Coverage |
|--------|----------|
| Canvas core draws | drawRect, drawRRect, drawPath, drawImage, drawImageRect, drawText, drawPicture |
| Canvas extensions | drawOval, drawCircle, drawArc, drawLine, drawRoundRect, withPicture |
| Canvas state | save/saveLayer/restore/restoreToCount, transmit, scale, rotate, skew, concat, setMatrix, resetMatrix, clipRect/clipRRect/clipPath |
| Paint | 14 fields: color, shader, blendMode, colorFilter, maskFilter, pathEffect, imageFilter, blender, style, strokeWidth/Cap/Join/Miter, antiAlias |
| Shader | 10 subtypes: solid color, linear/radial/sweep/conical gradients, image, blend, runtime effect, local matrix wrapper, color filter wrapper |
| ColorFilter | 7 subtypes: matrix, blend, compose, table, lighting, linear-to-sRGB, sRGB-to-linear |
| MaskFilter | Gaussian blur |
| PathEffect | Dash pattern, rounded corners, discrete scattering |
| ImageFilter | Gaussian blur, drop shadow, color filter, compose, blend |
| Blender | Porter-Duff mode, arithmetic |
| BlendMode | 29 values: all Porter-Duff compositing + separable + non-separable blend modes |
| Geometry | Path (move, line, quad, cubic, arc, close) + fill type + convenience shapes; ClipStack (wide-open, device rect, complex stack) |
| Core types | Color, Point, Size, Rect, RRect, CornerRadii, Matrix33 |
| Surface | Width/height/pixel format, Canvas provider, render to pixel buffer with diagnostics |
| Pipeline | GPU context, render passes, render pipelines, shader modules, uniform blocks, runtime effect descriptors |
| Picture | Immutable command snapshot (Picture), recording session (PictureRecorder), Canvas.drawPicture, withPicture extension |
| DSL | Path builders, gradient builders, @KanvasDsl scope marker |

### Out of Scope

| # | Domain | Description |
|---|--------|-------------|
| C1 | Canvas | Point primitives (drawPoint, drawPoints) |
| C2 | Canvas | Double rounded rectangle (drawDRRect) |
| C3 | Canvas | Nine-patch and lattice image drawing |
| C4 | Canvas | Full-canvas fill or clear |
| C5 | Canvas | Visibility culling queries |
| C6 | Canvas | Direct pixel readback or injection |
| C7 | Canvas | Triangle mesh, sprite atlas, patch, annotation rendering |
| D1 | Geometry | Path measurement (arc length, tangent, segment) |
| D2 | Geometry | Path boolean operations |
| D3 | Geometry | Path introspection (convexity, shape detection, interpolatability) |
| D4 | Geometry | Region-based clipping geometry |
| E1 | Effects | Path-based shape effects (pattern repeat, deformation, trimming) |
| E2 | Shader | Procedural noise (Perlin, turbulence) |
| E3 | Shader | Coordinate clamping |
| E4 | ColorFilter | HSLA matrix, filter interpolation, high-contrast, luma extraction, overdraw |
| E5 | MaskFilter | Shader-based masks, lookup-table masks |
| E6 | ImageFilter | Morphological filters, lighting effects, displacement mapping, magnifier, matrix convolution, offset, tile, merge, crop |
| I1 | Image | Codec integration (decode + encode beyond PNG) |
| S1 | Serialize | Picture binary serialization |
| T1 | Text | Font management (delegated to `:font`) |
| T2 | Text | Text shaping — Unicode to glyph, bidi, kerning, glyph substitution/positioning (delegated to `:font`) |
| T3 | Text | Text blob bounds, serialization, intercepts, string-to-glyph conversion |
| D5 | Document | PDF, XPS backends |
| G1 | GPU | Externally-managed buffer/texture lifecycle, compute pipelines, multi-pass render graphs |

## Status Policy

- All specs start as `Draft`.
- A spec moves to `Accepted` when: (a) the design is approved, (b) implementation evidence exists, (c) conformance diagnostics pass.
- Editorial clarifications (typos, cross-reference fixes) do not require re-review.
- Structural changes to contracts require re-review against the parent design doc.
