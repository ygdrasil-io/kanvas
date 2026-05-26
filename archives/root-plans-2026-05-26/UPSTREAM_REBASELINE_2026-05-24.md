# Upstream rebaseline

Snapshot date: 2026-05-24.

This is the first mechanical rebaseline after archiving the old migration
plans. It is intentionally evidence-first: counts below come from the
current tree and the local upstream Skia checkout, not from archived
phase-plan checkboxes.

## Scope

Reference upstream tree:

- `/Users/chaos/workspace/kanvas-forge/skia-main/`
- GM sources scanned at `/Users/chaos/workspace/kanvas-forge/skia-main/gm`

Repository areas scanned:

- `skia-integration-tests/src/main/kotlin/org/skia/tests`
- `skia-integration-tests/src/test/kotlin/org/skia/tests`
- `integration-tests/src/main/kotlin/org/skia/tests`
- `integration-tests/src/test/kotlin/org/skia/tests`
- `kanvas-skia/src`
- `cpu-raster/src`
- `gpu-raster/src`
- `codec-*`

No test suite was executed for this snapshot. This report classifies the
static surface and disabled/stub signals that are already present.

Generated TSV:

- `reports/upstream-rebaseline/2026-05-24.tsv`

Implementation shortlist:

- `UPSTREAM_IMPLEMENTATION_SHORTLIST_2026-05-24.md`

Generator:

- `scripts/gm/upstream-rebaseline.sh`

## Mechanical counts

| Signal | Count | Command shape |
|---|---:|---|
| Upstream GM `.cpp` files | 437 | `find skia-main/gm -maxdepth 1 -name '*.cpp'` |
| Local `*GM.kt` files in integration GM sources | 753 | `find skia-integration-tests/src/main ... integration-tests/src/main ... -name '*GM.kt'` |
| GPU test files under `gpu-raster` | 354 | `find gpu-raster/src/test/kotlin/org/skia/gpu/webgpu -name '*Test.kt'` |
| WGSL shader resources | 26 | `find gpu-raster/src/main/resources/shaders -name '*.wgsl'` |
| `@Disabled(...)` annotations in test sources | 231 | `rg '@Disabled\\(' .../src/test` |
| Distinct `STUB.*` tags in Kotlin sources | 111 | `rg -o 'STUB\\....' ... | sort -u` |

`scripts/gm/gm-status.sh --summary` reports the current GM-source
matching baseline:

| GM status | `.cpp` files |
|---|---:|
| `PORTED` | 313 |
| `TEST_DISABLED` | 47 |
| `STUB` | 2 |
| `PARTIAL` | 55 |
| `MISSING` | 17 |
| `HELPER` | 3 |

The generated TSV applies a first-pass bucket on top of those statuses:

| Bucket | `.cpp` rows |
|---|---:|
| `ported` | 309 |
| `implementation` | 30 |
| `gpu-intractable` | 22 |
| `partial-coverage` | 17 |
| `missing-mapping` | 17 |
| `codec-gated` | 11 |
| `font-gated` | 9 |
| `slow-or-reference` | 6 |
| `fixture-gated` | 4 |
| `wgsl-runtime-gated` | 4 |
| `alias` | 4 |
| `helper` | 3 |
| `platform-gated` | 1 |

Important interpretation notes:

- `753` local GM files is not a coverage percentage. The local tree has
  split variants, aliases, GPU-specific tests, and compile-pinned stubs.
- The archived "357 / 437 GM files ported" figure remains a hypothesis
  until a GM-name mapping is regenerated.
- `@Disabled` is a triage signal, not always a failure. Some disabled
  tests are aliases, stress tests, missing references, or intentionally
  intractable Ganesh/Graphite cases.

## Highest-frequency source tags

Top `STUB.*` tags across scanned Kotlin sources:

