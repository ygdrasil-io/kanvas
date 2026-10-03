# W7 — qualification de la source PATH AA ordinaire sous W6

3 octobre 2026. Branche `codex/w7-w6-ordinary-aa-path-source`,
parent draft[#2438](https://github.com/ygdrasil-io/kanvas/pull/2438),
BASE `1872d31cb637b6704c1580ea1f1220b8aefdf883`.
[Design](w6-ordinary-aa-path-source-design.md),
[plan](w6-ordinary-aa-path-source-plan.md). Qualification en cours ; aucun
gain corpus, livraison complète, publication ou clôture W7 revendiqué ici.

## Produit qualifié et frontières

Préalable `63f23b6f6ad72bb916398b92df3734788c3a3003` : `Canvas.drawLine`
impose implicitement STROKE en copiant seulement le style de Paint.
`GmCanvas.drawLine` conserve le même wrapper CTM/clip que drawPath et appelle
ce helper partagé. Width0 reste Hairline préparée par math, pas width1 local.
Le draw original22 de blurcircles2 est maintenant STROKEwidth0, mêmes path,
CTMtranslation65,65, matériau/AA/filtre absent. L'ancien FILL ouvert aurait
pu être admis sans contenu ; cette capture causale est conservée.

Source `c14864408d3d63eb162bbbfb04489d64d3fd0625` : factory/predicate W6
ordinaire nommé dans W4d, sélection depuis original réellement sans filtre
dans W6a. Root PATH/AA FILL ou STROKE, solide SrcOver, pas d'effet/filtre ;
original et child identiques, ownsW6b requis, admissions indépendantes.
Exclusion du deferred coverage seulement pour cette source. Contours math
F32/F64 immutables existants, AA4→resolve1x couleur prémultipliée, consommation
unique authentifiée, ordre/clip/transform/lifetimes/budgets conservés.
Aucun consumer, shader, ABI, sampling, domaine, budget ou référence modifié.

## Preuves natives réduites et contrôle des régressions

Tous les runs ont un watchdog240s, un seul runtime et des archives privées
immuables. Terminal original puis audit séparé complet log/exit/events/tous
XML/cases/stacks/stdout/stderr avant toute modification ou nouveau runtime.
Les cases PASS ci-dessous sont effectivement atteints ; aucun skip/fallback.

| Gate | Cases | Résultat de processus |
| --- | --- | --- |
| drawLine public Canvas | 4/4 PASS | exit0 |
| drawLine GmCanvas | 4/4 PASS | exit0 |
| Contrôles wrapper GM existants | 7/7 PASS | exit0 |
| Sources ordinaires réduites (FILL/STROKE/hairline/couleur/ordre/négatifs) | 5/5 PASS | exit0, deux validations exactes |
| Root MSAA corrélé, deux domaines | 6/6 PASS | exit0 |
| Root mixed, ordre/corrélation/Picture/guards | 10/10 PASS | exit0 |
| Layers W6 existantes | 40/40 PASS | exit0 |
| Transform valide/horizon refusé et recovery | 2/2 PASS | exit0 |
| Vrais masques W6b | 15 corps natifs PASS | RED : executor133 après tous les END |

Task1 couvre78 identités natives, mais pas78 runs GREEN. Pour les masques,
un seul discriminateur remet exactement les deux sources BASE63, mêmes
fixtures, puis rejoue : mêmes15 corps PASS et executor133 après les END.
Le crash survient sans le correctif Task1 ; sa cause native/shutdown n'est
pas attribuée. Réapplication des sources byte-identiques puis reduced5/5
PASS avant commit. Pas de réparation d'infrastructure ou de chasse répétée
au crash dans ce lot. Anciennes suites globales RED/incomplètes et tests
unitaires gpu-plan/gpu-renderer non compilants restent des dettes explicites.

RED causal avant source : véritables GM refusés draw10 rrect et draw22 blur,
reduced FILL/STROKE, scaled hairline WITH-sibling refusés alors que standalone
et foreign controls passent. Le témoin hairline fixe width1 device sous
scale2,3, avec pixels extérieurs transparents ; les demi-couvertures fixent
la conversion LINEAR et distinguent sources isolées des samples corrélés root.
Oracles gelés entre RED et GREEN ; pas de tolérance ajustée après sortie.

## Relectures et dettes

Sol Task0 : spec Steps1/2 et qualité approuvées C0/I0/M1 warnings hérités.
Step3 causal hairline RED atteint ensuite, Task0 complet. Sol Task1 : spec et
qualité approuvées C0/I0/M1 nouveau safe-call inutile W6a305, sans impact
fonctionnel constaté. Minor différé pour préserver les sources qualifiées ;
warnings native-access/Unsafe/Gradle restent suivis, pas de globale GREEN.

Deux autres dettes séparées : open-FILL horizontal de surface nulle sur y
entier peut être refusé par geometry.invalid ; le témoin y demi-entier garde
toutes ses attentes vides sans prétendre réparer ce cas. La route préparée
canonique n'exporte pas les mêmes scopes que la route planifiée ; Task0 GM
prouve ses vrais draw/pipeline counters et pixels. Task2 garde le predicate
Render/Readback de sa route W6 planifiée, sans fabrication de metadata.

## GM complets et corpus — pending

Task2 prolonge les deux mêmes witnesses natifs : sources GM inchangées,
300×400 et730×1350 LINEAR, dimensions/background opaque, formes ET
séparateurs, replay identique, références SHA fixes, comparaison/crops privés.
Attentes avant mesure : RRect centers aux couleurs source exactes, background68,
séparateurs blancs width1 à demi-couverture RGB192±1 sur deux rows/columns,
extérieurs68. Blur first circle/almost-circle centers65,65/65,170 RGB0..96
alpha255, fond10,10 blanc, quatre hairlines noires aux rows222/456/722/1020.
La borne sombre provient du vrai kernel W6b sigma3.38675/radius11 : union
des queues>=9 <=0.0222481, plus16/255 d'arrondi conservateur, byte sRGB<83.
Ce n'est ni exact noir ni preuve de fidélité ; metrics globales/régionales
indépendantes et inspection des images exposent les écarts.

Après livraison des deux frames seulement : sous-groupe9 ordinaire puis
corpus631/443 fixe via measureSkiaParity. Baseline
[8e44f0c8a](root-aa-rect-8e44f0c8a.json) :217 rendus/194 comparés,
47≥99%±2,63≥95%±2. Neuf opportunités ne garantissent pas neuf gains.
Fonts133/codecs54/jpg-color-cube1 restent exclus, aucune référence/score/seuil
ou scope modifié. Nouveau refus substantiel, contenu absent, forte erreur
de composition ou budget hors contrat falsifie ce lot avant extension.

### Premier résultat réel : admission acquise, fidélité falsifiée

`rrect_blurs` complet et son replay sont natifs,14ops/0refus,
78drawCalls/92pipelineBinds,79renderPasses,1submit/1readback. Tous les pixels
alpha255, les25 ancres indépendantes passent ; SHA RGBA identique au replay
`3d0b827e4ce1627465564ea05eed2a3edc769b77766b34f6b9f52fc90587e1fc`.
Run exit0 sans timeout/skip/executor133,1testPASS ;22 images/crops archivés.

Cependant **54,42%±2**, exact51,585833%, **SSIM0,621383**,
erreur moyenne normalisée0,0747544/maxRGB187 : pas de livraison Skia validée.
Inspection des vrais PNG sRGB : coins manquants dans les premières formes
blanches, colonne centrale diff/labels de la référence non reproduite par le
port (fond gris actuel), écarts de bounds bleus. L'accord alpha sur fond opaque
n'exclut pas une erreur géométrique. Aucun diagnostic de cause renderer forcé.
La revue stratégique Astra distingue port/math/renderer avant nouveau lot ;
fonts restent exclus, aucune référence/seuil/scope ajusté pour gagner un score.
La seconde frame planifiée est maintenant caractérisée séparément :
`blurcircles2`55ops/0refus,413drawCalls/468pipelineBinds/414renderPasses,
1submit/1readback, Render+Readback et CompletionSucceeded. Les50 formes et
quatre séparateurs sont visibles ; allalpha255, sept ancres passent, replay
identique `2c239fabc210472e7a6aa3942190800796ac9f9d6a81463184d4589cc3f10c92`.
Run exit0 sans timeout/skip/executor133,1testPASS ;30 images/crops archivés.
Exact48,971081%,±2=60,844850%,SSIM0,913971,erreur moyenne0,0355799,
maxRGB82 : flous visibles plus clairs que la référence. La cause couleur/
kernel/coverage n'est pas attribuée ; aucun domaine GM migré par supposition.

Corpus/delivery restent pending, sans gain revendiqué ; Task1 source réduite
approuvée reste conservée. Astra confirme le défaut caller RRectF32.of : le
second argument CornerRadii renseigne seulement topLeft, les autres restent0,
Path.addRRect les consomme correctement. Modifier math serait injustifié.
Le prochain lot corrige seulement les entrées/opérations du port après preuve
causale et vérification upstream. Le panneau différentiel et les labels ne
seront ni remplacés par un rectangle noir, ni réalisés via un CPU diff.

Task2 Sol, revue finale Astra, draft empilée, CI et merge restent pending.
Aucun merge ni W7 complet autorisé par ces gates locales.
