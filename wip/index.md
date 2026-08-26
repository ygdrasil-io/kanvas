# WIP — ordre d'exécution de l'élargissement des tests GPU

> Dossier de travail temporaire. Aucun fichier de `wip/` n'est une source de
> vérité ni une preuve promue : le code Kotlin, les tests exécutés et les
> artefacts de preuve vérifiés le sont. Supprimer ce dossier après absorption
> de chaque lot dans le code, les tests et les preuves promues.

## Règle de lecture

[coverage-map.md](coverage-map.md) est la liste source-derived des APIs et
sous-types publics à classer, avec un unique lot propriétaire. Les briefs
numérotés détaillent les groupes de tests. Ils n'autorisent jamais à annoncer
un support : une route ne devient supportée qu'après test de la route publique
`Surface`, oracle/référence applicable, capture GPU, diagnostics,
diff/statistiques et politique de fallback vérifiée.

## Lots (inventaires, pas autorisations)

| Lot | But | Brief |
| --- | --- | --- |
| 00 | Stabiliser le catalogue, les artefacts, les oracles et les refus. | [00-evidence-and-catalog.md](00-evidence-and-catalog.md) |
| 10 | Tester l'état Canvas et la géométrie rectangulaire déjà basse niveau. | [10-canvas-state-and-basic-geometry.md](10-canvas-state-and-basic-geometry.md) |
| 20 | Tester path, stroke, coverage et clips non rectangulaires. | [20-paths-strokes-and-clips.md](20-paths-strokes-and-clips.md) |
| 30 | Tester paint, blend, couleur et gradients. | [30-paint-color-and-gradients.md](30-paint-color-and-gradients.md) |
| 40 | Tester images, layers et image filters. | [40-images-layers-and-filters.md](40-images-layers-and-filters.md) |
| 50 | Tester runtime effects enregistrés, WGSL, reflection et layouts. | [50-runtime-effects-and-wgsl.md](50-runtime-effects-and-wgsl.md) |
| 60 | Tester texte, vertices, mesh et picture. | [60-text-vertices-mesh-and-picture.md](60-text-vertices-mesh-and-picture.md) |
| 70 | Tester cycle de vie WebGPU, caches, performance et promotion GM. | [70-webgpu-lifecycle-performance-and-gm.md](70-webgpu-lifecycle-performance-and-gm.md) |
| Carte | Empêcher toute suppression de WIP avant classification des APIs/types publics. | [coverage-map.md](coverage-map.md) |

Un lot est un inventaire de travail et non l'autorisation d'ajouter des scènes,
du code ou des captures. Tout travail ultérieur est découpé en petites vagues
explicitement approuvées, avec IDs de cas exacts et preuves d'acceptation.
Aucun lot ne constitue, à lui seul, une autorisation de vague.

## Graphe de dépendances

```text
00 evidence/catalog
 ├── 10 state + rect/rrect ────> 20 paths/strokes/clips
 ├── 30 paint/couleur/gradients
 ├── 40 images/layers/filters
 ├── 50 runtime effects/WGSL
 └── 60 text/vertices/mesh/picture

10, 20, 30, 40, 50 et 60 (routes rendables) ───> 70 lifecycle/perf/GM
```

Les flèches indiquent des dépendances de preuve, pas le droit de commencer à
lire ou à écrire les tests de refus. Par exemple, `20` et `60` peuvent ajouter
leurs cas de refus dès la première vague ; leurs captures de rendu attendent
le lowerer et l'oracle correspondants.

## Stop points humains non transférables

1. Avant une promotion correctness transactionnelle, arrêter après la
   vérification du catalogue generated complet et obtenir une autorisation
   utilisateur explicite pour une promotion correctness complète
   (`promoteGpuEvidence` sans sélecteur, ou `-Pall` explicitement), en
   présentant le SHA exact, le root generated exact et l'adapter de capture
   exact. La métadonnée `promotionReviewer` ne remplace pas cette autorisation
   préalable. Cette autorisation ne couvre ni capture performance, ni nouvelle
   vague, `gpu-renderer-scenes` ou publication.
2. Avant une capture performance, arrêter et obtenir une autorisation
   utilisateur distincte pour le catalogue rendu au HEAD/SHA exact, au root de
   sortie exact et à l'adapter exact. Cette autorisation ne vaut ni promotion
   correctness, ni nouvelle vague, ni travail `gpu-renderer-scenes`, ni
   publication.

Réciproquement, l'autorisation du premier stop point ne couvre pas le second
ni aucune de ces opérations hors portée.

## Concurrence réelle : ce qui est parallélisable et ce qui ne l'est pas

