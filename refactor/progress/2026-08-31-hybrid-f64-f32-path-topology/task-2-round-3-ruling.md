# Task 2 round 3 — ruling on projected interior cuts

## Context

Les re-reviews Sol round 2 demandent une fixture publique produisant une borne
de coïncidence projetée strictement intérieure à une section, puis une
matérialisation hybride de ce cut et son débit `maxIntersections`.

La règle de diagnostic limitait la recherche à trois hypothèses publiques afin
d'éviter d'ajouter une voie de production morte :

1. quad vs cubic degree-elevated, offsets `+/-2^-25` autour de zéro : RED, mais
   deux witnesses exacts `[1,6]` couvrent la paire ; l'autorité locale unique
   requise n'existe pas ;
2. ovals tangents asymétriques translatés vers `3_000` : GREEN, aucune borne
   intérieure observable ;
3. mêmes tangences asymétriques à grande échelle autour de `-1_000_000` :
   GREEN, aucune borne intérieure observable malgré des inputs F32 distincts.

L'invariant observé est que le registre source atomise les endpoints de contact
exacts avant la topologie hybride. Les rails locaux valides restent sur des
endpoints de section `0/1`; des subdivisions divergentes introduisent un autre
witness exact et deviennent ambiguës, donc rejetables.

## Ruling

- Task 2 n'ajoute pas de materializer intérieur sans fixture publique valide.
- Le rejet conservateur des bornes hybrides non identifiées reste en place.
- Le plan validé assigne explicitement à Task 3, étapes 3–4, le proposal/commit
  des claims et `PathBoundaryDisposition KEEP/DROP/REJECT`. Les findings sur les
  cuts projetés intérieurs, leur débit `maxIntersections` et la disposition
  collapsed complète sont transférés à cette tâche avec les preuves ci-dessus.
- Task 2 round 3 continue sur les blockers reproductibles de son arrangement :
  ordre cyclique des bundles selon les directions per-incidence F64, lookup
  d'autorité d'overlap non cartésien et débit exact du travail local ajouté.

Ce ruling ne déclare pas les findings approuvés ; il évite une implémentation
inatteignable en Task 2 et préserve leur vérification dans la tâche qui les
possède selon le plan.
