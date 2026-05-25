# Implementation shortlist

Snapshot date: 2026-05-24.

Source: `reports/upstream-rebaseline/2026-05-24.tsv`, filtered to
`bucket=implementation`.

Purpose: pick implementation work that is useful while fonts, codecs, and
WGSL/parser delivery are still pending.

## Summary

The rebaseline currently classifies 5 upstream `.cpp` rows as
`implementation`. These are not blocked by the font delivery, codec
delivery, or the WGSL/runtime-effect parser track.

The best immediate work is small API surface with clear tests and low
cross-cutting risk. Avoid broad rendering rewrites until the dependency
deliveries land.

Completed since this snapshot:

- `STUB.SURFACE_SNAPSHOT_SUBSET`: implemented in `SkSurface.makeImageSnapshot(SkIRect)`
  and `SurfaceUnderdrawTest` is enabled.
- `STUB.IMAGE_MAKE_SCALED`: implemented in `SkImage.makeScaled`
  and `Crbug404394639Test` is enabled.
- `STUB.SRC_RECT_CONSTRAINT`: implemented for raster `drawImageRect`
  and `SrcRectConstraintTest` is enabled.
- `STUB.MAKE_WITH_COLOR_FILTER`: implemented in shader/color-filter composition
  and `ColorFilterShaderGM` is enabled.
- `STUB.COLOR4F_BLEND_CF`: implemented for `SkColor4f` color-filter blending
  and `Color4blendcfGM` is enabled.
- `STUB.PERSPECTIVE_ADDPATH`: implemented in `SkPathBuilder.addPath`
  and `PathAppendExtendGM` is enabled.
- `STUB.GAUSSIAN_COLOR_FILTER`: implemented for private gaussian color filters
  and `ShadowUtilsGaussianColorFilterGM` is enabled.
- `STUB.PATH_EFFECT_CTM`: implemented in `CTMPathEffectGM` with CTM-aware
  line inflation and ratchet coverage.
- `STUB.POLY_TO_POLY`: `ClipShaderPerspGM` is ported and enabled; the
  matrix factory was already present, and the remaining visual delta is
  tracked by the ratchet.
- `STUB.IFX.MULTIPLE_FILTERS_SPAN`: raster `saveLayerWithMultipleFilters`
  is implemented for `MultipleFiltersGM` and the test is enabled.
- `STUB.DRAW_VERTICES_VISUAL_PARITY`: `VerticesBatchingGM` is ported through
  the existing `drawVertices` raster path and the test is enabled. `VerticesGM`
  is source-ported, and the vertex blend / paint color-filter paths are now
  covered. Tranche-2 diagnostics on current `master` reached ~62% for both
  normal and scaled-shader variants; an alpha/colorFilter ordering experiment
  regressed slightly, so the remaining visual-parity blocker is not a bounded
  paint alpha / `paint.colorFilter` order fix. The GM remains disabled without
  a ratchet until a new bounded color-pipeline fix is proven.
- `STUB.EDGE_AA_IMAGE_SET`: raster fallback implemented in
  `SkCanvas.experimental_DrawEdgeAAImageSet`; `DrawImageSetGM`,
  `DrawImageSetRectToRectGM`, `DrawImageSetAlphaOnlyGM`, and `Skbug14554GM`
  are enabled with ratchet coverage.
- `STUB.COMPOSE_SHADER`: `ComposeShaderAlphaGM` is ported and enabled;
  the compose-shader family is now covered by ratcheted tests.
- `STUB.ALPHA8_IMAGE_AS_MASK`: `ImageMaskSubsetGM` is enabled against the
  existing alpha-mask tint path in `SkBitmapDevice.drawImageRect`.
- `STUB.EDGE_AA_QUAD`: `DrawQuadSetGM` is enabled against the existing
  `experimental_DrawEdgeAAQuad` raster fallback; the GPU-gradient residual is
  covered by the similarity floor.
- `STUB.STROKEDLINE_CAPS`: `StrokedLineCapsGM` is enabled with ratchet
  coverage; `StrokedLinesGM` was already enabled.
