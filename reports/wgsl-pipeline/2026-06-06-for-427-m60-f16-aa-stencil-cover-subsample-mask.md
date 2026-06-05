# FOR-427 M60 F16 AA stencil-cover subsample mask

FOR-427 ajoute une preuve diagnostique opt-in pour les 6 pixels partiels localisés par FOR-426. Le rendu par défaut, les seuils, le scoring, la promotion de scène, la fallback policy, PipelineKey et wgsl4k restent inchangés.

## Source

- Draft: `global/kanvas/tickets/drafts/brouillon-ticket-for-427-m60-f16-comparer-les-sous-echantillons-cpu-et-wgsl-sample-covered`
- Finding: `global/kanvas/findings/for-426-raw-path-coverage-already-96-before-clip`
- Prerequisite artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-coverage-input-stage-for426/m60-f16-aa-stencil-cover-coverage-input-stage-for426.json`
- FOR-427 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-subsample-mask-for427/m60-f16-aa-stencil-cover-subsample-mask-for427.json`

## Result

The artifact captures the WGSL `sample_covered` 4x4 mask (`6/16`, `96/255`) for exactly the FOR-426 pixels and refuses a CPU cell-by-cell comparison because the CPU `scanFillPath` subsample identity is not exported yet. The CPU oracle still proves the expected coverage byte (`160/255`, matching `10/16` coverage), but not the exact 16-cell mask.

- `(92,75)`
- `(91,76)`
- `(90,77)`
- `(89,78)`
- `(88,79)`
- `(87,80)`

Each pixel includes `x`, `y`, `drawIndex`, `subdrawOrdinal`, `subdrawRole`, `entryPoint`, `edgeCount`, `fillType`, the FOR-426 raw coverage fields, the expected CPU 10/16 count, and the captured WGSL 4x4 grid. `cpuSubsampleMask4x4`, `cpuGrid4x4`, `matchingSubsamples`, `cpuOnlySubsamples`, and `wgslOnlySubsamples` remain `null` so the artifact does not claim a synthetic CPU mask.

Classification: `subsample-mask-stage-incomplete`.

Summary:

- CPU reference coverage count: `60/96` expected covered subsamples across the 6 pixels (`10/16` each), derived from the checked-in CPU alpha oracle.
- WGSL `sample_covered`: `36/96` covered subsamples across the 6 pixels (`6/16` each).
- Matching cells: `null`.
- CPU-only cells: `null`.
- WGSL-only cells: `null`.

Interpretation: FOR-427 proves the WGSL-side mask source is capturable and still reports `6/16` on the six partial pixels, but it does not yet prove which CPU subsamples are missing. The next ticket should export the CPU `scanFillPath` 4x4 subsample identity before any rendering correction.

## Validation

Planned commands:

```bash
rtk python3 scripts/validate_for427_m60_f16_aa_stencil_cover_subsample_mask.py
rtk python3 scripts/validate_for426_m60_f16_aa_stencil_cover_coverage_input_stage.py
rtk python3 scripts/validate_for425_m60_f16_aa_stencil_cover_alpha_conversion_stage.py
rtk python3 scripts/validate_for424_m60_f16_aa_stencil_cover_partial_coverage_alpha.py
rtk python3 scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py
rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for427-pycache python3 -m py_compile scripts/validate_for427_m60_f16_aa_stencil_cover_subsample_mask.py
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
```
