# W7 — recensement transversal complet (4 octobre 2026)

[Qualification](transversal-corpus-qualification.md),
[snapshot gelé2485cfb](transversal-corpus-2485cfb.json), parent draft #2446,
branche `codex/w7-transversal-corpus-census`. 631 fiches /443 éligibles,
392 tentatives /220 rendus /197 comparés : aucun gain d'admission depuis7360c5.
Pixels±2 ≥95 % :63→65 ; ≥99 % :47→49. Trois deltas pixels confirmés :
3x3bitmaprect→100 %, child_sampling_rt→84,3277 %, tinybitmap→100 % ;
huit compteurs Mesh changent sans variation pixels. 195 PASS historiques,
343 seuils0, ne prouvent pas la gate de parité W7. vertices607 reste timeout
30 s/exit1, conservé dans443 ; aucune nouvelle exclusion/cap/budget modifié.
Cinq audits natifs complets et postseals séparés ; refs/PNG/scores/protected
inverse inchangés. Premiers refus communs :layerchild32/pathbudget19,
strokedRectAA10 ; causes hétérogènes, gains non promis.
Prochain diagnostic : normalisation AA strokedRect vers géométrie commune,
comparée à l'admission enfant compositionnelle et au domaine encoded.
Review/publication en attente ; pas de review Astra revendiquée après limite
de sous-agents. W7 ACTIVE, aucun merge/globalGREEN/clôture.

# W7 — tinybitmap : composition encoded du port (4 octobre 2026)

