# M59 Performance Release Gate Selection

Result: pass.

M59 replaces the M58 measured-lane-only contract with a final selected-target
contract: every selected lane is release-blocking and must carry measured
payload metadata. Estimated and missing payloads fail the final target instead
of being counted as measured evidence.

The gate contract is
`reports/wgsl-pipeline/performance/m59-performance-release-gate.json`. The
generated output is
`build/reports/wgsl-pipeline-performance-release-gate/m59-performance-release-gate.md`.

| Row | Family | CPU lane | GPU/cache lane | Owner |
|---|---|---|---|---|
| `src-over-stack` | blend/composition | blocking measured | blocking measured | Kanvas rendering release owner |
| `bitmap-shader-local-matrix` | bitmap/local matrix | blocking measured | blocking measured | Kanvas rendering release owner |
| `m54-src-over-composition-depth` | M54 runtime / paint composition | blocking measured | blocking measured | Kanvas rendering release owner |
| `m54-local-matrix-blend-composition` | M54 bitmap/local matrix + paint composition | blocking measured | blocking measured | Kanvas rendering release owner |
| `solid-rect` | simple fill baseline | blocking measured | blocking measured | Kanvas rendering release owner |
| `linear-gradient-rect` | gradient/shader | blocking measured | blocking measured | Kanvas rendering release owner |
| `m54-simple-aa-clip` | M54 Path AA / clip | blocking measured | blocking measured | Kanvas rendering release owner |

Thresholds keep the M58 policy: median may increase by 15%, p95 may increase by
20%, and sample count must be at least 30. The M59-added rows derive thresholds
from their M59 measured payloads.

Final gate counters from validation:

| Counter | Value |
|---|---:|
| Selected rows | 7 |
| Pass rows | 7 |
| Not-measured rows | 0 |
| Measured release-blocking lanes | 14 |
| Not-measured lanes | 0 |
| Blocking failures | 0 |

Non-claims: this selection does not expand rendering support, broad Skia GM
parity, broad Path AA, arbitrary image-filter DAG, font, codec, emoji, shaping,
arbitrary SkSL, Graphite, or Ganesh scope.
