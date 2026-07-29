# FP-05 Prepared Text Composite Shader Design

**Date:** 2026-07-29  
**Status:** Validated architecture, pending written-spec review  
**Scope:** Correct the Task 10 shader contract, reopen the minimum Task 3/8/9
contracts required by that correction, then resume native TextA8
materialization.

## Context

FP-05 Tasks 8 and 9 established:

- one immutable 64-byte prepared-text instance record;
- one frame-global instance buffer;
- exact per-subrun `firstInstance` and `instanceCount`;
- pure preflight of R8 pages, instances, materials, dependencies, limits and
  ownership before native creation.

The initially delivered `PreparedTextA8Shader` parses in isolation, but cannot
be used by Task 10:

1. its WGSL instance record is 32 bytes and declared as storage, while the
   frame plan provides a 64-byte vertex instance buffer;
2. it reads four device-space corners as position/size/UV data;
3. it sends device pixels directly to clip space;
4. it replaces the common Task 3 material with one fixed color block;
5. Task 3 currently publishes complete standalone stages with heterogeneous
   bindings and color contracts, not a composable fragment.

Materializing that shader would render incorrect geometry and could not support
the accepted gradient, image, blend or registered runtime-effect materials.
This is a Kanvas contract gap, not a wgpu4k defect.

## Reference Principle From Graphite + Dawn

The useful Graphite principle is separation followed by one final composition:

- bitmap text supplies geometry, atlas coordinates and coverage;
- the common paint supplies color;
- local coordinates, coverage, clip and blend are assembled into one program;
- bindings are allocated for that final program;
- Dawn receives one coherent pipeline and uses an instanced vertex buffer plus
  `baseInstance`.

Kanvas adopts only that principle. It does not port Graphite's RenderStep
hierarchy, paint-key tree, shader dictionary, SkSL compiler, recorder or
multi-backend resource abstractions.

## Decisions

### One canonical color contract

Every composable Task 3 material fragment returns **linear premultiplied RGBA**.

```text
materialPremul = evaluatePreparedMaterial(localPosition)
output = materialPremul * paintAlpha * coverage
```

Both RGB and alpha are multiplied by `paintAlpha` and A8 coverage exactly once.
The text composer never premultiplies a material again.

Task 3 owns source normalization:

- solid sources are converted to linear premultiplied RGBA;
- gradients remain linear premultiplied;
- image sources remain linear premultiplied;
- blend programs operate on and return linear premultiplied RGBA;
- registered runtime effects declare their source output contract, and Task 3
  inserts the one required conversion to the canonical contract.

The mapper must keep intrinsic source alpha separate from the final
`paintAlpha` modulation. No source family may encode that final modulation in
both its material bytes and `paintAlpha`.

### Task 3 remains the only material authority

`GPUPreparedMaterialProgramCompiler` gains a canonical composable fragment
contract while retaining its standalone WGSL program for existing consumers.

Conceptually:

```kotlin
data class GPUPreparedMaterialFragment(
    val declarationsWgsl: String,
    val evaluationFunctionWgsl: String,
    val evaluationFunction: String,
    val uniformLayout: GPUPreparedMaterialUniformLayout?,
    val sampledResourceLayouts: List<GPUPreparedMaterialSampledLayout>,
    val colorContract: GPUPreparedMaterialColorContract,
    val coordinateContract: GPUPreparedMaterialCoordinateContract,
    val fragmentHash: String,
    val abiHash: String,
)
```

The exported function has one semantic:

```wgsl
fn kanvas_evaluate_material(localPosition: vec2<f32>) -> vec4<f32>
```

It returns linear premultiplied RGBA. Task 10 does not switch on material
families. Solid, gradient, image, blend and runtime-effect differences remain
inside Task 3 compilation.

Composable sources use reserved Kanvas identifiers and a canonical binding
contract. The composer must not rename arbitrary WGSL with regexes or become a
second WGSL/material compiler.

### One deterministic text shader composer

`GPUPreparedTextShaderComposer` combines:

- the fixed TextA8 vertex stage;
- the Task 3 composable material fragment;
- A8 atlas sampling;
- final `paintAlpha × coverage`;
- the already accepted target and blend contract.

It produces:

