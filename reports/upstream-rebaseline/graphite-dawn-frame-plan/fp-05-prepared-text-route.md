# FP-05 prepared text product-route evidence

Date: 2026-07-31

Verdict: **completed**

## Scope and accepting head

This report closes only FP-05. `DisplayOp.DrawText` is now either executed by
the common prepared WebGPU frame route or refused terminally. It cannot return
to the immediate, CPU, or legacy text renderer after product admission.

The accepting implementation head is:

```text
ce0ae1f75a53b53689ef85d7b47dd0d7eedae987
feat(surface): activate prepared text routing
```

The reviewed Task 1–14 heads are:

| Task | Accepted head | Bounded result |
|---|---|---|
| 1 | `e39bd5adbe951ffa87e7a464dce530d74848a0dd` | immutable font identity and numeric artifact generation |
| 2 | `fdf5a4dd9326cede05d0aa8405010158f7ddb979` | canonical 4×4 antialiased A8 glyph-mask authority |
| 3 | `25af20160da7178292622d7c2b37368f66a2767f` | one prepared material compiler and parser-validated WGSL |
| 4 | `faf0446effb36a0f4c9949c5e77e202cb761e1f5` | pure transactional text lowerer and canonical refusals |
| 5 | `bf6a686707bee14c6c501ceedb7fc2b68df4a133` | immutable frame-local A8/COLRv0 inventory and blur |
| 6 | `4fe5d69f6d39d2441cb1ac41a161fc5ca8bdddc1` | generic immutable R8 upload/resource plan |
| 7 | `dae32bae574ad0a90436216cf28082579c94f011` | exact TextA8/ColorGlyph semantic expansion |
| 8 | `2316796951d2b1d16d82d76a427ef9e299750295` | mixed upload-before-sample task graph |
| 9 | `3972071f94c1aba559939ca634611fa561c5b804` | pure native preflight, budgets and sealed ownership |
| 10 | `128fa23aecd59ea2343b73c3817d0e4cd9c4db45` | prepared A8 native materialization and composition |
| 11 | `04e9e18cf7c690dcf59bdaf8c660d5f0a0380cf8` | mixed COLRv0/currentColor native route |
| 12 | `0b8c73526c9d1b500a4ea329649071d764e53543` | text stroke, blur and filter boundaries |
| 13 | `5b774e0768fd95240f66ba7edc4022348a8f4206` | independent pixel oracles, native counters and cold frames |
| 14 | `ce0ae1f75a53b53689ef85d7b47dd0d7eedae987` | atomic product cutover and legacy Text removal |

No font public specification was changed by Task 15: the implementation
realizes the already documented contracts without introducing a new public
font API.

## Atomic product decision

`DrawText` is a prepared visual candidate. Once admitted:

- lowering, inventory, recording, preflight, materialization, execution and
  completion refusals are terminal;
- the exact diagnostic is retained and the legacy port is not invoked;
- empty text and empty target/scissor work are typed no-ops before backend
  acquisition;
- wide-open, hard scissor, fractional analytic coverage and common complex
  coverage-mask clips retain their exact execution semantics;
- mixed Core/Image/TextA8/ColorGlyph frames preserve recorded packet order.

`Text` was removed from `LegacyDisplayOpFamily`, its allowlist and its legacy
diagnostic. The remaining legacy families are exactly:

```text
Vertices
Composites
```

The production search was empty:

```text
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.text|LegacyDisplayOpFamily\\.Text" \
  kanvas/src/main gpu-renderer/src/main font
```

## Accepted rendering matrix

| Area | Accepted FP-05 rows |
|---|---|
| A8 glyphs | Exact outline representation, deterministic 4×4 AA coverage, frame-local R8 page, positioned glyphs, affine transform and common clips |
| Emoji | Already-shaped monochrome outline glyphs, already-shaped COLRv0 glyphs, and a ZWJ sequence already reduced to one glyph ID |
| COLRv0 | Ordered layers, CPAL palette color, `currentColor`, shared A8 mask pages and paint alpha applied once |
| Materials | Solid, linear/radial/sweep/conical gradients, supported two-solid BlendShader, registered runtime effect with Kotlin/CPU behavior and parser-validated WGSL, and supported image shader |
| Stroke | Width zero and positive width; BUTT/ROUND/SQUARE caps; MITER/ROUND/BEVEL joins; admitted miter limits and even dash arrays, through the common prepared path authority |
| Mask blur | NORMAL, SOLID, OUTER and INNER; exact sigma/style/transform/padding identity before packing |
| Fixed-function text blend | `CLEAR`, `SRC_OVER`, `DST_OVER`, `DST_IN`, `DST_OUT`, `SRC_ATOP`, `XOR`, `MODULATE`, `SCREEN`, plus typed `DST` no-op |

