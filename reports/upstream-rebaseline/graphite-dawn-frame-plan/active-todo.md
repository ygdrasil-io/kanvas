# Graphite/Dawn Frame Plan Active TODO

Last updated: 2026-08-14

This is the active, branch-specific backlog for
`codex/graphite-dawn-frame-plan-design`. Items are processed strictly in the
order below, with at most one `in_progress`.

Out of scope: dependency-verification metadata, checksum maintenance, and
reproducible-build policy.

Status values: `pending`, `in_progress`, `blocked`, `completed`.

## Ordered work

### FP-01 — Effective WebGPU uniform alignment on Windows

Status: `completed`

Goal: ensure every planned dynamic uniform offset satisfies the effective
limits of the created WebGPU device.

Resolution evidence:

- `61224a8a9 fix(gpu): validate uniform alignment limits` enforces positive
  power-of-two `GPULimits` uniform alignment;
- `b323c80a2 fix(gpu): use created device limits` sources execution limits
  from `GPUDevice.limits`;
- the native capability smoke reports `source=device.limits`, effective uniform
  alignment `256`; the three-packet analytic frame uses offsets `[0, 256, 512]`,
  and the two-packet Kanvas inventory frame completes without a native
  alignment panic;
- `.\gradlew.bat :gpu-renderer:test :gpu-renderer-scenes:test :kanvas:test
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks`
  reaches the Gradle summary with no `min_uniform_buffer_offset_alignment`;
  remaining red evidence is independently assigned to FP-03 and FP-10.

Acceptance:

- focused limit normalization tests pass;
- the native capability smoke reports the effective alignment used by frame
  planning;
- prepared multi-packet native tests complete without an alignment validation
  panic;
- affected `gpu-renderer`, `gpu-renderer-scenes`, and `kanvas` tests are rerun
  and their remaining independent failures are recorded under FP-03.

### FP-02 — Integrate current origin/master

Status: `completed`

Goal: integrate the four upstream commits currently missing from the branch
without losing branch or user changes.

Acceptance:

- branch contains the current `origin/master`;
- conflicts are resolved against the current renderer targets;
- compile and focused frame-plan suites reach their normal validation stage;
- the resulting integration commit is recorded here.

Resolution evidence:

- `41e05b682` is the Task 1 two-parent merge commit (`efe9b4470` and
  `958687305`) that integrates `origin/master@958687305`, including upstream
  commits `bbd07f790`, `ce46015e2`, `7f66913aa`, and `958687305`;
- the three semantic overlaps auto-merged as a semantic union, preserving the
  prepared WebGPU frame route while adding the JPEG-LS/JPEG 2000/JPEG XL
  projects and faithful lattice sampling/fixed-color behavior;
- Temurin 25 `projects` and `assemble` compile every included project locally
  with dependency verification disabled per scope;
- focused codec and font validation reached only the 14 Windows line-ending
  portability failures assigned below to FP-03; the 16-GM, lattice,
  device-limit, and prepared-frame selections passed.

### FP-03 — Windows test portability

Status: `completed`

Goal: remove host-only failures from Gradle subprocess tests, textual goldens,
diagnostic assertions, and platform-sensitive pixel assertions.

Current evidence:

- suite:
  `org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest`;
  test `direct core materializer integrity gate performs no canonical hash work`;
  first message `uniform80 materialization must not create a second payload
  snapshot per draw`; owner=`lf-normalization`; LF-only source delimiter misses
  the CRLF checkout delimiter and extends the inspected slice into later code.
- suite:
  `org.graphiks.kanvas.gpu.renderer.scenes.GPURendererScenesModuleBoundaryTest`;
  test `check task graph does not include opt in render tasks`; first message
  `Cannot run program "./gradlew"`; owner=`platform-wrapper`.
- suite:
  `org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest`;
  test `solid frame sampler measures completion only and performs one final
  readback`; first message `Expected the collection to contain the element`;
  owner=`diagnostic-assertion`; the assertion searches for one whole collection
  element instead of comparing semantic diagnostic fields.
- suite:
  `org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest`;
  test `color matrix uses one prepared submit and matches the independent
  row-major reference`; first message `Expected the collection to contain the
  element`; owner=`pixel-policy`.
