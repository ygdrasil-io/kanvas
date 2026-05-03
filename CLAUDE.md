# Project memory — kanvas → kanvas-skia

## Reference sources

### Skia GM C++ sources (read-only, primary spec)
- **Path** : `/Users/chaos/workspace/kanvas-forge/skia-main/gm`
- **Usage** : when porting a `tests/<Name>GM.kt`, the upstream C++ implementation lives at `<Name>.cpp` (or sometimes a grouped file) under that directory. Use it as the authoritative spec — the Javadoc in `kanvas/src/generated/tests/org/skia/tests/` is mechanically extracted from these files but only carries the `onDraw` body, while the `.cpp` may add helpers, comments, and edge-case rationale.

### Other relevant trees
- `kanvas/src/generated/tests/org/skia/tests/` — mechanically translated Kotlin stubs (TODO bodies) carrying the original C++ as Javadoc; useful for class shape and signatures.
- `kanvas/src/test/resources/original-888/` — 989 PNG references rendered by upstream Skia (embedded `Google/Skia` ICC profile, see Phase 1 note in `MIGRATION_PLAN.md`).
- `kanvas/src/main/kotlin/` — the legacy hand-written `:kanvas` implementation (`com.kanvas.*`); useful as a porting reference but **not** to be depended upon — `:kanvas-skia` is autonomous.

## Migration plan
See [MIGRATION_PLAN.md](MIGRATION_PLAN.md) at the project root for phase tracking.
