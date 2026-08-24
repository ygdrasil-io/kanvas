# WIP — ordre d'exécution de l'élargissement des tests GPU

> Dossier de travail temporaire. Aucun fichier de `wip/` n'est une source de
> vérité ni une preuve promue : le code Kotlin, les tests exécutés et les
> artefacts de preuve vérifiés le sont. Supprimer ce dossier après absorption
> de chaque lot dans le code, les tests et les preuves promues.

## Règle de lecture

[current-test-inventory.md](current-test-inventory.md) décrit l'état observé
au commit de départ. Les briefs numérotés détaillent les groupes de tests. Ils
n'autorisent jamais à annoncer un support : une route ne devient supportée
qu'après test de la route publique `Surface`, oracle/référence applicable,
capture GPU, diagnostics, diff/statistiques et politique de fallback vérifiée.

## Lots

| Lot | But | Brief |
| --- | --- | --- |
| 00 | Stabiliser le catalogue, les artefacts, les oracles et les refus. | [00-evidence-and-catalog.md](00-evidence-and-catalog.md) |
| 10 | Tester l'état Canvas et la géométrie rectangulaire déjà basse niveau. | [10-canvas-state-and-basic-geometry.md](10-canvas-state-and-basic-geometry.md) |
| 20 | Tester path, stroke, coverage et clips non rectangulaires. | [20-paths-strokes-and-clips.md](20-paths-strokes-and-clips.md) |
| 30 | Tester paint, blend, couleur et gradients. | [30-paint-color-and-gradients.md](30-paint-color-and-gradients.md) |
| 40 | Tester images, layers et image filters. | [40-images-layers-and-filters.md](40-images-layers-and-filters.md) |
| 50 | Tester runtime effects enregistrés, WGSL, reflection et layouts. | [50-runtime-effects-and-wgsl.md](50-runtime-effects-and-wgsl.md) |
| 60 | Tester texte, vertices, mesh, atlas et picture. | [60-text-vertices-mesh-and-picture.md](60-text-vertices-mesh-and-picture.md) |
| 70 | Tester cycle de vie WebGPU, caches, performance et promotion GM. | [70-webgpu-lifecycle-performance-and-gm.md](70-webgpu-lifecycle-performance-and-gm.md) |

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

## Ordre d'exécution et parallélisme

| Vague | Lots | Mode | Précondition et règle de sortie |
| --- | --- | --- | --- |
| A | 00 | Exclusif | Figer les contrats d'evidence, réaligner `REPEAT` et recapturer le catalogue courant. Sortie : 13 rendus / 3 refus observables, sans transformer les anciens artefacts en preuve du nouveau catalogue. |
| B | 10, 30, 40, 50, 60-refus | Parallèle par branches | Chaque lot peut écrire ses tests unitaires et ses scénarios de refus en parallèle. Une seule branche à la fois modifie le même bloc de `GpuEvidenceCatalog.kt` lors de l'intégration. |
| C | 20, 60-rendu | Parallèle conditionnel | `20` attend les contrats state/coverage de 10 pour les captures rendables. Le texte, les images atlas et les codecs restent dependency-gated : pas de substitut temporaire. |
| D | 10, 20, 30, 40, 50, 60-promotions | Sérialisé par adapter | Les captures hardware et promotions se font une scène à la fois sur un adapter identifié. Les tests unitaires/CI restent parallèles. |
| E | 70 | Partiellement parallèle puis final | Les tests unitaires de cache/layout/lifecycle peuvent commencer après A. Les mesures et GMs ne deviennent des gates qu'après les preuves correctness des routes concernées. |

## Concurrence réelle : ce qui est parallélisable et ce qui ne l'est pas

| Ressource ou activité | Parallélisable ? | Raison et règle |
| --- | --- | --- |
| Lecture du code, écriture d'un oracle isolé, test de refus isolé | Oui | Utiliser une branche par lot et conserver un identifiant de scène unique. |
| Tests unitaires Gradle de fichiers disjoints | Oui | Lancer les ciblages de classes en parallèle ; le module complet reste le contrôle d'intégration. |
| Modification de `GpuEvidenceCatalog.kt`, `KanvasScenePrograms.kt` ou `KanvasSurfaceProgram.kt` | Non à l'intégration | Ces fichiers concentrent les scènes publiques. Préparer les commits en parallèle est acceptable, mais les rebaser/cherry-pick dans l'ordre du présent index. |
| Modification d'un même lowerer ou d'un même oracle | Non | Un seul propriétaire jusqu'au test vert et au commit ; les autres lots emploient le refus stable existant. |
| Capture GPU, adapter hardware, répertoire `correctness/promoted/` | Non | Une capture à la fois pour empêcher le mélange d'environnement, de commit, de hash ou d'artefacts. |
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
   dependency-gated), puis intégrer les tests. Le brief peut alors être
   supprimé lorsque son contenu est absorbé.

## Commandes d'intégration

```bash
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
./gradlew :integration-tests:gpu-evidence:generateGpuEvidence -PsourceCommit=<sha>
./gradlew :integration-tests:gpu-evidence:gpuEvidencePerformance -PsourceCommit=<sha>
```

Une commande hardware qui ne dispose pas d'un adapter admissible produit une
observation indisponible ou un refus explicite ; elle ne produit pas une
promotion par approximation.
