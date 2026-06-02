# M88 Realtime Renderer RC2

Status: `pass`

M88 freezes the RC2 package for the realtime renderer. It collects the current dashboard, runtime, fidelity, performance, and limitation evidence into one reproducible PM handoff without claiming new broad rendering support.

## PM Scorecard

| Area | RC2 state | Evidence |
|---|---|---|
| API/demo surface | Frozen for RC2 | `api-surface.json` |
| Correctness gates | `pass` | `gate-freeze.json`, `pipelineSceneDashboardGate` |
| Support/refusal matrix | `21` pass, `5` expected-unsupported | `support-refusal-matrix.json` |
| Fidelity queue | `19` ranked candidates, `6` Skia-comparable support rows | M86 burn-down evidence |
| Runtime effect live editing | Selected SimpleRT only | M87 evidence |
| Native timing/cache | Reporting-only/candidate | M84/M85 evidence |

## Reproduce

```bash
rtk ./gradlew --no-daemon :kadre-runtime:pipelineM88ReleaseCandidate2 pipelinePmBundle
python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard
```

## Non-Claims

- No full Skia parity claim.
- No arbitrary SkSL support.
- No release-grade `frame.kadre-windowed` FPS gate.
- No observed broad runtime cache telemetry beyond M85's selected deterministic ledger.
- No window-surface screenshot/readback support.
