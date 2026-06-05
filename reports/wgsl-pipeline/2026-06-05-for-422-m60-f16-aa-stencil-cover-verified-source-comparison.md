# FOR-422 - M60 F16 comparaison source verifiee, color-target et sortie finale

Date: 2026-06-05

## Resultat

Classification: `verified-source-matches-scratch-and-final-mutation`.

FOR-422 rapproche la source verifiee par FOR-421, la sortie scratch
`RGBA16Float` no-blend, la destination avant draw et la destination juste apres
draw. Sur les 16 enregistrements decisifs, la source retournee par le shader
correspond au color-target scratch, puis `SrcOver(scratch, dstBefore)`
reconstruit la mutation finale dans la tolerance F16.

## Preuve

- 48 comparaisons bornees sont ecrites: 16 pixels cibles sur les draws
  inspectes.
- 16 comparaisons sont decisives pour la mutation M60 F16.
- 32 sous-dessins source non nuls sont observes par l'instrumentation FOR-421.
- 16 sorties scratch color-target non nulles sont observees.
- Les 16 sorties scratch reconstruisent la destination finale immediate.
- Les 32 autres comparaisons restent presentes dans l'artefact, mais ne sont
  pas decisives car les draws correspondants ne mutent pas ces pixels.

## Interpretation

Le residu M60 F16 ne vient plus du canal storage diagnostique. Il ne vient pas
non plus d'un ecart entre la source verifiee et l'ecriture color-target scratch
sur les echantillons decisifs. La chaine locale observee est coherente:

```text
sourceColorSentToBlend verifiee
  -> scratch color-target RGBA16Float no-blend
  -> SrcOver(scratch, dstBefore)
  -> destination apres draw
```

La prochaine analyse doit donc utiliser ces valeurs verifiees comme point de
depart et comparer la couleur/coverage obtenue avec la reference attendue de la
scene. Le prochain ticket ne devrait pas continuer a suspecter le side-channel
storage.

## Artefact

- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-verified-source-comparison-for422/m60-f16-aa-stencil-cover-verified-source-comparison-for422.json`

## Non-objectifs preserves

- Aucun changement de rendu par defaut.
- Aucun changement de seuil, score, fallback ou promotion.
- Aucun dump WGSL massif.
- Aucun changement `wgsl4k`.
- Aucun correctif de rendu final applique dans ce ticket.

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py`
