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
| Canvas core draws | drawRect, drawRRect, drawDRRect, drawPath, drawPoint, drawPoints, drawImage, drawImageRect, drawImageNine, drawImageLattice, drawText, drawPicture, drawVertices, drawAtlas |
| Canvas fill/clear | drawColor(mode), clear(color) |
| Canvas culling | quickReject(rect/path), isClipEmpty, isClipRect |
| Canvas extensions | drawOval, drawCircle, drawArc, drawLine, drawRoundRect, drawPatch, drawAnnotation, withPicture |
| Canvas state | save/saveLayer/restore/restoreToCount, translate, scale, rotate, skew, concat, setMatrix, resetMatrix, clipRect/clipRRect/clipPath |
| Paint | 14 fields: color, shader, blendMode, colorFilter, maskFilter, pathEffect, imageFilter, blender, style, strokeWidth/Cap/Join/Miter, antiAlias |
| Shader | 15 subtypes: solid color, 4 gradients (linear/radial/sweep/conical), image, perlin noise, fractal noise, blend, runtime effect, local matrix wrapper, color filter wrapper, working color space wrapper, coordinate clamp wrapper |
| ColorFilter | 12 subtypes: matrix, blend, compose, table, lighting, linear-to-sRGB, sRGB-to-linear, HSLA matrix, lerp, high-contrast, luma, overdraw |
| MaskFilter | 3 subtypes: gaussian blur, shader-based mask, lookup-table mask |
| PathEffect | 6 subtypes: dash, corner, discrete, 1D path repeat, 2D path deform, trim |
| ImageFilter | 20 subtypes: blur, drop shadow, color filter, compose, blend, dilate, erode, distant/point/spot diffuse lighting, distant/point/spot specular lighting, offset, tile, merge, displacement map, magnifier, matrix convolution |
| Blender | 2 subtypes: Porter-Duff mode, arithmetic |
| BlendMode | 29 values: all Porter-Duff + separable + non-separable blend modes |
| Color space | sRGB, Display P3, Linear sRGB, PQ/HLG transfer functions, SRGB/DisplayP3/Rec2020 gamuts; gradient color space interpolation |
| Geometry | Path (6 verbs + rect/oval/circle/rrect/path shapes), PathMeasure, PathOps (union/intersect/difference/xor/simplify), Path queries (isConvex, isRect, isOval, isRRect, isLine, isInterpolatable, contains), FillType, Region (rectangle boolean ops) |
| Clip | ClipStack (wide-open, device rect, complex stack with rect/rrect/path ops) |
| Core types | Color, Point, Size, Rect, RRect, CornerRadii, Matrix33 |
| Surface | Width/height/pixel format/color space, Canvas provider, readPixels (region readback), render to pixels + diagnostics |
| Image codec | decode (PNG, JPEG, WebP, GIF, BMP) via SPI; encode (PNG, JPEG, WebP) |
| Pipeline | GPU context, render passes, render pipelines, shader modules, uniform blocks, runtime effect descriptors |
| Picture | Immutable command snapshot, recording session, Canvas.drawPicture, withPicture, binary serialization |
| DSL | Path builders, gradient builders, @KanvasDsl scope marker |

### Out of Scope

| # | Domain | Description |
|---|--------|-------------|
| T1 | Text | Font management — delegated to `:font` |
| T2 | Text | Text shaping (Unicode→glyph, bidi, kerning, glyph substitution/positioning) — delegated to `:font` |
| T3 | Text | TextBlob bounds, serialization, intercepts, string-to-glyph conversion |
| D1 | Document | PDF, XPS backends |
| S1 | Surface | makeSurface / makeImageSnapshot — multi-surface composition |
| G1 | GPU | Externally-managed buffer/texture lifecycle, compute pipelines, multi-pass render graphs |

## Status Policy

- All specs start as `Draft`.
- A spec moves to `Accepted` when: (a) the design is approved, (b) implementation evidence exists, (c) conformance diagnostics pass.
- Editorial clarifications (typos, cross-reference fixes) do not require re-review.
- Structural changes to contracts require re-review against the parent design doc.
