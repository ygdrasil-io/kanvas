# W7 — qualification distincte de l’admission ordinary-AA retenue

## Décision et objectif

Le correctif W6 ordinary-AA et le port RRectBlur sont conservés après leurs
preuves natives. Cette décision ne termine pas la Task2 originale de fidélité :
`rrect_blurs` reste à 55.0775 % et `blurcircles2` à 60.84485032978184 % à ±2.
Ce lot mesure leur utilité réelle et leurs régressions sur le registre fixe,
sans nouvelle fonctionnalité renderer, puis prépare une draft PR empilée.
L’autorité W7 est déléguée par l’utilisateur; aucune nouvelle boucle d’accord.

## Contrat de mesure

- Baseline physique : `root-aa-rect-8e44f0c8a.json`, commit
  `8e44f0c8ad1ac65d97275d010ed083e1e44bbfcd`.
- Registre : 631 entrées, SHA-256
  `4ca8eea61451b1143fd3d15634d2c34e0c9ec31b74fd351ca30a69ee36f565d7`;
  443 éligibles; 133 fonts, 54 codecs et 1 `jpg-color-cube` exclus inchangés.
- Même runner `SkiaGmParityCheckpointKt`, domaine propre à chaque GM,
  références physiques SHA-256, dimensions, tolerance, minSimilarity,
  requiresZeroRefusals et scope inchangés. Mac OS X/aarch64/Java25.0.1;
  timeout GM 30 s et processus 240 s, sans hausse de budget.
- Le rendererCommit est le HEAD réel complet de chaque phase, jamais le SHA
  historique ou un nom de branche. Sources figées et empreintes avant/après.
- Baseline : 217 rendus, 194 comparés, 175 render_failed, 50 setup_failed,
  15 rendered_uncompared, 8 reference_dimension_mismatch, 1 timeout;
  47 à ≥99 % et 63 à ≥95 % à ±2; médiane comparée 77.45815728081598 %.
- Les neuf opportunités, non neuf gains promis : 34 backdrop_hintrect_clipping,
  64 blurcircles2, 210 dropshadow_pseudopersp, 213 emboss, 233 filterfastbounds,
  317 imagefiltersbase, 338 inverse_windingmode_filters, 470 rotate_imagefilter,
  472 rrect_blurs. Chacune était render_failed dans la baseline.

## Architecture et preuves

Réutiliser uniquement le runner et l’agrégateur existants. Ne pas créer de
test d’infrastructure, filtre de noms ou outil de fake admission. Le contrôleur
possède chaque runtime/Git; les Subagents interprètent et documentent les
reçus après audit. Une seule exécution native à la fois; terminal, puis lecture
séparée du log complet, exit, événements éventuels, JSONL entier et XML éventuel
avec stdout/stderr non tronqués, avant toute édition ou exécution suivante.
`measureSkiaParity` est JavaExec, donc l’absence attendue de JUnit XML est
consignée, jamais remplacée par un faux PASS.

Les neuf journaux sont indépendants du journal corpus. Le corpus couvre chaque
indice exactement une fois et conserve les exclusions et timeouts. L’indice
607 vertices, historiquement timeout, est isolé; son timeout reste une ligne
et un processus RED. Un arrêt prématuré peut imposer des tranches de reprise
non chevauchantes après le dernier indice écrit, sans rejouer les cas ou
augmenter les caps. Une tranche sans identité exploitable invalide la mesure.

Publier les changements de statut, pixels, métriques et refus de tous les cas,
pas seulement des neuf. Le RRect corrigé a intentionnellement une scène
différente; la correction publique drawLine peut affecter d’autres GMs.
Le bilan sépare ces corrections de port/API de l’admission renderer; une
première diagnostic n’est ni une causalité complète ni un gain garanti.

## Critères et limites

La mesure est recevable si le registre/configuration/références sont fixes,
les 631 identités sont présentes sans doublon, tous les processus et pixels
disponibles sont conservés et les pertes explicitement listées. Une mesure
recevable n’est pas un corpus GREEN. Les gains sont les rendus natifs nouveaux,
les comparaisons nouvelles et le delta des seuils fixes; l’analyse des anciens
comparés indique toute baisse, même si le statut reste compared.

Toute perte de rendu ou nouveau refus doit être expliquée avant de déclarer
l’admission qualifiée sans régression. Sinon publier la mesure comme telle et
ouvrir un lot causal distinct; ne pas masquer la perte, élargir un scope ou
baisser un seuil ici. Pas de modification math I/F32/64, shader, AA, domaine,
sampling, budget, source, fixtures, références ni scores historiques.
Le panneau central RRect absent, les labels/fonts exclus, les différences de
blur, le processus true-mask exit133 également rouge avant patch et les suites
globales rouges/incomplètes restent ouverts. Pas de claim near-ISO, global
GREEN, merge ou W7 terminé. Les workspaces, reçus et diagnostic protégé sont
préservés. La revue finale indépendante couvre toute la branche depuis
1872d31cb637b6704c1580ea1f1220b8aefdf883, puis une draft PR seulement si ses
findings C/I sont résolus; parent draft #2438, jamais de force push/merge.
