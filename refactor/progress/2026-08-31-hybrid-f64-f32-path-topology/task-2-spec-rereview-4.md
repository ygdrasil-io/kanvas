# Task 2 — re-review de conformité round 4

## Verdict

`Spec: PASS`

- Critical : aucun.
- Important : aucun.
- Minor : aucun.

## Preuves

- Le sweep source F64 précède le tri F32 et la construction des faces. Son
  comparator angulaire utilise uniquement quadrant et `OrientationPredicateF64`,
  sans ID, label ni F32.
- Le groupe de rays égaux est scanné avant toute autorité F32 : égalité
  inter-bundles vers `path-f32-projection-collapse`, égalité intra-bundle
  admise.
- La reproduction éphémère du contre-exemple round 3 sur les classes compilées
  du head donne :

```text
round3 inter-bundle equal: REJECT path-f32-projection-collapse
intra-bundle equal: ACCEPT
round3 unequal control: REJECT path-f32-projection-collapse
```

- Le comparator forme un ordre angulaire total sur les classes de rays, avec
  `0` seulement pour une même direction exacte. Le résultat ne dépend donc ni
  de la stabilité du sort JVM/JS, ni d'un tie-break d'ID.
- La map de positions, les runs, le bitmap `seen` et la séquence modulo `B`
  vérifient contiguïté, rotation cyclique et non-reversal. La complexité reste
  `O(M log M + M)` et la mémoire `O(M+B)`.
- Le débit du sweep couvre le comptage, l'allocation des événements, le sort,
  les scans d'égalité et de dot product, la map/runs/seen/order et les paires
  adjacentes. Les `zipWithNext()` non débités ont disparu.
- Le troisième preflight de `buildOverlapWitnessIndexF64F32` contient
  explicitement `W`; avec `W=1,E=R=0`, les trois passes sont toutes débités.
- La jointure two-pointer débite les deux lookups puis `8*(R1+R2)` par
  arithmétique checked I64 avant la boucle. Une suite de witnesses communs non
  couvrants avance les deux curseurs sans masquer le witness couvrant suivant.
- Le test public vérifie `5_315` rejet / `5_316` succès et la permutation. Il
  est explicitement documenté comme non-régression, pas comme oracle global.

Vérification fraîche :

```text
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks --console=plain
BUILD SUCCESSFUL in 24s
61 actionable tasks: 61 executed

rtk git diff --check 52165381f..70897f2b2
exit 0

rtk git status --short
<vide>
```

Le diff reste dans `:math:geometry` et les documents de suivi : aucun font,
codec, GM, render, score ou exclusion; aucune fixture interne durable;
nomenclature I32/I64/F32/F64 conforme.

Dettes non closes : claims/cuts projetés intérieurs et disposition complète
`KEEP/DROP/REJECT` à Task 3; modèle global indépendant du budget à Task 5.