[Qualification ciblée](tinybitmap-encoded-qualification.md), branche
`codex/w7-tinybitmap-encoded-parity`, [draft #2446](https://github.com/ygdrasil-io/kanvas/pull/2446),
source735d96af, parent draft #2445.
GM explicitement SRGB_ENCODED/non-AA, source/fond/alpha/tile/sampling inchangés.
RED2échecs encoded/2contrôles PASS ; GREEN17/17 ciblés PASS, vrais buffers
entiers/replay/completion native ; contrôle LINEAR strict conservé.
Cas592 frais : exact0% inchangé, ±2 0%→100%, SSIM0.9949447882→0.9999928051,
maxRGBA[0,28,28,0]→[0,1,1,0]. Pas de parité bit-exacte ni gain agrégé.
Générateur1rendu/0échec ; runnerstandard1PASS/score100.0, seuil0/tol2 inchangés.
Seuls targetPNG/score changent ;813autresPNG/558autrescores/référence gelés.
La PNG de référence contient un ICC Rec.2020 : stockage brut distinct des
canaux sRGB comparés ; producteur/révision inconnus, quantification/proxy suivis.
Relecture de tâche Sol Approved C0/I0 ; warnings hérités suivis.
Review finale indépendante Sol Approved pour draft C0/I0/nouveauM0 ;
sources upstream non authentifiées indépendamment par le reviewer (réseau
indisponible), inférence bornée et origine historique inconnue conservées.
Draft stackée publiée/attachée ; base/head/description vérifiés.
W7 ACTIVE, pas de merge/globalGREEN.

# W7 — contrats image promus et preuve native Surface (4 octobre 2026)

[Qualification ciblée](promoted-image-contracts-qualification.md), [draft #2445](https://github.com/ygdrasil-io/kanvas/pull/2445), branche
`codex/w7-promoted-image-contracts`, base84a7c8a3, parent draft #2444.
Quatre refus W5e obsolètes maintenus en sondes honnêtes, scènes conservées,
compagnons hors frontières vérifiés intégralement ; vrai Point négatif avec
sentinel et récupération/replay même Surface. Trois adaptateurs corrigent le
comptage Vertices/Mesh survivants et transmettent scopes/telemetry/deltas de
la completion GPU réelle, sans changer runtime/sampling/AA/admission/budgets.
10/10 ciblés puis57/57 de contexte PASS, dont B888/B−1/sentinel/récupération.
RED causaux et runner133 initial explicités dans la qualification ; pas de133
après disposal AfterAll. Review de tâches Sol Approved C0/I0 ; warnings
hérités suivis. Review finale Sol Approved C0/I0 pour publication draft.
69 chemins pré/post-scellés ;814PNG/559scores inchangés, aucun gain corpus.
Hairline8cellules, triangle4diagonales, TL RRect restent non qualifiés ;
compteur hétérogène historique et off-target sans clip restent des dettes.
Pas de GREEN global ni merge ni W7 terminé : W7 ACTIVE.


# W7 — admission readback après warmup (3 octobre 2026)

[Qualification ciblée](readback-budget-admission.md), branche
`codex/w7-readback-budget-admission`, base60ce1489, parent draft #2443.
Défaut causal démontré : purge du staging réutilisable, puis arrêt prématuré
malgré d'autres bytes libérables. Correctif provider-only, retry uniquement
après baisse réelle de résidence ; budgets, LRU et ownership inchangés.
16/16 contrôles GPU PASS ; témoin cosmétique requalifié 2/2.
Dans le contexte52 original, Composition25/25 dont B888/B−1/sentinel/recovery
PASS ; résultat total48/52, quatre anciens refus W5e toujours RED.
Ce relevé remplace le gap de budget non attribué du lot précédent pour ce
contexte précis, sans reconstruction de son précurseur exact ni GREEN global.
814 PNG/559scores inchangés, aucun gain corpus mesuré. W7 ACTIVE, aucun merge.

# W7 — source `tinybitmap` et image shader encoded (3 octobre 2026)

[Qualification ciblée](tinybitmap-source-fidelity.md), branche
`codex/w7-tinybitmap-source-fidelity`, source
`78ee0ee0870fb2387eddbfedb819bb358f1f0690`, base e5069ccb54, parent draft #2442.
Source PREMUL corrigée et feuille image encoded admise sur RECT hard entière
non-AA/SrcOver/nearest, domaine transmis depuis l'autorité existante.
14/14 témoins natifs indépendants PASS ; revue source Sol Approved C0/I0/M0.
La parité historique reste exact 0 % / ±2 0 %, malgré SSIM
0.9845261966447928 → 0.9949447881896182. Le runner standard PASS grâce au
seuil historique inchangé 0 %, pas grâce à une parité atteinte. GM toujours
LINEAR ; proxy drawPaint, alpha quantifié et producteur PNG inconnu restent
des limites. Générateur : 1 rendu/0 échec ; 813 PNG non-cibles et 558 autres
valeurs de scores inchangées, score cible 0.0.

Suite publique ciblée RED 47/52 : quatre refus W5e hérités prouvés sur baseline,
plus un refus de budget exact 888 en contexte de suite, cause non attribuée
malgré le PASS isolé inchangé. Gap ouvert avant validation globale/merge.
Revue finale Sol du lot e5069ccb…f7b445f8 Approved pour publication draft,
aucun nouveau C/I/M ; les deux points Important hérités/non attribués restent
ouverts. Aucun merge, GREEN global, gain de corpus agrégé ou clôture : W7 ACTIVE.

# W7 — fidélité source `DrawBitmapRect3` (3 octobre 2026)

[Qualification ciblée](bitmaprect3-source-fidelity.md), branche
`codex/w7-bitmaprect3-source-fidelity`, source `f6c041d8d8088648e70850f60d00dc5c3e0c8192`,
base `f9bd8fae23cf2a15af235e61dc9c6096661ad740`, sur le parent draft #2441.
11/11 tests ciblés PASS, sans skip/erreur. Parité PNG historique : exact
4.069010416666666 % → 98.37239583333334 %, ±2 5.696614583333333 % → 100 %,
SSIM 0.03333736469852865 → 0.9999996522494206. Les 813 PNG non-cibles
`1dfc6fe2b56f175eadeadf66be1c5960a7d7e6675cd6509985ad4954ffa9f171` et les
558 scores hors cible `34f4c04ce043a3194e963203cb0efb2874f0d52e65479948e2da02c864187533`
restent inchangés. Révision de la PNG de référence inconnue ; résultat exact
source distinct de la parité historique. Revue Sol approuvée C0/I0/M0new,
verdict scoped à cette tâche. Pas de merge ; W7 ACTIVE, aucun GREEN global ni
clôture revendiqués.

# W7 — port `child_sampling_rt` (3 octobre 2026)

[Qualification ciblée](child-sampling-port-qualification.md). Source `73fc10a84f0b49edf3365f54793faf254c165f8c`; tests F12 au commit `0d456121143a3104575d7ca82040be652f2345bb`. Le parent publié [#2440](https://github.com/ygdrasil-io/kanvas/pull/2440) reste au HEAD e39; [#2441](https://github.com/ygdrasil-io/kanvas/pull/2441) est un draft ouvert. Le pin Skia reste `4f26f22daa4bf124e2999145f5caad4b10625580`; la révision exacte de la PNG de référence reste inconnue.

D7 : 4 PASS natifs ciblés; D8 : 443 éligibles / 431 capturés / 11 `SetupBlocked` / 1 `CaptureInvalid`; D9 et D5/D6 pré-correctif restent des RED historiques. F6 : 52/52 tests ciblés; F7 : 8/8 intégration; F8 : case77 identique aux octets antérieurs; F12C : 54/54 tests PASS (52 contrôles existants + deux nouveaux tests). Clear bleu avant le point DARKEN refuse deux fois avec `invalid.w5b.prepared-points` et conserve les opérations. Les mutations typed refusent avec diagnostics correspondant au marker, bounds et identité de capture; les quatre baselines consomment un vrai snapshot destination et un packet DARKEN. Cela ne donne pas à Clear une source-material authority et n’ajoute pas son admission. Clip/scissor et ordre isolé restent des limites explicites.

D10 et F8, cas77 uniquement : 81,96563720703125 % exact, 84,32769775390625 % à ±2, SSIM 0,9806240190874789; F8 utilise le checkpoint source `73fc10a`. Le scellement F8 des 814 PNG repo-relatifs est `142b7f05265919d08d5a1471b9cd09e9bae2aded083ad705fd45ae1d0737ae4d`; celui des 813 PNG non-cibles avec chemins relatifs à la racine générée reste `a6bf35b1ace3748b2cf16a27d28c04629a75ecb4e83c5e91ac63b4bedb9e0d5f`. Les 559 scores et octets cibles n’ont pas changé; aucune régénération n’était nécessaire. Le delta d’intensité/couverture reste non attribué; ni benchmark ni gain agrégé. Le gap général d’ownership stencil, les dettes `SurfaceSceneSnapshotTest`, warnings et exclusions restent documentés.

La revue Sol originale (avant F6) avait I1; la correction round1 l’a marqué `ADDRESSED` et signalé un nouvel I2 Important (`C0 / new I2 / M0 new`, M2 hérité). La revue round2 approuve le périmètre (I1/I2 clos, `C0 / I0 / M0 new`); Task1 est complete scoped reviewclean et aucun nouveau minor n’est signalé. M2 hérité reste suivi. La PR #2441 reste draft; aucun merge, GREEN global, agrégat W7 ou clôture n’est revendiqué. Résultats détaillés : [qualification](child-sampling-port-qualification.md), [addendum](child-sampling-capture-addendum.md) et reçu privé `.superpowers/sdd/child-sampling-port-e39a2be31/point-square-controller-evidence.md`.

# W7 — diagnostic causal natif case 77 (3 octobre 2026)

[Qualification](child-sampling-causal-qualification.md), branche
`codex/w7-child-sampling-causal`, empilée sur le parent [#2439](https://github.com/ygdrasil-io/kanvas/pull/2439), déjà publié en draft au HEAD
`5bfa05e28a1697c57ad36dc6cfb0e8e786e36451`. Le diagnostic et la publication
de cette nouvelle pile sont en attente de revue. Une exécution native bornée :
1 PASS, aucun skip, échec, refus ou diagnostic, six rendus/relectures sur trois
variantes. Le `FILL` ouvert historique reproduit le RGBA historique ;
`ChildSamplingRTGm` actuel est identique au `STROKE` explicite. Le changement de
sémantique `FILL`→`STROKE` explique cette variation sur la scène locale
actuelle. Il ne qualifie pas la fidélité du port : l’implémentation locale
reste simplifiée par rapport au GM upstream de sampling enfant et à sa
référence. Témoin historique à préserver littéralement lors d’un futur
portage ; nouvelle scène upstream à qualifier séparément. Tolérance 2,
métriques du corpus, dette de fidélité et état global RED restent inchangés.
Aucun corpus complet, global GREEN ou W7 clos revendiqué.

# W7 — qualification du corpus ordinary-AA retenu — 3 octobre 2026

[Qualification](ordinary-aa-retained-corpus-qualification.md), snapshot
[631/443](ordinary-aa-retained-corpus.json), baseline
[8e44f0c8a](root-aa-rect-8e44f0c8a.json). HEAD mesuré
`7360c5f94e3fcaa2f68d4375d8cdd7ad12295460` ; identités, config et références
fixes. Résultat : 220 rendus / 197 comparés (baseline 217/194), 3 statuts
`render_failed` → `compared` (64, 210, 472), aucun nouveau seuil ≥99 % ou
≥95 % ; médiane 76,24387741088867 % (−1,21427986992731 point). Le timeout
native `vertices` à l’indice 607 reste RED (tranche/Gradle exit 1). Une baisse
de SSIM demeure sur l’ancien comparé `child_sampling_rt` (index 77), avec
cause non attribuée ; sept diagnostics changent sur des cas toujours refusés.
La gate de mesure Task2 Sol est approuvée C0/I0/M1. La revue large Astra du
full40 `befdceaa4e167ac52c67d162913f3031789caa9b` (1872..befdceaa4) juge la
publication en draft prête C0/I0/M1 ; les bytes source sont inchangés. Cette
revue ultérieure ne modifie pas l’état historique de mesure au HEAD7360. La
publication reste en attente du contrôleur. Aucun claim sans régression, de
fidélité complète, de global GREEN, de merge ou de W7 clos ; reçus privés et
détails des pertes dans la qualification.

M1 différé au prochain edit qualifié : safe call W6a superflu (`W6aLayerPlanCompiler.kt:305`),
fixture sans opt-in explicite (`W7W6OrdinaryAaPathSourceIntegrationTest.kt:118`),
warnings Java native-access / `sun.misc.Unsafe` LWJGL et dépréciations Gradle.
Ils restent visibles comme dette technique : sortie sans warnings non revendiquée,
données non invalidées et suites globales toujours non vertes.

# W7 — checkpoint de qualification RRectBlur (3 octobre 2026)

Le correctif d’appelant RRectBlur est retenu avec l’admission W6 : le GM réel
nomme les quatre rayons au lieu de compter sur `RRectF32.of(rect, CornerRadii)`
pour renseigner les quatre coins. Les mathématiques I/F32/64, l’AA, le domaine,
les budgets, les références et les seuils restent inchangés.

Après correction, `rrect_blurs` passe de 54,42 % à 55,0775 % à ±2, avec SSIM
de 0,621383424754948 à 0,6624135636987509. Les 25 ancres passent, replay
identique, 15 ops / 0 refus, 77 draw calls, 92 pipelines, 82 render passes;
run exit 0, 1 PASS, sans skip ni timeout. Les coins de la scène sont corrigés,
mais le panneau central et ses labels manquent toujours et les bordures/flous
divergent : la fidélité complète de Task2 reste falsifiée.

Le contrôle inchangé `blurcircles2` garde 60,84485032978184 % à ±2 et SSIM
0,913971350294999, identiques au relevé précédent. Les 50 formes et quatre
lignes sont présentes, mais les régions floues restent trop claires; aucune
cause couleur/kernel/AA n’est attribuée. Les deux audits natifs complets,
empreintes, chemins d’archives et décision figurent dans
[la qualification RRectBlur](rrect-blur-port-qualification.md).

Les 21 PNG/crops RRect sont conservés; l’inspection couvre les vues entières
actual/reference/diff±2 et les triplets première/dernière rangée, x200 inclus.
Les 29 PNG du contrôle sont conservés; seules ses vues entières
actual/reference/diff±2 ont été inspectées.

Retenir l’admission qualifiée et le correctif réversible autorise à planifier
une qualification d’admission distincte sur neuf cas puis le corpus fixe
631/443; cette étape n’est ni exécutée ni publiée automatiquement. Aucun gain
corpus mesuré, nouveau PR, merge, global GREEN ou clôture W7. La provenance
Skia 8d5cb2e est vérifiée; target 8019 et origine exacte des PNG inconnues.
Les exclusions 133 fonts / 54 codecs / 1 `jpg-color-cube` restent inchangées.

# Pilotage de la convergence Skia

## Couverture diagonale isolée — 3 octobre 2026

[Diagnostic causal](diagonal-aa-diagnostic.md), parent#2437/d12749b64,
branche codex/w7-aa-coverage-diagnostic. Produit inchangé : pas de gain corpus.
Contour tiny CTM math conforme aux quatre sommets indépendants, cinq échelles ;
source tiny, ligne écran et FILL littéral donnent la même rampe native.
Alpha64/255 aux cellules106/107 contre aire idéale≈36.57/227.49, indépendant
du domaine couleur. LINEAR225/0 et encoded191/0 restent différents de223/31–32
de la référence parent. Le problème n'est pas résolu par un déplacement du
contour ou par la seule couleur : sampling AA actuel quantifié sur ce témoin.
Route promue W4d/math F64 confirmée, première inférence legacy corrigée.

1math ciblé et2Surface natives PASS puis311math:matrix PASS, exits0 sans
timeout/skip ;20rendus natifs avec repeats et full stdout/counters archivés.
Pas de génération d'images/références/scores, pas de MSAA/budget relevé.
Revue Astra finale conforme/Approved,C0/I0/M2 hérités. Elle diffère l'AA
convexe : aire correcte LINEAR peut empirer Teeny99.5228%, et les layers
comptent36refus partagés. [Décision](diagonal-aa-strategy-review.md),
[design](w6-ordinary-aa-path-source-design.md) et
[plan](w6-ordinary-aa-path-source-plan.md) écrits : source PATH AA ordinaire
FILL/STROKE solide SrcOver sousW6, livraison sur rrect_blurs/blurcircles2
complets, contours/isolation AA4→1x existants. Deux groupes de9 ne garantissent
pas18gains ; neuf draws filtrés hors première extension. Pas de nouvelle
capacité AA ni de domaine migré ; W7/globales/Picture/RRect/inverse OPEN.

## Connexion des vrais Rect hard au root-AA encoded — 3 octobre 2026

[Qualification](root-aa-rect-qualification.md) et [corpus](root-aa-rect-8e44f0c8a.json),
branche codex/w7-root-aa-rect-admission sur#2436/53bf9c55b, candidate8e44f0c8a.
La vraie scène fondRect+Paths est maintenant admise explicitement, sans Path
de fond artificiel ni lane privée empruntée.25témoins natifs10+15PASS et
498mathPASS, historiques48/49 comme parent et globale RED/incomplète conservés.
Deux limites sorties du diagnostic, pas masquées : drawPicture wrappers OPEN
(playback exact C seul positif), budget de fixture omettait hardstencil256.
Autorité de conversion exacte I/F32/I32 dans math, grands spans préservés.

Mesure réelle H : domaine encoded corrige188→128 vertical mais191diagonal
reste différent de223. Gain±2 minime0.0065625point, pas de couverture corrigée.
GM déclaré LINEAR et toutes631fiches corpus hors temps restent inchangés :
217rendus/194comparés,47≥99/63≥95, zéro gain/perte GM. Ne pas compter une
capacité ouverte comme convergence visuelle. Prochain axe : couverture/placement
AA avec témoins indépendants, pas migration de domaine sans preuve.
Sol Task2 Approved C0/I0/M1 ; Astra whole-branch C0/I0/M2, draft acceptable,
mergeNO, source/graph/facts/physical keys/preflight statiquement cohérents.
Warnings et trois libellés imprécis différés, source native gelée inchangée.
Draft stack après vérification distante ; aucun merge/globalGREEN/W7clos.

Draft [#2437](https://github.com/ygdrasil-io/kanvas/pull/2437) publiée/rattachée
sur#2436, base53bf9c55b et head initial28e2f8dbf/body distants exacts vérifiés.
Source/tests commit1b24a6e12 exactement qualifiés8e44 ; reçu ultérieur docs-only,
CI non inspectée, aucune fusion. Prochain lot AA indépendant, sans gain présumé.

## Capacité root Path AA encoded — 2 octobre 2026

[Qualification locale](root-aa-encoded-qualification.md) et
[corpus frais](root-aa-encoded-28adb36d3.json) ; source privée28adb36d3,
base publiée#2435/6f059f0dc, draft [#2436](https://github.com/ygdrasil-io/kanvas/pull/2436)
publiée/rattachée, base/head produit initial7d4a1b7f5/body distants vérifiés.
Le témoin indépendant sépare188LINEAR/128encoded et conserve les masques
corrélés ;3LINEARPASS+3encodedRED puis9guardsRED précèdent le patch.
Après source :15nativePASS,48/49contrôles historiquesPASS avec1PictureFAIL
exact parent. Sol Task1/Task2 approuve ; Astra finale C0/I0/M2, draft acceptable.
M1 agrégation documenté, contre-revue Sol sans nouvelle rupture ; M2 warnings
différés. Source/tests publiés byte-identiques au candidat qualifié ;
suivi documentaire ultérieur seul, CI non inspectée, merge/W7 non qualifiés.
Globale RED/incomplète et suites unitaires à tests hérités noncompilants
explicitement suivies. Précision native générale du resolve non démontrée.

Toutes631fiches hors temps strictement identiques au parent :217/194,
47≥99%/63≥95%, zéro gain/perte GM. Aucun GM migré ou fond remplacé par un
Path artificiel. La consommation GM encoded doit être un lot mesuré distinct,
avec un vrai témoin causal et son admission complète ; ne pas confondre
ouverture de capacité, changement de sampling et gain Skia. W7 reste actif.

## Attribution des intensités — 2 octobre 2026

Le [diagnostic couleur](color-authority-diagnostic.md), relu par Sol C0/I0,
écarte l'hypothèse des primaires erronées fondée sur des PNG ICC bruts. Le chemin
existant reproduit les scores qualifiés après normalisation sRGB, sans changement
produit/référence/score. Une précision Minor de formulation est appliquée.
Les 129 échantillons des rampes sRGB sont maintenant archivés ; prochaine
expérience : isoler placement/couverture et composition par un témoin natif
indépendant. Aucune
correction renderer/couleur/codec n'est encore justifiée. W7 reste ouvert.

## CTM des paths — 2 octobre 2026

[Plan et qualification locale](scaled-stroke-diagnostic-plan.md),
[snapshot complet](path-ctm-4e4b699a6.json), branche
`codex/w7-scaled-stroke-diagnostic` empilée sur#2434/7658d902b.
Le vrai GM transformé reproduisait un contour absent malgré le succès natif.
Sur avis ciblé Astra, GmCanvas conserve maintenant path/paint source sous
un même CTM pour tous les paths nonidentity, pas une largeur scalaire devinée.
12intégration +4Surface +2math PASS ; la fixture pré-CTM corrigée est rejouée
12PASS, Sol ferme les Important. Source private4e4b699a6 identique aprèsd5b.
Revue finale Astra7658..4c3d approuvée pour draft,C0/I0 nouveaux,
M1warnings hérité/M2pathops ouverts ; draft[#2435](https://github.com/ygdrasil-io/kanvas/pull/2435)
publiée/rattachée sur#2434,base7658d902b et headinitial2e044cd8e distants
identiques vérifiés ; corps conforme, CI non inspectée, aucune fusion.

**217rendus/194comparés inchangés,47≥99%(+1),63≥95%(=)** à631/443 figé.
Aucune perte d'admission,212RGBA identiques,cinq changés.
crbug arc/diamètre présents,98.7686%,SSIM0.989508 ; il reste nonISO.
ctmpatheffect99.7546/teenyStrokes99.5228 gardent des écarts d'intensité visibles ;
les aperçus ICC bruts ne prouvent pas des couleurs pleines erronées ;
pathops garde95.7428 malgré pixels différents ; sharedcorners reste de mauvaise
taille versusréférence, sans score inventé. Quatre triples et ce dernier couple
inspectés. Cinq PNG frais ont les mêmes pixels que le corpus.
Quatre scores rafraîchis,555autres inchangés. pathops a une légère baisse de
SSIM0.7854852→0.7854735 même si son score corpus reste95.7428 ; garder OPEN.
23diagnostics ne changent que l'index ; trois reclassifications réelles restent
suivies sur des GM toujours refusés. ComplexClip2 Path/RRect inchangés.

Priorité Astra du diagnostic couleur/port désormais traitée : le vert brut ICC
diffère du vert sRGB mais les plateaux normalisés concordent. La suite porte
sur les intensités/couverture/placement/composition, pas sur une correction
spéculative de couleurs ou du décodage externe, sans changer référence/seuil.
AA=false de crbug reste différé, pas un nouveau relèvement de cap.
Les limites de clips/état, RRect/I2 et inverse/filter restent OPEN.
Suites globales héritées RED/incomplètes, pas de promesse de parité/W7 clos.
Carte blanche couvre les changements nécessaires, pas une cause présumée.

## Travail undashed SVG arc et refus ComplexClip2 — 2 octobre 2026, publié

Branche `codex/w7-complexclip2-resource-convergence`, parent draft#2433/ff3cc8398.
[Plan de diagnostic](complexclip2-resource-diagnostic-plan.md), puis
[plan math](undashed-arc-work-plan.md), source/tests
`c0567497f217337012f39284b4701edc74093760`,
[snapshot complet](undashed-arc-c0567497f.json).
W4d/W4e retiennent le premier index et les raisons math réelles ;
les quatre refus initiaux sont FrameWorkLimit, pas une hausse nécessaire du cap.
Sur avis ciblé Astra, une certification F64 conservatrice élimine la récursion
de longueur inutilisée uniquement pour les arcs SVG sans tirets.
Source/order/closure/spans et budgets restent intacts, repli mesuré pour
chaque cas non prouvé ; pas de refonte Bézier ou de raccourci GM.

RED2/21 puis GREEN21/21,798math JVM PASS et JS effectivement compilé,
six témoins natifs PASS. Sol Task1 C0/I0/M1 warnings hérités.
Covering481/484PASS, tous les456statuts parent identiques ;28cas existants
additionnels PASS. Intégration10/11PASS : le test stroke sélectionné en plus
attend un vieux diagnostic, déjà contredit par le vrai corpus parent ;
première assertion FAIL conservée, suivantes non atteintes, aucun oracle changé.
Globale725END=687PASS37FAIL1interruption à240s, mêmes IDs/types/statuts,
35messages identiques et2adresses Diagnostics seules ; non exhaustive.
399PASS/2PictureFAIL supplémentaires hors globale dans le covering.
Pas de globale verte/merge/W7 clos.

**217rendus(+5),194comparés(+5),46≥99%(+2),63≥95%(+3)** à631/443 constant.
212anciens hashes/métriques identiques, aucune perte,608cas hors temps
identiques ;5admissions et18diagnostics enrichis/progressés, pas23gains.
PathBW99.9123%,PathAA99.0614% ; RRectBW94.7524%,RRectAA94.2213%.
`crbug_691386`98.3154% mais presque vide : score de fond, contour manquant
explicitement ouvert. Les cinq triples visuels sont inspectés ; RRect possède
des écarts de zones remplies et une cellule entière, pas seulement d'AA.
Les cinq artefacts ciblés sont régénérés byte-identiques au corpus ; cinq
sélections natives1PASS chacune, cinq scores seuls changent. Le score historique
crbug98.49 n'était pas un rendu parent qualifié ; sa mise à jour98.315 garde
le gap de contenu visible. La revue finale Astra approuve la draft locale.

Relecture finale Astra ff3..6423 : Spec/Quality locales approuvées, draftYes,
mergeNo/W7No, C0/I2/M1 exposés/hérités, introduits démontrés0/0/0.
Les deux Important ne sont pas corrigés ; le Minor reste suivi.
**Priorité suivante unique : crbug691386**, plus petit que les25cellules RRect.
Comparer unité/CTM96+translate1.25,width0.025 à contour écran/identity,width≈2.4,
deux spans math littéraux et pixels natifs black/background indépendants du
score global. Arrondis F32/F64 explicites, pas d'égalité bit-exact forcée.
Départager espace de largeur/tolérance, outline fermé et couverture aval ;
arrêter à la première frontière fautive avant le patch. RRect différé, ne pas
traiter ce98% comme une réussite et ne pas lancer les deux chantiers.
Le chantier inverse/filter337/338 reste ouvert, sans forcer l'admission
single-sample ni relever caps/epsilons/seuils. Carte blanche permet les
changements nécessaires, pas une approbation anticipée de cause ou de parité.

Draft [#2434](https://github.com/ygdrasil-io/kanvas/pull/2434) publiée/rattachée
sur #2433, base ff3cc8398461bb776115ee5d4dcb709f62bc04aa et head initial
b8d5f9f9ad0f7082899a076336fd1b3c8d64b031 distants vérifiés. Corps conforme ;
produit/tests inchangés après c0567497f. CI distante non inspectée, pas de merge.

## Port ComplexClip2 — 2 octobre 2026

Source/tests `092a293be0d37534769b32fa774faa56d1231952`,
[snapshot](complexclip2-092a293be.json), [plan](complexclip2-port-plan.md),
branche `codex/w7-complexclip2-port`, draft
[#2433](https://github.com/ygdrasil-io/kanvas/pull/2433) publiée/rattachée sur
draft#2432/d958bd26c, headinitial1eef80b79 distant identique vérifié.
Après source092a293be, seuls documents/snapshot/PNG/scores changent.
Le port primaire corrige RNG/paint sans toucher clip-AA ni références ; les
témoins natifs révèlent ensuite deux frontières W4e incohérentes. On retient
les faits compiler-owned du masque binaire non clippé et on les consomme
via le même contrat host/native. Ni budget élargi ni nouvelle géométrie.

**Gain pixel réel : complexclip2 79.70→100%±2, rect-AA79.70→99.075%±2**.
Pas bit-exact : exact96.515%/95.791%. Corpus631/443 inchangé,212rendus/189comparés,
44≥99%(+2),60≥95%(+2),médiane75%. Deux seuls rendus changent,
210hashes/métriques et629cas hors temps identiques ; aucune perte.
Les quatre Path/RRect restent refusées : port fidèle ne signifie pas admission.

Six témoins/10intégrationPASS, Sol C0/I0/M1 ; covering453/456PASS avec les trois
échecs hérités, globale bornée687PASS/37FAIL/1interruption sur725END.
Même37échecs atteints, au moins411identités non atteintes sur borne1136 :
pas de globale verte/merge/W7clos. Deux PNG/scores régénérés byte-identiques au
corpus, deux sélections natives1PASS chacune, sans skip. Revue finale Astra
d958..b56 : C0/I0/M0 introduits, ancienI1du port corrigé, M1warningshérité ouvert.
Publication draft approuvée, fusion non qualifiée ; aucun fixwave produit.

Après publication, priorité choisie avec Astra : diagnostic causal borné des
quatre refus ComplexClip2 Path/RRect122/123/125/126, même frontière
`w4e.clip.geometry-limit: W4d.2 rejected normalized W4e draw resources`.
Identifier le premier draw/resource normalisé fautif, avec les deux contrôles
proches≥99%, avant toute extension. Ce n'est pas une promesse de quatre gains.
Conserver caps/budgets et autorité math, aucun GM routing. Si la limite est
légitime et exige une refonte large, comparer son coût au chantier inverse/filter.
`inverse_fill_filters`337/338, source-AA single-sample et root PATH/WINDING/AA
sousW6b restent ouverts : autorité originale, source/halo, domaine inverse fini,
clip terminal à distinguer. Témoin public causal avant tout fix, sans oracle
relâché. Carte blanche pour les changements nécessaires, pas un claim ISO.

## Assemblage inverse/hairline — 2 octobre 2026

Source/tests `2e5419afd6501cfc9d09dbed8409324149450a4c`,
[snapshot](inverse-hairline-2e5419afd.json),
[design](inverse-hairline-assembly-design.md),
[plan](inverse-hairline-assembly-plan.md).
Branche `codex/w7-inverse-filter-convergence`, draft
[#2432](https://github.com/ygdrasil-io/kanvas/pull/2432) publiée/rattachée sur
parent#2431/9c182355b. Head distant initial118aee3d9 identique au checkout vérifié ;
les commits après2e5419afd ne changent que documents/artefacts générés.
Deux gates Sol approuvés puis revue large Astra : aucun défaut introduitC/I/M,
un Important hérité de port et un Minor hérité d'environnement suivis.
Source qualifiée pour publication draft, pas pour merge.

Le fix sépare normalisation AA solide et admission Rect dans les deux seams
W4e ordinaires. Le shader BinaryConsumer inverse le path, pas le clip.
La source root Rect/STROKE existante est sélectionnée sousW6b uniquement depuis
le draw original non filtré ; aucune autorité issue de faits stripped ni ouverture
de Picture/layer/guard single-sample. Sept tests retenus passent réellement
(Kanvas6 +GmCanvas1), pixels littéraux/native/repeat ; six probes diagnostiques
inchangés gardent3PASS/3FAIL au garde W4e de source-AA.

Qualification :449/452natifsPASS, trois échecs hérités. Les deux Picture
nouvellement rencontrés ont aussi échoué sur le parent exact exécuté séparément.
Globale bornée240s :729END,691PASS/37FAIL/1SKIP d'interruption, incomplète ;
mêmes37échecs atteints,0nouveau,403nonatteints connus sur une borne1132,
pas une preuve exhaustive. Warnings natifs/Gradle conservés.

Corpus631/443 : **212rendus(+5),189comparés(+5),42≥99%(=),58≥95%(+1),
médiane75%**. Aucun ancien rendu perdu/modifié :207hashes/métriques identiques.
Les18invariants restent figés ;44deltas hors temps=5admissions+39diagnostics.
Small_sigma96.707%,complexclip2/rectAA≈79.699/79.698%,
localmatrix66.462%,offset82.825% : le gain d'admission n'est pas l'ISO.
Cinq triples visuels inspectés, cinq PNG générés byte-identiques au corpus,
cinq seuls scores rafraîchis via le runner existant ; ses PASS au seuil0
ne prouvent pas la parité. Aucun changement de référence ou d'exclusion.

**Prochaine priorité : port fidèle des six variantes ComplexClip2**, choisi
avec Astra avant une nouvelle capacité de source root W6b. Sources Skia figées
defc3a5a92966c32cb2a6a901e2fa3036a13bb8a : stream SkRandom.nextU()%2,
peintures explicitement non-AA, clip-AA conservé. Le helper SkiaRandom existe ;
ordre boucles/index et remplissage fini50×50/hairline0 restent fidèles.
Tests publics visibles/native/repeat puis corpus identique ; aucun gain promis.
La révision primaire établit ces écarts de port, pas l'origine des PNG.

Après ce lot borné, GM337 reste au guard source-AA et GM338 au premier root
PATH/WINDING/AA ordinaire non filtré sousW6b. Le contrat architectural devra
séparer autorité originale, demande source/halo, domaine inverse fini et
clip terminal. Localmatrix3colonnes absentes/API, offset/font exclu,
globale, W7/W6/W0 et merge restent ouverts ; aucune clôture globale.

## Inverse direct / Picture inverse-AA — 2 octobre 2026

Produit/tests `38c75ab120494be5889929a2e7afc9195d3905a3`,
[snapshot final frais](inverse-inventory-38c75ab12.json),
[design](inverse-native-authority-design.md),
[plan](inverse-scene-inventory-plan.md),
[extension Picture](inverse-aa-picture-plan.md).
Branche `codex/w7-inverse-scene-inventory`, draft
[#2431](https://github.com/ygdrasil-io/kanvas/pull/2431) publiée/rattachée sur#2430 ;
revue finale indépendante Astra :1Important/3Minor, tous fermés par une vague
finale et contre-revue ciblée Sol, aucun nouveauC/I/M. Parent exact0dce69805
et head distant initialaf3d41480 vérifiés ; commits de suivi documentaires.

La paire root producer/cover et l'inventaire exact sont authentifiés jusqu'au
natif. Une capacité source distincte porte l'inverse-AA Geometry/SrcOver
des vraies Picture vers le graphe W6, sans réutiliser l'autorité root.
La recette scelle domaine/origine/extent et les deux géométries/clip/payloads
source-final ; la projection numérique est dans `math`. Sol accepte les
corrections de ces trois points. Le masked inverse-AA non pris en charge
refuse avant allocation, avec sentinel/discard/récupération vérifiés.

La preuve target-local/device prend en charge le plain layer décalé, sans
confondre clip inverse et target complet ni translater les bytes deux fois.
Recette val, opérations/ordre immutables et unique index root sont qualifiés.
Au SHA produit :342/343 natifs affectés passent, seul échec W5bNoOp hérité ;
788 événements math passent (478geometry+310matrix, XML final matrix310seulement).
JS compile UP-TO-DATE après le candidat byte-identique compilé.
La globale reste incomplète à240s,687PASS/37FAIL/1SKIP, mêmes725cas/statuts
et37échecs atteints, sans nouvel échec atteint. Les298tests non atteints
identifiés ne constituent pas l'univers exhaustif ;272passent séparément
dans le gate affecté. Pas de claim globale verte ni de merge.

**Aucun gain de parité GM** dans ce lot :207rendus/184comparés,42≥99%,57≥95%,
médiane74.06067251461988%, exactement comme le corpus précédent.
Les207hashes/métriques et631lignes hors temps sont inchangés,18invariants
préservés, références/domaines/exclusions/seuils inchangés,vertices30s conservé.
Aucune image nouvelle/modifiée, donc pas de régénération de scores/dashboard.
Le progrès des scènes publiques est réel, mais ne vaut pas un gain corpus.

**Pilotage suivant : une cause mesurée d'un GM bloqué avant une nouvelle
extension générique.** Les42premiers `unsupported_child` couvrent plusieurs
familles et ne promettent pas42gains. Astra conseille `inverse_fill_filters`337,
puis contrôle338 une fois la cause établie : trois cellules puis cercle inverse
et hairline séparés. Un diagnostic public causal précède toute admission,
avec un gate sur le corpus, sans retoucher une référence pour masquer un refus.
Conjonction alpha128 fractionnel sur destination opaque, rendu positif
masked inverse-AA, globale, autres gaps W7/W6/W0 restent ouverts.

## Port inverseclip fidèle — 1er octobre 2026

Source/test `e9da0ebd6892419987356e5a8d4803cb1a7a0023`,
[snapshot](inverseclip-port-e9da0ebd6.json), [plan](inverseclip-port-plan.md).
Branche `codex/w7-inverseclip-port`, draft
[#2430](https://github.com/ygdrasil-io/kanvas/pull/2430) publiée/rattachée sur#2429 ;
revues Sol/Astra favorables à la publication draft, sans Critical/Important
ni nouveau Minor ; warnings hérités conservés, merge non qualifié.

Le port applique maintenant l'inverse clipPath AA puis le rectangle bleu
non-AA, comme Skia ; il ne dessine plus un path inverse blanc sur fond bleu.
Pas de changement du renderer, de GmCanvas, des références ou des seuils.
RED causal sur le port initial, puis **41/41 témoins natifs frais sans skip**.
Un PNG et sa seule entrée de score sont régénérés et inspectés.

Corpus631/443 : **207 rendus (+1),184 comparés (+1),42≥99 % (+1),57≥95 % (+1)**.
Seul `inverseclip` gagne un rendu : exact99.32875 %, ±2/canal99.340625 %,
SSIM0.9960695474233923. Aucun ancien rendu ne change :206hashes/métriques
identiques,630autres identités inchangées hors temps,18invariants préservés.
Le résidu de contour n'est pas clos ; le profil Rec.2020 de la référence
est normalisé par le comparateur existant, sans modification de codec.

**Suite : isoler par Surface le mélange ordinary+inverse direct**, comparé
au dessin inverse seul et au clip inverse fidèle, pour identifier la
conjonction fautive de l'inventaire W4e avant tout changement d'autorité.
Ce gain de port ne résout pas ce gap architectural ni le contrat W6 AA4.
Globale renderer be813afd7 rouge/incomplète, vertices timeout30s,
warnings, fidélité générale, gates W6 et quarantaine W0 restent ouverts.
W7 n'est pas clos ; aucune décision de merge.

## Ports complexclip4 / manypathatlases fidèles — 1er octobre 2026

Source/test `6e0fca2df5aef733bb516ddd2b714dd342920ab4`,
[snapshot](clip-gm-ports-6e0fca2df.json), [plan](clip-gm-ports-plan.md).
Branche `codex/w7-clip-gm-ports`, draft
[#2429](https://github.com/ygdrasil-io/kanvas/pull/2429) publiée sur#2428 ; verdict final
Astra favorable à la publication, sans Critical/Important/Minor restant.

Les deux erreurs de port constatées dans le lot précédent sont corrigées :
Difference+AA pour les feuilles ; restrictions device persistantes et fixées
avant CTM pour complexclip4. Le renderer, GmCanvas, les références, domaines,
seuils et exclusions ne changent pas. L'adaptation RGB du remplissage final
est prouvée suffisante pour toute la restriction de cette scène ; elle ne
résout pas DrawColor général sous clip analytique complexe.

40/40 témoins natifs frais passent sans skip ; RED causal4/4, revue Sol et
contre-relecture approuvées. Quatre PNG/entrées de score régénérés et inspectés.
Corpus631/443 : **206 rendus,183 comparés,41≥99 % (+2),56≥95 % (+4)**.
Aucune admission/perte ; seuls quatre hashes et leurs métriques évoluent,
202autres rendus inchangés et18invariants préservés. complexclip4 passe de
85.29/85.40 à99.926/99.999 % ; manypathatlases de33.325 à95.325 % (±2/canal).
Les résultats exacts complexclip4 restent80.38/80.42 %, donc pas d'ISO exact.
Le diff binaire exact et les profils PNG distincts ne doivent pas être lus
comme une amplitude d'erreur. Aucun changement du comparateur ou du codec.

La globale rouge/incomplète reste celle du renderer be813afd7, pas une
nouvelle globale au SHA des ports. vertices reste timeout30s. L'erreur
initiale de sélection par indices a écrit zéro PNG et fini Java133 ;
les huit exécutions utiles par noms exacts terminent0/0. Détails dans le status.

**Prochaine priorité architecturale : diagnostiquer l'inventaire inverse W4e
et le contrat commun source clip-free / préfixe W4e figé / consumer différé
W6 AA4**, en isolant un témoin public avant élargissement. Les gains de ce
lot confirment aussi la nécessité de vérifier les ports avant d'attribuer
un défaut au renderer. Les42premiers unsupported_child ne sont pas42gains
promis. Résidus de contour/couleur, fidélité générale, globale, warnings,
gates W6 et quarantaine W0 restent ouverts ; ni merge ni clôture W7.

## Scissor de clip / resolve root AA4 — 1er octobre 2026

Produit `be813afd75da9e094a9368c65a0d9fc31b2598a9`,
[snapshot](clip-producer-scissor-be813afd7.json), [plan](clip-producer-scissor-plan.md).
Branche `codex/w7-clip-producer-scissor`, draft
[#2428](https://github.com/ygdrasil-io/kanvas/pull/2428) publiée sur #2427 avec
verdict de qualification Astra favorable à une draft, pas au merge.
Source corrigée et relue (Sol + Astra),
347 témoins natifs ciblés et compilation JS passent.

Mesure631/443 : **206 rendus,183 comparés,39≥99 %,52≥95 %**.
Six admissions nouvelles, zéro perte, aucune modification des200 anciennes
images ;18invariants inchangés. C'est un gain de capacité, pas six GM conformes.
Les images révèlent de vrais débordements dans `complexclip4_aa/bw`
(85.29/85.40 %) et la perte de silhouette de `manypathatlases_128/2048`
(33.33 %). `circular-clips` reste à65.46 %, `clipsuperrrect` sans référence.
`inverseclip` progresse jusqu'à un refus d'inventaire W4e, sans rendu.
Aucun seuil, référence, domaine, corpus, cap ou exclusion modifié.

La globale bornée reste rouge/incomplète :686SUCCESS/37FAILURE/1SKIPPED,
724END,240s/124–143. Trois échecs hérités corrigés, zéro nouveau parmi les
atteints ;27identités de la baseline751 non atteintes. L'assertion Bounds
supplémentaire est reproduite au parent (shutdown executor133 non diagnostiqué).
Les trois scènes W6 complex-AA4 restent des refus contrôlés avec récupération,
pas une preuve positive W6ConstantZero4x. Détails et evidence dans le status.

**Priorité du prochain lot : attribuer les défauts visuels nouvellement exposés
avant de chercher d'autres gains d'admission.** Auditer les ports
`complexclip4` et `manypathatlases` face à leurs sources Skia, puis isoler
un témoin public minimal discriminant défaut de port / clip / géométrie /
composition. Ne pas traiter le score élevé de fond ou la seule alpha opaque
comme preuve de justesse. Le diagnostic déterminera un correctif transversal
ou un port fidèle, sans routage par nom de GM. Pas de correction spéculative.

Ensuite : résoudre l'inventaire inverse W4e et le contrat commun
source clip-free / préfixe W4e figé / consumer différé W6 AA4. Les42premiers
`unsupported_child` ne sont pas42causes ni42gains promis. Couleur Sk3d,
allocation temporaire Path, warnings JDK/Gradle, globale et gates W6 restent
ouverts ; ni merge ni clôture W7.

## Port Sk3d fidèle / Picture hard — 1er octobre 2026

Produit mesuré `813e61f098317750c3a8a1d98dea185629ead38e`,
[snapshot](sk3d-port-813e61f09.json), [design](sk3d-port-design.md),
[plan](sk3d-port-plan.md), branche `codex/w7-sk3d-port`, draft
[#2427](https://github.com/ygdrasil-io/kanvas/pull/2427) publiée sur #2426.
Task1/Task2 approuvées par Sol, revue globale Astra puis unique contre-relecture
Sol du correctif `5a931c87a` approuvée, sans nouveau Critical/Important/Minor.

Le port rétablit caméra perspective/rotation Y, alpha136/255 et hard edge,
sans supprimer la vraie Picture Rect+CTM. L'owner hard Picture et sa source
Rect W4d sont fermés, les lanes analytiques conservées. Les clips de source
sont admis avant cull/carrier, avec priorité par entrée de leur provenance
sur leur transformation ; les refus historiques et les empty/no-op sont
préservés. Les helpers numériques sont dans math, sans nouveau cap/epsilon.

`sk3d_simple` passe de51.931111111111115 % à77.62444444444445 % de pixels±2,
SSIM0.6385013748432061→0.9828793554600629. L'inspection actual/référence/diff
confirme le net rapprochement de silhouette, mais une couleur intérieure
différente persiste. Aucun changement du domaine LINEAR ou des références
pour augmenter le score ; aucune parité couleur/contour exacte revendiquée.

Corpus631/443 inchangé :200rendus,178comparés,39≥99 %,52≥95 %,
médiane73.26467803030303 % contre72.34801136363637 %. Les18invariants,
outcomes, diagnostics, dispatches/refus sont conservés ; hors Sk3d, aucun
hash ni métrique ne change et aucun ancien rendu n'est perdu. `vertices`
garde son timeout30s. Mesure neuve strictement séquentielle ; l'essai
ff3e3bb chevauché est conservé mais exclu de la qualification.

263/263 témoins publics ciblés passent au produit813e61f09 avec processus0/0
et XML propres. Après revue, le trou even-odd dispose d'un témoin rouge non
masqué par le dessin vert suivant ; 103/103 validations fraîches public94/GM9
passent au correctif test/KDoc, mêmes sorties propres. Code exécutable inchangé,
pas de nouveau covering160/global/corpus attribué à ce correctif.
La globale240s reste incomplète :688SUCCESS/40FAILURE/1SKIPPED,124/143,
mêmes40échecs atteints que la baseline,22 anciennes identités non atteintes.
Pas de nouvelle assertion en échec atteinte, ni de claim globale verte.

Priorités encore ouvertes : attribuer l'écart couleur sans modifier l'oracle,
réparer séparément l'autorité de scissor cible-local W4e, puis poursuivre les
causes transversales mesurées. Le groupe unsupported_child compte toujours
42premiers diagnostics, ce n'est ni42causes indépendantes ni42gains promis.
Suivis non bloquants : limiter les allocations temporaires O(taille du Path)
de l'admission numérique sans réimplémenter les arcs ; résoudre les warnings
hérités Gradle/LWJGL native access et `sun.misc.Unsafe` avant upgrade JDK.
Ni merge, ni clôture W7/W6/W0.

## Qualification Picture / Porter-Duff — 1er octobre 2026

Code produit mesuré `3398dc3db8c8741d49234c74643f464baae645f6`, snapshot
[`picture-3398dc3.json`](picture-3398dc3.json). Le correctif scoped conserve
la lane analytique W4a des Rect `identity`/`scale-translate` de plain layer et
réserve W7 au fait existant `GeneralAffine`, sans modifier les routes root ou
Picture. Son témoin public a un RED causal au B complet `26808` (sans le garde :
`requires 28736`) et un GREEN avec le garde. Les témoins publics sont **186/186**
(185 identités antérieures + un budget, archive
`layer-preservation-covering-186-1`, wrapper/enfant 0/0, zéro
failure/error/skip/stderr). La relecture ciblée Sol est approuvée : I-new et
I1 résiduel corrigés, aucun nouveau Critical/Important/Minor. La draft
[#2426](https://github.com/ygdrasil-io/kanvas/pull/2426) est publiée sur #2425,
après revue globale Astra et corrections. Ni merge ni clôture W7.

Corpus : 631/443, 200 rendus, 178 comparés, 39 ≥99 %, 52 ≥95 %, médiane
72.34801136363637 % à ±2. Les 18 invariants, hashes, métriques, outcomes,
diagnostics, dispatches et refus sont inchangés face à `picture-8829d18`; aucune
perte ou nouvelle image n'appelle une inspection visuelle. Face au hardstop,
`sk3d_simple` rend nativement (51.931111111111115 % à ±2, SSIM
0.6385013748432061, max [136,255,119,0]) mais son actual est un large carré
magenta tourné contre un quadrilatère violet étroit de référence : gain de
capacité, pas parité ni diagnostic d'attribution. `PlusMergesAA` reste le gain
historique. `vertices` index 607 conserve son timeout 30 s.
La globale bornée a 751 END (710 SUCCESS/40 FAILURE/1 SKIPPED), timeout
wrapper/enfant 124/143; les 725 identités précédentes sont présentes, mêmes 40
failures, 26 W5e supplémentaires sont atteintes (25 SUCCESS, un SKIPPED),
`cubicDrawImageMatchesMitchellNetravaliOracle` SKIPPED→SUCCESS. Toutes les 718
identités Task1 restent présentes, `rowPadding` SKIPPED→SUCCESS.

Le checkpoint antérieur `picture-7488067469.json` n'est pas conservé dans le
repo : ses journaux privés restent disponibles sous
`/private/tmp/kanvas-w7-aa-blend.rFtTrn/task4-corpus-parity-7488067`. Sa perte
unique `lattice2` a été localisée à la priorité W6 d'un Rect AA SRC qui fragmentait
un frame `ImageLattice`. Le correctif final privilégie W5e seulement lorsqu'il
est candidat pour le frame root sans layer/W6b; le hash `lattice2` restauré est
`49d38b8f277d292c029ab9a7c9e5f19c6300821d3c9b21af6af150384ff02da0`.

Baseline : PR draft [#2411](https://github.com/ygdrasil-io/kanvas/pull/2411),
empilée sur [#2410](https://github.com/ygdrasil-io/kanvas/pull/2410).
Lot standalone : PR draft [#2412](https://github.com/ygdrasil-io/kanvas/pull/2412),
empilée sur #2411, routage standalone rect/path, renderer `718445e6e`.
Lot précédent : pointillés réparés, expérience AA retirée après mesure,
renderer `5f971f750`, PR draft [#2413](https://github.com/ygdrasil-io/kanvas/pull/2413)
empilée sur #2412.
Lot cache de preuve CPU : renderer `b256b3d68`, PR draft
[#2414](https://github.com/ygdrasil-io/kanvas/pull/2414), empilée sur #2413.
Lot image/opacité : renderer `bef3af6fa`, PR draft
[#2415](https://github.com/ygdrasil-io/kanvas/pull/2415), empilée sur #2414.
Lot preuve Sweep AA : renderer `49d8224d3`, PR draft
[#2416](https://github.com/ygdrasil-io/kanvas/pull/2416), branche
`codex/w7-sweep-aa-proof`, empilée sur #2415.
Lot adaptateur Rect+CTM : code `d45904e0b`, branche
`codex/w7-layer-source-routing`, PR draft
[#2417](https://github.com/ygdrasil-io/kanvas/pull/2417), empilée sur #2416.
Lot précédent : source AA racine, renderer `470f62e63`, branche
`codex/w7-root-aa-source`, PR draft
[#2418](https://github.com/ygdrasil-io/kanvas/pull/2418), empilée sur #2417.
Lot précédent : couverture AA filtrée, renderer `82893045c`, branche
`codex/w7-aa-mask-coverage`, draft
[#2419](https://github.com/ygdrasil-io/kanvas/pull/2419) empilée sur #2418.
Lot précédent : Rect stroke AA dans un mélange racine, renderer `ff628a94d`,
branche `codex/w7-mixed-root-aa-rect`, draft
[#2420](https://github.com/ygdrasil-io/kanvas/pull/2420) empilée sur #2419.
Lot précédent : politique alpha du LinearGradient, renderer `09d9574b5`,
branche `codex/w7-gradient-alpha-mode`, draft
[#2421](https://github.com/ygdrasil-io/kanvas/pull/2421) empilée sur #2420.
Lot précédent : domaine de composition Surface, branche
`codex/w7-surface-composition`, draft
[#2422](https://github.com/ygdrasil-io/kanvas/pull/2422) empilée sur #2421 ;
validation ciblée et revue finale terminées, réserves ci-dessous.
Lot précédent : Rect hairline entier et encodé, renderer `f80d94fb4`, branche
`codex/w7-encoded-hairline`, draft
[#2423](https://github.com/ygdrasil-io/kanvas/pull/2423) empilée sur #2422 ;
mesure terminée et réserves explicites ci-dessous, sans merge.
Lot précédent : port fidèle d'alphagradients et domaine explicite des GM,
code `6259c38d8`, branche `codex/w7-alphagradients-port`, draft
[#2424](https://github.com/ygdrasil-io/kanvas/pull/2424) empilée sur #2423 ;
mesure et reviews terminées ci-dessous, sans merge.
Lot précédent : ports fidèles des deux hardstop, code `34e3d4e98`, branche
`codex/w7-hardstop-ports`, draft [#2425](https://github.com/ygdrasil-io/kanvas/pull/2425)
empilée sur #2424 ; mesure et reviews terminées
ci-dessous, publication draft uniquement.

Série courante qualifiée (sans clôture W7) : `codex/w7-aa-blend-sources`, draft
[#2426](https://github.com/ygdrasil-io/kanvas/pull/2426) sur #2425,
[design](aa-blend-sources-design.md) et
[plan](aa-blend-sources-plan.md). Correction W5 PLUS couvert revue et validée
ciblée ; premier témoin root Path PLUS du consommateur GPU validé au code
`6f07a6448` (couverture pleine, nulle et partielle, répétition native).
L'émetteur partagé est extrait (`fa84fa81d`) et l'ownership Rect racine corrigé
(`2dbefcdb9`). Le checkpoint natif compte 25 succès/25 (processus 0, sans
timeout) : huit cellules root/layer, quatre formes concave/even-odd, le premier
témoin PLUS, huit contrôles root historiques et quatre témoins PLUS couvert.
SRC_OVER utilise des contrôles alignés après rejet indépendant de sa fixture
de bord ; aucun oracle n'est élargi. Le partage physique des uniformes W5/W7
est corrigé (`0980fab24`, contrôle natif 22/22). B=27 804 et B−1 avec sentinelle
et récupération passent (`9b822cee1`, contrôle couvrant 23/23). Les témoins
numériques et mapping passent (`b4d48388a`, 33/33), avec une correction du
matériau normalisé transparent. Les 31 identités AA historiques et PointV2
sont qualifiés ; 58 tests uniques passent au code final. `PlusMergesAA` rend
désormais sans refus, mais reste à 69,4824 % de pixels identiques (SSIM 0,985107,
écarts couleur R/G, alpha identique). Après deux corrections de revue
(`a851621fc`), Sol accepte Task2 : **59/59** tests ciblés et image GM inchangée.
Porter-Duff implémenté au code `dd498a4bc`, corrigé par `7953b2116` : 52 cellules
root/layer positives, covering110/110, sorties0/0, tous les témoins précédents
conservés ; relecture Sol approuvée. L'ordre inverse observe désormais DST_OUT,
et le nouveau scope W7 reste affine non singulier avec refus/récupération testés.
Les lanes analytiques Rect des layers sont préservées ; les
nouveaux Rect root sont projetés via W4d. SRC_ATOP reste qualifié sur une
forme alignée. `aarectmodes` refuse encore (unsupported_child non isolé).
À ce checkpoint historique, Picture et le corpus complet restaient à venir ;
aucune parité globale n'était revendiquée.
L'[état courant](status.md#série-aa-et-composition-différée--livrée-en-draft-1er-octobre-2026)
distingue les preuves natives réussies des limites historiques et de
l'exit 133 W5g non qualifié. À ce checkpoint historique, le corpus mesuré
ci-dessous était la baseline.

Objectif : rapprocher les pixels du corpus Skia éligible, avec une mesure par
identité de GM, une durée bornée et des régressions explicites. Les fonts,
codecs et `jpg-color-cube` conservent leurs exclusions documentées. Les limites
de rendu et timeouts éligibles restent au dénominateur.

## Boucle de décision

1. Mesurer le code actuel contre les références inchangées ; conserver commit,
   corpus, empreintes des références, configuration, résultat et durée par GM.
2. Distinguer setup, refus de rendu, timeout, comparaison impossible et pixels
   comparés. Un rendu disponible ne vaut pas une conformité visuelle.
3. Classer les causes par nombre de GMs affectées et sélectionner une cause
   transversale, puis réduire un cas réel en témoin public si nécessaire.
4. Corriger et rejouer les mêmes identités. Toute disparition, changement de
   scope/référence/tolérance ou régression est signalé séparément du gain.
5. Publier le lot en PR empilée avec le bilan avant/après. Astra sert au
   diagnostic difficile ou à l'arbitrage architectural ; les validations
   intermédiaires ne remplacent pas les pixels.

Les gates W6 relatives à la durée de vie et aux ressources restent suivies.
Leur fermeture et la proximité visuelle sont deux mesures distinctes.

## Lot ports hardstop fidèles — 30 septembre 2026

[Contrat et plan](hardstop-ports-plan.md),
[snapshot complet](hardstop-ports-34e3d4e98.json).
Les deux scènes restent en domaine LINEAR ; aucun moteur, shader, planner,
adaptateur, preuve numérique, budget ou configuration globale ne change.

`hardstop_gradients` conserve son image 512×512, mais retrouve la disposition
Skia calculée sur 500×500 : cellules 166×62, rectangles 160×56, marge 3 et
gradient horizontal de longueur 100. Couleurs, stops et modes CLAMP/REPEAT/
MIRROR sont inchangés. `hardstop_gradients_many` retrouve les 2N stops par
bande : bleu à 0, blanc puis bleu à chaque position interne k/N, blanc à 1.
Le rectangle XYWH(0,1,1000,18) couvre les lignes 1 à 18, et non 1 à 17.
Les paints des deux ports sont non-AA, comme les sources Skia épinglées à
`8019e2e0629f3516b9d829737de2553b1d0ecb4a`, relues indépendamment par Astra.

### Preuves et reviews

Le commit `02802171b` porte les deux corrections et les deux tests natifs
à image entière. Les attentes sont construites avant GPU, à partir de
données littérales indépendantes du produit : coordonnées/stops F32,
interpolation Double, sélection continue à droite, traitement des valeurs
hors CLAMP avant les doubles stops aux extrémités. Tous les pixels sont
vérifiés, sans retirer les ruptures : fond et alpha exacts, RGB ±2 dans les
rectangles. Le deuxième rendu doit être byte-identique, sans refus ni
diagnostic, avec dispatch natif effectif.

RED causal : deux échecs natifs sur les anciens ports, `(164,10)` dans une
marge de la grille et `(0,1)` dans la première rampe de many. Puis GREEN
séparés 1/1 et 1/1. Les premiers essais refusés par le sandbox et en échec
de compilation du test sont archivés séparément, pas comptés comme RED.

Sol approuve conformité et qualité, aucun Critical/Important. Astra confirme
la fidélité aux deux C++ épinglés et relève un Minor nouveau : le témoin
interpolé natif `(499,21)` exigeait RGB exact au lieu du ±2 déjà spécifié.
`34e3d4e98` aligne cette seule assertion au contrat ; l'oracle littéral reste
exact et l'alpha exact. Contre-relecture Sol : corrigé, sans nouvelle casse.
Le Minor des avertissements Java/LWJGL/Gradle reste hérité et différé.

Validation après la dernière correction : **9/9 tests, trois classes**,
toutes les sept identités de baseline conservées, zéro échec/error/skip/
doublon/runner, processus et wrapper 0. Aucun test d'infrastructure, mock,
forwarding ou inspection de code source n'est ajouté.

### Résultat mesuré et limites

Code mesuré exactement `34e3d4e980f7dce59260285e6523c002b45ad5cf` :

| GM | Pixels ±2 avant → après | Pixels exactement égaux après | Écart maximal RGBA |
| --- | --- | --- | --- |
| hardstop_gradients | 16,11328125 % → **100 %** | 90,472412109375 % | 2, 1, 1, 0 |
| hardstop_gradients_many | 10,65515 % → **100 %** | 96,382 % | 1, 1, 0, 0 |

SSIM respectifs 0,9999390936 et 0,9999963262 ; 25 et 101 dispatches, aucun
refus. Les PNG actual/diff ont été inspectés : les marques du diff signalent
les derniers écarts de 1–2 codes RGB, pas des écarts alpha. Ce résultat est
un gain du **port fidèle**, sans nouvelle capacité moteur ni preuve de
parité numérique universelle entre GPU. Les seuils historiques permissifs
à 0 sont inchangés ; le bilan repose sur les pixels, pas le statut PASS.

Le corpus reste **631 identités / 443 éligibles, 198 rendus et 176 comparés**.
Les **196 autres anciennes images**, dont alphagradients, sont strictement
byte-identiques. Aucune perte ni nouveau rendu, aucun changement d'identité,
de référence/empreinte, dimensions, scope, seuil ou diagnostic. Métadonnées
de domaines auditées séparément : 630 LINEAR et un SRGB_ENCODED inchangés.
On passe de 37 à **39 cas ≥99 %**, de 50 à **52 cas ≥95 %**, avec médiane
73,264678 % contre 72,010742 %. Restent 194 échecs de rendu, 50 de setup,
14 rendus non comparés, huit dimensions différentes et le timeout vertices.

Les tranches [0,607), [607,608), [608,631) terminent avec sorties Gradle/
wrapper 0/1/0. Le 1 correspond au timeout interne vertices de 30 s, processus
de mesure 124 ; aucun wrapper externe ne dépasse sa limite. Ce cas reste
au dénominateur. La globale historique reste **678 PASS / 40 FAIL /
1 interrompu / 25 non atteints**, non rejouée et non déclarée verte.
Les fonts, codecs/décodage externe et jpg-color-cube restent exclus.
Provenance du générateur PNG historique toujours inconnue ; fidélité aux
sources et proximité des références sont deux preuves distinctes.
W7 et les gates W6/W0 restent ouverts, aucune fusion autorisée.

Archives : `/private/tmp/kanvas-w7-hardstop.pVPuxL/`, dont `RED-task1c`,
`GREEN-grid-task1`, `GREEN-many-task1`, `FINAL-task1`, `FINAL-review-fix`,
`corpus-*` et `parity/`. Les journaux complets et PNG restent disponibles.

### Arbitrages de ce lot, dans l'ordre

1. Exécuter ce lot borné sous carte blanche, avec un contrat/plan compact et
   le workflow SDD choisi : risque de reprise réversible des scènes/tests
   sur une draft, sans nouvel aller-retour d'approbation.
2. Réparer les ports et conserver LINEAR avant d'envisager le moteur : un
   écart résiduel peut nécessiter un lot moteur distinct, jamais un ajustement
   des scores. La provenance des références reste une limite explicite.
3. Fixer avant GPU un oracle indépendant F32/Double, RGB ±2 et géométrie/
   alpha exacts : une divergence numérique valide sur un autre GPU peut
   demander un diagnostic supplémentaire, sans pixels ignorés ni seuil élargi.
4. Utiliser témoins natifs et corpus borné sans globale ni Runner écrivant
   les scores : les chemins non ciblés ne sont pas fraîchement exercés ;
   l'état global rouge/incomplet et les warnings hérités restent visibles.

### Suite sélectionnée : capacités des sources de layers

La prochaine boucle revient aux capacités moteur : 38 premiers refus
partagent `w6a.layer.unsupported_child`, sans prouver une cause unique.
La reconnaissance statique de `PlusMergesAA` distingue les triangles AA
SrcOver à la racine et les enfants AA PLUS dans une layer. Le contrat actuel
des sources AA n'admet que le fill solide SrcOver sans filtre ; retirer sa
garde ne créerait pas une sémantique de blend correcte. Isoler cette cause
sur Surface publique, puis évaluer une extension cohérente avant de mesurer
ses gains réels. Aucun support PLUS/filtre supplémentaire n'est livré ici.

## Lot alphagradients fidèle et diagnostic cohérent — 30 septembre 2026

[Design](alphagradients-port-design.md), [plan](alphagradients-port-plan.md),
[snapshot631](alphagradients-port-6259c38d8.json).
Le domaine de composition devient une propriété explicite de `SkiaGm` :
LINEAR par défaut, SRGB_ENCODED pour alphagradients uniquement. La déclaration
prévaut sur le domaine du config appelant ; tous ses autres champs restent
préservés par `copy`. Rendu, tentative terminale, inventaire et checkpoint
utilisent le même contrat. Le config effectif traverse aussi DiagnosticRunner
et tous les replays OpInspector, y compris leurs captures avant/après.

Le port reprend les24 couples fill/Rect hairline, les12 paires de couleurs,
le gradient diagonal et les placements Skia ; gauche STRAIGHT, droite
PREMULTIPLIED, paints non-AA, strokeWidth0. Aucun renderer/shader/proof/cap,
adaptateur ou CompositionEnvelope ne change. Le Minor du helper hairline
de #2423 est fermé : le contrôle de largeur précède la boucle des caractères.

### Preuves et reviews

Task1 `3a24bebb7` : RED natif191 attendu contre205, puis image640×480
indépendante complète, domaines par défaut/explicite, budget1 refusé, refus
AA encodé sur les trois entrées et récupération. Sol demande des assertions
de récupération complètes ; `7f8ac1ee5` compare les buffers entiers et vérifie
zéro refus/diagnostic, contre-relecture approuvée. Task2 `17bbcab1f` : RED
du PNG de replay187 contre127±2, puis conservation du config avec preuve
PNG réelle dans les deux modes (≤50 et >50ops), review Sol approuvée.

Astra ne relève aucun Critical/Important. Les deux Minor — taille16 du PNG
avant et témoin natif191±2 conforme à la spec, oracle indépendant toujours
exact191 — sont corrigés ensemble dans `6259c38d8`, contre-relecture Sol
sans nouvelle casse. **Validation finale7/7** (4AlphaGradients+3GmCanvas),
toutes les7 identités de Task2 conservées, sorties0 ; classe hairline
**13/13**, identités parent inchangées, exécutée après son unique modification.
Zéro skip/error/doublon/runner. Warnings Java/LWJGL/Gradle/unsigned hérités
restent explicitement présents ; les sorties ne sont pas dites « pristine ».

Les essais de compilation/setup ratés sont conservés et séparés des RED
causaux. Le premier oracle avait omis le bord droit inclusif : corrigé contre
le contrat géométrique préexistant, pas en copiant un résultat GPU. Aucun
test d'infrastructure, mock, forwarding ou inspection de source n'est ajouté.

### Gain mesuré, sans modification des critères

Le corpus final mesure exactement `6259c38d8c6e00fa72d25b9a3e62915c957c4ee0`.
Les trois tranches [0,607), [607,608), [608,631) sortent Gradle/wrapper0/1/0 ;
le1 est le timeout interne30s de vertices (processus mesure124), pas une
nouvelle panne ni un dépassement des wrappers externes.

**alphagradients :33,882161% →100% de pixels ±2**,94,528971% exactement égaux,
écart maximalRGB1/alpha0, SSIM0,9998747112,49dispatch/0refus. Les83256pixels
de fond et15840pixels de contour sont tous strictement égaux à la référence
décodée ; les208104pixels intérieurs sont tous à≤1 (191297exactement égaux).
Les PNG actual/diff ont été inspectés ; les points du diff ne représentent
que des écarts d'un code RGB. Un contrôle F64 indépendant sur184704pixels
intérieurs donne écart0 pour le rendu et≤1 pour la référence décodée. C'est
un gain du **port fidèle utilisant les capacités moteur déjà livrées**, pas
une nouvelle capacité moteur ni une preuve de parité universelle.

**631 identités/443 éligibles,198 rendus,176 comparaisons** ;197 anciennes
images autres qu'alphagradients sont byte-identiques, aucune perte/nouveau
rendu. Références et empreintes, dimensions, scopes, seuils, diagnostics et
résultats non ciblés inchangés. Métadonnée additive auditée séparément :
630LINEAR/1SRGB_ENCODED, y compris LINEAR sur le timeout vertices.
**37cas à≥99%** (contre36), **50 à≥95%** (contre49), médiane72,010742%.
Restent194 échecs de rendu,50 de setup,14 rendus non comparés,8 dimensions
différentes et1timeout. Le seuil historique0 d'alphagradients est inchangé ;
le gain ci-dessus provient des pixels, pas de son statut PASS permissif.

La globale Kanvas héritée reste rouge/incomplète :678PASS/40FAIL/
1interrompu/25nonatteints ; elle n'est pas fraîchement rejouée ni blanchie.
Les69/69 du moteur parent sont une preuve historique, pas une nouvelle suite.
Replay SetClip/layers approximatif, budgets des graphes partiels et Surfaces
internes des autres GM ne sont pas déclarés universellement corrigés.
Provenance du générateur PNG historique inconnue ; aucune conclusion sur
tous les domaines Skia. Fonts, codecs/décodage externe et jpg-color-cube
restent exclus. Pas de promesse ABI pour le module de tests diagnostiques.
W7 et les gates W6/W0 restent ouverts ; draft uniquement.

Archives : `/private/tmp/kanvas-w7-alphagradients.QVeKNw/`, dont
`RUN-red-alpha`, `RUN-final-hairline-getter`, `RUN-review-fix-round1-green`,
`RUN-task2-red-causal`, `RUN-task2-green-retry`, `RUN-final-fix-escalated`,
`corpus-*` et `parity/` (journaux et PNG). Analyse PNG read-only :
`/private/tmp/kanvas-w7-alpha-audit.S67HDe/png-samples.mjs`, déjà auditée.

### Arbitrages de ce lot, dans l'ordre

1. Exécuter sous carte blanche sans nouvelle approbation : risque de reprise
   réversible du port/config/design sur draft, pas de merge.
2. Faire primer le domaine du GM : un appelant voulant le remplacer doit
   désormais modifier la déclaration de scène ; les autres champs restent.
3. Témoins natifs + corpus borné, sans globale ni Runner écrivant les scores :
   coût, chemins non ciblés non fraîchement testés ; baseline rouge conservée.
4. Inclure le config du replay en seconde tâche : coût, raccords/API de
   diagnostic supplémentaires, sans extension des algorithmes clip/layer.
5. Conserver les limites écartées explicitement par la revue finale tout en
   achevant le corpus : un futur cas peut imposer une extension replay,
   offscreen, ABI ou validation baseline ; aucune promesse universelle.

### Reconnaissance préalable aux ports hardstop

Reconnaissance read-only, pas encore un gain attribué :
`hardstop_gradients` utilise localement des cellules170×64 sur512, tandis
que la [source Skia épinglée](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/hardstop_gradients.cpp)
calcule166×62 sur500 dans une image512×512 ; marge/décalage visibles dans
les images. `hardstop_gradients_many` place localement bleu et blanc à0,
rendant la première rampe blanche, et utilise bottom18 au lieu de19 ; la
[source épinglée](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/hardstop_gradients_many.cpp)
décrit une rampe bleu0→blanc1 et MakeXYWH(0,1,1000,18). Les deux ports ont
AAtrue par défaut contre le paint Skia non-AA. Scores actuels16,113281% et
10,65515%. Prochain petit lot : attentes indépendantes, RED des vrais GM,
port fidèle, review puis mesure séparée, sans retoucher les références.

## Lot Rect hairline entier et encodé — 30 septembre 2026

Le [design](encoded-hairline-design.md) et le [plan](encoded-hairline-plan.md)
conservent le vrai Rect/STROKE de largeur zéro, non-AA, solide SrcOver,
BUTT/MITER. `math` calcule les bandes disjointes de la bordure intérieure
`[left,top,right+1,bottom+1)` : coins une seule fois, arithmétique I64 avant
clipping, pas de bord inventé au clip. Une seule occurrence W4d conserve
source et commande d'origine. La géométrie est commune aux deux domaines ;
les scales axis-aligned LINEAR déjà admis gardent une largeur device d'un
pixel. Encodé reste limité à identité/translation entière.

Le domaine authentifié traverse source différée, formats de ressources,
W5b/W6, stencil producer/cover et readback. La sortie d'octets conserve son
interprétation EncodedPremulSrgb, distincte du domaine de composition de la
cible. Le layout clear-only hérité de #2422 est corrigé au producteur.
Source authority, seals, snapshot physique et caps ne sont pas relâchés ;
pas de fan-out en quatre commandes, ni de nouvelle source composée fictive.
Les deux entrées publiques W4d réutilisent l'admission encodée fermée ; les
factories internes W4e/W6 conservent leurs contrats de scènes localisées.
Les routes AA restent LINEAR. Aucun support implicite Path, stroke fini,
shader de stroke, filtre, blend ou transform hors tranche.

### Validation et corrections de review

Task1 `ca058e49a` : RED causal au pixel(1,1), puis math478/478 et native48/48,
dont les43 contrôles initiaux, sorties0. Sol a demandé le cas height-one,
ajouté dans `aa29de2ca`, test math ciblé2/2 et contre-relecture approuvée.
Task2 `52f09880e` : RED d'admission encodée puis68/68 dans onze classes.
Sa correction de review `ebfd29bb1` ferme AA encodé et propage le format au
clear-only différé ;56/56, quatre classes, toutes les43 identités initiales.

Astra a ensuite relevé deux Important : le successeur W5b **non vide** avec
NoOp élidé reconstruit encore LINEAR, et les mélanges hairline/sibling dans
le même plain layer ne sont pas testés. Un Minor omet une colonne de quatre
rangées du témoin scale. La vague unique `f80d94fb4` transmet le format de
source aux deux successeurs W5b, ferme les entrées publiques à la tranche
existante, étend les mélanges et vérifie toutes les dimensions de la grille.
La contre-relecture Sol confirme les deux Important corrigés, sans nouvelle
casse Critical/Important. Reliquat Minor : le contrôle de largeur est placé
dans la boucle des caractères ; une future ligne vide le sauterait. Les grilles
actuelles sont toutes complètes, y compris le pixel x=7 du cas scale. Ce
durcissement du helper est différé après l'unique vague finale, sans prétendre
que M1 est intégralement clos ni que la branche est prête à merger.

**Validation finale69/69**, onze classes, zéro fail/skip/doublon/runner,
processus/wrapper0. Les43 contrôles initiaux et les50 du lot parent sont tous
présents avec résultat inchangé. Témoins : domaines/layouts RGBA/BGRA,
mélanges des cinq sources dans les deux ordres, racine et même layer borné,
restore255/128 une seule fois, répétition, translation/clip actif, Picture
mémoire/archive avec mutation réelle du Rect, snapshots SOURCE_SPACE full/
subset, refus/sentinel/discard/récupération. Paint/ColorARGB étant immuables,
aucune réaffectation de variable n'est présentée comme mutation de capture.
Budget4×4 dérivé avant GPU :
`B=64+64+1024+16+32+16384+4096+4096+64=25840`, succès à B,
refus transactionnel à B−1 et récupération répétée, aucun cap relevé.
Le contrat d'intégration CompositionEnvelope reste inchangé et moins précis
que le contrat primitif ; pas de parité à un code déduite de ces témoins.

Les essais incorrects restent archivés. En particulier, le premier témoin
final de mélanges échoue à cause de sa fixture (ordre d'oracle, coordonnée de
gradient et image étirée hors1:1) : ce n'est pas un RED causal du moteur.
L'hypothèse intermédiaire de mauvaise localisation layer est retirée.
Le défaut W5b est établi statiquement sur le compiler public, pas par un
échec Surface dont la route W6 le contourne. Aucun test d'infrastructure
n'est ajouté. Les audits d'identités LINEAR et de snapshot physique sont
statiques et ne sont pas présentés comme des comparaisons runtime de seals.

Globale unique240s, antérieure à la correction finale : **678 PASS,
40 échecs hérités,1 interrompu**,719 identités communes avec le parent,
25 anciennes non atteintes ; wrapper124/enfant143. Aucun nouvel échec observé
dans cette intersection. `nearestAndLinearUsePixelCenters` est interrompu
(auparavant PASS). La suite reste **rouge/incomplète**, sans attribution de
performance ni garantie sur les tests non atteints ; elle n'est pas relancée
pour obtenir un statut vert. Warnings native-access Java/LWJGL Unsafe/Gradle
hérités conservés.

Le [snapshot631 final](encoded-hairline-f80d94fb4.json) mesure exactement
`f80d94fb4b5378f10bd2dc4e58d2819897d0adc6`. Les trois tranches sérialisées
`[0,607)`, `[607,608)`, `[608,631)` terminent Gradle/wrapper0/1/0 ; le1
correspond au timeout interne30s de `vertices`, journalisé avant sortie124
du processus de mesure. Aucun wrapper n'a atteint sa limite extérieure.
**198/443 rendus,176 comparaisons,198 anciennes empreintes RGBA identiques**,
aucune perte ni nouveau rendu. Les631 identités,443 éligibles, scopes,
références, dimensions, seuils, résultats et diagnostics restent inchangés.
Toujours36 cas à≥99%,49 à≥95%, médiane71,734909%,194 échecs de rendu,
50 de setup,14 rendus non comparés,8 désaccords de dimensions et1 timeout.
Aucun changement de pixels à attribuer ou inspecter dans cette comparaison.
`alphagradients` reste à33,882161% des pixels ±2/canal : son port n'active
pas encore le contrat corrigé. **Aucun gain GM**, aucune parité nouvelle
revendiquée pour cette capacité moteur ; le changement de scène sera mesuré
dans un lot distinct.

Archives : `/private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/`, notamment
`baseline`, `green-math`, `green-final-43-commit`, `fix-round1-math`,
`task2-final-04`, `task2-review-green-02`, `global-240`,
`global-comparison.json`, `final-fix-final-05`, les archives `corpus-*` et
les journaux/PNG sous `parity/`.
Fonts, codecs/décodage externe, `jpg-color-cube`, ports GM, références,
seuils, exclusions et scores historiques sont inchangés. Port fidèle
d'`alphagradients` séparé, puis mesure distincte ; W7 reste ouvert.

### Arbitrages de ce lot, dans l'ordre

1. Exécuter le plan sous la carte blanche W7, sans nouvelle boucle
   d'approbation : risque de reprise réversible du code/design sur draft.
2. Une occurrence W4d plutôt que quatre commandes W3 : coût d'erreur,
   davantage de raccords planner/native à reprendre, sans contourner les seals.
3. Même géométrie entière LINEAR et encodée : risque de modification de pixels
   LINEAR historiques, à mesurer et attribuer plutôt que masquer.
4. Muter réellement RectF32 et vérifier les valeurs Paint/ColorARGB immuables :
   un futur payload de paint mutable demanderait un témoin supplémentaire.
5. Différer le contrôle des lignes vides du helper après l'unique vague finale :
   les grilles actuelles sont complètes ; risque, une future ligne vide pourrait
   omettre ses pixels sans faire échouer ce contrôle.

## Lot domaine de composition Surface — 30 septembre 2026

Le [design](surface-composition-design.md) et le
[plan](surface-composition-plan.md) ajoutent le domaine explicite
`SRGB_ENCODED` à `RenderConfig`, avec `LINEAR` conservé par défaut.
La tranche admise utilise le pipeline partagé : Rect FILL non-AA et SrcOver,
solides, LinearGradient sRGB/CLAMP dans les deux modes alpha, zéro ou un
plain layer, images SOURCE_SPACE/PREMUL/SRGB nearest1:1 et snapshots.
Le domaine suit cible, construction différée, sources/proofs authentifiés,
programmes, occurrences de layer, texture native et métadonnées de sortie.
Il ne devient pas une propriété intrinsèque de Picture/SceneSnapshot.

`GPUColorFormat.AUTO` résout la cible RGBA8UnormSrgb pour LINEAR et
RGBA8Unorm pour SRGB_ENCODED. `PixelFormat` décrit uniquement l'ordre RGBA/BGRA
des octets publics. Les formats natifs contradictoires, BGRA8 natif et F16
refusent explicitement : c'est une correction intentionnelle du contrat
public, qui peut demander une migration de configuration.
Les snapshots encodés sont SOURCE_SPACE, les historiques restent
TRANSFER_ENCODED_LINEAR_PREMUL. Encodé→LINEAR est admis ; l'inverse refuse
dans cette tranche. Ni version Picture ni format externe ne change.

### Validation ciblée et réserve numérique

Task1 est approuvée après trois corrections/re-reviews Sol, commit `bcc3e9deb` :
**45/45 tests publics**, neuf classes XML, processus et wrapper0. La review
initiale a notamment fait corriger les ensembles numériques amputés par un
midpoint, les identités LINEAR, l'admission des layers, la précédence des
diagnostics et les attentes calculées après GPU. Les corrections suivantes
conservent tous les champs des identités encodées, un vrai discriminateur R/B,
et chaque blend/store natif, y compris le premier SrcOver sur transparent.

Task2 `f21162055` ajoute les gradients encodés. RED comportemental : refus
`unsupported.surface.composition.source` avant support. L'intégration révèle
ensuite la perte du domaine lors du re-seal après opacité, corrigée dans le
proof. Un second échec venait du test de récupération dessinant1×1 dans une
cible2×2, pas du moteur. La sélection finale `task2-final-selected-02` donne
**48/48**, neuf classes XML, zéro fail/skip/doublon, processus et wrapper0 ;
les45 identités précédentes sont toutes conservées. Couvre root/layer,
Picture mémoire/archive, mutation des stops après capture, opacité,
alternance des domaines, répétition, hard stops, un-stop, axe dégénéré,
alpha0/1 et refus/sentinel/discard/récupération. Sol approuve conformité et
qualité Task2 ; seul un libellé de refus devenu incomplet reste Minor.

**Amendement de précision approuvé par l'utilisateur :** les nouveaux tests
de composition utilisent un type distinct `CompositionEnvelope`, ensembles
complets par canal et traces des stores calculés avant le GPU. Astra a montré
que les bornes primitives existantes peuvent produire verts94/96 après les
stores du témoin obligatoire : l'ancien critère de deux codes adjacents
ne peut pas couvrir cet ensemble. Ni bornes primitives, ni preuves produit,
ni gates historiques, ni seuils GM ne sont élargis. L'acceptation de ces
tests d'intégration est néanmoins **moins précise**, pas une preuve de parité
Skia à un code. Les témoins séparent mauvais domaine, opacité omise/doublée
et inversion R/B. L'omission d'un store peut rester indétectable quand ses
ensembles se recouvrent ; répétition et traces ne lèvent pas cette réserve.

Budget final dérivé statiquement avant essai sur3×3 :
`B=36+36+768+16+16+16=888`, contre887 pour le refus transactionnel.
Les trois16 représentent geometry UniformData, source root et source child.
Les premières dérivations576→592 puis3×2/608 étaient incorrectes ou mal
attribuées ; elles restent des erreurs de protocole documentées, non des
preuves a priori. Aucun plafond produit n'a augmenté.

Les deux témoins W5e isolés avaient des assertions PASS puis native133
(GLFW/AppKit fermé depuis le shutdown thread). La sélection combinée dispose
explicitement du runtime et finit0 ; elle ne prouve pas la correction du
lifecycle global. Warnings JVM native-access/Unsafe et Gradle conservés.

### Validation globale, corpus et publication

La globale unique `global-240` sur `f21162055` atteint744 identités :
**703 PASS,40 FAIL,1 interrompu**, sans doublon, wrapper124/enfant143 après
TERM à240s, XML non finalisés. Les724 identités communes avec la globale
précédente ne montrent aucun nouvel échec ; les40 échecs sont hérités.
Les13 échecs Picture corrigés dans le lot parent passent maintenant, ainsi
que `cubicDrawImageMatchesMitchellNetravaliOracle`, interrompu auparavant.
L'ancien nom `unsupported SRC scene retains its known legacy pixels` a été
remplacé par le témoin élargi AUTO/formats compatibles, qui passe : ce n'est
pas un test perdu. Les20 identités non appariées comptent19 succès et le cas
`imageNineInvalidCentersRefuseAndRecoverOnSameRuntime` interrompu à l'arrêt.
Les tests au-delà ne sont pas couverts par cette globale ; les validations
ciblées W7 restent séparées. La suite demeure **rouge/incomplète**.

Le [snapshot631 final](surface-composition-1d629b0be.json) mesure exactement
`1d629b0be8e51ee2fc9de3623ede705e3a4523fa`, après la correction finale ci-dessous.
Les trois tranches sérialisées
`[0,607)`, `[607,608)`, `[608,631)` terminent Gradle0/1/0 ; le1 représente
le processus de mesure124 après timeout30s de `vertices`, conservé au
dénominateur. Les631 identités,443 éligibles, références PNG, scopes, seuils,
dimensions, résultats et diagnostics sont identiques au lot parent.
**198/443 rendus,176 comparaisons,198 anciennes empreintes RGBA identiques,
zéro rendu perdu ou ajouté.** Toujours36 cas à≥99%,49 à≥95%, médiane71,734909%,
194 échecs de rendu,50 de setup,14 non comparés et8 désaccords de dimensions.
Le corpus par défaut n'active pas SRGB_ENCODED : aucun gain GM pour ce lot.
Le checkpoint préalable `f21162055` donnait déjà les mêmes198 empreintes ;
son snapshot intermédiaire redondant est retiré du checkout, récupérable
dans le commit documentaire `4238e9778`. Les deux jeux de journaux sont conservés.

La revue finale Astra de `b1ba6d0f3..4238e9778` trouve quatre Important :
mixtures root `drawColor` + solide/gradient/image sans owner, Surface encodée
vide sans owner, PlanId W6 identique entre domaines, et nouvelles capabilities
modifiant indirectement les identités LINEAR historiques. Deux Minor portent
sur le libellé de refus source et la précédence géométrique d'ImagePatch.
Ce sont des constats statiques ; aucun mauvais pixel/cache GPU n'est inventé.
Le commit `1d629b0be` corrige ces six constats en une vague : ownership W6
root-only des mixtures encodées, clear/readback vide authentifié, discriminant
W6 encodé, projection des seules identités capability par cible, diagnostics.
Les LegacyColor restent figées, sans référence de matériau W5 artificielle ;
les vérifications physiques gardent le snapshot capability complet.
La sélection finale `final-fix-selected-10` passe **50/50**, neuf classes XML,
processus/wrapper0 ; les48 anciennes identités restent présentes, plus deux
témoins de mixtures et Surface vide. La globale reste celle avant correction
finale, elle n'est pas relancée ni requalifiée comme verte. Le corpus final
est vérifié ci-dessus. La re-review ciblée Sol de `4238e9778..1d629b0be`
confirme les six constats corrigés, sans nouvelle casse Critical/Important.

Une réserve Minor reste ouverte : le chemin clear-only encodé authentifie
correctement la cible native RGBA8Unorm mais renseigne encore
RGBA8UnormSrgb dans `GPUPreparedNativeReadbackLayout.format`
(`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:7093`). Le consumer
`GPUFrameReadbackCompletion.kt:710–718` accepte les deux formats et la copie
native ne dépend pas de ce champ ; les pixels transparents et le tag
SOURCE_SPACE sont vérifiés. C'est une métadonnée physique inexacte, pas un
défaut de pixels établi. Point différé après l'unique vague finale, à corriger
avant qu'un nouveau consumer utilise ce champ pour interpréter les octets.
Risque conservé : une future interprétation stricte pourrait être erronée.

Réserve d'exécution : quatre essais intermédiaires ont utilisé par erreur le
checkout `cbf6` (aucun test W7 disponible). Les deux fichiers produit modifiés
accidentellement par l'agent y ont été restaurés par patch inverse ; le
contrôleur a vérifié le retour aux seuls deux fichiers utilisateurs déjà
modifiés. Ces essais exit1 ne sont ni des GREEN ni une preuve de panne GPU.
Seule la sélection finale50 sur le bon worktree est retenue.
PR draft [#2422](https://github.com/ygdrasil-io/kanvas/pull/2422) empilée sur
#2421 avec cette réserve ; aucune readiness pour merge.
Archives, commandes et comparaison nominative :
`/private/tmp/kanvas-w7-composition.25GaUn/`.
Fonts, codecs, `jpg-color-cube`, GM/adaptateurs, références, seuils,
exclusions et scores historiques restent inchangés.

Le hairline encodé d'`alphagradients`, puis son port fidèle, restent des lots
distincts. AA, filtres, conversions générales et topologies de layers plus
riches ne sont pas rendus possibles par ce contrat. W7 reste ouvert.

### Arbitrages de ce lot, dans l'ordre

1. Design et plan pilotés sans nouvelle boucle d'approbation, avec Astra :
   coût d'erreur, reprise réversible du code/design sur draft.
2. Tranche verticale en deux tâches, pas migration W3–W6 complète : coût,
   extensions ultérieures et gain GM retardé.
3. AUTO et séparation format natif/layout public : coût, migration des callers
   aux configurations auparavant contradictoires.
4. SOURCE_SPACE pour les snapshots encodés, refus du replay inverse : coût,
   extension de conversion et preuve à réaliser ultérieurement.
5. Task1 subdivisée en milestones, sans réduire son acceptance : coût,
   handoffs et validations supplémentaires.
6. Résolution fermée du tuple cible/format/interprétation/sortie, retrait du
   bypass temporaire W3 avant GREEN : coût, reprise locale des jonctions.
7. Nouveau contexte Terra après le premier checkpoint : coût, risque de
   perte de contexte contrôlé par brief et baseline fraîche.
8. Native133 isolé conservé comme dette, validation combinée : coût,
   un défaut de lifecycle dépendant de l'ordre pourrait rester masqué.
9. Nouveau contexte Terra pour snapshots après des retours sans exécution :
   coût, handoff et omissions possibles, contrôlés par acceptance complète.
10. Publication frozen-color W6 bornée pour DrawColor, sans élargir W5 : coût,
    risque local couleur/ordre/clip couvert par les témoins natifs.
11. Erreurs de budget conservées et nouvelle dérivation statique3×3 figée
    avant essai : coût, validation supplémentaire sans effacer l'erreur initiale.
12. CompositionEnvelope distinct approuvé : coût, certaines petites régressions
    d'arrondi ou de store peuvent échapper à cette acceptance moins précise ;
    discriminants avant GPU et mesure GM indépendante restent obligatoires.
13. Métadonnée readback clear-only inexacte différée après l'unique vague
    finale : aucun effet actuel sur les pixels établi ; coût si cet arbitrage
    est erroné, mauvaise interprétation par un futur consumer du format.

## Lot politique alpha du gradient — 30 septembre 2026

Le [design](gradient-alpha-design.md) et le [plan](gradient-alpha-plan.md)
livrent `GradientAlphaMode.STRAIGHT/PREMULTIPLIED` pour LinearGradient.
STRAIGHT reste le défaut et conserve ses identités historiques. Le nouveau
mode est admis seulement dans l'espace effectif SRGB avec tile CLAMP ;
les feuilles composées sont contrôlées avant réduction, y compris à un stop.
Le transport paint/IR/Picture/V4 conserve le mode sans doubler les slabs de
stops. La recipe F32 appartient au graphe partagé preuve/WGSL, sans formule
indépendante de l'émetteur. Picture16/schema10 écrit les wire IDs0/1 ;
la façade conserve explicitement les versions13/14/15.

Prérequis natif borné : blending désactivé (`blend=null`) seulement pour
PremulSrc, single-sample et programme direct authentifié, dont la route
garantit coverage None. AA, masque, clip analytique et destination-read ne
reçoivent pas cette optimisation. Le template, son identité et le descripteur
représentent fidèlement l'absence de blending ; les contrôles d'autorité
restent en place. Ni GM/adaptateur, référence, seuil, score historique,
exclusion, plafond de budget, enveloppe numérique ni composition de Surface
n'est changé.

### Preuves publiques et corrections de review

Le RED causal `red-src` contient deux vrais écarts de pixels : R92 contre
187–188 pour blanc opaque→noir transparent, R80 contre108–109 pour
rouge alpha128→bleu alpha64. Les contrôles STRAIGHT passent. Les essais
`red2` (oracle non borné avant GPU) et `red3` (destination incohérente) ne sont
pas des RED valides. La fixture Picture15 provient du writer historique,
capturée avant modification ; ses583 octets et sa base64 ont été comparés
au XML de capture. L'assertion volontaire d'export quitte1, pas un succès.
Le replay réel direct/décodé, les mutations après capture, les alphas zéro
et1/255, stops2/16/17, hard stops, axe dégénéré CLAMP t=1, wrappers et refus
transactionnels sont couverts par la Surface publique.

Le budget mixte est dérivé **avant essai** :
`B=4+256+16384+4096+4096+2×(112+48)+64=25220` octets.
Les deux programmes V4 partagent un slab de64 octets. B passe, B−1 refuse
avec `resource.material.gradient.stop-budget`, sentinel intact ; discard et
deux rendus sains vérifient la récupération. Aucun seuil n'a été recherché
par essais successifs.

La première review Sol trouve quatre Important et un Minor : garde bypassée
en capture composée, identités STRAIGHT modifiées, Picture13 perdu, témoin
mixed-AA non discriminant et liste de blend vide acceptée pour destination.
Le commit `032cd9466` corrige ces cinq points. La re-review Sol de
`1ce59fdcc..032cd9466` approuve conformité et qualité : cinq constats clos,
zéro Critical/Important/Minor, aucune nouvelle casse trouvée. Le mixed-AA compare désormais
un vrai pixel intermédiaire SrcOver blanc opaque→gris204 transparent à t=.5 :
STRAIGHT RGB168–169 contre PREMULTIPLIED187–188, alpha127–128. La première
valeur bornée/disjointe est figée après un tableau CPU, **avant** rendu GPU ;
aucune attente n'est copiée des pixels observés. Le stroke AA sibling et ses
trois contrôles restent présents.

Validation finale ciblée `fix1-final-tests-b` : **26/26**, XML complets,
wrapper/Gradle0, sans terminal native133. Répartition : alpha13, mixed-rootAA7,
fractional Rect1, trois contrôles W5f et deux fixtures Picture13/schema7.
Les méthodes schema7 conservent leurs noms pour l'appariement historique ;
seules leurs attentes obsolètes du writer14/schema8 deviennent16/schema10.
Cela ne constitue pas un gain de rendu GM.

### Réserves conservées

Step0 a été validée après le premier GREEN alpha, contrairement au séquencement
prescrit ; les contrôles ont ensuite été rejoués sur la portée native corrigée.
Aucun RED natif One/Zero n'est revendiqué. Un contrôle exploratoire SRC+AA
refuse au préflight (`Every material packet must have its sealed geometry
host template`) : retiré sans ouvrir l'admission. **Les pixels SRC à couverture
partielle AA ne sont donc pas validés** par ce lot.

Le run voisin W5c compte22/27 assertions PASS, puis executor133/Gradle1.
Les cinq identités déjà en échec sur le parent sont
`conicalGradientMasksFragmentsWithoutValidRoot`,
`conicalGradientCoversFourLanesAndSelectsLargestValidRoot`,
`mixedGradientFramePreservesOrderRangesOpacityAndBlend`,
`radialGradientCoversFourGeometryLanes` et
`conicalGradientCoversAllDegeneracyBranches`.
Les anciens runs W5f (sélections1/2/3) ont leurs assertions PASS mais terminent
aussi native133 : ils ne sont pas verts et leur cause native commune n'est
pas établie. Le dernier run combiné exit0 n'efface pas ces réserves.
Warnings JVM native-access/Unsafe et dépréciations Gradle restent présents.
Archives : `/private/tmp/kanvas-w7-alpha-mode.vpi7cG/`.

### Arbitrages

1. Design décidé dans la délégation de pilotage utilisateur, avec review :
   coût en cas d'erreur, reprise réversible du design/code sur draft.
2. Livraison end-to-end atomique, pas un flag public sans comportement :
   coût, review plus large et éventuel découpage si couplage réel.
3. Recipe F32 propre, partition exacte des alphas et calcul depuis leur minimum :
   coût, reprise de preuve/intégration ; pas d'identité bit-à-bit Skia promise.
4. Témoins SRC source seule et prérequis natif borné : coût, risque de régression
   SRC/template à contrôler ; témoin numérique sur destination blanche différé.
   Aucune enveloppe ni authentification n'est assouplie.

### Globale bornée et régressions Picture corrigées

La seule globale `global-240` sur `032cd9466` atteint725 identités :
**671 PASS,53 FAIL,1 interrompu**, sans doublon ; wrapper124/enfant143 après
TERM à240s, XML non finalisés. Elle ne prouve pas une suite verte ni complète.
Les708 identités du parent sont toutes appariées :13 anciens succès deviennent
des échecs Picture, trois anciens échecs passent (deux fixtures schema7 et le
refus de version inconnue), et `generalCoordinateUniformBudgetRefusesPreciselyAndRecovers`
passe après l'interruption parente. Les17 cas supplémentaires comptent15 succès,
un échec et un interrompu (`cubicDrawImageMatchesMitchellNetravaliOracle`).
Leur échec `excludedLinearPaintLanesPreservePreparedRefusals` était déjà présent
dans le run AA-mask antérieur, non atteint dans le relevé parent immédiat.

Les13 régressions Picture mêlent attentes de header devenues obsolètes et vrais
retours `null` au décodage public. La cause est `ArchiveReader.clip()` :
le writer schema10 émet toujours `clipTransformV2`, mais le reader ne reconnaît
que schemas2..9. Le commit `fadbd80e3` ajoute10 à cette branche et met à jour
les seules attentes du writer courant16/10 dans les13 cas. Aucun format de
clip, fixture historique, nom de méthode ou assertion sémantique n'est modifié.

Le témoin public isolé `fix2-red-picture-enums` confirme le défaut avant patch
(un échec requireNotNull, Gradle1). Le run `fix2-final-controls-regressions`
valide ensuite **39/39 tests sur12 classes, XML complets, Gradle0 en26s**, sans
native133 : les13 régressions et les26 contrôles précédents. Le contrôleur
a vérifié exit/log/XML indépendamment du rapport. La globale ci-dessus reste
le résultat **avant ce dernier correctif** ; aucune seconde globale ni garantie
sur le reste de la suite n'en est déduite. Ces39 tests ne sont pas un gain GM.
La re-review Sol de `032cd9466..fadbd80e3` approuve conformité et qualité,
les deux constats sont clos sans nouveau Critical/Important/Minor.

### Corpus constant après correction

Le [snapshot631](gradient-alpha-09d9574b5.json) étiquette exactement
`09d9574b5c10be782109efecc73efd53d4174759`, après le correctif legacy décrit
ci-dessous. Les631 identités,443 éligibles,
empreintes de références PNG, scopes, seuils, dimensions et paramètres
restent identiques à [la base](mixed-root-ff628a94d.json). Les trois tranches
`[0,607)`, `[607,608)`, `[608,631)` sont sérialisées, exits Gradle0/1/0 ;
le1 est le processus de mesure124 après timeout30s de `vertices`, conservé
au dénominateur. La première tranche termine en1m57, sans valeur de benchmark.

| Indicateur | Parent #2420 | Alpha explicite |
| --- | ---: | ---: |
| Rendus / comparaisons | 198 / 176 | 198 / 176 |
| Cas à≥99% / ≥95% des pixels ±2 | 36 / 49 | 36 / 49 |
| Échecs rendu / setup | 194 / 50 | 194 / 50 |
| Non comparés / dimensions incompatibles | 14 / 8 | 14 / 8 |
| Timeouts | 1 | 1 |
| Médiane des176 comparaisons | 71,7349% | 71,7349% |

**Les198 anciennes empreintes RGBA sont identiques, aucun rendu perdu ou
nouveau, aucun changement d'issue ni de diagnostic.** Aucun GM n'active
encore le nouveau mode ; cette livraison apporte un contrat public testé,
pas un gain de parité mesuré. Les journaux sont dans
`/private/tmp/kanvas-w7-alpha-mode.vpi7cG/corpus-final`. Le checkpoint préalable
`fadbd80e3` donnait déjà les mêmes198 empreintes dans `corpus` ; son snapshot
intermédiaire redondant est retiré du checkout, récupérable dans le commit
documentaire `08a3fd8dd`. L'agrégateur contrôle
complétude/unicité et métadonnées ; la comparaison appariée vérifie aussi
les références, scopes, scores et empreintes, pas seulement les compteurs.

La revue finale architecturale est confiée une fois à Astra, les reviews de
tâche restant Sol. Sur `c80e5b56d..08a3fd8dd`, elle confirme les preuves
39/39 et corpus invariant, mais trouve un Important : les voies legacy du
mapper et des normalisations V1/V2 ignorent encore `alphaMode`. Un Rect stroke
non-AA à CTM identité peut atteindre le descriptor historique et rendre
STRAIGHT à la place de PREMULTIPLIED. Aucune reproduction native n'est attribuée
à cette review statique. Le contrôleur déclenche une seule correction finale.

Le RED public `legacyStrokeRouteRefusesPremultipliedBeforePublicationAndRecovers`
reproduit ensuite le défaut : `readPixels` retourne true au lieu du refus,
un échec XML/Gradle1. Le commit `09d9574b5` ferme les frontières legacy du
mapper, des normalisations V1/V2 (wrappers inclus) et de l'admission stroke,
avec `unsupported.material.gradient.alpha-mode`, avant réduction un-stop.
Aucune route legacy n'est promue en V4, STRAIGHT garde son contrat.

La sélection finale `finalfix-targeted46-w6e` passe **46/46,13 classes XML,
Gradle0 en18s**, zéro échec/skip/doublon. Les39 identités antérieures sont toutes
présentes, avec le nouveau refus/sentinel/récupération et six contrôles
`W7StrokeRoutingSurfacePixelTest`. Le contrôleur vérifie aussi les identités,
pas seulement le compte : la première sélection45 avait omis W6e. Son run
isolé de complément passe une assertion puis termine native133/Gradle1 ;
il reste **non vert**, même après le succès du run combiné46. Le shutdown
isolé de W6e n'est pas corrigé par ce lot.

Le corpus final sur `09d9574b5` est invariant comme détaillé ci-dessus.
La re-review finale Sol de `08a3fd8dd..09d9574b5` clôt le finding :
conformité et qualité PASS, zéro Critical/Important/Minor résiduel. Le cycle
reste une seule correction finale suivie d'une seule re-review ciblée.
SRC partiel AA, dettes globales/native133, composition,
port GM et backends GPU non exécutés restent des limites explicites.
La suite prévue est le contrat de composition de Surface, puis le port GM
dans un lot distinct. W7 reste ouvert ; aucune autorisation de merge.

## Audit alpha et domaine de composition — 29 septembre 2026

L'[audit causal de `alphagradients`](alphagradients-audit.md) distingue deux
contrats manquants : choix premul/unpremul du gradient et domaine de
composition de Surface. Sur les 184 704 pixels intérieurs sondés, la référence
suit straight gauche / premul droite et SrcOver sRGB encodé ; Kanvas suit
straight des deux côtés et SrcOver linéaire. Chaque modèle retrouve les RGB
de son image à un octet près. Les différences d'AA du port restent distinctes.
La diagonale est conforme, contrairement à une première hypothèse retirée.

Le profil Rec.2020 de la référence est reconnu par le comparateur ; cette
expérience ne modifie pas le codec. **15/15 contrôles natifs frais, Gradle 0**,
mais ils valident le contrat actuel et non sa parité Skia. Aucun renderer,
GM, référence, seuil ou score modifié : 198 rendus / 176 comparaisons demeurent
le dernier bilan, pas un résultat amélioré par l'audit. Astra reproduit la
sonde et recommande cet ordre borné : politique alpha du LinearGradient sRGB
clamp d'abord, contrat de composition ensuite, correction du port séparée.
Les critères d'arrêt figurent dans l'audit ; aucune extension implicite aux
images/filtres ou autres familles de gradients. Ni retouche locale du gradient
ni conversion terminale seule ne suffiront à résoudre tous les écarts.

## Lot mélange racine Rect stroke AA — 29 septembre 2026

Le [design](mixed-root-aa-rect-design.md), issu d'un diagnostic Terra et d'un
avis stratégique Astra, puis le [plan](mixed-root-aa-rect-plan.md) bornent le
nouveau domaine aux Rect solid STROKE AA / SrcOver / MITER et aux siblings
Rect FILL solides ou LinearGradient. Pas de layer artificielle : W6 sélectionne
la frame complète, W4d garde RECT/STROKE et l'outline math, produit une source
MSAA4/resolve1 puis compose immédiatement dans l'ordre. Les anciennes voies
standalone/layers/filtres gardent leurs contrats. Les images/Picture, autres
blends, clips complexes et transforms non axis-aligned restent hors extension.

Le [snapshot](mixed-root-ff628a94d.json), renderer exact
`ff628a94da2a9c38aa05c004dff354f61ceaafbf`, conserve les631 identités,443
éligibles, références, scènes, dimensions, seuils, scopes et exclusions.

| Mesure à corpus inchangé | Parent #2419 | Ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 197 | 198 |
| Comparaisons possibles | 175 | 176 |
| Échecs de rendu | 195 | 194 |
| Échecs de setup | 50 | 50 |
| Rendus sans référence exploitable | 14 | 14 |
| Dimensions incompatibles | 8 | 8 |
| Timeouts à30s | 1 | 1 |
| Cas à≥99% des pixels ±2/canal | 36 | 36 |
| Cas à≥95% des pixels ±2/canal | 49 | 49 |

**Un seul nouveau rendu : `alphagradients`,33,8822% de pixels ±2/canal**,553ms
dans le corpus. SSIM luminance0,658254, erreur absolue normalisée0,0840202.
Les197 anciennes empreintes RGBA sont identiques, sans perte, ni autre
changement d'outcome/diagnostic. Les13 autres premiers refus Rect stroke AA
restent des refus, pas des gains promis. La médiane appariée des175 anciens
cas reste71,8965%; celle des176 devient71,7349% par changement de population.
Les trois sessions [0,607),[607,608),[608,631) terminent Gradle0/1/0 :
`vertices` reste timeout au rendu, child124. `ninepatch-stretch` rend en26,758s
avec faible marge sous30s. Somme des durées155,241s, hors démarrage Gradle/JVM,
observation et non benchmark.

Le rejeu PNG de `alphagradients` conserve exactement l'empreinte du corpus.
Inspection du rendu et de la référence : colonnes produites identiques contre
colonnes différenciées dans la référence, contours gris contre noirs et écarts
de couleur visibles. `AlphaGradientsGm.draw` appelle la même construction
`drawGrad` pour les deux colonnes, seul leur placement change. Cela identifie
une divergence du port; cela n'attribue pas causalement chaque écart de pixel
au port ni au renderer. Le seuil historique0 donne `declaredContractPass=true`
malgré33,88% : il ne devient pas une preuve de parité. Prochaine priorité de
fidélité : auditer le port et son contrat d'interpolation/alpha par rapport à
Skia, puis isoler les écarts renderer avec des témoins publics. Aucun GM,
adaptateur, PNG de référence, seuil ni score historique n'est changé ici.

### Validation et limites du lot

La revue de tâche Sol a demandé des témoins séparés : transparent visible
(fond inchangé) et opaque hors écran (refus terminal W4d lors de la sélection
du mélange W6, sentinel intact, récupération). Correction tests-only
`ff628a94d`, re-review validée. **Final proche47/47 sur6classes, XML complets,
Gradle0**, natif Render/Readback et deux rendus identiques. Un run isolé
antérieur W6 budget avait10 assertions PASS puis executor133/Gradle1; ce fait
reste distinct du succès combiné final.

Budget physique final B=29408,B−1 refusé sans publication puis récupération.
Le premier pré-calcul53952 était erroné (deux tripletsV/I/U au lieu d'un,
uniforme gradientV1 mal compté). La dérivation statique corrigée, revue par
Sol, précède le succès B/B−1 final mais pas le premier essai. Le RED négatif
ne relevait pas les préfixes; les préfixes finaux ne prouvent pas leur stabilité
historique. Ces deux écarts au plan sont assumés, pas effacés rétroactivement.

Une seule globale bornée240s :708 END persistés,665 PASS,42 FAIL déjà présents,
1 SKIPPED à l'interruption de
`W5dGradientAddressingSurfacePixelTest.generalCoordinateUniformBudgetRefusesPreciselyAndRecovers`.
Les708 identités sont communes au parent, sans nouvelle assertion en échec
observée;16 autres cas du relevé parent ne sont pas atteints. Le wrapper
retourne124 et l'enfant Gradle143 après TERM de son groupe, sans daemon partagé.
Les XML globaux n'ont pas été finalisés : preuve issue des events et du log,
pas708 XML. Warnings JVM `System::load`/`Unsafe` et dépréciations Gradle restent
signalés; conversions redondantes des nouveaux tests retirées en re-review.
La globale est donc rouge/incomplète. W7 et les gates W6 restent ouverts,
aucune merge readiness. Revue globale Sol `1cd04aa77..cd6f923fd` :
Critical0/Important0/Minor0, publication draft recevable. Non jugés par cette
revue : fidélité Skia globale, extension aux images/Picture, périmètres
exclus et stabilité des tests globaux non atteints. Ces sujets restent ouverts;
aucune preuve correspondante n'est revendiquée.

## Lot couverture AA filtrée — 29 septembre 2026

Le [design](aa-mask-design.md) relu par Astra et le [plan](aa-mask-plan.md)
livrent une couverture blanche distincte de la source AA couleur. Les chemins
direct et stencil producer/cover produisent leur resolve dans une seule passe
native MSAA4. Le blur NORMAL précède l'application unique du matériau W5,
halo compris. Les contributions hors viewport restent disponibles jusqu'au
clip terminal. Le contrat demeure root/Path fill solide/SrcOver/NORMAL.

Le [snapshot](aa-mask-82893045c.json) porte le renderer exact
`82893045c29c0f75cf3b7a95d882b0c3c0e0562a`. Trois sessions couvrent les
631 mêmes identités, 443 éligibles, sans changement de référence, seuil,
scope ou exclusion ; fonts, codecs et `jpg-color-cube` restent hors périmètre.

| Mesure à corpus inchangé | Parent #2418 | Après ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 193 | 197 |
| Comparaisons possibles | 171 | 175 |
| Échecs de rendu | 199 | 195 |
| Échecs de setup | 50 | 50 |
| Rendus sans référence exploitable | 14 | 14 |
| Dimensions incompatibles | 8 | 8 |
| Timeouts à 30 s | 1 | 1 |
| Cas à ≥99 % des pixels ±2/canal | 36 | 36 |
| Cas à ≥95 % des pixels ±2/canal | 48 | 49 |

| Nouveau rendu | Pixels ±2/canal | Durée du cas |
| --- | ---: | ---: |
| `blur2rects` | 96,1751 % | 499 ms |
| `blur2rectsnonninepatch` | 94,8811 % | 651 ms |
| `blur_matrix_rect` | 91,6193 % | 5 845 ms |
| `blurcircles` | 86,2413 % | 3 281 ms |

Aucun ancien rendu perdu : les **193 anciennes empreintes RGBA sont identiques**.
Les quatre gains sont les seuls changements d'outcome/diagnostic. La médiane
des 171 mêmes comparaisons reste **68,9011 %** ; la médiane des 175 cas passe
à 71,8965 % par élargissement de population, pas par amélioration des anciens
pixels. La somme des durées des cas est 161,847 s, timeout inclus, hors
démarrage Gradle/JVM ; ce n'est pas un benchmark. `vertices` reste un timeout
de rendu (child124/Gradle1), les deux autres sessions terminent Gradle0.

Les preuves publiques couvrent AA causal à sigma0,1, alpha appliqué une fois,
translation, trou/halos, source hors écran/clip, deux occurrences et récupération.
Les budgets indépendants direct299104 et stencil723296 incluent MSAA4,
resolve1, depth-stencil4, cibles W6b/W5, V/I/U et readback aligné : B passe,
B−1 refuse sans publication puis la Surface récupère sur deux rendus identiques.
Les revues de tâche Sol sont validées après renforcement des tests.

Validation finale proche : **75/75 exit0**, puis **8/8 exit0** après correctif
tests-only de répétition. La seule globale bornée donne 724 identités communes,
680 succès, les 43 mêmes échecs et un test interrompu. Le wrapper Ruby240s
envoie TERM à son enfant Gradle et retourne124 ; l'exit Gradle indépendant
n'est pas observé. `formatsAlphaAndColorSpaceMatchOracle` (passait sur parent)
est marqué skipped à l'interruption ; `cubicDrawImageMatchesMitchellNetravaliOracle`
n'est pas atteint. Aucune nouvelle assertion en échec observée n'équivaut pas
à une suite verte. Warnings JVM/Gradle et le fallback Kotlin daemon historique
de Task1 restent signalés ; les runs finaux n'ont pas ce fallback.

AA filtré en layer/Picture, autres styles/blends/strokes/clips complexes,
réduction des écarts de pixels et fermeture des gates W6 restent des suites
possibles, pas des propriétés livrées ici. La draft et sa revue globale ne
doivent pas être présentées comme une clôture W7 ou une autorisation de merge.
Revue globale Sol de `a21bb6472..70f2ff164` : **Critical 0 / Important 0 / Minor 3**,
publication draft recevable. Restent deux commentaires trop restrictifs
(classe de test « direct », sélection `DirectTriangle`) et les warnings/outillage.
La revue n'a pas jugé les images spatialement, les autres GPUs/OS, toutes les
combinaisons EVEN_ODD/transformations, les causes des échecs historiques ni les
tests globaux non atteints. Aucune preuve correspondante n'est revendiquée.

## Mesure

`measureSkiaParity` utilise la capture existante `Surface`, y compris les
exclusions observées pendant le setup. Il écrit un journal JSONL hors des
références et du fichier historique de scores. Chaque cas est persisté avant
le suivant ; un watchdog termine seulement le processus de mesure à 30 s
par GM. Après un timeout à l'index N, reprendre avec `gm.parityFrom=N+1` dans
le même dossier. Le cas reste enregistré comme timeout.
L'identité, la configuration et l'empreinte de référence sont figées avant
le watchdog ; les 30 s bornent ensuite setup, rendu et comparaison, pas
l'initialisation du registre ni la lecture préalable de cette empreinte.

```sh
rtk proxy ./gradlew :integration-tests:skia:measureSkiaParity --offline \
  -Pgm.rendererCommit=<SHA complet du renderer> \
  -Pgm.parityOutput=<dossier neuf> -Pgm.parityTimeout=30
```

La comparaison publie les pixels exactement identiques, les pixels dont tous
les canaux diffèrent de 2 au plus, la MAE normalisée sans tolérance et le SSIM
de luminance. Les tolérances/seuils propres à chaque GM restent visibles,
mais leur réussite n'est pas un taux global de parité : certains seuils sont
très permissifs. Les fonds uniformes peuvent aussi gonfler une métrique
globale ; les cas prioritaires sont inspectés spatialement.

Le premier journal complet fixe les identités et scopes de la baseline.
Les mesures suivantes doivent comparer ces mêmes lignes et empreintes,
et conserver les changements de périmètre comme tels. Aucune modification
des références, des seuils ou des exclusions ne peut compter comme réparation.

## Baseline du 29 septembre 2026

Renderer : `d661f10c32777c5d43cb549d3b27fa86449b1471` (PR #2410),
macOS / aarch64, JDK 25.0.1, Apple M2 Max. Configuration `Surface` par défaut,
fond blanc opaque comme le runner existant, comparaison RGBA sRGB via
`ComparisonUtils`. Ce lot ne modifie pas le renderer, les GMs, les références,
les seuils, les exclusions ou les anciens scores.

Le [snapshot machine](baseline-d661f10c3.json) conserve les 631 identités,
les quatre sessions, les empreintes et les résultats par GM. Les diagnostics
sont le **premier refus observé**, pas une attribution exhaustive des causes.

| Résultat sur les 443 GMs éligibles | Nombre |
| --- | ---: |
| Rendu obtenu et comparaison possible | 105 |
| Rendu obtenu, référence absente ou déclarée non fiable | 11 |
| Rendu obtenu, dimensions différentes de la référence | 7 |
| Échec de rendu | 260 |
| Échec pendant le setup | 57 |
| Timeout à 30 s | 3 |

Les 188 exclusions restent visibles : 133 fonts, 54 codecs, 1 quarantaine
(`jpg-color-cube`). Les timeouts éligibles sont `lattice2`,
`ninepatch-stretch` et `vertices` ; ils ne deviennent pas des exclusions.

Le rejeu complet retrouve les mêmes empreintes RGBA pour les **123 rendus**
et les mêmes empreintes de références. Les trois timeouts ont leur provenance
complète dans le snapshot final. La somme des durées par cas est de **188,45 s**,
timeouts inclus, hors démarrage Gradle/JVM ; ce n'est pas un benchmark de
débit ni une garantie de déterminisme sur d'autres GPUs.

L'ancien inventaire à 124 rendus n'avait pas cette borne de 30 s :
`ninepatch-stretch` et `vertices`, auparavant rendus, expirent désormais ;
`recordopts` rend maintenant, mais sa comparaison est à 0 %. Ce delta de
disponibilité ne constitue donc pas à lui seul une régression ni un gain visuel.

Sur les 105 comparaisons disponibles, **20** atteignent 99 % des pixels à
±2 par canal et **25** atteignent 95 %. La médiane est **54,64 %**.
Ce ne sont ni un pourcentage de parité globale ni une preuve de fidélité des
ports. `referenceStatus=trusted` signifie uniquement « déclaré tel par la
GM », pas « port audité et conforme à la source Skia ».

Les sept désaccords de dimensions concernent les six variantes
`fast/strict_constraint_*` rendues et `scale-pixels`. Ils restent au bilan,
sans redimensionner artificiellement les références.

## Lot standalone rect/path — 29 septembre 2026

Renderer : `718445e6ef366dbc63ab213b0f4eb301c574af12`.
Le [plan exécuté](stroke-routing-plan.md) et le
[snapshot complet](strokes-718445e6e.json) décrivent ce lot. Les **631 identités**,
les **443 éligibles**, les empreintes de référence, les seuils et la borne de
30 s sont inchangés. Les quatre sessions terminent le registre sans doublon.
Aucun port GM, codec, font, budget ou référence n'a été modifié.

| Mesure à corpus inchangé | Baseline | Après ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 123 | 164 |
| Comparaisons possibles | 105 | 142 |
| Rendus sans référence fiable disponible | 11 | 14 |
| Dimensions incompatibles | 7 | 8 |
| Échecs de rendu | 260 | 226 |
| Échecs de setup | 57 | 50 |
| Timeouts | 3 | 3 |
| Cas à ≥99 % des pixels ±2/canal | 20 | 26 |
| Cas à ≥95 % des pixels ±2/canal | 25 | 36 |

Les **41 nouveaux rendus** comprennent 37 comparaisons, 3 références
absentes/non fiables et `sharedcorners` aux dimensions incompatibles.
Les **123 anciens rendus restent disponibles**, dont **118 empreintes RGBA
identiques**. Aucun ancien cas comparable ne devient non comparable.
La médiane des 142 comparaisons atteint **65,23 %**, mais la médiane des
**105 mêmes cas appariés reste 54,64 %** : la hausse globale provient d'une
population élargie, pas d'une amélioration uniforme des anciens pixels.
Durée cumulée des cas : **185,12 s**, hors démarrage Gradle/JVM, sans valeur
de benchmark. `lattice2`, `ninepatch-stretch` et `vertices` expirent encore
à 30 s ; ils restent dans les 443.

Les six nouveaux cas au-dessus de 99 % sont `clip_strokerect` (100 %),
`clippedcubic` (99,52 %), `crbug_1174186` (99,999 %), `crbug_1177833`
(99,85 %), `ctmpatheffect` (99,52 %) et `tinyanglearcs` (99,90 %).
Ces scores décrivent la comparaison RGBA sRGB du runner, pas une conformité
globale Skia. Deux nouveaux rendus sont à **0 %** : `child_sampling_rt` et
`matrixconvolution_color`. Leur inspection source/PNG confirme des scènes
non fidèles : le premier ne construit aucun runtime effect, dessine un fond
gris et transmet un paint FILL à deux paths linéaires sans aire via
`GmCanvas.drawLine`; le second dessine cinq cercles, alors que son PNG montre
une convolution de texte. Ils restent au dénominateur actuel ; aucun gain
de fidélité n'est revendiqué pour eux.

### Changements des anciens pixels et dette ouverte

| GM | Pixels ±2 avant → après | Observation |
| --- | --- | --- |
| `circle_sizes` | 94,39697 → 94,34204 % | Recul réel : SSIM 0,987834 → 0,974624 ; MAE normalisée 0,006447 → 0,008638. Les contours du nouveau rendu restent visiblement moins lisses que la référence. |
| `concavepaths` | 98,79767 → 98,79833 % | MAE et SSIM légèrement meilleurs ; pas une identité bit à bit. |
| `crbug_640176` | 99,6832 → 99,6832 % | Même taux ±2, mais MAE 0,000391 → 0,000403 et SSIM légèrement moins bon. |
| `p3_ovals` | 88,44861 → 88,44792 % | Léger recul de MAE/SSIM également. |
| `rect` | Non comparable | Empreinte modifiée, référence absente ; aucune conclusion de fidélité. |

Le lot apporte une couverture fonctionnelle plus large, **pas une absence
de régression visuelle**. La sélection du parcours AA et la qualité des
contours, en priorité `circle_sizes`, constituent la prochaine correction
renderer avant tout élargissement supplémentaire du domaine.

Le test public historique de phase négative des pointillés échoue encore
sur `w5b.geometry.incompatible-plan`. L'A/B avec le constructeur historique
reproduit le même échec : le snapshot recopie `PathStrokeDashF64`, dont
l'égalité reste une identité d'objet ; la comparaison de styles du seal W5b
échoue. Ce défaut de validation, distinct de la géométrie des pointillés,
est à corriger séparément avec le témoin public existant, sans desserrer le
seal et sans ajouter de test d'infrastructure.

### Vérification du lot

Le parcours standalone conserve la provenance RECT/PATH et utilise la
géométrie `math` existante. Les réparations natives concernent la cible
logique resolve-only, le mapping 4× des consommateurs de masque, la
continuité MSAA jusqu'au resolve final, les masques hard blancs et la
compatibilité de pipeline avec l'attachement D24 existant. Les compilateurs
de sources W5/W6 ne reçoivent pas l'extension d'admission des rectangles.

Les trois suites publiques W6/W7 complètes (40 + 9 + 6 tests),
`GPUPlanSurfacePixelTest.W4dGeneral*` et
`GPUPlanSurfacePixelTest.W4e public hard*` (14 tests) passent :
**69/69, Gradle exit 0**. Un sélecteur élargi antérieur a donné **68/69**,
avec le seul défaut pointillé décrit ci-dessus ; aucun succès global des
tests n'est revendiqué. Astra a aidé au diagnostic/raccord natif difficile ;
la relecture indépendante Sol n'a trouvé aucun défaut confirmé du diff et
a vérifié séparément la cohérence du bilan complet.

Le rejeu avec PNG des témoins `child_sampling_rt`, `circle_sizes`,
`clip_strokerect`, `clipdrawdraw`, `cliplargerect` et
`matrixconvolution_color` conserve leurs empreintes du relevé complet.
Les références et les scores historiques ne sont pas réécrits.
W7, les gates W6 et la décision de merge restent ouverts.

## Lot pointillés et expérience AA retirée — 29 septembre 2026

Le correctif `adcf16eba` donne à `PathStrokeDashF64` une égalité structurelle
exacte et un `hashCode` cohérent, phase et intervalles inclus. Les copies
défensives restent inchangées ; le seal W5b n'est pas desserré. Le test public
historique de phase négative et une matrice littérale de trois pointillés
passent après avoir échoué avant la correction. Les 476 tests math geometry
passent également.

L'expérience AA `9d3355ec9` appliquait une précision géométrique de 1/16 de
pixel aux seuls remplissages racine AA, sans modifier le MSAA4. Les témoins
d'aire alpha passaient (rayon 1 : 2,008 → 2,996 ; rayon 8 : 195,043 → 199,012,
y compris après scale 2). `circle_sizes` remontait à SSIM **0,987049** contre
0,974624 dans #2412, encore sous les 0,987834 historiques.

Mais le corpus complet inchangé perdait **deux rendus**, `parsedpaths` et
`perspective_clip`, sur `Geometry(value=VertexLimit)` : 164 → 162. Le gate
statique à 255 arêtes winding est le premier suspect, pas un sous-motif
capturé : le diagnostic public agrège plusieurs limites. Après l'avis
ciblé d'Astra, l'expérience et ses trois nouveaux tests ont été retirés en
`5f971f750`. Leur code et leurs résultats restent récupérables dans
`9d3355ec9` et le [plan](aa-dash-repair-plan.md).

**Aucun gain AA n'est livré par ce lot.** Le retour à `.25` conserve donc la
régression de `circle_sizes` constatée dans #2412. Une marge GPU inventée,
une hausse de budget ou un retour silencieux à une approximation grossière
n'ont pas été utilisés pour sauver le compteur. La prochaine reprise doit
capturer le refus interne exact, puis traiter la borne stencil avec des
témoins winding 1 à plus de 255 arêtes et winding réel 256, inversions et
annulations incluses. Des tests pixels ne remplacent pas la preuve de
classification GPU manquante.

Le [snapshot final](dash-5f971f750.json), renderer
`5f971f750f4699adb1a7fb1cbe531e9383367641`, couvre les **631 mêmes identités**
et **443 éligibles** : **164 rendus, 142 comparaisons, 26 à ≥99 % et 36 à
≥95 % de pixels ±2/canal**. Les 164 empreintes RGBA et tous les résultats
terminaux sont identiques à #2412, sans gain ni perte de GM. Les trois
timeouts (`lattice2`, `ninepatch-stretch`, `vertices`) sont conservés à 30 s.
Les références, seuils, exclusions et scènes sont inchangés.

Par rapport à la baseline `d661f10c3`, les 41 gains de #2412 sont donc
préservés, avec les mêmes cinq anciennes images modifiées et la même médiane
appariée de 54,64 %. La médiane des 142 comparaisons reste 65,23 %. Durée
cumulée des cas : 189,97 s, hors démarrage Gradle/JVM, sans valeur de benchmark.
Le gain du lot est la sémantique publique des pointillés, pas une hausse
de la similarité du corpus.

### Validation du lot et limite de la suite complète

Sur le code final, **77/77 tests publics W4/W6/W7** et **476/476 tests math
geometry** passent, zéro skipped, Gradle exit 0. Les 77 incluent les 16
tests W7 stroke/routing, AA layer et dash, les 40 W6 layer W4/W5 et les 21
sélecteurs W4d/W4e hard, dont le témoin historique de phase négative.

```sh
rtk proxy ./gradlew :math:geometry:jvmTest --rerun :kanvas:test --offline --console=plain \
  --tests 'org.graphiks.kanvas.surface.W7StrokeRoutingSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W7AaPathLayerSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W7DashStrokeSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest.W4d*' \
  --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest.W4e public hard*'
```

La sélection élargie de l'expérience donnait 118/119 : l'échec
`W6aLayerBoundsSurfacePixelTest.emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers`
est reproduit avec les deux politiques `.25` et `.0625`, puis laissé intact.

Une tentative de `:kanvas:test` complet sur `5f971f750` est **inachevée** :
646 tests passent, 39 échouent et le test en cours est marqué skipped après
arrêt explicite du processus. Deux lectures de pile localisent le calcul
CPU dans `ColorRoundedGraphProofV1.prove`, appelé par
`W5dGradientAddressingSurfacePixelTest.sweepFullCoveragePreservesRequestedTileBudgetIdentity`.
Le worker avait consommé plus de 208 s de CPU après 263 s d'exécution ;
l'arrêt aboutit à l'exit natif 133 et Gradle 1. Ce n'est ni une absence de
GPU ni une suite verte. Aucun fichier de score/référence n'a changé.

Les 39 échecs observés se répartissent comme suit ; leur antériorité n'a
pas été établie individuellement dans ce lot :

| Suite | Échecs |
| --- | ---: |
| ImageTest / PictureTest | 1 / 1 |
| W5ePictureImageSamplingTest / W5hRuntimeEffectPictureTest | 5 / 1 |
| W6aLayerPictureTest / W6bFilterPictureTest | 1 / 2 |
| DisplayOpSceneAdapterTest / SceneRoundTripTest | 1 / 4 |
| GPUPlanSurfacePixelTest / SceneRecordingScopeTest | 4 / 1 |
| SurfaceSceneSnapshotTest / W5aMaterialSurfacePixelTest | 4 / 1 |
| W5bBlendSurfacePixelTest / W5cGradientSurfacePixelTest | 2 / 7 |
| W5dGradientAddressingSurfacePixelTest | 4 |

Les XML de cette tentative sont conservés localement dans
`/private/tmp/kanvas-w7-full-suite-5f971f750-20260929` ; ils contiennent aussi
un échec synthétique du runner Gradle, distinct des 39 tests échoués.
La pile est conservée dans `/private/tmp/kanvas-w7-w5d-stall-5f971f750.txt`.
La PR reste draft, sans promesse de merge readiness.
La relecture finale indépendante Sol ne relève aucun nouveau défaut du diff
livré et confirme les chiffres des snapshots et XML ; elle valide la
publication draft, pas le merge. L'origine des 39 échecs globaux, la
réparation AA et les domaines font/codec/ports restent ouverts ou hors scope.

## Lot cache de preuve CPU — 29 septembre 2026

Le [plan](proof-evaluation-plan.md) borne la correction à la mémoïsation
des évaluations scalaires. Une clé immutable conserve les identités des
scalars, les bits exacts des bornes et le scope Noise (région/octave).
Elle projette seulement la **clé de cache** sur les dépendances transitives
conservatrices ; les conditions réellement évaluées restent complètes,
y compris la présence des Add matérialisés. Les régions opaques
Image/Noise/GradientStop et leurs parents gardent le contexte complet.
Ni calcul numérique, ni enveloppe, budget ou règle d'admission n'est élargi.
Les facts de la première évaluation restent présents, sans rejouer leurs
doublons sur les hits ; la multiplicité textuelle des identités opaques
n'est pas un contrat conservé.

La première tentative, à contexte complet, dépassait encore 60 s.
L'analyse Astra a identifié les conditions affines externes sans incidence
sur les coordonnées antérieures ; la projection conservatrice traite
cette redondance. La revue Sol du code et sa relecture des preuves sont
approuvées. Les témoins permanents restent publics, sans test d'infrastructure.

### Résultat mesuré à corpus constant

Le [snapshot](proof-b256b3d68.json) conserve les 631 identités, 443 éligibles,
133 exclusions font, 54 codec et une quarantaine. Scènes, références,
empreintes, dimensions, seuils et limite de 30 s sont inchangés.

| Mesure | `5f971f750` | `b256b3d68` |
| --- | ---: | ---: |
| Rendus disponibles | 164 | 165 |
| Comparaisons | 142 | 143 |
| Non comparés / dimensions incompatibles | 14 / 8 | 14 / 8 |
| Échecs de rendu / setup | 226 / 50 | 227 / 50 |
| Timeouts | 3 | 1 |
| Cas à ≥99 % de pixels ±2/canal | 26 | 26 |
| Cas à ≥95 % | 36 | 36 |
| Médiane des comparaisons courantes | 65,23469 % | 65,410625 % |

Les **164 anciens rendus gardent leur empreinte RGBA exacte** : aucune perte,
aucun pixel modifié. La médiane appariée des 142 anciennes comparaisons
reste **65,23469 %** ; la hausse de médiane globale vient de l'ajout d'un cas,
pas d'une amélioration de leurs pixels.

- `ninepatch-stretch` passe de timeout à rendu comparé en **26,168 s**,
  avec **78,14951 %** de pixels ±2/canal. Il reste proche de la limite :
  ce relevé unique ne prouve pas une marge robuste sur d'autres hôtes.
- `lattice2` passe de timeout à refus en **0,527 s** :
  `w5b.geometry.incompatible-plan: Invalid W5a source authority`.
  C'est un diagnostic désormais accessible, pas un rendu gagné.
- `vertices` reste timeout à 30 s. Son résultat est persisté, puis le
  corpus reprend à l'index 608 ; aucune ligne n'est supprimée.

Pour les deux anciens timeouts terminés, `scopeReason` et `scopeOwner`,
absents de la ligne timeout, sont désormais explicitement `null` : quatre
enrichissements de champs, sans changement de scope. Les journaux locaux
sont dans `/private/tmp/kanvas-w7-proof-parity.suk2dP` ; leur agrégation
vérifie les 631 indices uniques et le même renderer/configuration.

### Tests publics et limites

Les deux nouveaux tests W7 passent en **0,163 s** dans la sélection
adjacente, qui compte **20/20 réussites**, zéro skipped, Gradle 0.
Le Sweep hard vérifie quatre TileModes et vingt paires matrix/clamp ;
le témoin bicolore vérifie les deux ordres non commutatifs, avec pixels
littéraux, Render/Readback et répétition sur la même Surface.
Ce dernier est un contrôle de non-régression, déjà vert avant projection,
pas un RED inventé. Le Sweep fournit le RED causal (timeout de 60,047 s).

Le test historique W5d auparavant bloqué termine en **1,756 s**, mais
**échoue** sur `unsupported.material.composed.numeric-domain-unbounded` ;
ses assertions restent intactes. Une sonde AA RRect sans wrapper,
strictement identique sur base et correctif, reproduit ce refus dans les
deux versions. Un premier contrôle avait accidentellement utilisé Rect
au lieu de RRect : sa conclusion a été rétractée, et aucun correctif
numérique n'a été fondé sur cette comparaison non équivalente.

Les **476 tests math geometry passent**. La tentative Kanvas complète,
bornée globalement à **240 s**, reste **inachevée et rouge** :
**779 tests observés = 728 réussites + 50 échecs + 1 interrompu**.
Le runner ajoute un échec synthétique de shutdown, distinct de ces 50.
L'arrêt atteint `W5eImageShaderSurfacePixelTest.cubicTileBoundariesMatchOracle`,
alors que la suite progressait ; ce n'est pas la preuve d'un nouveau stall.
Gradle termine 1 après 4 min 1 s ; le worker est ensuite absent.

Les 686 identités du relevé précédent sont toutes retrouvées :
646 restent vertes, les 39 échecs restent rouges, et le Sweep interrompu
atteint maintenant son refus. Aucun passage vert→rouge n'est observé dans
cette intersection. Parmi les tests atteints en plus, dix autres échecs
sont observés ; leur antériorité n'est pas établie individuellement.
La répartition reprend le tableau précédent, avec **14 échecs W5d** au lieu
de quatre, et **un W5eImageShader** supplémentaire. Ces derniers concernent
les refus numériques/lanes, les budgets/diagnostics et l'attente historique
de refus du hairline image shader ; ils restent ouverts, sans changement
d'oracle. Les XML sont archivés dans
`/private/tmp/kanvas-w7-proof-full.TzcJf8/{kanvas-test,math-geometry}`.

```sh
rtk proxy ./gradlew :kanvas:test :math:geometry:jvmTest --rerun --offline --console=plain \
  --init-script /private/tmp/kanvas-w7-proof-full-timeout.gradle --continue
```

Le lot justifie une publication draft, pas une clôture de W7 ni une merge
readiness. La suite doit encore être complétée par sélections bornées ;
les refus AA de gradient, les erreurs de budget/autorité, le timeout
`vertices` et la réparation géométrique AA restent des travaux distincts.
La relecture finale indépendante Sol confirme les comptes des XML, les
631 identités et les 164 anciennes empreintes RGBA ; elle ne relève aucun
défaut confirmé du lot et valide sa publication draft, pas le merge.

### Validation complémentaire par lots bornés

Dix sélections supplémentaires de `:kanvas:test` ont été exécutées en série,
sans modification persistante du renderer, des tests ou des oracles. Chaque
tâche est bornée à 240 s ; cette limite d'exécution ne modifie aucun budget
du renderer. Les résultats ci-dessous sont **par exécution, non additionnables
en un total de tests uniques** : des reprises recouvrent des cas déjà observés.

| Lot | Réussites | Échecs d'assertion | Cas interrompus | Terminaison |
| --- | ---: | ---: | ---: | --- |
| 01 — W5e méthodes manquantes | 14 | 3 | 0 | worker natif 133 après assertions |
| 02 — W5e frontière cubic | 1 | 0 | 0 | worker natif 133 après assertions |
| 03 — W5f Surface | 83 | 16 | 1 | limite 240 s |
| 04 — W5f image filter | 92 | 0 | 0 | worker natif 133 après assertions |
| 05 — W5g composed | 48 | 2 | 1 | limite 240 s |
| 06 — contrats GPU, API/blend, text/types existants | 1 990 | 1 254 | 0 | Gradle 1, assertions |
| 07 — W6/W7 Surface | 411 | 1 | 0 | Gradle 1, assertion |
| 08 — W5g convergence/noise, W5h convergence partiel | 89 | 4 | 1 | limite 240 s |
| 09 — W5h géométrie, image origin partiel | 671 | 0 | 1 | limite 240 s |
| 10 — reprise W5f matrix/table | 16 | 0 | 0 | worker natif 133 après assertions |

Les interruptions et erreurs synthétiques du runner ne sont pas des échecs
d'assertion. Trois conteneurs paramétrés marqués `skipped` sont aussi exclus
de la colonne « cas interrompus ». Les 57 réussites W5gNoise du lot 08 sont
attestées par le listener JSONL, son XML étant vide après l'arrêt. Le lot 09
comprend **598/598 cas de géométrie W5h réussis** ; ses six noms d'affichage
dupliqués dans ImageOrigin désignent des invocations distinctes et ne doivent
pas être fusionnés. Un `SUITE_END` lors d'un timeout ne certifie pas que toutes
les méthodes de la classe ont été exécutées.

La sélection W6/W7 atteint 38 classes et **411/412 réussites**. Son seul échec
est `emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers` :
le test attend `IllegalStateException`, mais le rendu termine avec `true`.
L'attente n'a pas été changée. La sélection large atteint 86 classes ;
`GPUAllApiBlendSurfaceTest` concentre **1 071 des 1 254 échecs**, sans que cela
prouve une cause unique. Cette dette nouvellement mesurée n'a pas de baseline
individuelle complète : elle n'est pas présentée comme autant de régressions
du cache. L'exécution de tests existants text/types n'élargit pas le périmètre
de réparation, qui exclut toujours fonts et codecs.

La validation globale reste **incomplète** : W5fGradientInterpolation,
W5gComposedMaterial, W5hConvergence et W5hImageOrigin ont encore des méthodes
ou variantes non exécutées ; W5hRuntimeEffect et W5hTextVertices ne sont pas
atteints dans ces lots. Le rejeu du seul template matrix/table du lot 10 ne
ferme pas la classe GradientInterpolation. Le dry-run ne fournit pas un
dénominateur fiable pour les invocations paramétrées et les factories.

Archives XML, résultats binaires et événements :
`/private/tmp/kanvas-w7-remaining.IzRmgJ/batches/`. Les événements des lots
06–10 sont du JSONL malgré le suffixe `.tsv` ; ceux du lot 05 sont altérés par
un échappement du listener et ne servent pas à certifier la couverture.
Les XML restent exploitables. Aucun test d'infrastructure n'a été ajouté,
aucun seuil ni exclusion n'a été changé et aucun nouveau score GM n'est
revendiqué. Ces résultats justifient un triage ciblé, pas une suite verte.
La relecture indépendante Sol recoupe ces comptes dans les XML et le listener,
y compris les conteneurs synthétiques et les 57 cas JSONL-only. Elle ne relève
pas de problème important dans ce bilan et valide sa publication draft,
sans valider le merge.

La prochaine correction de comportement vise d'abord l'autorité image/opacité
de `lattice2`, dont la cause est localisée ci-dessous ; le raffinement de preuve
Sweep vient ensuite. Les portions de validation manquantes restent explicites,
sans devenir un prétexte pour modifier leurs oracles ou multiplier les relances
globales interrompues.

### Diagnostic numérique AA et prochain correctif borné

Le refus Sweep est reproduit par une `Surface(17, 1)` avec un RRect AA
débordant et un Sweep de 0 à 360 degrés, sans wrapper, sur la base comme
sur le correctif de cache. La preuve reçoit le rectangle raster conservateur,
pas seulement les fragments de couverture non nulle. Le graph flush les
deltas subnormaux vers zéro, puis remplace les axes nuls par un avant
`Atan2`. Les axes sont donc normaux, mais le hull de `EagerSelect` perd cette
disjonction et invente zéro entre les valeurs atteignables. Le rejet provient
de la précondition de normalité d'`Atan2`, pas du GPU ou du cache.

L'avis ciblé Astra recommande une analyse auxiliaire locale des classes F32
possibles : zéro (signé inclus), subnormal non nul, normal. Le fallback reste
l'intervalle conservateur. Seules les sélections et les comparaisons de
`abs(v)` à zéro ou `MIN_NORMAL`, portant sur la même identité scalaire,
raffineraient ces classes. Il ne faut jamais déduire « normal » de `v != 0`
seul : une comparaison peut subir le FTZ (flush-to-zero).

La réparation proposée conserve la validation des **deux bras eager**,
les contextes, la réassociation des Add, le cache et tous les contrats
numériques. Pour deux axes certifiés normaux, `Atan2` découperait leurs
bornes en deux signes au plus, soit quatre rectangles, appliquerait à chacun
la même enveloppe 4096 ULP, puis réunirait les résultats. Aucune modification
du shader, de la tolérance, du raster ou des budgets n'est prévue. Ce design
n'est pas encore implémenté et aucun gain de GM ne lui est attribué.

Les témoins doivent rester publics : Sweep non uniforme en RRect AA et
PATH_STROKE, quadrants/axes/coupure angulaire, mêmes pixels au second rendu,
oracle existant inchangé, témoin rouge et vingt wrappers, refus précis puis
récupération. Les limites zéro/subnormal/normal doivent être exercées via
des transformations publiques admises ; aucun test d'infrastructure ajouté.

Radial et Conical restent distincts : leur `Sqrt` guardé par égalité à zéro
ne prouve pas l'absence de subnormaux. L'absence de subnormal dans une trace
ne suffirait pas non plus à le démontrer. Le premier nœud fautif du Conical
reste à capturer ; ses dénominateurs corrélés constituent une autre hypothèse.
La réparation de ces familles n'est pas incluse dans le correctif Sweep.

### Refus d'autorité de lattice2 localisé

Une instrumentation temporaire, retirée après le rejeu du seul index 343,
localise le refus dans `W5aMaterialPlanVersionWitnessV2.issue`. La commande 4
porte `MaterialV4(ref=5, coordinates=V3)` ; son stage original et rebasé,
leur identité canonique et les contrôles proof/structural/layout sont valides,
avec 864 octets d'uniformes. Le programme racine déclare pourtant
`versionI32=1`, donc le witness rejette l'autorité V4.

Le producteur est `FrameSourceLayoutV4.prepareAndFinish` : il ajoute
`MaterialProgramPlan.OpacityV1` autour de l'image V3. `OpacityV1` propage
aujourd'hui la version 4 seulement pour un enfant V4 ; un enfant image V3
retombe donc à V1 malgré la preuve de source V4 scellée sur le wrapper.
La correction devra construire un programme d'opacité V4 authentique pour
cette chaîne image V3, avec graph et bindings cohérents, puis vérifier les
pixels et l'opacité appliquée une seule fois. Il ne s'agit pas d'admettre
arbitrairement V1 côté renderer ni de supprimer le contrôle du witness.
La relecture Sol confirme cette causalité. Aucun correctif de comportement
n'est livré ici ; localiser le premier refus ne prouve pas qu'il soit unique.

Le relevé instrumenté, distinct de la mesure de corpus, est conservé dans
`/private/tmp/kanvas-w7-lattice-probe.tjMJ5N` : `[343,344)` exécute un GM,
reproduit le refus, et termine avec Gradle 0. Le premier intervalle
`[343,343)` était vide et n'est pas compté. Après retrait exact des logs,
`:gpu-renderer:compileKotlin :gpu-renderer:jar` termine avec Gradle 0 ;
aucun diff de source ou de test ne reste. Les contrôles restent stricts.

## Lot image/opacité — 29 septembre 2026

Le renderer `bef3af6faabcbbd205fe711e54ba38de1c4e4ac1`, sur
`codex/w7-image-opacity-authority` ([Draft #2415](https://github.com/ygdrasil-io/kanvas/pull/2415)
empilée sur #2414), corrige la cause localisée
ci-dessus. `OpacityV1` propage désormais V4 lorsqu'il enveloppe précisément
`ImageMaterialProgramV3`, comme il le faisait déjà pour un enfant V4. Le graph
numérique V4 existant est donc sélectionné ; l'opacité V1 non-image, les bindings,
le scellement de preuve et le witness strict du renderer sont conservés.
Aucun shader, budget ou contrat numérique n'est assoupli.

Le nouveau `W7ImageOpacitySurfacePixelTest` reproduit le refus d'autorité
avant modification, puis passe après correction. Il utilise une image issue
d'un snapshot public et un frame mêlant image rect, lattice opaque et lattice
avec alpha. Les variantes AA/hard-edge, `SRC_OVER`/`SRC_ATOP`, cellules
default/fixed/transparent et rendu répété vérifient les pixels au moyen de
l'oracle image existant, inchangé. Aucun test d'infrastructure n'est ajouté.
Les revues Sol de tâche et de branche ne trouvent pas de défaut bloquant ;
la publication en draft est approuvée. Elles relèvent une amélioration
de couverture non bloquante : ces nouveaux bords sont entiers. Le témoin
existant `latticeAdjacentCellsKeepFullInteriorCoverage` couvre séparément
les bords fractionnaires, pas leur combinaison avec cette opacité.

### Mesure à corpus constant

Le [snapshot](image-opacity-bef3af6fa.json), comparé à `proof-b256b3d68.json`,
conserve les 631 identités, les 443 éligibles, les 133 exclusions font,
54 codec et la quarantaine `jpg-color-cube`. Aucun port GM, PNG de référence,
seuil ni score historique n'est changé.

| Mesure | Base #2414 | Image/opacité |
| --- | ---: | ---: |
| Rendus disponibles | 165 | 166 |
| Comparaisons | 143 | 144 |
| Échecs de rendu / setup | 227 / 50 | 226 / 50 |
| Cas à ≥99 % / ≥95 % de pixels ±2/canal | 26 / 36 | 26 / 36 |
| Timeouts à 30 s | 1 | 1 |

`lattice2` est le seul changement d'issue : huit opérations dispatchées,
zéro refus, **54,0875 %** de pixels à ±2/canal, 1,063 s sur ce relevé.
Son seuil déclaré de 50 % est franchi, sans être relevé ni abaissé ; cela
ne signifie pas une parité visuelle. **Les 165 anciens rendus conservent
exactement leur empreinte RGBA**. La médiane appariée des 143 anciennes
comparaisons reste 65,410625 % ; la médiane globale devient 65,23469075520833 %
par ajout du nouveau cas, sans dégradation des anciens pixels.
`ninepatch-stretch` reste rendu en 23,261 s ; `vertices` reste timeout.

Les trois tranches `[0,607)`, `[607,608)` et `[608,631)` sont exécutées en série
sur le commit, avec 30 s par GM. Gradle termine respectivement 0, 1 (worker124
après persistance du timeout `vertices`), puis 0. Journaux immuables :
`/private/tmp/kanvas-w7-image-opacity.YmtXjI/corpus`. La sonde antérieure
`lattice2-dirty` utilise un code non committé et n'entre pas dans ce snapshot.

### Validation et réserves

La sélection finale isolée donne **9/9 tests publics réussis, Gradle 0** :
nouveau témoin, deux contrôles W7 de preuve, deux W5a d'opacité, un W5f de
filtre couleur et trois W5f de filtres image. La suite Kanvas générale est
également rejouée isolément avec une borne de 240 s : **779 cas = 728 réussites
+ 50 échecs + 1 interrompu**, Gradle 1. Les 779 identités et résultats sont
identiques à la tentative de la base, sans nouveau passage vert→rouge dans
cette intersection. `cubicTileBoundariesMatchOracle` est interrompu ; la suite
globale reste incomplète et rouge. L'échec synthétique du runner est distinct.

Les XML contiennent 742 cas ; les 37 manquants, dont sept échecs, sont conservés
dans les événements JUnit JSONL écrits directement pendant ce run. Le listener
et le total console concordent sur 779. Archives finales :
`/private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated-focused` et `isolated-full`.
Les avertissements JVM/Gradle et la dette de suite restent visibles.

Une annonce prématurée de fin du sous-agent avait fait chevaucher sa tentative
générale avec le début d'un contrôle ciblé. Son archive initiale était aussi
une copie obsolète de W5e ; l'attribution du code133 à la suite générale était
erronée. Ces deux runs sont écartés de la validation finale. Après confirmation
de leur terminaison, les contrôles et la suite générale ont été rejoués en
série, aux bornes inchangées, avec sorties d'archives dédiées. Les 27 assertions
W5e suivies de133 restent une observation préliminaire, pas une suite verte.

Le lot résout le refus d'autorité de `lattice2`, pas son écart de fidélité.
La prochaine correction est la preuve Sweep AA décrite plus haut ; Radial,
Conical, les défauts géométriques AA et les autres gates restent distincts.
La publication demeure draft, sans clôture W7 ni autorisation de merge.

## Lot preuve Sweep AA — 29 septembre 2026

Renderer `49d8224d3158002d2e52d73bc4d90976d161c19a`. L'analyse locale des
classes F32 conserve zéro (signé inclus), subnormal non nul et normal en
complément des intervalles conservateurs. Elle reconnaît les prédicats exacts
sur `abs(v)` et zéro/`MIN_NORMAL`, avec la même identité scalaire et le contexte
d'évaluation. Elle ne déduit pas « normal » d'un simple `v != 0`. Les deux bras
de `EagerSelect` restent validés, la réassociation et les preuves scellées restent
inchangées, et le cache ne mélange pas les environnements.

Pour deux opérandes certifiés normaux, `Atan2` découpe les bornes originales en
au plus deux signes chacun, soit quatre rectangles. Chaque région conserve
l'enveloppe **4096 ULP**, puis les résultats sont réunis. La limite de magnitude
de x et le domaine normal fini restent ceux de la
[spécification WGSL épinglée](https://www.w3.org/TR/2026/CRD-WGSL-20260831/#accuracy-of-concrete-expressions).
Ni shader, ni géométrie, ni budget, ni tolérance ne sont modifiés.

Le témoin public initial RRect AA non uniforme, sur `Surface(17,1)`, échoue
d'abord sur `composed.numeric-domain-unbounded`. La nouvelle preuve expose
ensuite une incohérence distincte du label de l'allocation `GradientStopData` :
le lowerer RRect déclarait un label générique alors que le validateur exigeait
le label composé déjà utilisé par Rect. Astra a confirmé la cause par lecture
du stack et du producteur/consommateur. Le correctif reprend ce seul helper et
son fallback dans le lowerer RRect ; octets, extent, lifetime, identité du slab,
witness et comparaison exacte sont conservés. `NoiseTableData` reste hors lot.

### Tests publics et limites

La sélection finale initiale comprend **9/9 réussites, Gradle 0** : huit tests
`W7ProofContextSurfacePixelTest` et le contrôle image/opacité. Le témoin RRect
initial conserve ses huit pixels bleus puis neuf rouges, Render/Readback et
replay. Le `PATH_STROKE` non uniforme utilise la route hard-edge déjà admise ;
sa première variante AA refusée n'a pas servi à élargir les capacités.
Une fixture distincte vérifie quadrants, axes et coupure angulaire avec vingt
paires CoordClamp/LocalMatrix et pixels littéraux. **La combinaison RRect ×
vingt wrappers n'est pas revendiquée** ; le test historique de budget
`sweepFullCoveragePreservesRequestedTileBudgetIdentity` reste rouge.

Les points-clamps publics zéro, `Float.MIN_VALUE` et `Float.MIN_NORMAL`
atteignent réellement Render/Readback/replay. Les petites échelles LocalMatrix
conservent séparément leurs refus précis et la récupération ; ces refus en amont
ne sont pas présentés comme preuve positive des classes F32. Aucun test
d'infrastructure ni changement de l'oracle n'est ajouté.

La review de tâche Sol approuve le code (aucun point Critical/Important), mais
relève une réserve de méthode : aucun RED `PATH_STROKE` antérieur au correctif
n'est attesté. Un contrôle causal postérieur rétablit temporairement l'ancienne
restriction `Atan2` : le test échoue alors sur `numeric-domain-unbounded`.
Le code est ensuite restauré exactement au commit mesuré. La seconde lecture
Sol valide cette preuve causale, sans la confondre avec une chronologie TDD
initiale : cette dernière reste non démontrée. La sélection initiale 9/9 et le
corpus sérialisé ci-dessous restent les preuves d'acceptation principales.
Les replays supplémentaires après mutation ne renforcent pas ce gate : une
tentative venait du cache, puis la terminaison d'un rerun n'a pas été attestée
avant le suivant. Bien que le dernier ait fini Gradle 0 avec neuf succès,
l'isolation de cette reprise n'est pas certifiée ; aucun gain de durée ou de
robustesse n'en est déduit. Aucun worker ne subsistait à la restitution finale.

La tentative générale est unique et strictement sérialisée : session `20040`,
Gradle 1 à 240 s, **730 cas = 686 réussites + 43 échecs + un interrompu**.
Les 730 identités sont appariées à la base. Six anciens échecs deviennent verts :

- W5c `sweepGradientUsesClockwiseScreenAnglesOnFourLanes` ;
- W5c `sweepGradientHandlesSpanBoundariesAndDegeneracy` ;
- W5d `nonClampDropsOnlyTheOuterEndpointDuplicate` ;
- W5d `sweepFullCoverageForcesClamp` ;
- W5d `linearTileModesCoverSignedBoundariesOnEveryLane` ;
- W5d `sweepEndpointsAndFullCoverageMatchOracle`.

Aucun nouvel échec d'assertion n'apparaît dans cette intersection, mais 49 cas
atteints par la base ne le sont pas ici. L'ancien succès
`W5eImageConvergenceSurfaceTest.unownedDirectImagesKeepExplicitLinearSampling`
est interrompu : la suite reste rouge/incomplète et ce relevé ne certifie ni
sa totalité ni une amélioration de durée. Les 43 échecs sont conservés dans
les XML/JSONL directs, et le runner ajoute un échec synthétique distinct.
Archives : `/private/tmp/kanvas-w7-sweep-aa.82Ytul/green-final-focused` et
`full-suite-240`. Les avertissements JVM/Gradle et de compilation restent visibles.

### Corpus et priorité suivante

Le [snapshot complet](sweep-aa-49d8224d3.json) garde les 631 identités, 443
éligibles, 133 exclusions font, 54 codec et la quarantaine `jpg-color-cube`.
Les trois tranches sérialisées `[0,607)`, `[607,608)`, `[608,631)` terminent
respectivement Gradle 0, 1 (timeout `vertices` persisté par le worker124), 0.
Elles utilisent le commit exact, une limite de 30 s inchangée et des archives
dédiées dans `/private/tmp/kanvas-w7-sweep-aa.82Ytul/corpus`.

Comparé à `image-opacity-bef3af6fa.json`, **aucune issue, aucun diagnostic,
aucune empreinte RGBA, aucune référence, aucun seuil ni scope ne change**.
Les 166 rendus, 144 comparaisons, 26 cas à ≥99 % et 36 à ≥95 % de pixels ±2
restent identiques ; la médiane reste 65,23469075520833 %. `vertices` reste
timeout et `ninepatch-stretch` prend 23,760 s sur ce relevé.
Ce lot répare donc des comportements publics et six tests historiques, mais
**ne produit aucun gain de GM ni de fidélité mesuré**.

La prochaine priorité de diagnostic est le groupe `w6a.layer.unsupported_child`
(51 premiers refus du corpus). Il faut isoler un cas représentatif, distinguer
les restrictions du planner des capacités réellement disponibles, puis choisir
une correction transversale mesurable ; ces 51 refus ne promettent pas 51 gains.
Radial/Conical, RRect × wrappers, AA géométrique, opacité × bords fractionnaires,
les gates W6/W0 et la suite générale restent explicitement ouverts. Publication
draft uniquement, sans clôture W7 ni autorisation de merge.

La review indépendante Sol de branche `14d2be4f8..6ad868c91` autorise la
publication draft : aucun point Critical/Important/Minor. Elle confirme le
snapshot inchangé et conserve toutes les limites ci-dessus, sans autoriser
merge ou clôture W7. PR [#2416](https://github.com/ygdrasil-io/kanvas/pull/2416)
empilée sur #2415 ; le suivi de revue/lien ne change ni code ni mesures.

## Diagnostic des sources de layers — 29 septembre 2026

Base `5c89431a1`, après la PR #2416. Le groupe de 51 premiers refus
`w6a.layer.unsupported_child` comprend 24 GMs COMPOSITE, 21 BLUR, cinq IMAGE
et un CLIP. Ce regroupement ne désigne pas une cause unique : W6a remplace
par ce message générique les `NotCandidate` de sa chaîne de sources enfants.

Le triage statique des témoins distingue :

- `PlusMergesAA` : le premier path AA est dessiné hors `saveLayer`, alors que
  la source `AaResolvedColor` exige un scope W6 explicite. Les enfants `PLUS`
  rencontreraient ensuite une limite distincte, le contrat AA ne permettant
  actuellement que `SrcOver`. Une simple admission ne crée pas ces contrats.
- `blur2rects` : paths AA avec mask blur, hors layer explicite. Il manque la
  source AA racine et son contrat de couverture filtrée. Le refus explicite
  des paths AA filtrés dans une layer reste un comportement testé et conservé.
- `crbug_899512` : rectangle AA réfléchi avec mask blur et color filter `Blend`.
  Une source Rect existante paraît pouvoir le traiter, mais la cause exacte
  du refus doit être établie. La restriction Matrix de W4a n'est pas une
  explication suffisante : W3 est essayé avant W4a et normalise déjà `Blend`.

Une nouvelle exécution sur la base confirme `crbug_899512` refusé (149 ms),
et les neuf tests `W7AaPathLayerSurfacePixelTest` passent (Gradle 0, sans
branche de refus de capacité dans les logs). Ces tests ne prouvent pas les
contrats manquants ci-dessus. Le témoin Surface réduit et la trace temporaire
de sélection ci-dessous ont ensuite établi la cause avant correction.
Archives : `/private/tmp/kanvas-w7-layer-source.mCvMdN/baseline-crbug` et
`baseline-aa-layer`.

### Cause établie et correctif retenu

Le témoin `Surface` reproduisant directement le rectangle, sa CTM, son mask
blur et son color filter passe déjà sur la base, ainsi que son replay Picture
et son aller-retour sérialisé. Ce résultat est un contrôle positif, **pas un
RED**. La trace temporaire du GM montre en revanche `PATH/ANTIALIASED` avant
et après le retrait du mask filter. W3/W4a refusent donc la géométrie Path et
W4c refuse l'AA ; aucun défaut de leur normalisation des color filters n'est
établi. Le producteur fautif est `GmCanvas.drawRect`, qui prétransforme les
coins et remplace tout rectangle à CTM non identité par un path device-space.

L'[appel Skia de référence](https://github.com/google/skia/blob/3f4c5038da37/gm/crbug_899512.cpp)
conserve au contraire `concat(matrix)` puis `drawRect`. Sur avis ciblé Astra,
le correctif retenu préserve ce couple Rect+CTM pour scale/translate, reflets
inclus, à l'intérieur de l'adaptateur : clip existant posé d'abord, puis
`save/concat/drawRect/restore` avec `finally`. Rect et Paint restent intacts.
Il ne change ni une whitelist du renderer ni le contrat des paths AA filtrés.

La preuve retenue passe par **GmCanvas puis Surface**, avec trois
témoins pixels : le cas blur exact, un gradient local réfléchi avec clip et
restauration de l'état, et un stroke mis à l'échelle. Le renderer direct déjà
vert ne peut pas remplacer leur RED avant correction.

Cette décision change la capture effectuée par l'adaptateur partagé : une
hausse du nombre de GMs rendus est un **gain d'adaptateur**, pas une nouvelle
capacité GPU. La comparaison porte sur chaque identité, les anciens rendus et
les scores, sans affirmer que l'IR capturée est restée identique. Les fixtures GM,
références, seuils et exclusions ne sont pas modifiés.

Portée réservée : rotation/skew/perspective, autres primitives prétransformées,
`setMatrix/resetMatrix`, clip différé et forwarding de `saveLayer`. La fidélité
complète du port `crbug_899512` n'est pas certifiée : AA par défaut et
`respectCTM` du blur sont des écarts distincts à auditer. Le reflet unitaire du
témoin ne prouve pas la fidélité d'un blur sous échelle non unitaire.

### Mesure complète du code `d45904e0b`

Le [snapshot](rect-adapter-d45904e0b.json) contient les **631 mêmes identités**,
dont 443 éligibles, 133 fonts, 54 codecs et la même quarantaine. Registre,
dimensions, empreintes PNG de référence, scopes, seuils et tolérances sont
identiques à [#2416](sweep-aa-49d8224d3.json). La capture Rect+CTM, elle, change.

| Indicateur | Base #2416 | Adaptateur corrigé |
| --- | ---: | ---: |
| Rendus / 443 éligibles | 166 | 192 (+26) |
| Comparaisons possibles | 144 | 170 (+26) |
| Cas à ≥99 % de pixels ±2/canal | 26 | 36 (+10) |
| Cas à ≥95 % de pixels ±2/canal | 36 | 48 (+12) |
| Médiane des comparaisons courantes | 65,23469 % | 70,23720 % |
| Médiane appariée des 144 anciennes comparaisons | 65,23469 % | 65,49051 % |
| Rendus anciens perdus | — | 0 |
| Empreintes RGBA anciennes identiques | — | 162 / 166 |

Les 26 gains sont `analytic_gradients`, `anisomips`,
`backdrop_imagefilter_croprect`, `clamped_gradients`, `color4blendcf`,
`colorcomposefilter_alpha`, `colorcomposefilter_wacky`, `colorfilterimagefilter`,
`composeshader_alpha`, `composeshader_bitmap`, `composeshader_bitmap_lm`,
`crbug_899512`, `drawimagerect_filter`, `fillrect_gradient`,
`gradient_dirty_laundry`, `gradient_matrix`, `gradients_interesting`,
`hardstop_gradients_many`, `imagefiltersgraph`, `linear_gradient_rt`,
`linear_gradient_tiny`, `localmatriximageshader`, `luminosity_overflow`,
`paint_alpha_normals_rt`, `perlinnoise` et `sweep_tiling`.
`crbug_899512` atteint **91,63905 %** ; un rendu nouveau n'est pas forcément
fidèle (`paint_alpha_normals_rt` reste à 0,22430 %, par exemple).

Quatre anciens rendus changent effectivement de pixels :

| GM | Score avant | Score après |
| --- | ---: | ---: |
| `crbug_938592` | 93,4 % | 99,8 % |
| `scaled_tilemode_gradient` | 61,02320 % | 99,44489 % |
| `thinstrokedrects` | 89,14583 % | 91,66667 % |
| `perlinnoise_localmatrix` | 62,5 % | 62,5 % |

Aucun score ancien ne baisse, mais l'égalité du score Noise **ne prouve pas
l'égalité des pixels ni l'absence de différences locales**. Le snapshot conserve
les deux empreintes dans les checkpoints respectifs. Vingt-neuf cas toujours
en échec changent seulement de premier diagnostic : aucun gain de rendu ne
leur est attribué. Les refus génériques de segment layer passent de 51 à 43,
sans prétendre que les huit sorties de ce groupe deviennent toutes des succès.

Exécutions sérielles sur le même code et timeout 30 s : `[0,607)` Gradle 0
(2 min 2 s), `[607,608)` Gradle 1 / processus 124 (`vertices`, timeout rendu),
`[608,631)` Gradle 0 (5 s). Les 50 setup failures, 200 render failures,
huit dimensions incompatibles et quatorze références non comparables restent
comptés. `ninepatch-stretch` rend en 22,991 s, avec toujours peu de marge ;
ces durées isolées ne constituent pas un benchmark. Journaux :
`/private/tmp/kanvas-w7-layer-source.mCvMdN/corpus`.

### Limites de replay révélées par les contrôles

Les trois nouveaux témoins passent réellement au rouge avant le correctif :
refus `unsupported_child` pour le blur, pixel (8,0) bleu au lieu de rouge pour
le shader, pixel (2,8) transparent au lieu de rouge pour le stroke. Les oracles
directs passent ensuite sans modification de leurs valeurs attendues.

Deux essais complémentaires de `Picture.playback` avec clip divergent : un
pixel extérieur au clip devient coloré. Le code existant ignore `SetClip` et
rejoue `DrawRect` sans réappliquer son clip capturé. La dette de CTM parent est
distincte ; elle ne suffit pas à expliquer ces deux observations. Ce lot ne
modifie pas `Picture`. Les contrôles shader/stroke vérifient donc le second
`Surface.render()` et les mêmes pixels littéraux ; seul le cas blur sans clip
revendique aussi le replay Picture. Les archives des deux échecs sont conservées
(`stage-b-green-adapter`, `stage-b-green-replay-diagnostic`) : aucune couverture
générale de replay Picture avec clip n'est revendiquée.

Le contrôle existant `GmCanvasTest.rotated clip rect is captured as a device path`
échoue également. Il appelle directement Surface/Canvas, sans passer par la
méthode de l'adaptateur modifiée ; l'échec reste rapporté, sans modifier ce test
d'infrastructure ni étendre la correction aux clips tournés.

### Validation du correctif d'adaptateur

Le commit `d45904e0b` ne touche que `GmCanvas.kt` (huit lignes) et les trois
tests pixels publics. Le run final `stage-b-final-adapter` donne **3/3 PASS**,
sans skip, Gradle 0. Les 24 contrôles W6b/W7 AA passent avec Gradle 0. Une
sélection incluant W5f a émis 68 PASS, mais sans sortie terminale exploitable :
elle reste incomplète et n'est pas une validation réussie du run entier.
Après demande de review, le seul shard W5f manquant a été relancé séparément
(`review-r1-w5f`) : **44/44 assertions PASS**, sans skip, puis le worker natif
quitte avec `133` et Gradle avec 1 après 3 min 23 s. La cause native reste
`UNKNOWN` ; ce run a maintenant une issue connue, mais n'est pas vert. Aucun
worker ne reste actif après sa terminaison. Il ne justifie aucune nouvelle
relance générale ni effacement de l'exécution incomplète précédente.
Les contrôles historiques de l'adaptateur donnent 9/10, Gradle 1, avec le
seul échec de clip tourné détaillé ci-dessus.

L'unique tentative complète `:kanvas:test` atteint 240,104 s puis sort 1
(`Could not stop all services`). Les événements/XML recensent **729 tests :
685 PASS, les mêmes 43 échecs que la base #2416 et un interrompu**.
`W5eImageConvergenceSurfaceTest.unownedSyntheticImageSamplersKeepHistoricalCompatibility`
passe sur la tentative parente mais est interrompu ici ; un autre cas de la
base n'est pas atteint. Il n'y a aucun nouvel échec d'assertion dans les 729
identités communes, mais aucune conclusion verte ou complète n'est possible.
Les warnings JVM native-access, LWJGL Unsafe et Gradle deprecation restent
présents ; le rapport conserve aussi les essais de compilation/trace invalides,
qui ne valent pas RED comportemental. Archives dans
`/private/tmp/kanvas-w7-layer-source.mCvMdN/`, comparaison avec
`/private/tmp/kanvas-w7-sweep-aa.82Ytul/full-suite-240`.

La review de tâche Sol approuve conformité et qualité après correction du
rapport : sélection incomplète explicitée et 43 échecs listés par méthode.
Le nom interne « replay » de deux résultats désigne le second rendu Surface,
pas une preuve Picture ; cette remarque mineure reste ouverte, sans modifier
les assertions. Les warnings restent visibles. La review Sol de toute la branche
`5c89431a1..728868af4` approuve la publication draft (Critical 0 / Important 0 /
Minor 1 de nommage). Elle vérifie indépendamment les 631 identités, les 26 gains,
les quatre anciens rendus modifiés et les mêmes 43 échecs. Les contrats AA racine,
AA filtré/PLUS, transforms générales et Picture/clip restent hors correction,
explicitement ouverts ; aucune clôture W7/merge n'est proposée.

## Lot source AA racine — 29 septembre 2026

Le [design](root-aa-design.md) et le [plan](root-aa-plan.md), relus par Astra,
réemploient la source MSAA4/resolve1× pour les Paths AA solid-fill SrcOver
racine d'une frame déjà possédée par W6. L'ownership ne change pas, la racine
reste 1× et chaque source isolée est composée immédiatement dans l'ordre.
Le mapping root ne réapplique pas la CTM ; le binding natif reçoit une origine
zéro explicite, au lieu d'interpréter l'ordinal multisample comme une layer.
Les chemins layer existants, seals, budgets et contrôles d'autorité restent
inchangés. Aucun adaptateur, fixture, référence, seuil ou exclusion n'a changé.

### Mesure à corpus constant

Le [snapshot](root-aa-470f62e63.json) porte le commit complet
`470f62e638618274997aec9dfd5cd4f172688001` et les **631 mêmes identités**,
dont **443 éligibles**. Les 133 fonts, 54 codecs et `jpg-color-cube` restent
exclus. Les trois tranches terminent avec des exits Gradle 0/1/0 ; le 1 est
le timeout conservé de `vertices` à 30 s (processus 124).

| Mesure | #2417 | Ce lot |
| --- | ---: | ---: |
| Rendus disponibles | 192 | 193 |
| Comparaisons possibles | 170 | 171 |
| Échecs de rendu / setup | 200 / 50 | 199 / 50 |
| Non comparés / dimensions incompatibles | 14 / 8 | 14 / 8 |
| Timeouts | 1 | 1 |
| Cas à ≥99 % / ≥95 % des pixels ±2/canal | 36 / 48 | 36 / 48 |

Le seul gain est **`rasterallocator`, à 27,2533 %**. Les **192 anciens rendus
sont pixel-identiques** ; aucune perte, aucun autre changement d'issue ou de
diagnostic. Les identités, scopes, références et seuils sont invariants.
La médiane des 170 mêmes cas reste **70,2372 %** ; celle des 171 comparaisons
devient **68,9011 %**, par ajout d'un cas moins fidèle, pas par régression
des anciens pixels. Le port `RasterAllocatorGm` se décrit lui-même comme
une approximation simplifiée ; ce rendu gagné ne valide pas sa fidélité au GM
Skia. Aucun rapprochement ISO n'est revendiqué pour ce nouveau cas.
Somme des durées par cas : **149,813 s**, sans valeur de benchmark.
`ninepatch-stretch` termine à 25,092 s, toujours proche de la borne de 30 s.

### Validation et limites

Cinq positifs directs refusent avant patch puis passent : ordre dans les
deux sens, alpha 128 composé une fois à 188 sur noir, translation/clip hard et
origine layer, deux sources stencil et budget exact **B=27 772 / B−1**.
La layer vide de la fixture B est réellement allouée (196 octets inclus).
Le run final W7 donne **16/16 PASS**, et les contrôles voisins **53/53 PASS**,
sans skip, Gradle 0. Ce sont deux sélections qui se recoupent, pas 69 tests
distincts. Les assertions positives exigent Render/Readback.

L'unique tentative globale, arrêtée par la borne de quatre minutes, termine
Gradle 1 en 4m01 : **681 PASS, 43 échecs, un interrompu** parmi 725 identités.
Ce sont les mêmes 43 échecs que la base dans l'intersection. Le cas auparavant
passant `W5eDecodedImageSurfacePixelTest.cubicDrawImageMatchesMitchellNetravaliOracle`
est interrompu ; quatre cas parents ne sont pas atteints. Le runner XML
signale aussi `failed to execute tests` / `Could not stop all services`.
La suite reste rouge/incomplète. Warnings JVM native-access, LWJGL Unsafe et
Gradle deprecation conservés ; le problème natif 133 W5f antérieur n'est ni
réexécuté ni clos par ce lot.

Les archives résident dans `/private/tmp/kanvas-w7-root-aa.8WK1ZR/`.
`green` mélange plusieurs tentatives (trois échecs alpha) ; `final` est
séparé, sans doublon. Une première mesure contrôleur avait un SHA étiqueté
incorrectement : arrêtée, conservée mais exclue. Seul `corpus-verified`
alimente le snapshot et son SHA est celui réellement exécuté.

La review Sol a demandé un oracle bleu indépendant pour le contrôle hard root,
en plus du vert de la layer et de Render/Readback. Le commit test-only
`3bfab918c` l'ajoute et précise le nom du refus Picture en layer. Son run
`review-r1` repasse **16/16**, Gradle 0 ; aucun fichier de production n'a changé
depuis le commit mesuré. Le détail d'une des trois tentatives GREEN échouées
n'est plus récupérable après réutilisation du dossier : cette perte de détail
reste signalée, sans cause inventée.
La re-review Sol approuve conformité et qualité après ces corrections, sans
nouveau défaut. La review indépendante de l'ensemble `54781716e..e900a0983`
approuve la publication draft #2418 : Critical 0 / Important 0 / Minor 1.
Le Minor reste suivi pour le chantier Picture : le refus permanent est testé
en layer, pas au root, où il n'a été observé que dans une tentative GREEN.
Le support positif Picture/AA filtré/PLUS/W6b/clips complexes et la fidélité
du port simplifié restent explicitement ouverts. Cette validation ne vaut
ni merge readiness ni clôture W7.

### Arbitrages et suite

- Nouvelle admission limitée à `!ownsW6b` : les frames mêlant filtres et root
  AA restent refusées ; la restriction ne touche pas l'AA des layers existantes.
- Source plein viewport isolée conservée : simplicité de preuve et ordre
  préservés, au coût d'une allocation mémoire conservatrice par occurrence.
- **Picture AA positif différé**, contrairement au premier plan : sa chaîne
  de compilation et son émetteur SingleSample demandent un raccord séparé.
  Le test vérifie son refus/sentinel/récupération, pas un playback réussi,
  même sans clip. Cette part du contrat initial n'est pas accomplie.

AA filtré, `PLUS`, les frames W6b et Picture AA restent des extensions
distinctes. `PlusMergesAA` et `blur2rects` refusent toujours ; les 42 refus
génériques de segments restants ne sont pas 42 gains potentiels démontrés.
Le prochain travail doit traiter la couverture AA filtrée avec un témoin
public et un consommateur réel, sans contourner le garde W6b ; le raccord
Picture reste identifié séparément. Ni merge ni clôture W7 à ce stade.

## Décisions de pilotage

1. **Vérifier les scènes avant d'optimiser leurs scores.** L'audit de témoins
   réels a déjà trouvé `matrixconvolution_bigger` réduit à trois rectangles,
   contre une référence avec convolutions et texte ; `tinybitmap` fournit
   des octets RGBA avec alpha nul au lieu du texel rouge prémultiplié de
   [Skia](https://github.com/google/skia/blob/main/gm/tinybitmap.cpp).
   `imagefiltersunpremul` passe `"unpremul"` comme `sourceId`, pas comme
   `AlphaType`, et remplace le filtre Image de
   [Skia](https://github.com/google/skia/blob/main/gm/imagefiltersunpremul.cpp)
   par un drawImage. Ces défauts de port sont suivis séparément : corriger
   une scène ne constitue pas un gain du renderer à corpus identique.
   La révision Skia ayant produit les PNG n'est pas établie ici ; les liens
   upstream servent au diagnostic, pas à inventer cette provenance.
   L'audit du lot strokes relève aussi un piège partagé dans `GmCanvas` :
   ses transformations affines sont souvent appliquées aux coordonnées des
   paths avant le draw, sans transmettre la CTM et donc sans transformer
   simultanément la largeur du stroke. Son `drawColor(color)` passe par un
   rectangle transformé, contrairement au `clear` de
   [cliplargerect upstream](https://github.com/google/skia/blob/main/gm/scaledrects.cpp).
   Le port `nonclosedpaths` remplace aussi ses deux styles par `STROKE`.
   Ces scènes ne sont pas des oracles suffisants pour une correction du
   renderer : le lot utilise des témoins `Surface` directs et conserve les
   ports inchangés pour mesurer un delta à corpus constant.
2. **Strokes/hairlines standalone et AA : lot mesuré ci-dessus.** Les refus
   initiaux `width_invalid` (15 cas), `rect_anti_alias` (11) et
   `scalar_aa_not_promoted` (19) n'étaient pas des gains additionnables.
   Le bilan réel est de 41 rendus supplémentaires avec des reculs localisés.
   L'égalité des styles dash W5b est réparée dans le lot suivant. La précision
   AA seule améliore `circle_sizes` mais fait perdre deux nouveaux rendus :
   l'expérience est retirée après revue Astra. Priorité suivante : capturer
   le refus géométrique exact et choisir une correction bornée de la limite
   stencil ; préserver les 41 nouveaux rendus. Ne pas
   remplacer un hairline par une largeur locale ni désactiver l'AA.
3. **Puis composition des layers et paths généraux.** `unsupported_child`
   est le premier refus de 51 cas, `fan_budget_exceeded` de 28 cas.
   Décomposer les refus de layer par opération enfant avant d'élargir leur
   contrat. Garder les contrôles de ressources ; les budgets ne sont pas
   relevés aveuglément pour augmenter le compteur de rendus.
4. Chaque correction du renderer doit montrer un avant/après sur les mêmes
   identités, références et scènes. Chaque correction de GM a son bilan
   distinct. Rejouer ensuite les 443 cas éligibles, y compris les refus et
   timeouts, avant de conclure à une convergence. La médiane et les cas
   proches de Skia complètent le taux de rendus disponibles.

## Fiabilité des validations

Le crash `133` des suites ciblées venait de la fermeture GLFW/AppKit depuis
un thread de shutdown JVM. Le rapport natif du 28 septembre à 13:15:17
indique `Must only be used from the main thread` dans la fermeture de fenêtre,
sur `Java: Thread-5`. Les deux suites disposent désormais explicitement du
runtime GPU en `@AfterAll`, sur le thread de test lancé avec
`-XstartOnFirstThread`, comme le runner Skia existant.

Validation réelle : **49/49 tests publics**, dont 40 W6 et 9 W7,
`:kanvas:test` **exit 0**. Cela corrige la terminaison de ces deux suites,
pas la politique de shutdown de tous les consommateurs du runtime.
Pas de nouveau test d'infrastructure : l'outil est exécuté sur les GMs
réelles et le cleanup est vérifié par les tests publics existants.

```sh
rtk proxy ./gradlew :kanvas:test --offline --console=plain \
  --tests 'org.graphiks.kanvas.surface.W7AaPathLayerSurfacePixelTest' \
  --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest'

rtk proxy node refactor/waves/W07-gm-convergence/summarize-parity.mjs \
  <dossier-des-journaux> <nouveau-snapshot.json>
```

L'agrégateur refuse un corpus incomplet, des identités dupliquées ou des
sessions mélangeant commits/configurations. Les timeouts restent des lignes
ordinaires. La relecture indépendante du lot a demandé de préserver leurs
empreintes ; correction appliquée avant le relevé final. W7 et les gates W6
encore ouverts ne sont pas déclarés terminés par ce checkpoint.
