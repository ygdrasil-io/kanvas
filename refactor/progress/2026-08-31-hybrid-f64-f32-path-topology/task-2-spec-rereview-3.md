# Task 2 — re-review de conformité round 3

## Verdict

`Spec: FAIL`

## Critical

### Les rays F64 exactement égaux entre bundles distincts ne sont pas rejetés

Le sweep emploie bien les directions per-incidence F64 et vérifie la contiguïté
cyclique, la rotation et l'absence de reversal. En revanche, son comparator
départage une égalité F64 avec `embeddingPositionI32` dans
`PathArrangementF64F32.kt`, puis le dernier garde compare uniquement les
directions d'embedding F32.

La reproduction read-only utilise trois bundles F32 ordonnés par `(1,0)`,
`(1,0.5)`, `(1,1)`, avec les rays source F64 `(1,0.2)`, `(1,0.2)`, `(1,1)`.
Les deux premiers bundles sont distincts, sans coïncidence validée, mais ont le
même ray F64 exact. Le validateur accepte ce cas :

```text
ACCEPTED unresolved equal F64 rays in distinct F32 bundles
```

Le contrôle négatif `(1,0.4)`, `(1,0.2)`, `(1,1)` rejette avec
`path-f32-projection-collapse`, ce qui isole le défaut. Cela contredit
explicitement l'invariant du plan « unresolved equal rays reject » et redonne
au F32 une autorité angulaire. Le test public high-valence utilise des rays
littéraux où F64 et F32 coïncident et ne couvre pas cette ambiguïté.

## Important

### Le nouvel index d'overlap reste sous-débité localement

`buildOverlapWitnessIndexF64F32` parcourt `witnessesF64` trois fois. Les deux
premiers parcours débitent chacun `W`, mais le préflight précédant le troisième
ne réserve que `2E + R`. Avec un unique `PointF64`, donc `W=1`, `E=R=0`, la
reproduction donne :

```text
budget=1 -> path-candidate-limit
budget=2 -> SUCCESS (three witness-list visits executed)
```

Une visite complète est donc exécutée sans débit. Il s'agit de la dette locale
Task 2 du round 2, distincte du modèle global transféré à Task 5.

## Recontrôle du round 2

- Sweep angulaire F64 : partiellement fermé, donc `FAIL`. La complexité
  `O(M log M + M)`, les directions per-incidence, la contiguïté cyclique et
  l'absence de reversal sont présentes; les equal rays non résolus restent
  acceptés par autorité F32.
- Lookup d'autorité d'overlap : `PASS` fonctionnel. Index par input edge,
  listes triées par witness, jointure two-pointer sans produit cartésien et
  couverture exacte d'intervalle.
- Débit du travail local : `FAIL`, reproduction ci-dessus.

Les tests publics JVM/JS et `git diff --check` du worktree passent. Aucun fichier
font, codec ou GM/exclusion n'est modifié; la géométrie reste dans
`:math:geometry`, la nomenclature F32/F64/I32/I64 est respectée et aucun test
d'infrastructure ou de structure interne n'a été ajouté.

Les cuts projetés intérieurs, leur `maxIntersections` et la disposition complète
`KEEP/DROP/REJECT` restent à Task 3. Le modèle global indépendant du budget
reste à Task 5. Ces transferts ne sont pas utilisés comme blockers Task 2.