| Tag | Hits | Track |
|---|---:|---|
| `STUB.EMOJI_TABLES` | 47 | font-gated |
| `STUB.YUVA_PIXMAPS` | 35 | codec/GPU-format gated |
| `STUB.FONTATIONS` | 35 | font-gated |
| `STUB.COMPRESSED_TEXTURES` | 29 | codec/GPU-format gated |
| `STUB.FIXTURE` | 26 | asset-gated |
| `STUB.GPU_GANESH_BLUR_UTILS` | 25 | intractable Ganesh |
| `STUB.MESH` | 23 | implementation / GPU feature |
| `STUB.COLR_V1` | 22 | font-gated |
| `STUB.RSXBLOB` | 21 | implementation / text |
| `STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET` | 18 | Ganesh / GPU texture effect |
| `STUB.EDGE_AA_IMAGE_SET` | 17 | implementation |
| `STUB.ASYNC_RESCALE_READ` | 15 | implementation / readback |
| `STUB.LAZY_YUV_IMAGE` | 13 | codec/GPU-format gated |
| `STUB.FFMPEG` | 12 | codec-gated |
| `STUB.LIBERATION_FM` | 11 | font-gated |
| `STUB.WEBP_LOSSY` | 10 | codec-gated |
| `STUB.GRADIENT_INTERPOLATION` | 10 | implementation |
| `STUB.COLOR4F_BLEND_CF` | 10 | implementation |
| `STUB.MAKE_WITH_COLOR_FILTER` | 8 | implementation |
| `STUB.COLOR_FILTER_PRIV` | 8 | implementation |
| `STUB.CLIP_SUPER_RRECT` | 8 | WGSL/runtime-effect gated |
| `STUB.SKSL` | 6 | WGSL/runtime-effect gated |
| `STUB.GAUSSIAN_COLOR_FILTER` | 5 | implementation |

## Disabled-test signals

Top tags in `@Disabled(...)` test annotations:

| Tag | Disabled hits | Track |
|---|---:|---|
| `INTRACTABLE.GPU_ONLY` | 14 | intractable / no raster target |
| `STUB.FIXTURE` | 13 | asset-gated |
| `STUB.EMOJI_TABLES` | 13 | font-gated |
| `STUB.YUVA_PIXMAPS` | 11 | codec/GPU-format gated |
| `ALIAS` | 11 | not backlog |
| `STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET` | 9 | Ganesh / GPU texture effect |
| `STUB.GPU_GANESH_BLUR_UTILS` | 9 | intractable Ganesh |
| `STUB.FONTATIONS` | 8 | font-gated |
| `STUB.EDGE_AA_IMAGE_SET` | 8 | implementation |
| `STUB.COMPRESSED_TEXTURES` | 8 | codec/GPU-format gated |
| `STUB.RSXBLOB` | 6 | implementation / text |
| `STUB.ASYNC_RESCALE_AND_READ` | 6 | implementation / readback |
| `STUB.DF_TEXT_RASTER` | 5 | implementation / text |
| `STUB.LAZY_YUV_IMAGE` | 4 | codec/GPU-format gated |
| `STUB.DRAW_VERTICES` | 4 | implementation |
| `STUB.COLR_V1` | 4 | font-gated |
| `STUB.CLIP_SUPER_RRECT` | 4 | WGSL/runtime-effect gated |

## Reclassified backlog buckets

### Dependency-gated: fonts

Do not spend implementation time here until the font delivery lands.

- `STUB.EMOJI_TABLES`
- `STUB.FONTATIONS`
- `STUB.COLR_V1`
- `STUB.LIBERATION_FM`
- font fixture gaps tied to delivered font bundles

### Dependency-gated: codecs / media / encoded formats

Do not replace with short-lived substitutes while codec delivery is in
progress.

- `STUB.FFMPEG`
- `STUB.WEBP_LOSSY`
- animated image/player gaps that depend on delivered frame decode
- compressed texture decode/upload if included in codec delivery
- YUV/YUVA image construction where the codec/GPU-format boundary is not
  settled

### WGSL / runtime-effect gated

Keep SkSL compiler work out of scope. Use the incoming shader parser to
feed typed WGSL/Kotlin effect registrations.

