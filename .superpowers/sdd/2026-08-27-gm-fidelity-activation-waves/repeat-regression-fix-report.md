# Correctif de régression Repeat — vague 3

Date : 2026-08-27  
Branche : `codex/gm-activation-wave-11`

## Cause

La validation bornée du prepared material v2 (`clamp`, deux arrêts, matrice
affine) était appelée par des points de décision partagés : mapping `Shader`,
analyse FirstRoute et garde Surface. Un `LinearGradient` `repeat`, pourtant
représentable par le chemin legacy CorePrimitive, était donc transformé en
`Unsupported` ou refusé avant la sélection de
`DirectLinearGradientRepeat`/`AnalyticLinearGradientRepeat`.

## Correctif

* Le mapper conserve un descripteur linéaire `repeat` pour la voie legacy ; il
  n'essaie pas de le faire entrer dans v2.
* Les gardes partagées diffèrent la validation des gradients linéaires aux
  validations spécifiques de route. Seul `FillRect` sans filtre peut admettre
  `repeat`; `FillRRect`, `FillPath` et `FillRect` filtré le refusent toujours
  avec `unsupported.material.gradient_tile_mode_unsupported`.
* `GPULinearGradientMaterialLowering` et `GPUPreparedMaterialProgram`
  restent volontairement v2-only : `repeat` y est refusé avec ses diagnostics
  v2 (`unsupported.material.gradient_tile_mode` et
  `unsupported.material.mapping.linear_gradient_tile_mode`). La route clampée
  à deux arrêts garde `fs_main`, le payload
  `GradientBlock.v2...@group1.binding0` et son ABI de 576 octets.

## Preuves de non-régression

Commande forcée :

```text
./gradlew --no-daemon --rerun-tasks :gpu-renderer:test \
  --tests '...FirstRoutePlannerTest.linear repeat gradient routes natively while adjacent tile modes and families remain refused' \
  --tests '...LinearGradientMaterialLoweringTest' \
  --tests '...GPUPreparedMaterialProgramTest' \
  --tests '...GPUCorePrimitivePayloadContractsTest.linear repeat selects cache-distinct direct and analytic structural programs with unchanged ABIs' \
  --tests '...GPUCorePrimitiveNativeShaderTest.linear repeat shaders are parser validated and wrap positive and negative raw coordinates' \
  :kanvas:test --tests '...GPUMaterialMapperTest' \
  --tests '...GPUPreparedSurfaceProductNativeSmokeTest.internal prepared Surface route wraps the post first cycle pixel natively' \
  --tests '...GPUPreparedSurfaceProductNativeSmokeTest.Surface render wraps the post first cycle repeat gradient pixel'
```

Résultat : `BUILD SUCCESSFUL` (53 s). Les deux smoke tests Surface ont rendu
le pixel post-cycle `t_raw = 1` rouge (`repeat`, et non bleu comme `clamp`),
avec une opération dispatchée et zéro refus.

Commande forcée supplémentaire :

```text
./gradlew --no-daemon --rerun-tasks :gpu-renderer:test \
  --tests '...GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.linear gradients materialize exact direct and analytic slabs with distinct binding components' \
  --tests '...GPUWgpu4kCorePrimitivePipelineDescriptorTest.linear repeat owns direct and analytic structural shader variants'
```

Résultat : `BUILD SUCCESSFUL` (54 s). Le materializer conserve les variants
Repeat distincts et les ABIs CorePrimitive inchangés : direct 592 octets,
analytic 656 octets. Les clés de pipeline structurelles sont distinctes de
`clamp`; les WGSL Repeat validés par parser utilisent `t_raw - floor(t_raw)`.

Les tests de mapper fixent aussi un `repeat` à trois arrêts comme descripteur
legacy conservé, alors que `mirror` et le `clamp` à trois arrêts restent
refusés par l'admission prepared v2.

## Portée et réserves

Aucun fichier sous `gpu-renderer-scenes` n'a été modifié. Les avertissements
Gradle/Kotlin existants restent hors de cette correction ; aucun échec de test
ni régression de l'ABI v2 clampée n'a été observé.
