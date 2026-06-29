# Codec identity + folder-layout refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move codec to a Kanvas identity (`org.skia.codec.*` → `org.graphiks.kanvas.codec.*`, keeping `Sk*` class names) and consolidate the ~19 flat `codec-*` Gradle modules into one nested `codec/` folder mirroring `font/`, with `codec-all-kotlin` promoted to head module `:codec`.

**Architecture:** Two phases, each independently build-verifiable. Phase 1 relocates directories and rewires Gradle module paths with the package untouched. Phase 2 renames only the package coordinates. The existing `CodecAllKotlinAssemblyTest` / `CodecAllKotlinRealImageTest` (which enumerate the ServiceLoader-discovered decoder FQCNs) plus `./gradlew build` are the verification gates.

**Tech Stack:** Kotlin/JVM, Gradle (Kotlin DSL), `java.util.ServiceLoader` for decoder registration.

**Design spec:** `docs/superpowers/specs/2026-06-29-codec-identity-folder-refactor-design.md`

---

## Canonical mapping (reference for all tasks)

| Current module | New Gradle path | New directory |
|---|---|---|
| codec-all-kotlin | `:codec` | `codec/` |
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

Package: every `org.skia.codec` → `org.graphiks.kanvas.codec` (incl. `.png/.jpeg/.gif/.bmp/.wbmp/.webp/.test/.real`). `Sk*` class names unchanged.

**Do NOT modify** (historical evidence / contains intentional `org.skia.codec` strings): `archives/`, `reports/`, `.upstream/source/`, and this plan's own `docs/superpowers/` specs/plans.

---

# PHASE 1 — Folder & module restructure (package untouched)

## Task 1: Relocate every codec module directory into `codec/`

**Files:** all 19 `codec-*/` directories (moved via `git mv`).

- [ ] **Step 1: Move the head bundle's contents into `codec/` first**

The head module `:codec` lives at `codec/` itself. Move `codec-all-kotlin`'s `src` and `build.gradle.kts` up into a fresh `codec/` directory:

```bash
mkdir -p codec
git mv codec-all-kotlin/src codec/src
git mv codec-all-kotlin/build.gradle.kts codec/build.gradle.kts
# move any remaining tracked files (e.g. .gitignore) if present, then drop the empty dir
rmdir codec-all-kotlin 2>/dev/null || true
```

- [ ] **Step 2: Move the remaining submodules under `codec/`**

```bash
for m in api core common png-api jpeg-api test-fixtures real-image-tests android animated extended image-generator; do
  git mv "codec-$m" "codec/$m"
done
# decoder impls drop the -kotlin suffix
git mv codec-png-kotlin  codec/png
git mv codec-jpeg-kotlin codec/jpeg
git mv codec-gif-kotlin  codec/gif
git mv codec-bmp-kotlin  codec/bmp
git mv codec-wbmp-kotlin codec/wbmp
git mv codec-webp-kotlin codec/webp
git mv codec-ico-kotlin  codec/ico
```

- [ ] **Step 3: Verify no top-level `codec-*` directories remain**

Run: `ls -d codec-* 2>/dev/null; echo "exit=$?"`
Expected: prints nothing, `exit=1` (glob matched nothing). And `ls codec/` lists `api core common png-api png jpeg-api jpeg gif bmp wbmp webp ico android animated extended image-generator test-fixtures real-image-tests src build.gradle.kts`.

## Task 2: Rewire `settings.gradle.kts`

**Files:** Modify `settings.gradle.kts:75-93`

- [ ] **Step 1: Replace the flat codec includes with nested ones**

Replace these lines:

```kotlin
include(":codec-api")
include(":codec-core")
include(":codec-common")
include(":codec-test-fixtures")
include(":codec-real-image-tests")
include(":codec-all-kotlin")
include(":codec-png-api")
include(":codec-png-kotlin")
include(":codec-jpeg-api")
include(":codec-jpeg-kotlin")
include(":codec-gif-kotlin")
include(":codec-bmp-kotlin")
include(":codec-wbmp-kotlin")
include(":codec-webp-kotlin")
include(":codec-ico-kotlin")
include(":codec-android")
include(":codec-animated")
include(":codec-extended")
include(":codec-image-generator")
```

with:

