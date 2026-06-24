# MX - Milestone Title

## Goal

State the milestone goal.

## Dependencies

State upstream milestone or ticket dependencies.

## Exit Criteria

- [ ] Criterion one.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|

## Validation Bundle

Include `rtk git diff --check` plus milestone-specific module, PM, adapter, or
evidence commands. Avoid a bundle that only repeats generic ticket checks.

```bash
rtk git diff --check
```

### Offscreen Render Validation (obligatoire pour toute scene evidence)

Apres avoir defini les scenes et ajoute les nouvelles familles de commandes
(FillRRect, LinearGradientRect, etc.) au `RectOnlyOffscreenRenderer`,
produire les PNGs de rendu et les committer dans `reports/gpu-renderer-scenes/` :

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

- State what this milestone does not claim.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
