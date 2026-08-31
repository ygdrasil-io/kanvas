# Task 2 fix round 1 — Sol spec re-review

Base : `04f7accbb`  
Head : `2eae107ba`

## Verdict

- `Spec: FAIL`
- `Gate spec: NEEDS FIXES`

## Findings résolus

- Toutes les subdivisions de flattening restent des carriers sans nouvelle identité topologique ; les groupes complets et la compatibilité des rayons F64 sont conservés.
- Les tickets exacts `(inputEdgeIdI32, parameterBitsI64)` et l'atomisation n-way staggered remplacent la récupération par coordonnées/ULP.
- L'autorité est locale au witness et les claims sont validés avant publication des IDs/aliases.
- La borne ULP est strictement `< 16`, la nomenclature de l'index est corrigée et Booth canonicalise la séquence complète.
- Les tests publics distinguent désormais courbe et corde et couvrent les overlaps cross-operand.
- Aucun changement font, codec ou GM ; aucune exclusion ajoutée ; géométrie maintenue dans `:math:geometry`.
- Vérification fraîche JVM + JS complète : verte.

## Critical

1. **Les coïncidences projetées partielles ne sont ni matérialisées ni comptées.** `projectedOverlapClaimF64F32` n'accepte que les paramètres `0.0/1.0`, retourne `null` pour une borne intérieure et ne débite pas `maxIntersections` (`PathHybridTopologyF64F32.kt:489-511`; `PathIntersectionsF64.kt:411-412`). Construire transactionnellement les cuts intérieurs avec identité/provenance exacte, compter leurs groupes avant publication, puis sectionner les carriers.

2. **La disposition collapsed reste implicite et peut publier un résultat partiel.** Les endpoints collapsed sont aliasés puis le carrier est retiré avant contribution winding/classification ; un cycle sélectionné d'aire F32 nulle retourne ensuite `null` et est filtré (`PathArrangementF64F32.kt:145-172`, `:230-307`, `:1035-1051`). Conserver directions/incidences et appliquer `KEEP/DROP/REJECT` : `DROP` seulement pour le contour sélectionné entier dont l'aire source exacte est `<= 2^-45`, sinon `REJECT` avant émission.

## Important

1. **Le fallback représentant « même witness » manque.** `chooseRepresentativePointF32` ne reçoit pas le witness et rejette dès que les candidats diffèrent (`PathHybridTopologyF64F32.kt:673-695`). Sélectionner le candidat F32 sémantique du même witness et le valider contre toutes les incidences/orientations/embeddings.

2. **Le sweep est encore `O(P*E)` et sous-débité.** Chaque intervalle rescane tous les edges du composant malgré un preflight linéaire (`PathIntersectionsF64.kt:2107-2114`, `:2142-2149`). Maintenir un active set événementiel et débiter insert/remove/output avant travail.

3. **Les limites publiques sont encore appliquées trop tôt.** Le splitter applique `maxHalfEdges/2` aux edges/splits bruts avant agrégation (`PathIntersectionsF64.kt:352-360`, `:440-449`) et `maxVertices` est vérifié après construction de groupes/listes (`PathArrangementF64F32.kt:247-253`, `:591-605`). Appliquer les limites publiques aux comptes canoniques avant allocation ; utiliser une borne transitoire distincte si nécessaire.

4. **Des tests contournent encore l'API publique.** Ils reconstruisent `PathVertexIdentityF64`/`PathInputEdgeF64` et appellent `projectSourceEdgesThroughHybridF64F32` (`PathOpsHybridTopologyF32Test.kt:391-473`). Migrer vers `PathBuilder`/`PathOpsF32` et couvrir publiquement cut partiel, `maxIntersections` et conflit transactionnel lorsque constructible.

## Minor

- Les responsabilités restent concentrées dans trois fichiers de plus de 1 200 lignes. Après les correctifs fonctionnels, isoler claims, sweep et writer faciliterait l'audit sans ajouter de tests d'infrastructure.