```kotlin
include(":codec")
include(":codec:api")
include(":codec:core")
include(":codec:common")
include(":codec:test-fixtures")
include(":codec:real-image-tests")
include(":codec:png-api")
include(":codec:png")
include(":codec:jpeg-api")
include(":codec:jpeg")
include(":codec:gif")
include(":codec:bmp")
include(":codec:wbmp")
include(":codec:webp")
include(":codec:ico")
include(":codec:android")
include(":codec:animated")
include(":codec:extended")
include(":codec:image-generator")
```

No `projectDir` overrides are needed: Gradle maps `:codec:png` → `codec/png` and `:codec` → `codec/` by path, exactly as `:font` is wired.

## Task 3: Rewire intra-codec `project(":codec-…")` dependencies

**Files:** Modify the `build.gradle.kts` inside the moved codec modules.

- [ ] **Step 1: Apply these exact replacements**

| File (new path) | Replace | With |
|---|---|---|
| `codec/build.gradle.kts` | `project(":codec-core")` | `project(":codec:core")` |
| `codec/build.gradle.kts` | `project(":codec-png-kotlin")` | `project(":codec:png")` |
| `codec/build.gradle.kts` | `project(":codec-jpeg-kotlin")` | `project(":codec:jpeg")` |
| `codec/build.gradle.kts` | `project(":codec-gif-kotlin")` | `project(":codec:gif")` |
| `codec/build.gradle.kts` | `project(":codec-wbmp-kotlin")` | `project(":codec:wbmp")` |
| `codec/build.gradle.kts` | `project(":codec-bmp-kotlin")` | `project(":codec:bmp")` |
| `codec/build.gradle.kts` | `project(":codec-webp-kotlin")` | `project(":codec:webp")` |
| `codec/build.gradle.kts` | `project(":codec-ico-kotlin")` | `project(":codec:ico")` |
| `codec/build.gradle.kts` | `project(":codec-extended")` | `project(":codec:extended")` |
| `codec/build.gradle.kts` | `project(":codec-test-fixtures")` | `project(":codec:test-fixtures")` |
| `codec/core/build.gradle.kts` | `project(":codec-api")` | `project(":codec:api")` |
| `codec/test-fixtures/build.gradle.kts` | `project(":codec-api")` | `project(":codec:api")` |
| `codec/png-api/build.gradle.kts` | `project(":codec-core")` | `project(":codec:core")` |
| `codec/jpeg-api/build.gradle.kts` | `project(":codec-core")` | `project(":codec:core")` |
| `codec/png/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")`, `project(":codec-png-api")`, `project(":codec-test-fixtures")` | `project(":codec:core")`, `project(":codec:common")`, `project(":codec:png-api")`, `project(":codec:test-fixtures")` |
| `codec/jpeg/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")`, `project(":codec-jpeg-api")`, `project(":codec-test-fixtures")` | `project(":codec:core")`, `project(":codec:common")`, `project(":codec:jpeg-api")`, `project(":codec:test-fixtures")` |
| `codec/gif/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")` | `project(":codec:core")`, `project(":codec:common")` |
| `codec/bmp/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")` | `project(":codec:core")`, `project(":codec:common")` |
| `codec/wbmp/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")`, `project(":codec-test-fixtures")` | `project(":codec:core")`, `project(":codec:common")`, `project(":codec:test-fixtures")` |
| `codec/webp/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")` | `project(":codec:core")`, `project(":codec:common")` |
| `codec/ico/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")`, `project(":codec-png-kotlin")`, `project(":codec-bmp-kotlin")` | `project(":codec:core")`, `project(":codec:common")`, `project(":codec:png")`, `project(":codec:bmp")` |
| `codec/core` (codec-core) | `project(":codec-api")` | `project(":codec:api")` |
| `codec/android/build.gradle.kts` | `project(":codec-core")`, `project(":codec-all-kotlin")`, `project(":codec-test-fixtures")` | `project(":codec:core")`, `project(":codec")`, `project(":codec:test-fixtures")` |
| `codec/animated/build.gradle.kts` | `project(":codec-core")`, `project(":codec-android")`, `project(":codec-common")`, `project(":codec-gif-kotlin")` | `project(":codec:core")`, `project(":codec:android")`, `project(":codec:common")`, `project(":codec:gif")` |
| `codec/extended/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")` | `project(":codec:core")`, `project(":codec:common")` |
| `codec/image-generator/build.gradle.kts` | `project(":codec-core")` | `project(":codec:core")` |
| `codec/real-image-tests/build.gradle.kts` | `project(":codec-all-kotlin")`, `project(":codec-core")` | `project(":codec")`, `project(":codec:core")` |

