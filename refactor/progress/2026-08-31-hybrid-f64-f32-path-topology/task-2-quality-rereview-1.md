# Task 2 fix round 1 — Sol quality re-review

Base : `04f7accbb`  
Head : `2eae107ba`

## Verdict

- `Quality: FAIL`
- `Gate qualité: NEEDS FIXES`

## Findings résolus

- Transport exact des identités d'overlap, chord replacement, validation avant IDs/aliases et Booth complet.
- Groupes de carriers conservés et limites finales présentes dans le DCEL hybride.
- Aucun changement font/codec/GM, aucune exclusion, géométrie dans `:math:geometry`.

## Critical

1. **La vraie coïncidence locale n'est plus testée et échoue encore.** La fixture a été déplacée de `e=2^-25` à `2^-22`, gardant les endpoints F32 distincts (`PathOpsHybridTopologyF32Test.kt:49`). Restaurée à `2^-25`, elle lève `path-f32-projection-collapse` à cause de la compatibilité stricte d'un carrier unique (`PathArrangementF64F32.kt:660`). Restaurer le RED ; conserver toutes les directions/incidences pour la classification et le carrier canonique seulement pour la géométrie écrite.

2. **Un cycle sélectionné d'aire F32 nulle est toujours supprimé silencieusement** (`PathArrangementF64F32.kt:164`, `:1035`). Décider `KEEP/DROP/REJECT` atomiquement ; `DROP` seulement pour un contour source entier sous le seuil exact `2^-45`, sinon rejeter avant toute sortie.

3. **Le sweep atomique est quadratique et non débité.** Chaque intervalle rescane tous les carriers (`PathIntersectionsF64.kt:2142`). Remplacer par un vrai sweep avec active set, débit checked avant événement/predicate/output, complexité `O(S log S + K)` et longue fixture staggered.

## Important

1. **Les représentants ne sont pas vraiment évalués per-incidence.** Chaque cut reprend `component.canonicalPoint` puis le sélecteur compare des candidats déjà identiques (`PathIntersectionsF64.kt:423`; `PathHybridTopologyF64F32.kt:674`). Préserver chaque évaluation exacte, sélectionner/valider le candidat canonique du même witness.

2. **Les claims partiels sont limités à `0/1`, sans cut intérieur ni `maxIntersections`** (`PathHybridTopologyF64F32.kt:494`). Proposer cuts/identités transactionnellement, compter les groupes n-way/bornes et publier après validation globale.

3. **`maxHalfEdges` reste appliqué aux splits bruts** (`PathIntersectionsF64.kt:352`). Deux rectangles identiques avec limite 8 échouent malgré un DCEL canonique final de 8. Distinguer stockage transitoire et limite publique finale.

4. **Les incidences collapsed sont aliasées puis supprimées localement**, sans secteurs ni disposition de contour (`PathArrangementF64F32.kt:230`, `:278`). Transporter directions/incidences et couvrir le chemin intrinsic collapse.

5. **Le budget contient encore du travail avant débit et son oracle reflète l'implémentation.** `sumOf`/`fold` précèdent certains preflights (`PathHybridTopologyF64F32.kt:118`, `:824`) ; `4_329` additionne les mêmes constantes de phase (`PathOpsHybridTopologyF32Test.kt:506`, `task-2-report.md:192`). Débiter avant scan et dériver l'oracle des candidats géométriques attendus, avec séries longues, permutations et JVM/JS.

6. **La tolérance de normalisation peut produire `NaN`.** Une magnitude Double finie supérieure à `Float.MAX_VALUE` devient `Infinity` lors de la conversion, puis contamine le calcul (`PathNormalizationF64.kt:44`). Borner explicitement au domaine F32 fini et tester extrêmes/subnormaux.

## Tests et rapport

- Les suites JVM focalisées (89 tests) et JS passent, mais les reproductions adversariales ci-dessus échouent.
- Le rapport affirme encore à tort que le point witness local réussit réellement, que les bornes partielles sont exactes et que la sélection est per-incidence (`task-2-report.md:80`, `:146`, `:150`). Corriger les claims documentaires après le code.

## Minor

- Extraire claims, overlap sweep et canonical trace en unités pures après correction fonctionnelle ; ne pas tester leur structure interne.
