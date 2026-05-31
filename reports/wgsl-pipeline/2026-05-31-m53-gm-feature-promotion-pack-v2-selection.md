# M53 GM Feature Promotion Pack v2 Selection

Date: 2026-05-31
Milestone: M53
Linear epic: GRA-309
Ticket: GRA-310

## Scope

This report selects the M53 GM-derived promotion pack before implementation.
The selected rows are planning inputs for generated scene evidence only. They
do not claim broad Skia GM parity, broad image-filter DAG support, broad Path
AA support, or dependency-gated font/codec/emoji/shaping/SDF/LCD/glyph-mask
support.

Inputs:

- M52 PM report:
  `reports/wgsl-pipeline/2026-05-31-m52-pm-report.md`
- M52 promotion pack:
  `reports/wgsl-pipeline/2026-05-31-m52-inventory-promotion-pack.md`
- Generated inventory:
  `build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json`
- Inventory gate:
  `build/reports/wgsl-pipeline-skia-gm-inventory-gate/inventory-gate.md`

The generated inventory gate currently reports 802 inventory rows, 27
dashboard-promoted rows, 28 promotion-candidate rows, 619 not-triaged rows, and
0 gate failures.

## Selection Rules

- Prefer PM-visible rendered feature breadth over dashboard presentation work.
- Select 12-16 rows; this pack selects 12 rows across five visual families.
- Prefer likely `pass` rows, but keep high-value boundaries visible as
  `expected-unsupported` when support would otherwise over-claim.
- Do not add `tracked-gap` rows. A non-viable candidate must be rejected or
  emitted as stable `expected-unsupported`, not hidden.
- Keep inventory-only rows separate from support claims until generated
  reference, CPU, GPU/refusal, diff/stat, route, and tag evidence exists.

## Selected Pack

| Inventory id | Family | Intended status | Reference source | CPU route expectation | GPU route expectation | Fallback policy |
|---|---|---|---|---|---|---|
| `skia-gm-gradientsdegenerate` | gradients | `pass` if generated thresholds hold | Candidate-specific Skia GM capture or generated test oracle from `gradients_degenerate.cpp`. | CPU gradient PipelineIR oracle for bounded degenerate stop cases. | Generated WGSL gradient route for bounded degenerate linear/radial cases. | `fallbackReason=none` required for `pass`; reject instead of widening to arbitrary gradient behavior. |
| `skia-gm-sweepgradient` | gradients | `pass` for the kClamp path subset | Existing `sweep-gradient-path-clamp` generated test oracle. | CPU sweep-gradient path-AA oracle. | Generated WebGPU sweep-gradient path-AA route with `fallbackReason=none`. | Do not broaden to all sweep tile modes, all gradient families, or broad Path AA support. |
| `skia-gm-bitmappremul` | bitmap/image | `pass` if premul sampling is stable | Candidate-specific Skia GM capture or generated bitmap oracle from `bitmappremul.cpp`. | CPU bitmap/image sampling oracle with premul alpha checks. | WebGPU bitmap/image sampling route with `fallbackReason=none`. | Reject if premul alpha mismatch appears; do not add a tracked gap. |
| `skia-gm-bitmapfilters` | bitmap/image | `pass` for a bounded filtering subset | Candidate-specific Skia GM capture or generated bitmap filter oracle from `bitmapfilters.cpp`. | CPU bitmap sampling/filtering oracle for selected simple modes. | WebGPU image sampling route for selected filter modes only. | `fallbackReason=none` required for promoted filter modes; unsupported filters remain out of scope. |
| `skia-gm-arithmode` | blend/color-filter | `pass` for a bounded arithmetic blend subset | Candidate-specific Skia GM capture or generated blend oracle from `arithmode.cpp`. | CPU PipelineIR paint/blend scalar oracle. | Generated WGSL BlendPlan route when `fallbackReason=none`. | Reject unsupported coefficients instead of claiming all arithmetic blend modes. |
| `skia-gm-modecolorfilters` | blend/color-filter | `pass` for selected mode color filters | Candidate-specific Skia GM capture or generated color-filter oracle from `modecolorfilters.cpp`. | CPU PipelineIR color-filter oracle. | Generated WGSL color-filter/blend route when `fallbackReason=none`. | `fallbackReason=none` for selected modes; no arbitrary color-filter claim. |
| `skia-gm-badpaint` | blend/color-filter | `pass` for sanitized paint-state cases | Candidate-specific Skia GM capture or generated paint oracle from `badpaint.cpp`. | CPU PipelineIR paint oracle with explicit invalid/edge paint-state handling. | WebGPU paint route for the bounded sanitized subset. | Reject cases that require undefined or unsupported paint semantics. |
| `skia-gm-clipshader` | clip/transform/saveLayer | `pass` for rectangular or otherwise bounded clip-shader cases | Candidate-specific Skia GM capture or generated clip-shader oracle from `clipshader.cpp`. | CPU clip plus shader oracle. | WebGPU clip/shader route with stable route diagnostics. | `fallbackReason=none` for the bounded route; complex clipping remains out of scope. |
| `skia-gm-convexpolyclip` | clip/transform/saveLayer | `pass` if convex coverage stays within existing limits | Candidate-specific Skia GM capture or generated convex clip oracle from `convexpolyclip.cpp`. | CPU convex polygon clip oracle. | WebGPU coverage/clip route only when edge budget and diagnostics pass. | Refuse with stable coverage reason if the selected polygon exceeds supported coverage. |
| `skia-gm-complexclip` | clip/transform/saveLayer | `expected-unsupported` | Candidate-specific Skia GM capture from `complexclip.cpp`. | CPU clip oracle may document the high-value boundary. | GPU expected refusal for complex/path clip behavior. | Stable non-`none` fallback reason required; no broad complex clip support claim. |
| `skia-gm-imageblur` | bounded image-filter | `pass` only for one bounded blur/prepass contract if viable | Candidate-specific Skia GM capture or generated image-filter oracle from `imageblur.cpp`. | CPU image-filter oracle for one bounded blur configuration. | WebGPU prepass/layer route only if it fits existing image-filter ownership contracts. | If prepass ownership is not viable, document rejection; do not emit tracked-gap. |
| `skia-gm-imagefilterscropped` | bounded image-filter | `expected-unsupported` unless the crop/prepass contract is explicit | Candidate-specific Skia GM capture from `imagefilterscropped.cpp`. | CPU image-filter crop oracle may document the boundary. | GPU must either use an explicit bounded prepass/layer route or refuse. | Stable crop/prepass fallback reason required; no arbitrary image-filter DAG claim. |

