# FOR-344 F16 Broader Non-Arc Color Policy Evidence

Linear: `FOR-344`

Decision: `F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS`

FOR-344 collects broader non-arc F16 color-policy evidence before any global
mutation of `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
`SkBitmap.getPixelAsSrgb`. The ticket is evidence/architecture only.

## Result

FOR-343 is present with `F16_COLOR_POLICY_BOUNDARY_READY_FOR_BROADER_EVIDENCE` and the
boundary `cpu-raster-f16-color-policy-boundary` is ready for broader evidence collection.

The result remains partial. The repository has non-arc target-color/blend
diagnostics, but not a genuine non-arc Rec.2020 F16
reference/current/candidate row for `straight_srgb_quantized_alpha_src_over_white`.
Those diagnostics are kept as gap evidence and substitution guards, not as a
global migration proof.

## Matrix

| row | scene | non-arc | F16/Rec.2020 blend signal | reference | current | candidate | current residual | candidate residual | comparable |
|---|---|---|---|---|---|---|---:|---:|---|
| arc-prerequisite-for342-adjacent-cells | circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342 | no | yes | isolated-skia-over-white-reference-available | old-current-and-actual-new-renderer-match-unchanged | computed-policy-samples-available-not-applied | 375 | 0 | yes |
| non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend | m60-bounded-stroke-cap-join | yes | yes | reference-rgba-high-delta-samples-available | current-gpu-rgba-high-delta-samples-available | missing-for-f16-policy-candidate | 856 | n/a | no |
| non-arc-m60-target-colorspace-neutral-aa-substitute-refused | m60-target-colorspace-neutral-aa | yes | yes | single-channel-cpu-reference-sample-available | post-present-red-sample-available | targetColorSpaceBlend-red-sample-available-not-f16-policy-candidate | 13 | 0 | no |
| non-arc-target-color-candidate-inventory-reference-gap | m60-target-color-candidate-inventory | yes | yes | inventory-references-existing-scene-artifacts-only | inventory-current-route-stats-only | missing-straight-srgb-f16-policy-candidate | n/a | n/a | no |

Comparable non-arc rows:
`0` /
`1`.

## Reference Gap

Stable reason:
`NON_ARC_REC2020_F16_REFERENCE_CURRENT_CANDIDATE_ROWS_MISSING`.

Missing row:
A non-arc Rec.2020 kRGBA_F16Norm SrcOver/blend scene with isolated reference, current renderer samples, straight_srgb_quantized_alpha_src_over_white candidate samples, per-sample residuals, aggregate residuals, and worsened-sample counts.

## Dangerous Routes

| diagnostic | route | status | reason |
|---|---|---|---|
| `F16_POLICY_UNSAFE_SELECTED_CELL_SUBSTITUTION` | reuse selected-cell or arc-adjacent samples as non-arc proof | rejected | FOR-344 requires genuine non-arc evidence, not extrapolation from circular_arcs_stroke_butt. |
| `F16_POLICY_UNSAFE_FIXTURE_BRANCH` | fixture-specific renderer branch | rejected | A fixture branch would encode evidence gaps into renderer behavior. |
| `F16_POLICY_UNSAFE_COORDINATE_BRANCH` | coordinate-specific renderer branch | rejected | Coordinate patches would substitute chosen cells for color-policy semantics. |
| `F16_POLICY_UNSAFE_GLOBAL_HOOK_MUTATION` | mutate colorToF16Premul, blendF16PremulMode, SkBitmap.getPixel, or SkBitmap.getPixelAsSrgb | rejected | Global hooks require comparable non-arc reference/current/candidate rows first. |
| `F16_POLICY_UNSAFE_FULL_GM_CROP` | use a full-GM crop as implementation reference | rejected | Full-GM crop evidence is not an isolated reference for this policy migration. |
| `F16_POLICY_UNSAFE_THRESHOLD_RELAXATION` | relax similarity or residual thresholds | rejected | FOR-344 is evidence-only and cannot create policy safety by weakening gates. |

## Non-goals Preserved

- No renderer behavior change.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/f16-broader-non-arc-color-policy-for344/f16-broader-non-arc-color-policy-for344.json`
- Validator: `scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-344-f16-broader-non-arc-color-policy-evidence.md`

## Validation

- `rtk python3 scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py`
- `rtk python3 scripts/validate_for343_f16_color_policy_boundary.py`
- `rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
