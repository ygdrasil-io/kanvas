# GPU Renderer M5-002 Destination Read Strategy Gate

Date: 2026-06-17
Branch: `codex/kgpu-m5-002-destination-read-strategy`
Ticket: `KGPU-M5-002`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M5-002 | `review` | Added `GPUDestinationReadStrategyPlanner`, destination-read action/copy/budget contracts, accepted/refused diagnostics, canonical copy/intermediate/binding/barrier/resource dumps, and `DestinationReadStrategyGateTest`. | Independent review is still required before moving to `done`. KGPU-M5-001 is also still `review`, so no destination-read support claim can promote until that dependency is accepted. Native adapter-backed destination-copy execution, readback/reference comparison, product activation, framebuffer fetch, input attachments, and CPU readback fallback remain unpromoted. |

## Evidence

- `DestinationReadStrategyGateTest` records the
  `gpu-renderer.destination-read.strategy` row with `routeKind=GPUNative`,
  `classification=TargetNative`, `promoted=false`,
  `productActivation=false`, and `materialized=false`.
- The accepted bounded target-copy fixture dumps destination-read requirement,
  conservative read/copy bounds, source target generation, copy texture
  descriptor hash, usage flags, binding layout/view/sampler hashes, pass split,
  copy-before-sample ordering token, budget class, and byte estimate.
- The validated intermediate fixture dumps `SampleExistingIntermediate`
  evidence without emitting a copy texture line.
- Accepted routes emit a non-terminal `accepted.destination_read.strategy`
  diagnostic while preserving the canonical dump lines.
- `NoDestinationRead`, `FixedFunctionAttachmentBlend`,
  `LayerCompositeIsolation`, and `RefuseDiagnostic` are outside this
  copy/intermediate gate and refuse with
  `unsupported.destination_read.strategy_unaccepted`.
- Material-key boundary evidence proves destination-read descriptor, binding,
  and target-generation hashes remain outside `MaterialKey`.
- Unsupported variants refuse with stable diagnostics:
  `unsupported.destination_read.bounds_unbounded`,
  `unsupported.destination_read.active_attachment_sampled`,
  `unsupported.destination_read.strategy_unaccepted`,
  `unsupported.destination_read.copy_usage_missing`,
  `unsupported.destination_read.strategy_action_mismatch`,
  `unsupported.destination_read.texture_binding_missing`,
  `unsupported.destination_read.intermediate_unvalidated`,
  `unsupported.destination_read.target_generation_stale`,
  `unsupported.destination_read.pass_split_illegal`,
  `unsupported.destination_read.framebuffer_fetch_unavailable`,
  `unsupported.destination_read.cpu_readback_forbidden`, and
  `unsupported.destination_read.copy_budget_exceeded`.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.destination.DestinationReadStrategyGateTest
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted test first failed in RED state because the destination-read
strategy planner, action contracts, and extended bounds fields did not exist.
After implementation, the targeted test, full `:gpu-renderer:check`,
`rtk git diff --check`, and the status-count command passed.

Current status count after moving KGPU-M5-002 to `review`:

```text
blocked 12
done 32
review 2
```

## Review

Local pre-PR review scope:

- check that accepted dumps include copy/intermediate, binding, barrier,
  resource, budget, and non-claim evidence;
- check that accepted routes emit non-terminal diagnostics;
- check that strategies outside the KGPU-M5-002 copy/intermediate scope refuse;
- check that strategy/action mismatches refuse instead of producing mixed route
  evidence;
- check that active-attachment sampling, copy usage, texture binding,
  generation, pass split, framebuffer fetch, CPU readback, and budget failures
  refuse before accepted dumps;
- check that destination-read bindings stay out of material-key evidence.

No independent review has accepted the ticket yet, so the ticket remains
`review` rather than `done`.

## Non-Claims

- No product route activation.
- No adapter-backed native destination-read execution.
- No materialized destination-copy WebGPU texture.
- No framebuffer fetch or input attachment support.
- No CPU readback fallback.
- No broad blend-mode support.
- No release-blocking or readiness movement.
