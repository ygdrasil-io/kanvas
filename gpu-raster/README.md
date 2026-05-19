# `:gpu-raster` — WebGPU backend for `kanvas-skia`

WebGPU-backed implementation of the `:kanvas-skia` device abstraction. Hosts `SkWebGpuDevice` (sibling of `SkBitmapDevice` from `:cpu-raster`) and its WebGPU plumbing (context, headless target, shader resources, present pass).

The module was extracted from `:kanvas-skia` in Phase G1 of [MIGRATION_PLAN_GPU_WEBGPU.md](../MIGRATION_PLAN_GPU_WEBGPU.md). Raster consumers of `:kanvas-skia` don't pay the `wgpu4k-toolkit` native binary cost (~50 MB Metal/Vulkan/DX) until they explicitly opt into this module.

The full phase-by-phase plan and post-mortems live in [MIGRATION_PLAN_GPU_WEBGPU.md](../MIGRATION_PLAN_GPU_WEBGPU.md). This README documents the **post-G7 steady state** — architecture choices, the `SkShader` → WGSL mapping, and how to add a new shader type.

---

## Architecture choices

### No Ganesh, no SkSL, hand-written WGSL

The GPU backend does **not** port Skia's `Ganesh` GL/Vulkan/Metal pipelines, nor does it run a SkSL → WGSL transpiler. Every shader under [`src/main/resources/shaders/`](src/main/resources/shaders/) is hand-written WGSL targeted at the exact set of features the cross-test GMs exercise. Rationale (see plan §"Pourquoi diverger") :

- The CPU rasterizer already produces a byte-accurate reference for every GM. The GPU backend only needs to be **functionally equivalent** within a per-test similarity floor, not an exhaustive port.
- WGSL is the single supported shader source ; no source-translation step means no SkSL fork to maintain.
- Each shader is ~100-300 lines of WGSL with explicit uniform layout — fast to read, fast to extend.
- Skia's `Ganesh` is GL/Vulkan-centric ; WebGPU's bind-group model is closer to Vulkan but flat — directly translating Ganesh's resource graph would import accidental complexity.

### Pipeline-cache strategy

Render pipelines are expensive to build but cheap to bind. `SkWebGpuDevice` caches them per-shader using the smallest key that captures the axes the WGSL doesn't already specialise on at runtime :

