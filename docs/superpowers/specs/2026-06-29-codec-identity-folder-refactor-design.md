# Codec identity + folder-layout refactor — design

Date: 2026-06-29
Status: Approved (design)

## Problem

The codec modules are the last major area still carrying the upstream Skia
identity. While `font` migrated to `org.graphiks.kanvas.font` and the renderer
to `org.graphiks.kanvas.gpu.renderer`, codec still uses the
`org.skia.codec.*` package and lives as ~19 flat top-level Gradle modules
(`codec-api`, `codec-core`, `codec-png-kotlin`, …) instead of a single nested
folder like `font/`.

Two goals:

1. **Identity** — give codec its own Kanvas package, mirroring `gpu-renderer`:
   `org.skia.codec.*` → `org.graphiks.kanvas.codec.*`.
2. **Folder layout** — consolidate the flat `codec-*` modules into one `codec/`
   directory with nested subprojects, mirroring `font/`, with the current
   `codec-all-kotlin` bundle promoted to the head module `:codec`.

## Constraints & decisions

- **Package only, keep `Sk*` class names.** `SkCodec`, `SkAndroidCodec`,
  `SkAnimatedImage`, etc. stay as-is. They are the Skia *API compatibility
  surface* consumed by other modules (`gpu-renderer`, `kanvas-skia`,
  `cpu-raster`, `skia-integration-tests`) and are explicitly preserved per
  `AGENTS.md`. Only the package coordinates move.
- **Submodule naming:** drop the `codec-` prefix *and* the `-kotlin` suffix;
  keep the `-api` suffix (it distinguishes the SPI/contract modules from
  implementations).
- **Head module:** `codec-all-kotlin` becomes `:codec` at `codec/`, exactly as
  `:font` is the bundle/aggregator for the font subprojects.
- **Aggregation unchanged:** `:codec` aggregates the same set as today's
  `codec-all-kotlin` (core + png/jpeg/gif/bmp/wbmp/webp/ico/extended decoders).
  `android`, `animated`, and `image-generator` remain independent
  `:codec:*` submodules that `:codec` does **not** pull. No dependency-graph
  behavior change.
- **Two-phase sequencing:** move first (package untouched, build green), rename
  package second (build green). Each phase is independently verifiable.
- **Docs in scope:** update the impacted docs so they stay accurate.

## Module mapping

| Current module | New Gradle path | New directory |
|---|---|---|
| codec-all-kotlin | `:codec` (head) | `codec/` |
| codec-api | `:codec:api` | `codec/api` |
| codec-core | `:codec:core` | `codec/core` |
| codec-common | `:codec:common` | `codec/common` |
| codec-png-api | `:codec:png-api` | `codec/png-api` |
| codec-png-kotlin | `:codec:png` | `codec/png` |
| codec-jpeg-api | `:codec:jpeg-api` | `codec/jpeg-api` |
| codec-jpeg-kotlin | `:codec:jpeg` | `codec/jpeg` |
| codec-gif-kotlin | `:codec:gif` | `codec/gif` |
| codec-bmp-kotlin | `:codec:bmp` | `codec/bmp` |
| codec-wbmp-kotlin | `:codec:wbmp` | `codec/wbmp` |
| codec-webp-kotlin | `:codec:webp` | `codec/webp` |
| codec-ico-kotlin | `:codec:ico` | `codec/ico` |
| codec-android | `:codec:android` | `codec/android` |
| codec-animated | `:codec:animated` | `codec/animated` |
| codec-extended | `:codec:extended` | `codec/extended` |
| codec-image-generator | `:codec:image-generator` | `codec/image-generator` |
| codec-test-fixtures | `:codec:test-fixtures` | `codec/test-fixtures` |
| codec-real-image-tests | `:codec:real-image-tests` | `codec/real-image-tests` |

Gradle maps a project path to a directory by path segments, so once the
physical directories match (`codec/png` for `:codec:png`, `codec/` for
`:codec`), no `projectDir` overrides are needed in `settings.gradle.kts` —
identical to how `font/` is wired.

## Package mapping

- `org.skia.codec` → `org.graphiks.kanvas.codec`
- `org.skia.codec.png` → `org.graphiks.kanvas.codec.png`
- `org.skia.codec.jpeg` → `org.graphiks.kanvas.codec.jpeg`
- `org.skia.codec.gif` → `org.graphiks.kanvas.codec.gif`
- `org.skia.codec.bmp` → `org.graphiks.kanvas.codec.bmp`
- `org.skia.codec.wbmp` → `org.graphiks.kanvas.codec.wbmp`
- `org.skia.codec.webp` → `org.graphiks.kanvas.codec.webp`
- `org.skia.codec.test` → `org.graphiks.kanvas.codec.test`
- `org.skia.codec.real` → `org.graphiks.kanvas.codec.real`