- suite:
  `org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest`;
  test `gaussian blur photo uses three prepared passes in one submit`; first
  message `Expected the collection to contain the element`; owner=`pixel-policy`.
- suite:
  `org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest`;
  test `registered runtime effect uses the generic prepared submit without
  source in the frame plan`; first message `Expected the collection to contain
  the element`; owner=`pixel-policy`.

Additional portability evidence:

- `JpegXlModularDecodeTest`: raw-PGM SHA-256 fixture
  `flower-510x532-8bit-lossless.pgm` differs because `core.autocrlf=true`
  converts its LF bytes to CRLF; owner=`line-endings`.
- `Jpeg2000DocumentTest`: raw-PGM SHA-256 fixture `source.pgm` differs because
  `core.autocrlf=true` converts its LF bytes to CRLF; owner=`line-endings`.
- `Jpeg2000DocumentTest`: raw-PGM SHA-256 fixture
  `source-two-codeblocks-96x17.pgm` differs because `core.autocrlf=true`
  converts its LF bytes to CRLF; owner=`line-endings`.
- `Jpeg2000DocumentTest`: raw-PGM SHA-256 fixture `source-ndecomp2-8x8.pgm`
  differs because `core.autocrlf=true` converts its LF bytes to CRLF;
  owner=`line-endings`.
- `Jpeg2000DocumentTest`: raw-PGM SHA-256 fixture
  `source-ndecomp2-5x5-random.pgm` differs because `core.autocrlf=true`
  converts its LF bytes to CRLF; owner=`line-endings`.
