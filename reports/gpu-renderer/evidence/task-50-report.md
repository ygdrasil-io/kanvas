# W74 — première micro-vague GM burn-down : classification des refus

## Décision

Cette micro-vague reste un audit test-only. Les résultats W73 ne montrent pas
une route de clipping absente que l'on puisse activer sans changer le contrat
GPU : les chemins courbes rencontrent soit le budget du stencil edge-fan, soit
la combinaison de layouts qui est explicitement refusée par le preflight.
L'implémentation d'une nouvelle route dans Kanvas aurait donc masqué un
problème de capacité (et non corrigé un bug local démontré).

La prochaine candidate reste `PathClipCoverage`, mais avec un cas borné :
deux contours finis, clip `Intersect`/`Difference`, sans AA scalaire promue et
sans mélange de layouts. Elle ne sera implémentée que si ce cas produit une
preuve CPU et GPU reproductible.

## Classification W73

Les occurrences ci-dessous sont comptées dans les XML de résultats du replay
W73 (elles peuvent contenir plusieurs occurrences pour un même GM). Elles
servent à choisir la prochaine route, pas à prétendre mesurer une couverture
unique.

| Première cause observée | Occurrences | Décision |
|---|---:|---|
| Contrat d'entrée stroke invalide (`unsupported.stroke.width_invalid`) | 114 | Refus correct : ne pas élargir la géométrie à partir d'une largeur nulle/non-finie. |
| Limite de matériau gradient (`unsupported.material.mapping.linear_gradient_stop_count`) | 113 | Dépend du contrat matériau borné ; hors PathClip. |
| Capacité pipeline absente (`unsupported.pipeline.capability_missing`) | 108 | Préflight correct ; nécessite une capacité WebGPU réellement disponible. |
| Budget de triangulation path (`geometry.path.fan_budget_exceeded`) | 92 | Limite mémoire/indices explicite ; pas un simple oubli de dispatch. |
| Source matériau non implémentée (`unsupported.material.source_unimplemented`) | 86 | Feature manquante, mais pas une route de clip isolée. |
| AA coverage scalaire non promue (`unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted`) | 70 | Bloqueur architectural connu ; aucune promotion implicite. |
| Composite/paint non supporté (`unsupported.composite.paint`) | 42 | Route de compositing distincte. |
| Liaison image native absente (`unsupported.image.native_binding`) | 35 | Dependency-gated ; hors périmètre non-font de cette micro-vague. |

Les autres refus fréquents sont les budgets de stroke, les modes de gradient,
les caps/joins et les validations de géométrie. Ils ne changent pas le choix
de cette vague : le plus gros cluster path/clip est un budget explicite, et
non une route d'intersection manquante.

## Vérification du code

Le code actuel confirme les limites observées :

- `PathTessellator.validateStencilEdgeFanBudget` refuse avant allocation avec
  `geometry.path.fan_budget_exceeded` ;
- `GPUOpMapper` propage ce refus au niveau de l'opération au lieu de supprimer
  silencieusement le dessin ;
- les contrats de clip exposent déjà `Intersect` et `Difference`, ainsi que les
  producteurs analytiques rect/RRect ;
- les tests `GPUPathClipRegressionTest` et `CurvedClipGmSurfaceRefusalEvidenceTest`
  conservent les refus de path-stencil comme findings explicites.

Modifier le budget par défaut, désactiver le preflight ou rebaseliner les PNG
ne fournirait pas de preuve de rendu correcte. Aucun de ces changements n'a
été fait.

## Commandes

```text
rtk rg -o "(?:geometry|unsupported|invalid|failed|stale)\.[A-Za-z0-9_.-]+" \
  integration-tests/skia/build/test-results/test -g '*.xml' \
  | sed 's/.*://g' | sort | uniq -c | sort -nr | head -45

rtk rg -n "width_invalid|width_budget|fan_budget_exceeded|scalar_aa_not_promoted" \
  gpu-renderer kanvas integration-tests -g '*.kt'
```

Le replay W73 reste la preuve d'exécution de référence : dashboard généré avec
533 pass, 7 scores sous seuil et 29 no-score ; les refus et le code JVM 133 du
runner complet sont conservés comme findings. Cette vague n'ajoute donc ni
PNG, ni seuil, ni promotion.

## Conclusion

Résultat : aucun correctif de renderer justifié par les données W73 dans cette
micro-vague. Le suivi prioritaire est une reproduction GPU ciblée de
`PathClipCoverage` borné ; si elle atteint encore le path-stencil ou le budget
AA, le refus restera la politique correcte jusqu'à une décision d'architecture
sur cette capacité.

## Test ciblé et finding séparé

Les vérifications ciblées ont été exécutées séparément :
`PathTessellatorTest` a donné 27/27 réussites ; le lot Kanvas
`GPUPathClipRegressionTest` + `GPUPreparedSurfaceProductRouterTest` a donné 20
tests, 19 réussites et 1 échec ; le lot integration-tests
`CurvedClipGmSurfaceRefusalEvidenceTest` + `SkiaGmInventoryTest` a donné 17/17
réussites. Le test Kanvas en échec est le cas
de blend path destination-read déjà présent ; il termine sur
`failed.native-core-primitive.frame-global-materialization` avec
`An indexed CorePrimitive run requires an exact bind group per component
identity`. Ce n'est pas un refus de clipping attendu : c'est un finding de
materialization natif (probable désaccord entre l'identité de composant et les
bind groups du frame pool). Il doit être corrigé dans une vague dédiée avant de
présenter ce cas comme preuve GPU positive. Aucun changement opportuniste n'a
été appliqué ici.

Le test d'évidence `CurvedClipGmSurfaceRefusalEvidenceTest` était également
désynchronisé : le code produit désormais `geometry.path.fan_budget_exceeded`
pour `clipcubic` et `clippedcubic`, alors que le test attendait deux anciens
codes. Les attentes ont été réalignées sur le diagnostic effectivement émis
(sans modifier le renderer), puis le test ciblé a réussi.

Commande exécutée :

```text
./gradlew --offline --no-daemon --max-workers=1 :gpu-renderer:test \
  --tests '*PathTessellatorTest'
./gradlew --offline --no-daemon --max-workers=1 :kanvas:test \
  --tests '*GPUPathClipRegressionTest' \
  --tests '*GPUPreparedSurfaceProductRouterTest'
./gradlew --offline --no-daemon --max-workers=1 :integration-tests:skia:test \
  --tests '*CurvedClipGmSurfaceRefusalEvidenceTest' \
  --tests '*SkiaGmInventoryTest'
```

Les deux premiers lots ont donc le résultat 27/27 puis 19/20 ; le troisième lot
réussit (1 test curved-clip et 16 tests inventory). Le finding materialization
reste reproductible dans
`kanvas/build/test-results/test/TEST-org.graphiks.kanvas.surface.gpu.GPUPathClipRegressionTest.xml`.