Text under saveLayer remains a composite operation, not a legacy Text escape.
Its migration belongs to FP-07.

## Upload-before-sample graph and ownership

The frame graph has one immutable R8 artifact/resource authority. Exact
key/generation/content/layout identity deduplicates a page once per frame. The
corresponding upload precedes every TextA8 or ColorGlyph consumer:

```text
Prepare target and frame-local resources
  -> Upload R8 pages and sampled material resources
  -> Render ordered Core / SampledImage / TextA8 / ColorGlyph packets
  -> Optional readback
  -> Queue completion closes frame-owned resources
```

All text instances use one frame-global immutable instance buffer with exact
contiguous packet ranges. Material and draw uniforms use aligned immutable
slabs. Pipeline/layout/sampler entries are session-owned; atlas pages,
textures, views, buffers, uniforms and bind groups are frame/completion-owned.
FP-05 makes no persistent atlas-residency claim.

The measured mixed frame order is:

```text
CorePrimitive -> TextA8 -> SampledImage -> TextA8 -> ColorGlyph
```

Its exact counters are:

```text
visualOperations=5
encoders=1 commandBuffers=1 submits=1 readbacks=1
textA8Instances=2 colorGlyphInstances=2 pathStrokeDraws=0
textPages=1 textPageBytes=262144 textSubRuns=3 textDraws=3
textBindGroups=7 textSubmits=1 totalDrawsAndDrawIndexed=5
activeNativePayloads=0 outputOwnedNativePayloads=0 quarantinedNativePayloads=0
retentionRegistrations=retentionCompletions
retentionQuarantines=0 distinctRetentionTickets=1
```

Completion-only evidence has one encoder and submit, no readback, one target
close and no live/quarantined payload. Runtime close/recreate then succeeds
with one new readback and the same close-once ownership invariants.

## Pixel and color evidence

The independent A8 oracle is:

```text
straight sRGB material -> linear
-> material alpha × paint alpha
-> premultiply once
-> multiply RGBA by glyph/clip coverage
-> common blend
-> sRGB attachment encoding
```

COLRv0 starts from the independently decoded primitive layer color, not the
text paint shader color, and applies paint alpha exactly once.

Fresh native XML markers from the accepting aggregate are:

```text
task13.native prepared=true skipped=0 encoders=1 submits=1 readbacks=1 maxChannelDelta=0
task11.native-source-colrv0 available=true executed=1 skipped=0 maxChannelDelta=0
fp05.task12.text-blur.native available=true executed=1 skipped=0 maxChannelDelta=1
```

The six partial-paint-alpha rows execute `CLEAR`, `DST_IN` and `MODULATE`,
each wide-open and scissored. Their measured maximum deltas are respectively
`0, 0, 1, 1, 1, 1`, under the exact one-LSB limit. The mixed Core/Text/Image/
ColorGlyph samples also all remain within one LSB.

The broad 1,864-case product matrix uses its existing tolerance of two bytes;
it is not relabeled as one-LSB evidence. The independent FP-05 native oracles
above are the one-LSB acceptance evidence.

## Cold-frame measurements

The current lane rebuilds 30 independent inventories and frame-local pages.
No warmup sample is discarded and no cache-hit, warm-frame or Graphite
performance claim is inferred.

