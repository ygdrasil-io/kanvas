# M54 PM Bundle Counters And Gates

Date: 2026-05-31
Milestone: M54
Linear epic: GRA-317
Ticket: GRA-323

## Scope

M54 extends the PM bundle and dashboard gate with hard feature depth metadata,
while preserving the existing M52 and M53 inventory promotion metadata.

## Bundle Metadata

`pipelinePmBundle` writes M54 metadata to
`build/reports/wgsl-pipeline-pm-bundle/manifest.json#m54HardFeatureDepth`.

The manifest block includes:

- selected/promoted/rejected counters;
- promoted `pass` and `expected-unsupported` counters;
- family counters;
- promoted row details with scene id, inventory id, status, source report,
  derivation report, derivation contract, base generated scene, derivation task,
  and hard feature family;
- warning-only performance rows;
- rejected/deferred inventory ids and reasons.

## Gate Metadata

`pipelineSceneDashboardGate` now reports:

- total M54 row count;
- M54 family counters under `m54.family.*`;
- stable fallback checks for M54 expected-unsupported rows;
- existing global promoted-dashboard checks for 0 `tracked-gap` and 0 `fail`.

The existing gate invariants already fail a generated `pass` row that lacks GPU
image/diff/stats or has a non-`none` `gpu.route.fallbackReason`, and fail an
`expected-unsupported` row that lacks a stable non-`none` fallback reason. M54
rows use those same invariants.

## Expected Counters

| Signal | Count |
|---|---:|
| M54 selected candidates | 13 |
| M54 promoted rows | 10 |
| M54 promoted `pass` rows | 8 |
| M54 promoted `expected-unsupported` rows | 2 |
| M54 rejected/deferred rows | 12 |
| M54 hard families | 3 |

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass.
