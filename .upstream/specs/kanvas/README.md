# Kanvas API Specs

Status: Draft
Date: 2026-07-01
Target: Public API surface of the Kanvas Kotlin facade — the Skia-level drawing API backed by a programmable GPU pipeline.

This pack captures the complete Kanvas Kotlin API surface: core types, Paint/effects, Path geometry, Canvas drawing commands, GPU pipeline interface, diagnostics/refusal policy, Surface rendering loop, DSL builders, and export encoding bridge.

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
| `01-core-types.md` | Color, Point, Size, Rect, RRect, Matrix33 |
| `02-paint-and-effects.md` | Paint, BlendMode, Shader, ColorFilter, MaskFilter, PathEffect, ImageFilter, Blender |
| `03-path-and-geometry.md` | Path, FillType, PathVerb, ClipStack |
| `04-canvas-and-drawing.md` | Canvas, DisplayOp, drawing methods, state, transforms, clips |
| `05-gpu-pipeline.md` | GPUContext, RenderPass, RenderPipeline, ShaderModule, RuntimeEffect, UniformBlock |
| `06-diagnostics-and-refusals.md` | Diagnostic, Diagnostics, refusal levels, error policy |
| `07-surface-and-rendering.md` | Surface, RenderResult, RenderStats, toPng() |
| `08-dsl-and-extensions.md` | @KanvasDsl, PathScope, PaintScope, CanvasScope, ShaderScopes, operators |
| `09-image-and-text.md` | Image, ColorType, TextBlob, KanvasTypeface, KanvasGlyphRun |

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

## Status Policy

- All specs start as `Draft`.
- A spec moves to `Accepted` when: (a) the design is approved, (b) implementation evidence exists, (c) conformance diagnostics pass.
- Editorial clarifications (typos, cross-reference fixes) do not require re-review.
- Structural changes to contracts require re-review against the parent design doc.

## Current Out-Of-Scope Decisions

- `drawVertices`, `drawPicture`, `drawAtlas`, `drawDrawable`, `drawPatch`
- `SkPicture` / `PictureRecorder`
- `SkFont` / `SkFontMgr` (delegated to `:font`)
- `SkRegion`, `SkPathMeasure`
- Path ops (union, intersect, difference)
- Color spaces beyond sRGB
- Text shaping Unicode→glyph (delegated to `:font`)