- `fiddle`: upstream's intentionally empty GM is already ported and covered
  by `FiddleTest`; the implementation bucket entry was stale.
- `STUB.SURFACE_PROPS`: `SurfacePropsGM` is ported for the raster path via
  `SkSurface.MakeRaster(..., SkSurfaceProps)` and enabled with ratchet
  coverage. The WebGPU and crossbackend wrappers are also enabled with
  95% floors.
- `RRectBlurGM`: `SkCanvas.readPixels` / `writePixels` raster overloads are
  implemented, the diff GM is ported, and `RRectBlurTest` is enabled.
- `STUB.BACKDROP_FILTER`: `SaveLayerWithBackdropGM` is ported and enabled on
  raster, WebGPU, and crossbackend wrappers with ratchet coverage; the
  remaining `imagefilters` bucket tag was stale tracking.
- `STUB.COLOR_FILTER_PRIV`: `SkColorFilterPriv.withWorkingFormat` delegates to
  the existing working-colour-space wrapper; `AlternateLumaGM` is enabled with a
  low ratchet because the mandrill source asset is still synthetic.
- Tracker hygiene: `gm-status.sh` now ignores upstream `#if 0` GM
  registrations, uses a per-run Kotlin index to avoid parallel collisions, and
  treats documented intentionally-empty ports such as `FiddleGM` as ported. The
  rebaseline bucket logic now lets already-`PORTED` rows override stale
  historical `STUB.*` tags.
- `STUB.BLURRECT_GALLERY`, `STUB.BLUR_RECTS_FULL`, and
  `STUB.BLUR_RECT_COMPARE`: `SkBlurMask.BlurRect`
  now delegates to the existing separable blur mask filter, and
  `BlurRectGalleryGM` / `BlurRectGM` / `BlurRectCompareGM` are enabled with
  raster, WebGPU, and crossbackend ratchets.
- `STUB.SAVE_BEHIND`: raster `SkCanvas.saveBehind` / `drawBehind` shims are
  implemented for the upstream private API behavior, and `SaveBehindGM` is
  enabled with a raster ratchet.
- `STUB.GRADIENT_INTERPOLATION`: the `SkGradient` aggregate and
  `SkShaders.LinearGradient(pts, SkGradient)` overload now exist for RGB
  working color spaces, and the CPU linear-gradient path has a bounded HSL
  hue-method sampler plus RGB `InPremul.kYes` acceptance. `gradients_hue_method`
  is enabled with labels and explicit-position endpoint coverage. HSL powerless
  hue is enabled with a focused ratchet (`GradientsPowerlessHueHslGM` at
  `69.53%`). Remaining blockers are the LCH/OKLCH/HWB perceptual conversion
  pipelines.
- `addarc`: `SkPathBuilder.emitArc` now normalises huge sweeps before conic
  decomposition, and `ManyArcsGM` is enabled with a raster ratchet.
- `STUB.TEXT_IMAGE_FILTER`: `imagefiltersbase`, `textfilter_image`, and
  `textfilter_color` are covered on raster. Remaining disabled wrappers are
  WebGPU text-filter dependency work, not an actionable raster
  `imagefiltersbase` implementation item.
- `STUB.RECORDOPTS.SAVELAYER_COLOR_FILTER_FOLD`: `RecordOptsGM` is enabled
  after preserving saveLayer image-filter working formats and routing
  image-filtered rect draws through layer restore.

## Recommended order

| Priority | Track | Impact | Effort | Why now |
|---:|---|---:|---|---|
| 1 | `vertices` | 1 cpp | M | `VerticesBatchingGM` is already ported; vertex blend, paint alpha, and color-filter ordering have been checked, but `VerticesGM` still needs a new bounded color-pipeline fix before reactivation. |
| 2 | `gradients` | 1 cpp | M/L | RGB API surface plus HSL hue-method/powerless-hue GMs are covered; remaining work is LCH/OKLCH/HWB perceptual sampler coverage. |
| 3 | `mesh` | 1 cpp | L/XL | Actionable but broad API work; start with the `custommesh` slice only. |
| 4 | `STUB.RSXBLOB` / `STUB.DF_TEXT_RASTER` | 2 cpps (`drawatlas`, `dftext_blob_persp`) | L/XL | Text/glyph transform work; defer if font delivery may change internals. |

