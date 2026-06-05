# FOR-419 - M60 F16 shader-return storage zero-cause

Classification: `application-point-storage-hook-not-on-rendered-return-path`.

## Resultat

- Les 16 transitions mutatrices FOR-401/FOR-413 restent couvertes.
- FOR-418 observe le storage d'entree mais les champs couleur/couverture restent a zero.
- FOR-419 desactive cette ecriture d'entree et ne preserve que les sorties non nulles au point d'application.
- Dans FOR-419, la cible couleur scratch reste non nulle pour les 16 records, mais le storage au point d'application n'observe aucun record.

## Cause probable

Le hook storage `m60_f16_application_point_output` n'est pas sur le chemin `@location(0)` reel qui produit la couleur dans cette variante diagnostique. La divergence ne vient pas d'un offset/stride storage, d'un mauvais side/subdraw, d'un clear apres draw, du blend fixe, ni du load/store render-pass.

## Artefacts

- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419/m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-storage-zero-cause-for419/raw-runtime-snapshot-for419.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-storage-vs-color-target-for418/raw-runtime-snapshot-for418.json`

## Garde-fous

- Guard opt-in: `kanvas.webgpu.m60F16AaStencilCoverShaderReturnStorageZeroCause.enabled`.
- Desactive par defaut.
- Aucun changement de rendu par defaut, route, score, seuil, fallback ou promotion.
- Resources et caches FOR-419 fermes dans `SkWebGpuDevice.close()`.

## Validation

- `rtk python3 scripts/validate_for419_m60_f16_aa_stencil_cover_shader_return_storage_zero_cause.py`