## Task 4: Rewire external consumer dependencies

**Files:** Modify these `build.gradle.kts`.

- [ ] **Step 1: Apply these exact replacements**

| File | Replace | With |
|---|---|---|
| `kanvas/build.gradle.kts` | `project(":codec-api")` | `project(":codec:api")` |
| `gpu-renderer/build.gradle.kts` | `project(":codec-api")` | `project(":codec:api")` |
| `gpu-renderer-scenes/build.gradle.kts` | `project(":codec-png-kotlin")` | `project(":codec:png")` |
| `font/glyph/build.gradle.kts` | `project(":codec-png-kotlin")` | `project(":codec:png")` |
| `integration-tests/build.gradle.kts` | `project(":codec-core")` | `project(":codec:core")` |
| `kanvas-skia/build.gradle.kts` | `project(":codec-core")`, `project(":codec-image-generator")`, `project(":codec-webp-kotlin")` | `project(":codec:core")`, `project(":codec:image-generator")`, `project(":codec:webp")` |
| `skia-integration-tests/build.gradle.kts` | `project(":codec-core")`, `project(":codec-android")`, `project(":codec-animated")`, `project(":codec-image-generator")`, `project(":codec-extended")` | `project(":codec:core")`, `project(":codec:android")`, `project(":codec:animated")`, `project(":codec:image-generator")`, `project(":codec:extended")` |
| `cpu-raster/build.gradle.kts` | `project(":codec-core")`, `project(":codec-common")`, `project(":codec-all-kotlin")`, `project(":codec-ico-kotlin")`, `project(":codec-android")`, `project(":codec-animated")`, `project(":codec-extended")`, `project(":codec-image-generator")`, `project(":codec-test-fixtures")` | `project(":codec:core")`, `project(":codec:common")`, `project(":codec")`, `project(":codec:ico")`, `project(":codec:android")`, `project(":codec:animated")`, `project(":codec:extended")`, `project(":codec:image-generator")`, `project(":codec:test-fixtures")` |

## Task 5: Update root `build.gradle.kts` enforcement sets

**Files:** Modify `build.gradle.kts:8-37` and `:3095` and `:3109`.

Background: `pureKotlinCodecProjects` entries are resolved with `findProject(":$name")` (lines 3014, 3110), so each entry must equal the new path *without the leading colon*. `forbiddenCodecBackendProjects` is matched against `dependency.name` (lines 3028, 3084, 3122), i.e. the project's leaf name.

- [ ] **Step 1: Replace `pureKotlinCodecProjects` (lines 8-27)**

```kotlin
val pureKotlinCodecProjects = setOf(
    "codec",
    "codec:api",
    "codec:core",
    "codec:common",
    "codec:test-fixtures",
    "codec:real-image-tests",
    "codec:png-api",
    "codec:png",
    "codec:jpeg-api",
    "codec:jpeg",
    "codec:gif",
    "codec:bmp",
    "codec:wbmp",
    "codec:webp",
    "codec:ico",
    "codec:android",
    "codec:animated",
    "codec:extended",
)
```

- [ ] **Step 2: Replace `forbiddenCodecBackendProjects` (lines 29-37) with leaf names**

```kotlin
val forbiddenCodecBackendProjects = setOf(
    "all-awt",
    "png-imageio",
    "jpeg-imageio",
    "gif-imageio",
    "bmp-imageio",
    "wbmp-imageio",
    "webp-imageio",
)
```

- [ ] **Step 3: Fix line 3109 image-generator entry**

Replace `(pureKotlinCodecProjects + "codec-image-generator" + "kanvas-skia" + "cpu-raster")`
with `(pureKotlinCodecProjects + "codec:image-generator" + "kanvas-skia" + "cpu-raster")`.

- [ ] **Step 4: Fix the message string at line 3095**