```text
samples=30
loweringRawNs=[606208, 627042, 639000, 680375, 694917, 695250, 703417, 711458, 721167, 726709, 741125, 774500, 863375, 864542, 911958, 914875, 919166, 929375, 935208, 947042, 964208, 983584, 1037500, 1083500, 1096084, 1176292, 1243667, 1397917, 1423542, 3585375]
rasterRawNs=[824125, 846458, 857083, 872500, 876916, 890792, 895167, 902375, 907083, 909750, 923584, 956708, 998959, 1011375, 1027292, 1029166, 1034583, 1040667, 1056375, 1075250, 1088334, 1095166, 1097958, 1130917, 1145500, 1157791, 1162833, 1190542, 1196542, 1832625]
packingRawNs=[5741875, 5746625, 5759166, 5773417, 5776375, 5780458, 5796291, 5834958, 5871833, 5883708, 5897833, 5905625, 5920166, 5975125, 5979458, 5984916, 5986209, 5996959, 6008167, 6010292, 6014542, 6023500, 6041875, 6048375, 6111042, 6136292, 6149125, 6189208, 6193792, 6217125]
totalRawNs=[7334958, 7372626, 7493333, 7606459, 7622166, 7629083, 7631708, 7699416, 7706249, 7732625, 7741543, 7762583, 7784875, 7787416, 7833417, 7849000, 7856374, 7897584, 7904416, 7917209, 7955626, 8004708, 8015333, 8036333, 8172583, 8242209, 8314834, 8327876, 9212584, 10942000]
p50Index=14 p50Ns=7833417
p95Index=27 p95Ns=8327876
```

These numbers characterize only this deterministic cold-frame fixture on the
measured host. Final comparative benchmarks and GM performance evidence remain
FP-11.

## Stable refusal matrix

The 36-test lowerer/refusal suite covers 29 parameterized refusal/priority
rows plus order and immutability checks. The distinct externally observed
lowerer/material codes in those rows are:

```text
unsupported.text.typeface_missing
unsupported.text.typeface_unsupported
unsupported.text.font_identity_unstable
unsupported.text.font_bytes_malformed
unsupported.text.position_count_mismatch
unsupported.text.font_size_invalid
unsupported.text.position_nonfinite
unsupported.text.glyph_id_invalid
unsupported.text.notdef_unavailable
unsupported.text.bitmap_cbdt_cblc_unsupported
unsupported.text.bitmap_sbix_unsupported
unsupported.text.svg_plan_unsupported
unsupported.text.colrv1_unproved
unsupported.text.representation_missing
unsupported.text.origin_nonfinite
unsupported.text.transform_nonfinite
unsupported.text.transform_singular
unsupported.text.transform_perspective
unsupported.text.clip_route_unaccepted
unsupported.text.paint_style_unsupported
unsupported.text.blend_unsupported
unsupported.text.image_filter_requires_composite
unsupported.text.mask_filter_unsupported
unsupported.material.mapping.noise_shader
```

Additional explicit material/filter boundaries are:

```text
unsupported.text.path_effect_unsupported
unsupported.material.paint_alpha
unsupported.material.runtime_effect.descriptor
unsupported.material.runtime_effect.wgsl_not_available
unsupported.material.runtime_effect.uniform_payload
unsupported.material.runtime_effect.children
unsupported.material.blend_shader
unsupported.material.wgsl_validation
```

The native text preflight corruption/budget matrix uses one canonical
authority for these exact codes:

```text
unsupported.preflight.prepared_text_unmaterialized
stale.preflight.text.atlas_generation
invalid.preflight.text.page_bytes
invalid.preflight.text.page_dimensions
invalid.preflight.text.page_row_bytes
unsupported.preflight.text.r8unorm
invalid.preflight.text.instance_uv
invalid.preflight.text.instance_stride
invalid.preflight.text.instance_range_overlap
invalid.preflight.text.instance_buffer_range
invalid.preflight.text.material_abi
invalid.preflight.text.wgsl_entry_point
invalid.preflight.text.binding_layout
invalid.preflight.text.material_uniforms
invalid.preflight.text.material_resources
invalid.preflight.text.upload_missing
invalid.preflight.text.upload_duplicate
invalid.preflight.text.upload_order
invalid.preflight.text.target
invalid.preflight.text.scissor
invalid.preflight.text.clip
invalid.preflight.text.blend
invalid.preflight.text.resource_lifetime
invalid.preflight.text.dependency
invalid.preflight.text.operand
invalid.preflight.text.operand_ownership
unsupported.preflight.text.texture_limit
unsupported.preflight.text.instance_buffer_limit
unsupported.preflight.text.copy_alignment
```

