# M91 ImageMakeWithFilterGM Graph Ownership Proof

Status: generated evidence

This report adds row-specific graph and intermediate-ownership requirement artifacts for `skia-gm-imagemakewithfilter`. It describes the 6x13 ImageMakeWithFilterGM grid, but does not execute `MakeWithFilter`, add render artifacts, add diff/stat payloads, add performance evidence, add fallbackReason=none routes, or add a support claim.

## Outputs

- Graph: `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/graph.json`
- Intermediate ownership: `reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/intermediate-ownership.json`

## Counters

- clipRowsDescribed: `6`
- dashboardPromotions: `0`
- diffStatsAdded: `0`
- fallbackReasonNoneRoutesAdded: `0`
- filterColumnsDescribed: `13`
- graphArtifacts: `1`
- gridCellsDescribed: `78`
- newSupportClaims: `0`
- ownershipArtifacts: `1`
- performanceArtifactsAdded: `0`
- readinessDelta: `0.0`
- renderArtifactsAdded: `0`
- thresholdChanges: `0`

## Support Guard

- belowThresholdCountedAsProductionGap: `False`
- boundedM61M89EvidenceInherited: `False`
- broadImageFilterDAGSupport: `False`
- cpuReadbackFallbackAdded: `False`
- cropPrepassSupport: `False`
- dashboardPromoted: `False`
- diffStatsAdded: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`
- fallbackReasonNoneClaimed: `False`
- ganeshPort: `False`
- genericImageFilterDagCompiler: `False`
- graphitePort: `False`
- imagemakewithfilterEvidenceInherited: `False`
- layerPrepassSupport: `False`
- makeWithFilterExecuted: `False`
- performanceGatePromoted: `False`
- picturePrepassSupport: `False`
- policyOnlyPromoted: `False`
- readinessMoved: `False`
- renderArtifactsAdded: `False`
- supportClaimAdded: `False`
- thresholdChanged: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_imagemakewithfilter_graph_ownership_proof.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterGraphOwnershipProof`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterEvidenceIntake`
- `rtk git diff --check`