- `STUB.SKSL`
- `STUB.SKSL_ES3_GPU`
- `STUB.CLIP_SUPER_RRECT`
- runtime shader child/effect tags such as `STUB.RUNTIME_SHADER_CHILD`,
  `STUB.NORMAL_MAP_SHADER`, `STUB.LIT_SHADER_LINEAR`,
  `STUB.COLOR_CUBE_CF`

### Intractable Ganesh / Graphite

Do not treat these as raster backlog unless a WebGPU equivalent is
explicitly designed.

- `INTRACTABLE.GPU_ONLY`
- `STUB.GPU_GANESH_BLUR_UTILS`
- `STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET`
- `STUB.GR_FRAGMENT_PROCESSOR`
- `STUB.GANESH_GPU`
- `STUB.GANESH_BEZIER_EFFECT`
- `STUB.GRAPHITE`
- `STUB.GPU_BIG_RRECT_AA_EFFECT`

### Actionable implementation candidates

These are the remaining generated rows in `bucket=implementation` after the
follow-up PRs landed:

- `dftext_blob_persp`
- `drawatlas`
- `gradients`
- `mesh`
- `recordopts`
- `vertices`

Best candidates while fonts/codecs/WGSL are landing:

- `vertices`: `VerticesBatchingGM` is already ported; isolate the remaining
  raster `VerticesGM` behavior before touching WebGPU wrappers.
- `recordopts`: the stale `STUB.XYZ` marker is gone; the remaining blocker is
  the measured saveLayer / detector color-filter fold delta.
- `gradients`: RGB `SkGradient` API surface is present; remaining work is the
  perceptual/hue/premul interpolation sampler.

Deferred until dependency delivery or a larger API slice:

- `drawatlas` / `STUB.RSXBLOB`: text/glyph transform work; revisit after font
  delivery.
- `dftext_blob_persp` / `STUB.DF_TEXT_RASTER`: text raster fidelity work;
  revisit after font delivery.
- `mesh` / `STUB.MESH`: actionable but broad API work; start with the
  `custommesh` slice once `SkMesh`, `SkMeshSpecification`, and
  `SkCanvas.drawMesh` are introduced.

Rows in `bucket=partial-coverage` are not immediate blockers. They are
multi-GM upstream files where the matcher sees mixed coverage but no
explicit stub or dependency tag:

- `circulararcs`
- `cubicpaths`
- `encode`
- `gammatext`
- `gradient_dirty_laundry`
- `gradtext`
- `hugepath`
- `lattice`
- `nonclosedpaths`
- `orientation`
- `overstroke`
- `pathfill`
- `polygons`
- `quadpaths`
- `strokefill`
- `strokes`
- `trickycubicstrokes`

## Gaps in this rebaseline

This snapshot is useful, but not complete enough to replace a generated
backlog by itself.

Missing automation:

- A more robust GM-name mapper from upstream `.cpp` to local `*GM.kt`
  and test classes. The current script uses the existing `gm-status.sh`
  matcher plus filename-based test heuristics.
- A parser that extracts full multi-line `@Disabled(...)` reasons.
- A normalized tag taxonomy that merges spelling variants such as
  `STUB.ASYNC_RESCALE_READ` and `STUB.ASYNC_RESCALE_AND_READ`.
- A GPU report equivalent to the CPU markdown similarity reports.
- A public API delta against upstream headers.
- Manual verification of `bucket=partial-coverage` rows. These are
  mostly multi-GM `.cpp` files where the matcher sees mixed status but
  no explicit `STUB.*` tag.

## Next rebaseline tasks

1. Verify the 17 `partial-coverage` rows and decide whether they need
   aliases, explicit tags, or no backlog item.
2. Normalize tag ownership in source comments and disabled reasons.
3. Improve multi-line `@Disabled(...)` extraction.
4. Add a GPU markdown report equivalent to the CPU similarity reports.
5. Run the relevant test tasks after the static report exists, then attach
   pass/fail/skip counts to this snapshot.
