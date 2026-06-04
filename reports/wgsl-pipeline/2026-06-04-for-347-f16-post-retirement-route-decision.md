# FOR-347 F16 Post-Retirement Route Decision

Linear: `FOR-347`

Decision: `F16_POST_RETIREMENT_ROUTE_SELECTED`

Recommended route: `new-candidate-search`

FOR-347 selects the next route after retiring `straight_srgb_quantized_alpha_src_over_white` as a
global F16 candidate. This is a decision artifact only; it does not change
renderer behavior or authorize a score increase.

## Result

The selected route is `new-candidate-search`. It is the only route that can
support a future score-oriented path without reusing the retired global
candidate or generalizing from one scene.

`straight_srgb_quantized_alpha_src_over_white` remains forbidden as a global candidate.

## Preserved Evidence

| source | current residual | candidate residual | worsened samples |
|---|---:|---:|---:|
| FOR-346 | 0 | 111 | 3 |
| FOR-345 | 0 | 111 | 3 |

## Route Matrix

| route | status | score potential | risk | reason |
|---|---|---|---|---|
| `localized-policy` | not-selected | limited | medium | A local policy can be valid only with strict scope and route diagnostics, but it risks reintroducing scene-shaped behavior if used as the immediate score path. |
| `stable-fallback` | not-selected | none | low | A stable fallback is the safest renderer behavior, but it does not move the score objective. |
| `new-candidate-search` | selected | highest | controlled-by-evidence | After the global candidate was retired, the only score-oriented route is a new candidate search that starts with arc and non-arc evidence instead of generalizing from one scene. |

## Child Ticket Seeds

### `localized-policy`
- Define exact local policy domain and route diagnostics.
- Collect positive reference/current/candidate rows for the local domain.

### `stable-fallback`
- Document unsupported global F16 color-policy migration route.
- Assert refusal diagnostics for unsupported broad migration attempts.

### `new-candidate-search`
- Create an arc plus non-arc F16 candidate-search matrix with reference/current/candidate rows.
- Define candidate rejection criteria before evaluating new formulas.
- Select a replacement candidate only after positive evidence across both families.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-post-retirement-route-decision-for347/f16-post-retirement-route-decision-for347.json`
- Validator: `scripts/validate_for347_f16_post_retirement_route_decision.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-347-f16-post-retirement-route-decision.md`

## Validation

- `rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py`
- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
