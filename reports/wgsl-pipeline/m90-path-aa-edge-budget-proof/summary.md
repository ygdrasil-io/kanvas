# M90 Path AA Edge-Budget Refusal Proof

Status: generated evidence

This report refreshes the refusal proof for the two M90 edge-budget rows. It keeps the WebGPU AA edge budget at 256 and does not promote rendering support.

## Counters

- Proof rows: `2`
- WebGPU AA edge budget: `256`
- GPU expected-unsupported rows: `2`
- CPU oracle pass rows: `2`
- New support claims: `0`
- Readiness delta: `0.0`

## Proof Rows

### path-aa-convexpaths-edge-budget

- Source scene: `ConvexPathsGM`
- Fallback: `coverage.edge-count-exceeded`
- Edge budget: `256`
- CPU route: `cpu.path-coverage.convexpaths-oracle` (`pass`)
- GPU route: `webgpu.coverage.refuse.edge-count` (`expected-unsupported`)
- GPU pipeline key: `coverageKind=pathCoverageUnsupported,pathFillRule=winding,topology=triangleList,source=ConvexPathsGM`
- Smoke candidate allowed: `False`
- Support claim: `False`
- CPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/path-aa-convexpaths-edge-budget/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/path-aa-convexpaths-edge-budget/route-gpu.json`
- Inventory stats: `reports/wgsl-pipeline/scenes/artifacts/path-aa-convexpaths-edge-budget/stats.json`
- Identity evidence: route diagnostics and registry edgeBudgetGateLinks; stats are inventory counters only

### path-aa-dashing-edge-budget

- Source scene: `DashingGM`
- Fallback: `coverage.edge-count-exceeded`
- Edge budget: `256`
- CPU route: `cpu.path-coverage.dashing-oracle` (`pass`)
- GPU route: `webgpu.coverage.refuse.edge-count` (`expected-unsupported`)
- GPU pipeline key: `coverageKind=pathStrokeDashOverflow,pathFillRule=winding,topology=triangleList,source=DashingGM`
- Smoke candidate allowed: `False`
- Support claim: `False`
- CPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/route-gpu.json`
- Inventory stats: `reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/stats.json`
- Identity evidence: route diagnostics and registry edgeBudgetGateLinks; stats are inventory counters only

## Support Guard

- supportClaimAdded: `False`
- readinessMoved: `False`
- thresholdChanged: `False`
- edgeBudgetChanged: `False`
- smokeCandidateAllowed: `False`
- belowThresholdCountedAsProductionGap: `False`
- broadPathAASupport: `False`
- broadDashSupport: `False`
- broadStrokeSupport: `False`
- ganeshPort: `False`
- graphitePort: `False`

## Validation Commands

- `rtk python3 scripts/m90_path_aa_edge_budget_proof.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaEdgeBudgetProof`
- `rtk git diff --check`
