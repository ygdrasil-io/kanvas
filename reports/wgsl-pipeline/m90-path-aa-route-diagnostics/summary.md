# M90 Path AA Route Diagnostics

Status: generated evidence

This report adds CPU/GPU route diagnostics for the grouped dash, hairline, and stroke GM policy-refusal rows selected by `M90-PAA-1`. It does not promote support, change thresholds, or change the 256-edge WebGPU AA budget.

## Counters

- Diagnostic rows: `9`
- CPU route diagnostics: `9`
- GPU route diagnostics: `9`
- New support claims: `0`
- Readiness delta: `0.0`

## Diagnostics

### skia-gm-dashcubics

- Source GM: `DashCubicsGM`
- Fallback: `coverage.dash-cubic.row-specific-artifacts-required`
- CPU route: `cpu.path.dash-cubic.expected-unsupported`
- GPU route: `webgpu.path.dash-cubic.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashcubics/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashcubics/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-dashing

- Source GM: `DashingGM`
- Fallback: `coverage.dashing.row-specific-artifacts-required`
- CPU route: `cpu.path.dashing.expected-unsupported`
- GPU route: `webgpu.path.dashing.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashing/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashing/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-hairlines

- Source GM: `HairlinesGM`
- Fallback: `coverage.hairline.row-specific-artifacts-required`
- CPU route: `cpu.path.hairline.expected-unsupported`
- GPU route: `webgpu.path.hairline.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairlines/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairlines/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-hairmodes

- Source GM: `HairModesGM`
- Fallback: `coverage.hairmode.row-specific-artifacts-required`
- CPU route: `cpu.path.hairmode.expected-unsupported`
- GPU route: `webgpu.path.hairmode.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairmodes/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairmodes/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-scaledstrokes

- Source GM: `ScaledStrokesGM`
- Fallback: `coverage.scaled-stroke.row-specific-artifacts-required`
- CPU route: `cpu.path.scaled-stroke.expected-unsupported`
- GPU route: `webgpu.path.scaled-stroke.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-scaledstrokes/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-scaledstrokes/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-strokedlines

- Source GM: `StrokedLinesGM`
- Fallback: `coverage.stroked-lines.row-specific-artifacts-required`
- CPU route: `cpu.path.stroked-lines.expected-unsupported`
- GPU route: `webgpu.path.stroked-lines.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokedlines/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokedlines/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-strokerect

- Source GM: `StrokeRectGM`
- Fallback: `coverage.stroke-rect.row-specific-artifacts-required`
- CPU route: `cpu.path.stroke-rect.expected-unsupported`
- GPU route: `webgpu.path.stroke-rect.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerect/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerect/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-strokerects

- Source GM: `StrokeRectsGM`
- Fallback: `coverage.stroke-rects.row-specific-artifacts-required`
- CPU route: `cpu.path.stroke-rects.expected-unsupported`
- GPU route: `webgpu.path.stroke-rects.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerects/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerects/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

### skia-gm-thinstrokedrects

- Source GM: `ThinStrokedRectsGM`
- Fallback: `coverage.thin-stroked-rects.row-specific-artifacts-required`
- CPU route: `cpu.path.thin-stroked-rects.expected-unsupported`
- GPU route: `webgpu.path.thin-stroked-rects.expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-thinstrokedrects/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-thinstrokedrects/route-gpu.json`
- Support claim: `False`
- Policy-only: `True`

## Support Guard

- supportClaimAdded: `False`
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

- `rtk python3 scripts/m90_path_aa_route_diagnostics.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaRouteDiagnostics`
- `rtk git diff --check`
