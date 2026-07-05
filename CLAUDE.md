# Project memory — Kanvas

## Reference sources

### Skia GM C++ sources (read-only, primary spec)
- **Path** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm`
- **Usage** : when porting a `tests/<Name>GM.kt`, the upstream C++ implementation lives at `<Name>.cpp` (or sometimes a grouped file) under that directory. Use it as the authoritative spec — the Javadoc in `kanvas/src/generated/tests/org/skia/tests/` is mechanically extracted from these files but only carries the `onDraw` body, while the `.cpp` may add helpers, comments, and edge-case rationale.

### Other relevant trees
- `kanvas/src/generated/tests/org/skia/tests/` — mechanically translated Kotlin stubs (TODO bodies) carrying the original C++ as Javadoc; useful for class shape and signatures.
- `kanvas/src/test/resources/original-888/` — 989 PNG references rendered by upstream Skia (embedded `Google/Skia` ICC profile, see Phase 1 note in `archives/MIGRATION_PLAN.md`).
- `kanvas/src/main/kotlin/` — the active Kanvas API and renderer-facing surface.

## Upstream planning status
The old root-level migration plans and upstream snapshots are archived because
their phase tracking no longer matches the implementation reliably. Treat
archived plans as historical evidence only; do not use archived checkboxes or
phase labels as active backlog.

Current upstream/rebaseline evidence lives under
[reports/upstream-rebaseline/](reports/upstream-rebaseline/) and
[.upstream/source/map/](.upstream/source/map/).

For future pipeline architecture, use
[.upstream/target/high-performance-wgsl-pipeline-target.md](.upstream/target/high-performance-wgsl-pipeline-target.md)
as the target design for the high-performance WGSL/WebGPU pipeline, including
the shared Kanvas pipeline IR, WGSL parser/IR module builder, CPU scalar/vector
execution, and GPU generated-shader direction.

For the active Skia-like real-time renderer target, use
[.upstream/target/skia-like-realtime-renderer-target.md](.upstream/target/skia-like-realtime-renderer-target.md)
and
[.upstream/specs/skia-like-realtime/README.md](.upstream/specs/skia-like-realtime/README.md)
before planning rendering feature expansion, Skia GM promotion, real-time
runtime, performance tiering, PM demos, or release-candidate work.

The completed MEP conformance/performance target and old post-MVP backlogs live
under
[archives/target-closeout-2026-05-31/](archives/target-closeout-2026-05-31/).
Treat them as historical evidence only, not active backlog or acceptance
criteria.

For pre-Geometry WGSL paint-pipeline implementation planning, use
[.upstream/specs/wgsl-pipeline/README.md](.upstream/specs/wgsl-pipeline/README.md)
as the entry point for PipelineIR, WGSL parser/reflection/module-builder,
uniform packers, CPU scalar/vector execution, generated WGSL, BlendPlan,
runtime-effect descriptors, validation, migration, and ADRs.

For Geometry/Coverage implementation planning, use
[.upstream/specs/geometry-coverage/README.md](.upstream/specs/geometry-coverage/README.md)
as the entry point for shape lowering, clip lowering, coverage plans, CPU
spans, WebGPU stencil-cover, diagnostics, validation, and ADRs.

For Linear milestone execution, skill usage, subagent handoffs, and PM demo
evidence, use
[.upstream/target/linear-agent-methodology.md](.upstream/target/linear-agent-methodology.md).

For completed Linear backlog archival, use
[reports/linear-archive/README.md](reports/linear-archive/README.md) and
[scripts/linear_archive.py](scripts/linear_archive.py). Committed Linear
archive snapshots are historical evidence only; active backlog remains in
Linear and the current target/spec documents.

For RC/MEP wording, PM demos, and runtime-effect explanations, WGSL is the
shader implementation target. SkSL may be mentioned only as Skia API
compatibility context: Kanvas does not dynamically compile SkSL, does not build
a SkSL compiler/IR/VM, and supports runtime effects through registered Kanvas
descriptors with Kotlin CPU behavior plus parser-validated WGSL GPU modules.

For RC/MEP CI and PM packages, keep mandatory headless validation separate from
native Kadre demo execution. `pipelinePmBundle` and checked-in RC validators
must not accidentally resolve unpublished Kadre artifacts or require an
initialized `external/poc-koreos` submodule. Native Kadre demos are opt-in local
evidence and must document when the submodule is required.

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
  do not add short-lived substitutes just to clear archived backlog rows.

Historical phase tracking lives in [archives/](archives/), including
the 2026-05-24 snapshot under
[archives/plan-snapshots-2026-05-24/](archives/plan-snapshots-2026-05-24/).
Root-level upstream snapshots archived on 2026-05-26 live under
[archives/root-plans-2026-05-26/](archives/root-plans-2026-05-26/).

## Map de correspondance Kotlin ↔ Skia upstream
La table Kotlin ↔ C++ vit dans [.upstream/source/map/](.upstream/source/map/) (TSV requêtable terminal, **hors du code Kotlin**). Spec dans [.upstream/source/map/README.md](.upstream/source/map/README.md) — format 4 colonnes `kotlin FQN | cpp FQN | kotlin path:line | cpp path:line`. Helpers : `_resolve_url.sh` (path:line → URL clickable) et `audit.sh <module>` (liste les symboles publics Kotlin sans entrée TSV).

## Doc API
Doc générée par Dokka 2.2.0 (mode V1). Voir [docs/README.md](docs/README.md). Pour le module `:math` : `./gradlew :math:dokkaHtml` (site) ou `./gradlew :math:dokkaGfm` (markdown). CI déploie sur GitHub Pages à chaque push master qui touche `math/**` (workflow [.github/workflows/docs.yml](.github/workflows/docs.yml)).