| Shader family | Cache key |
|---------------|-----------|
| `solid_color.wgsl` (rects) | `SkBlendMode` |
| `solid_polygon.wgsl` (non-AA polygons) | `SkBlendMode` |
| `aa_polygon.wgsl` (AA single-contour) | `(SkBlendMode, SkPathFillType)` |
| `aa_stencil_cover.wgsl` (AA multi-contour) | `(SkBlendMode, SkPathFillType, CoverageSide)` |
| gradient + bitmap shaders | `(SkBlendMode, SkFilterMode)` for bitmap ; tile mode + working colorspace are runtime uniforms |
| `layer_composite.wgsl` (saveLayer) | `SkBlendMode` |
| `blur_gaussian.wgsl` / `blur_image_filter.wgsl` | `SkBlendMode` (V-pass blends ; H-pass is internal so doesn't blend) |

Cache eviction : never. Pipelines are stable across the device's lifetime ; the set is bounded by `SkBlendMode.values().size * <fillType axes>`.

### Bind-group layout convention

Every draw shader follows the same skeleton :

- **Group 0 / binding 0** — uniform buffer (`<Shader>Uniforms` struct, viewport + per-draw payload).
- **Group 0 / binding 1+** — sampler + texture for shaders that read source pixels (`bitmap_shader`, `layer_composite`, `blur_*`, `present_pass`).
- **Group 1** — vertex buffer when the shader runs over a triangle list (polygon / stencil-cover).

The bind-group layout is created once per pipeline at module init and reused across every draw. Uniform buffer payloads are uploaded via `writeBuffer` at flush time, one buffer per `PendingDraw` in the queue.

### Premul sRGB-coded intermediate convention

All draw shaders write **premul sRGB-coded** values into the intermediate render target. The hardware blend stage operates on these sRGB-coded values directly — i.e. the WebGPU pipeline does **not** flip the attachment to a `*-srgb` view that would auto-linearise. Rationale lives in the `intermediateTexture` KDoc on [`SkWebGpuDevice`](src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt) ; short version :

- The CPU raster reference (`RasterSinkF16`) blends F16 values in the **destination's encoding space**, which is shape-equivalent to sRGB-coded blending on the GPU.
- Switching the GPU intermediate to a true linear working space (so the blend hardware sees linear premul) regresses translucent-stacking GMs by 30-65 percentage points against the cross-test reference. This was measured at G6.2 and is the trade-off documented in the `intermediateTexture` KDoc.
- F16 still buys sub-byte precision in the intermediate ; gradient stop lerps and edge-coverage products no longer quantise to 8-bit before the readback re-encoding.

### F16 vs RGBA8Unorm intermediate

The intermediate format defaults to `GPUTextureFormat.RGBA16Float` (F16). Callers on drivers that don't support F16 blending or are memory-constrained can pass `RGBA8Unorm` to fall back to the G6.1 behaviour. The final readback target is always `RGBA8Unorm` — the format toggle only affects the intermediate.

### Present pass and colorspace transform

A dedicated present pass copies the intermediate to the readback target. Two variants :

- `present_identity.wgsl` — identity copy (intermediate's sRGB-coded bytes flow through unchanged).
- `present_pass.wgsl` — applies the sRGB → linear → Rec.2020 → encoded transform so readback bytes are in `DM_REFERENCE_COLOR_SPACE` (cross-test convention against `original-888/*.png`).

The toggle is the `applyColorspaceTransform` constructor flag on `SkWebGpuDevice`. `WebGpuSink` (cross-tests) sets it to `true` ; unit tests that compare against raw sRGB bytes leave it at the default `false`.

---

## `SkShader` → WGSL mapping

Every `SkShader` subtype the cross-test set exercises maps to one or two WGSL files. The "rect path" variant is the analytical full-screen quad with a per-pixel coverage formula (G3.1.1) ; the "AA stencil-cover" variant is the multi-contour path renderer (G3.3b.3) that calls back into the same colour-resolution helper from a cover-pass fragment shader.

| `SkShader` | Rect path | AA stencil-cover path |
|------------|-----------|------------------------|
| Solid colour (no shader)         | [`solid_color.wgsl`](src/main/resources/shaders/solid_color.wgsl) | [`aa_stencil_cover.wgsl`](src/main/resources/shaders/aa_stencil_cover.wgsl) |
| `SkLinearGradient`               | [`linear_gradient.wgsl`](src/main/resources/shaders/linear_gradient.wgsl) | [`aa_stencil_cover_gradient.wgsl`](src/main/resources/shaders/aa_stencil_cover_gradient.wgsl) |
| `SkRadialGradient`               | [`radial_gradient.wgsl`](src/main/resources/shaders/radial_gradient.wgsl) | [`aa_stencil_cover_radial_gradient.wgsl`](src/main/resources/shaders/aa_stencil_cover_radial_gradient.wgsl) |
| `SkSweepGradient`                | [`sweep_gradient.wgsl`](src/main/resources/shaders/sweep_gradient.wgsl) | [`aa_stencil_cover_sweep_gradient.wgsl`](src/main/resources/shaders/aa_stencil_cover_sweep_gradient.wgsl) |
| `SkConicalGradient` (general)    | [`conical_gradient.wgsl`](src/main/resources/shaders/conical_gradient.wgsl) | [`aa_stencil_cover_conical_gradient.wgsl`](src/main/resources/shaders/aa_stencil_cover_conical_gradient.wgsl) |
| `SkConicalGradient` (focal inside) | [`conical_focal_gradient.wgsl`](src/main/resources/shaders/conical_focal_gradient.wgsl) | [`aa_stencil_cover_conical_focal_gradient.wgsl`](src/main/resources/shaders/aa_stencil_cover_conical_focal_gradient.wgsl) |
| `SkConicalGradient` (strip)      | [`conical_strip_gradient.wgsl`](src/main/resources/shaders/conical_strip_gradient.wgsl) | — |
| `SkBitmapShader`                 | [`bitmap_shader.wgsl`](src/main/resources/shaders/bitmap_shader.wgsl) | [`aa_stencil_cover_bitmap_shader.wgsl`](src/main/resources/shaders/aa_stencil_cover_bitmap_shader.wgsl) |

The non-AA polygon path (`solid_polygon.wgsl` + `aa_polygon.wgsl`) handles the geometry side ; the shader-resolution helpers are inlined per file because WGSL has no `#include` and a literal include preprocessor was deemed not worth its complexity for a finite shader set.

### Analytical clip-shape uniform

Every shader in the table above carries a `clipKind` + `clipShapeBounds` (+ `clipShapeRx`, `clipShapeRy`) uniform slot. When `clipKind == CLIP_KIND_NONE`, the slot is ignored. When `clipKind == CLIP_KIND_RRECT`, the fragment shader multiplies its coverage by `rrect_cov(pos, clipShapeBounds, clipShapeRx, clipShapeRy)` so analytical rounded-rect clips are resolved per-pixel without a stencil pass. The slot layout is identical across all shaders so the device-side `PendingDraw` packing is shared.

### Color management

All non-trivial shaders carry a 5-uniform colorspace block — sentinel + 3x3 matrix + two `vec4f` of parametric transfer function coefficients :

| Uniform       | Type        | Purpose |
|---------------|-------------|---------|
| `csMode`      | sentinel    | `0 = sRGB (no transform)`, `1 = SRGB_TF_MATRIX (sRGB transfer + matrix)`, `2 = PARAMETRIC_TF_MATRIX (full 7-coefficient transfer + matrix)` |
| `csMatrix`    | `mat3x3<f32>` | source-linear → sRGB-linear primaries matrix (column-major) |
| `csTfParams0` | `vec4f`     | `(g, a, b, c)` parametric transfer coefficients |
| `csTfParams1` | `vec4f`     | `(d, e, f, _)` parametric transfer coefficients |

The same packing covers sRGB / Display P3 / Rec.2020-linear / Adobe RGB inputs. Display P3 hits the `SRGB_TF_MATRIX` fast path (matrix-only, transfer reuses the sRGB curve). Adobe RGB and Rec.2020-linear hit the parametric path. HDR colour spaces (Rec.2100 PQ / HLG) are **deferred** — `SkColorSpace` parses them on the CPU, but the GPU shaders don't yet accept the PQ / HLG curves.

---

## How to add a new shader type

Concrete recipe (mirrors how `SkSweepGradient` was added in G4.3) :

1. **Write the WGSL file** under `src/main/resources/shaders/<your-shader>.wgsl`. Required uniform slots :
   - `viewport: vec2f` (device-pixel width/height).
   - `clipKind` + `clipShapeBounds` + `clipShapeRx` + `clipShapeRy` analytical clip-shape slots (use the existing constants : `CLIP_KIND_NONE`, `CLIP_KIND_RRECT`).
   - The 5-uniform colorspace block (`csMode`, `csMatrix`, `csTfParams0`, `csTfParams1`) if the shader is reachable from a path that may carry a non-sRGB working colorspace. If the shader is sRGB-only (e.g. a debug overlay), document this on the file.
   - Your per-draw payload (gradient stops, focal point, tile mode, ...).
2. **Add a `PendingDraw` sealed-interface variant** in `SkWebGpuDevice.kt` carrying the per-draw payload (geometry + colours + your new uniforms). Reuse the analytical clip-shape + colour-filter packing from existing variants — they're already wired through `flush()`.
3. **Add a pipeline cache** keyed by the axes your shader actually specialises on (typically `SkBlendMode`, plus `SkPathFillType` if the shader is reachable via the AA stencil-cover path, plus `SkFilterMode` if it samples a texture). Mirror one of the `MutableMap<...>` caches in the file — they all follow the same shape.
4. **Implement an `enqueue<X>DrawResources` builder** that :
   - Packs the WGSL uniform buffer (use the `Float32Array` / `writeBuffer` helpers in the file).
   - Creates the bind group with the cached layout.
   - Adds the `PendingDraw` to the device's queue.
   - Returns nothing — `flush()` later iterates the queue and binds each entry's pipeline + bind group.
5. **Wire dispatch in the appropriate `draw<X>` entry point** (`drawRect`, `drawPath`, `drawImageRect`, ...) by detecting your `SkShader` subtype on the paint and routing to the new `enqueue<X>DrawResources` instead of the default solid-colour path.
6. **Add a cross-test** under `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/` using `runGpuCrossTest(MyGM(), floor = ...)` or `runCrossBackendTest(MyGM(), rasterFloor = ..., gpuFloor = ...)`. The harness handles WebGPU adapter skip, similarity floor, debug-image dump, and (cross-backend) the per-pixel diff PNG when a backend slips into the warning band. See [`testing/CrossTestHarness.kt`](src/test/kotlin/org/skia/gpu/webgpu/testing/CrossTestHarness.kt) and [`testing/CrossBackendHarness.kt`](src/test/kotlin/org/skia/gpu/webgpu/testing/CrossBackendHarness.kt) for the contract.

The whole add-a-shader loop is intentionally non-DRY across WGSL files — every shader inlines the colorspace + clip-shape + colour-filter helpers it needs. Adding a new shader is a copy-paste from the closest existing one + the per-shader payload changes ; nothing forces you to refactor the existing shaders to share a base file.

---

## Effects layered pipeline (post-G7)

Effects layer on top of the shader table above without touching the per-shader WGSL : they operate at the **layer composite** stage, after the children have rendered to a temporary intermediate texture, before the result is folded into the parent.

### `saveLayer` composite

`saveLayer` opens a fresh intermediate render target the size of the layer bounds (or the current device clip if no bounds are given). Child draws target this temporary intermediate ; `restore()` then runs `layer_composite.wgsl` which samples the temporary as a texture and writes it through the parent's blend mode + paint into the parent's intermediate. The composite is **readback-free** — both textures are GPU-resident and the composite is a single full-screen quad.

### `MaskFilter` blur

`SkBlurMaskFilter(kNormal)` is implemented as a two-pass H/V Gaussian on a shape mask :

1. Draw the geometry as a binary alpha mask into a temporary texture (via `aa_stencil_cover.wgsl` writing alpha only, no colour).
2. Horizontal pass : `blur_gaussian.wgsl` reads the mask row-wise, writes blurred alpha to a second temporary.
3. Vertical pass : same shader, transposed, reads the H-output, multiplies the V-blurred alpha by the paint's colour, and blends into the parent.

The paint colour modulation happens in the V pass so the paint's `SkColor4f` is folded inline without a separate colour-resolve draw. `MaskFilter` on shaded paints (gradient / bitmap shader carrying the geometry's fill) is **deferred** — the V pass currently expects a flat paint colour.

### `ImageFilter` blur on `saveLayer`

`SkImageFilters.Blur` on a `saveLayer` paint reuses the H/V Gaussian shader from MaskFilter, but operates on the **layer texture** (RGBA) instead of a single-channel mask :

1. Child draws populate the layer's temporary texture as usual.
2. H pass : `blur_image_filter.wgsl` reads the layer, writes a horizontal-blurred copy to a scratch texture.
3. V pass : same shader on the transposed axis ; the result goes into the layer composite.
4. The layer composite then folds the blurred texture into the parent with the paint's blend mode + colour filter inline.

The paint's `colorFilter` is folded **on the composite step**, not on the blur passes — same convention as the solid-colour path : `solid_color.wgsl` and `layer_composite.wgsl` share the `apply_color_filter` helper packing (24-float `colorFilterPacked` payload), so a paint with a `Blend` or `Matrix` colour filter routes through the same code on either path.

`ImageFilter` UV-remap variants (Offset, Matrix-transform, Compose, ...) are **deferred** — only Blur on a `saveLayer` paint is wired today.

### `paint.colorFilter`

Inline-folded in `solid_color.wgsl` (rect path) and `layer_composite.wgsl` (saveLayer composite). Supported : `Blend` (sRGB or working-space) and `Matrix` (4x5 `SkColorMatrix`). The packed payload is a 24-float `colorFilterPacked` array passed through the uniform buffer. Unsupported variants — `Compose`, `Lerp`, `Table`, `SrcGamma`, and working-CS wrappers — fall back to a byte-identical no-op (the shader's filter branch is dead when `colorFilterKindMode.x == 0`).

---

## Cross-validation

`:gpu-raster` ships two cross-test harnesses :

- [`runGpuCrossTest(gm, floor)`](src/test/kotlin/org/skia/gpu/webgpu/testing/CrossTestHarness.kt) — render `gm` through WebGPU, compare against `original-888/<gm-name>.png`, assert similarity ≥ floor. Skips cleanly when no WebGPU adapter is available.
- [`runCrossBackendTest(gm, rasterFloor, gpuFloor)`](src/test/kotlin/org/skia/gpu/webgpu/testing/CrossBackendHarness.kt) — same GM through **both** CPU raster and GPU backends from a single test, asserts each backend's floor, and writes a per-pixel `|raster - gpu| * 4` diff PNG to `build/debug-images/<gm>-diff.png` when either backend slips inside `floor + 2 %` or the two backends diverge by more than 2 %.

The CI matrix in [`.github/workflows/test.yml`](../.github/workflows/test.yml) splits raster (ubuntu, `:cpu-raster` + `:skia-integration-tests`) from GPU (macOS, `:gpu-raster` with the Metal adapter via `wgpu4k-toolkit`). Debug images and test reports are uploaded as artefacts on failure.

---

## Deferred items

Tracked in [MIGRATION_PLAN_GPU_WEBGPU.md](../MIGRATION_PLAN_GPU_WEBGPU.md), surfaced here for quick orientation :

- **HDR colour spaces** (Rec.2100 PQ / HLG) — CPU parsing works ; GPU shaders don't accept the curves yet.
- **`PathEffect`** (dash, corner, ...) — CPU-only ; the path arrives at `SkWebGpuDevice.drawPath` already-flattened on the raster side but no GPU-side wiring exists.
- **`ImageFilter` UV-remap variants** — only `Blur` on `saveLayer` is wired today. Offset / Matrix-transform / Compose / DropShadow / DisplacementMap fall back to the unfiltered path.
- **`MaskFilter` on shaded paints** — only flat-colour paints. Gradient or bitmap-shader paints with a `MaskFilter` fall back to the unfiltered shaded path.
- **GPU compute tessellation (G8)** — current path tessellation runs on the CPU. The trigger for migrating to a compute pre-pass is a path-heavy GM showing a ≥ 2× headroom on the GPU vs CPU per-frame budget. Benchmarks landed alongside the G7 finalization PR baseline this comparison (`src/test/kotlin/.../benchmarks/`).
