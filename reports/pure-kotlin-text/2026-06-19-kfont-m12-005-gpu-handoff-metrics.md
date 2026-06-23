# KFONT-M12-005 - GPU handoff telemetry

## Scope landed

- `reports/pure-kotlin-text/gpu-text-handoff-metrics.json` now records one
  deterministic advisory GPU handoff snapshot spanning the selected simple
  A8 route plus bounded refusal rows for SDF, outline, color, bitmap, SVG,
  upload-plan-missing, generation-stale, budget, nondeterministic-key, and
  CPU-rendered-texture cases.
- `reports/pure-kotlin-text/draw-text-run-upload-plan.json` now records one
  deterministic advisory `DrawTextRunPayload` upload-plan dump with stable
  artifact key hashes, upload dependency ordering, upload byte counts, reuse
  counters, and a bounded MaterialKey/no-`Sk*` leakage audit excerpt.
- The wave keeps telemetry advisory-only, preserves `dftext`, and does not
  promote GPU route support, executed upload claims, or release-gate status.

## Evidence

- `gpu-text-handoff-metrics.json` keeps `routeOutcome` split between selected
  and refused rows, preserves stable `text.gpu.*` / `unsupported.text.*`
  diagnostics, and records adapter/backend facts as bounded evidence only.
- `draw-text-run-upload-plan.json` binds the same fixture family to
  `material.materialKey`, `artifactKeyHashes`, and `uploadDependencyLabels`
  while keeping `findings: []` for the no-`Sk*` leakage audit excerpt.
- `font-claim-dashboard.json`, `font-telemetry-pm-bundle.json`,
  `fixture-evidence-manifest.json`, and `dump-evidence-index.json` now expose
  separate `GPU text handoff metrics`, `GPU text upload metrics`, and
  `GPU text route refusals` tracked-gap rows plus the checked-in PM bundle
  packaging for `gpu-text-handoff-metrics.json` and
  `draw-text-run-upload-plan.json`.
- `no-performance-release-gate-claim` remains explicit throughout this slice.

## Validation

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextHandoffMetricsEvidenceTest*'
rtk ./gradlew --no-daemon :font:core:test --tests '*FontTelemetrySchemaTest*'
rtk ./gradlew --no-daemon validateKfontM12001TelemetryPmEvidence
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining gate

No ticket-local gate remains for `KFONT-M12-005`. `dftext`, broader M11 GPU
route promotion, executed GPU upload claims, and any release-gate promotion
stay on their owning tickets and specs.
