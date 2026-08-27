# W20 — path curves

Date: 2026-08-28

## Verdicts verified

| Route | Verdict | Executed evidence |
| --- | --- | --- |
| Quadratic and cubic closed fill paths | supported, bounded | `GPUFramePathApiInventoryTest` verifies the public `Surface` route selects `native.path_fill.stencil_cover`; `GPUClipCoverageSurfaceTest` performs native WebGPU readback against an independent CPU buffer for cubic clip variants. |
| Oval and circle fill paths | supported, bounded | Both public-path constructions (four cubic segments) select the same native stencil-cover route without a refusal. |
| Curve edge-fan budget | stable refusal | `PathTessellatorTest` proves `geometry.path.fan_budget_exceeded` is emitted before the fan buffers are allocated. The production `RenderConfig.maxPathVertices` refusal remains separately covered by `GPUFramePathApiInventoryTest`. |
| Rational conic transport | lowerer-only contract | The internal GPU lowerer accepts a finite positive-weight rational conic and preserves its endpoint. Kanvas currently exposes no public conic verb, so this is not promoted as public `Surface` support. |

## Native proof

`GPUClipCoverageSurfaceTest.bounded cubic clip matches the independent CPU buffer for both fill rules and operations` completed on the headless WebGPU adapter. It compares full RGBA buffers for winding/even-odd and intersect/difference variants; no CPU fallback is selected.

## Deliberate boundary

The public `Path` API currently has quadratic, cubic, oval and circle construction, but no conic verb. Adding one would also require an approved picture serialization and compatibility change. This wave therefore does not claim public conic rendering.
