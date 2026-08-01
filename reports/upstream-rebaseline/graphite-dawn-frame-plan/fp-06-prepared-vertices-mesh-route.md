# FP-06 prepared vertices and mesh product-route evidence

Date: 2026-07-31

Verdict: **completed** — evidence below is recorded from the accepting runs;
the two independent Task 16 reviews and the active-roadmap update remain the
final gate per the plan (the roadmap row is updated only after clean review).

## Scope and accepting head

This report closes only FP-06. `DisplayOp.DrawVertices` and
`DisplayOp.DrawMesh` are now either executed by the common prepared WebGPU
frame route or refused terminally. They cannot return to the immediate, CPU,
or legacy vertices renderer after product admission.

The accepting implementation head is:

```text
2ecd951ecb9285521e1c04f134aec6d58e85318c
feat(surface): activate prepared vertices routing
```

The branch sequence from the FP-05 closure to the FP-06 accepting head is 40
commits: the four FP-05 closure commits (`5b774e076`, `ce0ae1f75`,
`0197a9e29`, `6f8a617b1`) followed by the 36 FP-06 Task 1-16 commits.

## Commit ledger (Task 1-16, oldest first)

All 36 FP-06 commits, grouped by task using the SDD ledger
(`.superpowers/sdd/2026-07-31-fp-06-prepared-vertices-mesh-route/progress.md`).
Where a commit sits positionally inside a task cluster but is not named on the
ledger's per-task line, it is grouped by its position and description and
flagged with `*`.

| Task | Commits (oldest first) | Bounded result |
|---|---|---|
| 1 | `56516eec2` feat(renderer): define prepared vertices artifacts | canonical refusal authority and immutable upload artifact |
| 2 | `8087ca63e` feat(renderer): pack prepared vertices exactly | canonical topology conversion and vertex/index packing |
| 3 | `6ae97f3d6` feat(surface): map registered mesh programs; `d74464dde` fix(surface): harden typed mesh program graphs; `97f802ce5` fix(surface): bound mesh child analysis depth | typed MeshProgram mapping |
| 4 | `f10a196cd` feat(renderer): compile registered material children; `4f40e0403` fix(renderer): compile executable runtime children; `43a31f929` fix(renderer): align runtime child identity and parity | registered runtime-effect children compiled once |
| 5 | `0207d8dbd` feat(surface): lower prepared vertices and meshes; `9c557f3b9` fix(surface): snapshot prepared vertices lowering facts; `901ec46db` fix(fp06): close prepared vertices lowering boundaries; `692b99f66` fix(fp06): harden prepared vertices boundaries; `9f3927a1b` fix(fp06): guard runtime effect resolution; `c94e495c4` test(fp06): exercise public position and budget refusals; `e0d8e258a` test(fp06): prove canonical refusal coverage; `2018c2ccc` test(fp06): cover public vertices product and bounds; `a2594633c` fix(fp06): prove mesh lowering boundaries; `9af80d378`* fix(fp06): expose exact WebGPU vertex formats | pure DrawVertices/DrawMesh lowerer |
| 6 | `992e32bb4` feat(surface): inventory prepared vertices per frame; `7311c33e8` fix(surface): integrate prepared vertices frame inventory; `205ca1f3f`* fix(surface): harden prepared vertices mapping contracts | frame-local vertices inventory and budgets |
| 7 | `19d3a10d3` feat(renderer): define prepared vertices semantics; `c7269d723` fix(renderer): close prepared vertices semantic authority; `995452432` fix(gpu): close prepared vertices semantic authority; `7c9429b3b`* test(gpu): prove inventory material snapshot isolation; `386a87551` test(gpu): rebaseline prepared material fixtures after f10a196cd an…; `e0d6c1c4d` fix(gpu): reject foreign prepared vertices authorities | closed vertices semantic payload |
| 8 | `ce4dbbce0` feat(renderer): assemble prepared vertices wgsl; `adff39df3` fix(renderer): keep prepared vertices shader within package boundary | parser-validated vertices WGSL and exact ABI |
| 9 | `e2239f773` feat(renderer): plan prepared vertices resources | vertex/index resource plans and heterogeneous task graph |
| 10 | `a604a8457` feat(renderer): preflight prepared vertices frames | full-frame native preflight |
| 11 | `839134c6a` feat(renderer): materialize prepared vertices natively | wgpu4k vertices materializer and close-once ownership |
| 12 | `feec8a779` perf(renderer): batch prepared vertices safely | compatible batching and telemetry |
| 13 | `d99808f58` test(surface): add prepared vertices pixel oracles | deterministic fixtures and independent CPU pixel oracle |
| 14 | `c2a32bb14` test(surface): prove prepared vertices natively | native pixels, MeshProgram, mixed-frame, and refusal smokes |
| 15 | `2ecd951ec` feat(surface): activate prepared vertices routing | atomic product route and legacy removal |
| 16 | this report (evidence only; no code head) | FP-06 evidence and independent review gate |

