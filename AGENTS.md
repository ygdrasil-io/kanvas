@/Users/chaos/.codex/RTK.md

Archived migration plans and root upstream snapshots are historical evidence
only. Do not use archived checkboxes or phase labels as active backlog.

Current upstream/rebaseline evidence lives under `reports/upstream-rebaseline/`
and `.upstream/source/map/`.

For the target high-performance WGSL/WebGPU pipeline architecture, read
`.upstream/target/high-performance-wgsl-pipeline-target.md` before planning
pipeline, shader, runtime-effect, or raster/GPU convergence work.

For pre-Geometry WGSL paint-pipeline implementation specs, read
`.upstream/specs/wgsl-pipeline/README.md` before planning PipelineIR, WGSL
parser/reflection/module-builder, uniform packer, CPU scalar/vector, generated
WGSL, BlendPlan, runtime-effect descriptor, or migration/retirement work.

For Geometry/Coverage implementation specs, read
`.upstream/specs/geometry-coverage/README.md` before planning shape lowering,
clip lowering, coverage plans, CPU spans, WebGPU stencil-cover, or fallback
diagnostics work.

For Linear milestone execution, skill usage, subagent handoffs, and PM demo
evidence, read `.upstream/target/linear-agent-methodology.md`.

Hard architecture decisions:

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep `SkRuntimeEffect` as a compatibility facade backed by registered
  Kotlin/WGSL implementations.
- Treat font/codec gaps as dependency-gated until the real deliveries land;
  do not add short-lived substitutes just to clear archived backlog rows.
