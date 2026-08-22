# GPU Renderer Evidence Rebuild Design

Date: 2026-08-22  
Design decision: approved for implementation planning  
Implementation evidence: pending

## Summary

Replace `:gpu-renderer-scenes` with a new, deliberately small
`:integration-tests:gpu-evidence` module. The replacement is built from
scratch beside the frozen legacy module, proves a curated correctness set
through the production GPU renderer, and then removes the legacy module and
its active reports in one atomic cutover. All new GPU renderer evidence reports
live under the single `reports/gpu-renderer/evidence/` namespace; the current
contents scattered directly under `reports/gpu-renderer/` are legacy catch-all
material and are removed during cutover.

This is option C2: a shadow replacement followed by an atomic cutover. It is
not a refactor or a source migration. Existing scene intent and neutral assets
may inform the replacement, but no legacy renderer, command DSL, shader
assembly, route selector, catalog implementation, or report generator becomes
load-bearing in the new module.

## Context And Problem

`:gpu-renderer-scenes` contains approximately 20,000 lines of Kotlin across
main and test sources, 88 catalog entries, custom report generation, direct
WebGPU rendering helpers, prepared-frame routes, benchmarks, and historical PM
or windowed material. These responsibilities make it both an integration
harness and a second renderer.

The most important correctness failure is structural: scene expectations are
catalog metadata rather than an execution invariant. For example,
`custom-runtime-effect-unregistered-refusal` declares a stable refusal but the
offscreen runner renders it successfully. Its arbitrary WGSL source reaches a
custom assembly path without enforcing registered runtime-effect identity.
Tests validate the catalog and renderer separately, so they do not detect that
contradiction.

The module also has two execution centers:

1. typed prepared routes that eventually use `GPUTaskList` and
   `GPUFrameCoordinator`;
2. direct target encoding and custom WGSL logic in
   `RectOnlyOffscreenRenderer`.

The second path duplicates product behavior and can make unsupported features
look supported. Existing reports are incomplete as product evidence because
most scenes do not have the full GPU/reference/diff/statistics/route bundle.
Performance reports also mix adapter eligibility and derived telemetry with
product activation claims.

## Decision

Create `:integration-tests:gpu-evidence` as the only scene-level GPU renderer
correctness and promotion harness. Freeze `:gpu-renderer-scenes` while the new
module is built. Cut over only after the replacement's initial catalog and
evidence gates pass. At cutover:

- remove `include(":gpu-renderer-scenes")` from `settings.gradle.kts`;
- delete the legacy module and its tests;
- delete `reports/gpu-renderer-scenes/` as active evidence;
- delete every legacy child of `reports/gpu-renderer/` except the new
  `reports/gpu-renderer/evidence/` namespace;
- retire the report-only `:integration-tests:skia-evidence` Phase 6 module and
  the legacy R6/M9 GPU renderer PM exporters/validators that would recreate the
  catch-all reports;
- update active build, CI, documentation, and package-boundary references;
- retain historical traceability through Git and this decision document,
  rather than an active archive tree.

The replacement is allowed to reduce active scene count from 88 to a curated
initial set of 10 to 12. Breadth is recovered only through production-backed
scenes with complete evidence.

## Goals

- Make the scene expectation and observed execution outcome one enforced
  invariant.
- Exercise the canonical production lowering, recording, preparation, and
  `GPUFrameCoordinator` execution path.
- Produce a complete, machine-verifiable evidence bundle for every positive
  rendering claim.
- Treat stable refusal as a first-class successful test outcome when the
  product route is unsupported.
- Keep correctness, performance, and PM presentation as separate claims.
- Keep all supported execution headless/offscreen.
- Make adding a scene substantially cheaper than adding or maintaining a
  renderer path.

## Non-Goals

- Do not port Ganesh or Graphite.
- Do not implement a SkSL compiler, IR, or VM.
- Do not compile arbitrary SkSL or WGSL supplied by a scene.
- Do not add renderer features, compatibility shaders, geometry lowering, or
  CPU-rendered texture fallbacks to make the initial catalog pass.
- Do not reproduce all 88 legacy scenes or preserve pixel comparability with
  every legacy PNG.
- Do not retain roadmap boards, release panels, native windowing, Kadre, or
  historical milestone scenes as active correctness evidence.
- Do not merge this work into the broad Skia GM corpus. The new module may
  consume Skia references, but it owns a much smaller GPU product-promotion
  contract.

