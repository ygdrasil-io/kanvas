# M32 refusal coverage: unsupported families refuse (no silent-wrong rendering)

- Date: 2026-06-26
- Phase: Legacy gpu-raster Decommission — Phase 2.B(ii) (Pattern A refusal coverage)
- Tickets (refused sub-cases): KGPU-M32-010, -012, -014, -016, -017, -018, -019, -020
- Status of tickets: kept `proposed` (independent review still owed)
- Mirrors the just-landed stroke refuse fix (commit 4e083f7,
  `reports/gpu-renderer/2026-06-26-m32-013-stroke-refusal.md`).
- **No short-lived substitute added** for any dependency-gated family (per
  AGENTS.md). Real ports remain dependency-gated and linked below.

## KEY FINDING — gradient vs image-shader / runtime-effect

The CRITICAL check (rect/rrect/path with a non-SolidColor paint) split two ways:

- **Gradients were ALREADY refusing.** `Canvas.lowerPaint` lowers
  `Shader.LinearGradient/RadialGradient/SweepGradient` to the matching
  non-SolidColor `GPUMaterialDescriptor.{Linear,Radial,Sweep}Gradient`, and
  `Surface.dispatchFillRect/RRect/Path` refuse any non-SolidColor material with
  `unsupported_material:<kind>` BEFORE any fill dispatch. No silent solid-fill.
  → kept as green regression tests.

- **Image-shaders and runtime-effects were SILENTLY SOLID-FILLED (BUG, FIXED).**
  `Canvas.lowerPaint` had a catch-all `else -> SolidColor` branch that mapped
  **`Shader.Bitmap` (image shader)** and **`Shader.RuntimeEffect`** paints to a
  `SolidColor` material. The dispatch material guard then saw SolidColor and
  **filled them as a flat solid color** — a "no silent fallback" violation and a
  second silent-wrong bug (the same class as the stroke bug).
  - `Shader.Bitmap` is **bridge-reachable**:
    `KanvasSkiaBridge.toKanvasShader()` maps `ShaderKind.Bitmap → Shader.Bitmap`,
    so an `SkPaint` bitmap shader on `drawRect/drawRRect/drawPath` hit the silent
    fill. This is a real, reachable integrity bug.
  - `Shader.RuntimeEffect` is **not** bridge-reachable today (no `toKanvasShader`
    branch) but was reachable via the direct Kanvas `Paint` API; it was also
    silently filled.

### The fix (clean refusal, not a renderer)

1. `gpu-renderer/.../commands/NormalizedDrawCommand.kt`: added
   `GPUMaterialKind.RuntimeEffect` and `GPUMaterialDescriptor.RuntimeEffect`
   (dependency-gated; refuses via the existing non-SolidColor material guard).
   No exhaustive `when` over material/kind exists, so this is additive.
2. `kanvas/Canvas.kt` `lowerPaint`: removed the silent `else -> SolidColor` and
   made the `when (paint.shader)` exhaustive:
   - `Shader.Bitmap -> GPUMaterialDescriptor.ImageDraw(...)` (same pattern as the
     existing `Canvas.drawImage` refuse path).
   - `Shader.RuntimeEffect -> GPUMaterialDescriptor.RuntimeEffect(...)`.
   Both are non-SolidColor → refused (`unsupported_material:ImageDraw` /
   `unsupported_material:RuntimeEffect`), never silently filled.
3. `kanvas/Surface.kt`: extracted two pure, hermetically-testable guards
   (mirroring `strokeRefusalReasonOrNull()`):
   - `NormalizedDrawCommand.fillGuardRefusalReasonOrNull()` — the shared fill
     guards (stroke → material → transform → clip → layer → blend), in the exact
     historical order.
   - `NormalizedDrawCommand.FillRRect.nonUniformRadiiRefusalReasonOrNull()` — the
     rrect non-uniform radii guard.
   `dispatchFillRect/RRect/Path` now call these first and `refuse(it); return`
   before any GPU pass. Behavior is preserved exactly (same order, same reason
   tokens); the extraction removes duplication and makes every refusal testable
   without a GPU.

No new render feature was added. The deliverable is clean refusal + regression
coverage.

## Diagnostic codes (stable)

