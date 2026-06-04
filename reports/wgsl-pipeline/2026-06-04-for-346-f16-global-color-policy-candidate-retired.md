# FOR-346 F16 Global Color Policy Candidate Retired

Linear: `FOR-346`

Decision: `F16_GLOBAL_COLOR_POLICY_CANDIDATE_RETIRED`

FOR-346 retires `straight_srgb_quantized_alpha_src_over_white` as a global F16 color-policy
candidate. This is a decision artifact only; it does not change renderer behavior.

## Result

The candidate is no longer globally open, cannot be used to raise the global
F16 score, and cannot authorize a global F16 renderer migration.

The retirement is based on the FOR-343/FOR-344/FOR-345 evidence chain:

- FOR-343 keeps global F16 renderer migration behind an explicit color-policy boundary.
- FOR-344 records that non-arc evidence was required before broader policy use.
- FOR-345 supplies the comparable non-arc row and the candidate worsens covered samples.

## FOR-345 Evidence

| metric | value |
|---|---:|
| non-arc | true |
| excluded scene | `circular_arcs_stroke_butt` |
| color type | `kRGBA_F16Norm` |
| color space | `Rec.2020` |
| blend mode | `kSrcOver` |
| samples | 4 |
| covered samples | 3 |
| current residual | 0 |
| candidate residual | 111 |
| candidate minus current | 111 |
| worsened samples | 3 |

## Remaining Options

- `localized-policy`: A narrower policy may still be explored only with explicit scope, route diagnostics, and positive reference/current/candidate evidence.
- `stable-fallback`: Keep the current behavior and document unsupported broader F16 migration routes.
- `new-candidate-search`: Search for a different candidate using non-arc and arc evidence from the start.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-global-color-policy-candidate-retired-for346/f16-global-color-policy-candidate-retired-for346.json`
- Validator: `scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-346-f16-global-color-policy-candidate-retired.md`

## Validation

- `rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- `rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py`
- `rtk python3 scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py`
- `rtk python3 scripts/validate_for343_f16_color_policy_boundary.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
