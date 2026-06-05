# FOR-425 - M60 F16 alpha conversion stage AA stencil-cover

Linear: FOR-425

Artefact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-alpha-conversion-stage-for425/m60-f16-aa-stencil-cover-alpha-conversion-stage-for425.json`

## Resultat

Le diagnostic FOR-425 localise les 6 pixels partiels FOR-424 en
`alpha-drop-before-shader-return`.

Pour les 6 pixels partiels, la couverture CPU attendue reste `160/255`, mais
le premier stade capture dans le retour shader, `coverageOrAaAlpha`, vaut deja
`96/255`. Les stades suivants gardent la meme valeur `96/255` :

- `sourceAlphaAfterCoverage`
- `sourceColorBeforeQuantization.alpha`
- `quantizedAlphaSentToBlend`
- `sourceColorSentToBlend.alpha`

Le passage `160/255 -> 96/255` est donc deja present avant le stade de retour
shader capture par FOR-421/FOR-425. FOR-425 ne corrige pas le rendu et ne
change ni seuil, ni score, ni promotion de scene.

## Comptes

- 6 pixels partiels controles.
- 6 couvertures CPU attendues a `160/255`.
- 6 alphas source observes a `96/255`.
- 6 classifications `alpha-drop-before-shader-return`.
- 0 classification incomplete.

## Commandes de validation

- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for425_m60_f16_aa_stencil_cover_alpha_conversion_stage.py`
- `rtk python3 scripts/validate_for424_m60_f16_aa_stencil_cover_partial_coverage_alpha.py`
- `rtk python3 scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py`
- `rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for425-pycache python3 -m py_compile scripts/validate_for425_m60_f16_aa_stencil_cover_alpha_conversion_stage.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
- `rtk git diff --check`
