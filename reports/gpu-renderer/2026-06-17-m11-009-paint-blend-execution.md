# GPU Renderer M11-009 Paint/Blend Execution Boundary

Date: 2026-06-17
Branch: `codex/kgpu-m11-009-paint-blend-execution`
Ticket: `KGPU-M11-009`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-009 | `done` | Added contract-only paint dictionary plus fixed-function blend execution materialization for accepted solid material routes; materialized payload upload, bind group, render-pipeline operand, and command-stream evidence; preserved stable refusals for shader/destination-read, target-state, material-key, payload-schema, alpha-plan, active-attachment, and resource-provider mismatches. | Successful adapter readback/reference diff, product route activation, shader blend execution, destination-read texture sampling, broad blend-mode support, and CPU fallback remain unpromoted. |

## Evidence

- `GPUPaintBlendExecutionRequest` consumes the accepted paint pipeline,
  material dictionary assembly, blend allowlist gate, payload materialization
  request, expected dictionary/root/snippet facts, target state hash, and
  render-pipeline cache key.
- `GPUPaintBlendExecutionKeys` derives render-pipeline preimages through
  `GPUPipelineKeys`; the command stream uses the compact render key while
  resource materialization evidence carries the separate pipeline cache key.
- Accepted material roots and snippet IDs are validated before pipeline
  materialization. Material key, payload values, payload fingerprints, and
  concrete resource operands remain outside the executable pipeline key.
- Fixed-function blend execution derives a canonical target-state hash from
  target format, blend state, and sample count before `BeginRenderPass`.
  Stale or incompatible target-state evidence refuses with
  `unsupported.paint_blend.target_state_mismatch`.
- Accepted execution delegates upload and bind-group validation to
  `ValidatingPayloadResourceProvider`, then adds a provider-owned
  `RenderPipeline` command operand with material, dictionary, root, snippet,
  blend-state, and `uniformValuesInKey=false` evidence.
- Command evidence preserves the Dawn-style order:
  `beginRenderPass`, `setRenderPipeline`, `setBindGroup`, `draw`,
  `endRenderPass`.
- Destination-read linkage remains visible in accepted and refused dumps:
  strategy, cited plan, cited plan strategy, and `activeAttachmentSampled` are
  emitted by the paint/blend boundary.
- Refusal fixtures cover shader-route refusal with an accepted destination-read
  plan, stale target state, stale pipeline cache key, material key mismatch,
  uniform payload schema mismatch, payload provider refusal, unsupported blend,
  missing destination-read strategy, destination-read plan mismatch,
  incompatible alpha plan, and active-attachment sampling.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.paintblend.PaintBlendExecutionBoundaryTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Review

Independent review found three P2 issues:

- Target-state evidence was trusted instead of validated against the blend
  plan.
- Refused shader/destination-read evidence dropped the cited destination-read
  plan linkage.
- Refusal fixtures did not cover all ticket-required unsupported blend,
  destination-read, alpha-plan, and active-attachment cases.

The follow-up patch added canonical target-state derivation and validation,
destination-read plan linkage in paint/blend dumps, and the missing refusal
fixtures. Re-review found no remaining P0/P1/P2 findings.

## Non-Claims

- No product route activation.
- No successful adapter-backed readback claim.
- No shader blend, framebuffer fetch, input attachment, or destination-read
  texture sampling execution.
- No broad blend-mode support beyond the fixed-function allowlist gate.
- No CPU-rendered paint/blend fallback.
- No arbitrary SkSL or WGSL compilation.
- No Graphite, Ganesh, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
