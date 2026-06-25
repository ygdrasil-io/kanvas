# KGPU-M31-005 — FillRRect Dispatch & Pixel Parity Evidence

## Scope

First supported family in the M31-005 pixel-parity campaign. Extends
`Surface.renderToRgba()` to dispatch `NormalizedDrawCommand.FillRRect`
through the offscreen WebGPU backend using an SDF-based coverage shader.

## Status

**in-progress** (FillRRect done; FillPath, DrawImage, DrawTextRun pending)

## Dispatch Details

- **WGSL**: Fullscreen triangle pass with `rrect_cov` SDF function
  (reused from `RRectCoverageSnippet`). Fragment shader outputs
  `color * coverage` (premultiplied alpha with coverage modulation).
- **Data path**: `drawFullscreenRawUniformPass` with 48-byte uniform
  (bounds vec4f, radii vec4f, color vec4f).
- **Constraints**: SolidColor material, Identity transform, Root layer,
  WideOpen/DeviceRect clip, uniform corner radii.
- **Refusal**: Non-solid materials, transforms, complex clips, non-root
  layers, non-uniform radii all emit stable `refuse:` diagnostics.

## Validation Commands and Output

### 1. Test suites (unit tests, no GPU needed)

```
rtk ./gradlew --no-daemon :kanvas:test :gpu-renderer:test :kanvas-skia-bridge:test
→ All 3 suites green
```

### 2. GPU render → PNG (JavaExec)

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderKanvasSurfaceOffscreen \
  -PsceneName=solid-rrect -PsceneOutput=reports/kanvas-surface-offscreen/rrect
→ nonTransparentPixels=30504 dispatched=1 refused=0
```

Scene: 320x240 surface, blue rrect (0, 0.5, 1, 1), rect (50,50)-(270,190),
radii 20px uniform.

### 3. GPU vs CPU mathematical reference comparison (JavaExec)

```
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen \
  -PsceneName=solid-rrect -PsceneOutput=reports/kanvas-surface-offscreen/rrect/compare
→ similarity=100,00% matching=76800/76800
→ maxDiff=(R=0,G=0,B=0,A=0) meanDiff=(R=0.00,G=0.00,B=0.00,A=0.00)
→ PASS: GPU output matches CPU reference (100% similarity)
```

CPU reference uses the same SDF coverage function in Kotlin, sampling at pixel
centers (x+0.5, y+0.5). Tolerance=1 accounts for WGSL vs JVM f32 rounding
at anti-aliased edges. The rect reference uses tolerance=0 (binary
inside/outside).

### 4. Git whitespace check

```
rtk git diff --check → clean
```

## Key Numbers

| Metric | Value |
|---|---|
| `nonTransparentPixels` (render task) | 30504 (220×140 rrect, 20px radii) |
| CPU similarity | 100% (76800/76800, tolerance=1) |
| Max pixel diff | 0 all channels |
| Command dispatch count | 1 solid rrect |
| Command refuse count | 0 |
| Test suites passing | 3/3 |

## Files

### Modified
- `kanvas/.../Surface.kt` — `dispatchFillRRect`, RRECT_WGSL, when-branch

### New
- (scenes added to existing files)

## Known Limitations

- Non-uniform corner radii refused (`non_uniform_radii` diagnostic)
- SDF coverage is analytic (no multi-sample AA); tolerance=1 for edge rounding
- Same state constraints as FillRect (Identity, Root, WideOpen/DeviceRect, SrcOver)
- GPU still requires JavaExec; not available in JUnit test JVM
