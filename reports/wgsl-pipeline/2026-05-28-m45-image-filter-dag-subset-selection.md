# M45-A Image-filter DAG subset selection

M45 selects a bounded image-filter DAG subset beyond the M38
`Crop(kDecal, input = Offset(null))` child pre-pass. The selected V1 subset is:

```text
Compose(
  outer = ColorFilter(Matrix|Blend, input = null),
  inner = MatrixTransform(affine 2x3, input = null)
)
```

The subset is a two-node single-input DAG: first materialise an affine
`MatrixTransform` into a WebGPU scratch texture, then apply one supported
`ColorFilter` during the final layer composite. It is not a general image-filter
DAG compiler.

## Source tests and scene

First implementation/dashboard source test:

```text
org.skia.gpu.webgpu.SaveLayerImageFilterTest#saveLayer with Compose(ColorFilter, MatrixTransform) grayscales the transformed pixels()
```

Candidate dashboard scene id:

```text
image-filter-compose-cf-matrix-transform
```

The representative scene draws a red layer rect, applies an affine translation
through `MatrixTransform`, then applies a grayscale `ColorFilter` as the outer
Compose node. Expected evidence must include reference, CPU, GPU, diff, stats,
and route JSON.

## Supported nodes

| Node | Supported V1 shape | Notes |
| --- | --- | --- |
| `Compose` | exactly one `outer` and one `inner` node | Evaluation order is inner first, then outer. |
| `MatrixTransform` | affine 2x3 matrix, `input = null`, nearest or linear sampling | Perspective is out of scope. Scratch extent starts as the layer source extent. |
| `ColorFilter` | one supported Matrix or Blend color filter, `input = null` | Runs in the final composite color-filter slot. |

## Composition order

Evaluation must stay explicit:

1. Render the saveLayer content into the existing layer source texture.
2. Materialise `MatrixTransform(affine, input = null)` into one scratch texture.
3. Final composite samples the matrix scratch and applies the outer
   `ColorFilter` while blending to the parent render target.

Required GPU route diagnostics:

```text
webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix
webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite
```

Required CPU route diagnostic:

```text
cpu.image-filter.compose.cf-matrix-transform-oracle
```

## Bounds and crop policy

M45-A does not add new crop semantics. Bounds are inherited from the saveLayer
source texture and final composite bounds:

| Field | Policy |
| --- | --- |
| Layer source extent | Existing saveLayer source texture size. |
| Matrix scratch extent | Same as layer source extent for V1 evidence stability. |
| Matrix sample outside source | Existing MatrixTransform sampling semantics and transparent outside-source behavior. |
| Final composite bounds | Existing `compositeFrom` destination bounds and clip remain authoritative. |
| Crop rects | Out of scope unless the graph is exactly the M38 Crop(kDecal)+Offset shape. |

## Intermediate texture requirements

GRA-217 must define the ownership contract for this subset in code terms, but
M45-A fixes the required behavior:

- allocate exactly one matrix materialise scratch for the selected two-node DAG;
- scratch format matches existing layer composite scratch textures;
- usage includes render attachment and texture binding;
- lifetime is one composite dispatch: retain until final composite command is
  encoded, then release through existing per-frame cleanup;
- no CPU readback, no raster fallback, and no second resource lifecycle model;
- route JSON must name the scratch/materialise stage and the final composite
  stage.

## Reference, diff, and threshold policy

- CPU oracle: existing raster/WebGPU test oracle for the same fixture, plus
  route JSON that names `cpu.image-filter.compose.cf-matrix-transform-oracle`.
- GPU support claim: adapter-backed rendered output only, `fallbackReason=none`,
  and route JSON naming both V1 GPU stages.
- Dashboard status: `pass` only when reference, CPU, GPU, diffs, stats, and
  route diagnostics are attached.
- Similarity floors must come from the source test evidence and must not be
  lowered merely to clear dashboard or inventory rows.

## Stable fallback policy

Non-selected DAG shapes remain explicit unsupported scope. They must not become
silent no-ops or CPU fallback.

| Shape | Policy |
| --- | --- |
| `MatrixTransform` with perspective | Stable refusal mentioning perspective. |
| `MatrixTransform(input != null)` outside the selected Compose shape | Stable refusal mentioning non-null child. |
| `ColorFilter(input != null)` outside the selected Compose shape | Stable refusal mentioning non-null child. |
| `Compose(ColorFilter, ColorFilter)` | Stable single-occupancy color-filter refusal. |
| `Compose(Blur, Blur)` | Stable duplicate-blur refusal until a blur-composition ticket owns kernel math. |
| `Compose` with Crop/Tile/Magnifier/Image/Blend/morphology/lighting/displacement | Out of scope; keep stable diagnostics. |
| Arbitrary nested DAG compilation | Out of scope; do not port Skia image-filter internals. |

## Follow-up ticket split

- GRA-217: define code-level intermediate texture/layer ownership for the
  selected matrix-materialise plus final color-filter composite path.
- GRA-218: implement or harden the selected route and emit deterministic route
  diagnostics for the source test.
- GRA-219: add dashboard evidence for `image-filter-compose-cf-matrix-transform`.
- GRA-220: close M45 with validation, risks, and remaining image-filter DAG
  order.

## Validation

```bash
rtk git diff --check
```

Documentation/spec review only; runtime support and dashboard validation belong
to GRA-218 and GRA-219.
