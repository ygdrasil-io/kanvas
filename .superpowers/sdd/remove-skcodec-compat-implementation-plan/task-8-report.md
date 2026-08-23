# Task 8 report — DONE

## Migration

- Deleted `kanvas/src/main/kotlin/org/skia/foundation/SkCodecCompat.kt`.
- Confirmed that the Task 3 adapter
  `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/KanvasCodec.kt` remains
  absent.
- Migrated the remaining real-image codec test from `SkBitmap` to canonical
  `Bitmap`: its frame destination now preserves `checkedCodec.getInfo()` and
  probes use `Bitmap.getArgb`.
- No compatibility facade, alias, overload, import, type signature, or
  executable `Sk*` codec reference was added. Remaining upstream `Sk*`
  mentions are KDoc/provenance-only comments.

## Pre-removal scan

Command:

```bash
rtk rg -n --glob '*.kt' '^import org\.skia\.foundation' codec kanvas integration-tests
```

Observed migration consumer:

```text
codec/real-image-tests/src/test/kotlin/org/graphiks/kanvas/codec/real/CodecAllKotlinRealImageTest.kt:16:import org.skia.foundation.SkBitmap
```

The independent file inventory identified the compatibility source itself at
`kanvas/src/main/kotlin/org/skia/foundation/SkCodecCompat.kt`; it was the
planned deletion target.

## Validation

- PASS — declared full codec matrix:

  ```bash
  rtk ./gradlew :codec:api:test :codec:bmp:test :codec:common:test \
    :codec:core:test :codec:extended:test :codec:gif:test :codec:ico:test \
    :codec:jpeg:test :codec:jpeg-ls:test :codec:jpeg2000:test \
    :codec:jpegxl:test :codec:png:test :codec:test-fixtures:test \
    :codec:wbmp:test :codec:webp:test
  ```

  Result: `BUILD SUCCESSFUL` (117 actionable tasks: 45 executed, 72
  up-to-date). The normal optional oracle tests were SKIPPED; no test failed.

- PASS — post-removal import scan returned no matches:

  ```bash
  rtk rg -n --glob '*.kt' '^import org\.skia\.foundation' codec kanvas integration-tests
  ```

- PASS — broader executable/signature compatibility scan returned no matches:

  ```bash
  rtk rg -n --glob '*.kt' '\bSkCodecCompat\b|\borg\.skia\.foundation\.(SkBitmap|SkCodec|SkImageInfo|SkPixmap|SkData|SkColorType|SkAlphaType|SkColorSpace)\b' codec kanvas integration-tests
  ```

- PASS — compatibility file absence verified with:

  ```bash
  rtk sh -c 'test ! -e kanvas/src/main/kotlin/org/skia/foundation/SkCodecCompat.kt'
  ```

  The brief's literal `rtk test ! -e ...` was also invoked, but the local RTK
  wrapper passed `-e` to `sh` as a command and emitted `sh: -e: command not
  found`; the shell-wrapped equivalent above completed successfully.

- PASS — `rtk git diff --check` returned no output.

## Observed unrelated project issue

The modified real-image test resides in an orphaned Gradle directory:
`codec/real-image-tests/build.gradle.kts` exists but `settings.gradle.kts`
does not include `:codec:real-image-tests`. Therefore its direct task cannot
be run independently. Exact evidence:

```text
Cannot locate tasks that match ':codec:real-image-tests:test' as project 'real-image-tests' not found in project ':codec'.
```

This is not attributed to the compatibility removal. The declared matrix
passed, and the test's canonical imports and APIs were statically verified.

## Score-file integrity

`integration-tests/skia/test-similarity-scores.properties` was already
modified in the worktree. It was neither edited nor staged by this task and
is excluded from the commit.

## Commit

Commit message: `refactor(codec): remove SkCodecCompat`.

## Status score

10/10 — the requested compatibility implementation is physically absent,
all in-scope source scans are clean, the complete declared codec matrix
passes, and no generated score file is included.
