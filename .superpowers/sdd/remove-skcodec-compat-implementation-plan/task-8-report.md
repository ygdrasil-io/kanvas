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

## Review correction — DONE

- Replaced the remaining alpha probe expression
  `checkedBitmap.getPixel(x, y) ushr 24` with
  `checkedBitmap.getArgb(x, y) ushr 24`. `Bitmap.getPixel` returns `Color`,
  whereas `getArgb` is the canonical packed-integer API required by this
  bitwise assertion.
- PASS — the full declared codec matrix was rerun after the correction:
  `BUILD SUCCESSFUL` (117 actionable tasks: 5 executed, 112 up-to-date).
- PASS — static scans confirm no `Bitmap.getPixel(... ) ushr` expression and
  no `org.skia.foundation` import remain. The orphaned real-image test is
  still not a Gradle project, as documented above.
- PASS — `rtk git diff --check` is clean; the pre-existing score-file change
  remains unstaged and excluded.

## Global integration correction — DONE

### Findings resolved

1. PNG and JPEG now accept only `RGBA_8888` with `UNPREMUL` or `OPAQUE`
   metadata for both `Bitmap` and `Pixmap` entry points. Existing encoder
   tests prove `PREMUL` and `UNKNOWN` alpha refusals produce no output.
2. `Pixmap.getArgb` retains its documented `0` out-of-bounds sentinel, but
   now throws `UnsupportedOperationException` with a Kanvas diagnostic for
   an in-bounds CPU-inactive color type.
3. `AnimatedImage` now allocates its decode and display buffers directly
   from `codec.getInfo()` and `decodeInfo`; it does not coerce them to
   `RGBA_8888`. `PixmapUtils.orient` accepts every CPU-readable canonical
   color type and preserves F16 components without an ARGB round trip.
   Existing animation tests cover F16 scaling and RGB_565 orientation.
4. Android `ARGB_4444` serialization now emits the canonical little-endian
   packed value `A << 12 | R << 8 | G << 4 | B`; its existing bundle test
   checks the exact bytes `0x23, 0xF1` for `0xFF112233`.
5. JPEG writes to an in-memory transaction before forwarding bytes to the
   caller's `OutputStream`, so an ICC serialization refusal leaves the
   destination empty. The existing JPEG encoder test covers this path.

### Test evidence

- RED observed before the fixes:
  `PngEncoderTest` alpha refusal, `JpegEncoderTest` alpha and ICC refusal,
  and `PixmapTest` CPU-inactive read all failed at their new assertions.
- PASS after the fixes:
  `rtk ./gradlew :codec:png:test --tests org.graphiks.kanvas.codec.png.PngEncoderTest`,
  `rtk ./gradlew :codec:jpeg:test --tests org.graphiks.kanvas.codec.jpeg.JpegEncoderTest`,
  and `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.image.PixmapTest`.
- PASS: the complete declared codec matrix was rerun after the integration
  correction: `BUILD SUCCESSFUL` (117 actionable tasks: 17 executed,
  100 up-to-date).

`codec:android` and `codec:animated` remain directories with Gradle builds
but no corresponding projects in `settings.gradle.kts`; their updated
existing tests are therefore statically validated rather than executable in
this worktree. This is the pre-existing package-boundary condition already
recorded for Task 7, not a result of this correction.

## Final re-review correction — DONE

### Findings resolved

1. `PixmapUtils.orient` and the no-postprocess `AnimatedImage` crop/scale
   route now copy each canonical pixel's raw `bytesPerPixel`; neither route
   converts through `getArgb`/`setArgb` or `getPixel`/`setPixel`. The
   destination/source metadata must match exactly before this copy occurs.
   `AnimatedImage.Make` now rejects a requested color type, alpha type, or
   `ImageColorSpace` different from `codec.getInfo()`; only geometry may
   change. The existing orphaned animated tests cover the non-maximal
   RGB_565 raw value, exact ARGB_4444 bytes, and HDR RGBA_F16 components
   greater than one through orientation/scale.
2. `CodecImageGenerator.getPixels` validates dimensions, `RGBA_8888`, alpha,
   color-space identity, row bytes, and the `ByteBuffer` remaining capacity
   using `Long` arithmetic before decode. Writes target a little-endian
   `slice()` beginning at the input buffer's current position, so every
   refusal leaves the supplied buffer unchanged. `DeferredFromGenerator`
   now preserves the generator image alpha type instead of forcing
   `UNPREMUL`.
3. `Pixmap.getArgb` KDoc now explicitly distinguishes the out-of-bounds
   `0` sentinel from an in-bounds inactive-format
   `UnsupportedOperationException` diagnostic.

### Final validation

- PASS — `rtk ./gradlew :kanvas:test --tests
  org.graphiks.kanvas.image.PixmapTest :codec:common:compileKotlin`:
  `BUILD SUCCESSFUL`; all 7 `PixmapTest` tests passed.
- PASS — the declared full codec matrix was rerun after these corrections:
  `BUILD SUCCESSFUL` (117 actionable tasks: 18 executed, 99 up-to-date).
- PASS — static copy scan in `PixmapUtils` and `AnimatedImage` contains no
  pixel conversion accessor; the only `getPixels` results are codec decode
  calls. Generator source statically contains `remaining().toLong()`,
  `slice().order(ByteOrder.LITTLE_ENDIAN)`, and alpha propagation.
- The `codec:animated` and `codec:image-generator` directories have no
  projects included by `settings.gradle.kts`, so their existing/extended
  tests cannot be registered by this root Gradle build. This is the same
  pre-existing orphaned-module condition recorded above; the changed sources
  were compiled where included (`codec:common`) and statically checked.
- PASS — final `rtk git diff --check` is clean. The pre-existing score-file
  modification remains unedited and un-staged.
