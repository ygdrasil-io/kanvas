# M65/M66 Sprint Report And Readiness Accounting

Date: 2026-06-01
Linear epics: FOR-31, FOR-32
PR scope: real-time smoke lane plus cumulative GM/reference promotion wave

## Summary

M65 and M66 are ready for review as a paired sprint.

M65 adds a reporting-only runtime smoke lane, not a live Kadre demo. It
generates 120-frame telemetry, nonblank PNG frame artifacts, route/refusal
diagnostics, and a precise Kadre blocker: `m65.kadre-host-not-wired`.

M66 adds a cumulative GM/reference wave with 19 generated dashboard rows:
16 `pass` rows and 3 `expected-unsupported` rows. The selected fidelity
evidence numerator moves from 31/100 to 50/100, while Skia-comparable evidence
is explicitly tracked as a lower 37/100 minimum because CPU-oracle rows are not
automatically Skia parity claims.

## Linear Result

| Ticket | Result | Evidence |
|---|---|---|
| FOR-33 M65 Kadre audit | Done in this PR scope | `reports/wgsl-pipeline/2026-06-01-m65-kadre-audit.md` |
| FOR-34 M65 frame-loop smoke | Done as headless/offscreen lane | `pipelineM65RuntimeSmoke` |
| FOR-35 M65 telemetry/artifacts | Done | `reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json` and PNG artifacts |
| FOR-36 M65 curated slots | Done with stable runtime refusals for M63/M64 replay | `reports/wgsl-pipeline/m65-runtime-smoke/slots.json` |
| FOR-37 M65 closeout | Done by this report and README/target update | This report |
| FOR-38 M66 ranking | Done | `reports/wgsl-pipeline/2026-06-01-m66-selection-ranking.md` |
| FOR-39/FOR-40/FOR-41 M66 rows | Done | `reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json` |
| FOR-42 M66 closeout | Done by this report and README/target update | This report |

## Dashboard Delta

| Counter | Before M65/M66 | After M65/M66 |
|---|---:|---:|
| Dashboard rows | 73 | 92 |
| `pass` | 52 | 68 |
| `expected-unsupported` | 21 | 24 |
| M66 rows | 0 | 19 |
| M66 `skia-upstream` rows | 0 | 6 |
| M66 `test-oracle` rows | 0 | 6 |
| M66 `cpu-oracle` rows | 0 | 7 |

## Readiness Accounting

| Area | Weight | Before | After | Score movement |
|---|---:|---:|---:|---|
| Rendering feature breadth | 30% | 6/10 | 6/10 | No new family denominator; M66 normalizes rows inside existing families. |
| Skia-like fidelity | 20% | 31/100 | 50/100 selected evidence | +3.8 weighted points. Minimum Skia-comparable subset is 37/100. |
| Real-time runtime | 20% | 1/10 | 4/10 | +6 weighted points from frame loop smoke, invalidation diagnostics, nonblank proof, and exportable telemetry. |
| Performance/cache readiness | 15% | 7/20 | 7/20 | No movement; M65 metrics are reporting-only. |
| PM/demo operability | 15% | 11/20 | 16/20 | +3.75 weighted points from runtime artifacts, M66 reports, PM bundle manifest, and family/reference counters. |

Weighted readiness moves from approximately 39% to approximately 53%.

The movement stays within the target caps: M65 contributes at most +6% and M66
contributes at most +8%.

## PM-Visible Evidence

- M65 runtime smoke report:
  `reports/wgsl-pipeline/2026-06-01-m65-runtime-smoke.md`
- M65 telemetry:
  `reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json`
- M66 selection report:
  `reports/wgsl-pipeline/2026-06-01-m66-selection-ranking.md`
- M66 generated contract:
  `reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json`
- PM bundle manifest includes `m65RuntimeSmoke` and `m66GmPromotionWave`.

## Non-Claims

- M65 does not claim Kadre-hosted live presentation.
- M65 does not claim WebGPU adapter timing, release FPS, input, live controls,
  or native packaging.
- M66 does not claim full Skia GM parity.
- CPU-oracle rows are breadth/refusal evidence and do not automatically count
  as Skia-comparable fidelity.
- No global visual threshold was lowered.
- No arbitrary SkSL, font shaping substitute, codec substitute, or alternate
  window shell was introduced.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineM65RuntimeSmoke pipelineM66GmPromotionWave pipelineSceneDashboardGate pipelinePmBundle
rtk ./gradlew --no-daemon pipelinePmBundle
rtk git diff --check
```

All commands passed locally.
