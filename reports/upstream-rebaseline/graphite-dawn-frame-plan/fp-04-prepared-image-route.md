# FP-04 prepared image product-route evidence

Date: 2026-07-28

Verdict: **completed**

## Scope and commit chain

This report closes only FP-04. It activates the already prepared
`DrawImage`, `DrawImageNine`, `DrawImageLattice`, and `DrawAtlas` work as one
atomic Surface product-route change. It does not change FP-05 or claim text,
vertices, composite, codec, or animation promotion.

The implementation chain used by this cutover is:

- mixed prepared-frame materialization: `983ce190c`;
- direct DrawImage product preparation: `5cc0225e3`;
- image-nine and lattice preparation: `f913b62fb`;
- affine atlas and image evidence: `0414290a1`;
- atlas review repairs through the Task 9 head: `0f61b130f`;
- atomic product admission and this evidence: the commit containing this
  report, with subject `feat(surface): activate prepared image routing`;
- post-cutover Blender authority and refusal evidence repair: the commit
  containing this update, with subject
  `fix(surface): honor prepared image blender authority`.

The earlier resource, ABI, cache, and sRGB acceptance evidence remains in
`fp-04-task-5-review.md` and `fp-04-srgb-store.md`. This report records the
product cutover rather than substituting historical evidence for current
validation.

## Atomic product decision

The whole-frame gate now classifies all four image operation families as
prepared candidates. Once an image frame is admitted:

- builder, preflight, WGSL, resource, or native refusal is terminal;
- the exact prepared diagnostic is retained;
- the legacy port is never invoked after admission;
- invalid images cannot partially execute or fall back;
- pure image frames use `prepared.surface.direct` without a synthetic
  CorePrimitive draw.

`Images` was removed from `LegacyDisplayOpFamily`, its allowlist, and its
classification branch. The legacy allowlist is exactly:

```text
Text
Vertices
Composites
```

Core-only before-entry refusal, Text, Vertices, Composites, and unsupported
public color formats preserve their previous fallback boundary. No image
legacy route or legacy image diagnostic remains in production or tests.

## Native route and ownership evidence

The product native smoke covers direct image-only, mixed core/image, nine,
lattice, affine atlas, A8 tint, RGBA tint, and hardware sRGB store frames.
Every accepted image case records `prepared.surface.direct`.

The image-only frame proves:

- one dispatched DrawImage;
- no fabricated core semantic, core route, core uniform slab, or path
  depth/stencil view;
- transparent pixels outside the destination;
- one native submission;
- zero active native payloads after completion.

The mixed direct/path/direct native frame proves one target creation and close,
one coordinator, one encoder, one command buffer, one submit, one readback
copy, one render pass, four indexed draws, four pipeline binds, and:

```text
destinationSnapshotCreations=0
destinationReadbackSnapshots=0
retentionQuarantines=0
activeNativePayloads=0
outputOwnedNativePayloads=0
quarantinedNativePayloads=0
```

Retention registrations equal completions and the frame owns one distinct
retention ticket. This route performs no destination CPU snapshot,
compatibility readback, or texture reupload.

## Upload, texture, sampler, binding, and uniform counts

The current resource/materializer tests lock the following exact cases:

| Prepared run | Uploads | Textures | Views | Samplers | Uniform buffers | ABI112 records | Bind groups | Pipelines |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| one artifact, nearest/nearest/linear, three draws | 1 | 1 | 1 | 2 | 1 | 3 | 2 | 1 |
| two artifacts, same nearest sampler descriptor | 2 | 2 | 2 | 1 | 2 | 2 | 2 | session-cached |

The three command-exact uniform records use dynamic offsets
`[0, 256, 512]`; each record is 112 bytes. The two nearest draws share the
same bind group while the linear draw uses the second sampler/bind group. One
artifact is uploaded exactly once before every consumer. Two artifacts with
the same sampler descriptor share one native sampler but retain independent
texture, view, uniform-buffer, and bind-group ownership. Successful teardown
closes every owned identity exactly once.

Image-only mixed-frame materialization emits the exact ordered partition:

```text
TextureUpload -> Render(SampledImage) -> Readback
```

It contains no compatibility operand and no empty CorePrimitive materializer
result.

## Physical color and pixel evidence

The bounded SDR contract is:

```text
source.color=RGBA8UnormSrgb
source.coverage=RGBA8Unorm
source.colorUploadEncoding=StraightEncodedSrgb
target=RGBA8UnormSrgb
shaderInterpretation=linear-premul
attachmentSrgbConversion=true
oracleMaxChannelDelta=0 limit<=1
```

