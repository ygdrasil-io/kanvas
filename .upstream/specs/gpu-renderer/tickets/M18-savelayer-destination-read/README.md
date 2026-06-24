# M18 - SaveLayer + Destination Read

## Goal

Deliver SaveLayer execution, restore compositing, and destination-read strategies with controlled product activation. Foundation for layer compositing.

## Dependencies

Depends on M12 (wgsl4k gate KGPU-M12-010). Wave 2 milestone.

## Exit Criteria

- [ ] SaveLayer allocates and manages offscreen textures correctly
- [ ] Restore composites child texture into parent with blend
- [ ] Destination-read copy strategy works for blend modes
- [ ] Intermediate strategy avoids unnecessary copies
- [ ] SaveLayer and destination-read routes are product-activated

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M18-001 - Add SaveLayer execution: offscreen target allocation + clear/load/store](KGPU-M18-001-savelayer-execution.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `layers-passes` | [KGPU-M12-010] | null |
| [KGPU-M18-002 - Add SaveLayer restore: composite child texture into parent with blend](KGPU-M18-002-savelayer-restore.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `layers-passes` | [KGPU-M18-001] | null |
| [KGPU-M18-003 - Add destination-read copy strategy: split pass + copy target texture](KGPU-M18-003-dst-read-copy.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `layers-passes` | [KGPU-M18-001] | null |
| [KGPU-M18-004 - Add destination-read intermediate strategy: bind existing intermediate texture](KGPU-M18-004-dst-read-intermediate.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `layers-passes` | [KGPU-M18-003] | null |
| [KGPU-M18-005 - Activate M18 routes: SaveLayer + destination read default ON with rollback](KGPU-M18-005-route-activation.md) | `done` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M18-001, KGPU-M18-002, KGPU-M18-003, KGPU-M18-004] | legacy saveLayer/restore |
| [KGPU-M18-006 - Add gpu-renderer-scenes evidence: savelayer-isolated, dst-read-strategy](KGPU-M18-006-scenes-evidence.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M18-001, KGPU-M18-002, KGPU-M18-003, KGPU-M18-004] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SaveLayer*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*LayerComposite*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DstRead*'
```

## Non-Claims

- No framebuffer-fetch
- No layer elision
- No backdrop filters
- No f16 or HDR
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
