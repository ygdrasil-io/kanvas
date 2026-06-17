# GPU Renderer M5-001 SaveLayer Isolated Target Gate

Date: 2026-06-17
Branch: `codex/kgpu-m5-001-savelayer-isolated-target`
Ticket: `KGPU-M5-001`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M5-001 | `review` | Added `GPUSaveLayerIsolatedTargetPlanner`, extended `GPULayerPlan` contracts with dumpable target, initialization, resource, task, pass, and restore-composite facts, and added `SaveLayerIsolatedTargetGateTest`. | Independent review is still required before moving to `done`; native adapter-backed saveLayer execution, readback/reference comparison, product activation, filters, arbitrary layer stacks, and destination reads remain unpromoted. |

## Evidence

- `SaveLayerIsolatedTargetGateTest` records the
  `gpu-renderer.savelayer.isolated-target` row with `routeKind=GPUNative`,
  `classification=TargetNative`, `promoted=false`,
  `productActivation=false`, and `materialized=false`.
- The accepted bounded transparent fixture dumps save record, device bounds,
  target descriptor hash, target owner/generation, usage flags, clear/load/store
  policy, task order, pass separation, fixed-function `srcOver` restore
  composite, and resource ownership/release policy.
- Target descriptor hashes exclude child draw command ids, target generation,
  and layer scope ownership labels.
- Unsupported variants refuse with stable diagnostics:
  `unsupported.layer.bounds_unbounded`,
  `unsupported.layer.target_usage_missing`,
  `unsupported.layer.active_attachment_sampled`,
  `unsupported.layer.init_previous_unaccepted`,
  `unsupported.layer.backdrop_filter`,
  `unsupported.layer.filter_chain`,
  `unsupported.layer.restore_blend`,
  `unsupported.layer.cpu_fallback_forbidden`, and
  `unsupported.layer.target_too_large`.
- Oversized target budget checks saturate instead of overflowing before
  `unsupported.layer.target_too_large`.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerIsolatedTargetGateTest
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted test first failed in RED state because the saveLayer isolated
target planner and fields did not exist. After implementation, the targeted
test and full `:gpu-renderer:check` passed. A local review then added the
explicit pass dump, removed scope from the target descriptor hash, and hardened
the budget calculation against overflow; the targeted test and full check
passed again.

Current status count after moving KGPU-M5-001 to `review`:

```text
blocked 13
done 32
review 1
```

## Review

Local pre-PR review found two issues before PR publication:

- required evidence asked for a pass dump, while the initial implementation only
  exposed task and composite dumps;
- extreme target dimensions could overflow byte estimation before budget
  refusal.

Both findings were remediated. No independent review has accepted the ticket
yet, so the ticket remains `review` rather than `done`.

## Non-Claims

- No product route activation.
- No adapter-backed native saveLayer execution.
- No materialized offscreen WebGPU target.
- No CPU-rendered full-layer texture fallback.
- No arbitrary layer stacks.
- No filters or backdrop filter support.
- No destination-read support.
- No release-blocking or readiness movement.
