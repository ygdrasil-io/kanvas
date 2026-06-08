# M90 Path AA Candidate Readiness

Status: generated evidence

This report evaluates the M90-PAA-3 dash, hairline, and stroke candidates for readiness only. It does not promote rendering support because every candidate is still policy-only and lacks the required row-specific evidence.

## Counters

- Candidate rows: `9`
- Ready for promotion rows: `0`
- Blocked by missing evidence rows: `9`
- New support claims: `0`
- Readiness delta: `0.0`

## Required Promotion Evidence

- `row-specific Skia reference`
- `CPU route evidence with fallbackReason=none`
- `WebGPU route evidence with fallbackReason=none`
- `CPU/GPU diff/stat artifacts`
- `performance impact evidence`

## Ranking Policy

- Source: `scripts/m90_path_aa_candidate_readiness.py`
- Contract: M90-PAA-3 readiness ranking is a local PM ordering over the selected policy rows; it does not change registry support status or selection membership.
- Row order: `skia-gm-hairlines, skia-gm-strokerect, skia-gm-thinstrokedrects, skia-gm-strokedlines, skia-gm-strokerects, skia-gm-hairmodes, skia-gm-scaledstrokes, skia-gm-dashing, skia-gm-dashcubics`

## Candidate Ranking

### 1. skia-gm-hairlines

- Source GM: `HairlinesGM`
- Candidate kind: `bounded-hairline`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.hairline.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairlines/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairlines/route-gpu.json`
- Promotion ticket: `M90-PAA-3A`
- Notes: Smallest hairline candidate by route taxonomy, but still policy-only and missing all promotion evidence.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 2. skia-gm-strokerect

- Source GM: `StrokeRectGM`
- Candidate kind: `bounded-stroke-rect`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.stroke-rect.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerect/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerect/route-gpu.json`
- Promotion ticket: `M90-PAA-3B`
- Notes: Smallest stroke-rect candidate after hairline; still lacks row-specific reference, pass routes, diff/stat, and perf evidence.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 3. skia-gm-thinstrokedrects

- Source GM: `ThinStrokedRectsGM`
- Candidate kind: `thin-stroke-rect`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.thin-stroked-rects.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-thinstrokedrects/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-thinstrokedrects/route-gpu.json`
- Promotion ticket: `M90-PAA-3C`
- Notes: Thin rect strokes need subpixel coverage proof before any support evaluation.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 4. skia-gm-strokedlines

- Source GM: `StrokedLinesGM`
- Candidate kind: `bounded-stroked-lines`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.stroked-lines.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokedlines/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokedlines/route-gpu.json`
- Promotion ticket: `M90-PAA-3D`
- Notes: Line stroke caps require row-specific CPU/GPU agreement before promotion can be evaluated.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 5. skia-gm-strokerects

- Source GM: `StrokeRectsGM`
- Candidate kind: `multi-stroke-rects`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.stroke-rects.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerects/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerects/route-gpu.json`
- Promotion ticket: `M90-PAA-3E`
- Notes: Multiple stroked rects broaden the slice and are not first candidate material without row-specific evidence.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 6. skia-gm-hairmodes

- Source GM: `HairModesGM`
- Candidate kind: `hairline-paint-mode`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.hairmode.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairmodes/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairmodes/route-gpu.json`
- Promotion ticket: `M90-PAA-3F`
- Notes: Paint/blend mode interaction makes this later than the plain hairline candidate.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 7. skia-gm-scaledstrokes

- Source GM: `ScaledStrokesGM`
- Candidate kind: `scaled-stroke`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.scaled-stroke.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-scaledstrokes/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-scaledstrokes/route-gpu.json`
- Promotion ticket: `M90-PAA-3G`
- Notes: Transform-dependent stroke scale should follow a simpler stroke slice.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 8. skia-gm-dashing

- Source GM: `DashingGM`
- Candidate kind: `dash-stroke`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.dashing.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashing/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashing/route-gpu.json`
- Promotion ticket: `M90-PAA-3H`
- Notes: Dash intervals, caps, and joins broaden the slice; edge-budget refusal proof remains separate.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 9. skia-gm-dashcubics

- Source GM: `DashCubicsGM`
- Candidate kind: `dash-cubic`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `coverage.dash-cubic.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashcubics/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashcubics/route-gpu.json`
- Promotion ticket: `M90-PAA-3I`
- Notes: Cubic dash behavior is the broadest candidate in this set and must stay after bounded hairline/stroke proof.
- Missing evidence:
  - `row-specific Skia reference`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

## Next Recommended Ticket

- ID: `M90-PAA-3A`
- Row: `skia-gm-hairlines`
- Scope: Collect row-specific Skia reference, CPU/GPU fallbackReason=none route evidence, diff/stat artifacts, and performance impact for HairlinesGM before any support evaluation.
- Support claim allowed: `False`
- Promotion allowed without evidence: `False`

## Support Guard

- supportClaimAdded: `False`
- readinessMoved: `False`
- policyOnlyPromoted: `False`
- thresholdChanged: `False`
- edgeBudgetChanged: `False`
- belowThresholdCountedAsProductionGap: `False`
- broadPathAASupport: `False`
- broadDashSupport: `False`
- broadHairlineSupport: `False`
- broadStrokeSupport: `False`
- ganeshPort: `False`
- graphitePort: `False`

## Validation Commands

- `rtk python3 scripts/m90_path_aa_candidate_readiness.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaCandidateReadiness`
- `rtk git diff --check`
