# GPU Renderer M1 Product Activation

Date: 2026-06-23
Decision: Accepted
Activated by: user

## Decision

The first solid `FillRect` route is product-activated. The default mode changes
from `Disabled` to `ProductFlag`.

## Evidence

- KGPU-M1-001: activation policy accepted
- KGPU-M1-002: root PM bundle reports `ActivationCandidate`
- KGPU-M1-003: controlled flag `kanvas.gpu.renderer.product.fillRect` exists
- KGPU-M1-004: rollback/parity validation proves identical pixels
- `validateGpuRendererR6AdapterBackedPromotionReadinessBoundary`: boundary holds

## Changes

| File | Change |
|------|--------|
| `GpuRendererShadowAdapter.kt:70` | Default mode `Disabled` → `ProductFlag` |
| `GpuRendererShadowAdapter.kt:32-41` | Updated KDoc: ProductFlag is now the default |
| `GpuRendererShadowAdapter.kt:68` | Updated config KDoc: default is ProductFlag since M1 |
| `GpuRendererShadowAdapter.kt:84-86` | New property `kanvas.gpu.renderer.product.fillRect.disable` |
| `GpuRendererShadowAdapter.kt:99-109` | `fromSystemProperties`: disable wins, then shadow, default ProductFlag |

## Rollback

To restore legacy rendering:

```bash
-Dkanvas.gpu.renderer.product.fillRect.disable=true
```

## Non-Claims

- Only solid `FillRect` (identity/translate, WideOpen/DeviceRect clip,
  SolidColor, SrcOver, Root layer, rgba8unorm) is product-activated.
- RRect, gradients, paths, images, text, filters, runtime effects remain
  diagnostic-only or refused.
- `StrokeAndFill` decomposition remains legacy; fill component is recorded
  as first-route evidence but rendered through the legacy path.
- This is not a release-blocking gate and does not move readiness.
