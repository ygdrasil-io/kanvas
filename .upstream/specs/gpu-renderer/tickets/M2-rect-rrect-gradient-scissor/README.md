# M2 - Rect, RRect, Gradient, And Scissor Expansion

## Goal

Apply the reviewed first-route pattern to nearby native routes: rounded rects,
linear gradients, simple device-rect scissor clips, and conservative batching.

## Dependencies

Depends on M0 review completion and, for product activation, the M1 policy
decision. Implementation may remain evidence-only if M1 is still policy-gated.

## Exit Criteria

- [x] RRect and linear-gradient routes include accepted and refusal evidence.
- [x] Simple scissor clips are represented through `GPUClipPlan` dumps.
- [x] Batching evidence proves key/payload/resource boundaries remain stable.
- [x] No route claims complex clips, paths, image textures, or saveLayer support.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M2-001 - Add native `FillRRect` first expansion route](KGPU-M2-001-add-native-fillrrect-first-expansion-route.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | `KGPU-M0-007` | - |
| [KGPU-M2-002 - Add linear-gradient rect and rrect material route](KGPU-M2-002-add-linear-gradient-rect-and-rrect-material-route.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `materials-wgsl` | `KGPU-M2-001` | - |
| [KGPU-M2-003 - Add simple scissor clip route](KGPU-M2-003-add-simple-scissor-clip-route.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `clips-passes` | `KGPU-M2-001` | - |
| [KGPU-M2-004 - Add conservative batching and sort evidence](KGPU-M2-004-add-conservative-batching-and-sort-evidence.md) | `done` | `P1` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `analysis-recording` | `KGPU-M2-001`, `KGPU-M2-002`, `KGPU-M2-003` | - |

## Closeout Evidence

- `M2SimpleSceneEvidenceTest` records the simple closeout scene as
  `scene:m2.simple.rrect-gradient-scissor-batch mode=contract-fixture`.
- The scene covers accepted rrect route-candidate evidence, linear-gradient
  material/payload/WGSL fixture evidence, `GPUClipPlan` device-scissor evidence,
  conservative batching evidence, and stable refusal lines.
- The GPU lane is explicitly skipped with
  `productRouteActivated=false`, `releaseBlocking=false`, and
  `readinessDelta=0.0`; this is not adapter-backed execution evidence.
- Independent review `019ec7aa-f95b-7f40-9f40-1bf80d87d2b9` accepted this
  contract-fixture evidence for `done` while preserving the non-claims below.

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.validation.M2SimpleSceneEvidenceTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
```

## Non-Claims

- No broad path, image, text, filter, or saveLayer support.
- No complex clip stack or destination-read support.
- No route activation without M1 policy acceptance.
- M2 closeout evidence is a contract fixture accepted by independent review;
  it is not adapter-backed execution evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
