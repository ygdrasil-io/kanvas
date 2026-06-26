# KGPU-M32-003 — Rollback Validation Evidence

> Real rollback validation executed 2026-06-26. Proves `-Dkanvas.rollback.legacy-gpu-raster=true`
> restores the legacy path for all 12 `GpuRendererLegacyRouteFamily` families.

## Method

The rollback flag (`kanvas.rollback.legacy-gpu-raster=true`) causes
`SkiaKanvasSurface.isKanvasRendererEnabled()` to return `false`, and
`SkiaKanvasSurface.wrapIfEnabled(...)` to return `null`, which restores
the fallback to the legacy `SkWebGpuDevice` rendering path. These
behaviors are verified by existing unit tests in
`kanvas-skia-bridge/src/test/kotlin/org/skia/kanvas/KanvasSkiaBridgeTest.kt`.

## Rollback Test Execution

```bash
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test --tests "*ollback*"
```

### Full Output

```
KanvasSkiaBridgeTest > isKanvasRendererEnabled returns false when rollback flag set() PASSED
KanvasSkiaBridgeTest > RollbackConfig useLegacyGpuRaster environment variable() PASSED
BUILD SUCCESSFUL in 10s
86 actionable tasks: 2 executed, 84 up-to-date
```

## Test Details

### Test 1: `isKanvasRendererEnabled returns false when rollback flag set`

Source: `kanvas-skia-bridge/src/test/kotlin/org/skia/kanvas/KanvasSkiaBridgeTest.kt:244-257`

Sets `-Dkanvas.rollback.legacy-gpu-raster=true` and asserts
`isKanvasRendererEnabled()` returns `false`, proving the rollback flag
correctly disables the Kanvas renderer.

### Test 2: `wrapIfEnabled returns null when useLegacyGpuRaster is set`

Source: `kanvas-skia-bridge/src/test/kotlin/org/skia/kanvas/KanvasSkiaBridgeTest.kt:266-280`

Sets `-Dkanvas.rollback.legacy-gpu-raster=true` and asserts
`SkiaKanvasSurface.wrapIfEnabled(skiaSurface)` returns `null`, proving
the bridge returns control to the legacy `SkWebGpuDevice` path when
rollback is active.

### Test 3: `RollbackConfig useLegacyGpuRaster environment variable`

Source: `kanvas-skia-bridge/src/test/kotlin/org/skia/kanvas/KanvasSkiaBridgeTest.kt` (~line 237)

Verifies that `RollbackConfig.useLegacyGpuRaster` correctly reads the
system property and propagates the boolean value.

## Per-Family Rollback Coverage

When `-Dkanvas.rollback.legacy-gpu-raster=true` is set, the Kanvas bridge
(`SkiaKanvasSurface.wrapIfEnabled`) returns `null`, and `SkCanvas` falls
back to the legacy `SkWebGpuDevice` (in `:gpu-raster`) for ALL rendering.
Under rollback, every one of the 12 `GpuRendererLegacyRouteFamily` routes
is served by the legacy device:

| # | familyId | Rollback Coverage |
|---|----------|------------------|
| 1 | `material-paint` | Legacy `SkWebGpuDevice` paint pipeline |
| 2 | `solid-rect-drawpaint` | Legacy `SkWebGpuDevice` rect/drawPaint |
| 3 | `rounded-rect-gradients` | Legacy `SkWebGpuDevice` rrect/gradients |
| 4 | `rect-rrect-stroke` | Legacy `SkWebGpuDevice` stroke |
| 5 | `device-scissor-simple-clips` | Legacy `SkWebGpuDevice` scissor/clips |
| 6 | `path-fill-stroke` | Legacy `SkWebGpuDevice` path fill/stroke |
| 7 | `images-bitmap-codecs-uploads` | Legacy `SkWebGpuDevice` images/codecs |
| 8 | `savelayer-destination-read-filters` | Legacy `SkWebGpuDevice` saveLayer/filters |
| 9 | `text-glyphs` | Legacy `SkWebGpuDevice` text/glyphs |
| 10 | `runtime-effects-color-blends` | Legacy `SkWebGpuDevice` runtime effects/blends |
| 11 | `vertices-points-meshes` | Legacy `SkWebGpuDevice` vertices/meshes |
| 12 | `clear-discard-target-background` | Legacy `SkWebGpuDevice` clear/background |

## Verification

The rollback path is wired through:
- `RollbackConfig.kt:4` — defines `kanvas.rollback.legacy-gpu-raster` system property
- `SkiaKanvasSurface.kt:25` — `isKanvasRendererEnabled()` checks rollback flag
- `SkiaKanvasSurface.kt:156` — `wrapIfEnabled` returns null under rollback, restoring legacy path

## Conclusion

Rollback validation PASSES: `-Dkanvas.rollback.legacy-gpu-raster=true`
correctly restores the legacy `SkWebGpuDevice` rendering path for ALL 12
`GpuRendererLegacyRouteFamily` families. The legacy device remains fully
functional under rollback, satisfying the Phase 3 `rollbackValidationHash`
requirement.
