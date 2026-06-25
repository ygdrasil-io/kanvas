# M30 - Skia Wrapper Legacy Retirement

## Goal

Build the `KanvasSkiaBridge` that translates existing `SkCanvas` calls to native
`KanvasCanvas` commands, route `SkSurface` through `KanvasSurface`, validate
visual parity with regression tests, and formally deprecate the `gpu-raster`
legacy path.

## Dependencies

Depends on M29 (Kanvas Native API) for `KanvasSurface`, `KanvasCanvas`,
`KanvasPaint`, `KanvasPath`, `KanvasShader`, `KanvasImage`, and `KanvasTextBlob`
that the bridge targets.

## Exit Criteria

- [ ] `KanvasSkiaBridge` translates all five draw families from SkCanvas to KanvasCanvas
- [ ] `SkSurface.flush()` routes through `KanvasSurface.flush()`
- [ ] Regression test suite confirms pixel-identical output for all supported draw families
- [ ] `gpu-raster` module formally deprecated with frozen routes
- [ ] Legacy `useLegacyGpuRaster` flag documented as emergency-only rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M30-001 - KanvasSkiaBridge — SkCanvas to KanvasCanvas command translation](KGPU-M30-001-kanvas-skia-bridge.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-skia-bridge` | [KGPU-M29-008] | null |
| [KGPU-M30-002 - Route SkSurface to KanvasSurface — replace gpu-raster path](KGPU-M30-002-route-sk-surface-to-kanvas.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `kanvas-skia-bridge` | [KGPU-M30-001] | `gpu-raster-legacy-path` |
| [KGPU-M30-003 - Regression tests — Skia GM parity via Kanvas bridge](KGPU-M30-003-regression-parity-tests.md) | `review` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `kanvas-skia-bridge` | [KGPU-M30-002] | null |
| [KGPU-M30-004 - gpu-raster deprecation and legacy route freeze](KGPU-M30-004-gpu-raster-deprecation.md) | `review` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `kanvas-skia-bridge` | [KGPU-M30-002] | `gpu-raster-legacy-freeze` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :skia-integration-tests:test
rtk ./gradlew --no-daemon :integration-tests:test
```

## Non-Claims

- No product activation: M30 validates parity and deprecates legacy, it does not flip routes ON
- No new draw families or rendering features
- No performance optimization of the bridge path
- No deletion of `gpu-raster` source code (archive only)
- No dynamic SkSL compilation; runtime effects use registered Kanvas descriptors with parser-validated WGSL

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
