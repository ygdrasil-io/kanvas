# W50 — registre des descriptors `RuntimeEffect`

W50 ferme le premier morceau du contrat des runtime effects : un effet GPU
doit être identifié par un descriptor Kanvas enregistré, versionné et associé
à une route déclarée. Le registre expose maintenant un snapshot déterministe,
les lookups par ID et version, le kind et l’oracle CPU associés, ainsi qu’une
validation WGSL parser-backed. Un ID inconnu, une version absente ou un module
non enregistré ne produit pas de source GPU.

Trois descriptors représentatifs sont reliés explicitement à leurs
implémentations :

- `runtime.simple_rt@1` : couleur uniforme et payload `vec4<f32>` ;
- `runtime.spiral_rt@1` : coordonnées locales, centre/couleurs/paramètres ;
- `runtime.intrinsics_matrix@1` : uniforme scalaire, matrice `mat4x4` et vecteur.

Les trois exposent un kind `Material`, leur module WGSL statique et un
comportement CPU de validation. Les tests couvrent le snapshot, l’ID/version,
le kind, la validation parser-backed, les payloads CPU et le refus d’un ID
inconnu. Les routes d’exécution enregistrées existantes restent couvertes par
les tests `RegisteredRuntimeEffect*`.

Cette vague ne prétend pas promouvoir un nouveau rendu pixel de ces trois
effets : l’intégration complète dans la route public-Surface et les scènes
CPU/GPU pixel-validées seront traitées par les vagues runtime-effect suivantes.
Les effets SkSL/WGSL arbitraires, les children et les codecs/fonts restent
explicitement hors contrat.

Vérifications exécutées :

```text
./gradlew --offline :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.runtimeeffects.*' :kanvas:test --tests '*RuntimeEffect*'
./gradlew --offline :gpu-renderer:test --tests '*KanvasRuntimeEffectRegistryW50Test' --tests '*RegisteredRuntimeEffect*'
```

Résultat : `BUILD SUCCESSFUL`; tous les tests ciblés passent. Aucun fichier
`gpu-renderer-scenes` n’est modifié.
