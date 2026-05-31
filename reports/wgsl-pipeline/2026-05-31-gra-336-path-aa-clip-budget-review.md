# GRA-336 Path AA / Clip Budget Review

Date: 2026-05-31
Scope: GRA-336 Path AA / clip budget

## Decision

No row is promoted in this pass.

The four inspected rows are still refusal or policy-sentinel rows. None has the
complete `pass` evidence required by
`.upstream/specs/wgsl-pipeline/11-conformance-dashboard-generation.md`: GPU
image, GPU diff, GPU stats, GPU route diagnostics with `fallbackReason=none`,
and a row-specific CPU/GPU/reference artifact set.

Changing any of these rows to `pass` from the current evidence would convert
copied refusal artifacts into a support claim. The current artifacts are useful
for policy visibility, but they are not enough to prove a bounded WebGPU Path
AA/clip promotion.

## Rows Inspected

| Row | Current status | Generator | GPU route | Fallback reason | Promotion decision |
|---|---|---|---|---|---|
| `path-aa-edge-budget-boundary` | `expected-unsupported` | static | `webgpu.coverage.refuse` | `coverage.edge-count-exceeded` | Keep as broad 256-edge budget sentinel. |
| `m52-closed-capped-hairlines-edge-budget` | `expected-unsupported` | `pipelineM52InventoryPromotionPack` | `webgpu.coverage.refuse` | `coverage.edge-count-exceeded` | Keep as closed hairline cap boundary until row-specific GPU artifacts exist. |
| `m53-complexclip-boundary-refusal` | `expected-unsupported` | `pipelineM53InventoryPromotionPack` | `webgpu.clip.complex-path.expected-unsupported` | `coverage.complex-clip-path-unsupported` | Keep as complex path clip refusal; this is not an edge-budget-only case. |
| `m54-dash-circle-boundary` | `expected-unsupported` | `pipelineM54HardFeatureDepthPack` | `webgpu.coverage.dash-circle.expected-unsupported` | `coverage.edge-count-exceeded` | Keep as dash/cap/join boundary; do not infer support from other dash tests. |

## Artifact Findings

`path-aa-edge-budget-boundary` is a static policy row. Its dashboard artifacts
are 16 x 16 placeholder/oracle images and its stats report `pixels=0`.

The M52, M53, and M54 rows are generated from compact contracts but derive their
images from base scenes instead of row-specific GM captures:

| Row | Derived from | PNG evidence present | Missing for `pass` |
|---|---|---|---|
| `m52-closed-capped-hairlines-edge-budget` | `path-aa-convexpaths-edge-budget` | `skia.png`, `cpu.png`, `cpu-diff.png` | `gpu.png`, `gpu-diff.png`, GPU stats with `fallbackReason=none`. |
| `m53-complexclip-boundary-refusal` | `path-aa-convexpaths-edge-budget` | `skia.png`, `cpu.png`, `cpu-diff.png` | `gpu.png`, `gpu-diff.png`, GPU stats with `fallbackReason=none`. |
| `m54-dash-circle-boundary` | `path-aa-dashing-edge-budget` | `skia.png`, `cpu.png`, `cpu-diff.png` | `gpu.png`, `gpu-diff.png`, GPU stats with `fallbackReason=none`. |

The generated route JSON files explicitly preserve the refusal decisions:

- `m52-closed-capped-hairlines-edge-budget`: `coverageKind=pathAA hairlineCaps=closed edgeBudget=exceeded source=ClosedCappedHairlinesGM`.
- `m53-complexclip-boundary-refusal`: `clip=complexPath source=ComplexClipGM`.
- `m54-dash-circle-boundary`: `pathAA=dashCircle budget=current source=DashCircleGM`.

## Geometry And Budget Facts

### `path-aa-edge-budget-boundary`

This row summarizes broad Path AA inventory remaining outside the M44 primitive
stroke promotion. M44 promoted only `StrokeRectGM` and `StrokeCircleGM` through
`webgpu.coverage.path-aa-stroke-primitive`; the broad edge-budget bucket stayed
expected unsupported with the global 256-edge budget unchanged.

