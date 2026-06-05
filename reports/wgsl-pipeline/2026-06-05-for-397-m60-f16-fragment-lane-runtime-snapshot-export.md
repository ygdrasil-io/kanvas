# FOR-397 M60 F16 fragment-lane runtime snapshot export

Decision: `M60_F16_FRAGMENT_LANE_RUNTIME_SNAPSHOT_EXPORTED`

Classification: `fragment-lane-runtime-snapshot-exported`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-fragment-lane-runtime-snapshot-export-for397/m60-f16-fragment-lane-runtime-snapshot-export-for397.json`

FOR-397 exports the runtime samples from
`m60F16FragmentLaneDiagnosticSnapshot()` into checked-in M60 F16 evidence. The
scene evidence uses the FOR-394 band metadata guard and the FOR-396 fragment
diagnostic guard. Production defaults remain disabled.

## Runtime snapshot

- API: `SkWebGpuDevice.m60F16FragmentLaneDiagnosticSnapshot()`
- guard: `kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled`
- enabled for evidence: `True`
- sample count: `8`
- runtime readback captured: `True`
- exact match proven by runtime readback: `True`

Observed shader pixels:

- (93, 74)
- (92, 75)
- (91, 76)
- (17, 77)
- (90, 77)
- (89, 78)
- (88, 79)
- (87, 80)

False positives:

- none

False negatives:

- none

## Classification

The opt-in fragment diagnostic snapshot exported the 8 FOR-391 expected lane pixels.

Next step: Use the exported predicate evidence only as diagnostic input; FOR-397 does not enable correction or promotion.

## Non-goals preserved

- supportClaim remains `false`
- promoted remains `false`
- no color correction, coverage, fallback, scoring, threshold, promotion, or FOR-380 route change

## Validations

- `rtk python3 scripts/validate_for397_m60_f16_fragment_lane_runtime_snapshot_export.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for397-pycache-parent python3 -m py_compile scripts/validate_for397_m60_f16_fragment_lane_runtime_snapshot_export.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for396_m60_f16_aa_stencil_cover_fragment_lane_diagnostic_channel.py`
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
