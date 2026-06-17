# GPU Renderer M11-005 Destination-Read Materialization

Date: 2026-06-17
Branch: `codex/kgpu-m11-005-destination-read-materialization`
Ticket: `KGPU-M11-005`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-005 | `done` | Added validated destination-read materialization for bounded target copies and existing intermediates; bridged copy texture, texture-view, and sampler operands into pass command evidence; emitted pass split/copy-before-sample ordering and stable refusal diagnostics. | Product route activation, broad blend/filter coverage, framebuffer fetch/input attachments, CPU readback fallback, and successful adapter readback remain unpromoted. |

## Evidence

- `GPUDestinationReadMaterializationRequest` carries the accepted strategy gate,
  packet stream, target/device generations, source/read usage labels, copy
  capability, copy budget, and intermediate validation facts.
- `ValidatingDestinationReadMaterializer` materializes accepted
  `TargetCopySnapshot` plans into a separate destination-copy texture resource
  plus `DestinationCopyTexture`, `TextureView`, and `Sampler` command operands.
- Destination-copy command streams show render-pass split ordering:
  `endRenderPass`, `copyTexture`, then a new render pass that samples the
  copied texture through the packet bind group.
- Existing-intermediate materialization binds a validated separate sampled
  texture and sampler without inserting a copy command.
- Refusal fixtures cover missing copy capability, missing `copy_src`,
  missing `copy_dst`, missing `texture_binding`, copy budget overflow, stale
  target generation, stale gate-plan generation, missing consumer packet
  streams, active-attachment sampling, stale intermediate generation,
  bounds/format/sample-count mismatch, and unvalidated intermediate facts.
- Adapter readback evidence is explicitly skipped:
  `failureReason=kgpu-m11-005.adapter-readback-not-promoted`.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.destination.DestinationReadLiveMaterializationTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Non-Claims

- No product route activation.
- No framebuffer fetch, input attachment, or active-attachment sampling support.
- No CPU readback fallback for product destination-read rendering.
- No broad blend, filter, backdrop, or arbitrary destination-read coverage.
- No successful adapter-backed readback claim.
- No Graphite, Ganesh, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
