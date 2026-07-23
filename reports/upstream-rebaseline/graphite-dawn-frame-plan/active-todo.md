# Graphite/Dawn Frame Plan Active TODO

Last updated: 2026-07-23

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
  remaining red evidence is independently assigned to FP-03 and FP-09.

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

Status: `in_progress`

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

Acceptance:

- Gradle subprocess tests use the platform wrapper;
- font golden tests compare semantic LF-normalized content;
- diagnostic assertions compare semantic facts independently, not as one
  complete collection string;
- exact and one-LSB pixel policies are explicit and independently tested;
- the previously failing Windows suites pass or expose only failures assigned
  to later functional items.

### FP-04 — Prepared image route

Status: `pending`

Goal: migrate image, image-nine, lattice, and atlas Surface operations to the
common prepared frame route, including texture and sampler ownership.

Acceptance:

- image operations no longer produce
  `legacy.surface.prepared.family.images`;
- sampling filters and required texture bindings are native and tested;
- image pixel and alpha-material evidence passes;
- `Images` is removed from the legacy allowlist.

### FP-05 — Prepared text route

Status: `pending`

Goal: migrate text and glyph Surface operations to the common prepared frame
route.

Acceptance:

- text operations no longer produce `legacy.surface.prepared.family.text`;
- A8 and supported color glyph resources use prepared upload-before-sample
  ordering;
- text atlas and paint-alpha evidence passes;
- `Text` is removed from the legacy allowlist.

### FP-06 — Prepared vertices and mesh route

Status: `pending`

Goal: migrate vertices and mesh Surface operations to the common prepared
frame route.

Acceptance:

- vertices and mesh operations no longer produce
  `legacy.surface.prepared.family.vertices`;
- vertex/index resources and blend authority are prepared and tested;
- vertices pixel evidence passes;
- `Vertices` is removed from the legacy allowlist.

### FP-07 — Prepared composite route

Status: `pending`

Goal: migrate layers, filters, masks, pictures, and backdrop composites to the
common prepared frame route.

Acceptance:

- composite operations no longer produce
  `legacy.surface.prepared.family.composites`;
- child layer content is sampled by the composite pass;
- saveLayer, image-filter, mask-blur, picture, and backdrop evidence passes;
- `Composites` is removed from the legacy allowlist.

### FP-08 — Retire immediate and CPU continuation paths

Status: `pending`

Goal: remove the superseded high-level immediate renderer, CPU destination
snapshot/upload paths, and duplicate route authorities.

Acceptance:

- `GPULegacyImmediatePathAdapter` and its final consumers are deleted;
- no migrated family reaches an immediate high-level dispatch;
- destination continuation remains GPU-owned;
- production searches and regression tests prove the retired paths are absent.

### FP-09 — Reusable prepared Surface session

Status: `pending`

Goal: reuse backend, target, invariant pipelines, and frame-local pools across
compatible Surface frames.

Current evidence:

- full `:kanvas:test` reproduces an `EXCEPTION_ACCESS_VIOLATION` in
  `wgpu_native.dll` through `Queue.writeBuffer` and
  `WgpuRenderRecorder.materializeFullscreenUniformSlab`;
- class-only `GPUAllApiBlendSurfaceTest` passes all 1,858 tests; the ordered
  reproduction `.\gradlew.bat :kanvas:test --tests
  "org.graphiks.kanvas.surface.SurfaceTest" --tests
  "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest"
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks`
  passes `SurfaceTest` 10/10, then the blend worker crashes in
  `Queue.writeBuffer`;
- `SurfaceTest.@AfterEach` repeatedly calls the process-global
  `GPUBackendRuntimeFactory.dispose()`; teardown/recreation is a sufficient
  predecessor trigger;
- the FP-01 device-limit change is causally excluded: focused created-device
  alignment tests pass and none of the three crash dumps contains the former
  alignment validation panic;
- the future minimal TDD reproduction is repeated runtime dispose/recreate
  followed by fullscreen uniform slab writes in one JVM.

Acceptance:

- repeated frames do not reopen the backend or prepared session;
- generation, size, format, owner, and close transitions are deterministic;
- completion-only and readback outputs share the same session boundary;
- cache creation/reuse counters and lifetime tests pass.

### FP-10 — Close bounded native-rendering gaps

Status: `pending`

Goal: address the native gaps explicitly retained by the completed migrations,
including required stroke, coverage, sampling, filter, and runtime-effect
cases.

Acceptance:

- every accepted expansion has CPU/reference and native GPU evidence;
- unsupported cases retain stable typed refusals;
- no hidden fallback or unsupported Graphite/Ganesh/SkSL compiler path is
  introduced.

### FP-11 — Current visual and performance evidence

Status: `pending`

Goal: regenerate the current GM evidence and measure the final prepared
candidate after legacy retirement.

Acceptance:

- current render images and similarity scores are regenerated;
- benchmark inputs, raw samples, hashes, p50/p95 results, and verdicts are
  recorded;
- headless validation stays independent from opt-in Kadre execution;
- measured lanes and explicit non-claims are documented.