For TextA8, fixed-function modes outside the accepted list terminate at
`invalid.preflight.text.blend` before destination snapshot or native
allocation. Valid complex clips use the common analytic/coverage-mask route;
only a clip rejected by that common authority produces
`unsupported.text.clip_route_unaccepted`. Image filters on text remain
terminal with `unsupported.text.image_filter_requires_composite` until FP-07.

## Fresh aggregate validation

The final Task 15 command was:

```text
rtk proxy ./gradlew :font:core:test :font:glyph:test :font:gpu-api:test \
  :font:test :gpu-renderer:test :kanvas:test --no-parallel
```

Result: `BUILD SUCCESSFUL`.

| Module | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| `:font:core` | 84 | 0 | 0 | 0 |
| `:font:glyph` | 155 | 0 | 0 | 0 |
| `:font:gpu-api` | 106 | 0 | 0 | 0 |
| `:font` | 19 | 0 | 0 | 0 |
| `:gpu-renderer` | 3,004 | 0 | 0 | 0 |
| `:kanvas` | 3,071 | 0 | 0 | 0 |
| **Total** | **6,439** | **0** | **0** | **0** |

Important focused manifests in the same fresh results include:

```text
GPUAllApiBlendSurfaceTest                         1864/1864
GPUPreparedTextNativePreflightTest                 106/106
GPUFramePreflighterTest                              99/99
GPUPreparedTextOwnershipTest                         19/19
GPUPreparedSurfaceTextNativeSmokeTest                12/12
GPUPreparedTextRefusalMatrixTest                     36/36
GPUPreparedTextLowererTest                           31/31
GPUPreparedTextStrokeTest                             9/9
GPUPreparedTextBlurTest                               4/4
GPUPreparedTextFilterBoundaryTest                     2/2
GPUPreparedEmojiTextTest                              4/4
GPURendererPackageBoundaryTest                       22/22
```

The native results were produced on host `Omega`, Darwin arm64/macOS, through
`wgpu4k-native`; the observed adapter is `Apple M2 Max`. The build uses
Temurin JDK `25.0.1`. Native FP-05 markers report `available=true` or
`prepared=true`, `executed=1` where applicable and zero skip; no unavailable
adapter assumption converted native evidence into green.

Task-level independent reviews repaired every legitimate Critical/Important
finding. The final Task 14 review at `ce0ae1f75` reported `READY`, C0/I0, and
the complete serial aggregate above remained green after its corrective
oracle and packet-order fixes.

## Dependency findings

FP-05 did not expose a new wgpu4k defect.

The branch already carries one explicit, non-hidden wgsl4k lowerer gap:
[wgsl4k#14](https://github.com/ygdrasil-io/wgsl4k/issues/14). With the reviewed
wgsl4k snapshot, valid component access such as
`textureLoad(...).r` is incorrectly inferred as access on the texture handle
and raises `LoweringError`; the equivalent indexed access through the returned
vector lowers successfully. The ticket contains the minimized WGSL
reproduction, affected revision, expected behavior and root cause. The
prepared coverage-mask source records this constraint in its KDoc and uses the
standards-valid indexed form; the complete parser/lowering/ABI and native
evidence above remains green. The issue is open but did not require a second
FP-05-specific ticket or a hidden semantic workaround.

## Explicit nonclaims

- FP-06 remains pending: vertices and mesh are not migrated by this report.
- FP-07 remains pending: saveLayer, text image filters, layers, masks,
  pictures and backdrop composites are not promoted.
- FP-08 remains pending: the legacy adapter still exists for Vertices and
  Composites.
- FP-09 remains pending: there is no persistent atlas residency, eviction,
  inter-frame page reuse or reusable-session claim.
- FP-10 remains pending: this report does not close every bounded native
  stroke, coverage, sampling, filter or runtime-effect gap outside FP-05.
- FP-11 remains pending: no GM regeneration, similarity-score update, release
  performance verdict or Graphite performance-parity claim is made here.
- Implicit shaping, font fallback, COLRv1, CBDT/CBLC, sbix, SVG color glyphs,
  LCD/subpixel text and arbitrary SkSL compilation remain unsupported.
- Animation and `unsupported.image.animation` are unchanged.
- No Ganesh or Graphite backend, SkSL compiler/IR/VM, multi-backend hierarchy,
  CPU destination snapshot fallback or compatibility reupload was added.
