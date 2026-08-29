# W31 — LinearGradient REPEAT borné

## Décision de périmètre

L'inventaire de la base W30 a établi que le chemin natif `LinearGradient`
à deux stops avec `TileMode.REPEAT` était déjà livré : capability et
descripteur, variantes WGSL statiques directes/analytique, identités de
pipeline/session et materializer. Cette vague ne duplique donc pas cette
implémentation ni les routes `CLAMP` existantes.

Elle ferme le contrat public autour de cette tranche : un `FillRect` REPEAT
continue d'être un rendu GPU vérifié, tandis que `MIRROR` est refusé avant
la soumission. Aucun code de `gpu-renderer-scenes`, Ganesh, Graphite, SkSL
dynamique ou windowing natif n'est impliqué.

## Preuves

Le cas public existant `repeat-gradient-refusal` (nom historique, attente
`ShouldRender`) a été rejoué sur WebGPU headless. Son oracle CPU indépendant
fait l'interpolation en linéaire prémultiplié puis le stockage sRGB. Le
résultat est `100.0 %`, `0` pixel différent, différence de canal maximale
`0`, avec une soumission, un bind de pipeline et un draw.

Le nouveau cas public
`mirror-linear-gradient-fillrect-refusal` emploie la même géométrie `FillRect`
mais `TileMode.MIRROR`. Il refuse avant soumission avec la raison stable
`unsupported.material.gradient_tile_mode_unsupported`. Ce code est celui
émis par la route Surface préparée, et non le code historique du mapper
legacy.

## Vérification

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:test \
  --tests org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalogTest
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence \
  -Pscene=repeat-gradient-refusal -PsourceCommit=<commit>
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence \
  -Pscene=mirror-linear-gradient-fillrect-refusal -PsourceCommit=<commit>
```

Les deux bundles ont été générés depuis
`5c6e36c4517ac85c11ff0d8fccf5943964c48e2b`, vérifiés, puis promus. La
preuve REPEAT est un rendu GPU pixel-validé ; la preuve MIRROR est promue
comme refus stable séparé avant soumission.
