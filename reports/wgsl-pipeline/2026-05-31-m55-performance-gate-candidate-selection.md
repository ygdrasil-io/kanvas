# M55 Performance Gate Candidate Selection

Result: pass.

M55 selects seven representative scene rows for a non-blocking performance gate
candidate. The selection exercises stable baseline coverage, shader/gradient
coverage, bitmap local-matrix sampling, blend/composition, one M54 Path AA/clip
row, and M54 composition-depth rows. This is not a rendering support expansion:
the dashboard row count and support statuses remain unchanged.

Selection contract:
`reports/wgsl-pipeline/performance/m55-performance-gate-candidates.json`

Generated candidate output:
`build/reports/wgsl-pipeline-performance-warnings/m55-performance-gate-candidate.md`

## Selected Rows

| Scene | Family | Decision | CPU eligibility | GPU/cache eligibility | Variance risk | Owner |
|---|---|---|---|---|---|---|
| `solid-rect` | simple fill baseline | deferred | eligible, no measured M55 payload | eligible, no measured M55 payload | low | Kanvas rendering release owner |
| `linear-gradient-rect` | gradient/shader | deferred | estimated only | estimated only | medium | Kanvas rendering release owner |
| `src-over-stack` | blend/composition | measured now | measured | measured | medium | Kanvas rendering release owner |
| `bitmap-shader-local-matrix` | bitmap/local matrix | measured now | measured | measured | medium | Kanvas rendering release owner |
| `m54-simple-aa-clip` | M54 Path AA / clip | deferred | eligible, benchmark not approved | eligible, adapter baseline not approved | high | Kanvas rendering release owner |
| `m54-src-over-composition-depth` | M54 runtime / paint composition | measured now | measured | measured | medium | Kanvas rendering release owner |
| `m54-local-matrix-blend-composition` | M54 bitmap/local matrix + paint composition | measured now | measured | measured | medium | Kanvas rendering release owner |

## Excluded Rows

| Scene | Reason |
|---|---|
| `m54-imagefilter-transformed-affine` | No approved measured image-filter v2 benchmark baseline exists in M55. |
| `m54-runtime-imagefilter-descriptor` | Runtime image-filter descriptor timing remains estimated on the base row and is not promoted to measured. |

## Score Preconditions

M55 can move readiness to 95% only if:

- 6-8 selected rows have explicit candidate status output;
- measured rows use only existing measured payloads with host/JDK/backend,
  baseline owner, sample count, regression label, and reporting-only gate mode;
- missing measured lanes are `deferred` or `warn`, never hidden as pass;
- `pipelinePerformanceTrendWarnings` and `pipelinePmBundle` expose the M55
  candidate data;
- dashboard gates remain 0 `tracked-gap` and 0 `fail`;
- no release-blocking performance gate is enabled in M55.

## Validation

- `rtk git diff --check`
- `rtk ./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate pipelinePerformanceTrendWarnings`
