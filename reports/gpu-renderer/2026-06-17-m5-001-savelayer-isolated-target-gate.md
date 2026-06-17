# GPU Renderer M5-001 SaveLayer Isolated Target Gate

Date: 2026-06-17
Branch: `codex/kgpu-m5-001-savelayer-isolated-target`
Ticket: `KGPU-M5-001`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M5-001 | `done` | Added `GPUSaveLayerIsolatedTargetPlanner`, extended `GPULayerPlan` contracts with dumpable target, initialization, resource, task, pass, and restore-composite facts, and added `SaveLayerIsolatedTargetGateTest`. Independent review accepted the evidence after mandatory usage enforcement and refusal-matrix coverage fixes. | Native adapter-backed saveLayer execution, readback/reference comparison, product activation, filters, arbitrary layer stacks, and destination reads remain unpromoted. |

## Evidence

- `SaveLayerIsolatedTargetGateTest` records the
  `gpu-renderer.savelayer.isolated-target` row with `routeKind=GPUNative`,
  `classification=TargetNative`, `promoted=false`,
  `productActivation=false`, and `materialized=false`.
- The accepted bounded transparent fixture dumps save record, device bounds,
  target descriptor hash, target owner/generation, mandatory
  `render_attachment`/`texture_binding` usage flags, clear/load/store policy,
  task order, pass separation, fixed-function `srcOver` restore composite, and
  resource ownership/release policy.
- Target descriptor hashes exclude child draw command ids, target generation,
  and layer scope ownership labels.
- Unsupported variants refuse with stable diagnostics:
  `unsupported.layer.bounds_unbounded`,
  `unsupported.layer.bounds_invalid`,
  `unsupported.layer.target_usage_missing`,
  `unsupported.layer.active_attachment_sampled`,
  `unsupported.layer.init_previous_unaccepted`,
  `unsupported.layer.backdrop_filter`,
  `unsupported.layer.filter_chain`,
  `unsupported.layer.restore_blend`,
  `unsupported.layer.cpu_fallback_forbidden`,
  `unsupported.layer.preserve_lcd_text`,
  `unsupported.layer.f16_unavailable`, and
  `unsupported.layer.target_too_large`.
- The target usage refusal covers caller attempts to drop the mandatory
  `texture_binding` usage from the contract as well as unavailable required
  usage labels.
- Oversized target budget checks saturate instead of overflowing before
  `unsupported.layer.target_too_large`.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.layers.SaveLayerIsolatedTargetGateTest
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-raster:test --tests '*Layer*' --tests '*Filter*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted test first failed in RED state because the saveLayer isolated
target planner and fields did not exist. After implementation, the targeted
test and full `:gpu-renderer:check` passed. A local review then added the
explicit pass dump, removed scope from the target descriptor hash, and hardened
the budget calculation against overflow; the targeted test and full check
passed again.

Current status count after moving KGPU-M5-001 to `done`:

```text
blocked 13
done 33
```

## Review

Local pre-PR review found two issues before PR publication:

- required evidence asked for a pass dump, while the initial implementation only
  exposed task and composite dumps;
- extreme target dimensions could overflow byte estimation before budget
  refusal.

Both findings were remediated before the ticket entered independent review.

Independent review then accepted the contract-gate boundary with two required
fixes:

- enforce internal saveLayer target usages so callers cannot produce a
  `GPUNative` plan without `render_attachment` and `texture_binding`;
- include every emitted unsupported-layer refusal in the deterministic test and
  report matrix.

Both required fixes were applied and revalidated with the targeted
`SaveLayerIsolatedTargetGateTest`, full `:gpu-renderer:check`, the M5
`gpu-raster` Layer/Filter validation bundle, `rtk git diff --check`, and the
status counter. The ticket is `done` as accepted contract-gate evidence only;
no product route activation or native saveLayer support is claimed.

## Non-Claims

- No product route activation.
- No adapter-backed native saveLayer execution.
- No materialized offscreen WebGPU target.
- No CPU-rendered full-layer texture fallback.
- No arbitrary layer stacks.
- No filters or backdrop filter support.
- No destination-read support.
- No release-blocking or readiness movement.
