# M90 Path AA REF Gate Closeout

Date: 2026-06-08

## Scope

`M90-PAA-3-REF-CLOSEOUT` is a coordination/visibility closeout only.
It links the now-materialized M90 Path AA candidate REF gates and harnesses
without adding rendering support claims.

This closeout does not replace row-specific Skia reference, CPU/WebGPU route,
render, diff/stat, or performance artifacts. Each row still needs its own
artifact bundle and `fallbackReason=none` evidence before support evaluation.

## Counters

- Candidate rows: `9`
- Rows with REF gate or harness: `9`
- Row-specific gate artifacts: `8`
- Hairlines harness artifacts: `2`
- Support claims: `0`
- New support claims: `0`
- Readiness delta: `0.0`
- Dashboard promotions: `0`
- Threshold changes: `0`
- Edge-budget changes: `0`

## Rows

| Order | Row | REF artifact | Status | Support claim |
|---:|---|---|---|---|
| 1 | `HairlinesGM` / `skia-gm-hairlines` | `artifact-harness` plus `adapter-backed-gate` | `expected-unsupported` | `False` |
| 2 | `StrokeRectGM` / `skia-gm-strokerect` | `m90-strokerect-row-specific-evidence-gate.json` | `dependency-gated` | `False` |
| 3 | `ThinStrokedRectsGM` / `skia-gm-thinstrokedrects` | `m90-thinstrokedrects-row-specific-evidence-gate.json` | `dependency-gated` | `False` |
| 4 | `StrokedLinesGM` / `skia-gm-strokedlines` | `m90-strokedlines-row-specific-evidence-gate.json` | `dependency-gated` | `False` |
| 5 | `StrokeRectsGM` / `skia-gm-strokerects` | `m90-strokerects-row-specific-evidence-gate.json` | `dependency-gated` | `False` |
| 6 | `HairModesGM` / `skia-gm-hairmodes` | `m90-hairmodes-row-specific-evidence-gate.json` | `dependency-gated` | `False` |
| 7 | `ScaledStrokesGM` / `skia-gm-scaledstrokes` | `m90-scaledstrokes-row-specific-evidence-gate.json` | `dependency-gated` | `False` |
| 8 | `DashingGM` / `skia-gm-dashing` | `m90-dashing-row-specific-evidence-gate.json` | `dependency-gated` | `False` |
| 9 | `DashCubicsGM` / `skia-gm-dashcubics` | `m90-dashcubics-row-specific-evidence-gate.json` | `dependency-gated` | `False` |

## Artifact Links

Hairlines is represented by both:

```text
reports/wgsl-pipeline/scenes/generated/m90-hairlines-artifact-harness.json
reports/wgsl-pipeline/scenes/generated/m90-hairlines-adapter-backed-gate.json
```

B-I rows are represented by:

```text
reports/wgsl-pipeline/scenes/generated/m90-strokerect-row-specific-evidence-gate.json
reports/wgsl-pipeline/scenes/generated/m90-thinstrokedrects-row-specific-evidence-gate.json
reports/wgsl-pipeline/scenes/generated/m90-strokedlines-row-specific-evidence-gate.json
reports/wgsl-pipeline/scenes/generated/m90-strokerects-row-specific-evidence-gate.json
reports/wgsl-pipeline/scenes/generated/m90-hairmodes-row-specific-evidence-gate.json
reports/wgsl-pipeline/scenes/generated/m90-scaledstrokes-row-specific-evidence-gate.json
reports/wgsl-pipeline/scenes/generated/m90-dashing-row-specific-evidence-gate.json
reports/wgsl-pipeline/scenes/generated/m90-dashcubics-row-specific-evidence-gate.json
```

The aggregate closeout artifact is:

```text
reports/wgsl-pipeline/scenes/generated/m90-path-aa-ref-gate-closeout.json
```

## Non-Claims

- No row support claim.
- No new rendering support claim.
- No registry promotion.
- No dashboard promotion.
- No readiness promotion.
- No global threshold change.
- No edge-budget change.
- No broad Path AA support claim.
- No broad dash support claim.
- No broad hairline support claim.
- No broad stroke support claim.
- No Ganesh or Graphite port.
- No dynamic SkSL compiler, IR, or VM.
- No support promotion from historical scores or a below-threshold/tolerance-only case.

## Next Handoff

The active next handoff remains `M90-PAA-3A-REF` / `skia-gm-hairlines`.
`supportClaimAllowed=false` and `promotionAllowedWithoutEvidence=false`.

The required scope remains row-specific HairlinesGM reference, CPU/WebGPU
`fallbackReason=none` route, render, diff/stat, and performance evidence before
any support evaluation.

## Validation

```text
rtk python3 scripts/validate_m90_path_aa_ref_gate_closeout.py --check-worktree-scope
rtk ./gradlew --no-daemon pipelineM90PathAaRefGateCloseout
rtk git diff --check
```
