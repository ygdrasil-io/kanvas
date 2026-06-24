# M25 - Missing Wiring

## Goal

Replace the placeholder WGSL `*_WRAPPER` constants and bounding-rect fallbacks
that M24 used with real wiring into the delivered executors and module catalog.
M24 proved the offscreen path runs WGSL, but several families still route
through procedural wrapper shaders (`BITMAP_SHADER_WRAPPER`,
`TEXT_ATLAS_WRAPPER`, `TEXT_SDF_WRAPPER`, `RUNTIME_EFFECT_WRAPPER`,
`LAYER_COMPOSITE_WRAPPER`) or draw bounding rectangles instead of real
geometry. This milestone wires each family to its M12-M22 executor so the
offscreen renderer dispatches the real material pipeline.

## Dependencies

Depends on the contract/stub milestones M12, M15, M17, M18, M20, M21, M22 and
the GPU-native offscreen renderer base (KGPU-M14-005, completed in M24).

## Exit Criteria

M25 closed at `ImplementationCandidate`: every family is routed through its real delivered
executor / module-snippet for diagnostic evidence. The offscreen `GPUBackendRenderRecorder`
supports only fullscreen uniform passes (no textures, samplers, vertex/index buffers, stencil, or
secondary targets), so the procedural `*_WRAPPER` visuals and bounding-rect fills are intentionally
kept for visual output. Real textures / atlas / secondary-target / vertex-buffer visuals are owned
by M26.

- [x] BitmapShader routes through the real `BitmapShaderSnippet` identity + `drawFullscreenRawUniformPass` (executor/snippet wiring done; `BITMAP_SHADER_WRAPPER` procedural visual kept — M26)
- [x] Text A8 + SDF route through `TextA8AtlasExecutor` + `SDFGenerator` + `GlyphAtlasUploadPlanner` (executor wiring done; `TEXT_ATLAS_WRAPPER` procedural visual kept — M26)
- [x] Runtime effects route through the registered SimpleRT descriptor WGSL + gColor ABI — **real GPU output**, no `RUNTIME_EFFECT_WRAPPER`
- [x] SaveLayer routes through `SaveLayerExecutor` + `LayerCompositeSnippet` identity (executor wiring done; `LAYER_COMPOSITE_WRAPPER` procedural visual kept — M26)
- [x] PathFill routes through `PathTessellator` -> `StencilCoverExecutor`/`ConvexFanExecutor` (tessellation/executor wiring done; bounding-rect visual kept — M26)
- [x] Vertices route through `VerticesExecutor` + `GPUVertexBufferUploader` + `GPUMeshBatcher`
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M25-001 - Wire BitmapShader with real GPU sampling](KGPU-M25-001-bitmap-shader-sampling.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M24-002, KGPU-M17-001, KGPU-M17-002] | null |
| [KGPU-M25-002 - Wire Text A8 + SDF atlas rendering](KGPU-M25-002-text-atlas-rendering.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M24-003, KGPU-M20-001, KGPU-M20-002, KGPU-M12-001, KGPU-M12-002] | null |
| [KGPU-M25-003 - Wire RuntimeEffect execution](KGPU-M25-003-runtime-effect-execution.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M24-004, KGPU-M21-001, KGPU-M21-002, KGPU-M21-003] | null |
| [KGPU-M25-004 - Wire SaveLayer offscreen target + composite](KGPU-M25-004-savelayer-composite.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M24-006, KGPU-M18-001, KGPU-M18-003] | null |
| [KGPU-M25-005 - Wire PathFill (tessellation + stencil-cover + convex fan)](KGPU-M25-005-pathfill-stencil-cover.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M15-001, KGPU-M15-002, KGPU-M15-003] | null |
| [KGPU-M25-006 - Wire Vertices mesh rendering](KGPU-M25-006-vertices-mesh-rendering.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M22-001, KGPU-M22-002, KGPU-M22-003] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Non-Claims

- No product activation: these tickets wire and render evidence, they do not flip routes ON
- No new family contracts: M25 only wires families already contracted in M12-M22
- No real decoded textures or glyph atlases yet (M26 owns real-image/atlas data)
- No performance readiness claims (M27 owns performance gates)
- No dynamic SkSL compilation; runtime effects use registered Kanvas descriptors with parser-validated WGSL

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