Class names (`Sk*`) are **not** changed. Source directories move from
`…/kotlin/org/skia/codec/…` to `…/kotlin/org/graphiks/kanvas/codec/…`.

Consumers outside codec (`gpu-renderer`, `kanvas-skia`, `cpu-raster`,
`skia-integration-tests`) update only their `import org.skia.codec.…`
statements; their own packages (`org.skia.encode`, `org.skia.tests`,
`org.skia.testing`) are out of scope and remain unchanged.

## Phase 1 — Folder & module restructure (package untouched)

1. Move each `codec-X/` directory to its new location under `codec/` per the
   mapping table. `codec-all-kotlin/` contents move to `codec/` (the head
   module's own `src/` + `build.gradle.kts`).
2. `settings.gradle.kts`: replace the flat `include(":codec-*")` lines with
   `include(":codec")` plus the nested `include(":codec:api")`,
   `include(":codec:core")`, … entries.
3. Update every cross-module `project(":codec-X")` reference to
   `project(":codec:Y")` across the build (~25 `build.gradle.kts`: `kanvas`,
   `gpu-renderer`, `gpu-renderer-scenes`, `cpu-raster`, `kanvas-skia`,
   `font/glyph`, `integration-tests`, `skia-integration-tests`, plus the codec
   modules referencing each other). `:codec-all-kotlin` references become
   `:codec`.
4. Root `build.gradle.kts`: update `pureKotlinCodecProjects` and
   `forbiddenCodecBackendProjects`, and switch the AWT-free enforcement matching
   from `project.name` to `project.path` — because `:codec:png` now has
   `project.name == "png"`, which would otherwise collide / fail to match. The
   `forbiddenCodecBackendProjects` guard entries (future `*-awt` / `*-imageio`
   backends that do not yet exist on disk) are renamed to their `:codec:*-awt` /
   `:codec:*-imageio` path form for consistency.
5. **Gate:** `./gradlew build` green; `CodecAllKotlinAssemblyTest` and
   `CodecAllKotlinRealImageTest` pass (ServiceLoader registration intact).

## Phase 2 — Package rename

1. Move sources `…/kotlin/org/skia/codec/…` → `…/kotlin/org/graphiks/kanvas/codec/…`
   in every codec module (main + test).
2. Rewrite `package` and `import` declarations `org.skia.codec` →
   `org.graphiks.kanvas.codec` across codec and its consumers.
3. **ServiceLoader (highest-risk):** the 8 provider registration files
   `*/src/main/resources/META-INF/services/org.skia.codec.CodecDecoderProvider`
   (in `png`, `jpeg`, `gif`, `bmp`, `wbmp`, `webp`, `ico`, `extended`) must be
   **renamed** to `…/org.graphiks.kanvas.codec.CodecDecoderProvider` *and* have
   their listed implementation FQCNs updated.
4. Update hardcoded FQCN strings in `CodecAllKotlinAssemblyTest`,
   `CodecAllKotlinRealImageTest`, and the KDoc references in `SkCodec.kt`.
5. **Gate:** `./gradlew build` green; the assembly/real-image tests pass and
   assert the new provider FQCNs are discovered.

## Docs

Update `README.md`, `SUPPORTED_CODECS.md`, `CONTEXT.md`, and `.upstream`
specs/reports that reference `codec-*` module names or the `org.skia.codec`
package so the documentation stays accurate after both phases.

## Risks & safeguards

- **Silent ServiceLoader breakage** (a missed/renamed service file or stale
  FQCN means a decoder is no longer registered at runtime): covered by the
  assembly and real-image tests that enumerate the loaded provider FQCNs.
- **Enforcement matching regression**: the `project.name`→`project.path`
  switch in the root build must be verified, otherwise the AWT-free guard
  silently stops covering codec modules. Add/confirm a check that the guard
  still applies to all `:codec:*` pure-Kotlin modules.
- **Skia compat surface**: keeping `Sk*` class names preserves the Skia API
  facade required by `AGENTS.md`.
- **No dependency-graph change**: `:codec` aggregation matches today's
  `codec-all-kotlin`, so transitive consumers are unaffected.

## Acceptance criteria

- All codec modules live under `codec/` with the nested paths above; no
  top-level `codec-*` directories remain.
- `:codec` is the head/aggregator module (former `codec-all-kotlin`).
- No `org.skia.codec` references remain in the repo (code, resources, tests,
  build, docs); all replaced by `org.graphiks.kanvas.codec`.
- `Sk*` class names unchanged.
- The 8 `META-INF/services` files are renamed and their contents updated.
- `./gradlew build` is green and the codec assembly + real-image ServiceLoader
  tests pass after each phase.
