# Task 7 — W4e materialization report

## Delivered

- Native sealed W4e materialization executes initialize, producer, UNORM8 fold,
  mask consumer, hard-mask stencil, inverse-domain, readback, and completion paths
  without the legacy multi-render route.
- W4e has independent typed scene-MSAA continuation authority. Intermediate scene
  passes use `Skip`; only the sealed final pass resolves the canonical scene target.
- The attachment pool retains exact mask inventories and a direct inverse-domain
  inventory with no hidden ping-pong masks. All scene D24S8 attachments, including
  ordinary stencil scene paths, are declared by the frame authority, budgeted, and
  leased by resource identity.
- `InverseDomain.Zero` stays a direct finite-domain cover without D24S8. Geometry
  uses its declared scene D24S8: it initializes the finite domain in stencil, then
  rasterizes the sealed original geometry to remove the interior before sampling the
  inverse cover.
- Pool cleanup retains failed closes for retry, prevents new allocations while close
  cleanup is pending, and terminalizes leases independently of cleanup failures.

## Verification

- `rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4e*' --tests '*ClipStencil*' --tests '*CoverageMask*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks` — passed.
- `rtk ./gradlew :gpu-plan:test :gpu-renderer:test` — `gpu-plan` passed; the renderer
  full suite reported 14 pre-existing out-of-scope failures (package-boundary fixture,
  image/material expectations, native smoke/counter expectations, and two W4a/W4b
  lease-cleanup tests). The W4e-targeted gate above was clean.
- `rtk git diff --check` — passed.

The public native hard inverse-domain frame proof completed and read back the correct
finite-domain exterior with its non-rectangular interior hole. The AA4 proof skips on
the local adapter when the native planning capability is genuinely absent; no capability
is synthesized.
