# M54 Hard Feature Depth Selection

Date: 2026-05-31
Milestone: M54
Linear epic: GRA-317
Ticket: GRA-318

## Scope

This report selects the M54 hard feature depth pack before implementation.
The selected rows are planning inputs for generated scene evidence only. They
do not claim broad Skia GM parity, broad arbitrary image-filter DAG support,
broad Path AA support, arbitrary SkSL, or dependency-gated font/codec/emoji/
shaping/SDF/LCD/glyph-mask support.

M54 starts from the merged M53 baseline:

| Signal | M53 baseline |
|---|---:|
| PM readiness | 90% |
| Dashboard rows | 50 |
| `pass` | 37 |
| `expected-unsupported` | 13 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated rows | 48 |
| Adapter-backed rows | 33 |
| Inventory-derived rows | 22 |

Inputs:

- M53 PM report:
  `reports/wgsl-pipeline/2026-05-31-m53-pm-report.md`
- M53 selection:
  `reports/wgsl-pipeline/2026-05-31-m53-gm-feature-promotion-pack-v2-selection.md`
- M53 promotion evidence:
  `reports/wgsl-pipeline/2026-05-31-m53-inventory-promotion-pack.md`
- Generated inventory:
  `build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json`
- Inventory gate:
  `build/reports/wgsl-pipeline-skia-gm-inventory-gate/inventory-gate.md`

The generated inventory gate reports 802 inventory rows, 27
dashboard-promoted rows, 28 promotion-candidate rows, 619 not-triaged rows, and
0 gate failures. Inventory rows remain planning evidence until a later ticket
materializes generated reference, CPU, GPU or refusal, diff/stat, route, tag,
and metadata artifacts.

## Selection Rules

- Select depth in difficult rendering families, not dashboard UX changes or
  easy row-count expansion.
- Aim for 8-10 generated rows if viable, with at least 6 `pass` rows.
- Cover at least bounded image-filter v2, Path AA / clip depth, and
  runtime / paint composition.
- Prefer `expected-unsupported` with stable reasons over `tracked-gap` when a
  candidate is not viable.
- Keep performance evidence warning-only and reporting-only; do not add a
  release-blocking performance gate in M54.
- Do not raise the global Path AA edge budget.
- Keep every support claim limited to the selected generated scene contract.

## Selected Pack

