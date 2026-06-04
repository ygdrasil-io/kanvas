# FOR-306 Bounded Image-Filter Residual Policy

Linear: `FOR-306`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-bounded-image-filter-residual-policy-guard-ticket`

Decision: `IMAGE_FILTER_RESIDUAL_POLICY_GUARD_APPLIED`

## Result

FOR-306 codifies the bounded image-filter residual policy without changing the
renderer, shaders, thresholds, fallbacks, or scene statuses. The diagnostic
signals from FOR-261 to FOR-265 remain evidence, not support claims.

The preserved fallback reason remains:

```text
image-filter.crop-input-nonnull-prepass-required
```

The remaining boundary remains:

```text
rgba16float-intermediate-store-to-present-byte-quantization-policy
```

## Source Audits

| Audit | Signal | Decision | Missing condition |
|---|---|---|---|
| `FOR-261` | RGBA8Unorm intermediate candidate | `KEEP_DIAGNOSTIC` | `missing_precision_sensitive_whole_scene_rgba8_intermediate_correction_without_targetColorSpaceBlend` |
| `FOR-262` | targetColorSpaceBlend scope | `KEEP_DIAGNOSTIC` | `missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation` |
| `FOR-263` | targetColorSpaceBlend x intermediateFormat matrix | `KEEP_DIAGNOSTIC` | `missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation` |
| `FOR-264` | RGBA16Float present byte quantization | `KEEP_DIAGNOSTIC` | `missing_family_bound_proof_that_rgba16float_present_byte_quantization_is_safe_without_targetColorSpaceBlend` |
| `FOR-265` | RGBA16Float quantization family scope | `KEEP_DIAGNOSTIC` | `missing_family_bound_proof_that_rgba16float_present_byte_quantization_is_safe_without_targetColorSpaceBlend` |

## Policy

The following signals are diagnostic-only for `webgpu.image-filter.offset-crop-prepass-and-src-over` until a future
ticket provides complete row-local proof:

- `RGBA8Unorm` intermediate improvement;
- `RGBA16Float` present-byte quantization reconstruction;
- `targetColorSpaceBlend` correction signal.

Required local proof for any future support candidate:

- reference artifact;
- CPU artifact;
- GPU artifact;
- diff/stat artifact;
- route diagnostics;
- stable fallback policy;
- strict local improvement;
- no global threshold change;
- no global `targetColorSpaceBlend` enablement.

## Guard Cases

| Case | Decision | Allowed | Reason |
|---|---|---|---|
| FOR-261/FOR-265 diagnostic signals alone must not promote image-filter support | `forbidden` | `False` | Promotion lacks required local proof fields: cpuArtifact, diffStats, fallbackStable, noGlobalTargetColorSpaceBlend, noGlobalThresholdChange, routeDiagnostics, strictLocalImprovement |
| Global targetColorSpaceBlend activation must not promote image-filter support | `forbidden` | `False` | Global targetColorSpaceBlend activation is not an allowed image-filter residual support proof. |
| Diagnostic inventory without support claim is allowed | `diagnostic-inventory` | `True` | Diagnostic inventory is allowed when it does not claim support or promotion. |
| Complete local proof can become a support candidate | `candidate-local-proof` | `True` | Promotion is allowed only as a candidate because complete local proof is present. |

## Validation

- `rtk python3 scripts/validate_for306_bounded_image_filter_residual_policy.py`
- `rtk python3 scripts/validate_for265_rgba16float_quantization_family_scope.py`
- `rtk python3 scripts/validate_for264_rgba16float_present_quantization_audit.py`
- `rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py`
- `rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
