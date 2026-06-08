# M91 OffsetImageFilterGM Graph Ownership Proof

Status: generated evidence

This report adds the row-specific graph dump and intermediate-ownership requirement artifacts for `skia-gm-offsetimagefilter`. It does not add render artifacts, diff/stat payloads, performance evidence, fallbackReason=none routes, or a support claim.

## Outputs

- Graph: `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/graph.json`
- Intermediate ownership: `reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/intermediate-ownership.json`

## Counters

- Graph artifacts: `1`
- Ownership artifacts: `1`
- Render artifacts added: `0`
- Diff/stat added: `0`
- Performance artifacts added: `0`
- fallbackReason=none routes added: `0`
- New support claims: `0`
- Readiness delta: `0.0`
- Dashboard promotions: `0`
- Threshold changes: `0`

## Support Guard

- supportClaimAdded: `False`
- readinessMoved: `False`
- policyOnlyPromoted: `False`
- thresholdChanged: `False`
- dashboardPromoted: `False`
- belowThresholdCountedAsProductionGap: `False`
- requiredSmokeCandidateAllowed: `False`
- generalImageFilterDagSupport: `False`
- genericImageFilterDagCompiler: `False`
- cropImageFilterDagSupport: `False`
- picturePrepassSupport: `False`
- arbitraryLayerPrepass: `False`
- cpuReadbackFallbackAdded: `False`
- adjacentSimpleOffsetEvidenceInherited: `False`
- fallbackReasonNoneClaimed: `False`
- renderArtifactsAdded: `False`
- diffStatsAdded: `False`
- performanceGatePromoted: `False`
- ganeshPort: `False`
- graphitePort: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_offset_graph_ownership_proof.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetGraphOwnershipProof`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetEvidenceIntake`
- `rtk git diff --check`
