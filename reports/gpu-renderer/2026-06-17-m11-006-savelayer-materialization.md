# GPU Renderer M11-006 SaveLayer Materialization

Date: 2026-06-17
Branch: `codex/kgpu-m11-006-savelayer-materialization`
Ticket: `KGPU-M11-006`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-006 | `done` | Added validated saveLayer isolated-target materialization for accepted bounded transparent layers; bridged layer target texture, render-target, texture-view, and sampler operands into pass command evidence; emitted target prepare, clear, child render, and restore composite ordering plus stable refusal diagnostics. | Product route activation, broad saveLayer/filter/backdrop coverage, previous-content initialization, CPU layer fallback, and successful adapter readback remain unpromoted. |

## Evidence

- `GPUSaveLayerMaterializationRequest` carries the accepted isolated-target gate,
  parent/child pass labels, target state/load-store labels, device and target
  generations, available usage labels, allocation availability, budget, and
  actual target validation facts.
- `ValidatingSaveLayerMaterializer` materializes accepted plans into a
  provider-owned layer target texture plus `Texture`, `RenderTarget`,
  `TextureView`, and `Sampler` command operands.
- Command evidence preserves the required ordering:
  `prepareLayerTarget`, child `beginRenderPass`, `clearLayerTarget`,
  `renderLayerChildren`, child `endRenderPass`, parent `beginRenderPass`,
  `compositeLayer`, parent `endRenderPass`.
- Refusal fixtures cover allocation unavailable, missing texture-binding usage,
  target budget overflow, stale target generation, stale gate generation,
  bounds mismatch, format mismatch, sample-count mismatch, active parent
  attachment sampling, parent read/write aliasing, and propagated gate
  refusals.
- Adapter readback evidence is explicitly skipped:
  `failureReason=kgpu-m11-006.adapter-readback-not-promoted`.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerLiveMaterializationTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Non-Claims

- No product route activation.
- No arbitrary saveLayer, filter DAG, backdrop, previous-content, or layer-stack
  support.
- No CPU-rendered full-layer compatibility texture.
- No successful adapter-backed readback claim.
- No framebuffer fetch, input attachment, Graphite/Ganesh, Dawn C++, SkSL
  compiler, SkSL IR, or SkSL VM port.
