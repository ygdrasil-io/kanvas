# M54 Warning-Only Performance Evidence

Date: 2026-05-31
Milestone: M54
Linear epic: GRA-317
Ticket: GRA-322

## Scope

M54 attaches warning-only performance metadata only where selected hard feature
rows can inherit credible measured payloads from existing measured generated
rows. No estimated metric is promoted to measured, and no performance threshold
becomes release-blocking.

## Rows With Measured Warning Payloads

| M54 scene id | Source measured row | CPU status | GPU status | Gate policy |
|---|---|---|---|---|
| `m54-src-over-composition-depth` | `src-over-stack` | `measured` | `measured` | warning-only / reporting-only |
| `m54-local-matrix-blend-composition` | `bitmap-shader-local-matrix` | `measured` | `measured` | warning-only / reporting-only |

The M54 materialization task copies the base CPU/GPU performance JSON artifacts
into each M54 row artifact directory and rewrites `rawMetrics` to the M54
artifact path. The payloads retain host, JDK, backend/adapter, warm/cold context,
baseline, regression label, owner, quarantine, and rollback policy metadata
from the measured source rows.

## Deferred Measurements

| Candidate | Reason |
|---|---|
| `m54-imagefilter-transformed-affine` | No approved measured image-filter v2 benchmark baseline exists. |
| `m54-matrix-imagefilter-affine` | No approved measured matrix image-filter benchmark baseline exists. |
| `m54-simple-aa-clip` | Clip/coverage timing needs a dedicated benchmark before claiming measured trend evidence. |
| `m54-rrect-clip-drawpaint` | RRect clip timing is correctness evidence only in M54. |
| `m54-runtime-imagefilter-descriptor` | Runtime descriptor timing remains estimated on the base row and is not promoted to measured. |
| `m54-compose-colorfilter-paint` | Color-filter composition has correctness evidence but no measured trend payload in M54. |

## Non-Claims

- No release-blocking performance gate is added.
- Estimated metrics remain informational and do not move readiness.
- Adapter-missing or environment-mismatched future measurements must use stable
  warning/unavailable policy, not failures.
- Correctness readiness remains separate from performance readiness.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass.