| Candidate | Family | Intended status | Source evidence or inventory id | Reference source | CPU route expectation | GPU route expectation | Fallback policy | Blocker risk |
|---|---|---|---|---|---|---|---|---|
| `m54-imagefilter-transformed-affine` | bounded image-filter v2 | `pass` if affine-only thresholds hold | `skia-gm-imagefilterstransformed`; M45/M46 `image-filter-compose-cf-matrix-transform` route evidence | Candidate-specific GM capture from `imagefilterstransformed.cpp` or generated oracle derived from the bounded affine transform fixture | CPU image-filter oracle for affine MatrixTransform plus final composite | WebGPU prepass route must materialize one affine transform scratch, then final composite; no recursive DAG scheduler | `fallbackReason=none` required for `pass`; reject perspective or recursive graph variants | Medium: must preserve layer-local coordinate and scratch ownership semantics |
| `m54-matrix-imagefilter-affine` | bounded image-filter v2 | `pass` for affine matrix subset | `skia-gm-matriximagefilter`; M45 image-filter ownership contract | Candidate-specific GM capture from `matriximagefilter.cpp` | CPU affine matrix image-filter oracle | WebGPU matrix image-filter prepass using existing layer composite scratch semantics | `fallbackReason=none` only for affine subset; perspective remains out of scope | Medium: may need explicit matrix-type guard |
| `m54-cropped-filter-prepass-v2` | bounded image-filter v2 | `pass` if crop/prepass contract becomes explicit | `skia-gm-imagefilterscropped`; M38 `crop-image-filter-nonnull-prepass`; M53 `m53-imagefilters-cropped-boundary` | Candidate-specific GM capture from `imagefilterscropped.cpp` | CPU crop/filter oracle with exact crop rect and child bounds | WebGPU must either use explicit bounded prepass/layer ownership or refuse | No `tracked-gap`; if non-selected graph shape remains unsupported use `image-filter.crop-input-nonnull-prepass-required` | High: easy to over-claim arbitrary Crop(input=nonNull) graphs |
| `m54-imagefilters-graph-boundary` | bounded image-filter v2 | `expected-unsupported` | `skia-gm-imagefiltersgraph`; inventory reason `image-filter.dag-or-picture-prepass-required` | Candidate-specific GM capture from `imagefiltersgraph.cpp` when available | CPU/reference oracle may document the broad graph boundary | GPU expected refusal for DAG or picture-prepass graph shapes | Stable non-`none` fallback reason required; no arbitrary image-filter DAG claim | Low: refusal evidence only, but reason must stay stable |
| `m54-simple-aa-clip` | Path AA / clip depth | `pass` if bounded AA clip stays within current limits | `skia-gm-simpleaaclip`; Geometry/Coverage Path AA boundary | Candidate-specific GM capture from `simpleaaclip.cpp` | CPU AA clip oracle for simple rect/rrect/path clip subset | WebGPU coverage/clip route with current edge budget and explicit route diagnostics | `fallbackReason=none` for selected simple clip; over-budget paths refuse | Medium: clip lowering must not imply full clip-stack support |
| `m54-rrect-clip-drawpaint` | Path AA / clip depth | `pass` if bounded rrect clip route holds | `skia-gm-rrectclipdrawpaint`; M47 `clip-rect-difference` evidence | Candidate-specific GM capture from `rrectclipdrawpaint.cpp` | CPU rrect clip plus drawPaint oracle | WebGPU analytic rrect clip or mask route with `fallbackReason=none` | Complex clip stacks and path clips remain out of scope | Medium: threshold and AA edge behavior must be explicit |
| `m54-aaclip-bounded-subset` | Path AA / clip depth | `pass` only for a narrow AA clip subset; otherwise reject | `skia-gm-aaclip`; M33/M44 Path AA policies | Candidate-specific GM capture from `aaclip.cpp` | CPU AA clip oracle for selected bounded fixture | WebGPU coverage route only if edge count and clip diagnostics stay within current policy | Do not raise global edge budget; over-budget cases use stable coverage refusal | High: broad AA clip pack can hide unrelated path breadth |
| `m54-dash-circle-boundary` | Path AA / clip depth | `expected-unsupported` | `skia-gm-dashcircle`; inventory reason `coverage.edge-count-exceeded` | Candidate-specific GM capture from `dashcircle.cpp` | CPU dash/circle oracle may document expected unsupported boundary | GPU expected refusal through `webgpu.coverage.refuse` | Stable `coverage.edge-count-exceeded`; no dash/cap/join support claim | Low: refusal row, but must not weaken edge-budget policy |
| `m54-runtime-imagefilter-descriptor` | runtime / paint composition | `pass` only if a registered descriptor-backed slice exists; otherwise reject or stable refusal | `skia-gm-runtimeimagefilter`; M53 deferred row; runtime descriptor target | Candidate-specific GM capture from `runtimeimagefilter.cpp` or registered Kotlin/WGSL oracle | CPU registered runtime image-filter descriptor oracle, not SkSL interpretation | WebGPU registered WGSL implementation selected by descriptor metadata | No SkSL compiler/IR/VM; missing descriptor must become concrete rejection or stable expected-unsupported | High: descriptor availability is the gate |
| `m54-runtime-intrinsics-subset` | runtime / paint composition | `pass` for one registered bounded intrinsic subset if descriptor evidence lands | `skia-gm-runtimeintrinsics`; M53 deferred row | Candidate-specific GM capture from `runtimeintrinsics.cpp` or registered intrinsic fixture | CPU registered runtime-effect oracle for selected intrinsic family | WebGPU registered WGSL intrinsic implementation with reflected descriptor metadata | `fallbackReason=none` required; no arbitrary runtime intrinsic support claim | High: must avoid generic SkSL intrinsic claims |
| `m54-compose-colorfilter-paint` | runtime / paint composition | `pass` | `skia-gm-composecolorfilter`; existing color-filter/blend evidence from M53 | Candidate-specific GM capture from `composecolorfilter.cpp` | CPU PipelineIR color-filter composition oracle | Generated WGSL color-filter/blend route for selected composition | `fallbackReason=none` for selected composition only | Medium: composition order must be asserted |
| `m54-colorfilter-shader-composition` | runtime / paint composition | `pass` | `skia-gm-colorfiltershader`; M39/M53 paint/color-filter routes | Generated oracle from selected color-filter plus shader fixture | CPU shader then color-filter oracle with paint color modulation | WebGPU shader/color-filter route with explicit PipelineIR order | `fallbackReason=none`; no arbitrary color-filter or shader family claim | Medium: ordering and premul behavior must be explicit |
| `m54-warning-only-hard-row-performance` | warning-only performance evidence | reporting-only metrics attached to selected hard rows if stable | Selected rows above plus M49/M50 `pipelinePerformanceTrendWarnings` policy | Existing generated scene references only; no new support by timing alone | CPU timing payload for selected hard row when benchmark metadata is complete | GPU/cache timing payload only on named adapter | `gate.status=reporting-only`; unavailable adapter uses stable reason, not failure | Medium: do not turn perf into a release-blocking gate |

Selected counters:

| Signal | Count |
|---|---:|
| Selected candidates | 13 |
| Candidate families | 4 |
| Target generated rows if viable | 8-10 |
| Target `pass` rows if viable | 6-8 |
| Intentional boundary/refusal rows | 2-3 |
| New `tracked-gap` rows allowed | 0 |
| New `fail` rows allowed | 0 |
| Release-blocking performance gates | 0 |

