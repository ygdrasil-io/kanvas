# Architecture Overview

Status: Draft
Date: 2026-07-01

## Purpose

Defines the module structure, package layout, naming conventions, data flow, and architectural boundaries of the Kanvas Kotlin API facade. This is the authoritative reference for where types live and how the system is organized.

## Package Structure

The `:kanvas` module lives at `kanvas/` and uses the base package `org.graphiks.kanvas` with these sub-packages:

| Sub-package | Responsibility |
|-------------|----------------|
| `types/` | Core value types: Color, Point, Size, Rect, RRect, Matrix33, ColorSpace |
| `paint/` | Paint data class and all effect sealed hierarchies |
| `geometry/` | Path, FillType, PathVerb, ClipStack |
| `canvas/` | Canvas, DisplayOp sealed hierarchy, extensions |
| `pipeline/` | GPU pipeline interfaces and supporting types |
| `surface/` | Surface, RenderResult, Diagnostics, ImageEncoder bridge |
| `text/` | TextBlob, KanvasTypeface, KanvasGlyphRun |
| `image/` | Image, ColorType |
| `dsl/` | @KanvasDsl annotation, DSL scopes (PathScope, PaintScope, CanvasScope) |
| `operators/` | Operator extensions for Point, Matrix33, Rect, Path |
| `picture/` | Picture, PictureRecorder — recording and playback of DisplayOp snapshots |

## Naming Conventions

| Rule | Example |
|------|---------|
| No "Kanvas" prefix on types | `Canvas` not `KanvasCanvas` |
| GPU abstractions use `GPU` prefix | `GPUContext`, `GPUHandle`, not `GpuContext` |
| Sealed interfaces for effects | `Shader`, `ColorFilter`, `MaskFilter` |
| Data classes for value types | `Point`, `Rect`, `RRect`, `Paint` |
| Value classes for single-field wrappers | `Color` (UInt), `GPUHandle` (Long) |
| Enum classes for fixed sets | `BlendMode`, `TileMode`, `FillType` |

## Data Flow

```
User Code
    │
    ├── PictureRecorder ──→ Picture (frozen DisplayOp[] snapshot)
    │
    ├── Canvas API ──→ DisplayOp[] (command buffer, may include DrawPicture)
    │
    ▼
Surface.render()
    │
    ├── PipelineCompiler.compile(DisplayOp[]) → CompiledFrame
    │       │
    │       ├── Expand DrawPicture → nested DisplayOp[]
    │       ├── Map Paint.shader → RenderPipeline (built-in or RuntimeEffect)
    │       ├── Map DisplayOp → RenderPass + GPU bindings
    │       └── Collect Diagnostics on refusal
    │
    ├── Execute RenderPass[] on GPUContext
    │
    ▼
RenderResult(pixels, diagnostics, stats)
```

## Module Dependencies

- `:kanvas` → `:gpu-renderer` (api), `:font:gpu-api` (api)
- `:kanvas` → `:codec:api` (implementation, for SPI image encoding)
- `:kanvas` → `:font` (implementation, for text lowering)
- `:kanvas:svg` → `:kanvas` (api) — SVG→Kanvas bridge, defined in a separate spec pass

## Non-Goals

- This spec does not define the GPU backend execution model — see `../gpu-renderer/`
- File-level grouping within sub-packages is not prescribed (decided during implementation)
- `:kanvas` does not depend on `:kanvas-skia` or any native C++ rendering bindings
