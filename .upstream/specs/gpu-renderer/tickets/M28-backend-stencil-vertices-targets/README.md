# M28 - Backend Stencil, Vertices, and Targets

## Goal

Add stencil buffer, vertex/index buffers, and secondary render targets to the offscreen backend. This unblocks real GPU rendering for path fill (stencil-cover), vertices, and saveLayer compositing.

## Dependencies

Depends on M25 (executor wiring) and M26 (real textures).

## Exit Criteria

- [ ] Stencil buffer depth-stencil attachment + write/read/clear functional
- [ ] Vertex/index buffer creation + indexed draw functional
- [ ] Secondary render target creation + texture sampling functional
- [ ] Path fill scenes render real stencil-cover output
- [ ] Vertices scenes render real mesh output
- [ ] SaveLayer scenes render real composite output

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M28-001 - Add depth-stencil attachment to offscreen backend](KGPU-M28-001-depth-stencil-attachment.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-backend` | [KGPU-M25-005] | null |
| [KGPU-M28-002 - Wire stencil-cover real GPU rendering for path fill](KGPU-M28-002-stencil-cover-path-fill.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-backend` | [KGPU-M28-001] | null |
| [KGPU-M28-003 - Add vertex/index buffer to offscreen backend](KGPU-M28-003-vertex-index-buffer.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-backend` | [KGPU-M25-006] | null |
| [KGPU-M28-004 - Wire vertices real GPU rendering](KGPU-M28-004-vertices-mesh-rendering.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-backend` | [KGPU-M28-003] | null |
| [KGPU-M28-005 - Add secondary render target support](KGPU-M28-005-secondary-render-target.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-backend` | [KGPU-M25-004] | null |
| [KGPU-M28-006 - Wire saveLayer real composite rendering](KGPU-M28-006-savelayer-composite.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-backend` | [KGPU-M28-005] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=path-fill-stencil
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=convex-fan-mesh
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=vertices-color-mesh
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=mesh-ribbon-depth
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-isolated
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=dst-read-strategy
```

## Non-Claims

- No product activation: these tickets wire and render evidence, they do not flip routes ON
- No new family contracts: M28 only expands the backend, doesn't create new draw families
- No performance readiness claims (M27 owns performance gates)
- No surface/swapchain rendering (offscreen only)
- No dynamic SkSL compilation; runtime effects use registered Kanvas descriptors with parser-validated WGSL

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