## Implementation bucket rows

| Upstream cpp | Tags | Local GM files |
|---|---|---|
| `dftext_blob_persp` | `STUB.DF_TEXT_RASTER` | `DFTextBlobPerspGM.kt` |
| `drawatlas` | `STUB.RSXBLOB` | `BlobRSXformDistortableGM.kt`, `BlobRSXformGM.kt`, `CompareAtlasVerticesGM.kt`, `DrawAtlasGM.kt`, `DrawTextRSXformGM.kt` |
| `gradients` | `STUB.GRADIENT_INTERPOLATION`; RGB `SkGradient` overload exposed, HSL hue-method and HSL powerless-hue GMs enabled, LCH/OKLCH/HWB perceptual sampler still missing | gradient interpolation variants |
| `mesh` | `STUB.MESH`; minimal CPU `SkMesh` / `SkMeshSpecification` / `SkCanvas.drawMesh` skeleton exists for position-only triangles and optional `ubyte4_unorm color`, but all 11 upstream registrations remain compile-pinned until shader attributes, varyings, uniforms, and fragment output are implemented | `MeshGMs.kt` |
| `vertices` | `STUB.DRAW_VERTICES_VISUAL_PARITY`; `VerticesGM` is source-ported, but focused validation still renders ~62% for both `vertices` and `vertices_scaled_shader`; paint alpha / color-filter ordering has been isolated as non-improving | `Skbug13047GM.kt`, `VerticesBatchingGM.kt`, `VerticesCollapsedGM.kt`, `VerticesGM.kt`, `VerticesPerspectiveGM.kt` |

## Notes

- `STUB.MISSING_API` is too vague. The affected rows (`aaclip`, `addarc`)
  need tag cleanup before implementation selection.
- `dashcubics` is now ported: `SkTrimPathEffect` is implemented and
  `TrimGM` is reactivated with a 90% ratchet (`92.29%` in validation).
- `recordopts` no longer carries the stale `STUB.XYZ` placeholder; the
  saveLayer / detector color-filter fold path is covered by `RecordOptsTest`.
- Text-related rows should be revisited after the font delivery if their
  implementation would touch glyph storage or shaping assumptions.

### Vertices reconnaissance

The `vertices` row is actionable raster work, but not reactivatable as a
GM-only patch. `skia-integration-tests/src/main/kotlin/org/skia/tests/VerticesGM.kt`
now mirrors upstream `gm/vertices.cpp::VerticesGM`, including the normal and
`vertices_scaled_shader` variants, the 29 `SkBlendMode` grid, paint alpha,
optional `SkColorFilters.Blend(0xFFAABBCC, kDarken)`, the linear-gradient
shader, the blue color shader, and all three vertex attribute combinations.

Reactivation evidence:

- Focused validation with `VerticesTest` compiled and rendered the new GM.
- The normal variant reached `59.70%` similarity against
  `original-888/vertices.png`.
- The scaled variant reached `59.84%` similarity against
  `original-888/vertices_scaled_shader.png`.
- Tranche-2 validation on current `master` reached `62.10103655210039%`
  for `VerticesGM` and `62.177501363884346%` for
  `VerticesScaledShaderGM`.
- `VerticesBatchingGM` still passes at its existing ratchet.

Real blocker:

- The blocker is not fonts, codecs, WGSL, `SkShaders.Color`, or
  the public blend-mode enum. Those dependencies are present on current
  `origin/master`.
- `SkCanvas.drawVertices(vertices, blendMode, paint)` now combines
  interpolated vertex colors with shader samples through the shared
  `SkBlendMode` dispatcher, including the no-`texCoords` shader case.
