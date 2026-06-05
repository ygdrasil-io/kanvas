# FOR-420 - M60 F16 final WGSL diagnostic

Date: 2026-06-05

Classification: `diagnostic-final-wgsl-hooks-not-on-rendered-return-path`.

## Résultat

FOR-420 exporte, de façon opt-in par
`kanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled`, la source WGSL
finale juste avant l'appel `createShaderModule`.

L'artefact ne stocke pas de dump WGSL complet. Il contient les hashes SHA-256,
les labels, les cache keys, et un résumé borné des fonctions `fs_inside`,
`fs_outside` et `m60_f16_application_point_output`.

## Preuve principale

- Les quatre variantes attendues sont exportées: rendu normal bounded runtime
  correction, FOR-412 shader-return storage, FOR-418 storage-vs-color-target,
  et FOR-419 storage-zero-cause.
- Les quatre variantes ont bien `fs_inside` et `fs_outside` avec retour
  `@location(0)`.
- FOR-418 partage la source FOR-412; seule la passe/pipeline de comparaison
  change.
- Les variantes FOR-412/FOR-418 contiennent `m60_f16_record_fragment_lane` en
  entrée de fragment, mais `fs_inside` et `fs_outside` retournent encore
  l'expression normale bounded runtime correction.
- FOR-419 supprime bien l'écriture storage d'entrée et contient le helper
  `m60_f16_application_point_output` avec garde `output_nonzero`, mais
  `fs_inside` et `fs_outside` ne retournent pas ce helper.

## Interprétation

La source finale confirme le finding FOR-419: le hook
`m60_f16_record_application_point` existe dans la source diagnostique, mais il
n'est pas sur le chemin rendu `@location(0)` de `fs_inside`/`fs_outside`.

Le problème n'est donc pas le blend fixe, le load/store du render pass,
l'offset storage, le stride storage, le mauvais side/subdraw ou un clear après
draw. Le problème ciblé devient la substitution WGSL diagnostique elle-même:
le bloc de remplacement censé changer le retour de `fs_inside` et `fs_outside`
ne remplace pas le retour final effectivement compilé.

## Artefacts

- JSON:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-final-wgsl-diagnostic-for420/m60-f16-aa-stencil-cover-final-wgsl-diagnostic-for420.json`
- Validateur:
  `scripts/validate_for420_m60_f16_aa_stencil_cover_final_wgsl_diagnostic.py`

## Non-objectifs préservés

- Aucun changement de rendu par défaut.
- Aucune promotion M60 F16.
- Aucun changement de seuil, score, route ou fallback.
- Aucun changement wgsl4k.
- Aucun correctif de rendu final M60 F16.

## Suite recommandée

Ouvrir un ticket de correction diagnostique bornée: remplacer la substitution
fragile par une construction explicite ou un point d'instrumentation vérifié
par hash/structure, puis relancer FOR-412/FOR-418/FOR-419 pour capturer le vrai
retour `@location(0)` sans modifier le rendu final M60 F16.

## Validations

- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverFinalWgslDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
