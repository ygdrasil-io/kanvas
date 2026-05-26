# Project memory — kanvas → kanvas-skia

## Reference sources

### Skia GM C++ sources (read-only, primary spec)
- **Path** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm`
- **Usage** : when porting a `tests/<Name>GM.kt`, the upstream C++ implementation lives at `<Name>.cpp` (or sometimes a grouped file) under that directory. Use it as the authoritative spec — the Javadoc in `kanvas/src/generated/tests/org/skia/tests/` is mechanically extracted from these files but only carries the `onDraw` body, while the `.cpp` may add helpers, comments, and edge-case rationale.

### Other relevant trees
- `kanvas/src/generated/tests/org/skia/tests/` — mechanically translated Kotlin stubs (TODO bodies) carrying the original C++ as Javadoc; useful for class shape and signatures.
- `kanvas/src/test/resources/original-888/` — 989 PNG references rendered by upstream Skia (embedded `Google/Skia` ICC profile, see Phase 1 note in `archives/MIGRATION_PLAN.md`).
- `kanvas/src/main/kotlin/` — the legacy hand-written `:kanvas` implementation (`com.kanvas.*`); useful as a porting reference but **not** to be depended upon — `:kanvas-skia` is autonomous.

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

Hard architecture decisions:

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep `SkRuntimeEffect` as a compatibility facade backed by registered
  Kotlin/WGSL implementations.
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
