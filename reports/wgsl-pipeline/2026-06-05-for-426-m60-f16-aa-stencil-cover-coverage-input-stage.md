# FOR-426 - M60 F16 coverage input stage AA stencil-cover

Linear: FOR-426

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-coverage-input-stage-for426/m60-f16-aa-stencil-cover-coverage-input-stage-for426.json`

## Result

Le diagnostic FOR-426 mesure les entrees de couverture avant
`coverageOrAaAlpha` pour les 6 pixels partiels FOR-425.

Il expose `rawPathCoverage` depuis `supersampled_path_cov`,
`clipCoverage` depuis `clip_cov`, `finalCoverage` passee a
`m60_f16_application_point_output`, le cote inside/outside, `edgeCount`,
`fillType`, et le nombre de sous-echantillons couverts sur la grille 4x4.

La classification attendue permet de distinguer :

- `path-coverage-already-96` : la couverture brute vaut deja `96/255`.
- `clip-reduces-160-to-96` : la couverture brute vaut `160/255` puis le clip
  reduit le produit a `96/255`.
- `coverage-product-matches-160` : le produit reste a `160/255`.
- `inside-outside-selection-mismatch` : le mauvais cote de sous-dessin est
  selectionne.
- `coverage-input-stage-incomplete` : mesure insuffisante.

FOR-426 ne corrige pas encore le rendu M60 F16. Il ne change pas le rendu par
defaut, les seuils, le score, la promotion de scene, la politique de repli, le
`PipelineKey`, le runtime device hors instrumentation opt-in, ni wgsl4k.

## Validation

- `rtk python3 scripts/validate_for426_m60_f16_aa_stencil_cover_coverage_input_stage.py`
- `rtk python3 scripts/validate_for425_m60_f16_aa_stencil_cover_alpha_conversion_stage.py`
- `rtk python3 scripts/validate_for424_m60_f16_aa_stencil_cover_partial_coverage_alpha.py`
- `rtk python3 scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py`
- `rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for426-pycache python3 -m py_compile scripts/validate_for426_m60_f16_aa_stencil_cover_coverage_input_stage.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
- `rtk git diff --check`
