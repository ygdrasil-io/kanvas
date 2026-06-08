# M90 StrokedLines Evidence Gate

Date: 2026-06-08

## Scope

`M90-PAA-3D-REF` is the row-specific evidence gate for `StrokedLinesGM` /
`skia-gm-strokedlines`.
The current M90 intake records only policy and historical signals, so support
evaluation remains blocked until a row-specific Skia reference, CPU/WebGPU
routes, render images, diff/stat artifacts, and performance artifacts exist.

## Current Status

- Row status: `expected-unsupported`
- Support claim: `False`
- Fallback: `coverage.stroked-lines.row-specific-artifacts-required`
- Present row-specific evidence items: `0`
- Missing row-specific evidence items: `10`
- Historical signals: `6`, all non-promotional

Historical StrokedLines CPU tests, the Kotlin GM source port, and historical
similarity floors remain provenance only. They do not replace the
`skia-gm-strokedlines` artifact bundle and do not update registry, dashboard,
threshold, edge-budget, or readiness state.

## Gate Artifact

The unresolved evidence requirement is tracked in:

```text
reports/wgsl-pipeline/scenes/generated/m90-strokedlines-row-specific-evidence-gate.json
```

The gate requires all ten row-specific artifacts under:

```text
reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokedlines/
```

Future support evaluation also requires CPU and WebGPU route evidence with
`fallbackReason=none`.

## Non-Claims

- No broad Path AA support claim.
- No broad stroke support claim.
- No Ganesh or Graphite port.
- No dynamic SkSL compiler, IR, or VM.
- No global threshold reduction.
- No support promotion from historical scores or a below-threshold/tolerance-only case.

## Validation

```text
rtk python3 scripts/validate_m90_strokedlines_evidence_gate.py --check-worktree-scope
rtk ./gradlew --no-daemon pipelineM90PathAaStrokedLinesEvidenceGate
rtk python3 scripts/m90_path_aa_strokedlines_evidence_intake.py
rtk ./gradlew --no-daemon pipelineM90PathAaStrokedLinesEvidenceIntake
rtk git diff --check
```