## Hard Architectural Boundaries

The new module must obey these boundaries:

1. It depends on `:gpu-renderer` and only the product or test-fixture modules
   needed to construct inputs and references. It has no compile-time direct
   dependency on wgpu4k and no `io.ygdrasil.webgpu` imports.
2. It does not own WGSL source, shader composition, bind-group layouts,
   uniform packing, render-pipeline construction, command encoders, submission,
   or readback implementation.
3. It never calls a direct or immediate target encoder. GPU work passes
   through the production prepared-frame/session facade and
   `GPUFrameCoordinator`.
4. It creates production inputs, preferably the public Kanvas recording
   boundary when that is the integration under test, or existing
   `NormalizedDrawCommand` values for isolated renderer evidence. It does not
   introduce a parallel `SceneCommand` DSL.
5. Registered runtime effects use registered Kanvas descriptors with Kotlin
   or CPU oracle behavior and parser-validated WGSL GPU implementations.
   Unknown descriptor IDs and arbitrary source refuse with stable diagnostics.
6. A feature that cannot be expressed or executed through product APIs is
   omitted from the positive catalog or represented by `ShouldRefuse`. The
   harness never implements it locally.
7. Native interactive windowing remains out of scope.

Package-boundary tests in both `:gpu-renderer` and the new module enforce these
rules by scanning dependencies, imports, source resources, and forbidden call
sites.

## Component Model

### Scene Descriptor

A scene descriptor contains only stable metadata:

- business-readable scene ID;
- title and purpose;
- dimensions and deterministic seed, if applicable;
- draw-family tags;
- expected outcome;
- oracle policy;
- comparison policy;
- capability requirements.

The outcome is exactly one of:

- `ShouldRender`;
- `ShouldRefuse(stableReasonCode)`.

There is no generic product-activation boolean and no roadmap milestone field.
Promotion state is derived from evidence, not declared by catalog prose.

### Scene Program

A scene program constructs immutable production inputs. It owns fixture values
such as geometry, colors, matrices, registered descriptor IDs, images, and text
runs. It does not lower those values into WGSL or backend-specific commands.

Scene programs are deterministic and have no access to output directories,
adapter discovery, renderer caches, or report writers.

### Scene Runner

The runner performs one closed operation:

```text
scene program
  -> production command/recording boundary
  -> product analysis and routing
  -> GPUTaskList / prepared frame
  -> GPUFrameCoordinator
  -> readback or RefuseDiagnostic
```

The runner returns a typed result. `Rendered` carries pixels, route facts,
diagnostics, telemetry, and environment facts. `Refused` carries the stable
reason code and diagnostics. `Unavailable` is reserved for absent or unusable
GPU infrastructure and cannot satisfy either a rendering or performance
promotion gate.

### Oracle And Comparator

Every `ShouldRender` scene names one authoritative oracle:

- Kanvas CPU rendering;
- an upstream Skia GM reference;
- a checked-in, provenance-bearing reference approved for that scene.

The comparator operates on explicit color format, alpha convention, and image
dimensions. It emits exact counters and the configured similarity metric. A
threshold is stored with its rationale and version; it is not silently
rebaselined.

`ShouldRefuse` scenes do not fabricate an image oracle. Their oracle is the
exact stable diagnostic code and the absence of GPU submission and product
pixels.

### Expectation Gate

The gate compares descriptor expectation with the typed runner result:

| Expectation | Observed result | Verdict |
|---|---|---|
| `ShouldRender` | rendered and comparison passes | pass |
| `ShouldRender` | refused, unavailable, or diff fails | fail/inconclusive; never pass |
| `ShouldRefuse(code)` | refused with exactly `code` and no submission | pass |
| `ShouldRefuse(code)` | rendered or refused with another code | fail |
| either | unavailable adapter/device | unavailable; cannot promote |

This invariant is tested at the runner boundary and verified again from the
serialized evidence bundle.

### Evidence Writer And Verifier

All correctness and performance reports, including generated runs, refusals,
failures retained for diagnosis, and reviewed promotions, live below one
root:

```text
reports/gpu-renderer/evidence/
  correctness/
    generated/<source-commit>/<scene-id>/
    promoted/<scene-id>/
  performance/
    generated/<source-commit>/<scene-id>/
    promoted/<scene-id>/
```

