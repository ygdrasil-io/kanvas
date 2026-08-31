# Task 1 — Review Sol de qualité

Base : `b81c3859485202d01022fe01dccc0f619cc7a1ac`  
Head : `d1f29b829`

## Qualité

- `Quality: FAIL`

## Forces

- L'interpolation du paramètre source est directe et sans réévaluation des coordonnées, puis appliquée aux deux extrémités de chaque split (`PathIntersectionsF64.kt:29-33`, `PathIntersectionsF64.kt:295-296`).
- La propagation `Drop` est explicite pour la projection initiale et conserve les contours frères (`PathOpsF32.kt:269-273`, `PathOpsF32.kt:1400-1404`).
- Les trois régressions vérifient uniquement le `PathF32` émis et les erreurs publiques (`PathOpsHybridTopologyF32Test.kt:22-27`, `PathOpsHybridTopologyF32Test.kt:40-46`, `PathOpsHybridTopologyF32Test.kt:49-54`).
- L'adaptation de `PathMeasureF32.kt` reste mécanique (`PathMeasureF32.kt:18-20`, `PathMeasureF32.kt:78-85`).

## Findings

### Critical

1. **La déduplication par coordonnées détruit les locations source.** Elle efface le point `t=0` de chaque segment après le premier et supprime le seam `sourceSegmentIndexI32 = -1` (`PathOpsF32.kt:231-233`, `PathOpsF32.kt:243-258`, `PathFlatteningF64.kt:53-56`). L'arête suivante prend le segment depuis `edge.end`, mais le paramètre de départ depuis `edge.start`, produisant typiquement `[1,1]` au lieu de `[0,1]`; la fermeture hérite du segment du `MoveTo`. Préserver les deux locations source lors d'une coïncidence géométrique, construire explicitement les intervalles du segment destination depuis `0.0`, et conserver une arête seam `-1` avec ses paramètres.

2. **La fusion des spans inverse la règle requise.** `sourceId` est l'ID de chaque arête aplatie (`PathIntersectionsF64.kt:291`), donc `next.sourceId == first.sourceId` interdit de fusionner les subdivisions d'un même segment (`PathSourceTopologyF64.kt:74`). À l'inverse, deux morceaux séparés par une intersection exacte ont le même `sourceId` et la même identité commune, donc ils sont fusionnés à travers l'événement (`PathSourceTopologyF64.kt:78-79`). L'adaptateur réutilise ensuite les mêmes identités globales pour chaque section (`PathSourceTopologyF64.kt:132-149`). Transporter la nature des cuts, fusionner seulement à travers une subdivision sans événement et conserver les identités propres de chaque section.

3. **Les contacts sont reconstruits par paires avec le kernel brut.** Un contact n-way devient plusieurs witnesses partiels; l'identité est choisie par recherche de coordonnées puis `maxByOrNull`, dépendante des ties (`PathSourceTopologyF64.kt:97-125`, `PathSourceTopologyF64.kt:174-192`). Les listes incidentes ne couvrent que deux arêtes et l'overlap seulement le span contenant son début. Exposer les composantes/cuts exacts de `splitPathEdgesF64`, créer un witness par composante et résoudre ses spans par identité/intervalle source, jamais par coordonnées.

### Important

1. **Le compactor reste accessible par heuristique globale.** Un ensemble mixte ou un run synthétique dans un contour avec un seul point original repasse par le compactor, qui peut supprimer le run; sa sortie nullable reste sous `checkNotNull` (`PathOpsF32.kt:278-300`, `PathOpsF32.kt:480-503`). Décider par witness/run à partir de la provenance explicite, refuser toute suppression synthétique non certifiée et propager `Drop` sur toute la chaîne.

2. **Travail candidat non borné.** Le rejet parcourt toutes les paires avec deux intersections robustes sans débit (`PathOpsF32.kt:666-681`). La topologie débite une fois par paire puis effectue des scans complets non débités des split edges et spans (`PathSourceTopologyF64.kt:98-125`, `PathSourceTopologyF64.kt:174-192`). Réutiliser le broad phase canonique, débiter avant chaque opération et indexer identités/spans une fois.

3. **IDs dépendants des labels et de l'ordre.** Les IDs séquentiels dépendent de `contourIndexI32`, `sourceSegmentIndexI32`, de l'ordre stable des ties et de `sourceId` (`PathSourceTopologyF64.kt:59-60`, `PathSourceTopologyF64.kt:74`, `PathSourceTopologyF64.kt:85-86`). Définir un ordre sémantique total basé sur géométrie canonique, winding et provenance indépendante des labels, puis traiter les ties avant attribution.

4. **La production contourne la nouvelle topologie et les tests ne peuvent pas le voir.** `PathOpsF32` continue d'appeler `splitPathEdgesF64` (`PathOpsF32.kt:161-170`); les trois tests ne couvrent que la projection synthétique (`PathOpsHybridTopologyF32Test.kt:9-54`). Faire passer l'arrangement legacy par la topologie/adaptateur et ajouter des oracles end-to-end pour subdivision courbe, seam, contact multi-arêtes, permutation/relabeling, provenance mixte et limite de candidats.

### Minor

1. **Preuve RED incomplète.** Le rapport ne donne pas le message exact du troisième échec JVM/JS (`task-1-report.md:23-37`). Consigner la preuve exacte disponible.

## Vérifications ciblées

- Aucune.

## Verdict

- `Gate qualité: NEEDS FIXES`