`Image.alphaType` is authoritative. RGBA and BGRA sources normalize through
straight encoded-sRGB upload; A8 expands to a linear coverage texture. Source
stride, tight normalized stride, and 256-byte native upload stride remain
distinct. Width-three A8 and BGRA tests assert zeroed upload padding.

The direct translucent RGBA image-only readback is exact:

```text
(1,1) = [188, 0, 0, 128]
(2,1) = [0, 188, 0, 128]
(1,2) = [0, 0, 188, 128]
(2,2) = [188, 188, 188, 128]
```

The independent pixel suite records a maximum channel delta of zero against a
one-LSB policy. BGRA channel order, A8 half/full coverage, RGBA paint-alpha,
and transparent exterior pixels are asserted separately. The direct native
inventory smoke also proves the canonical sRGB target authority and exact
readback `[188,0,0,128]` followed by `[137,188,0,192]`.

## Nine, lattice, and affine atlas evidence

- Image-nine expands to nine row-ordered sampled packets sharing one artifact.
  Its integral hard scissor is applied identically to every packet.
- Lattice lowering preserves sampled, fixed-color, and transparent cells in
  source order; transparent cells emit no packet and fixed-color-only
  lattices allocate no image resource.
- Atlas retains identity, translation, scale, rotation, reflection, and skew
  as exact four-corner affine quads. It never replaces a skewed sprite with
  its bounding box.
- Later invalid cells/sprites refuse the entire logical operation without a
  valid prefix.
- Native nine/lattice/atlas pixels use exact or one-LSB assertions. No affine
  channel diff exceeds the declared one-LSB policy.

The closed atlas source-blend set is:

```text
SRC, DST, SRC_OVER, PLUS, MODULATE
```

The other 24 public BlendMode values refuse transactionally with
`unsupported.image.atlas.source_blend`. Direct A8 and RGBA native tests cover
all five accepted modes; RGBA paint RGB is neutral and paint alpha is applied
once, while A8 tint and coverage are applied once.

## Stable refusal and zero-allocation evidence

The canonical FP-04 refusal table is:

```text
unsupported.image.pixels_missing
unsupported.image.dimensions
unsupported.image.pixel.row_stride
unsupported.image.pixel.length
unsupported.image.pixel.format
unsupported.image.alpha_interpretation
unsupported.color.image_profile_conversion
unsupported.color.gamut_transform
unsupported.image.orientation
unsupported.color.yuv_conversion
unsupported.color.hdr_transfer
unsupported.image.codec.unregistered
unsupported.image.animation
unsupported.texture.import_unvalidated
unsupported.image.upload.budget_exceeded
unsupported.image.texture_limit
unsupported.image.mip_required
unsupported.image.sampling_cubic
unsupported.image.sampling_anisotropic
unsupported.image.tile_mode
unsupported.image.perspective_sampling
unsupported.image.nine_geometry
unsupported.image.lattice_geometry
unsupported.image.atlas.array_lengths
unsupported.image.atlas.geometry
unsupported.image.atlas.source_blend
unsupported.image.native_generation
unsupported.image.native_binding
unsupported.image.wgsl_validation
```

Complex/antialiased image clips additionally terminate at the Surface seam
with `unsupported.surface.prepared.image-clip`. Unbound image paint effects
(`colorFilter`, `maskFilter`, and `imageFilter`) terminate with
`unsupported.image.native_binding`, facts
`reason=unsupported_paint_effect` and the exact `paintField`. These operations
are FP-07 nonclaims, not silently ignored effects.

Direct DrawImage resolves destination composition from `paint.blender` before
the legacy `paint.blendMode` field. `Blender.Mode(SRC_OVER)` is accepted even
when the legacy field differs; other resolved modes retain the exact
native-binding refusal. `Blender.Arithmetic` terminates with
`reason=unsupported_blender` and `blenderKind=Arithmetic`.

The refusal matrix preserves one code through source preparation, Surface
mapping, recording, preflight, and the product terminal exception. Texture
limit, invalid WGSL, generation, binding, mixed late-surface, invalid clip,
invalid atlas blend, and paint-effect tests assert refusal before native
session preparation. The product-entry tests use the real executor and
prove the legacy port remains untouched.

## TDD and current validation

All Gradle commands used JDK 25, `--dependency-verification=off`,
`--no-daemon`, `--console=plain`, and `--max-workers=1`.

The initial product inversion ran 80 tests with seven expected failures. The
same four product classes passed 80/80 after the atomic cutover.

The image-only native inversion ran 132 tests with four expected failures.
After enabling the sealed image-only route, the same four classes passed
132/132.

Independent review found that image paint effects were silently discarded.
The focused RED run failed 2/2 new contracts; the GREEN run passed 2/2 after
transactional validation was shared by DrawImage, image-grid, and atlas
lowerers.

