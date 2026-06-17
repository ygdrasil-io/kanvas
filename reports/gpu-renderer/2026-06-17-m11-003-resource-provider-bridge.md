# GPU Renderer M11-003 Resource Provider Bridge

Date: 2026-06-17
Branch: `codex/kgpu-m11-003-resource-provider-bridge`
Ticket: `KGPU-M11-003`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-003 | `done` | Added provider-owned materialized command operand references, provider request/plan validation, packet-to-pass-command bridge evidence, first-route submit bridge requirements, refusal diagnostics, skipped-readback evidence, and independent review acceptance. | Product route activation, adapter-backed payload/bind-group/texture/sampler/destination/layer/stencil/runtime-effect lanes, and release-blocking promotion remain unclaimed and owned by later M11 tickets. |

## Evidence

- `GPUMaterializedCommandOperandKind` and
  `GPUMaterializedCommandOperandReference` cover render/compute pipelines,
  buffers, bind groups, textures, views, samplers, render targets,
  destination copies, and readback resources as backend-neutral operand refs.
- Operand dumps include device generation, owner scope, usage facts,
  invalidation policy, descriptor hash, and deterministic evidence facts.
- `GPUCommandOperandMaterializationPlan` and
  `GPUCommandOperandMaterializationRequest` validate dumpable fields before
  accepted refs or refused diagnostics can leak WGPU/facade handle-like text.
- `ValidatingCommandOperandResourceProvider` returns provider-owned
  `GPUMaterializedCommandOperandBinding` rows for accepted first-route command
  operands and refuses target mismatch, stale device generation, missing usage,
  and evicted resources before encoding/submission.
- `GPUPassCommandStream.fromDrawPacketStream` consumes the provider
  materialization decision and emits `passes.command-bridge` rows from packet
  refs to pass-command operands, including pass-level `beginRenderPass`
  target operands.
- `GPUFirstRouteRenderSubmitRequest` now requires bridged materialized command
  operands; loose operand refs are rejected for first-route submit handoff.
- Surface lease provider refusals cover stale generation, missing usage,
  evicted resource, target mismatch, frame mismatch, and active attachment
  sampling without dumping backend refs.
- Skipped readback evidence remains explicit through
  `GPUReadbackResult.Skipped`; adapter-requested evidence does not become a
  fake submitted/readback success.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProviderTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContextTest --tests org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketCommandStreamTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProviderTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

Targeted RED runs failed before the provider operand bridge, pass-stream
materialization handoff, evicted refusal, handle-like field filtering, and
first-route bridged-operand requirement existed. The final targeted and full
module checks pass.

## Review

Independent review `019ed650-f089-76c2-ad04-a0357e5cfb74` initially found
provider/bridge and handle-leak blockers:

- the only materialized provider path returned no command operands;
- `GPUPassCommandStream` accepted a synthetic bridge not tied to provider
  materialization;
- accepted operand refs filtered too late to protect refused provider dumps;
- evicted/readback evidence was not explicit enough.

Fixes added provider-produced operand bindings, made pass lowering consume
`GPUResourceMaterializationDecision.Materialized`, required bridged operands at
first-route submit, filtered provider request/plan/binding dump fields, and
strengthened refusal/readback tests.

Re-review found no remaining P0/P1/P2 blockers. A P3 note that direct
`GPUMaterializedCommandOperandBinding` construction could carry handle-like
packet/command labels was also fixed and covered by
`GPUResourceProviderTest`.

## Non-Claims

- No product route activation.
- No release-blocking performance gate.
- No broad renderer support claim beyond this resource-provider bridge
  contract lane.
- No actual queue submission or completed readback claim from this bridge
  alone.
- No Ganesh, Graphite, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
- No CPU-rendered compatibility fallback.
- No M11 payload upload, texture/sampler, destination-read, saveLayer,
  stencil-cover, runtime-effect, or paint/blend execution-lane completion.
