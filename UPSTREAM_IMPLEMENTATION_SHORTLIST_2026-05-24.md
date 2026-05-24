# Implementation shortlist

Snapshot date: 2026-05-24.

Source: `reports/upstream-rebaseline/2026-05-24.tsv`, filtered to
`bucket=implementation`.

Purpose: pick implementation work that is useful while fonts, codecs, and
WGSL/parser delivery are still pending.

## Summary

The rebaseline currently classifies 6 upstream `.cpp` rows as
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
- `STUB.DRAW_VERTICES`: `VerticesBatchingGM` is ported through the existing
  `drawVertices` raster path and the test is enabled. `VerticesGM` is now
  source-ported, but remains disabled because raster `drawVertices` still
  needs full vertex color/shader blend-mode semantics.
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
  working color spaces. The remaining blocker is the actual perceptual
  color-space / hue-method / premul interpolation sampler.
- `addarc`: `SkPathBuilder.emitArc` now normalises huge sweeps before conic
  decomposition, and `ManyArcsGM` is enabled with a raster ratchet.
- `STUB.TEXT_IMAGE_FILTER`: `imagefiltersbase`, `textfilter_image`, and
  `textfilter_color` are covered on raster. Remaining disabled wrappers are
  WebGPU text-filter dependency work, not an actionable raster
  `imagefiltersbase` implementation item.

## Recommended order

| Priority | Track | Impact | Effort | Why now |
|---:|---|---:|---|---|
| 1 | `vertices` | 1 cpp | M | `VerticesBatchingGM` is already ported; the remaining raster `VerticesGM` slice may be smaller than the row suggests. |
| 2 | `recordopts` | 1 cpp | M | The stale placeholder is gone and the remaining saveLayer/color-filter fold delta is measured. |
| 3 | `gradients` | 1 cpp | M/L | RGB API surface exists; remaining work is the real interpolation sampler. |
| 4 | `mesh` | 1 cpp | L/XL | Actionable but broad API work; start with the `custommesh` slice only. |
| 5 | `STUB.RSXBLOB` / `STUB.DF_TEXT_RASTER` | 2 cpps (`drawatlas`, `dftext_blob_persp`) | L/XL | Text/glyph transform work; defer if font delivery may change internals. |

## Implementation bucket rows

| Upstream cpp | Tags | Local GM files |
|---|---|---|
| `dftext_blob_persp` | `STUB.DF_TEXT_RASTER` | `DFTextBlobPerspGM.kt` |
| `drawatlas` | `STUB.RSXBLOB` | `BlobRSXformDistortableGM.kt`, `BlobRSXformGM.kt`, `CompareAtlasVerticesGM.kt`, `DrawAtlasGM.kt`, `DrawTextRSXformGM.kt` |
| `gradients` | `STUB.GRADIENT_INTERPOLATION`; RGB `SkGradient` overload exposed, perceptual/hue/premul sampler still missing | gradient interpolation variants |
| `mesh` | `STUB.MESH`; all 11 upstream registrations are compile-pinned, but none can be re-enabled before `SkMesh` / `SkMeshSpecification` and `SkCanvas.drawMesh` exist in the active modules | `MeshGMs.kt` |
| `recordopts` | `STUB.RECORDOPTS.SAVELAYER_COLOR_FILTER_FOLD`; reactivation audit rendered 67.96% vs `original-888/recordopts.png` | `RecordOptsGM.kt` |
| `vertices` | `STUB.DRAW_VERTICES_VERTEX_BLEND`; `VerticesGM` is source-ported, but focused validation renders ~59% for both `vertices` and `vertices_scaled_shader` because `drawVertices` only combines vertex colors with shader samples for `kSrc`, `kDst`, and `kModulate` | `Skbug13047GM.kt`, `VerticesBatchingGM.kt`, `VerticesCollapsedGM.kt`, `VerticesGM.kt`, `VerticesPerspectiveGM.kt` |

## Notes

- `STUB.MISSING_API` is too vague. The affected rows (`aaclip`, `addarc`)
  need tag cleanup before implementation selection.
- `dashcubics` is now ported: `SkTrimPathEffect` is implemented and
  `TrimGM` is reactivated with a 90% ratchet (`92.29%` in validation).
- `recordopts` no longer carries the stale `STUB.XYZ` placeholder; the
  focused blocker is the saveLayer / detector color-filter fold path.
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
- The normal variant reached `59.09%` similarity against
  `original-888/vertices.png`.
- The scaled variant reached `59.16%` similarity against
  `original-888/vertices_scaled_shader.png`.
- `VerticesBatchingGM` still passes at its existing ratchet.

Real blocker:

- The blocker is not fonts, codecs, WGSL, `SkShaders.Color`, color filters, or
  the public blend-mode enum. Those dependencies are present on current
  `origin/master`.
- The remaining blocker is `SkCanvas.drawVertices(vertices, blendMode, paint)`
  semantics. Raster currently combines per-vertex colors with shader samples
  only for `kSrc`, `kDst`, and `kModulate`; upstream `VerticesGM` intentionally
  exercises all 29 vertex blend modes. Enabling the GM now would ratchet a
  known-wrong image rather than prove upstream parity.

Next implementation slice:

1. Extend the raster `drawVertices` path so the `blendMode` argument combines
   interpolated vertex color and shader sample for every `SkBlendMode`, while
   `paint.blendMode` remains the destination compositing mode.
2. Add focused unit tests in `DrawVerticesTest` for representative advanced
   vertex blends such as `kSrcOver`, `kScreen`, `kOverlay`, and one HSL mode.
3. Re-enable `VerticesTest` for both `vertices` and `vertices_scaled_shader`
   with a similarity floor only after the full vertex-blend matrix is covered.

### Mesh reconnaissance

The `mesh` row is an implementation-track blocker, not stale disabled-test
coverage. `skia-integration-tests/src/main/kotlin/org/skia/tests/MeshGMs.kt`
contains stubs for all 11 registrations in upstream `gm/mesh.cpp`, and
`skia-integration-tests/src/test/kotlin/org/skia/tests/MeshTest.kt` is
correctly disabled with `STUB.MESH`.

Reactivation blocker:

- Active modules have no `org.skia.core.SkMesh`,
  `org.skia.core.SkMeshSpecification`, `SkCanvas.drawMesh(...)`, or
  device-level draw-mesh dispatch. The generated legacy files under
  `kanvas-legacy/src/generated/core/org/skia/core/SkMesh*.kt` are incomplete
  translation artifacts and should be used only as source notes.

Implementation entry points:

- Add public mesh data/API types under
  `kanvas-skia/src/main/kotlin/org/skia/core/`: `SkMesh`,
  `SkMeshSpecification`, vertex/index buffer abstractions, `Make` /
  `MakeIndexed`, and specification validation for attributes, varyings,
  vertex stride, uniforms, children, bounds, draw mode, offsets, and
  CPU-backed buffer updates.
- Add `SkCanvas.drawMesh(mesh, blender, paint)` near the existing
  `drawVertices(...)` implementation in
  `kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt`; dispatch through
  the top device after applying clip/CTM behavior consistent with other
  canvas draws.
- Add device handling in the active raster path, starting from
  `kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt`. A first
  useful slice can rasterize constant-position/constant-color mesh specs by
  lowering compatible meshes to `SkVertices`; shader-child, color-managed,
  uniform-driven, update, zero-init, and picture playback cases should remain
  disabled until their specific behavior is implemented.
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
