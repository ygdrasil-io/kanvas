# FOR-395 M60 F16 source-facing lane shader readback

Decision: `M60_F16_SOURCE_FACING_LANE_SHADER_READBACK_REFUSED`

Classification: `shader-lane-readback-refused-missing-extension`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-facing-lane-shader-readback-for395/m60-f16-source-facing-lane-shader-readback-for395.json`

FOR-395 refuse l'activation d'une preuve shader par readback pour
`m60_f16_candidate_lane`, parce que le chemin `aa_stencil_cover.wgsl` ne dispose
pas encore d'un canal non-couleur capable d'enregistrer la valeur booleenne du
helper pendant `fs_inside` / `fs_outside`.

## Garde diagnostique

- garde: `kanvas.webgpu.m60F16SourceFacingLaneShaderReadback.enabled`
- active par defaut: `False`
- reconnue par le renderer: `True`
- pipeline de readback installe: `False`

## Resultat

Le transport FOR-394 est conserve et reutilise comme source unique :
`m60F16BandMetadata0`, `m60F16BandMetadata1` et le helper
`m60_f16_candidate_lane` restent disponibles dans le shader. Le helper n'est pas
raccorde a la couleur finale, a la couverture, aux fallbacks, au scoring, aux
seuils, a la promotion, ni a la route FOR-380.

Statut de comparaison: `not-executed-missing-extension`

Pixels utiles attendus depuis FOR-391 :

- (93, 74)
- (92, 75)
- (91, 76)
- (17, 77)
- (90, 77)
- (89, 78)
- (88, 79)
- (87, 80)

Pixels observes cote shader: `0`.
Aucun faux positif ou faux negatif mesure n'est revendique, car la comparaison
n'a pas ete executee. Les 8 pixels restent dans `unobservedExpectedPixels`.

## Blocage concret

The existing FOR-258 diagnostic compute probe can sample a texture after normal rendering, but the AA stencil-cover path has no side channel that records the boolean value returned by m60_f16_candidate_lane during fs_inside/fs_outside execution.

Extension minimale :

Add an opt-in diagnostic storage buffer or separate diagnostic render target bound to the AA stencil-cover fragment pass, recording pixel coordinate plus m60_f16_candidate_lane without feeding final color, coverage, fallback, scoring, thresholds, promotion, or FOR-380.

## Impact ideal conserve

- pixels utiles: `8`
- pixels ameliores: `8`
- regressions incluses: `0`
- residu plein scene ideal: `2014` -> `1949`

## Non-objectifs preserves

- aucune correction M60 F16 activee ;
- aucun raccordement a `fs_inside` / `fs_outside` ;
- aucune modification de couleur finale, couverture, fallback, scoring, seuil ou promotion ;
- aucune utilisation de l'ancien probe draw-wide FOR-380.

## Validations

- `rtk python3 scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for395-pycache-parent python3 -m py_compile scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk python3 scripts/validate_for394_m60_f16_aa_stencil_cover_band_metadata_transport.py`
- `rtk python3 scripts/validate_for393_m60_f16_source_facing_lane_shader_metadata.py`
- `rtk python3 scripts/validate_for392_m60_f16_source_facing_lane_runtime_opt_in.py`
- `rtk python3 scripts/validate_for391_m60_f16_source_facing_lane_metadata.py`
- `rtk python3 scripts/validate_for390_m60_f16_full_scene_regression_discriminator.py`
- `rtk python3 scripts/validate_for389_m60_f16_source_coverage_full_scene_candidate.py`
- `rtk python3 scripts/validate_for388_m60_f16_composition_metadata_audit.py`
- `rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py`
- `rtk python3 scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py`
- `rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py`
- `rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py`
- `rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py`
- `rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py`
- `rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py`
- `rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py`
- `rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py`
- `rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py`
- `rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py`
- `rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py`
- `rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py`
- `rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py`
- `rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py`
- `rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py`
- `rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py`
- `rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py`
- `rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py`
- `rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py`
- `rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`
- `rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py`
- `rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
