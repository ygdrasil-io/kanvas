# M54 Hard Feature Depth Pack Evidence

Date: 2026-05-31
Milestone: M54
Linear epic: GRA-317
Tickets: GRA-319, GRA-320, GRA-321

## Scope

M54 promotes selected hard feature depth candidates into generated dashboard
evidence. The rows are derived from existing generated scene evidence, but each
row has its own GM inventory id, route semantics, fallback policy, tags, and
M54 derivation contract.

This pack does not claim broad Skia GM parity, broad arbitrary image-filter DAG
support, broad Path AA support, arbitrary SkSL, or dependency-gated font/codec/
emoji/shaping/SDF/LCD/glyph-mask support.

## Promotion Path

`pipelineM54HardFeatureDepthPack` reads
`reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json` and
materializes generated rows and artifacts under
`build/reports/wgsl-pipeline-m54-generated/`.

`pipelineGeneratedSceneExport` merges base generated rows, M52 rows, M53 rows,
and M54 rows before `pipelineSceneDashboard` and `pipelineSceneDashboardGate`
validate the dashboard.

## Promoted Rows

| Scene id | Inventory id | Family | Status | Contract |
|---|---|---|---|---|
| `m54-imagefilter-transformed-affine` | `skia-gm-imagefilterstransformed` | bounded image-filter v2 | `pass` | Affine MatrixTransform image-filter subset with one explicit layer scratch prepass and final composite. |
| `m54-matrix-imagefilter-affine` | `skia-gm-matriximagefilter` | bounded image-filter v2 | `pass` | Matrix image-filter affine subset using existing image-filter layer ownership semantics. |
| `m54-imagefilters-graph-boundary` | `skia-gm-imagefiltersgraph` | bounded image-filter v2 | `expected-unsupported` | Broad graph or picture-prepass image-filter shapes remain explicit with `image-filter.dag-or-picture-prepass-required`. |
| `m54-simple-aa-clip` | `skia-gm-simpleaaclip` | Path AA / clip depth | `pass` | Simple AA clip subset inside current WebGPU coverage and edge-budget policy. |
| `m54-rrect-clip-drawpaint` | `skia-gm-rrectclipdrawpaint` | Path AA / clip depth | `pass` | RRect clip plus drawPaint subset with explicit CPU/GPU clip route diagnostics. |
| `m54-dash-circle-boundary` | `skia-gm-dashcircle` | Path AA / clip depth | `expected-unsupported` | Dash circle Path AA remains an edge-budget boundary with stable `coverage.edge-count-exceeded`. |
| `m54-runtime-imagefilter-descriptor` | `skia-gm-runtimeimagefilter` | runtime / paint composition | `pass` | Registered runtime image-filter descriptor subset; no SkSL compiler, IR, or VM. |
| `m54-compose-colorfilter-paint` | `skia-gm-composecolorfilter` | runtime / paint composition | `pass` | Color-filter composition over paint/shader route with explicit PipelineIR order. |
| `m54-src-over-composition-depth` | `skia-gm-xfermodes` | runtime / paint composition | `pass` | SrcOver composition depth row with warning-only measured performance carried from the measured base row. |
| `m54-local-matrix-blend-composition` | `skia-gm-localmatriximageshader` | runtime / paint composition | `pass` | Local-matrix bitmap shader plus blend composition row with warning-only measured performance carried from the measured base row. |

## Counters

| Signal | Count |
|---|---:|
| M54 selected candidates | 13 |
| M54 promoted generated rows | 10 |
| M54 promoted `pass` rows | 8 |
| M54 promoted `expected-unsupported` rows | 2 |
| Hard feature families covered | 3 |
| M54 warning-only performance rows | 2 |
| New `tracked-gap` rows | 0 |
| New `fail` rows | 0 |

## Family Evidence

| Family | Rows | Evidence boundary |
|---|---:|---|
| bounded image-filter v2 | 3 | Two affine/prepass pass rows plus one graph/picture-prepass refusal. |
| Path AA / clip depth | 3 | Two bounded AA/clip pass rows plus one stable edge-budget refusal. |
| runtime / paint composition | 4 | Registered runtime descriptor, color-filter paint composition, SrcOver depth, and local-matrix blend composition. |

## Warning-Only Performance

Two M54 runtime/paint composition rows carry measured trend payloads from their
measured base rows:

| Scene id | Source measured row | Policy |
|---|---|---|
| `m54-src-over-composition-depth` | `src-over-stack` | Warning-only, reporting-only. |
| `m54-local-matrix-blend-composition` | `bitmap-shader-local-matrix` | Warning-only, reporting-only. |

The copied payloads retain host/JDK/backend/adapter, baseline, regression, and
owner policy metadata, but do not become release-blocking gates.

## Gate And Bundle Metadata

`pipelineSceneDashboardGate` reports M54 row and family counters and validates
that M54 pass rows have GPU evidence plus `fallbackReason=none`, while M54
expected-unsupported rows retain stable non-`none` fallback reasons.

`pipelinePmBundle` exposes M54 counters in `manifest.json` under
`m54HardFeatureDepth`, including selected/promoted/rejected counts, family
counters, promoted row details, performance warning rows, and rejected/deferred
details.

## Rejected Or Deferred

| Inventory id | Reason |
|---|---|
| `skia-gm-animatedbackdropblur` | Codec/animation dependency remains gated. |
| `skia-gm-animatedimageblurs` | Animated image decode remains dependency-gated. |
| `skia-gm-blurbigsigma` | Large-sigma blur is a stress case, not the bounded v2 proof. |
| `skia-gm-imagefiltersstroked` | Combines image-filter and Path AA stroke breadth before base routes are separately proven. |
| `skia-gm-localmatriximagefilter` | Deferred behind clearer affine MatrixTransform and crop ownership rows. |
| `skia-gm-xfermodeimagefilter` | Crosses image-filter DAG and blend/xfermode breadth too early. |
| `skia-gm-dashcubics` | Broad dashed cubic coverage remains edge-budget gated. |
| `skia-gm-hairlines` | Hairline Path AA breadth remains outside this bounded clip/depth proof. |
| `skia-gm-largeclippedpath` | Large path clip remains over the current edge budget. |
| `skia-gm-perspectiveclip` | Perspective clip support would change the bounded clip/transform boundary. |
| `skia-gm-shadertext3` | Text/glyph rendering remains dependency-gated. |
| `skia-gm-dashtextcaps` | Combines text/glyph dependency gates with dash/cap Path AA breadth. |

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass.