```kotlin
data class GPUPreparedTextCompositeProgram(
    val wgslSource: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val bindingPlan: GPUPreparedTextCompositeBindingPlan,
    val vertexLayout: GPUPreparedTextVertexLayout,
    val sourceHash: String,
    val abiHash: String,
    val pipelineKey: String,
)
```

The final source is parsed, lowered and reflected through wgsl4k before it can
enter a frame plan. Reflection must match the declared binding and vertex ABI
exactly.

### Canonical binding allocation

The composed program uses one centrally assigned layout:

- group 0, binding 0: prepared-text draw uniform slice;
- group 1, binding 0: material uniform slice when present;
- group 1, subsequent bindings: material sampled texture/sampler pairs in
  exact Task 3 order;
- group 2, binding 0: R8 text atlas texture;
- group 2, binding 1: text atlas sampler.

The exact presence, count, visibility, sample type, sampler type and minimum
binding size are part of the composite ABI and pipeline key.

The pipeline key contains shader code, ABI/resource layout, vertex layout,
target format and blend facts. It deliberately excludes uniform values,
`paintAlpha`, texture contents and the value-level `materialKey`; changing a
color must not compile another pipeline.

Material fragments do not carry conflicting hard-coded groups. Task 3 emits
them for this canonical layout. Registered runtime effects that cannot satisfy
the composable binding contract are refused by Task 3 before frame recording.

### The 64-byte instance record is a vertex ABI

The existing Task 8 bytes remain authoritative:

| Offset | Field | Vertex format |
|---:|---|---|
| 0 | device TL | `float32x2` |
| 8 | device TR | `float32x2` |
| 16 | device BR | `float32x2` |
| 24 | device BL | `float32x2` |
| 32 | UV left/top/right/bottom | `float32x4` |
| 48 | glyph ID | unused by TextA8 shader |
| 52 | source glyph index | unused by TextA8 shader |
| 56 | page index | unused by TextA8 shader |
| 60 | color layer index | unused by TextA8 shader |

The pipeline binds this buffer with:

- `arrayStride = 64`;
- `stepMode = Instance`;
- buffer offset `0`;
- no storage-buffer binding.

The vertex shader maps six vertices to corners:

```text
TL, TR, BR, TL, BR, BL
```

UVs use the same corner order. It preserves affine/skewed quads and never
reconstructs a rectangle from origin and size.

Each draw is:

```text
draw(vertexCount=6, instanceCount, firstVertex=0, firstInstance)
```

The global buffer offset and `firstInstance` must not both apply the range.

### Draw uniforms and coordinate continuity

Task 8 adds one immutable frame-global draw-uniform buffer with an aligned
slice per text subrun.

The canonical 48-byte logical payload is:

```wgsl
struct PreparedTextDrawUniforms {
    // x = target width, y = target height, z = paintAlpha, w = reserved
    targetSizeAndPaintAlpha: vec4<f32>,
    // affine device-to-local rows; xyz are used, w is reserved
    deviceToLocalRow0: vec4<f32>,
    deviceToLocalRow1: vec4<f32>,
}
```

Physical slices respect the observed
`minUniformBufferOffsetAlignment`. The whole buffer is uploaded once.

The vertex shader:

1. selects the exact device-space corner;
2. converts device pixels to NDC:

   ```text
   ndcX = 2 × x / targetWidth - 1
   ndcY = 1 - 2 × y / targetHeight
   ```

3. computes continuous material coordinates using `deviceToLocal`;
4. forwards the exact atlas UV.

Consequently, a gradient or image shader does not restart at `[0,1]` for every
glyph.

Perspective text remains refused by the existing lowerer/preflight contract.
The draw-uniform transform is affine.

## Task Boundaries

### Task 3 correction

- add the composable fragment result;
- normalize every accepted source to linear premultiplied RGBA;
- preserve standalone WGSL for existing consumers;
- normalize material bindings without text-specific branching;
- include fragment, color and coordinate contracts in `materialKey` and
  `abiHash`;
- parser/reflection validate standalone and composable forms;
- refuse a registered runtime effect whose declared Kotlin/WGSL behavior or
  bindings cannot satisfy the composable contract.

### Task 8 correction

- keep the existing 64-byte instance buffer unchanged;
- add the frame-global draw-uniform plan and per-subrun slices;
- create and retain the composite program before task-list publication;
- include composite source/ABI/pipeline facts and draw-uniform facts in the
  immutable render binding and frame-plan identity;
