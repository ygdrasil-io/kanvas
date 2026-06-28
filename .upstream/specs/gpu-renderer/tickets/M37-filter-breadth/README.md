# M37 - Filter Breadth

## Goal

Promote multi-pass separable blur, morphology, drop shadow, lighting, displacement map, and tile-based filter evaluation from TargetNative specs to accepted routes.

## Dependencies

Depends on M0-M1. M37-003 reuses M37-001 blur contracts.

## Exit Criteria

- [ ] `GPUSeparableBlurPlan` with horizontal + vertical passes, quality tiers, and intermediate textures accepted with CPU oracle evidence.
- [ ] `GPUMorphologyPlan` dilate/erode with rect and circular kernels accepted with CPU oracle evidence.
- [ ] `GPUDropShadowPlan` with blur reuse and SrcOver composite accepted with CPU oracle evidence.
- [ ] `GPULightingPlan` directional + specular with bump/normal map sources accepted with CPU oracle evidence.
- [ ] `GPUDisplacementMapPlan` channel-select offset sampling with tile modes accepted with CPU oracle evidence.
- [ ] `GPUFilterTilePlan` tiled sub-renders with overlap matching non-tiled output accepted with CPU oracle evidence.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M37-001 - Multi-pass separable blur](KGPU-M37-001-multi-pass-separable-blur.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `false` | `filters` | `KGPU-M1-001` | `null` |
| [KGPU-M37-002 - Morphology filter](KGPU-M37-002-morphology-filter.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `false` | `filters` | `KGPU-M1-001` | `null` |
| [KGPU-M37-003 - Drop shadow filter](KGPU-M37-003-drop-shadow-filter.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `false` | `filters` | `KGPU-M1-001`, `KGPU-M37-001` | `null` |
| [KGPU-M37-004 - Lighting filters](KGPU-M37-004-lighting-filters.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `false` | `filters` | `KGPU-M1-001` | `null` |
| [KGPU-M37-005 - Displacement map filter](KGPU-M37-005-displacement-map-filter.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `false` | `filters` | `KGPU-M1-001` | `null` |
| [KGPU-M37-006 - Filter tile-based evaluation](KGPU-M37-006-filter-tile-based-evaluation.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `false` | `filters` | `KGPU-M1-001` | `null` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Blur*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Morphology*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DropShadow*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Lighting*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Displacement*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FilterTile*'
```

### Offscreen Render Validation (obligatoire pour toute scene evidence)

Apres avoir defini les scenes et ajoute les nouvelles familles de commandes
(blur, morphology, drop shadow, lighting, displacement map, filter tile) au
`RectOnlyOffscreenRenderer`, produire les PNGs de rendu et les committer dans
`reports/gpu-renderer-scenes/` :

```bash
# 1. Verifier que le renderer accepte les nouvelles scenes
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test

# 2. Produire les PNGs (necessite GPU Apple Metal)
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=<scene-id>

# 3. Verifier que render.png et run.json sont produits
rtk cat reports/gpu-renderer-scenes/offscreen/<scene-id>/run.json | rtk jq .status
# -> "rendered"

# 4. Committer les rapports avec le code
rtk git add reports/gpu-renderer-scenes/offscreen/<scene-id>/
```

Ne pas oublier de mettre a jour `RectOnlyOffscreenRenderer` pour supporter
les nouvelles familles de commandes dans :
- `rectOnlyCommandSequenceUnsupportedReason()`
- `prepareRectOnlyDrawPlan()`

## Non-Claims

- This milestone does not activate product routing for any filter.
- Acceptance does not imply Skia `SkImageFilter` or `SkColorFilter` parity.
- Runtime effect filter integration is not claimed; that belongs to M38.
- No CPU-rendered filter-to-texture fallback is accepted.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