Replace `appendLine("Use :codec-all-kotlin for runtime codec dispatch.")`
with `appendLine("Use :codec for runtime codec dispatch.")`.

- [ ] **Step 5: Scan the rest of the root build for stragglers**

Run: `rg -n 'codec-[a-z]|":codec-' build.gradle.kts`
Expected: no matches. Fix any remaining `codec-…` literal that denotes a moved module.

## Task 6: Update CI path filters and provenance scripts (module-name strings)

**Files:** `.github/workflows/test.yml:24,57`, `scripts/validate_kan047_codec_provenance_matrix.py`, `scripts/test_validate_kan047_codec_provenance_matrix.py`.

- [ ] **Step 1: Update CI path filters**

In `.github/workflows/test.yml`, replace both occurrences of `- 'codec-*/**'` with `- 'codec/**'`.

- [ ] **Step 2: Update provenance module references**

In both scripts, replace the string `codec-png-kotlin` with `codec/png`. Run `rg -n 'codec-png-kotlin|codec-[a-z]+-kotlin|codec-[a-z]+-api' scripts/` and update any remaining moved-module names to their `codec/<name>` form. Expected after edits: `rg -n 'codec-[a-z]+-(kotlin|api)|codec-all-kotlin' scripts/` returns no matches.

## Task 7: Phase 1 verification gate + commit

- [ ] **Step 1: Confirm Gradle sees the new project paths**

Run: `./gradlew :codec:png:dependencies --configuration runtimeClasspath -q | head -5`
Expected: resolves without "Project ':codec-png-kotlin' not found" errors.

- [ ] **Step 2: Build + run the codec assembly + real-image tests**

Run:
```bash
./gradlew build :codec:test :codec:real-image-tests:test checkPureKotlinCodecNoAwt checkProductionCodecRuntimeNoAwt checkProductionCodecImageClasspathNoJavaDesktop
```
Expected: BUILD SUCCESSFUL. `CodecAllKotlinAssemblyTest` and `CodecAllKotlinRealImageTest` pass (ServiceLoader still resolves all 8 providers — package is unchanged in Phase 1).

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor(codec): consolidate codec-* modules into nested codec/ folder"
```

---

# PHASE 2 — Package rename `org.skia.codec` → `org.graphiks.kanvas.codec`

## Task 8: Move source directories to the new package path

**Files:** every codec module's `src/main` and `src/test` Kotlin tree.

- [ ] **Step 1: Move each `org/skia/codec` tree to `org/graphiks/kanvas/codec`**

Only the `codec/` modules contain an `org/skia/codec` source tree (consumers like `gpu-renderer`/`kanvas-skia`/`cpu-raster` only *import* it; their own packages are `org.skia.encode/tests/testing` and stay put).

```bash
for d in $(find codec -type d -path '*/kotlin/org/skia/codec' ! -path '*/build/*'); do
  parent="${d%/org/skia/codec}"
  mkdir -p "$parent/org/graphiks/kanvas"
  git mv "$d" "$parent/org/graphiks/kanvas/codec"
  rmdir "$parent/org/skia" 2>/dev/null || true   # drop now-empty org/skia
done
```

- [ ] **Step 2: Verify no source files remain under `org/skia/codec`**

Run: `rg --files | rg 'org/skia/codec/' ; echo "exit=$?"`
Expected: prints nothing, `exit=1`.

## Task 9: Rename the 8 ServiceLoader registration files

**Files:** `codec/{png,jpeg,gif,bmp,wbmp,webp,ico,extended}/src/main/resources/META-INF/services/org.skia.codec.CodecDecoderProvider`

- [ ] **Step 1: Rename each service file (contents fixed in Task 10)**

```bash
for m in png jpeg gif bmp wbmp webp ico extended; do
  d="codec/$m/src/main/resources/META-INF/services"
  git mv "$d/org.skia.codec.CodecDecoderProvider" "$d/org.graphiks.kanvas.codec.CodecDecoderProvider"