- keep Core/Image-only frame identities unchanged.

### Task 9 correction

Before any native creation, validate:

- vertex usage, stride 64 and exact attribute offsets/formats;
- target dimensions and draw-uniform slice alignment/range/content;
- exact `deviceToLocal` and `paintAlpha` seal;
- composite source hash, entry points and reflected ABI;
- binding layout without collisions;
- exact Task 3 uniform and sampled resources;
- exact R8 atlas texture/sampler;
- pipeline key, target and blend facts;
- upload-before-consumer and ownership for every new buffer/resource.

Late corruption receives a stable preflight refusal. Source material that
cannot be composed is refused earlier by Task 3.

### Task 10 native materialization

After Tasks 3/8/9 are corrected:

- cache only invariant shader modules, layouts, samplers and render pipelines;
- key the cache by composite source hash, composite ABI/resource layout,
  vertex layout, target format and blend plan;
- upload each R8 page exactly once;
- upload each material sampled resource exactly once;
- call real `queue.writeBuffer` once for each frame-global instance,
  draw-uniform and material-uniform plan;
- create bind groups per compatible subrun;
- bind the global instance buffer at offset zero;
- issue one instanced draw per ordered TextA8 subrun;
- add operands by source scope and execute only in Task 9
  `exactScopeKeys` order;
- retain one encoder, one submit and at most one readback;
- retain payload-owned resources until completion;
- close every partially created resource exactly once on setup failure.

The number of compatible render passes is not frozen by this design.

## Refusal Boundaries

New canonical boundaries distinguish:

- source material cannot produce a composable fragment;
- composite WGSL source or entry point mismatch;
- composite reflection/ABI mismatch;
- instance vertex ABI or usage mismatch;
- draw-uniform payload/slice mismatch;
- material binding/resource mismatch;
- atlas binding/resource mismatch.

Tests reference production constants. They do not duplicate refusal strings.
Every Task 9 refusal proves zero native creation.

## Verification

### Pure compiler/composer

- solid, linear/radial/sweep/conical gradient, image, supported blend and
  registered runtime effect all compose;
- final modules contain one vertex and one fragment entry point;
- wgsl4k parse/lowering/reflection succeeds;
- bindings have no collisions and match exact material resources;
- material and composite keys change for every ABI-relevant mutation.

### Vertex and coordinates

- two sentinel records prove the second instance begins at byte 64;
- affine/skewed four-corner quads map to the six expected vertices;
- UV LTRB maps to the same corner order;
- `(0,0)` and `(W,H)` map to `(-1,+1)` and `(+1,-1)`;
- `firstInstance = 1` selects record two with buffer offset zero;
- a gradient remains continuous across two glyphs.

### Color

For every accepted material family:

- material output is linear premultiplied;
- intrinsic source alpha is preserved;
- `paintAlpha` is applied once;
- A8 coverage is applied once;
- CPU and GPU results remain within one channel LSB where a native oracle is
  available.

### Preflight and native ownership

- every composite/vertex/draw-uniform mutation has an exact code and zero
  native creation;
- one `writeTexture` per page and material sampled resource;
- frame-global buffers are each written once;
- one draw per subrun with exact first/count;
- rollback, completion, readback failure and close/recreate are close-once;
- FP-04 Core/Image and FP-05 Tasks 8/9 regressions remain green.

## Non-Goals

- no Graphite, Ganesh, SkSL compiler, shader dictionary or multi-backend port;
- no persistent glyph atlas or inter-frame resource residency;
- no Task 11 ColorGlyph native integration;
- no product gate, router or legacy allowlist change;
- no animation change;
- no GM regeneration or performance claim;
- no hidden workaround for wgpu4k or wgsl4k.

## Implementation Sequence

1. Correct and re-review Task 3 composable material/color contracts.
2. Replace the prototype shader with the 64-byte vertex ABI and deterministic
   text composer.
3. Extend and re-review Task 8 draw-uniform/composite planning.
4. Extend and re-review Task 9 zero-allocation composite preflight.
5. Resume Task 10 materialization, ownership and mixed-frame execution.
6. Run an independent end-to-end Tasks 3/8/9/10 review before proceeding to
   Task 11.