## Implementation Grouping

The selected pack is intentionally broader than the target generated row count
so implementation tickets can reject non-viable candidates without forcing a
support claim.

| Follow-up ticket | Primary candidates | Expected output |
|---|---|---|
| GRA-319 bounded image-filter v2 | `m54-imagefilter-transformed-affine`, `m54-matrix-imagefilter-affine`, `m54-cropped-filter-prepass-v2`, `m54-imagefilters-graph-boundary` | 2-3 pass rows plus 1 stable boundary row if viable |
| GRA-320 Path AA and clip depth | `m54-simple-aa-clip`, `m54-rrect-clip-drawpaint`, `m54-aaclip-bounded-subset`, `m54-dash-circle-boundary` | 2-3 pass rows plus 1 stable edge-budget boundary row if viable |
| GRA-321 runtime and paint composition | `m54-runtime-imagefilter-descriptor`, `m54-runtime-intrinsics-subset`, `m54-compose-colorfilter-paint`, `m54-colorfilter-shader-composition` | 2-3 pass rows; reject descriptor-gated runtime rows if registered implementations are absent |
| GRA-322 warning-only performance | `m54-warning-only-hard-row-performance` | Reporting-only CPU/GPU/cache payloads attached only where complete and credible |

## Rejected Or Deferred

| Inventory id | Decision | Concrete reason |
|---|---|---|
| `skia-gm-animatedbackdropblur` | Rejected for M54 | Combines image-filter work with animated/codec-backed image delivery; codec and animation remain dependency-gated. |
| `skia-gm-animatedimageblurs` | Rejected for M54 | Animated image decode remains dependency-gated and must not be substituted with a static bitmap shortcut. |
| `skia-gm-blurbigsigma` | Deferred | Large-sigma blur is a performance and sampling stress case, not the bounded v2 proof; select smaller transform/crop contracts first. |
| `skia-gm-imagefiltersstroked` | Deferred | Combines image-filter and Path AA stroke breadth; promote base Path AA/clip and image-filter v2 routes separately before composing them. |
| `skia-gm-localmatriximagefilter` | Deferred | Useful follow-up, but M54 should first prove affine MatrixTransform/crop ownership with clearer prepass boundaries. |
| `skia-gm-xfermodeimagefilter` | Deferred | Crosses image-filter DAG and blend/xfermode breadth; too broad before selected color-filter and matrix-filter v2 rows land. |
| `skia-gm-dashcubics` | Deferred | Broad dashed cubic coverage remains edge-budget gated; split dash/cap/join before any support promotion. |
| `skia-gm-hairlines` | Deferred | Hairline Path AA breadth remains outside the selected bounded clip/depth proof. |
| `skia-gm-largeclippedpath` | Deferred | Large path clip remains over the current edge budget and should stay explicit expected-unsupported unless a separate strategy lands. |
| `skia-gm-perspectiveclip` | Deferred | Perspective clip support would change the clip/transform boundary and should not be bundled with bounded M54 clip depth. |
| `skia-gm-shadertext3` | Rejected for M54 | Text/glyph rendering remains dependency-gated; no substitute text path should be added to clear the row. |
| `skia-gm-dashtextcaps` | Rejected for M54 | Combines text/glyph dependency gates with dash/cap Path AA breadth. |

## Expected Score Movement

GRA-318 itself does not change the readiness score because it adds selection
evidence only. Inventory rows remain planning evidence until later M54 tickets
promote generated dashboard rows with artifacts and gates.

Expected M54 score movement after implementation:

- stay at 90% if the selected hard feature rows do not produce clean generated
  evidence;
- move to 92% if useful hard-family generated evidence lands across at least
  three families while `pipelineSceneDashboardGate` remains 0 `tracked-gap` and
  0 `fail`;
- consider 93% only if 8-10 generated rows land, at least 6 are `pass`, at
  least three hard families are represented, every unsupported row has stable
  fallback policy, and the PM bundle exposes M54 selected/promoted/rejected
  counters.

## Why This Is Feature Depth

The pack targets bounded but difficult execution boundaries:

- image-filter rows require explicit prepass/layer ownership, matrix/crop
  scope, and stable refusal for broader DAG shapes;
- Path AA/clip rows exercise clip depth and AA coverage while preserving the
  current edge-budget policy;
- runtime/paint rows require registered descriptor-backed runtime behavior and
  ordered shader/color-filter/blend composition;
- performance rows are attached only as warning-only evidence for already
  credible hard rows.

This is not a dashboard redesign, broad GM parity sweep, or row-count exercise.
Rows that would require broad DAG compilation, global Path AA budget changes,
SkSL rebuilding, codec/font substitutes, or release-blocking performance gates
are rejected or deferred.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate
```

Result: pass.
