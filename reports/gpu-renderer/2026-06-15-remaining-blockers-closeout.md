# GPU Renderer Remaining Blockers Closeout

## Scope

This closeout classifies the remaining GPU renderer tickets that were still
`proposed` after M10. No route implementation, product activation, default
legacy behavior change, or support promotion is included.

## Blocked Tickets

| Ticket | Blocker |
|---|---|
| `KGPU-M4-004` | Native WebGPU/adapter sampler evidence for tile/filter/mipmap mapping, behavior-affecting key boundaries, unsupported cubic/aniso/perspective diagnostics, and reference/readback artifacts. |
| `KGPU-M5-001` | Native WebGPU/adapter saveLayer isolated-target evidence for offscreen allocation, clear/load/store, child isolation, restore composite, active-attachment separation, resource generation, and CPU/GPU/reference comparison. |
| `KGPU-M7-001` | Registered descriptor with Kotlin/CPU oracle, complete parser-validated WGSL/reflection through `wgsl4k`, route integration, adapter-backed execution/readback evidence, and unregistered-descriptor refusals. |
| `KGPU-M9-003` | PM dashboard/manifest evidence that separates correctness, activation, performance, cache, and release readiness without moving readiness. |

## Resolved After Closeout

| Ticket | Resolution |
|---|---|
| `KGPU-M3-002` | Completed on 2026-06-17 by `reports/gpu-renderer/2026-06-17-m3-002-stencil-cover-gate-contract.md` as independently reviewed contract-gate evidence with stable skipped-lane refusals. The native stencil-cover route remains non-promoted and product activation stays false. |

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk rg -n '^status: (proposed|ready|in-progress)' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
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

After the 2026-06-17 KGPU-M3-002 follow-up, the GPU renderer ticket catalog has
no `proposed`, `ready`, `in-progress`, or `review` tickets remaining. The
remaining non-done tickets are explicitly `blocked` with named adapter,
dependency, policy, or evidence gates.

## 2026-06-15 M6 Follow-Up

After the font/text handoff evidence landed on master, KGPU-M6-001 and
KGPU-M6-004 were completed by
`reports/gpu-renderer/2026-06-15-m6-001-text-handoff.md` and
`reports/gpu-renderer/2026-06-15-m6-004-text-representation-gates.md`.
At that point, the catalog total was 29 `done` and 17 `blocked`; no `proposed`,
`ready`, `in-progress`, or `review` GPU renderer tickets remain.

## 2026-06-16 M9-002 Follow-Up

KGPU-M9-002 was completed by
`reports/gpu-renderer/2026-06-16-m9-002-frame-gate-policy-owned-samples.md`.
The evidence adds explicit frame gate policy contracts, negative and skipped
fixtures, a WebGPU offscreen raw sample artifact for `frame-gate-blocker-board`,
and independent review `019ed26f-3531-7fd0-8e5d-61f9a15d5a9a`. The sampled
lane remains candidate/non-release-blocking because its coefficient of
variation is `0.1472`.

The current catalog total is 31 `done` and 15 `blocked`; no `proposed`,
`ready`, `in-progress`, or `review` GPU renderer tickets remain.
