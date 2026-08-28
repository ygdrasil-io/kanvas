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

The four public fill families were additionally generated and promoted from
commit `3cc329d187b01ec61d1693134658a2165dba95d2` after the shared payload
contract correction:

| Family | Promoted bundle | Result |
| --- | --- | --- |
| Quadratic | `correctness/promoted/quadratic-path-fill/` | 64×64 independent CPU oracle and native WebGPU readback; 100%, 0 differing pixels. |
| Cubic | `correctness/promoted/cubic-path-fill/` | 64×64 independent CPU oracle and native WebGPU readback; 100%, 0 differing pixels. |
| Oval | `correctness/promoted/oval-path-fill/` | 64×64 independent CPU oracle and native WebGPU readback; 100%, 0 differing pixels. |
| Circle | `correctness/promoted/circle-path-fill/` | 64×64 independent CPU oracle and native WebGPU readback; 100%, 0 differing pixels. |

Each bundle contains CPU/GPU captures, diff and statistics, route diagnostics,
adapter metadata and integrity hashes. The rerun preserved the CPU/GPU bytes
and their manifest hashes; the regenerated catalogue binds all four bundles to
the code-final commit above. The route diagnostics attest one native
`kanvas.surface.render` submission per case; no refusal or CPU fallback was
recorded. `verifyPromotedGpuEvidence` independently verified the whole
promoted catalogue after the four additions.

## Bound

The public route admits at most 1,024 stencil edge-fan triangles, or 36,864
bytes (36 bytes per fan triangle). `GPUPathEdgeFanPayloadContract` is the
single renderer-owned source for these capacities: payload validation and
`RenderConfig` derive from it. Both limits are checked before the edge-fan
buffers are allocated and produce their respective stable diagnostics. This is
large enough for the four 64×64 curve proofs while keeping the admission
surface finite and auditable.

Configurations above either static payload capacity, or unsigned values that
cannot be represented by the backend `Int` contract, are refused during public
`Surface` mapping before any draw packet is recorded.

## Deliberate boundary

The public `Path` API currently has quadratic, cubic, oval and circle construction, but no conic verb. Adding one would also require an approved picture serialization and compatibility change. This wave therefore does not claim public conic rendering.
