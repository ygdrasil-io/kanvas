# M29 - Kanvas Native API

## Goal

Expose a public, Skia-free `:kanvas` module with `KanvasSurface`, `KanvasCanvas`,
`KanvasPaint`, `KanvasPath`, `KanvasShader`, `KanvasImage`, and `KanvasTextBlob`
as the native Kanvas drawing surface. This milestone creates the standalone
Kotlin API that callers use directly without Skia indirection.

## Dependencies

Depends on M12 (codec and font pipelines) for `KanvasImage` and `KanvasTextBlob`.
Depends on M24-M28 for the underlying GPU backend, executor wiring, and
stencil/vertices/targets support that `flush()` submits to.

## Exit Criteria

- [x] `:kanvas-api` module compiles as a standalone Kotlin JVM artifact
- [x] `KanvasSurface` + `KanvasCanvas` accept all five draw families (rect, rrect, path, image, text)
- [x] `KanvasPaint` defines color, shader, blendMode, colorFilter, stroke
- [x] `KanvasPath` supports moveTo/lineTo/quadTo/cubicTo/close with fillType
- [x] `KanvasShader` covers solid, linear, radial, sweep, bitmap, and render-target fills
- [x] `KanvasImage` decodes PNG/JPEG/WebP to GPU textures via M12 codec pipeline
- [x] `KanvasTextBlob` wraps glyph runs from M12 font pipeline
- [x] `KanvasSurface.flush()` submits recorded commands to GPU

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M29-001 - Kanvas API module skeleton + KanvasSurface](KGPU-M29-001-kanvas-surface-module.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | none | null |
| [KGPU-M29-002 - KanvasCanvas — drawRect/drawRRect/drawPath/drawImage/drawText](KGPU-M29-002-kanvas-canvas-draw.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | [KGPU-M29-001] | null |
| [KGPU-M29-003 - KanvasPaint — color, shader, blendMode, colorFilter, stroke](KGPU-M29-003-kanvas-paint.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | [KGPU-M29-001] | null |
| [KGPU-M29-004 - KanvasPath — moveTo/lineTo/quadTo/cubicTo/close + fillType](KGPU-M29-004-kanvas-path.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | [KGPU-M29-001] | null |
| [KGPU-M29-005 - KanvasShader — solid, linear, radial, sweep, bitmap, render target](KGPU-M29-005-kanvas-shader.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | [KGPU-M29-003] | null |
| [KGPU-M29-006 - KanvasImage — decode PNG/JPEG/WebP to GPU texture](KGPU-M29-006-kanvas-image.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | [KGPU-M12-010] | null |
| [KGPU-M29-007 - KanvasTextBlob — glyphRun + positioning](KGPU-M29-007-kanvas-text-blob.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | [KGPU-M12-009] | null |
| [KGPU-M29-008 - KanvasSurface.flush() — GPU submit and fence](KGPU-M29-008-kanvas-surface-flush.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-api` | [KGPU-M29-002, KGPU-M29-003, KGPU-M29-004, KGPU-M29-005, KGPU-M29-006, KGPU-M29-007] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas:compileKotlinJvm
rtk ./gradlew --no-daemon :kanvas:compileKotlinMacosArm64
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Non-Claims

- No product activation: M29 creates the native API, it does not flip routes ON
- No Skia bridge or migration (M30 owns bridge and legacy retirement)
- No rendering support claims beyond the API surface itself
- No dynamic SkSL compilation; runtime effects use registered Kanvas descriptors with parser-validated WGSL
- No offscreen rendering scenes (scenes live in gpu-renderer-scenes, not kanvas-api)

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
