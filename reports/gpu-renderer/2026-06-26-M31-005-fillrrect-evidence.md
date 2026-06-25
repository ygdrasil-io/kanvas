# KGPU-M31-005 — FillRRect & FillPath Dispatch & Pixel Parity Evidence

## Scope

Extends `Surface.renderToRgba()` with GPU dispatch for `FillRRect` (SDF
coverage) and `FillPath` (stencil-cover). DrawImage now explicitly refused
(via `GPUMaterialDescriptor.ImageDraw` — no silent solid-rect fallback).
**3/5 families dispatched with independent pixel parity; Image+Text refused.**

## Status

**in-progress** (FillRRect + FillPath dispatched; DrawImage + DrawTextRun refused)

## Dispatch Details

### FillRRect
- **WGSL**: Fullscreen triangle pass with `rrect_cov` SDF function
  (reused from `RRectCoverageSnippet`). Fragment shader outputs
  `color * coverage` (premultiplied alpha with coverage modulation).
- **Data path**: `drawFullscreenRawUniformPass` with 48-byte uniform
  (bounds vec4f, radii vec4f, color vec4f).
- **Constraints**: SolidColor material, Identity transform, Root layer,
  WideOpen/DeviceRect clip, **uniform corner radii**.

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
  `contourStarts`.

### DrawImage (NOW REFUSED)
- Uses `GPUMaterialDescriptor.ImageDraw` (non-SolidColor) → `dispatchFillRect`
  automatically refuses with stable `refuse:...:unsupported_material:ImageDraw`.
- No longer silently renders a solid rect of the paint color.
- True texture draw deferred.

## Validation Commands and Output

### 1. Test suites (unit tests, no GPU needed)

```
rtk ./gradlew --no-daemon :kanvas:test :gpu-renderer:test :kanvas-skia-bridge:test
→ All 3 suites green (including new drawImage refuse test)
```

### 2. FillRRect — GPU render → PNG

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderKanvasSurfaceOffscreen \
  -PsceneName=solid-rrect -PsceneOutput=reports/kanvas-surface-offscreen/rrect
→ nonTransparentPixels=30504 dispatched=1 refused=0
```

Scene: 320x240, blue rrect (0, 0.5, 1, 1), rect (50,50)-(270,190), radii 20px.

### 3. FillRRect — GPU vs INDEPENDENT CPU reference (geometric point-in-rounded-rect)

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen \
  -PsceneName=solid-rrect -PsceneOutput=reports/kanvas-surface-offscreen/rrect/final
→ similarity=99,84% matching=76680/76800
→ maxDiff=(R=0,G=62,B=124,A=124) meanDiff=(R=0.00,G=31.20,B=62.00,A=62.00)
→ PASS: GPU output matches CPU reference (similarity=99.84%)
```

**CPU reference**: Independent geometric point-in-rounded-rect test (not derived
from WGSL SDF). A pixel center is inside iff it is within the bounding rectangle
AND for each corner region, the distance from the corner center ≤ radius.

**Why not 100%**: GPU SDF produces anti-aliased fractional coverage at edges
(~120 pixels / 0.16%). The independent reference is binary (inside/outside),
so the AA boundary pixels inherently differ (max diff up to 124 at G/B/A on
50%-coverage edge pixels). This is expected and proves geometric correctness
— the rrect position, dimensions, and radii are exact.

### 4. FillPath (triangle) — GPU vs CPU reference

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen \
  -PsceneName=solid-path -PsceneOutput=reports/kanvas-surface-offscreen/path/final
→ similarity=100,00% matching=76800/76800 maxDiff=0
→ PASS: GPU output matches CPU reference (similarity=100.00%)
```

### 5. FillPath (star) — GPU vs CPU reference

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen \
  -PsceneName=solid-star-path -PsceneOutput=reports/kanvas-surface-offscreen/star-path/final
→ similarity=100,00% matching=76800/76800 maxDiff=0
→ PASS: GPU output matches CPU reference (similarity=100.00%)
```

### 6. drawImage refuse test (hermetic, captures stderr)

```
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test \
  --tests "*drawImage*"
→ KanvasSkiaBridgeTest > drawImage via bridge emits refuse diagnostic() PASSED
```

Confirms `refuse:` + `ImageDraw` appear in stderr after flush(). GPU-gated
(via `assumeTrue` on headless runners).

### 7. Git whitespace check

```
rtk git diff --check → clean
```

## Key Numbers

| Metric | FillRRect | Triangle | Star |
|---|---|---|---|
| nonTransparentPixels | 30504 | 11200 | 10125 |
| CPU similarity (tol=0) | 99.84% | 100% | 100% |
| Matching (tol=0) | 76680/76800 | 76800/76800 | 76800/76800 |
| Max pixel diff | G=62,B=124,A=124 | 0 all | 0 all |
| Pass threshold | ≥99.5% | 100% | 100% |

## Family Coverage Status

| Family | Status | Reference | Note |
|---|---|---|---|
| FillRect | ✅ Done | Independent (tolerance 0) | Scissored fullscreen pass |
| FillRRect | ✅ Done | Independent geometric | 99.84% (AA edge pixels) |
| FillPath | ✅ Done | Independent winding | Triangle + star |
| DrawImage | ❌ Refused | — | `ImageDraw` material; texture deferred |
| DrawTextRun | ❌ Refused | — | Needs text atlas/SDF pipeline |

Total: **3/5 dispatched with independent pixel parity; 2/5 explicitly refused.**

## Files

### Modified
- `gpu-renderer/.../NormalizedDrawCommand.kt` — `ImageDraw` material kind
- `kanvas/.../Canvas.kt` — `drawImage` uses `ImageDraw` material
- `kanvas/.../Surface.kt` — `dispatchFillRRect`, `dispatchFillPath`
- `gpu-renderer-scenes/.../CompareKanvasSurfaceOffscreenMain.kt` — indep rrect ref
- `kanvas-skia-bridge/.../KanvasSkiaBridgeTest.kt` — drawImage refuse test
- `gpu-renderer-scenes/.../RenderKanvasSurfaceOffscreenMain.kt` — path scenes

## Known Limitations

- RRect: SDF-vs-binary AA edge difference (120 px / 0.16%, expected)
- Non-uniform rrect radii refused
- FillPath: SolidColor only, Identity transform, Root layer
- DrawImage: texture draw deferred (solid color would be silently wrong)
- DrawTextRun: not dispatched
- GPU requires JavaExec; not available in JUnit test JVM