Generated directories are ignored by Git and may be recreated. A promotion
copies a verified generated bundle into the matching `promoted/` namespace and
adds review metadata. A normal run cannot write into a `promoted/` directory.
No GPU evidence report is written under a module `build/reports/` directory or
directly into the catch-all `reports/gpu-renderer/` root.

For `ShouldRender`, one bundle contains:

```text
manifest.json
gpu.png
cpu.png or skia.png
diff.png
stats.json
route.json
diagnostics.json
environment.json
verdict.json
```

For `ShouldRefuse`, the bundle omits image files and records the refusal,
zero-submission assertion, route diagnostics, environment, and verdict.

The verifier treats a missing, stale, internally inconsistent, or
schema-incompatible artifact as failure. Generated reports do not become
support claims merely because a Gradle task completed.

## Initial Catalog

The bootstrap gate contains three scenes before broader coverage is added:

1. solid rectangle, `ShouldRender`;
2. registered `SimpleRT` runtime effect, `ShouldRender`;
3. unregistered runtime-effect descriptor or arbitrary WGSL,
   `ShouldRefuse("unsupported.runtime_effect.custom_wgsl_not_registered")`.

The cutover catalog targets 10 to 12 focused scenes drawn from:

- solid rect;
- rounded rect with linear gradient;
- path fill;
- stroked path or stroked rect;
- clipping;
- bitmap sampling;
- `saveLayer` or destination read;
- A8 text;
- color glyph, only after the real font delivery and route evidence exist;
- registered runtime effect;
- unregistered runtime-effect refusal;
- one capability or budget refusal.

A candidate enters as `ShouldRender` only when the production route and oracle
already exist. Dependency-gated font or codec cases remain absent or explicit
refusals until the real dependency lands; no temporary substitute is allowed.

## Correctness And CI Policy

The module exposes three distinct verification levels:

1. Host-independent contract tests validate descriptor uniqueness,
   determinism, expectation matching, schema serialization, comparator
   behavior, boundary rules, and refusal semantics. These run in ordinary
   `check`.
2. A GPU correctness lane renders the curated catalog and writes complete
   bundles. Adapter or device unavailability yields `Unavailable`, not success.
3. A promotion verifier consumes complete bundles and validates schemas,
   route facts, expected outcomes, image statistics, and provenance.

CI must make the status of each lane explicit. A host-independent green build
does not imply GPU correctness. A GPU lane that did not run cannot update a
support matrix or PM activation report.

Failures preserve diagnostics and partial artifacts but do not overwrite an
approved baseline. Rebaseline is an explicit, reviewed operation with the
reason, prior metric, new metric, adapter facts, and source commit recorded.

## Performance Policy

Performance runs use a separate Gradle task, schema, promotion gate, and the
dedicated `reports/gpu-renderer/evidence/performance/` subtree. They never
influence correctness verdicts.

A performance bundle records at least:

- adapter, backend, vendor, device, driver, and software/hardware
  classification;
- capability lane and eligibility decision;
- cold, warmup, and measured frame counts;
- p50 and p95 frame measurements when timing is supported;
- observed cache, pipeline, submission, upload, and readback counters;
- metric source and unavailable fields;
- gate version and verdict.

Software adapters such as llvmpipe may provide diagnostic measurements but
cannot satisfy a hardware product-performance gate. Derived counters are
labeled derived and cannot masquerade as observed product activation.

## Cutover And Historical Policy

The old module remains frozen during reconstruction. Only changes strictly
needed to keep the repository building or to expose an already-existing
production API are allowed; new scene coverage and new legacy renderer paths
are forbidden.

Cutover is a single reviewable change after all entry gates pass. It removes:

- `gpu-renderer-scenes/`;
- `reports/gpu-renderer-scenes/`;
- every existing file and directory directly under `reports/gpu-renderer/`
  other than `evidence/`;
- the report-only `integration-tests/skia-evidence/` Phase 6 generator module;
- legacy R6/M9 PM report exporters, validators, tests, and Gradle tasks;
- Gradle tasks owned only by that module;
- package-boundary references that require the old module;
- active documentation that presents old reports as current evidence.

No source or report tree is moved into an `archive/` directory. Git retains the
full history. This specification and the cutover commit record why the material
was removed and where its last revision can be found. After cutover,
`reports/gpu-renderer/evidence/` is the only active report namespace for the GPU
renderer.

## Delivery Slices

The implementation plan will split the work into independently reviewable
slices:

