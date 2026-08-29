# W114 — gradient linéaire sous clip path et stroke square

## Résultat

La combinaison est maintenant admise par une extension bornée du contrat de
matérialisation. Un stroke `square/miter` mono-segment déjà abaissé en
`DirectTriangles` peut consommer un gradient linéaire `clamp` dans un clip
path Winding `StencilCoverage` 1x.

## Fixture

Le segment diagonal `(5.25,8.25) → (21.25,20.25)`, largeur 4, cap `square`,
join `miter`, AA désactivé, est intersecté avec le triangle Winding
`(7.25,6.25) → (30.25,6.25) → (7.25,29.25)`. Le matériau demandé est un
gradient linéaire rouge→bleu, `clamp`, de `(0,0)` à `(32,0)`.

L’inventaire confirme la route de stroke et le plan stencil Winding
(`IncrementWrap`/`DecrementWrap`, consommateur `NotEqual`). La correction
autorise uniquement le prédicat de stroke direct exact : un segment, cap
`butt` ou `square`, join `miter`, AA et effets désactivés, gradient `clamp` à
deux stops en interpolation sRGB, et clip stencil 1x. Les edge-fans, caps
round, chemins multi-segments et autres modes restent refusés.

Le readback GPU RGBA est comparé à un oracle CPU indépendant qui applique la
géométrie square dans l’espace device, le triangle Winding, puis
l’interpolation linéaire sRGB→linéaire→sRGB. La frame réussit avec un submit
et un readback uniques.

## Vérification

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.clamp linear gradient square miter stroke under winding path clip renders natively'
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

Le test négatif `clamp linear gradient round stroke under winding path clip remains refused`
confirme que le cap round conserve le refus `unsupported.core_primitive.material.path_stencil`.
La classe complète `GPUFramePathApiInventoryNativeSmokeTest` passe également.
Les classes renderer `GPUCorePrimitivePreparedFrameTaskListBuilderTest` et
`GPUCorePrimitiveNativeRouteTest` passent également.

La correction touche uniquement les autorités de ce lane direct exact dans
`GPUCorePrimitiveSemanticBuilder.kt` et
`GPUCorePrimitivePreparedFrameTaskListBuilder.kt`; aucun seuil, PNG ou
`gpu-renderer-scenes` n’a été modifié.