Integral image scissor support was then specified before implementation. The
RED run failed 3/3 and the GREEN run passed 3/3 after exact hard-scissor
classification was added; complex, fractional-AA, invalid, and empty clips
remain exact terminal refusals.

Post-cutover review found that direct DrawImage ignored `paint.blender`. The
focused RED run executed 58 tests with exactly four expected failures; the
same three classes passed 58/58 after the minimal authority repair. The Atlas
paint-effect contract is parameterized over all three unbound effect fields,
and product-router coverage exercises all twelve effect/family combinations
plus both direct Blender refusal shapes before native session preparation.

Final focused GPU validation:

| Suite | Passed |
|---|---:|
| `PreparedImageContractsTest` | 8/8 |
| `GPUPreparedImagePayloadTest` | 9/9 |
| `GPUPreparedSurfaceFrameTaskListBuilderTest` | 22/22 |
| `GPUPreparedImageFrameResourcePlanTest` | 5/5 |
| `GPUPreparedSurfaceNativePreflightTest` | 16/16 |
| `GPUPreparedImageShaderTest` | 4/4 |
| `GPUWgpu4kPreparedImageRenderRunMaterializerTest` | 24/24 |
| `GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest` | 12/12 |

Result: **100/100**, zero failure, zero skip.

Final focused Kanvas validation:

| Suite | Passed |
|---|---:|
| `GPUPreparedImageSourceTest` | 2/2 |
| `GPUPreparedDrawImageLowererTest` | 36/36 |
| `GPUPreparedImageGridLowererTest` | 11/11 |
| `GPUPreparedAtlasLowererTest` | 14/14 |
| `GPUPreparedSurfaceImagePixelTest` | 1/1 |
| `GPUPreparedImageRefusalMatrixTest` | 3/3 |
| `GPUPreparedSurfaceFrameGateTest` | 5/5 |
| `GPUPreparedSurfaceProductRouterTest` | 8/8 |
| `GPUPreparedSurfaceProductEntryTest` | 6/6 |
| `GPUFramePathApiInventoryTest` | 63/63 |
| `GPUPreparedSurfaceProductNativeSmokeTest` | 8/8 |

Result: **157/157**, zero failure, zero skip. Together with the focused
gpu-renderer group, FP-04 validation is **257/257**.

The expanded affected-suite group also passed:

```text
GPUClipCoverageSurfaceTest                  41/41
GPUImageFilterSurfaceTest                    8/8
SurfaceTest                                 10/10
GPUPreparedSurfaceSemanticBuilderTest        4/4
GPUFramePathApiInventoryNativeSmokeTest       1/1
```

The image rows in `GPUAllApiBlendSurfaceTest` execute 16 prepared pixel cases,
332 exact terminal-refusal cases with one Terminal decision, no destination
readback snapshot, and no Legacy decision, plus 116 explicit saveLayer legacy
pixel cases. Every image row passes.

Module aggregate truth is recorded without converting unrelated failures into
green results:

- `:gpu-renderer:test`: 2,500 tests, 2,499 passed, one pre-existing Task 9
  package-boundary failure for execution imports of clip semantic contracts;
- `:kanvas:test`: 2,708 tests, 2,656 passed, 52 failures, all confined to
  `GPUAllApiBlendSurfaceTest` DrawPath (26) and DrawDRRect (26) core baselines.

No aggregate failure belongs to an image route, pixel, alpha, gate, router,
entry, ownership, allowlist, clip, filter-refusal, semantic, or native smoke
contract.

## Explicit nonclaims

- Encoded codecs and animated-image decoding are not promoted.
- Animation behavior and fixtures are unchanged.
- HDR, YUV, gainmaps, wide gamut, arbitrary color profiles, and unresolved
  orientation remain refused.
- External/imported textures remain unvalidated and refused.
- Mipmaps, cubic filtering, anisotropic filtering, repeat, mirror, decal, and
  perspective image sampling are not supported by this route.
- Complex and fractional-AA image clips remain terminal; only wide-open and
  exact integral scissor image clips are admitted.
- Image color filters, mask filters, and image filters are not implemented by
  FP-04; they refuse instead of being ignored.
- There is no inter-frame prepared-image resource cache claim. Pipeline cache
  ownership remains session-scoped; image handles remain frame-owned.
- Text, vertices/mesh, layers, pictures, saveLayer, filters, masks, and
  backdrop composites retain their own later FP items.
- No Ganesh or Graphite backend, SkSL compiler/VM, CPU destination snapshot,
  compatibility reupload, or non-WebGPU backend was added.
