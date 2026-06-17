# GPU Renderer M9-003 PM Readiness Dashboard Integration

Date: 2026-06-17
Branch: `codex/kgpu-m9-003-pm-readiness-dashboard`
Ticket: `KGPU-M9-003`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M9-003 | `done` | Added `GPURendererReadinessDashboardIntegrator`, `gpuRendererM9ReadinessPmEvidenceBundle`, PM bundle validator/injection, and scene diagnostic updates. | No remaining gate for this reporting-only dashboard integration. Readiness movement, release-blocking gates, product activation, correctness-from-performance, derived-cache-as-observed, and dashboard promotion remain disallowed. |

## Evidence

- `GPURendererReadinessDashboardTest` records the `gpu-renderer.readiness` row
  with separate correctness, activation, performance, cache, and release rows.
- The dashboard stays `classification=PolicyGated` with `readinessDelta=0.0`,
  `releaseBlocking=false`, and `productRouteActivated=false`.
- The M9 evidence exporter writes:
  - `gpu-renderer-readiness-dashboard-lines.txt`
  - `gpu-renderer-readiness-dashboard-summary.json`
  - `pm-bundle-manifest-entry.json`
- `validate_gpu_renderer_m9_readiness_pm_evidence_bundle.py` validates the
  summary, dump-line non-claims, artifact hash, sidecar manifest entry, and
  `pipelinePmBundle` injection.
- `pipelinePmBundle` now depends on
  `:gpu-renderer:gpuRendererM9ReadinessPmEvidenceBundle` and finalizes with
  `injectGpuRendererM9ReadinessPmEvidenceIntoPmBundle` after the R6 injection.
- The generated root PM manifest includes `gpuRendererM9ReadinessPmEvidence`
  under `release/gpu-renderer-m9-readiness-pm-evidence/`.
- `pm-readiness-freeze-board` diagnostics now expose
  `missingGate=none`, `pipelinePmBundleUpdated=true`, and
  `pmManifestKey=gpuRendererM9ReadinessPmEvidence`.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.telemetry.GPURendererReadinessDashboardTest'
rtk python3 -m unittest scripts/test_validate_gpu_renderer_m9_readiness_pm_evidence_bundle.py
rtk ./gradlew --no-daemon :gpu-renderer:gpuRendererM9ReadinessPmEvidenceBundle
rtk python3 scripts/validate_gpu_renderer_m9_readiness_pm_evidence_bundle.py . gpu-renderer/build/reports/gpu-renderer-m9-readiness-pm-evidence
rtk ./gradlew --no-daemon pipelinePmBundle
rtk rg -n '"gpuRenderer(R6FirstRoutePmEvidence|M9ReadinessPmEvidence)"' build/reports/wgsl-pipeline-pm-bundle/manifest.json
```

The targeted RED failed because `GPURendererReadinessDashboardIntegrator` and
`GPURendererReadinessDashboardRow` did not exist. The validator RED failed
because `validate_gpu_renderer_m9_readiness_pm_evidence_bundle.py` did not
exist. The review regression RED rejected two missing safeguards: nonzero
dashboard-line `readinessDelta` tokens were not parsed generally, and manifest
output order could be masked by sorted keys. The validator suite now covers
all dashboard-line readiness and release/product movement tokens, preserves
R6-before-M9 manifest order, and reinserts stale M9 entries after R6.

Current status count after independent review:

```text
blocked 4
done 42
proposed 9
review 0
```

## Review

Independent review `019ed60a-90f1-73c3-8ecd-59666e982a64` found two P2
issues, both fixed before re-review:

- dashboard-line tokens are parsed generally, so any `readinessDelta` other
  than `0.0`, `releaseBlocking=true`, or `productRouteActivated=true` fails
  validation even when the summary hash is recalculated;
- PM bundle injection writes the root manifest without key sorting, preserving
  existing manifest order and appending `gpuRendererM9ReadinessPmEvidence`
  after `gpuRendererR6FirstRoutePmEvidence`.

Re-review then found one additional P2, also fixed:

- a stale `gpuRendererM9ReadinessPmEvidence` entry that already existed before
  R6 could keep its old position when updated; injection now removes any stale
  M9 entry before appending the rebuilt entry, and the validator suite covers
  this stale-manifest case.

Final re-review found no remaining P0/P1/P2 blockers. Review scope:

- dashboard rows separate correctness, activation, performance, cache, and
  release state;
- `pipelinePmBundle` injection is deterministic and does not overwrite R6 or
  other manifest entries;
- all PM rows keep `readinessDelta=0.0`, `releaseBlocking=false`, and
  `productRouteActivated=false`;
- cache reporting does not treat derived/reporting-only evidence as observed;
- frame-gate candidate evidence does not become release-blocking.

## Non-Claims

- No readiness delta.
- No release-blocking gate.
- No product activation.
- No correctness support inferred from performance evidence.
- No derived cache telemetry counted as observed.
- No dashboard row promotes readiness.
