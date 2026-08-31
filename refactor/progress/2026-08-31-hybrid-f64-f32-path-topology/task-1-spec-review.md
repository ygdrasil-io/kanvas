# Task 1 — Review Sol de conformité

Base : `b81c3859485202d01022fe01dccc0f619cc7a1ac`  
Head : `d1f29b829`

## Conformité à la spec

- `Spec: FAIL`
- Non vérifiable depuis le diff : l'antériorité réelle des exécutions RED sur les modifications de production n'est attestée que par le rapport (`task-1-report.md:22-54`).

## Points conformes

- Les cinq types de topologie source demandés reprennent les champs prescrits (`PathSourceTopologyF64.kt:3-51`).
- Le flattening transporte `sourceSegmentIndexI32` et `parameterF64`, et les paramètres des cuts sont interpolés sans réévaluation des coordonnées (`PathFlatteningF64.kt:19-23`, `PathIntersectionsF64.kt:29-33`, `PathIntersectionsF64.kt:295-296`).
- Les trois régressions utilisent uniquement l'appartenance `PathF32` et l'erreur publique exacte, avec des helpers privés (`PathOpsHybridTopologyF32Test.kt:10-88`).
- Une propagation explicite `Drop` existe pour la projection initiale d'un contour effondré (`PathOpsF32.kt:92-95`, `PathOpsF32.kt:269-273`, `PathOpsF32.kt:1400-1404`).
- L'adaptateur legacy est marqué pour suppression et ne déduit pas ses champs depuis des coordonnées projetées (`PathSourceTopologyF64.kt:130-145`).
- Le périmètre reste dans `:math:geometry` et les fichiers de suivi, avec l'adaptation mécanique autorisée de `PathMeasureF32`.

## Findings

### Critical

1. **Topologie source non intégrée au flux cible.** `buildArrangementF64` appelle encore directement `splitPathEdgesF64`, puis l'ancien arrangement (`PathOpsF32.kt:161-170`). `splitPathSourceTopologyF64` et son adaptateur restent isolés (`PathSourceTopologyF64.kt:53-58`, `PathSourceTopologyF64.kt:130-132`). Les spans et witnesses exacts ne traversent donc pas le pipeline. Construire `PathSourceTopologyF64` depuis les input edges avec le budget partagé, puis alimenter temporairement l'ancien arrangement via l'adaptateur.

2. **Une subdivision de flattening reste une autorité de span.** Chaque `PathSplitEdgeF64.sourceId` reçoit l'ID de l'arête aplatie (`PathIntersectionsF64.kt:290-291`), tandis que la fusion exige l'égalité de ce `sourceId` (`PathSourceTopologyF64.kt:69-80`). Deux sections contiguës d'un même segment source restent séparées. Fusionner selon opérande, contour, segment source, continuité paramétrique et frontières d'événements exacts, sans utiliser l'ID d'arête aplatie comme barrière.

### Important

1. **Provenance du seam implicite supprimée.** Le flattener ajoute une location `-1` (`PathFlatteningF64.kt:50-56`), mais la canonicalisation retire le dernier point et ne le reporte sur le premier que si celui-ci n'a aucune provenance originale (`PathOpsF32.kt:243-257`). L'arête cyclique prend ensuite le segment source de son endpoint (`PathOpsF32.kt:225-233`). Conserver une location seam distincte de la déduplication géométrique et l'attacher explicitement à l'arête de fermeture.

2. **La compaction reste une autorité et le chemin nullable subsiste.** Le bypass dépend d'un heuristique global et les autres cas appellent encore `compactProjectedPointWitnessRunsF64` (`PathOpsF32.kt:275-306`). Cette compaction supprime encore des sommets puis utilise `checkNotNull` (`PathOpsF32.kt:452-503`). Le nouveau rejet ne déclenche qu'avec plus d'un point-witness (`PathOpsF32.kt:666-693`). Retirer cette compaction comme autorité, classifier les conflits depuis witnesses/spans exacts et propager `Drop` sur toute la chaîne.

3. **Witnesses produits paire par paire, pas depuis les composantes exactes.** La double boucle ajoute immédiatement un `PointF64` ou `OverlapF64` par paire d'arêtes (`PathSourceTopologyF64.kt:97-126`). Un contact n-way devient plusieurs witnesses partiels. Collecter, grouper et trier sémantiquement les composantes exactes, puis créer un witness canonique avec tous les spans incidents.

4. **Contrôle quadratique hors budget candidat partagé.** `rejectProjectedRunsThatConsumeDistinctWitnessesF64` n'a pas de budget et exécute deux intersections par paire sans débit (`PathOpsF32.kt:666-678`). Passer le budget partagé, réutiliser le broad phase, et débiter avant le travail candidat.

5. **`PathInputEdgeF64` ne correspond pas à l'interface prescrite.** Les anciens noms et des valeurs par défaut silencieuses subsistent (`PathIntersectionsF64.kt:15-26`) au lieu de `idI32`, `contourIndexI32`, `startIdentityF64`, `endIdentityF64`, `startPointF64`, `endPointF64`, `windingDeltaI32` obligatoires. Appliquer l'interface exacte et adapter tous les appelants.

6. **`PathIntersectionsF64Test.kt` n'est pas modifié.** Le fichier obligatoire est absent du commit. Ajouter la couverture numérique/comportementale des nouveaux invariants sans assertions d'infrastructure ou de forme interne arbitraire.

### Minor

1. **Preuve RED JS non copiée exactement.** Le rapport paraphrase l'erreur en « Kotlin Preconditions » au lieu de consigner `Required value was null.` (`task-1-report.md:33-39`). Reporter la sortie exacte disponible ou qualifier honnêtement la limite de l'évidence historique.

## Verdict

- `Gate spec: NEEDS FIXES`

