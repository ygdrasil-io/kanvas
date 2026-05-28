# M41 Dashboard Artifact Exporter

Linear: GRA-197
Date: 2026-05-28

## Result

Added a generated-scene export path for the WGSL pipeline dashboard.

- `pipelineGeneratedSceneExport` reads `reports/wgsl-pipeline/scenes/generated/results.json`.
- Generated rows copy canonical `artifacts/<scene-id>/...` files into `build/reports/wgsl-pipeline-generated-scenes/`.
- `pipelineSceneDashboard` now depends on that exporter, merges generated rows with static rows, and writes the combined dashboard to `build/reports/wgsl-pipeline-scenes/`.
- Static M40 rows remain source-controlled under `reports/wgsl-pipeline/scenes/data/scenes.json` and still render without generated rows.

## Validation Contract

Generated rows must include a `generation` block with `mode=generated` or `mode=mixed`, producer, commit, schema, and deterministic `artifactRoot=artifacts/<scene-id>`.

The exporter fails before dashboard rendering when a generated row references a missing artifact, report, or data file. Errors include the scene id and JSON field path, for example `bitmap-rect-nearest: missing generated artifact for cpu.image`.

Generated rows also require non-empty raw `evidence` links so PM dashboard rows can expose their source report, task, or test output.

## Validation

- `rtk git diff --check`
- `rtk ./gradlew --no-daemon pipelineGeneratedSceneExport pipelineSceneDashboard`
