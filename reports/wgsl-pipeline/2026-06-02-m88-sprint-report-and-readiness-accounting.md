# M88 Sprint Report And Readiness Accounting

Date: 2026-06-02

Status: complete pending PR/merge.

## Summary

M88 freezes the realtime renderer RC2 package. The sprint adds a generated RC2
evidence pack under `reports/wgsl-pipeline/m88-realtime-rc2/` and wires it into
the PM bundle manifest.

This is a release-candidate packaging and evidence-freeze sprint. It does not
add new broad rendering support, Skia parity, arbitrary SkSL, release-grade
windowed FPS, or observed broad runtime cache telemetry.

## Linear Scope

| Issue | Scope | Status |
|---|---|---|
| `FOR-104` | M88 epic: realtime renderer RC2 | Complete in branch |
| `FOR-174` | Freeze runtime/demo API surface | Complete |
| `FOR-175` | Freeze correctness, evidence, and performance gates | Complete |
| `FOR-176` | Generate limitation and dependency matrix | Complete |
| `FOR-177` | Produce reproducible RC2 PM package | Complete |
| `FOR-178` | Review, PR, CI, merge, and Linear closeout | Review addressed; pending PR/merge |

## Evidence

Generated artifacts:

- `reports/wgsl-pipeline/m88-realtime-rc2/rc2-evidence.json`
- `reports/wgsl-pipeline/m88-realtime-rc2/rc2-evidence.md`
- `reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json`
- `reports/wgsl-pipeline/m88-realtime-rc2/gate-freeze.json`
- `reports/wgsl-pipeline/m88-realtime-rc2/api-surface.json`
- `reports/wgsl-pipeline/m88-realtime-rc2/pm-demo-script.md`
- `reports/wgsl-pipeline/m88-realtime-rc2/release-notes.md`

PM bundle entry:

- `m88ReleaseCandidate2`

## RC2 Counters

| Counter | Value |
|---|---:|
| Dashboard rows | 26 |
| Pass rows | 21 |
| Expected unsupported rows | 5 |
| Fail rows | 0 |
| Tracked-gap rows | 0 |
| Generated rows | 26 |
| Adapter-backed rows | 17 |
| M86 ranked fidelity candidates | 19 |
| M86 Skia-comparable support rows | 6 |

## Gate Freeze

Blocking or selected gates:

- `pipelineSceneDashboardGate`: blocking correctness gate.
- `pipelinePmBundle`: blocking PM bundle packaging gate.
- `:kadre-runtime:pipelineM88ReleaseCandidate2`: blocking RC2 evidence generation.
- `pipelinePerformanceReleaseGate`: still blocks only the selected M59 measured rows.

Reporting-only or candidate gates:

- `m67 frame.headless-webgpu`: candidate.
- `m84 frame.kadre-windowed`: reporting-only.
- `m85 resource/cache ledger`: reporting-only.

## Readiness Accounting

Readiness remains unchanged.

| Field | Value |
|---|---:|
| Before | 67.75% |
| After | 67.75% |
| Delta | 0.00% |

Reason: M88 freezes and packages the release-candidate evidence. It does not
change a counted denominator for rendering breadth, Skia-like fidelity,
real-time runtime capability, or measured performance/cache readiness.

## Validation

```bash
./gradlew --no-daemon :kadre-runtime:test --tests org.skia.kadre.runtime.M88ReleaseCandidate2Test :kadre-runtime:pipelineM88ReleaseCandidate2
./gradlew --no-daemon :kadre-runtime:test --tests org.skia.kadre.runtime.M88ReleaseCandidate2Test :kadre-runtime:validateM88ReleaseCandidate2
./gradlew --no-daemon pipelinePmBundle
python3 -m json.tool reports/wgsl-pipeline/m88-realtime-rc2/rc2-evidence.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m88-realtime-rc2/gate-freeze.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m88-realtime-rc2/api-surface.json >/dev/null
```

The Gradle validation passed after initializing the Kadre submodule from
`external/poc-koreos`.

Review follow-up:

- independent review found that `pipelinePmBundle` did not validate M88 and
  that M84/M85 could be treated as safe if their source files were absent;
- fixed by adding `:kadre-runtime:validateM88ReleaseCandidate2`, making
  `pipelinePmBundle` depend on it, and requiring expected M84/M85 pack/gate
  fields before M88 can report `pass`.

## Non-Claims

- No full Skia parity.
- No Ganesh or Graphite port.
- No SkSL compiler, IR, or VM.
- No arbitrary SkSL runtime-effect support.
- No release-grade `frame.kadre-windowed` FPS gate.
- No broad observed WebGPU runtime cache telemetry.
- No window-surface screenshot/readback support.
