# KGPU-M31-005 — FillRRect & FillPath Dispatch & Pixel Parity Evidence

## Scope

Extends `Surface.renderToRgba()` with GPU dispatch for `FillRRect` (SDF
coverage) and `FillPath` (stencil-cover). Covers 4 of 5 bridge draw families
(FillRect from M31-006, FillRRect + FillPath added, DrawImage lowered to
FillRect). DrawTextRun remains refused (text atlas pipeline pending).

## Status

**in-progress** (FillRRect + FillPath done; DrawTextRun still refused)

## Dispatch Details

### FillRRect
- **WGSL**: Fullscreen triangle pass with `rrect_cov` SDF function
  (reused from `RRectCoverageSnippet`). Fragment shader outputs
  `color * coverage` (premultiplied alpha with coverage modulation).
- **Data path**: `drawFullscreenRawUniformPass` with 48-byte uniform
  (bounds vec4f, radii vec4f, color vec4f).
- **Constraints**: SolidColor material, Identity transform, Root layer,
  WideOpen/DeviceRect clip, **uniform corner radii**.
- **Refusal**: Non-uniform radii → `non_uniform_radii` diagnostic.

### FillPath
- **WGSL (write)**: Vertex shader with `@location(0) position: vec2f`,
  maps canvas coords to NDC via target dimensions. No color output
  (write mask = None). Generated dynamically per surface.
- **WGSL (test)**: Fullscreen triangle with color uniform (same as
  `SOLID_RECT_WGSL`). Stencil test passes where stencil != 0.
- **Data path**: `drawFullscreenStencilPass` with Write mode (triangle
  mesh via `GPUBackendTriangleData`) + Test mode (color fill via
  `GPUBackendRawUniformDraw`).
- **Indexing**: Per-contour fan indices from `tessellatedVertices` +
  `contourStarts` (matches `GPUBasicPathFillPreparedPlanner.tessellate()`).
- **Constraints**: SolidColor, Identity, Root, WideOpen/DeviceRect.
- **Refusal**: Insufficient vertices → `insufficient_vertices`, no
  triangles generated → `no_triangles_generated`.

## Validation Commands and Output

### 1. Test suites (unit tests, no GPU needed)

```
rtk ./gradlew --no-daemon :kanvas:test :gpu-renderer:test :kanvas-skia-bridge:test
→ All 3 suites green
```

### 2. FillRRect — GPU render → PNG (JavaExec)

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderKanvasSurfaceOffscreen \
  -PsceneName=solid-rrect -PsceneOutput=reports/kanvas-surface-offscreen/rrect
→ nonTransparentPixels=30504 dispatched=1 refused=0
```

Scene: 320x240, blue rrect (0, 0.5, 1, 1), rect (50,50)-(270,190), radii 20px.

### 3. FillRRect — GPU vs CPU mathematical reference comparison

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen \
  -PsceneName=solid-rrect -PsceneOutput=reports/kanvas-surface-offscreen/rrect/compare
→ similarity=100,00% matching=76800/76800
→ maxDiff=(R=0,G=0,B=0,A=0) meanDiff=(R=0.00,G=0.00,B=0.00,A=0.00)
→ PASS: GPU output matches CPU reference (100% similarity)
```

CPU reference: same SDF coverage in Kotlin, pixel-center sampling.
Tolerance=1 for WGSL vs JVM f32 rounding at AA edges.

### 4. FillPath (triangle) — GPU render → PNG

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderKanvasSurfaceOffscreen \
  -PsceneName=solid-path -PsceneOutput=reports/kanvas-surface-offscreen/path
→ nonTransparentPixels=11200 dispatched=1 refused=0
```

Scene: 320x240, green triangle (80,50)-(240,50)-(160,190). Area = 11200 ✓

### 5. FillPath (triangle) — GPU vs CPU reference

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen \
  -PsceneName=solid-path -PsceneOutput=reports/kanvas-surface-offscreen/path/compare
→ similarity=100,00% matching=76800/76800
→ maxDiff=(R=0,G=0,B=0,A=0) meanDiff=(R=0.00,G=0.00,B=0.00,A=0.00)
→ PASS: GPU output matches CPU reference (100% similarity)
```

### 6. FillPath (star) — GPU render → PNG

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderKanvasSurfaceOffscreen \
  -PsceneName=solid-star-path -PsceneOutput=reports/kanvas-surface-offscreen/star-path
→ nonTransparentPixels=10125 dispatched=1 refused=0
```

Scene: 320x240, magenta 10-point star (non-zero winding). Stencil-cover proves
correct winding-number fill (not even-odd).

### 7. FillPath (star) — GPU vs CPU reference

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen \
  -PsceneName=solid-star-path -PsceneOutput=reports/kanvas-surface-offscreen/star-path/compare
→ similarity=100,00% matching=76800/76800
→ maxDiff=(R=0,G=0,B=0,A=0) meanDiff=(R=0.00,G=0.00,B=0.00,A=0.00)
→ PASS: GPU output matches CPU reference (100% similarity)
```

CPU reference: non-zero winding rule (matching stencil increment/decrement).

### 8. Git whitespace check

```
rtk git diff --check → clean
```

## Key Numbers

| Metric | FillRRect | FillPath (triangle) | FillPath (star) |
|---|---|---|---|
| `nonTransparentPixels` | 30504 | 11200 | 10125 |
| CPU similarity | 100% (tolerance 1) | 100% (tolerance 0) | 100% (tolerance 0) |
| Max pixel diff | 0 all channels | 0 all channels | 0 all channels |
| Dispatched | 1 | 1 | 1 |
| Refused | 0 | 0 | 0 |

## Family Coverage Status

| Family | Status | Method |
|---|---|---|
| FillRect | ✅ Done (M31-006) | Fullscreen pass + scissor |
| FillRRect | ✅ Done | SDF coverage via raw uniform pass |
| FillPath | ✅ Done | Stencil-cover (Write + Test) |
| DrawImage | ✅ Via FillRect lowering | Lowered to FillRect (solid color) |
| DrawTextRun | ❌ Refused | Needs text atlas/SDF pipeline |

## Known Limitations

- All families limited to: SolidColor material, Identity transform, Root layer,
  WideOpen/DeviceRect clip, SrcOver blend
- Non-uniform rrect radii refused
- DrawImage lowers to solid-color FillRect (loses pixel content; true texture
  draw deferred to future work)
- DrawTextRun refused (text atlas pipeline not wired into dispatch)
- GPU requires JavaExec; not available in JUnit test JVM