Emitted through the existing dispatch refuse pattern
`refuse:${cmd.diagnosticName}:$reason` and surfaced via
`SurfaceRenderResult.diagnostics` → `SkiaKanvasSurface.emitRefusedDiagnostics`.
Stable reason tokens used here (consistent with `unsupported_stroke`):

- `unsupported_material:<MaterialKind>` (LinearGradient, RadialGradient,
  SweepGradient, ImageDraw, RuntimeEffect)
- `unsupported_blend:<modeLabel>` (e.g. `multiply`)
- `unsupported_clip:<ClipKind>` (e.g. `ComplexStack`)
- `non_uniform_radii`

## Per-family coverage

### KGPU-M32-010 material-paint (refused sub-case) — gradients + shader pipeline
- Refuse reason: `unsupported_material:{LinearGradient,RadialGradient,SweepGradient,RuntimeEffect}`.
- Status: gradients **already refusing** (regression-locked); runtime-effect
  **was silent → FIXED**.
- New hermetic tests (`MaterialRefuseTest`): `linear/radial/sweep gradient rect
  refuses with unsupported_material`, `runtime-effect rect refuses with
  unsupported_material`, `drawRect with linear gradient shader lowers to a
  non-solid material`, `drawRect with runtime-effect shader is refused not
  silently solid-filled`, plus the negative `solid color fill command does not
  refuse` / `drawRect with default solid paint ...`.
- Real port: dependency-gated (gradient GPU routes / shader pipeline).

### KGPU-M32-012 rounded-rect-gradients (refused sub-case) — gradients + non-uniform radii
- Refuse reasons: `unsupported_material:<gradientKind>` (gradient rrect) and
  `non_uniform_radii`.
- New hermetic tests: `MaterialRefuseTest.linear gradient rrect refuses with
  unsupported_material`; `RRectRadiiRefuseTest.non-uniform rrect command refuses
  with non_uniform_radii`, `uniform rrect command does not refuse for radii`,
  `drawRRect with non-uniform radii preserves non-uniform radii so dispatch
  refuses` (proves non-uniform radii are reachable end-to-end via the public
  `Canvas.drawRRect`).

### KGPU-M32-014 device-scissor-simple-clips (refused sub-case) — complex clips
- Refuse reason: `unsupported_clip:<ClipKind>` (only `WideOpen`/`DeviceRect`
  dispatch).
- **Honesty / reachability:** the public Kanvas/bridge API **cannot construct a
  non-WideOpen clip**. `Canvas.drawRect/drawRRect/drawPath` never pass a clip
  (the command builder defaults to `GPUClipFacts.wideOpen`), and
  `KanvasSkiaBridge` exposes **no** clip entrypoint. The complex-clip refusal is
  therefore proven as a **DISPATCH-LEVEL guard** by constructing the command
  directly; it is NOT claimed as a reachable end-to-end complex-clip refuse.
- New hermetic tests (`ClipRefuseTest`): `complex-clip rect/rrect/path refuses
  with unsupported_clip`, `wideopen and devicerect clips do not refuse on fills`,
  and the reachability assertion `drawRect via public API produces a WideOpen
  clip`.
- Real port (device scissor for simple clips) remains dependency-gated.

### KGPU-M32-016 images-bitmap-codecs-uploads — image draws / image shaders
- Refuse reason: `unsupported_material:ImageDraw`.
- Status: `Canvas.drawImage` already used `ImageDraw` (existing GPU-gated bridge
  test `drawImage via bridge emits refuse diagnostic`). The **image-shader**
  (`Shader.Bitmap`) path **was silent → FIXED** to `ImageDraw`.
- New hermetic tests (`MaterialRefuseTest`): `image-draw rect refuses with
  unsupported_material`; and the FIX regressions `drawRect/drawRRect/drawPath
  with bitmap image shader is refused not silently solid-filled`.
- Real port: dependency-gated (codec/upload deliveries).

### KGPU-M32-017 savelayer-destination-read-filters — refuse-by-absence
- **No bridge API exists.** Verified by reading
  `kanvas-skia-bridge/.../KanvasSkiaBridge.kt`: the bridge exposes only
  `drawRect`, `drawRRect`, `drawPath`, `drawImage`, `drawTextBlob`, and
  `unsupported(feature)`. There is **no** `saveLayer`, `save`/`restore`, or
  layer-scope entrypoint. `NormalizedDrawCommand` has no saveLayer command; the
  only `GPULayerScopeKind.SaveLayer` path would refuse via the layer guard, but
  it is not constructible through the bridge.
