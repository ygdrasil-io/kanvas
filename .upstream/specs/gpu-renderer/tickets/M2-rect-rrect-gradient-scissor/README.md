# M2 - Rect, RRect, Gradient, And Scissor Expansion

## Goal

Apply the reviewed first-route pattern to nearby native routes: rounded rects,
linear gradients, simple device-rect scissor clips, and conservative batching.

## Dependencies

Depends on M0 review completion and, for product activation, the M1 policy
decision. Implementation may remain evidence-only if M1 is still policy-gated.

## Exit Criteria

- [ ] RRect and linear-gradient routes include accepted and refusal evidence.
- [ ] Simple scissor clips are represented through `GPUClipPlan` dumps.
- [ ] Batching evidence proves key/payload/resource boundaries remain stable.
- [ ] No route claims complex clips, paths, image textures, or saveLayer support.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M2-001 - Add native `FillRRect` first expansion route](KGPU-M2-001-add-native-fillrrect-first-expansion-route.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | `KGPU-M0-007` | - |
| [KGPU-M2-002 - Add linear-gradient rect and rrect material route](KGPU-M2-002-add-linear-gradient-rect-and-rrect-material-route.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `materials-wgsl` | `KGPU-M2-001` | - |
| [KGPU-M2-003 - Add simple scissor clip route](KGPU-M2-003-add-simple-scissor-clip-route.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `clips-passes` | `KGPU-M2-001` | - |
| [KGPU-M2-004 - Add conservative batching and sort evidence](KGPU-M2-004-add-conservative-batching-and-sort-evidence.md) | `proposed` | `P1` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `analysis-recording` | `KGPU-M2-001`, `KGPU-M2-002`, `KGPU-M2-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
```

## Non-Claims

- No broad path, image, text, filter, or saveLayer support.
- No complex clip stack or destination-read support.
- No route activation without M1 policy acceptance.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
