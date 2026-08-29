# W65 — audit de la route texte bornée

## Résultat

Cette vague ne modifie pas le renderer et ne promeut pas une nouvelle famille
de scènes. La route texte préparée existante consomme déjà les contrats communs
de `Canvas`/`DisplayOp` pour les positions, les métriques, l’alpha du paint, le
clip et les transforms affines bornées. Les gradients pris en charge et le
`saveLayer` borné restent couverts par leurs routes Surface/WebGPU existantes.

## Preuves vérifiées

- `CanvasTest` couvre le snapshot des clips, les coordonnées locales après
  scale/translation, les transforms affines, les positions de glyphes et le
  comportement explicite des outlines indisponibles.
- `GPUPreparedTextLowererTest` couvre l’abaissement canonique, l’identité de
  strike après transform affine, les positions exactes, l’alpha/paint snapshot,
  le clip et la préservation des autorités de capability.
- `GPUPreparedTextRefusalMatrixTest` couvre les transforms non finis,
  singuliers et perspective, les clips refusés, les métriques/positions
  invalides et les représentations de glyphes non livrées.
- `GPUGradientColorFilterMaterialTest` et `GPUMultiStopGradientTest` vérifient
  les gradients bornés et leurs données de stops ; les oracles Surface
  vérifient les positions pixel-center, le clamp et les valeurs alpha.
- `GPUSaveLayerCompositeRegressionTest` vérifie la couche bornée, son clip,
  l’opacité SrcOver et les refus avant encodage pour les blends non supportés.

## Limites et refus

La perspective reste refusée avec un diagnostic stable avant exécution.
Shaping/fallback complets, emoji, color fonts et autres représentations de
glyphes restent dependency-gated ou explicitement refusés par la matrice de
refus ; aucun glyph ou font système implicite n’est ajouté. Les clips complexes
et les compositions `saveLayer` hors du sous-ensemble borné ne sont pas
promus. Aucun fichier `gpu-renderer-scenes` n’a été modifié.

## Vérification

Commandes exécutées avec succès :

```text
rtk ./gradlew --no-daemon :kanvas:test \
  --tests 'org.graphiks.kanvas.canvas.CanvasTest' \
  --tests 'org.graphiks.kanvas.canvas.ClipStackTest' \
  --tests 'org.graphiks.kanvas.canvas.SaveLayerRecContractTest' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedTextLowererTest' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUPreparedTextRefusalMatrixTest' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUGradientColorFilterMaterialTest' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUMultiStopGradientTest' \
  --tests 'org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest'
```

Résultat : 9 classes ciblées, 0 échec.

Le test d’inventaire `GPUWgpu4kCorePrimitivePipelineDescriptorTest` passe également
(32 tests). Les assertions historiques ont été alignées sur les 48 programmes
réellement présents dans le registre (aucun changement de production).

```text
rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests 'org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest' \
  --tests 'org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogOracleTest' \
  --tests 'org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbGradientCpuOracleTest' \
  --tests 'org.graphiks.kanvas.gpu.evidence.oracle.SurfaceSrgbSaveLayerSrcOverOpacityCpuOracleTest'
```

Résultat : 62 tests ciblés, 0 échec (20 catalogue, 25 oracle catalogue,
14 oracle gradient et 3 oracle saveLayer).