- Refuse-by-absence: a saveLayer/destination-read/filter draw **cannot be
  silently served** by the Kanvas route — there is no entrypoint to serve it.
  No fabricated refuse test was added for a non-existent API. Calling
  `KanvasSkiaBridge.unsupported("saveLayer")` emits the stable
  `unsupported-skia-bridge-feature` diagnostic (existing bridge behavior).
- Real port = KGPU-M11-006 (dependency-gated).

### KGPU-M32-018 text-glyphs (refused sub-case) — non-solid text material / non-simple clip
- Refuse reasons: `unsupported_material:<kind>` (non-solid text material),
  `unsupported_clip:<ClipKind>` (non-WideOpen/DeviceRect clip on text).
- Status: material sub-case already covered hermetically by the existing
  `TextRunDispatchTest.planner refuses a non-solid text material` (referenced,
  still green). The text **clip** sub-case was previously uncovered.
- New hermetic test (`ClipRefuseTest`): `text run with complex clip refuses with
  unsupported_clip` — drives the hermetic `TextRunDispatchPlanner.plan(...)`
  (no GPU) with a `ComplexStack` clip → `Refused("unsupported_clip:ComplexStack")`.
- A8 text fill parity is unaffected and remains as previously evidenced.

### KGPU-M32-019 runtime-effects-color-blends (refused sub-case) — other blends / runtime effects
- Refuse reasons: `unsupported_blend:<mode>` (non-SRC_OVER blend);
  `unsupported_material:RuntimeEffect` (runtime-effect material).
- Status: blend refuse already covered end-to-end by the GPU-gated bridge test
  `non-srcover blend emits refuse diagnostic` (referenced). Added a **hermetic**
  blend guard test. The runtime-effect material **was silent → FIXED** (see Key
  Finding).
- New hermetic tests: `BlendRefuseTest.multiply-blend fill command refuses with
  unsupported_blend`, `drawRect with multiply blend lowers to unsupported blend
  and refuses`, `srcover-blend fill command does not refuse`;
  `MaterialRefuseTest.runtime-effect rect refuses with unsupported_material` +
  `drawRect with runtime-effect shader is refused not silently solid-filled`.
- Honesty caveat: **color filters** (`Paint.colorFilter`) are carried on `Paint`
  but are **not wired into material lowering** at all today, so they never
  produce a command material and never reach the dispatch guard. They are neither
  silently rendered as a color-filter effect nor explicitly refused as a distinct
  `colorfilter` token; wiring a color-filter material is out of scope for this
  localized refusal pass and remains dependency-gated (KGPU-M11-008).
- Real port (other blends / color filters / runtime effects / color management)
  remains dependency-gated (KGPU-M11-008).

### KGPU-M32-020 vertices-points-meshes — refuse-by-absence
- **No bridge API exists.** Verified by reading `KanvasSkiaBridge.kt`: there is
  **no** `drawVertices`, `drawPoints`, or mesh entrypoint, and
  `NormalizedDrawCommand` has no vertices/points command family used by the
  Kanvas route. A vertices/points draw **cannot be silently served**.
- Refuse-by-absence documented; no fabricated refuse test added for a
  non-existent API. `KanvasSkiaBridge.unsupported("drawVertices")` emits the
  stable `unsupported-skia-bridge-feature` diagnostic (asserted by the existing
  bridge test `bridge unsupported emits diagnostic to stderr`).
- Real port = KGPU-M8-003 (dependency-gated).

## Tests (TDD: failing first, then green)

### Pre-RED evidence
`:kanvas:compileTestKotlin` failed first with unresolved references
(`fillGuardRefusalReasonOrNull`, `nonUniformRadiiRefusalReasonOrNull`,
`GPUMaterialDescriptor.RuntimeEffect`) — feature-missing RED — before the
production change.

### Green run (headless, no GPU required)

```
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test :gpu-renderer:test
# BUILD SUCCESSFUL
```

Explicit new-coverage results (`:kanvas:test --rerun-tasks`):

