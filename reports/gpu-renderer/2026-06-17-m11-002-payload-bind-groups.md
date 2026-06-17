# GPU Renderer M11-002 Payload Upload And Bind Groups

Date: 2026-06-17
Branch: `codex/kgpu-m11-002-payload-bind-groups`
Ticket: `KGPU-M11-002`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-002 | `done` | Added provider-owned payload upload and bind-group materialization contracts, structured resource-binding facts, scoped non-promotional telemetry, pass-command bind-group bridge rows, and adapter-backed uniform-only smoke evidence. | No remaining gate for this uniform/storage payload and resource-binding contract lane. Product route activation, release-blocking performance readiness, and live texture/sampler/image/text/runtime-effect materialization remain unclaimed. |

## Evidence

- `GPUPayloadMaterializationRequest` links `GPUUniformPayloadBlock`,
  `GPUUniformPayloadSlot`, `GPUResourceBindingBlock`,
  `GPUResourceBindingSlot`, `GPUPayloadUploadPlan`, reflected layout hash,
  generation, usage, alignment, budget, and upload capability facts to
  `GPUResourceProvider`.
- `ValidatingPayloadResourceProvider` emits provider-owned `UniformBuffer` and
  `BindGroup` command operands without exposing WGPU handles or putting payload
  values into material or pipeline keys.
- Upload evidence records byte size, alignment, payload generation, upload
  scope, upload plan, layout hash, pass scope, and zeroed padding.
- Bind-group evidence records layout hash, binding count, dynamic offsets,
  uniform-buffer operand label, descriptor labels, and structured binding facts
  for sampled/storage resources when present.
- Refusals cover missing upload capability, upload budget exhaustion, invalid
  upload byte ranges, stale device generation, stale resource binding
  generation, missing usage, binding layout mismatch, excessive dynamic
  offsets, fingerprint mismatch, unzeroed padding, invalid bytes, and missing
  structured facts for non-uniform resource labels.
- `GPUPassCommandStream.fromDrawPacketStream` can consume the materialized
  decision and dump `passes.command-bridge` rows for payload bind groups without
  raw backend handles.
- `GPUBackendUniformPayloadDraw` now requires a provider-materialized uniform
  buffer and bind group, verifies uniform bytes match the materialized
  `byteSize`, and the WGPU fullscreen helper uses that materialized byte count
  for the buffer allocation.
- `GPUBackendRuntimeWgpuSmokeTest` executed an adapter-backed uniform-only
  payload route that uploads a 64-byte payload block, binds it, renders the
  expected pixel, and preserves non-promotional evidence.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest --tests org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketCommandStreamTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeContractsTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest
rtk ./gradlew --no-daemon :gpu-renderer:check
```

Targeted RED runs first failed because the payload materialization provider,
payload telemetry, pass-command payload bridge, and runtime payload upload API
did not exist. Review-driven RED runs then failed for missing upload range
validation, missing sampled/storage binding facts, unscoped reuse telemetry,
and runtime payload draws that were not tied to provider materialization.
Those gaps now pass targeted tests and full module check.

## Review

Independent review `019ed672-4911-7211-9477-533f505cb64e` found initial P1/P2
blockers:

- adapter-backed smoke evidence bypassed provider materialization;
- `GPUPayloadUploadPlan.byteRanges` were not validated;
- sampled/storage resource binding facts were opaque or missing;
- create/reuse telemetry could cross pass-local payload scopes.

The first re-review found remaining P1/P2 blockers:

- runtime upload bytes were not validated against materialized `byteSize`;
- `storage:` labels could still pass without structured facts;
- accepted bind-group dumps omitted structured resource binding facts.

Fixes added runtime byte-size validation from the materialized uniform operand,
made storage and sampled descriptors require structured binding facts, dumped
kind/descriptor/usage/generation facts for accepted resource bindings, scoped
telemetry keys by target/pass/slot/generation/invalidation, and added refusal
fixtures for invalid upload ranges and incomplete resource facts.

Final re-review found no remaining P0/P1/P2 blockers.

## Non-Claims

- No product route activation.
- No release-blocking performance gate.
- No broad renderer support claim beyond this payload/bind-group execution
  boundary.
- No live image, texture, sampler, glyph, text, destination-read, saveLayer,
  stencil-cover, runtime-effect, paint dictionary, or blend-plan execution
  materialization.
- No Ganesh, Graphite, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
- No CPU-rendered compatibility fallback.
