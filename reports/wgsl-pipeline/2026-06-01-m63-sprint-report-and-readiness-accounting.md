# M63 Sprint Report and Readiness Accounting

Date: 2026-06-01
Linear: FOR-20, FOR-21, FOR-22, FOR-23, FOR-24

## PM Summary

M63 adds the first explicit color/blend/color-filter parity slice for the new
Skia-like real-time renderer target. The sprint promotes three supported rows
and two stable refusal rows:

- supported Src/SrcOver alpha stack;
- supported linear-gradient plus Blend(red,kPlus) color-filter composite;
- supported sweep-gradient clamp over bounded path coverage;
- stable refusal for wide-gamut color-space conversion;
- stable refusal for advanced multi-stage blend chains.

This is visible feature evidence, not UX-only work. The dashboard now has
M63-specific rows, counters, artifacts, route diagnostics, and refusal reasons.

## Evidence Added

| Evidence | Path |
|---|---|
| Audit | `reports/wgsl-pipeline/2026-06-01-m63-color-blend-audit.md` |
| Contract | `reports/wgsl-pipeline/scenes/generated/m63-color-blend-parity-pack.json` |
| Generator task | `pipelineM63ColorBlendParityPack` |
| Gate counter | `m63Rows` and `m63.family.color blend and color-filter parity` |
| PM bundle contract link | `m63GeneratedContractJson` |

## Readiness Movement

Readiness moves from approximately 31% to approximately 35%.

| Area | Weight | Count after M63 | Progress after M63 | Movement |
|---|---:|---:|---:|---|
| Rendering feature breadth | 30% | 5/10 | 50% | M63 adds a selected color/blend/color-filter family contract. |
| Skia-like fidelity | 20% | 30/100 | 30% | Three supported rows add reference/CPU/GPU/diff evidence. |
| Real-time runtime | 20% | 1/10 | 10% | No Kadre/frame-loop runtime capability landed in M63. |
| Performance and cache readiness | 15% | 7/20 | 35% | M63 copies measured performance evidence only for the bounded SrcOver row; no new release gate denominator is added. |
| PM/demo operability | 15% | 10/20 | 50% | PM dashboard and bundle expose the M63 contract, counters, and stable refusal reasons. |

Weighted PM readiness after M63: **35%**.

## Residual Scope

Still open for later milestones:

- HDR and wide-gamut color-space support;
- transfer-function and ICC policy;
- arbitrary blend chains and saveLayer blend composition;
- broader color-filter DAG support;
- runtime-effect color paths in M64;
- Kadre-hosted real-time rendering and live telemetry in M65+.

## Validation

Required before merge:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk git diff --check
```

