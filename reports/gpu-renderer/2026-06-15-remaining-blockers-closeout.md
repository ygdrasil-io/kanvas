# GPU Renderer Remaining Blockers Closeout

## Scope

This closeout classifies the remaining GPU renderer tickets that were still
`proposed` after M10. No route implementation, product activation, default
legacy behavior change, or support promotion is included.

## Blocked Tickets

| Ticket | Blocker |
|---|---|
| `KGPU-M3-002` | Native WebGPU/adapter stencil-cover evidence for depth/stencil capability, producer-before-cover ordering, pass/resource/readback artifacts, and skipped/refusal diagnostics. |
| `KGPU-M4-004` | Native WebGPU/adapter sampler evidence for tile/filter/mipmap mapping, behavior-affecting key boundaries, unsupported cubic/aniso/perspective diagnostics, and reference/readback artifacts. |
| `KGPU-M5-001` | Native WebGPU/adapter saveLayer isolated-target evidence for offscreen allocation, clear/load/store, child isolation, restore composite, active-attachment separation, resource generation, and CPU/GPU/reference comparison. |
| `KGPU-M7-001` | Registered descriptor with Kotlin/CPU oracle, complete parser-validated WGSL/reflection through `wgsl4k`, route integration, adapter-backed execution/readback evidence, and unregistered-descriptor refusals. |
| `KGPU-M9-002` | Raw frame sample provenance from an owned adapter lane, warmup/variance policy, quarantine and rebaseline rules, negative threshold fixture, and skipped-lane diagnostics. |
| `KGPU-M9-003` | Accepted KGPU-M9-002 gate policy plus PM dashboard/manifest evidence that separates correctness, activation, performance, cache, and release readiness without moving readiness. |

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk rg -n '^status: (proposed|ready|in-progress|review)' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
rtk git diff --check
```

## Independent Review

- Review `019ec883-bd49-7741-b8d7-56e2e5f2e79d` accepted the closeout with no
  blocking findings. The reviewer confirmed that the six remaining `proposed`
  tickets are now `blocked`, the milestone tables and global totals are
  consistent, no actionable status remains, and the language does not widen
  product activation, readiness, release-blocking, or GPU route claims.

## Non-Claims

- No GPU route is promoted.
- No product route is activated.
- No release-blocking gate is created.
- No readiness delta is claimed.
- No CPU-rendered compatibility fallback is introduced.
- No Graphite/Ganesh port or SkSL compiler work is implied.

## Final State

After this closeout, the GPU renderer ticket catalog has no `proposed`,
`ready`, `in-progress`, or `review` tickets remaining. The remaining non-done
tickets are explicitly `blocked` with named adapter, dependency, policy, or
evidence gates.
