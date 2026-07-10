# Generalized WebGPU Mask Blur Routing

## Status

Proposed design, approved for planning. This document does not itself claim
support until the validation evidence defined below exists.

## Goal

Execute `MaskFilter.Blur` for `FillPath`, `FillRect`, and `FillRRect` on the
WebGPU backend. The route covers `NORMAL`, `SOLID`, `OUTER`, and `INNER`
styles, uses alpha-mask semantics, and removes the current false-positive
route where the planner accepts a blur but the runtime draws an unblurred
shape.

## Constraints

- Keep WebGPU as the GPU backend; do not port Ganesh or Graphite.
- Preserve one semantic path across CPU/reference evidence and WebGPU.
- Do not silently ignore a mask filter or fall back to CPU/readback.
- WGSL is the implementation target. New generated or assembled modules must
  be deterministic and parser-validated.
- Resource ownership, intermediate textures, and cache lifetime remain with
  the WebGPU backend.
- Existing `ImageFilter.Blur` limits are a direct-path policy, not a public
  `MaskFilter.Blur` API limit.

## API and Support Policy

`MaskFilter.Blur` continues to accept every finite non-negative sigma in the
public API. The implementation normalizes a requested sigma to 135 px,
matching the documented internal bound used by Skia's mask blur
implementation. A clamp is recorded as a non-terminal route diagnostic, not
an API error.

The backend uses two execution tiers:

1. Identity: sigma equal to zero is elided and retains the existing unfiltered
   shape route.
2. Direct: a native-resolution two-pass separable blur when the effective
   sigma is at most 12 px.
3. Reduced resolution: for a larger normalized sigma, select a scale factor
   that makes the effective sigma at most 12 px, rasterize and blur at that
   scale, then upscale during final composition.

Allocation is constrained by the clipped device-space bounds, a `3 * sigma`
halo, `maxTextureDimension2D`, and a backend memory budget. If a semantically
meaningful intermediate cannot be allocated after reduction, the backend
refuses the draw with a stable diagnostic. It does not draw an unblurred shape
or use an implicit CPU/readback path.

## Architecture

### Shared plan

Introduce a backend-owned `MaskBlurPlan` selected for normalized path, rect,
and rrect commands carrying `NormalizedMaskFilter.Blur`. It owns:

- original and clipped device-space bounds;
- requested, normalized, and effective sigma;
- halo and selected scale factor;
- intermediate texture descriptors and byte estimates;
- horizontal and vertical blur pass descriptors;
- style-composition descriptor;
- cache/invalidation facts and stable diagnostics.

The plan replaces the analysis-only `prepared.*.blur_mask` route and its
`pending.pipeline.*` key with an executable route. A route cannot be reported
as dispatched until its offscreen passes and final composite have been encoded.

### Execution flow

```text
geometry coverage
  -> alpha mask intermediate M
  -> horizontal blur
  -> vertical blur B
  -> style coverage
  -> paint modulation and final blend/store
```

The first intermediate stores only the shape coverage, not pre-composited
paint color. The final source color is formed after blur/style evaluation, so
paint alpha, color filters, blending, and destination semantics remain in the
normal final-composition order.

For original coverage `M` and blurred coverage `B`, use:

| Style | Final coverage |
|---|---|
| `NORMAL` | `B` |
| `SOLID` | `max(M, B)` |
| `OUTER` | `B * (1 - M)` |
| `INNER` | `B * M` |

The final coverage modulates the premultiplied paint source immediately before
the existing final blend/store path. The blur operates after geometric
transform/coverage lowering in device space. Kanvas exposes no
ignore-transform mask-blur flag in this slice.

### Resource and cache ownership

Temporary mask, horizontal-result, and style-composition textures are
frame-local backend resources. Their descriptors include dimensions, format,
usage, and scale. Reusable resources use the existing backend resource cache
with explicit invalidation on device loss and surface reconfiguration.

Pipeline keys include only execution topology: direct vs reduced-resolution,
style-composition requirements, intermediate format/layout, and relevant
pipeline state. Sigma, bounds, paint color, and transform values are uniforms
or per-draw data and do not create a pipeline key per value.

## Diagnostics and Refusal Policy

The route dump records the requested/normalized/effective sigma, scale,
clipped bounds, halo, intermediate dimensions and bytes, pass count, cache
facts, and final route outcome.

Stable terminal reasons cover at least invalid sigma, unavailable intermediate
allocation, device texture-limit overflow after reduction, unsupported command
coverage input, and unavailable backend resources. A large but valid sigma is
not itself a refusal reason: it selects the reduced-resolution route.

## Validation

### Unit and route tests

- `MaskBlurPlan` tests cover bounds, halo, scale selection, sigma clamp,
  allocation refusal, and all four style formulas.
- Command-mapping tests cover paths, rects, rrects, and stroked variants where
  the normalized command carries a mask filter.
- Route tests assert that no `pending.pipeline.*` blur key can be considered
  executable and that accepted routes encode actual blur and composite passes.

### GPU and visual evidence

- GPU tests validate mask rendering, horizontal/vertical passes, style
  composition, and final blend against CPU/reference evidence.
- `blur2rects` and `blur2rectsnonninepatch` verify nested paths and subpixel
  phases; additional focused scenes cover every blur style and a large sigma.
- Dashboard rows retain reference, GPU render, diff, score, route diagnostics,
  and intermediate-resource telemetry.
- Existing similarity thresholds do not weaken. A focused mask/halo oracle
  also asserts non-flat blurred coverage, preventing blank-background
  similarity from accepting an unblurred shape.

### Performance evidence

Measure native-resolution and reduced-resolution representative scenes. Report
offscreen pass count, intermediate bytes, pipeline/module cache hits and
misses, draw count, and render time. Any increase to the direct-sigma or
resource budget requires before/after evidence; it is not inferred from API
acceptance alone.

## Explicit Non-Goals

- Porting Skia GPU backends or reproducing their internal render graph.
- Arbitrary image-filter DAG support.
- CPU/readback compatibility hidden behind a successful GPU route.
- A universal blur shader with unbounded taps.
- Adding a public ignore-transform flag or a new public mask-filter API.

## Acceptance Criteria

The generalized route is supportable only when all selected shape/style cases
have CPU/reference/GPU/diff evidence, executable route diagnostics, stable
fallback behavior, and measured resource/performance payloads. The prior
unblurred-but-dispatched behavior must be impossible by construction and
covered by regression tests.
