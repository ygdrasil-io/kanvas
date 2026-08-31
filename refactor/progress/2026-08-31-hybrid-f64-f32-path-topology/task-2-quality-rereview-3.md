# Task 2 — re-review qualité round 3

## Verdict

`Quality: FAIL`

## Critical

Aucun blocker fonctionnel Task 2 supplémentaire reproduit par cette revue.
Le blocker d'equal rays F64 du gate de conformité reste toutefois bloquant pour
le round.

## Important

### Le sweep angulaire est sous-débité localement

L'enveloppe annoncée pour le sweep ne couvre pas tout le travail par rayon :
visite, test de nullité, dot product, copie et allocation d'événement. Après le
préflight final, `zipWithNext()` alloue encore `B-1` paires et exécute visites et
prédicats exacts `sameHybridOutgoingRayF64F32` sans débit dédié. Le test public
high-valence traverse ce chemin sans vérifier cette frontière.

### La jointure d'autorité d'overlap est fonctionnelle mais sous-débitée

La jointure réserve `R1 + R2 + 4`. Pour `n` witnesses communs non couvrants
avant le witness couvrant, elle répète comparaisons d'IDs, prédicat d'edge et
deux chaînes de couverture. Le déficit croît en `Theta(n)`; le constant `+4` ne
couvre pas ces prédicats. Cette dette est locale à Task 2 et distincte du modèle
global transféré à Task 5.

## Minor

Le diff historique `edc761be3..52165381f` contient des espaces finaux dans les
deux documents de review round 2. Ils doivent être nettoyés dans le prochain
commit documentaire.

## Recontrôle du round 2

- Tri angulaire fonctionnel nominal : directions per-incidence F64, comparator
  déterministe, contiguïté cyclique et rejet des inversions présents. La revue
  de conformité a isolé séparément le cas equal-ray non résolu.
- Overlaps : corrigés fonctionnellement. Index par edge, listes triées par
  witness et jointure two-pointer; un premier witness commun non couvrant
  n'empêche plus d'atteindre le suivant.
- Cuts intérieurs, `maxIntersections` hybride et disposition collapsed complète :
  transférés à Task 3 conformément au ruling.
- Modèle global indépendant du budget : transféré à Task 5.
- Aucun overflow non contrôlé trouvé dans les nouveaux compteurs I64.

Vérification fraîche du reviewer :

```text
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL in 24s
61 actionable tasks: 61 executed
```

Le worktree était propre. Aucun changement font, codec, GM, render, score ou
exclusion; placement `:math:geometry`, nomenclature F32/F64/I32/I64 et tests
comportementaux publics conformes.
