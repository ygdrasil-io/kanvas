# Task 2 fix round 2 — Sol spec re-review

Base : `2eae107ba`
Head : `edc761be3`

## Verdict

- `Spec: FAIL`
- `Gate spec: NEEDS FIXES`

## Clos

- Registre source atomisé par event sweep + active set, avec tickets exacts et cuts avant splits.
- Point source canonique séparé des évaluations per-incidence.
- `maxVertices`/`maxHalfEdges` appliqués au DCEL canonique final.
- Normalisation extrême finie, tests publics/numeric, portée `:math:geometry`, nomenclature et exclusions conformes.

## Critical

1. **Les coïncidences projetées partielles ne sont toujours ni découpées ni comptées.** Seuls les paramètres `0/1` reçoivent une identité ; les bornes intérieures sont rejetées (`PathHybridTopologyF64F32.kt:587-638`, `:1450-1454`). `limitsI32` n'est pas utilisé dans la topologie hybride et `maxIntersections` ne compte que les groupes source (`PathIntersectionsF64.kt:465-466`). Construire transactionnellement les groupes d'endpoints projetés, cuts/identités/provenance et carriers sectionnés ; compter les nouveaux groupes avant IDs/aliases/DCEL.

2. **La disposition collapsed n'est pas résolue avant les retours vides.** Les collapses non intrinsèques rejettent tôt, les sections terminales ne peuvent pas atteindre Drop, et l'extraction peut retourner vide avant classification (`PathHybridTopologyF64F32.kt:348-358`, `:511-549`; `PathArrangementF64F32.kt:65-93`, `:1221-1401`). Les directions `-d/+d` ne représentent pas les vrais secteurs voisins. Classifier tous les contours avant DCEL : Keep représentable ; Drop seulement contour complet sous `2^-45` ; Reject collapse partiel/significatif. Transporter les vrais rays précédent/suivant.

## Important

1. **Validation angulaire sous-budgétée et fondée sur l'embedding F32.** Le degré² rescane toutes les directions source et ne détecte pas correctement l'ordre mutuel des bundles (`PathArrangementF64F32.kt:1550-1644`). Utiliser les directions exactes per-incidence, un tri/sweep angulaire unique `O(N log N + K)` et un débit sur toutes les directions.

2. **Lookup d'autorité d'overlap quadratique.** Le produit cartésien des incidences par edge (`PathHybridTopologyF64F32.kt:1093-1123`) doit devenir un index par paire/intervalle ou une jointure two-pointer de listes triées.

3. **Couverture publique incomplète.** Ajouter une borne projetée réellement intérieure et des collapses sous/égal/au-dessus du seuil avec une grande composante stabilisant la normalisation. Réduire les claims du rapport tant que ces cas ne passent pas.
