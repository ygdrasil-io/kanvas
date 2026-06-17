# GPU Renderer M11-007 Stencil-Cover Lane

Date: 2026-06-17
Branch: `codex/kgpu-m11-007-stencil-cover-lane`
Ticket: `KGPU-M11-007`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-007 | `done` | Added validated bounded stencil-cover live materialization for an accepted `GPUStencilCoverPlan`; materialized pass-local depth/stencil attachment and render-pipeline operands; emitted distinct producer and cover packet identities with `producer-before-cover` ordering; preserved stable refusal diagnostics and skipped-readback evidence. | Product route activation, broad path coverage, atlas/tessellation/compute/stroke support, inverse fill, broad clip stacks, and successful adapter readback remain unpromoted. |

## Evidence

- `GPUStencilCoverMaterializationRequest` carries the accepted geometry gate,
  pass/target state labels, device/resource generations, available attachment
  usage labels, attachment allocation and budget facts, actual
  depth/stencil/bounds/sample facts, stencil compare/write-mask facts, and
  distinct producer/cover packet IDs.
- `ValidatingStencilCoverMaterializer` materializes accepted plans into a
  pass-local stencil attachment texture plus `Texture`,
  `DepthStencilAttachment`, and `RenderPipeline` command operands.
- Command evidence preserves the required ordering:
  `prepareStencilAttachment`, `beginRenderPass`, `clearStencilAttachment`,
  `stencilCoverProducer`, `stencilCoverDraw`, `endRenderPass`.
- Producer and cover commands carry distinct packet IDs:
  `stencil-producer:path-triangle-v1` and `stencil-cover:path-triangle-v1`.
- Depth/stencil facts include format, load/store policy, clear value, compare,
  write mask, sample count, and attachment usage labels.
- Refusal fixtures cover missing attachment support, missing attachment usage,
  attachment budget overflow, stale resource generation, sample-count
  mismatch, bounds mismatch, depth/stencil format mismatch, compare mismatch,
  write-mask mismatch, clear-value mismatch, ordering mismatch, and propagated
  gate refusals.
- Adapter readback evidence is explicitly skipped:
  `failureReason=kgpu-m11-007.adapter-readback-not-promoted`.
- No native support claim is promoted without successful readback/reference diff
  evidence; the row remains evidence-only with `adapterBacked=false` and
  `productActivation=false`.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.StencilCoverLiveMaterializationTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Review

Independent review found three P2 issues:

- Stencil compare/write-mask were not validated before materialization.
- Producer and cover commands lacked separate packet identities.
- Readback/reference evidence was not linked outside the unit test.
- Re-review found the same missing validation pattern for stencil clear value.

The follow-up patch added stable compare/write-mask refusals, packet IDs on
producer/cover commands and operand bridges, this report with explicit
skipped-readback non-claim evidence, and then a stable clear-value refusal.

## Non-Claims

- No product route activation.
- No broad path fill, path atlas, tessellation, compute path, stroke, inverse
  fill, or broad clip-stack support.
- No CPU-rendered path texture fallback.
- No successful adapter-backed readback claim.
- No Graphite, Ganesh, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
