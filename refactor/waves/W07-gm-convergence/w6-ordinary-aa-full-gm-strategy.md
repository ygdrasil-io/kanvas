# W7 — gate full-GM falsifiée et remédiation du port RRectBlur

3 octobre 2026. Relecture indépendante `/root/w7_ordinary_full_gm_strategy_review`,
Astra/high, lecture seule : aucune édition/build/runtime/Git/délégation.
Task1 `c14864408d3d63eb162bbbfb04489d64d3fd0625` conservée, C0/I0 nouveau
identifié par cette revue ; les gates Sol et leurs dettes restent valides.
[Qualification native et images](w6-ordinary-aa-path-source-qualification.md).

## Résultat et cause limitée

Les deux GM complets passent présence/stabilité/exécution native, mais pas
la livraison fidèle initialement prévue : rrect54,42%±2/SSIM0,621383 ;
blurcircles60,844850%±2/SSIM0,913971. Pas de corpus, subgroup9 ou PR déclenché
automatiquement. Task2 originale non livrée, pas réécrite comme réussite.

Le premier défaut de RRectBlur est certain : RRectF32.of(rect, CornerRadii)
renseigne topLeft uniquement, les trois autres paramètres valent Zero.
Les deux appels des premières lignes du GM produisent cette forme ;
Path.addRRect consomme correctement les quatre coins. Images natives et
référence corroborent les coins manquants. Pas de changement math/API requis.
L'accord alpha255 sur un fond opaque n'exclut pas la géométrie.

La [source primaire Skia8d5cb2e](https://skia.googlesource.com/skia/%2B/8d5cb2e10177804e3961b7b1f0a40fd6b08ac1a1/gm/rrect.cpp)
consultée intégralement par Astra distingue gauche drawRRect/droite drawPath,
BR bleu(10,30)/BL(30,30), verticales100 et200, séparateurs AA hairline.
Le milieu amplifie16fois la somme des différences ARGB lues entre cellules,
puis écrit un gris opaque ; labels ajoutés ensuite. Le port utilise deux
drawPath, inverse les coins bleus, omet200 et la comparaison centrale.
Provenance limitée : révision cible8019 inaccessible aux outils, pas de clone
local identifié. Aucune équivalence8d5/8019 ni SHA upstream du PNG affirmée.
Le contrôleur a retrouvé la source primaire par recherche mais son open
direct est en cache miss ; cette limite n'est pas une preuve de variante.

## Décision autonome de remédiation

Conserver la source W6 qualifiée et ouvrir un lot port borné, avec plan
distinct. Corriger les rayons et opérations réellement consommés, via
paramètres nommés ; ne pas ajouter un overload implicite à RRectF32.of.
Vrais pixels indépendants avant correction, puis GM filtré entier avec les
mêmes références/domaines/dimensions. Un nouveau refus reste un falsifier,
pas une permission de modifier une autre famille planner par opportunisme.

Panneau central et labels restent des manques explicitement suivis : pas de
rectangle noir de substitution, CPU diff, faux blend DIFFERENCE ou référence
modifiée. Une comparaison GPU serait un contrat distinct (capture/domaine/
quantification/composition), disproportionné pour ce seul port maintenant.
Les métriques entières continuent à inclure ces régions ; crops diagnostiques,
pas un masque d'exclusion améliorant les scores. Fonts restent hors périmètre.

La fidélité blurcircles (grandes sigmas notamment) reste sans cause attribuée.
Pas de migration encoded, changement de kernel ou de seuil basé sur ces images.
Une future qualification de l'admission retenue peut être explicitement
replanifiée avec critères propres ; elle ne clôt pas Task2 fidélité originale.

## Risques et coût retenus

Important : port différent et panneau absent ; centres/replay/admission ne
prouvent pas parité. Minor : ambiguïté d'appel, à éviter par paramètres nommés.
Coût : petit lot GM/fixtures/docs, cycle RED→GREEN causal, requalification du
seul GM et revue ciblée. Si les rayons explicites échouent natifs, attribuer
math/renderer séparément. Pas de rebuild global/corpus ni lecture des anciens
workspaces pour trouver un verdict permissif. Aucun merge/W7 complet.
