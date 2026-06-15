# GPU Renderer M1-003 Controlled First-Route Flag

Date: 2026-06-14
Ticket: `KGPU-M1-003`
Branch: `codex/gpu-renderer-m1-wave`

## Scope

KGPU-M1-003 adds an explicit local flag for the first solid `FillRect` route:
`kanvas.gpu.renderer.product.fillRect`. The default mode remains disabled.
When the flag is enabled, `gpu-raster` records a `ProductFlagged` diagnostic
candidate and keeps the legacy `drawRect` route available for the actual
rendering path.

## Evidence

- Default config remains `GpuRendererShadowMode.Disabled`.
- Shadow evidence remains gated by `kanvas.gpu.renderer.shadow.fillRect`.
- Product flag mode is gated by `kanvas.gpu.renderer.product.fillRect` and wins
  over shadow mode only when explicitly set.
- Product flag diagnostics include `mode=product-flag`,
  `status=product-flagged`, `productFlag=true:solid-fill-rect`,
  `legacyRouteAvailable=true`, and `cpuFallback=false`.
- Internal config rejects inconsistent mode/flag pairs, so `ProductFlag` cannot
  be constructed with a disabled flag state and shadow mode cannot carry an
  enabled product flag state.
- Legacy device test compares disabled and product-flagged pixels and keeps
  them byte-identical.
- `StrokeAndFill` refuses product-flag expansion with
  `unsupported.adapter.paint_style`; it is not converted into a product-flagged
  route.
- No `GPUCommandSubmission.Submitted`, `execution.submission:submitted`,
  `GPUReadbackResult.Completed`, or executed PM bundle marker appears in the
  product-flag diagnostic dump.

## TDD Evidence

- RED: `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererShadowAdapterTest`
  failed before implementation on missing `ProductFillRectProperty`,
  `ProductFlag`, `ProductFlagged`, `productFlag`, and `legacyRouteAvailable`.
- RED: `rtk ./gradlew --no-daemon :gpu-raster:test --tests 'org.skia.gpu.webgpu.GpuRendererShadowAdapterTest.product flag mode does not expand stroke and fill into product flagged route'`
  failed while `StrokeAndFill` was still accepted by product flag mode.
- GREEN: `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererShadowAdapterTest`
  passed after the flag implementation and `StrokeAndFill` refusal guard.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk rg -n 'productRouteActivated[=:][[:space:]]*true|Product route activated:[[:space:]]*`true`|releaseBlocking[=:][[:space:]]*true|Release blocking:[[:space:]]*`true`|readinessDelta[=:][[:space:]]*[1-9]|Readiness delta:[[:space:]]*`[1-9]' gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/GpuRendererShadowAdapter.kt gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GpuRendererShadowAdapterTest.kt .upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation reports/gpu-renderer/2026-06-14-m1-003-controlled-first-route-flag.md reports/gpu-renderer/2026-06-14-m1-m2-ticket-wave.md
rtk git diff --check
```

All commands above passed after RED/GREEN implementation. The targeted claim
scan returned no matches.

## Non-Claims

- This does not enable the first route by default.
- This does not remove or bypass legacy `drawRect` behavior.
- This does not add rollback/parity validation; that remains KGPU-M1-004.
- This does not claim broad GPU/WebGPU support beyond the solid `FillRect`
  flag diagnostic.
- This does not make the route release-blocking or move readiness.

## Review

Independent review `019ec724-9088-7512-b14c-e5c5090e84dd` approved moving
KGPU-M1-003 to `done`.

## Remaining Gate

None for KGPU-M1-003. The next M1 gate is KGPU-M1-004: rollback and parity
validation for the controlled first-route flag.