| Ressource ou activité | Parallélisable ? | Raison et règle |
| --- | --- | --- |
| Lecture du code, écriture d'un oracle isolé, test de refus isolé | Oui | Utiliser une branche par lot et conserver un identifiant de scène unique. |
| Tests unitaires Gradle de fichiers disjoints | Oui | Lancer les ciblages de classes en parallèle ; le module complet reste le contrôle d'intégration. |
| Modification de `GpuEvidenceCatalog.kt`, `KanvasScenePrograms.kt` ou `KanvasSurfaceProgram.kt` | Non à l'intégration | Ces fichiers concentrent les scènes publiques. Préparer les commits en parallèle est acceptable, mais les rebaser/cherry-pick dans l'ordre du présent index. |
| Modification d'un même lowerer ou d'un même oracle | Non | Un seul propriétaire jusqu'au test vert et au commit ; les autres lots emploient le refus stable existant. |
| Capture GPU, adapter hardware, répertoire `reports/gpu-renderer/evidence/correctness/promoted/` | Non | Une capture à la fois pour empêcher le mélange d'environnement, de commit, de hash ou d'artefacts. |
| Référence Skia et analyse de diff de familles disjointes | Oui | Chaque résultat conserve sa provenance et ses seuils ; aucune réécriture de seuil global. |
| Performance sur une même machine | Non | Exécuter séquentiellement, appareil refroidi/stable, mêmes warmups/mesures et métadonnées. |

## Protocole commun d'un lot

1. Lire le code listé dans le brief et confirmer la route réellement disponible.
2. Ajouter le test de contrat/refus pour les limites et entrées invalides.
3. Pour une route rendable, ajouter l'oracle indépendant avant la capture GPU.
4. Ajouter la scène à la route publique `Surface` et vérifier telemetry,
   readback, diff et absence de fallback CPU.
5. Exécuter les tests ciblés puis `:integration-tests:gpu-evidence:test`.
6. Capturer et promouvoir seulement sur hardware admissible ; attacher les
   diagnostics, route, stats, environnement et références quand applicables.
7. Reporter dans le brief la décision finale (rendu, refus stable ou
   dependency-gated), puis intégrer les tests. Vérifier aussi que chaque ligne
   de `coverage-map.md` a cette décision et que son lot propriétaire l'a
   absorbée ; le brief peut alors être supprimé.

## Commandes d'intégration

```bash
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha> -Pscene=solid-card-stack
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha> -PscenesFile=scenes.txt
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -PsourceCommit=<sha> -Pscene=solid-card-stack
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -PsourceCommit=<sha> -PscenesFile=scenes.txt
./gradlew :integration-tests:gpu-evidence:promoteGpuEvidence -PsourceCommit=<sha> -PpromotionReviewer=<reviewer> -PpromotionReason=<reason> -Pscene=solid-card-stack
./gradlew :integration-tests:gpu-evidence:promoteGpuEvidence -PsourceCommit=<sha> -PpromotionReviewer=<reviewer> -PpromotionReason=<reason> -PscenesFile=scenes.txt
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha>
./gradlew :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -PsourceCommit=<sha>
./gradlew :integration-tests:gpu-evidence:promoteGpuEvidence -PsourceCommit=<sha> -PpromotionReviewer=<reviewer> -PpromotionReason=<reason>
./gradlew :integration-tests:gpu-evidence:promoteGpuEvidence -PsourceCommit=<sha> -PpromotionReviewer=<reviewer> -PpromotionReason=<reason> -Pall
./gradlew :integration-tests:gpu-evidence:promoteGpuEvidence -PsourceCommit=<sha> -PpromotionReviewer=<reviewer> -PpromotionReason=<reason> -PpromotionRebaseline=true -PpromotionPriorComparison='<comparaison précédente>' -PpromotionNewComparison='<comparaison nouvelle>' -Pall
./gradlew :integration-tests:gpu-evidence:gpuEvidencePerformance -PsourceCommit=<sha> -Pscene=solid-card-stack
```

Une commande hardware qui ne dispose pas d'un adapter admissible produit une
observation indisponible ou un refus explicite ; elle ne produit pas une
promotion par approximation.

Pour le travail quotidien, utiliser une sélection explicite
(`-Pscene=solid-card-stack` ou `-PscenesFile=scenes.txt`) pour génération,
vérification generated et promotion. Quand aucun sélecteur n'est fourni à ces
full correctness gates, la tâche Gradle relaie `--all`; `-Pall` est une forme
explicite équivalente quand on veut l'annoncer. `verifyPromotedGpuEvidence`
reste un contrôle headless du root promoted checked-in et transmet en interne
`--allow-historical-commit --all`, sans réintroduire de native windowing.

`promoteGpuEvidence` n'est donc pas full-only : il partage le même helper de
sélection que `generateGpuEvidence` et `verifyGeneratedGpuEvidence`. Les
rebaselines, en revanche, restent des opérations catalogue complet et doivent
être annoncées comme telles.

La tâche Gradle transmet aussi le rebaseline et les comparaisons prior/nouveau
via `promotionRebaseline`, `promotionPriorComparison` et
`promotionNewComparison`. `promotionRebaseline` est strictement `true` ou
`false` (absent vaut `false`) ; avec `true`, les deux comparaisons non vides
sont obligatoires. Avec `false` ou absent, une comparaison est rejetée au lieu
d'être ignorée. Aucun init script caché n'est un workflow valide.

La capture hardware et la vérification correctness headless restent deux
actions séparées : absence d'adapter admissible, promotion, performance et
rebaseline gardent des autorisations humaines distinctes.
