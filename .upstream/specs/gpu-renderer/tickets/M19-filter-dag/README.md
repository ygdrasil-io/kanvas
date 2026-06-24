# M19 - Filter DAG

## Goal

Deliver Gaussian blur, ColorMatrix filter, and bounded filter DAG execution with controlled product activation.

## Dependencies

Depends on M18 SaveLayer execution (KGPU-M18-001). Wave 2 milestone.

## Exit Criteria

- [ ] Gaussian blur renders correctly with 2-pass separable algorithm
- [ ] ColorMatrix filter produces correct color transformations
- [ ] Filter DAG chains up to 2 nodes correctly
- [ ] All filter routes are product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M19-001 - Add Gaussian blur filter: 2-pass H/V separable blur with downsample/upsample](KGPU-M19-001-gaussian-blur.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `filters-passes` | [KGPU-M18-001] | null |
| [KGPU-M19-002 - Add ColorMatrix filter: 4x5 matrix + vector multiply in WGSL](KGPU-M19-002-color-matrix.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `filters-passes` | [KGPU-M18-001] | null |
| [KGPU-M19-003 - Add filter DAG execution: multi-node graphs with intermediate texture ownership](KGPU-M19-003-filter-dag-execution.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `filters-passes` | [KGPU-M19-001, KGPU-M19-002] | null |
| [KGPU-M19-004 - Activate M19 routes: Blur + ColorMatrix + bounded filter DAG default ON](KGPU-M19-004-route-activation.md) | `proposed` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M19-001, KGPU-M19-002, KGPU-M19-003] | legacy drawFilter |
| [KGPU-M19-005 - Add gpu-renderer-scenes evidence: blur-radius-ladder, color-matrix-filter](KGPU-M19-005-scenes-evidence.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M19-001, KGPU-M19-002, KGPU-M19-003] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Blur*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ColorMatrix*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FilterDAG*'
```

## Non-Claims

- No Picture, RuntimeShader, or arbitrary SkSL filters
- Bounded DAG: 2-node max
- No per-channel LUT or color space conversion
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
