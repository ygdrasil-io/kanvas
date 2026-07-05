@/Users/chaos/.codex/RTK.md

Removed migration plans and root upstream snapshots live in Git history only.
Do not use historical checkboxes or phase labels as active backlog.

Current upstream/rebaseline evidence lives under `reports/upstream-rebaseline/`
and `.upstream/source/map/`.

For the target high-performance WGSL/WebGPU pipeline architecture, read
`.upstream/target/high-performance-wgsl-pipeline-target.md` before planning
pipeline, shader, runtime-effect, or raster/GPU convergence work.

For the active Skia-like real-time renderer target, read
`.upstream/target/skia-like-realtime-renderer-target.md` and
`.upstream/specs/skia-like-realtime/README.md` before planning rendering
feature expansion, Skia GM promotion, real-time runtime, performance tiering,
PM demos, or release-candidate work.

The completed MEP conformance/performance target and old post-MVP backlogs were
removed from the working tree. Treat any recovered Git-history copy as
historical evidence only, not active backlog or acceptance criteria.

For pre-Geometry WGSL paint-pipeline implementation specs, read
`.upstream/specs/wgsl-pipeline/README.md` before planning PipelineIR, WGSL
parser/reflection/module-builder, uniform packer, CPU scalar/vector, generated
WGSL, BlendPlan, runtime-effect descriptor, or migration/retirement work.

For Geometry/Coverage implementation specs, read
`.upstream/specs/geometry-coverage/README.md` before planning shape lowering,
clip lowering, coverage plans, CPU spans, WebGPU stencil-cover, or fallback
diagnostics work.

For RC/MEP wording, PM demos, and runtime-effect explanations, use WGSL as the
implementation target. Mentions of SkSL must be framed only as Skia API
compatibility surface that Kanvas does not dynamically compile. Supported
runtime effects require registered Kanvas descriptors with Kotlin/CPU behavior
and parser-validated WGSL GPU implementations.

For RC/MEP CI and PM packages, keep headless validation separate from native
Kadre demo execution. `pipelinePmBundle` and checked-in RC validators must not
accidentally resolve unpublished Kadre artifacts or require an initialized
`external/poc-koreos` submodule. Native Kadre demos are opt-in local evidence
and must document when the submodule is required.

Hard architecture decisions:

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep `SkRuntimeEffect` as a compatibility facade backed by registered
  Kotlin/WGSL implementations.
- Use Kadre from `ygdrasil-io/poc-koreos` for live/native windowing work. It
  is incubating and unpublished, so it may be included as a git submodule.
- Treat `ygdrasil-io/wgsl4k` as evolving. If parser/IR/generator behavior is
  ambiguous or surprising, stop the Kanvas assumption and open a `wgsl4k`
  ticket with minimized evidence instead of adding a hidden workaround.
- Do not mark rendering support as complete without reference, CPU/GPU
  evidence or explicit refusal, diff/stat artifacts, route diagnostics, and
  stable fallback policy.
- Treat font/codec gaps as dependency-gated until the real deliveries land;
  do not add short-lived substitutes just to clear historical backlog rows.