Every reviewed task head passed its spec-compliance and technical review with
no legitimate Critical/Important finding (C0/I0 after corrective rounds where
the ledger records them).

## Atomic product decision

`DrawVertices` and `DrawMesh` are prepared visual candidates. Once admitted:

- lowering, inventory, recording, preflight, materialization, execution and
  completion refusals are terminal;
- the exact diagnostic is retained and the legacy port is not invoked;
- `MeshProgram` mapping requires a registered prepared runtime-effect
  descriptor; unregistered programs refuse before any native work;
- wide-open, hard scissor, affine transform, premultiplied RGBA8 colors, UVs
  and the accepted fixed-function final blends retain their exact execution
  semantics;
- mixed Core/Image/Text/Vertices frames preserve recorded packet order.

`Vertices` was removed from `LegacyDisplayOpFamily`, its allowlist and its
legacy diagnostic. The remaining legacy family is exactly:

```text
Composites
```

The production search was empty:

```text
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.vertices|LegacyDisplayOpFamily\\.Vertices|gpu_textured_vertices" \
  kanvas/src/main gpu-renderer/src/main
```

## Accepted rendering matrix

| Area | Accepted FP-06 rows |
|---|---|
| Topology | `Triangles` and `TriangleStrip` native draws; `TriangleFan` canonicalized to a triangle list before packing (fan-expansion counters recorded); alternating strip winding canonicalized to the fixed-function left/top edge rule |
| Vertex layouts | position-only, position+color, position+UV, position+color+UV, at WGSL locations 0/1/2, packed into one interleaved immutable buffer with exact strides and 4-byte-aligned subranges |
| Colors | Premultiplied RGBA8 (`Unorm8x4`); the vertex stage interpolates the stored premultiplied bytes raw, the fragment decodes interpolated RGB sRGB→linear before shading (alpha linear), per the `rgba8unorm-srgb` LinearPremul attachment model pinned by the Task 13 oracle |
| UVs | Texcoord attributes become the material-local coordinate input; CPU oracle proves nearest and linear sampled-image math |
| Indices | `uint16` (native-verified); `uint32` implemented end-to-end but capability-gated — no device capability producer emits the `vertices.uint32_index` fact yet, so native uint32 evidence is pending (documented non-claim); out-of-range and overflow refuse; index buffers are 4-byte aligned per `COPY_BUFFER_ALIGNMENT` |
| Materials | Solid and position-local gradient materials through the shared prepared material compiler; registered `MeshProgram` runtime effects with exact uniform ABI (native `runtime.simple_rt` uniform smoke) |
| MeshProgram children | Registered descriptors are required; the registered `runtime.compose_cf` child slots validate exactly but no registered CPU+WGSL program exists, so the compiler refuses deterministically at compile time with `unsupported.material.runtime_effect.wgsl_not_available` (the compiler's ProgramUnavailable mapping); through the public mesh chain the refusal is mapped precisely to `unsupported.mesh.program_cpu_not_available` — an honest refusal, not fabricated pixels |
| Final blends | Fixed-function `SRC_OVER`, `SRC`, `SRC_IN`, `PLUS`; primitive-color blending (SRC_OVER-style) when vertex colors are present; destination-read blends refuse on the vertices product surface |
| Clips and transforms | Wide-open, hard scissor, and affine (translate/scale/skew) transforms; the left/top edge inclusion rule is hardware-faithful (Metal-probe-verified) and pinned by fixtures. Mask and analytic-intersection clip plans refuse by design at the lowerer with `unsupported.vertices.clip_coverage` before the semantic is built |

Sampled image paints are not an accepted product row: they refuse at lowering
with `unsupported.vertices.material`, and the native materializer/preflight
keep `unsupported.prepared-vertices.sampled-material` as the documented
sampled-material boundary (the image-UV CPU oracle keeps proving the math; the
GPU family is refused with zero submission side effects).

## Upload-before-draw graph and ownership

The frame graph has one immutable vertex/index upload authority. Exact
artifact identity deduplicates a buffer once per frame; per-artifact vertex
and index uploads precede their exact consuming draws (exact upload tokens in
the recorded task list), material uploads precede consuming draws, and the
heterogeneous one-submit frame proves the ordering end to end:

```text
Prepare target and frame-local resources
  -> Upload prepared-vertices vertex/index artifacts
  -> Render ordered CorePrimitive / SampledImage / TextA8 / Vertices / CorePrimitive packets
  -> Optional readback
  -> Queue completion closes frame-owned resources
```

The measured heterogeneous frame (`CorePrimitive -> SampledImage -> TextA8 ->
Vertices -> CorePrimitive`) uses:

```text
visualOperationCount=4 (recorded) encoders=1 commandBuffers=1 submits=1 readbacks=1
renderPasses=5 draws+drawIndexed>=2
activeNativePayloads=0 outputOwnedNativePayloads=0 quarantinedNativePayloads=0
retentionRegistrations=retentionCompletions retentionQuarantines=0 distinctRetentionTickets=1
```

Ownership is close-once: retention registrations equal completions, zero
quarantines, and the vertices upload is asserted to be planned before its
consuming render in the same task list.

## Pixel and color evidence

The independent Task 13 CPU oracle is:

```text
stored premultiplied sRGB-encoded bytes -> vertex stage interpolates RAW
-> fragment decodes interpolated RGB sRGB->linear (alpha linear)
-> material result x primitive colour -> linear blend -> sRGB attachment encode
(RGB only; alpha stores linear) -> 8-bit UNORM round-half-up
```

Measured CPU/GPU pixel deltas (native smokes vs the oracle; every row satisfies
`matchesWithinOneLsb`, maxChannelDelta <= 1; the `differing/compared` pair is
differing channels over compared channels):

| Smoke | differing/compared |
|---|---|
| unindexed triangle | 0/36 |
| triangle strip | 1-2/16 |
| canonicalized fan | 1-2/24 |
| indexed triangle (uint16) | 1/72 |
| color partial alpha | 0/16 |
| position-local gradient | 0/36 |
| mesh-program uniforms | 1/16 |
| affine transform | 0/100 |
| scissor clip | 0/16 |
| blends SRC_OVER / SRC / SRC_IN / PLUS | 0/16 each |

The heterogeneous product frame reports `verticesDelta=0` (maxChannelDelta 0)
for its vertices overlap region, and the task markers report
`prepared=true skipped=0` with one submit and one readback per smoke.

## Stable refusal matrix

Every refusal is terminal and allocation-free. Preflight-stage refusals
additionally assert zero native target borrow, zero allocation/write, and zero
submit before the refusal surfaces.

Lowering/material refusals (canonical authority,
`GPUPreparedVerticesRefusalCodes`):

```text
unsupported.vertices.topology
unsupported.vertices.position_count
unsupported.vertices.attribute_count
unsupported.vertices.non_finite
unsupported.vertices.index_out_of_range
unsupported.vertices.index_format
unsupported.vertices.attribute_layout
unsupported.vertices.transform
unsupported.vertices.color_conversion_unvalidated
unsupported.vertices.primitive_blender_unregistered
unsupported.vertices.material
unsupported.vertices.budget
unsupported.vertices.clip_coverage
```

MeshProgram refusals:

```text
unsupported.mesh.bounds
unsupported.mesh.program_unregistered
unsupported.mesh.program_cpu_not_available
unsupported.mesh.program_wgsl_not_available
unsupported.mesh.program_wgsl_validation
unsupported.mesh.program_abi
unsupported.mesh.program_child
unsupported.mesh.program_resource
unsupported.mesh.budget
```

Prepared-surface boundary and staleness codes:

```text
unsupported.prepared-vertices.sampled-material
unsupported.prepared-surface.vertices-budget
unsupported.prepared-surface.vertices-topology
unsupported.prepared-surface.vertices-index-format
invalid.prepared-surface.vertices-artifact
invalid.prepared-surface.vertices-bounds
invalid.prepared-surface.vertices-dependency
invalid.prepared-surface.vertices-hash
invalid.prepared-surface.vertices-identity
invalid.prepared-surface.vertices-material-abi
invalid.prepared-surface.vertices-semantic
invalid.prepared-surface.vertices-target
invalid.prepared-surface.vertices-transform
invalid.prepared-surface.vertices-upload-duplicate
invalid.prepared-surface.vertices-upload-layout
invalid.prepared-surface.vertices-upload-missing
invalid.prepared-surface.vertices-upload-order
invalid.prepared-surface.vertices-usage
stale.prepared-surface.vertices-generation
```

The `invalid.prepared-surface.vertices-*` and stale-generation guards are
defense-in-depth: a valid public request cannot reach them (the execution port
derives frame/encoder/target/device/capability generations from one backend
snapshot; recorded uploads are reproduced exactly by the preflight). Mask and
analytic-intersection clip plans refuse by design at the lowerer with
`unsupported.vertices.clip_coverage` (authority `GPUClipMapper`, stage
`clip`) before the semantic is built — a designed boundary that replaces the
former incidental refusal at the generic preflight identity check, so a
vertices draw with an AA-mask or analytic clip can never silently degrade to
an unclipped full-target draw. The seven-case end-to-end refusal matrix
(`GPUPreparedVerticesRefusalMatrixTest`) deliberately asserts the reachable
set through the full Surface chain: non-finite positions, unsupported position
count (topology), index out of range, unregistered mesh program, hostile
material shader depth, mask clip plan, and sampled image paint material —
each with the exact terminal code and `legacy=0 native=0`. Vertices nested
inside pictures refuse with `unsupported.picture.nested_vertices` (FP-07
boundary).

## Aggregate validation

The accepting runs (recorded in the SDD ledger for Tasks 14-15; the final
Task 16 Step 2 command is the same six-module serial aggregate):

```text
rtk proxy ./gradlew :font:core:test :font:glyph:test :font:gpu-api:test \
  :font:test :gpu-renderer:test :kanvas:test --no-parallel
```

- `:kanvas:test` 3,209/3,209 green.
- `:gpu-renderer:test` 3,182 tests with the single pre-existing baseline
  failure `GPURendererPackageBoundaryTest > gpu renderer production source
  satisfies package boundary rules` — exactly the 20 historical package
  cycles with 0 rule violations of every class; this failure existed before
  FP-06 and is unchanged (see Boundary below).
- Native smokes 11/11 on the Apple M2 Max host through `wgpu4k`; the
  heterogeneous product smoke and the seven-case refusal matrix also ran
  natively; no unavailable-adapter assumption converted native evidence into
  green (every capability report prints adapter, device generation, target,
  index-format facts and the skip reason).
- `rtk git diff --check` clean; the legacy production search above returns no
  match.

Native host/device facts:

```text
host = Apple M2 Max (Darwin arm64/macOS, wgpu4k native)
target = rgba8unorm-srgb (LinearPremul)
index formats = uint16 native-verified; uint32 capability-gated, native evidence pending
device generations observed = 61-74 across runs
per smoke = 1 submit + 1 readback
```

The vertices batching telemetry emits the closed 19-counter set
(`GPUPreparedVerticesBatchingCounter`): draw count, unique artifacts (deduped),
vertex bytes, index bytes, fan expansion, buffer creations, upload count,
upload bytes, packed subranges (4-byte aligned), pipeline creations/reuses,
layout creations/reuses, compatible batches, draw calls, draw-indexed calls,
encoder scopes, queue submits, and readbacks.

## Dependency findings

None opened. One real wgpu4k-validation finding (index buffers must be
4-byte aligned per `COPY_BUFFER_ALIGNMENT`) was fixed as a Kanvas bug: packed
vertex/index subranges are 4-byte aligned and byte counts are rounded up to
the 4-byte copy alignment with zero fill, so no wgpu4k issue was needed.
wgsl4k parsed and reflected every generated prepared-vertices module
correctly; no wgsl4k issue was opened.

## Known unrelated flakes

Documented, not FP-06 regressions:

```text
PipelineTypesTest > RuntimeEffect compile fails validation   (wgsl4k hook-order JVM flake)
GPUAllApiBlendSurfaceTest / SurfaceTest                       (session-close flakes, pass on rerun)
SurfaceTest.drawImage                                         (session-close flake)
```

## Boundary

The unique residual failure in the aggregate is the historical
`GPURendererPackageBoundaryTest` with exactly the same 20 package cycles and
0 rule violations, unchanged since before FP-06. No FP-06 change introduced a
package-boundary violation (Task 8 moved the shader to the artifacts package
and restored the test to exactly the 20 historical cycles).

## Explicit nonclaims

- FP-07 remains pending: vertices inside composites, pictures and saveLayer
  layers are not promoted (`unsupported.picture.nested_vertices`).
- FP-08 remains pending: the legacy adapter still exists for Composites.
- FP-09 remains pending: there is no persistent vertex/index residency —
  buffers remain frame-owned with close-once completion ownership.
- FP-10 remains pending: this report does not close every bounded native
  gap outside FP-06.
- FP-11 remains pending: no GM regeneration, similarity-score update, release
  performance verdict or Graphite performance-parity claim is made here.
- `uint32` index format: implemented end-to-end and unit-tested on fake
  devices only; capability-gated behind a `vertices.uint32_index` fact that
  no device capability producer emits yet, so native uint32 evidence is
  pending.
- Mask and analytic-intersection clip plans on the vertices product surface
  refuse by design at the lowerer with `unsupported.vertices.clip_coverage`;
  destination-read primitive/final blends refuse as documented boundaries.
- Image-UV vertices (sampled-material boundary) are refused on the product
  surface; only the independent CPU oracle covers their UV math.
- Registered MeshProgram children with no registered CPU+WGSL program
  (`runtime.compose_cf`) refuse at compile time
  (`unsupported.material.runtime_effect.wgsl_not_available` mapped to
  `unsupported.mesh.program_cpu_not_available` on the public mesh chain)
  until a program lands.
- Animation and `unsupported.image.animation` are unchanged.
- No Ganesh or Graphite backend, SkSL compiler/IR/VM, multi-backend
  hierarchy, CPU destination snapshot fallback or compatibility reupload was
  added.
