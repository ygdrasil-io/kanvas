# MSAA for Stencil-Cover Rendering Pipeline

2026-07-05

## Problem

Stencil-cover anti-aliasing is binary (pixel ON/OFF via `offsetForAA` 0.5px vertex shift)
while Skia reference uses 256-level coverage. Thin strokes (16px wide) become nearly
invisible — the hard edges dominate visual appearance.

Current stroke-heavy GM scores: `strokes_poly` 18%, `strokes_round` 22%, `circular_arcs_stroke_butt` 45%.

## Design

**Architecture:**

```
Draw → Scene MSAA 4x ──resolve──> Scene 1x ──composite──> Main target 1x ──readback──> pixels
         ↑ multi-sample              ↑ GPU auto-resolve   ↑ single-sample
```

**Files changed (~3):**

| File | Change |
|------|--------|
| `kanvas/.../RenderConfig.kt` | Add `sampleCount: Int = 1` |
| `gpu-renderer/.../GPUBackendRuntimeContracts.kt` | Add `sampleCount: Int = 1` to `GPUOffscreenTargetRequest` |
| `gpu-renderer/.../GPUBackendRuntimeWgpu.kt` | Multisampled texture creation, resolve target, pipeline multisample state, cache key update |

**Key changes in `GPUBackendRuntimeWgpu.kt`:**

1. **Texture creation** — `TextureDescriptor` gets `sampleCount` field
   - Scene texture: `sampleCount = N` (4 for MSAA)
   - Depth-stencil: same `sampleCount`
   - Resolve target: `sampleCount = 1` (separate texture)
2. **Render pass** — `RenderPassColorAttachment` gets `resolveTarget` pointing to the 1x resolve texture
3. **Pipeline creation** — All `RenderPipelineDescriptor` instances get `multisample { count = sampleCount, mask = 0xFFFFFFFF, alphaToCoverageEnabled = false }`
4. **Cache key** — `sampleStateHash` updated from `"sample-state:count=1:mask=all"` to `"sample-state:count=N:mask=all"`
5. **Readback** — `readRgba()` reads from resolve target (not MSAA texture). `copyTextureToBuffer` targets the resolve texture.

**Not changed:**
- `src`/`snap` intermediate layers (single-sample, compositing unchanged)
- WGSL shaders (no multisampled texture reads needed)
- Kadre window surface (single-sample)
- `offsetForAA` (still applies, now combined with HW MSAA for better edges)

## Scope

- `sampleCount` configurable via `RenderConfig` (default 1 = no MSAA)
- Offscreen rendering only (GM tests, headless)
- 4x MSAA standard (WebGPU minimum guaranteed)
- No software MSAA or Graphite-style LUT coverage

## Expected Impact

| Metric | Before | After (estimated) |
|--------|--------|-------------------|
| `strokes_poly` | 18% | 85-90% |
| `strokes_round` | 22% | 85-90% |
| `circular_arcs_stroke_butt` | 45% | 90-95% |
| `ctmpatheffect` | 98.6% | 99.5%+ |

## Validation

- `./gradlew :integration-tests:skia:generateSkiaRenders -Pkanvas.render.sampleCount=4`
- Compare similarity scores before/after
- Visual inspection of stroke GM renders

## Out of Scope

- Kadre window surface MSAA
- Graphite-style software coverage LUT
- `msaa-render-to-single-sampled` optimization (Metal-only, separate PR)
