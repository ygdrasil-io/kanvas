# M45-B Intermediate texture and layer ownership

GRA-217 defines the ownership contract for the M45 selected image-filter DAG
subset:

```text
Compose(
  outer = ColorFilter(Matrix|Blend, input = null),
  inner = MatrixTransform(affine 2x3, input = null)
)
```

The contract is intentionally scoped to one MatrixTransform materialise pass plus
one final ColorFilter composite. It does not introduce a general image-filter DAG
resource model.

## Implementation boundary

Existing WebGPU layer-composite infrastructure already has the required shape:

| Concept | Implementation boundary |
| --- | --- |
| Matrix materialise draw | `LayerCompositeDraw.materializeTargetTexture` / `materializeTargetView` |
| Downstream source view | The materialised scratch becomes the `sourceLayerView` for the final composite. |
| Final color filter | `LayerCompositeDraw.colorFilterPacked` via the standard layer composite shader. |
| Cleanup owner | `DrawResources.materializeTargetTexture` / `materializeTargetView`, closed by draw-resource cleanup. |
| Source layer owner | The popped saveLayer device remains canvas-owned and is not transferred. |

GRA-218 should harden deterministic route diagnostics around this existing
boundary rather than adding a parallel scratch/layer abstraction.

## Ownership rules

| Resource | Owner | Lifetime | Close path |
| --- | --- | --- | --- |
| SaveLayer source texture/view | Child layer device / canvas stack | From saveLayer creation through parent composite submission | Existing child layer cleanup |
| Matrix materialise scratch texture/view | The `LayerCompositeDraw` that requests materialisation | From route planning through final composite command encoding/submission | `DrawResources.materializeTargetView.close()` then `materializeTargetTexture.close()` |
| Final composite bind groups/uniforms | Per-draw resources | Command encoding/submission | Existing per-draw resource cleanup |
| Parent render target | Parent `SkWebGpuDevice` | Device lifetime | Not owned by image-filter route |

No CPU readback, CPU fallback, or long-lived cache ownership is allowed for the
M45 selected subset.

## Allocation contract

The materialise scratch must use the same allocation convention as current layer
composite scratch textures:

| Field | Required value |
| --- | --- |
| Size | Layer source extent `(w, h)` for V1 evidence stability. |
| Format | Same color format as the parent layer composite scratch path. |
| Usage | Render attachment plus texture binding. |
| Initial contents | Cleared transparent before MatrixTransform materialisation. |
| Bounds domain | Layer-local coordinates. |
| Scissor | Layer-local scissor translated from parent clip and materialised bounds. |
| Sampling | MatrixTransform sampling from the selected affine filter. |

The scratch is not cropped smaller in M45 V1. Smaller allocations require a
separate bounds/origin proof because they increase coordinate-risk without
changing the selected support claim.

## Route stages

Route diagnostics must identify each stage clearly enough for dashboard and
Linear closeout evidence:

| Stage | Required route code | Resource fact |
| --- | --- | --- |
| CPU oracle | `cpu.image-filter.compose.cf-matrix-transform-oracle` | Raster/reference oracle; no WebGPU scratch. |
| Matrix materialise | `webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix` | Writes affine MatrixTransform output into one layer-sized scratch. |
| Final composite | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` | Samples matrix scratch and applies one ColorFilter in final composite. |

The final GPU route JSON should include:

```json
{
  "selectedRoute": "webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite",
  "prepassRoute": "webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix",
  "scratchOwner": "LayerCompositeDraw.materializeTargetTexture",
  "scratchLifetime": "per-composite-dispatch",
  "fallbackReason": "none"
}
```

## Fallback and refusal behavior

The selected route may claim support only when the graph matches the M45-A shape
and the GPU route emits both materialise and final composite diagnostics.
Everything else remains explicit refusal or existing supported behavior.

| Shape | Required behavior |
| --- | --- |
| Selected `Compose(ColorFilter, MatrixTransform affine)` | Route through one materialise scratch and final ColorFilter composite. |
| `MatrixTransform` with perspective | Refuse with perspective diagnostic. |
| `MatrixTransform(input != null)` outside selected shape | Refuse with non-null child diagnostic. |
| `ColorFilter(input != null)` outside selected shape | Refuse with non-null child diagnostic. |
| Two ColorFilters in the same effective stage | Refuse with single-occupancy diagnostic. |
| Two Blurs in one Compose chain | Refuse with duplicate-blur diagnostic. |
| Crop, Tile, Magnifier, Image, Blend, morphology, lighting, displacement in arbitrary Compose DAG | Out of scope; keep stable diagnostics. |

## GRA-218 implementation checklist

- Detect the selected graph shape deterministically.
- Route MatrixTransform materialisation through `LayerCompositeDraw` scratch
  ownership, not a new resource owner.
- Add or expose the required route codes in implementation/dashboard evidence.
- Preserve existing refusal messages for non-selected shapes.
- Run the selected `SaveLayerImageFilterTest` path and `rtk git diff --check`.

## Validation

```bash
rtk git diff --check
```

No runtime code changed in GRA-217; implementation validation belongs to GRA-218.