- `FontScalerSurfaceTest.cff2VariationTraceGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.cffSubroutineTraceGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.truetypeMalformedGlyfIsolationGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.cffCharStringTraceGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.truetypeCompositeGlyphReadinessGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.truetypeGvarIupGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.truetypeVerticalMetricsGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.cffIndexDictGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.
- `FontScalerSurfaceTest.cffScalerPathOutputGoldenMatchesGeneratedEvidence`:
  checked-in golden JSON is CRLF while generated evidence is LF;
  owner=`line-endings`.

Resolution evidence:

- `4ad100504 test(codec): preserve raw PGM fixture bytes` adds narrow `-text`
  contracts for the JPEG 2000 and JPEG XL PGM fixture directories; all five
  affected resources remain `attr/-text w/lf`, retain their original SHA-256
  assertions, and pass without decoder or loader changes;
- `78606a8f8 test(font): normalize golden line endings` canonicalizes only
  CRLF/CR line terminators in the nine semantic JSON comparisons; the complete
  `FontScalerSurfaceTest` passes all 105 tests without changing any golden or
  generator;
- `776b986d2 test(gpu): make source inspection EOL independent` replaces the
  LF-only source slice with an anchored, brace-balanced test-local extractor;
  `GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest` passes 55/55;
- `7422bf235 test(scenes): use the native Gradle wrapper` resolves and launches
  the repository wrapper by OS without a shell; the complete module-boundary
  suite passes 5/5, including the dry-run task-graph assertion;
- `a177df27b test(scenes): enforce portable diagnostic policies` compares the
  prepared-cache facts `solid=1/3` and `registered=0/0` semantically and
  requires full `withinOneLsb=64000/64000` coverage with
  `maxChannelDelta <= 1`; the complete offscreen suite passes 28/28;
- `9a2d8edca test(gpu): allow one-LSB UNORM quantization` applies the same
  strict one-LSB limit only to half-UNORM native smoke assertions while
  keeping all unrelated pixels exact; the three affected methods pass 3/3,
  the complete core smoke class passes 8/8, and the complete solid-rectangle
  smoke class passes 17/17 in isolated single-worker runs;
- `.\gradlew.bat :codec:jpeg2000:test :codec:jpegxl:test :font:scaler:test
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks`
  completed successfully under Temurin 25: JPEG 2000 98 tests with 0 failures
  and 8 skipped, JPEG XL 22 with 0 failures and 1 skipped, and font scaler 109
  with 0 failures;
- `.\gradlew.bat :gpu-renderer:test :gpu-renderer-scenes:test
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks`
  completed the scenes module 274/274 and all then-existing FP-03 target suites
  green, but the GPU worker ended non-green after two half-UNORM assertions
  and an independent native NVIDIA/wgpu access violation; the assertions are
  resolved by `9a2d8edca`, and the request-device lifetime/recreation crash is
  explicitly assigned to FP-10 below rather than represented as a green full
  GPU aggregate;
- zero FP-03 portability assertion remains failing; no renderer, WGSL, codec,
  font-scaler, CPU-reference, or normal Gradle task-graph production behavior
  changed.

Acceptance:

- Gradle subprocess tests use the platform wrapper;
- font golden tests compare semantic LF-normalized content;
- diagnostic assertions compare semantic facts independently, not as one
  complete collection string;
- exact and one-LSB pixel policies are explicit and independently tested;
- the previously failing Windows suites pass or expose only failures assigned
  to later functional items.

### FP-04 — Prepared image route

Status: `completed`

Goal: migrate image, image-nine, lattice, and atlas Surface operations to the
common prepared frame route, including texture and sampler ownership.

Current state:

- Tasks 1-4 and the first implementation of Task 5 are present through
  `a037a7c463d84d8ceb99eabeb0d01d426769d8b3`;
- the 2026-07-26 audit reran 289 focused tests with zero failure, but that
  historical evidence did not by itself accept Task 5 because the fake native
  seams did not validate the complete binding-layout and sharing contract;
- Task 5 consolidation is now completed and independently reviewed:
  - one reflected ABI112/layout identity from builder to native bind group;
  - command-exact image-source mapping allowing repeated draws;
  - Task-4 texture/view/sampler/bind-group sharing reused by Task 5;
  - immutable cache device generation;
  - stable refusal codes propagated unchanged;
  - measured removal of uniform-only pipeline-key axes;
- the native sRGB source/sample/store contract matches an independent
  translucent oracle without a destination CPU snapshot/upload;
- the Task 5.4 repair now propagates an exact `rgba8unorm-srgb` scene target
  authority, keeps coverage-mask intermediates unorm, closes the two prepared
  scene format/interpretation pairs, declares only single-sample native sRGB
  attachment support, explicitly refuses sRGB x4 before encoder/cache/pool or
  native acquisition, preserves byte-exact canonical
  `EncodedPremulSrgb` readback without CPU conversion, and updates the
  straight-upload/sRGB-store diagnostic;
- the fresh Task 5.4 manifests cover 24 GPU suites (531/531) and 9 Kanvas
  suites (81/81), with zero failure or skip;
- bounded evidence is recorded in
  `fp-04-srgb-store.md` and the accepting consolidation review in
  `fp-04-task-5-review.md`;
- Task 5 is `completed` and accepted after the initial independent findings
  were repaired and the independent re-review returned no significant finding;
- Task 6 is `completed` in `983ce190c`:
  - one pure full-frame preflight validates the closed
    `{CorePrimitive, SampledImage}` mix before target borrow or native
    allocation;
  - one mixed-frame materializer owns the target, readback, late surface,
    resource ledger, ordered Core/Image operands, and final draft;
  - prepared-image texture uploads occur before their exact consumers, while
    pipeline caching remains session-owned and image operands remain
    frame-owned;
  - runtime close and generation replacement close the prepared-image session
    cache without returning stale handles;
  - independent cache, preflight, ownership, and end-to-end reviews reported
    no blocking finding;
  - the fresh complete `:gpu-renderer:test --rerun-tasks` run passed
    2,485/2,485 tests with zero failure or error;
- Tasks 7-9 completed direct DrawImage, image-nine, lattice, affine atlas,
  pixel/refusal evidence, builder/inventory wiring, and native product smoke;
- Task 10 atomically admits all four image families to the whole-frame
  prepared product route, makes every post-admission refusal terminal, removes
  the image legacy family/allowlist/diagnostic, and supports image-only native
  frames without a synthetic CorePrimitive draw;
- the fresh Task 10 focused manifests pass 100/100 GPU tests and 149/149
  Kanvas tests;
- the final module aggregates contain no image failure: the sole GPU failure
  is the pre-existing Task 9 package-boundary import baseline, and all 52
  Kanvas failures are DrawPath/DrawDRRect core baselines;
- final cutover evidence and bounded nonclaims are recorded in
  `fp-04-prepared-image-route.md`.

Acceptance:

- image operations no longer produce
  `legacy.surface.prepared.family.images`;
- sampling filters and required texture bindings are native and tested;
- image pixel and alpha-material evidence passes;
- `Images` is removed from the legacy allowlist.

### FP-05 — Prepared text route

Status: `completed`

Goal: migrate text and glyph Surface operations to the common prepared frame
route.

Resolution evidence:

- `ce0ae1f75a53b53689ef85d7b47dd0d7eedae987` performs the atomic product
  cutover: DrawText is prepared-or-terminal, `Text` is absent from the legacy
  family/allowlist, and post-admission refusal never calls the legacy port;
- A8 and COLRv0 use one immutable frame-local R8 upload authority with exact
  upload-before-sample ordering, while stroke uses the common prepared path
  authority and mask blur is applied before atlas packing;
- native A8, COLRv0, blur, mixed-frame, completion/recreate, ownership and
  one-LSB pixel evidence executed without skips on the Apple M2 Max adapter;
- the final serial module aggregate passed 6,439/6,439 tests with zero
  failure, error or skip;
- exact scope, counters, refusals, cold-frame measurements and nonclaims are
  recorded in `fp-05-prepared-text-route.md`.

Acceptance:

- text operations no longer produce `legacy.surface.prepared.family.text`;
- A8 and supported color glyph resources use prepared upload-before-sample
  ordering;
- text atlas and paint-alpha evidence passes;
- `Text` is removed from the legacy allowlist.

### FP-06 — Prepared vertices and mesh route

Status: `completed`

Goal: migrate vertices and mesh Surface operations to the common prepared
frame route.

Resolution evidence:

- `2ecd951ecb9285521e1c04f134aec6d58e85318c` performs the atomic product
  cutover: DrawVertices and DrawMesh are prepared-or-terminal, `Vertices` is
  absent from the legacy family/allowlist, and post-admission refusal never
  calls the legacy port;
- topology canonicalization (Triangles/TriangleStrip native, fan to triangle
  list), premultiplied RGBA8 colors, UVs, uint16 indices (uint32
  capability-gated, native evidence pending), solid and gradient materials,
  and registered MeshProgram effects use one immutable frame-local
  vertex/index upload authority with exact upload-before-draw ordering;
- native vertices, mesh-program, mixed-frame, batching-telemetry, ownership
  and one-LSB pixel evidence executed without skips on the Apple M2 Max
  adapter; the seven-case end-to-end refusal matrix is terminal and
  allocation-free;
- the final serial module aggregate passed :kanvas:test 3,210/3,210 and
  :gpu-renderer:test 3,182 tests with only the historical package-boundary
  baseline (exactly 20 cycles, 0 rule violations);
- exact scope, counters, refusals, upload graph, ownership and nonclaims are
  recorded in `fp-06-prepared-vertices-mesh-route.md`.

Acceptance:

- vertices and mesh operations no longer produce
  `legacy.surface.prepared.family.vertices`;
- vertex/index resources and blend authority are prepared and tested;
- vertices pixel evidence passes;
- `Vertices` is removed from the legacy allowlist.

### FP-07 — Prepared composite route

Status: `completed`

Goal: migrate layers, filters, masks, pictures, and backdrop composites to the
common prepared frame route.

Resolution evidence:

- `aebfd94fe` is the accepting head (42 commits over the FP-06 tip
  `40a873560`); route closure recorded in
  `fp-07-composite-route.md` (route diagnostics, execution architecture,
  refusal matrix, boundary statements, fallback policy, deferred items) with
  the per-task trial record in `fp-07-composite-route-evidence.md`;
- layer-target execution (Graphite/Dawn model): one render pass per layer
  target plus the root pass in a single encoder — `compositeCommands`
  (`PrepareLayerTarget`/`RenderLayerChildren`/`CompositeLayer`) carried
  through `GPUPreparedWindowOutput.attachToFrame`, planned into frame steps,
  admitted as declared layer targets by the session validator, materialized
  as RGBA8 layer textures with child renders and a textured-quad composite
  draw (real `GPUBlendPlan` + alpha + clip, premultiplied layer sampling);
  flat child render elided when composite commands are scheduled;
- bounded saveLayer/mask-blur pixel evidence executes on the Apple M2 Max
  adapter (CPU vs GPU exact tolerances); the final serial aggregate passed
  `:kanvas:test` 3,230/3,230 and `:gpu-renderer:test` 3,256 tests with only
  the historical package-boundary baseline (exactly 20 cycles, 0 rule
  violations — restored by the Task 18 boundary audit);
- the fallback policy is explicit terminal refusal, never silent: unsupported
  topologies refuse with stable codes (`bounds_unbounded`, `composite.clip`,
  `composite.operation`, `layer-nesting`, `layer-composite-blend`,
  `mixed-composite-topology`, `nested_vertices`), and the router's terminal
  family is the loud-refusal safety net.

Acceptance:

- composite operations no longer produce
  `legacy.surface.prepared.family.composites` — zero occurrences in
  `kanvas/src` / `gpu-renderer/src`; ✓
- child layer content is sampled by the composite pass — bounded
  saveLayer/translated/scaled/partially-offscreen/empty-layer and mask-blur
  frames render with exact pixels; non-core children and unsupported
  topologies are documented terminal refusals (matrix in the closure report);
  ✓
- saveLayer, image-filter, mask-blur, picture, and backdrop evidence passes —
  saveLayer and mask-blur render; image-filter/picture/backdrop shapes that
  the route cannot cover refuse loudly with documented codes (no silent
  drop); ✓
- `Composites` is removed from the legacy allowlist — the legacy adapter has
  no display family and `allowedFamilies = emptySet()`. ✓

Known deferred items (tracked in the closure report §8): nested saveLayers,
non-SRC_OVER blend materialization, tight-bounds layer transforms, layer
texture frame-pool lease, non-core children inside layer scopes.

### FP-08 — Retire immediate and CPU continuation paths

Status: `completed` (reduced scope; full legacy retirement deferred to FP-09)

Goal: remove the superseded high-level immediate renderer, CPU destination
snapshot/upload paths, and duplicate route authorities.

Resolution evidence (`fp-08-retire-immediate-cpu-paths-evidence.md`):

- `GPULegacyImmediatePathAdapter` (with `LegacyDisplayOpFamily` and
  `GPULegacyImmediatePathDump`) and all `legacyDump` plumbing are deleted;
  production searches return nothing and `GPUPreparedSurfaceLegacyAbsenceTest`
  pins the retired tokens out of `surface/gpu` production sources;
- BGRA8 renders natively in the prepared route (readback `[0,0,255,255]` in
  `BGRA8Unorm` memory layout, route `prepared.surface.direct`, no CPU swizzle)
  per the Graphite/Dawn model;
- destination continuation remains GPU-owned (Graphite/Dawn
  `kTextureCopy`/`kFramebufferFetch`; CPU readback is `readPixels` only);
- the runtime-capabilities refusal is renamed to a non-legacy terminal code;
- guard suites green: `GPUPreparedSurfaceProductRouterTest`,
  `GPUPreparedCompositeCaptureSemanticTest`,
  `GPUPreparedCompositeFrameRouteIntegrationTest`, `GPUAllApiBlendSurfaceTest`,
  `GPUPreparedSurfaceLegacyAbsenceTest`; `nested_vertices` pinned; full run
  `:kanvas:test` 3,230/3,230 green and `:gpu-renderer:test` 3,257 with only
  the two documented pre-existing failures (package boundary, stencil smoke);
- the original route-collapse Tasks 4–5 were executed and reverted (~636 GPU
  cases regressed to terminal refusals with 5 uncovered refusal codes —
  destination-read 630, core-blend 330, hairline 168, mixed-layouts 92,
  analytic-clip 52); `renderViaGpuLegacy` stays as the fallback for the
  not-yet-covered families until FP-09;
- `GPURendererPackageBoundaryTest` remains in its documented pre-existing
  failing state (exactly 20 cycle violations, 0 rule violations).

### FP-09 — Retire the legacy immediate renderer (deferred from FP-08)

Status: `completed`

Resolution evidence (`fp-09-retire-legacy-immediate-renderer-evidence.md`):
- route authorities collapse to Prepared/Terminal/Refused/NoOp — `BeforePreparedEntryRefused`
  → always `Terminal`; the `Legacy` eligibility/route/decision variants and the legacy
  `GPUPreparedSurfaceLegacyPort` are deleted;
- prepared coverage added: non-SrcOver core-primitive blends (CLEAR/SRC/… fixed-function +
  shader-no-dst) and destination-read blends (`ShaderBlendWithDstRead` + GPU-owned
  `TextureCopy` snapshots + `GPUBlendFormulaLibrary` formulas) on core primitives and layer
  composites — the FP-08 evidence §3 codes `unsupported.native-core-primitive.blend` (330)
  and `unsupported.destination_read.required` (630) no longer fire for covered shapes;
  the coverage required the Graphite-faithful multi-pipeline per-pass materialization
  (Tasks 3b/3c: DrawPass pipeline array, `BindGraphicsPipeline` mid-pass, dst copy before
  the consuming pass — C++ evidence in skia-main, plan amendment `f45d4fc6f`);
- stable terminal refusals replace the legacy render for hairline points (175),
  mixed uniform layouts (202), analytic-clip non-direct geometry (2), and the blend
  residuals (multi-render-dst-copy 60, analytic-shape-multi-key 2, dst-read-formula 2,
  path-destination-read 60) — documented behavior change (pixels → loud refusal),
  tracked as bounded FP-11 gaps;
- `renderViaGpuLegacy` and the legacy-only machinery are deleted (GPUClipExecution.kt,
  LayerScissorOffscreenTarget, CPU text-atlas builders, legacy mask-lease machinery);
  the FP-06 `nested_vertices` guards stay test-pinned;
- `GPUAllApiBlendSurfaceTest`/`GPUClipCoverageSurfaceTest` re-pointed with evidence;
  the Task 10 full run exposed and re-pointed 18 further stale legacy pins in
  `GPUClipAdvancedBlendSurfaceTest`/`GPUMaskBlurSurfaceTest`/`GPUPathClipRegressionTest`
  (identical failures at pristine HEAD; all 21 tests green at the FP-08 tip, proving
  pre-FP-09 legacy rendering);
- full run: `:kanvas:test` 3,210/3,210 green and `:gpu-renderer:test` 3,273 with only
  the two documented pre-existing failures (package boundary — exactly 20 cycle
  violations, 0 rule violations, unchanged; stencil smoke — reproduces at base SHA);
  `GPUPreparedSurfaceLegacyAbsenceTest` pins all 16 retired tokens.

### FP-10 — Reusable prepared Surface session

Status: `completed`

Goal: reuse backend, target, invariant pipelines, and frame-local pools across
compatible Surface frames.

Resolution evidence (`fp-10-reusable-prepared-surface-session-evidence.md`):
- the backend factory is a synchronized state machine: create/dispose mutual exclusion,
  explicit per-dispose device-generation stamping, idempotent dispose that waits for
  registered prepared-session children (the existing `GPUPreparedSceneChildRegistry`
  close-wait) before releasing the shared device — the `EXCEPTION_ACCESS_VIOLATION`
  lifetime/recreation failure class (Queue.writeBuffer + materializeFullscreenUniformSlab
  after `GPUBackendRuntimeFactory.dispose()` churn) is closed by construction;
- the process-wide executor caches one prepared scene session keyed by
  (deviceGeneration, size, format, interpretation): compatible frames reuse the target,
  the invariant pipeline caches, and the frame-local pools (creation/reuse counters
  surfaced in the executor evidence), and completion-only + readback outputs share the
  same session boundary;
- generation/size/format/owner/close transitions are deterministic — each closes exactly
  one old session and creates exactly one new one, pinned by a transition matrix and the
  `GPUPreparedSurfaceLifetimeStressTest` (session reuse, output-sharing, churn probe);
- full run: `:kanvas:test`/`:gpu-renderer:test` green except the two documented
  pre-existing failures (package boundary, stencil smoke); the `failed.surface.prepared.session-close`
  flake remains documented environmental (FP-09 evidence §17).

FP-10 transfer (retained-session-exposed pre-existing gap, per evidence §11):
- mask-blur leading-composite retained-target ordering —
  `GPUTopLevelMaskBlurFrameRecording` `firstCompositeClears = sceneRenders.isEmpty()`
  does not account for a mixed frame whose first paint op is a mask blur (composite
  `loadOp="load"` samples the retained previous-frame pixels; pre-FP-10 loaded
  undefined fresh-target content); correct condition is "no scene clear render
  ordered BEFORE the composite"; no test covers the leading-blur-mixed shape.
  — **CLOSED by FP-11 Task 2** (commits `29949f297` + `f0b95fb4b`; pins at
  `GPUMaskBlurSurfaceTest.kt:362`, `:388`; evidence `fp-11-close-bounded-native-rendering-gaps-evidence.md` §6).

### FP-11 — Close bounded native-rendering gaps

Status: `completed`

Goal: address the native gaps explicitly retained by the completed migrations,
including required stroke, coverage, sampling, filter, and runtime-effect
cases.

Resolution evidence (`fp-11-close-bounded-native-rendering-gaps-evidence.md`):
- covered with CPU/reference + native GPU evidence: mask-blur leading-composite
  retained-target ordering (per-chain composite clear on retained sessions), exact
  hairline point lowering (175), multi-render dst-copy direct lane (60),
  multi-uniform-layout direct passes (uniform80 split; analytic-clip 64/160
  split reclassified to B — see evidence §4), and analytic rect clips on the
  top-level mask blur composite;
- justified stable terminal refusals re-documented: analytic clips over non-direct
  geometry (2 at FP-09; 4 at closure HEAD after the Task 3 hairline re-route),
  dst-read formula on mapped routes (2), analytic-shape multi-key dst-read (2),
  complex-clip blur (`core_primitive_clip_producer_authority`),
  path destination-read (60, reclassified: path-stencil dst-read requires a
  stencil-continuation feature — see evidence §3);
- full run green except the two documented pre-existing failures (package
  boundary — exactly 20 cycle violations, 0 rule violations, unchanged; stencil
  smoke — reproduces at base SHA); the `failed.surface.prepared.session-close`
  flake remains documented environmental (not observed in the closure full
  run); the FP-10 retained-session contract is preserved
  (`GPUPreparedSurfaceLifetimeStressTest` 6/6).

FP-13+ transfers (residual-refusal tracking note — bounded future work, not a new
roadmap entry; FP-13 closed 104 rows and re-pointed 227 blend-matrix rows + 10 clip
pins to stable codes, see the FP-13 entry below):
- analytic-clip blend programs (93 rows,
  `unsupported.native-core-primitive.session-cache-pipeline`) — non-SRC_OVER
  fixed-function and artistic modes on the analytic-clip uniform64 lane; needs an
  `AnalyticClipDstRead` program + geometric projection (NEW feature, parallels
  FP-13 Tasks 3/4);
- combined shape+clip shader (29 rows,
  `unsupported.recording.core_primitive_analytic_shape_clip`) — analytic-shape
  uniform80 under an analytic clip (NEW feature);
- analytic-clip × stencil-cover (58 rows,
  `invalid.preflight.core_primitive_path_stencil`) — path-stencil continuation
  under an analytic clip; the FP-13 Task 8 stencil-continuation does not yet
  compose with the analytic-clip authority (NEW feature);
- four-render / dst-slab direct-resource seal (47 rows,
  `invalid.preflight.core_primitive_direct_geometry_resources`) — 2 DrawRRect DST +
  30 DrawPoint + 15 DrawPoint dst-copy ALPHA_MASK (fp-11 §5 roots);
- 489 SkiaGmRunner GM refusals — untouched by FP-13 (no GM row closed);
- chantier B (missing-reference infra + committed gms.json) and chantier F
  (real-adapter re-measurement) — tracked outside FP-13.

### FP-12 — Current visual and performance evidence

Status: `completed`

Goal: regenerate the current GM evidence and measure the final prepared
candidate after legacy retirement.

Acceptance:

- current render images and similarity scores are regenerated — done (23
  generated-render PNGs + 17 score lines refreshed to the post-retirement
  prepared renderer; dashboard regenerated, see `fp-12-current-visual-and-
  performance-evidence.md` §1);
- benchmark inputs, raw samples, hashes, p50/p95 results, and verdicts are
  recorded — done (per-family benchmark, `solid-card-stack` frame samples,
  pipeline-cache ledger, SHA-256 hashes, p50/p95, and frame-gate verdicts,
  same evidence doc §2);
- headless validation stays independent from opt-in Kadre execution — done
  (Kadre submodule uninitialized; all lanes headless WebGPU offscreen);
- measured lanes and explicit non-claims are documented — done (§2, §5 of the
  evidence doc; llvmpipe software-GPU measurements with no cross-machine or
  release-blocking claim).

Two pre-existing `:gpu-renderer-scenes` defects were fixed because the module
owns the FP-12 benchmark lanes and had not compiled since `06844bc08`
(`SaveLayerExecutor` rename): (1) the deleted-class import in
`RectOnlyOffscreenRenderer.kt` was migrated to inline M25-pinned diagnostics;
(2) the COLRv0 offscreen session request now matches the recorded
`RGBA8UnormSrgb` scene target (fixes `unsupported.prepared-scene-session.
target-incompatible`). One pre-existing latent `colr-v0-color-glyph` scene
oracle divergence (opaque vs. transparent background, from the July 29-30
clear-semantics change) is documented and tracked in the FP-12+ transfer list
below, not FP-12-introduced. The FP-11 residual-refusal transfer list is
unchanged.

### FP-13 — Close bounded native-rendering gaps

Status: `completed`

Goal: close the M86 Wave-2 residual-refusal rows retained by FP-12 (the
analytic-shape dst-read formula, multi-key dst-read, complex-clip blur, the
analytic-clip 64/160 split, analytic clips over non-direct geometry, and the
path destination-read stencil-continuation), plus the two small harness /
test-hygiene items (`colr-v0` scenes oracle, `PipelineTypesTest`).

Resolution evidence
(`fp-13-close-bounded-native-rendering-gaps-evidence.md` §9):

- **104 rows closed** against the 341-row Task 0 denominator: 32 dst-read
  formula (30 frame-global DrawRRect + 2 clip:coverage DARKEN/COLOR_DODGE),
  2 analytic-shape multi-key CLEAR/SRC/DST_IN (+ 4 latent AA modes pinned),
  2 complex-clip blur pins, 4 SRC_OVER analytic-clip blend rows, 4
  analytic-clip non-direct DST rows, 60 path destination-read rows
  (stencil-continuation, CPU-oracle exact);
- **227 blend-matrix rows re-pointed** to stable codes (93
  session-cache-pipeline, 29 analytic_shape_clip, 58 path_stencil, 47
  direct_geometry_resources) and **10 clip pins** re-pointed (Coverage 1 →
  analytic_shape_clip, Advanced 8 → analytic_shape_clip, PathClip 1 →
  path_stencil) — 104 + 227 + 10 = 341 ✓;
- `colr-v0-color-glyph` scenes oracle fixed (harness-only, byte-exact
  4096/4096); `PipelineTypesTest` made fork-order independent (test hygiene)
  with the `fn main() {}` acceptance gap tracked upstream at
  ygdrasil-io/wgsl4k#15;
- full run green except the two documented baselines
  (`GPURendererPackageBoundaryTest` exactly 20 cycle violations / 0 rule
  violations; `GPUPreparedSurfaceImagePixelTest` UNORM 1-LSB on llvmpipe);
  `:gpu-renderer-scenes:test` 274/274; `GPUAllApiBlendSurfaceTest` 1864/1864;
  guards green; no `session-close` / `GPUOwnedNativeCloseIncompleteException`;
- dashboard gate unchanged: Total 615 / Pass 540 / Fail 6 / No score 30 — the
  same 6 below-threshold and 30 no-score sets as the Task 0 snapshot (0 new
  `fail`, 0 `tracked-gap`); no committed render/score drift (git status clean
  after regeneration).
