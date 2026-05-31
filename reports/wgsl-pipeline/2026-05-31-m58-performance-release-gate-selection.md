# M58 Performance Release Gate Selection

Result: pass.

M58 converts the M55 warning-only performance candidate into a narrow
release-blocking gate for measured rows only. The gate contract is:
`reports/wgsl-pipeline/performance/m58-performance-release-gate.json`.

Generated output:
`build/reports/wgsl-pipeline-performance-release-gate/m58-performance-release-gate.md`.

## Selection

| Scene | Family | M58 status | CPU lane | GPU/cache lane | Owner |
|---|---|---|---|---|---|
| `src-over-stack` | blend/composition | blocking measured | blocking measured | blocking measured | Kanvas rendering release owner |
| `bitmap-shader-local-matrix` | bitmap/local matrix | blocking measured | blocking measured | blocking measured | Kanvas rendering release owner |
| `m54-src-over-composition-depth` | M54 runtime / paint composition | blocking measured | blocking measured | blocking measured | Kanvas rendering release owner |
| `m54-local-matrix-blend-composition` | M54 bitmap/local matrix + paint composition | blocking measured | blocking measured | blocking measured | Kanvas rendering release owner |
| `solid-rect` | simple fill baseline | not measured | non-blocking missing | non-blocking missing | Kanvas rendering release owner |
| `linear-gradient-rect` | gradient/shader | not measured | non-blocking estimated | non-blocking estimated | Kanvas rendering release owner |
| `m54-simple-aa-clip` | M54 Path AA / clip | not measured | non-blocking missing | non-blocking missing | Kanvas rendering release owner |

## Excluded Rows

| Scene | Reason |
|---|---|
| `m54-imagefilter-transformed-affine` | No approved measured image-filter v2 benchmark baseline exists in M58. |
| `m54-runtime-imagefilter-descriptor` | Runtime image-filter descriptor timing remains estimated on the base row and is not promoted to measured. |

## Acceptance

- `pipelinePerformanceReleaseGate` writes JSON and Markdown under
  `build/reports/wgsl-pipeline-performance-release-gate/`.
- The gate fails when a selected measured lane lacks required measured metadata
  or exceeds its explicit median or p95 threshold.
- The negative fixture
  `-Pkanvas.performance.releaseGate.negativeFixture=true` proves that failure
  path without changing checked-in baseline data.
- Estimated and missing lanes are reported as `not-measured` and are not
  release-blocking measured evidence.
- The PM bundle exposes M58 counters and report links.

## Linear

GRA-345, GRA-346, GRA-347, GRA-348, GRA-349, and GRA-350 are covered by the
selection contract, release gate task, threshold policy, PM bundle exposure,
non-claims, and closeout reports.