There is no row-specific stroke, dash, or clip fixture here. Its purpose is to
keep the generic `coverage.edge-count-exceeded` policy visible.

### `m52-closed-capped-hairlines-edge-budget`

Source family: `closedcappedhairlines`.

Source GM facts:

- cap variants: butt, round, square;
- stroke width: hairline, `strokeWidth=0`;
- anti-aliasing: enabled;
- dash: none;
- per cap GM: 4 rows x 3 shapes: line, quad, cubic;
- contour modes: open and closed, on-pixel and 0.5px-shifted off-pixel;
- additional 4x grid and endpoint highlight drawing is present in the GM but is
  diagnostic overlay, not proof that the Path AA cap route is supported.

The dashboard row is an aggregate refusal derived from
`path-aa-convexpaths-edge-budget`. It does not contain row-specific GPU capture
or cap-sensitive diff/stat evidence. Promoting it would need separate evidence
for at least one cap contract, with closed-contour cap behavior asserted.

### `m53-complexclip-boundary-refusal`

Source family: `complexclip`.

Source GM facts:

- base draw path: rounded outer contour plus inner U-shaped hole;
- clip stack: two polygon path clips, `clipA` then `clipB`;
- operations: `Intersect` and `Difference`;
- inverse variants: four combinations across clip A and clip B;
- optional `saveLayer` variants exist;
- AA and BW clip variants exist;
- each stamp also draws path/clip hairline overlays for readability.

This row is blocked by complex path clip semantics, not only by edge count. The
current GPU route is `webgpu.clip.complex-path.expected-unsupported` with
`coverage.complex-clip-path-unsupported`. A promotion would need a
Geometry/Coverage-specific clip lowering slice that proves the selected clip op,
inverse behavior, saveLayer boundary if included, CPU/GPU/ref images, and diffs.

### `m54-dash-circle-boundary`

Source family: `dashcircle`.

Source GM facts:

- canvas: 900 x 1200;
- radius: 125;
- wedge counts: 6, 12, 36;
- dash patterns: `[1, 1]`, `[1, 3]`, `[1, 1, 3, 3]`, `[1, 3, 2, 4]`;
- reference stroke: 1px AA path segments in `0xFFbf3f7f`;
- dashed stroke: 10px AA circle path with `SkDashPathEffect`;
- reference arc spans per wedge column:
  - 6 wedges: 36 arc spans;
  - 12 wedges: 72 arc spans;
  - 36 wedges: 216 arc spans;
  - total reference spans across the GM: 324 before counting the dashed stroke
    expansion.

The 36-wedge column alone creates a dense dash/cap/join workload, and the full
GM combines analytic reference wedges plus path-effect-expanded dashed circle
strokes. Existing dash WebGPU tests prove narrower dash behavior, but this row
is specifically a dash-circle Path AA budget boundary. The current row has no
GPU image or diff for `DashCircleGM`, so `fallbackReason=none` cannot be
claimed.

## Required Follow-Up For A Real Promotion

A safe GRA-336 promotion should introduce a new bounded generated row, or
replace one of the refusal rows only after it can produce:

- a row-specific source test or report task;
- row-specific `skia.png`, `cpu.png`, `gpu.png`, `cpu-diff.png`, `gpu-diff.png`;
- CPU and GPU route JSON files with the selected edge count, stroke width, cap,
  join, dash pattern, clip op, and complex clip flags that define the contract;
- GPU stats with non-zero pixels, threshold, adapter metadata, and
  `fallbackReason=none`;
- an explicit inventory delta that does not raise `WEBGPU_PATH_AA_EDGE_BUDGET`
  or weaken `coverage.edge-count-exceeded` for out-of-scope rows.

The smallest likely promotion candidate is not one of the aggregate refusal rows
as-is. It should be a new bounded slice such as one closed hairline cap variant,
one simple dash-circle cell below the current budget, or one non-inverse
non-layer complex clip subset, each with its own generated artifacts and stable
route diagnostics.

## Validation

Commands run:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Results are recorded in the GRA-336 worker handoff.