Selected counters:

| Signal | Count |
|---|---:|
| Selected inventory candidates | 12 |
| Target `pass` candidates if viable | 10 |
| Intentional `expected-unsupported` boundary candidates | 2 |
| Families covered | 5 |
| New `tracked-gap` rows allowed | 0 |
| Broad Skia GM support claims | 0 |

## Rejected Or Deferred

| Inventory id | Decision | Concrete reason |
|---|---|---|
| `skia-gm-duckyyuvblend` | Deferred | YUV/codec image decode dependency remains gated; selecting it would risk a codec substitute or unsupported bitmap source claim. |
| `skia-gm-animatedimage` | Rejected for M53 | Animated image decoding remains dependency-gated. |
| `skia-gm-animatedimageblurs` | Rejected for M53 | Combines dependency-gated animated image decode with image-filter blur; not a bounded M53 feature proof. |
| `skia-gm-animatedimageorientation` | Rejected for M53 | Codec/image orientation path remains dependency-gated. |
| `skia-gm-ayncyuvnoscale` | Deferred | Async YUV image source requires codec/YUV delivery outside this sprint. |
| `skia-gm-runtimeimagefilter` | Deferred | Runtime image-filter support needs a descriptor-backed slice; do not rebuild SkSL or VM. |
| `skia-gm-runtimeintrinsics` | Deferred | Runtime intrinsic coverage needs registered Kotlin/WGSL descriptor evidence; no arbitrary SkSL claim. |
| `skia-gm-gradients2ptconical` | Deferred | Two-point conical gradient remains outside the sweep-gradient clamp scene contract. |
| `skia-gm-shadertext3` | Rejected for M53 | Text/glyph rendering and shader text behavior remain dependency-gated. |
| `skia-gm-dashtextcaps` | Rejected for M53 | Text plus dash cap behavior crosses dependency-gated glyph work and coverage limits. |
| `skia-gm-dftext` | Rejected for M53 | SDF glyph backend remains gated. |
| `skia-gm-dftextblobpersp` | Rejected for M53 | SDF glyph and perspective text dependencies remain gated. |
| `skia-gm-fontations` | Rejected for M53 | Font stack work remains dependency-gated and must not be substituted. |
| `skia-gm-animatedgif` | Rejected for M53 | GIF codec/animation dependency remains gated. |
| `skia-gm-animcodecplayerexif` | Rejected for M53 | Codec/EXIF dependency remains gated. |
| `skia-gm-convexalllinepaths` | Deferred | Broad Path AA line-path pack exceeds the current edge-budget policy. |
| `skia-gm-dashcubics` | Deferred | Broad dashed cubic coverage should remain explicit refusal until a scoped dash/cap/join slice exists. |
| `skia-gm-hairlines` | Deferred | Hairline Path AA breadth remains outside this feature pack. |
| `skia-gm-dashbigrects` | Deferred | Dash coverage still maps to edge-budget refusal, not a clean M53 pass target. |
| `skia-gm-dashcircle` | Deferred | Dash/circle behavior needs a separate bounded coverage slice. |
| `skia-gm-dashcircle2` | Deferred | Dash/circle variant would duplicate the same unresolved coverage slice. |
| `skia-gm-dashing` | Deferred | Broad dashing remains coverage-gated. |
| `skia-gm-dashing2` | Deferred | Broad dashing variant remains coverage-gated. |

## Expected Score Movement

GRA-310 itself does not change the readiness score because it adds selection
evidence only. Inventory rows remain planning evidence until later M53 tickets
promote generated dashboard rows with artifacts and gates.

Expected M53 score movement after implementation:

- stay at 85% if the selected rows do not produce clean generated evidence;
- move to 88% if useful feature rows land and dashboard, inventory, and PM
  bundle gates remain clean;
- consider 90% only if 8-12 new generated scenes land across multiple families
  with 0 `tracked-gap`, 0 `fail`, stable expected-unsupported rows, and
  PM-bundle counters.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass.
