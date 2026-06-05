# FOR-398 M60 F16 bounded runtime correction probe

Decision: `M60_F16_BOUNDED_RUNTIME_CORRECTION_PROBE_RECORDED`

Classification: `bounded-correction-refused`

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-bounded-runtime-correction-probe-for398/m60-f16-bounded-runtime-correction-probe-for398.json`

Guard: `kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled`

FOR-398 adds an opt-in experimental shader variant for the M60 F16 AA
stencil-cover path. The variant is disabled by default and uses the FOR-397
runtime predicate, `m60_f16_candidate_lane(frag.xy)`, to skip the
target-colour blend transform only for the proven lane pixels. It is evidence
only and is not wired into production routing.

## Result

- current total residual: `62748`
- corrected total residual: `62748`
- delta corrected minus current: `0`
- gain vs current: `0`
- current similarity: `93.428548`
- corrected similarity: `93.428548`
- full-scene improved pixels: `0`
- full-scene regressed pixels: `0`
- changed pixels: `0`
- outside expected changed pixels: `0`

Predicate-only residual:

- current predicate residual: `730`
- corrected predicate residual: `730`
- improved predicate pixels: `0`
- regressed predicate pixels: `0`
- unchanged predicate pixels: `8`

The FOR-397 runtime predicate remains exact:

- observed pixel count: `8`
- false positives: `0`
- false negatives: `0`
- exact match proven by runtime readback: `true`

## Classification

The correction is refused because it does not reduce total residual and does
not change the 8 predicate pixels in the measured runtime path. This keeps the
predicate proof useful while avoiding a hidden workaround or a reintroduction
of the broad FOR-380 correction path.

Next step: keep FOR-398 disabled and inspect why the per-fragment skip has no
observable effect before changing the correction formula or considering any
promotion ticket.

## Non-goals preserved

- supportClaim remains `false`
- promoted remains `false`
- default rendering remains unchanged
- no threshold, scoring, fallback, or promotion change
- no production runtime connection
- no generalization outside M60 F16 AA stencil-cover
- no reintroduction of the broad FOR-380 correction

## Validations

- `rtk python3 scripts/validate_for398_m60_f16_bounded_runtime_correction_probe.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for398-pycache-parent python3 -m py_compile scripts/validate_for398_m60_f16_bounded_runtime_correction_probe.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for397_m60_f16_fragment_lane_runtime_snapshot_export.py`
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