done
```

- [ ] **Step 2: Verify**

Run: `rg --files | rg 'META-INF/services/org\.skia\.codec'; echo "exit=$?"`
Expected: prints nothing, `exit=1`. And `rg --files | rg 'META-INF/services/org\.graphiks\.kanvas\.codec\.CodecDecoderProvider' | wc -l` → `8`.

## Task 10: Rewrite every `org.skia.codec` reference (contents)

This single literal replace fixes package declarations, imports, service-file contents, hardcoded FQCN strings in tests, and `SkCodec.kt` KDoc — `org.skia.codec` is a unique substring, so the replace is safe.

**Excluded paths** (historical evidence / intentional old-name strings): `archives/`, `reports/`, `.upstream/source/`, `docs/superpowers/`.

- [ ] **Step 1: Replace across code, resources, active docs, scripts, CI**

```bash
rg -l --hidden 'org\.skia\.codec' \
  -g '!archives/**' -g '!reports/**' -g '!.upstream/source/**' -g '!docs/superpowers/**' -g '!**/build/**' \
  | while IFS= read -r f; do
      sed -i '' 's#org\.skia\.codec#org.graphiks.kanvas.codec#g' "$f"
    done
```
(macOS `sed -i ''`. On Linux use `sed -i`.)

- [ ] **Step 2: Verify no live `org.skia.codec` references remain**

Run:
```bash
rg -n 'org\.skia\.codec' -g '!archives/**' -g '!reports/**' -g '!.upstream/source/**' -g '!docs/superpowers/**' -g '!**/build/**'
```
Expected: no matches.

- [ ] **Step 3: Sanity-check the service-file contents now point to the new package**

Run: `cat codec/png/src/main/resources/META-INF/services/org.graphiks.kanvas.codec.CodecDecoderProvider`
Expected: `org.graphiks.kanvas.codec.png.PngKotlinDecoderProvider`

## Task 11: Phase 2 verification gate + commit

- [ ] **Step 1: Build + assembly/real-image tests (ServiceLoader must still resolve all 8 providers)**

Run:
```bash
./gradlew build :codec:test :codec:real-image-tests:test
```
Expected: BUILD SUCCESSFUL. `CodecAllKotlinAssemblyTest` / `CodecAllKotlinRealImageTest` pass and assert the new `org.graphiks.kanvas.codec.*` provider FQCNs are discovered.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor(codec): rename package org.skia.codec -> org.graphiks.kanvas.codec"
```

## Task 12: Documentation pass + commit

**Files:** `README.md`, `SUPPORTED_CODECS.md`, `CONTEXT.md`, `docs/**` (excluding `docs/superpowers/`), `.upstream/specs/**`, `.upstream/target/**`, `mkdocs.yml`.

- [ ] **Step 1: Update remaining module-name references**

Run: `rg -n 'codec-[a-z]+-(kotlin|api)|codec-all-kotlin' README.md SUPPORTED_CODECS.md CONTEXT.md docs .upstream mkdocs.yml -g '!docs/superpowers/**' -g '!.upstream/source/**'`
For each match, replace the old module name with its `:codec:<name>` Gradle path (or `codec/<name>` directory form, per the surrounding prose) using the canonical mapping table. Leave `archives/`, `reports/`, `.upstream/source/` untouched.

- [ ] **Step 2: Verify docs are consistent**

Run: `rg -n 'org\.skia\.codec|codec-[a-z]+-(kotlin|api)|codec-all-kotlin' README.md SUPPORTED_CODECS.md CONTEXT.md docs .upstream mkdocs.yml -g '!docs/superpowers/**' -g '!.upstream/source/**'`
Expected: no matches.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs(codec): update module names and package to org.graphiks.kanvas.codec"
```

---

## Acceptance criteria (final check)

- [ ] No top-level `codec-*` directories remain; all modules live under `codec/` with the nested Gradle paths in the mapping table.
- [ ] `:codec` is the head/aggregator module (former `codec-all-kotlin`), aggregating the same set as before.
- [ ] No live `org.skia.codec` references remain (code, resources, tests, build, scripts, CI, active docs); `archives/`, `reports/`, `.upstream/source/`, `docs/superpowers/` intentionally unchanged.
- [ ] `Sk*` class names unchanged.
- [ ] The 8 `META-INF/services` files are renamed to `org.graphiks.kanvas.codec.CodecDecoderProvider` with updated contents.
- [ ] `./gradlew build` green and the codec assembly + real-image ServiceLoader tests pass after both phases.
