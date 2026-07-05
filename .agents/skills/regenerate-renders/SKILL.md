---
name: regenerate-renders
description: Regenerate Kanvas render PNGs and update test-similarity-scores.properties for Skia GMs. Use when adding or modifying GMs, or when reference images change.
---

# Regenerate Renders & Similarity Scores

After adding or modifying GMs in `integration-tests/skia/`, you must regenerate
render PNGs and similarity scores before creating a PR.

## 1. Generate all renders

```bash
./gradlew :integration-tests:skia:generateSkiaRenders
```

This renders every registered SkiaGm via WebGPU and writes PNGs to
`integration-tests/skia/src/test/resources/generated-renders/<family>/<name>.png`.

## 2. Generate renders for a single GM (fast)

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=<name>
```

Example — render only `recordopts`:

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=recordopts
```

## 3. Generate renders for a single family

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.family=<family>
```

Example — render only `blur` GMs:

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.family=blur
```

## 4. Update similarity scores

```bash
./gradlew :integration-tests:skia:test
```

This runs the `SkiaGmRunner` test, which renders each GM via WebGPU, compares
against the reference PNG in `reference/`, and writes similarity scores to
`integration-tests/skia/test-similarity-scores.properties`.

**Note:** The full test suite can take 5–15 minutes. If you only changed a few
GMs, prefer generating renders per-GM (step 2) and committing the partial
scores — CI will complete the rest.

## 5. Commit generated artifacts

```bash
git add integration-tests/skia/src/test/resources/generated-renders/
git add integration-tests/skia/test-similarity-scores.properties
git commit -m "gm: update renders and similarity scores"
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Render generation hangs/timeout | Some GMs (e.g. LightingGM) have thousands of draw dispatches | Use `-Pgm.name=<name>` to render only the GMs you need |
| `generateSkiaRenders` outputs nothing | WebGPU native library not loading | Verify `wgpu4k-toolkit` dependency and `--enable-native-access` JVM arg |
| `test` task fails on pre-existing GMs | Those GMs have similarity below `minSimilarity` threshold | Not your problem — only fix scores for GMs you changed |
| Similarity scores missing for new GMs | Test runner didn't reach them before timeout | Generate renders (step 2), commit, let CI compute scores |
