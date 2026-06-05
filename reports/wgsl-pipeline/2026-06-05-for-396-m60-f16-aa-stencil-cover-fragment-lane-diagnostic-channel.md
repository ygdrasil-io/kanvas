# FOR-396 M60 F16 AA stencil-cover fragment lane diagnostic channel

Decision: `M60_F16_AA_STENCIL_COVER_FRAGMENT_LANE_DIAGNOSTIC_CHANNEL_INSTALLED`

Classification: `fragment-lane-diagnostic-channel-installed`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel-for396/m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel-for396.json`

FOR-396 installe un canal diagnostique fragment pour le chemin AA stencil-cover
M60 F16. Le chemin par defaut reste sur `aa_stencil_cover.wgsl` et
`aaPolygonPipelineLayout`; la variante diagnostique est generee en memoire et
utilisee uniquement avec le garde opt-in.

## Garde diagnostique

- garde: `kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled`
- actif par defaut: `False`
- ecritures hors garde: `False`
- depend du transport FOR-394: `True`

## Canal installe

- shader: `in-memory diagnostic variant of aa_stencil_cover.wgsl`
- layout: `binding0 AA uniform, binding1 fragment storage buffer`
- buffer: `128` octets
- schema: `['x', 'y', 'candidateLaneBoolAsU32', 'coverageSide']`
- lecture CPU: `True` via `GPUBufferUsage.MapRead staging buffer after copyBufferToBuffer`
- smoke runtime scene M60: `True`

Pixels attendus encodes par le canal:

- (93, 74) -> lane=true
- (92, 75) -> lane=true
- (91, 76) -> lane=true
- (17, 77) -> lane=true
- (90, 77) -> lane=true
- (89, 78) -> lane=true
- (88, 79) -> lane=true
- (87, 80) -> lane=true

Pixels observes exportes depuis le snapshot runtime: `0`.
L'export runtime du snapshot reste hors de ce ticket ; l'artefact ne revendique
donc pas de faux positifs/faux negatifs mesures ni d'exact-match runtime.

## Non-objectifs preserves

- aucune correction M60 F16 activee ;
- aucune modification de la couleur finale ou de la couverture ;
- aucun changement de fallback, scoring, seuil ou promotion ;
- aucune route FOR-380.

## Impact ideal conserve

- pixels utiles: `8`
- pixels ameliores: `8`
- regressions incluses: `0`
- residu plein scene ideal: `2014` -> `1949`

## Risque restant

The M60 scene smoke test creates the diagnostic shader/pipeline with both guards enabled. The checked-in artifact still records the channel contract structurally; a follow-up can export m60F16FragmentLaneDiagnosticSnapshot if product evidence needs driver-observed values.

## Validations

- `rtk python3 scripts/validate_for396_m60_f16_aa_stencil_cover_fragment_lane_diagnostic_channel.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for396-pycache-parent python3 -m py_compile scripts/validate_for396_m60_f16_aa_stencil_cover_fragment_lane_diagnostic_channel.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py`
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
