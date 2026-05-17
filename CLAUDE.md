# Project memory — kanvas → kanvas-skia

## Reference sources

### Skia GM C++ sources (read-only, primary spec)
- **Path** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm`
- **Usage** : when porting a `tests/<Name>GM.kt`, the upstream C++ implementation lives at `<Name>.cpp` (or sometimes a grouped file) under that directory. Use it as the authoritative spec — the Javadoc in `kanvas/src/generated/tests/org/skia/tests/` is mechanically extracted from these files but only carries the `onDraw` body, while the `.cpp` may add helpers, comments, and edge-case rationale.

### Other relevant trees
- `kanvas/src/generated/tests/org/skia/tests/` — mechanically translated Kotlin stubs (TODO bodies) carrying the original C++ as Javadoc; useful for class shape and signatures.
- `kanvas/src/test/resources/original-888/` — 989 PNG references rendered by upstream Skia (embedded `Google/Skia` ICC profile, see Phase 1 note in `archives/MIGRATION_PLAN.md`).
- `kanvas/src/main/kotlin/` — the legacy hand-written `:kanvas` implementation (`com.kanvas.*`); useful as a porting reference but **not** to be depended upon — `:kanvas-skia` is autonomous.

## Migration plan
Raster/CPU port is essentially complete (357/437 GMs = 82 %, R-final closed). Historical phase tracking is archived in [archives/MIGRATION_PLAN.md](archives/MIGRATION_PLAN.md) (and the per-chantier mini-plans in [archives/](archives/)). The only **live** plan at the root is [MIGRATION_PLAN_GPU_WEBGPU.md](MIGRATION_PLAN_GPU_WEBGPU.md) — WebGPU/WGSL divergence plan.

## Map de correspondance Kotlin ↔ Skia upstream
La table Kotlin ↔ C++ vit dans [.upstream/source/map/](.upstream/source/map/) (TSV requêtable terminal, **hors du code Kotlin**). Spec dans [.upstream/source/map/README.md](.upstream/source/map/README.md) — format 4 colonnes `kotlin FQN | cpp FQN | kotlin path:line | cpp path:line`. Helpers : `_resolve_url.sh` (path:line → URL clickable) et `audit.sh <module>` (liste les symboles publics Kotlin sans entrée TSV).

## Doc API
Doc générée par Dokka 2.2.0 (mode V1). Voir [docs/README.md](docs/README.md). Pour le module `:math` : `./gradlew :math:dokkaHtml` (site) ou `./gradlew :math:dokkaGfm` (markdown). CI déploie sur GitHub Pages à chaque push master qui touche `math/**` (workflow [.github/workflows/docs.yml](.github/workflows/docs.yml)).
