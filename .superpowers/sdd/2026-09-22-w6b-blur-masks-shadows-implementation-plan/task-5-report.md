# Task 5 — W6b Mask Shader and Table Coverage

Base : `b7058de69a9849fd988ea5073bd3a78a285832ad`  
Commit prévu : `feat(gpu): execute w6b shader and table masks`

Statut : **DONE_WITH_CONCERNS** (les verdicts de test sont verts avant les
terminaisons natives 133, qui restent `UNKNOWN` sans attribution).

## RED → GREEN

1. Les nouveaux tests pixels publics ont d'abord atteint la terminaison
   `w6b.filter.native_execution_unimplemented` : les opérations `MaskShader`
   et `MaskTable` étaient dans le graphe, sans matérialisation native. Ils
   couvrent la shader solide, le gradient W5, une LUT inverse et une LUT
   identité, ainsi que parent/descendant `Picture`.
2. Le premier câblage de `MaskTable` révélait un RED causal : la coverage
   directe restait nulle, car le binding W4 n'était publié que pour `Blur`.
   GREEN : les trois masques W6b directs consomment le même producer typed W4
   déjà gelé, avant publication du plan. Aucune nouvelle lane, autorité
   Picture ou replanification renderer n'est introduite.
3. Le RED Picture a montré que la pass coverage du source agrégé était elle
   aussi dépourvue du binding W4. GREEN : la construction rattache le binding
   du draw W4 sélectionné au `FilterCoverageSourcePass` raw publié, tandis que
   la source W5 continue de lire l'output masqué. Le calcul est donc
   `coverage * alpha(source W5)` avant le unique source `SrcOver`, et le blend
   capturé n'est appliqué qu'au composite final.
4. Le RED historique utilise un fixture format 13 créé via l'API de capture et
   de sérialisation du dépôt, avec une table invalide de 255 entrées. Il se
   décode réellement, renvoie le diagnostic stable
   `w6b.filter.invalid_mask_filter.table_length`, ne modifie pas le sentinel
   de lecture et permet ensuite une nouvelle surface saine : refus atomique et
   recovery, sans decodeur parallèle, troncature, padding ou validation tardive.
5. GREEN final : le test shader/table exécute 7 assertions pixels publiques
   (solide, gradient, inverse, identité, parent/descendant, mutation
   post-capture, historique/recovery) et le test Picture 10 assertions
   (mémoire + wire replay). La mutation de la table après capture ne change pas
   les pixels : le snapshot planifié reste immuable.

## Correction d'autorité R16

La correction requise par le ruling R16 est exclusivement plan-owned :

- `PlanResourceRole.MaskTableData` publie une ressource buffer immuable de
  **256 bytes** avec `tableResourceId`, `entryCountI32 = 256`, génération
  `I64`, owner occurrence-local, usage/lifetime et budget `I64` pessimiste.
- La longueur est contrôlée avant allocation/publication. Une table historique
  de longueur invalide conserve le diagnostic stable et ne publie aucune
  allocation.
- `MaskShader` expose seulement une projection publique typée :
  `MaterialPlanRef`, ressource/uniform offset/capacité et `LayerMappingF64`
  déjà scellé depuis la seule autorité W5. Le type interne V1, toute material
  row supplémentaire, compiler, source lane ou autorité Picture parallèle
  restent absents.
- Le witness, `PlanPhysicalLayout`, validation et budget vérifient la resource
  LUT et le binding shader publiés. Le renderer copie/consomme seulement ces
  ressources gelées ; il ne planifie pas.

## Implémentation

- Ajout des opérations natives shader/table au frame plan et de snippets WGSL
  qui multiplient la coverage W4 avant le source W5. Le chemin table lit la
  storage LUT packée publiée par le plan ; il n'existe pas de LUT inline côté
  renderer.
- Les uniforms shader et les gradient stops réutilisent l'inventaire/resource
  mapping W5 gelé. Les coordonnées, mapping, extent stylé W5, clips exacts,
  schedule et terminaux Task 3/4 demeurent plan-owned.
- Les tests utilisent uniquement les APIs publiques (`Surface`, `Canvas`,
  `Picture`, `MaskFilter`) et les pixels RGBA8 sRGB prémultipliés. Aucun mock,
  fake device, reflection, compteur ou assertion de source statique n'a été
  ajouté.

## Gates exécutés

| Commande | Résultat vérifié |
| --- | --- |
| `rtk ./gradlew :render-ir:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew :gpu-plan:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew :gpu-renderer:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bMaskShaderTableSurfacePixelTest'` | 7 assertions `PASSED`, puis exit natif 133 : **UNKNOWN** |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6bFilterPictureTest'` | XML JUnit 10/0, puis exit natif 133 : **UNKNOWN** |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest'` | méthodes observées `PASSED`, puis exit natif 133 : **UNKNOWN** |
| `rtk git diff --check` | GREEN |

Les 133/134 sont conservés comme **UNKNOWN** conformément au brief, sans les
attribuer à la production, au test ou à l'environnement. Aucun travail de
fonts/codecs, GM, dashboard, renders/baselines/scores, `jpg-color-cube`, ni
suite globale Skia n'a été exécuté.

## Self-review / concerns

- La table ne peut ni être tronquée/paddée, ni être publiée avant son contrôle
  de longueur. Son contenu final reste l'unique snapshot immuable capturé.
- `MaskShader` consomme la row/material mapping W5 existante et ne recompilera
  pas de shader source. `MaskTable` ne voit qu'un operand/resource planifié.
- Les chemins parent/descendant, memory replay et wire replay gardent les
  frontières Picture et le blend final unique scellés. La coverage originale
  est maintenant explicitement publiée avant toute source maskée.
- Les 28 dettes W3/W4 full `gpu-plan` restent un contexte inchangé :
  `:gpu-plan:test` complet n'a pas été relancé ni reclassé.
- La revue Sol demandée par le brief n'a pas été lancée : l'instruction de
  tâche interdit explicitement tout sous-agent. Elle reste à faire hors de ce
  périmètre, sans remplacer la self-review ci-dessus.
- Le seul concern de livraison est le code hôte 133 des shards GPU, classé
  `UNKNOWN` sans attribution ; tous les résultats vérifiables listés ci-dessus
  sont conservés dans les sorties/XML avant cet arrêt.