1. freeze and inventory the legacy module;
2. establish module boundaries and typed outcome contracts;
3. build the runner and expectation gate with fake execution tests;
4. add evidence schemas, writer, verifier, and comparator;
5. connect the product prepared-frame execution path;
6. prove the three-scene bootstrap gate;
7. grow the catalog to the cutover set and wire the GPU CI lane;
8. perform the atomic cutover;
9. add the separate performance lane.

The expected review shape is six to nine PRs. The replacement should remain
approximately 3,000 to 5,000 lines of hand-maintained Kotlin excluding tests
and generated artifacts. This is a design budget, not a reason to combine
responsibilities into large files.

## Cutover Acceptance Criteria

Cutover is allowed only when all of the following are true:

- the new module has no source dependency on `gpu-renderer-scenes`;
- no scene definition embeds WGSL or backend encoding logic;
- every catalog entry has an expectation/result invariant test;
- the unregistered runtime-effect case refuses with the exact stable code and
  records zero GPU submissions;
- every `ShouldRender` scene produces GPU, oracle, diff, statistics, route,
  diagnostics, environment, and verdict artifacts;
- all positive scenes use the canonical production execution path;
- unsupported paths refuse without silent CPU rendering of a full draw,
  layer, filter, text run, or scene into a GPU texture;
- the initial 10-to-12-scene catalog passes on an eligible GPU correctness
  lane;
- unavailable hardware is reported as unavailable and cannot promote;
- correctness and performance tasks have separate schemas and gates;
- every new correctness and performance report is contained below
  `reports/gpu-renderer/evidence/`;
- `reports/gpu-renderer/` contains no legacy child beside `evidence/`;
- active documentation links the replacement evidence rather than legacy
  reports;
- the full repository build and relevant GPU renderer boundary tests pass
  after legacy deletion.

## Risks And Mitigations

### Temporary coverage contraction

The active catalog becomes much smaller. This is intentional. An inventory
maps each legacy scene intent to `covered`, `duplicate`, `historical`,
`dependency-gated`, `unsupported`, or `future candidate` before deletion, so
useful intent is not forgotten.

### Missing public product seam

The current prepared scene recorder lives in the legacy module. If the new
harness cannot reach the production path without copying that logic, work
stops at the boundary. The renderer receives the smallest generally useful
public or test-fixture seam required to record and execute production inputs;
the harness does not recreate the seam.

### Hardware-dependent CI

GPU availability and adapter eligibility are explicit result states. Contract
tests remain deterministic on ordinary hosts, while promotion requires a
separate eligible GPU lane and complete artifacts.

### Report churn

Schemas are versioned. Generated runs stay in the ignored
`reports/gpu-renderer/evidence/*/generated/` subtrees, while reviewed promotion
bundles enter the tracked `reports/gpu-renderer/evidence/*/promoted/` subtrees.
Writers reject any destination outside this namespace.

### Scope pressure

Scenes that expose missing renderer features do not expand this project. They
remain refusals or future candidates and generate separate renderer work under
the applicable GPU renderer specifications.

## Rejected Alternatives

### Delete first, rebuild later

This is cleaner operationally but creates an avoidable period with no
scene-level GPU evidence. It is rejected in favor of a frozen shadow period.

### Refactor the existing module in place

This preserves broad coverage but makes it too easy to retain direct rendering
helpers, custom WGSL, roadmap metadata, and coupled reports. It is option A,
not the approved clean rebuild.

### Preserve the old catalog and port scenes mechanically

This would reproduce the current semantic and maintenance burden under a new
package name. Scene intent is inventoried, but inclusion in the new catalog
requires fresh product evidence.

## Verification Authority

Checked-in production code at the evaluated commit is the source of truth for
implemented behavior. In particular, current command contracts, analysis and
routing, recording, frame preparation, execution, diagnostics, runtime-effect
registration, and telemetry are defined by the code under `gpu-renderer/src`.

Executable tests and runtime artifacts tied to the same commit verify claims
about that behavior. A rendering-support claim requires the observed route,
diagnostics, GPU result, oracle result, comparison statistics, environment, and
verdict required by this design. A refusal claim requires the observed stable
diagnostic and proof that no GPU submission or product pixels were produced.

Documentation may describe intent, constraints, or a future target. It is not
verification evidence and cannot override observed code behavior, activate a
route, satisfy a promotion gate, or turn an untested capability into supported
functionality. When documentation and code disagree, the evidence report must
record the divergence and describe the code as the current implementation.
Changing the intended contract then requires a reviewed code change with
executable verification.
