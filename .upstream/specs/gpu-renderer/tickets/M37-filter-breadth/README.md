# M37 - Filter Breadth

**Status:** active (2026-06-28) — Wave C Track 2


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
