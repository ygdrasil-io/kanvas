# M31 - Production Activation

## Goal

Activate Kanvas as the default production rendering path, provide an emergency
rollback flag, deliver the final PM evidence bundle for sign-off, and freeze
the public API with release notes.

## Dependencies

Depends on M30 (Skia Wrapper Legacy Retirement) for validated KanvasSkiaBridge,
proven visual parity, and deprecated `gpu-raster` path. Depends on all prior
milestones (M0-M30) for the complete rendering feature set.

## Exit Criteria

- [ ] Kanvas is the default renderer for all new surfaces (`product_activation=true`)
- [ ] Emergency rollback flag (`useLegacyGpuRaster`) is documented and tested
- [ ] Final PM evidence bundle committed: render PNGs, performance reports, support matrix
- [ ] Release notes published with API stability freeze on `:kanvas`
- [ ] Explicit support/non-support declarations documented

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M31-001 - Default renderer activation — Kanvas as production path](KGPU-M31-001-production-activation.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `true` | `true` | `product-validation` | [KGPU-M30-004] | null |
| [KGPU-M31-002 - Rollback flag — emergency gpu-raster fallback control](KGPU-M31-002-rollback-flag.md) | `done` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `true` | `product-validation` | [KGPU-M31-001] | null |
| [KGPU-M31-003 - Final PM evidence bundle — production-readiness sign-off](KGPU-M31-003-pm-evidence-bundle.md) | `review` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `true` | `product-validation` | [KGPU-M31-001] | null |
| [KGPU-M31-004 - Release notes and API stability freeze](KGPU-M31-004-release-notes-stability.md) | `done` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `true` | `product-validation` | [KGPU-M31-003] | null |
| [KGPU-M31-005 - SkCanvas-bridge ↔ legacy gpu-raster pixel/GM parity](KGPU-M31-005-bridge-pixel-parity.md) | `in-progress` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `true` | `product-validation` | [KGPU-M30-003, KGPU-M31-003, KGPU-M31-006] | `gpu-raster-legacy-path` |
| [KGPU-M31-006 - Execute KanvasSurface recording to pixels — bridge renders real GPU output](KGPU-M31-006-bridge-gpu-execution.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-backend` | [KGPU-M29-001, KGPU-M31-001] | null |

## Evidence Reports

- [KGPU-M31-006 evidence](../../../../reports/gpu-renderer/2026-06-26-M31-006-evidence.md)
  — bridge GPU execution, nonTransparentPixels=30800, CPU similarity=100%

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas:compileKotlinJvm
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :integration-tests:test
rtk ./gradlew --no-daemon :gpu-renderer:test -Dkanvas.rollback.legacy-gpu-raster=true
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=rect-srgb
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=path-fill-stencil
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=image-png
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=text-glyphs
```

## Non-Claims

- No future roadmap activation (M31 is the final recognized milestone in current scope)
- No dynamic SkSL compilation; runtime effects use registered Kanvas descriptors with parser-validated WGSL
- No Ganesh, Graphite, or SkSL compiler support
- No automated rollback detection (manual flag only)

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