```
MaterialRefuseTest > linear gradient rect refuses with unsupported_material() PASSED
MaterialRefuseTest > radial gradient rect refuses with unsupported_material() PASSED
MaterialRefuseTest > sweep gradient rect refuses with unsupported_material() PASSED
MaterialRefuseTest > linear gradient rrect refuses with unsupported_material() PASSED
MaterialRefuseTest > linear gradient path refuses with unsupported_material() PASSED
MaterialRefuseTest > image-draw rect refuses with unsupported_material() PASSED
MaterialRefuseTest > runtime-effect rect refuses with unsupported_material() PASSED
MaterialRefuseTest > drawRect with linear gradient shader lowers to a non-solid material() PASSED
MaterialRefuseTest > drawRect with bitmap image shader is refused not silently solid-filled() PASSED
MaterialRefuseTest > drawRRect with bitmap image shader is refused not silently solid-filled() PASSED
MaterialRefuseTest > drawPath with bitmap image shader is refused not silently solid-filled() PASSED
MaterialRefuseTest > drawRect with runtime-effect shader is refused not silently solid-filled() PASSED
MaterialRefuseTest > solid color fill command does not refuse() PASSED
MaterialRefuseTest > drawRect with default solid paint lowers to SolidColor and does not refuse() PASSED
RRectRadiiRefuseTest > non-uniform rrect command refuses with non_uniform_radii() PASSED
RRectRadiiRefuseTest > uniform rrect command does not refuse for radii() PASSED
RRectRadiiRefuseTest > drawRRect with non-uniform radii preserves non-uniform radii so dispatch refuses() PASSED
BlendRefuseTest > multiply-blend fill command refuses with unsupported_blend() PASSED
BlendRefuseTest > drawRect with multiply blend lowers to unsupported blend and refuses() PASSED
BlendRefuseTest > srcover-blend fill command does not refuse() PASSED
ClipRefuseTest > complex-clip rect refuses with unsupported_clip() PASSED
ClipRefuseTest > complex-clip rrect refuses with unsupported_clip() PASSED
ClipRefuseTest > complex-clip path refuses with unsupported_clip() PASSED
ClipRefuseTest > wideopen and devicerect clips do not refuse on fills() PASSED
ClipRefuseTest > drawRect via public API produces a WideOpen clip() PASSED
ClipRefuseTest > text run with complex clip refuses with unsupported_clip() PASSED
```

Referenced existing coverage (still green; defaults unchanged):

```
StrokeRefusalTest > (10 tests) PASSED
TextRunDispatchTest > planner refuses a non-solid text material() PASSED
TextRunDispatchTest > (3 other tests) PASSED
```

Suite-level: `:kanvas:test`, `:kanvas-skia-bridge:test`, `:gpu-renderer:test`
all **BUILD SUCCESSFUL** (the new defaulted material kind did not break
gpu-renderer analysis/validation/command tests; bridge GPU-gated refuse tests
`drawImage`/`non-srcover blend`/`stroked draw*` were exercised; the
WebGPU-absent-only test stays SKIPPED when WebGPU is present).

## Fill / parity behavior unchanged

The shared guard preserves the exact historical check order and reason tokens;
`SolidColor` + identity transform + WideOpen/DeviceRect clip + root layer +
SrcOver blend + uniform radii still dispatch unchanged. Negative tests
(`solid color fill command does not refuse`, `srcover-blend fill command does not
refuse`, `wideopen and devicerect clips do not refuse on fills`, `uniform rrect
command does not refuse for radii`) pin the unchanged defaults; existing
`SkiaBridgeParityTest` fill/parity tests remained green.

## Concerns / honesty caveats

- `surface.renderToRgba()` (the literal CRITICAL flow) is GPU-gated
  (`webgpu-context-unavailable` without WebGPU). These hermetic tests assert the
  dispatch-guard that runs BEFORE any GPU pass — i.e. the refused commands have
  `dispatched == 0` semantics — and the end-to-end emitted-diagnostic evidence is
  the existing GPU-gated bridge tests.
- Complex clips are not constructible via the public bridge/Canvas API (proven,
  not claimed otherwise); the complex-clip refusal is a dispatch-level guard.
- `Shader.RuntimeEffect` is not bridge-reachable today; the fix removes its
  (direct-API) silent-fill path and represents it as a refusing material.
- Color filters are not wired into material lowering and are neither rendered nor
  refused as a distinct token (documented under KGPU-M32-019).
- saveLayer / vertices / points are refuse-by-absence (no bridge API); no
  fabricated tests were added for non-existent entrypoints.