- `SkColorFilters.Blend` now supports separable/HSL modes, including the
  `kDarken` filter used by `VerticesGM`, and the colored/textured vertices
  paths apply `paint.colorFilter`.
- The remaining observed blocker is visual parity: enabling `VerticesTest`
  still renders at ~62% similarity. Dominant mismatch cells are the
  color-filter columns, especially `Blend(0xFFAABBCC, kDarken)`, plus
  half-alpha columns and broad blend-mode rows (`Difference`, `Exclusion`,
  `Luminosity`, `Modulate`, `Overlay`, `Multiply`, `HardLight`).
- A bounded experiment moving `paint.colorFilter` before paint-alpha
  modulation in both triangle paths regressed slightly (`VerticesGM` to
  `62.06%`, `VerticesScaledShaderGM` to `62.14%`) and was reverted. This
  isolates paint alpha / color-filter ordering as a non-improving path rather
  than a fix candidate.

Next implementation slice:

1. Start from shader-local sampling / gradient interpolation semantics in
   `VerticesGM`, especially `texOnly` and scaled-gradient cells.
2. Add unit coverage only with the next bounded fix candidate; the current
   alpha/colorFilter ordering probe does not justify new golden or ratchet
   coverage.
3. Re-enable `VerticesTest` for both `vertices` and `vertices_scaled_shader`
   only after the remaining mismatch has a bounded implementation fix.

### Mesh reconnaissance

The `mesh` row is an implementation-track blocker, not stale disabled-test
coverage. `skia-integration-tests/src/main/kotlin/org/skia/tests/MeshGMs.kt`
contains stubs for all 11 registrations in upstream `gm/mesh.cpp`, and
`skia-integration-tests/src/test/kotlin/org/skia/tests/MeshTest.kt` is
correctly disabled with `STUB.MESH`.

Reactivation blocker:

- Active modules now have minimal `org.skia.core.SkMesh`,
  `org.skia.core.SkMeshSpecification`, and `SkCanvas.drawMesh(...)` support
  for CPU-backed position-only meshes plus optional `ubyte4_unorm color`
  lowering to `SkVertices`. There is no generated legacy mesh source left in
  the active tree; use upstream Skia and the current implementation as the
  source references.

Implementation entry points:

- Continue the public mesh data/API types under
  `kanvas-skia/src/main/kotlin/org/skia/core/`: `SkMesh`,
  `SkMeshSpecification`, vertex/index buffer abstractions, `Make` /
  `MakeIndexed`, and specification validation. The current CPU subset
  intentionally rejects varyings, uniforms, children, and unsupported
  attributes.
- Extend `SkCanvas.drawMesh(mesh, blender, paint)` only through bounded CPU
  lowering slices. It currently lowers position-only and position+color
  meshes to `SkVertices`; shader-child, color-managed, uniform-driven, zero-init,
  and picture playback cases should remain disabled until their specific
  behavior is implemented.
- Wire picture recording/playback only after the public draw call exists;
  `PictureMeshGM` depends on recording a mesh operation, not only direct
  raster drawing.
- Add focused unit tests for mesh construction and validation before enabling
  any GM; then split `MeshTest.kt` so the first enabled GM is tied to the
  smallest implemented behavior rather than unblocking all 11 at once.

GM unblock order:

1. `custommesh`: basic non-indexed/indexed triangles, CPU vertex/index buffers,
   paint color/shader/blender handling.
2. `custommesh_cs`: mesh color-space and alpha-type handling.
3. `custommesh_uniforms`: uniform packing and fragment-stage uniform evaluation.
4. `mesh_updates` and `mesh_zero_init`: mutable buffers, update offsets, and
   zero-initialization semantics.
5. `picture_mesh`: picture record/playback support for mesh draw ops.
6. `mesh_with_image`, `mesh_with_paint_color`, `mesh_with_paint_image`,
   `mesh_with_effects`: shader/color-filter/blender child slots.
7. `custommesh_cs_uniforms`: color-managed `layout(color)` uniform behavior.
