# État W05 — material graph, gradients, images et color filters

## W5f — Task1–6 closes sur leurs tranches bornées, Task7 suivante

Dernier checkpoint Task6 : implémentation provisoire `da32e38010f29ea1c3622427e5869d200cfe4e05`
depuis BASE `b0ffdf2c51dc6d608af23416e7b6d6d4c95ca88e`,14fichiers modifiés,
aucun créé. ROOT vérifie427assertions publiques fraîches,0failure/error/skip :
interpolation280 (98ajouts Task6+182Task5),filters44,ordering10,images W5e68,
anciens W5a–d25. Cinq compiles séparées réussissent en incrémental, targets
UP-TO-DATE; pas un clean rebuild. Sources45 identiques avant/après ces gates,
staging et commit; manifest6a6bc7a0…16316/diff8b70958f…ac1f9 vérifiés.
Review Sol/high indépendante spec+quality Approved, zéro Critical/Important,
sur le seul paquet exact
BASE→HEAD113666B/SHA7b594ead…9d8fa2. Task6 est close sur sa tranche bornée.
Les quatorze Modify/R34–35 et les six Audit R36 sont vérifiés; corrections107
Add conditionné et111 partage SRGB des deux ordres approuvées. Minor cast
redondant ColorSourceProofV1:303 suivi pour nettoyage ultérieur vérifié; aucun
source fix/rerun/re-review pour ce polish sur les sources gelées.
Les cinq commandes de tests sont FAILED1/native133/executors113–117, malgré
les assertions passantes. Suite combinée93/5refus staging et antériorité non
prouvée restent OPEN. Warnings hérités et nouveau NoCastNeeded non supprimés;
aucun contournement natif/cache/lifecycle/budget/harnais. Les historiques ci-dessous
décrivent leurs checkpoints datés; celui-ci prévaut. Tâches7–8/review globale/PR
stackée Draft encore ouvertes, pas de claim ISO global.

Point d'architecture R26 fixé : une définition interne immuable du gradient
préparé, créée après l'inventaire final vérifié, sert de propriétaire commun à la
preuve numérique et au binding final. Le même compiler émet la preuve; le même
owner est authentifié par la table et le payload final. L'identité source ne
dépend pas de sa future preuve; le rebase réémet la preuve sans reconversion.
Aucune table publique partielle, preuve mutable ni nouvelle autorité. Coût :
provenance retenue et preuves réémises, à vérifier sur interning/rebase/packing et
les rendus publics. Task5 est maintenant close sur sa tranche bornée : les témoins ciblés, dont les
40cellules obligatoires LINEAR/OKLAB, budget B, mixed-range/projectif/H et
composites passent à leurs checkpoints. La suite combinée327tests a322PASS et
5refus d'exécution `readback_staging.aggregate_budget_exceeded`, pas un succès.
Atan2 durci;329assertions vérifiées par groupes et cinq compiles réussies.
Commit2e7867b livré, review Sol indépendante spec+quality Approved, zéro
Critical/Important; warnings hérités suivis, aucune correction de code requise.
Task6 HSL/OKLCH/working-space en cours avec Astra/high depuis BASEb0ffdf2c5.
R34 approuve avant édition trois raccords réels : capture du domaine outermost
dans MaterialSourceConstructionV4, préparation après inventaire vérifié dans
FrameSourceLayoutV4 et graphe polaire/Sin-Cos dans ColorSourceProofV1.
Même provider, mêmes preuves/bindings/slab/permit.
R35 ajoute précisément GradientPlanV1, validation interne des tuples/recipes
HSL/OKLCH. SRGB sélectionné par wrapper conserve son tuple et sa recipe null
historiques; pas de relaxation du constructeur/ABI public ni second slab.
Coût : risque de divergence domain/recipe/gray original ou preuve/WGSL.
Les40 témoins publics bornés, anciens
gates et review Sol restent à livrer. Tâches6–8 et review globale encore ouvertes;

Premier RED Task6 vérifié : XML UTC08:09:47.491Z,60tests/40échecs/0error/skip;
les40cellules HSL/OKLCH family×lane atteignent `unsupported.material.filter.slice`
après attentes/contre-exemples bornés et disjoints,20contrôles SRGB PASS.
Log8bea3964…30373 FAILED46s/Gradle1/executor103native133,55tasks7exec48UP;
sources45identiques avant/après, manifestb6708459…24bd0, diffbdde8937…7860f.
La première invocation arrêtée au lock du cache avant Gradle/XML périmé est
exclue. Production non modifiée à ce gate; hue/wrapper supplémentaires puis
implémentation et preuves positives/fresh Sol restent ouvertes.

R36 : six Audit/reuse Task6 précisément vérifiés (ColorNumericAuthority,
MaterialSourceFootprint, GradientAddressingCapture snapshots, MaterialPlan,
colorV4 stage, candidate gate délégué). Ils consomment déjà les vrais owners et
graphes, sans gate LINEAR/OKLAB indépendant. Aucun hunk artificiel; tous les
autres Modify initiaux/R34–35 et toutes les preuves publiques restent requis.

RED supplémentaire corrigé : XMLUTC08:13:54.038Z38tests38refus d'admission,
0error/skip,14hue/gray+20outer-wrapper families+4filter/coords. ROOT vérifie
tous les messages16Rect-slice/2Path-slice/20w5a.kind et les sources avant/après
identiques. Log1e1dcafc…4ab5c FAILED15s/Gradle1/assertions, sans native133
rapporté; ancien gate37RED+1oracle trop large exclu du compte38genuineRED.
No-clamp t1/no-matrix t.25/no-opacity1 bornés/disjoints; égalité±180 exacte HSL,
OKLCH mêmes couleurs décrites crossing, pas un faux tie. Graphe/proof/emitter et
production en cours après ces RED, aucun claim d'admission positive/clôture.

Premier gate production106 : XMLUTC08:19:14.241Z78tests/69PASS/9refus
`unsupported.material.filter.numeric-domain-unbounded`,0error/skip. Les neuf
cas sont5HSL Linear family×lane,3seam/ties etworking HSL Linear; obligation
réelle du même graphe/proof à corriger, pas une fixture à remplacer ni une
tolérance à élargir. Log1d74fd30…8ef5a FAILED59s/Gradle1/executor106native133;
sources avant/après identiques cf1959e9…0335a/diff98152c3d…35a2a.
Compile initiale SUCCESS8s/38tasks10exec28UP séparée; cinq compiles finales,
anciens gates complets/rapport/commit/Sol et clôture encore ouverts.

Cause107 : Addflatten ré-expandait une valeur HSL RGB déjà matérialisée/comparée
et perdait la borne de branche EOTF avant Pow. ROOT vérifie les vrais raccords
predicate/let et la correction ciblée `n !in conditions`, sans changer le domaine
builtin ni supprimer les marges arrondi/FMA/réassociation/FTZ.
Confirmation108 XMLUTC08:21:38.669Z58tests0failure/error/skip PASS; neuf refus
précédents levés sur ces témoins. Loge8a5677a…0a628 FAILED39s/Gradle1/native133,
sources45/diff avant-après identiques ec5c1070…8682e/e5295037…e3a76.
Le même interpréteur est partagé : classe interpolation entière, filters/order,
anciens publics affectés, cinq compiles finales et fresh Sol restent obligatoires.

Supplement109 :18assertions0failure/error/skip PASS (polar filtered-H,
working-space sur Solid et mélange SRGB/HSL/OKLCH), commande native133 FAILED.
Le risque de partage SRGB est ensuite réellement reproduit :111XMLUTC08:29:18.455Z
deux ordres legacy-first/working-first,2tests2refus `invalid.material.filter.schema`.
Inventaire Pending/Legacy distinct mais mêmes tuples SRGB/null recipe fusionnés
par la table finale. Correction dans le même FrameSourceLayout autorisé R34–35 :
égalité physique exacte symétrique avant préparation, sans perdre les identités
raw/captured source/proof ni contourner la bijection finale. Deux RED archivés,
Green/fresh classes complètes/compiles/report/commit/Sol encore ouverts.

Confirmation112 XMLUTC08:30:07.344Z3tests0failure/error/skip PASS, deux ordres
SRGB et mélange distinct des domaines. ROOT vérifie l'égalité symétrique exacte
SRGB/null/count/position+deuxRGBA, sans conversion/array ni relaxation des autres
domaines. Coût : comparaison O(nombre de stops), bornée par les limites existantes.
Log53c47a45…78684 FAILED9s/native133, sources/diff avant-après identiques.
Sources gelées après nettoyage local sans effet; groupes finaux complets puis
cinq compiles/rapport/commit/Sol à livrer avant clôture Task6.

Classe interpolation finale113 : XMLUTC08:30:49.557Z280tests0failure/error/skip
PASS,98ajouts Task6+182publics Task5 conservés. ROOT archive/log/cmp45sources et
diff vérifiés; manifest6a6bc7a0…16316/diff8b70958f…ac1f9. Commande FAILED2m36
Gradle1/executor113native13355tasks8exec47UP. Filters44/order10/images68/old25
et cinq compiles standalone enchaînent sur ces mêmes sources gelées; rapport,
commit provisoire/fresh Sol et clôture encore à fournir. Suite combinée93 reste
FAILED avec ses cinq refus staging, sans preuve d'antériorité ni workaround.
natif133 et limite d'exécution combinée restent distincts des preuves de rendu.

RED filtres/wrappers vérifié (UTC05:02:28.704Z) : 28 cas
échouent réellement à l'admission, 24 `gradient_interpolation` et 4 `opacity_child`,
sans error/skip. Les attentes et contre-exemples sont bornés/disjoints avant Surface.
Les quatre anciennes fixtures Table non bornées ne sont pas comptées comme RED
production; décalage fixe des plateaus, sans relâcher oracle/tolérance. Commande
FAILED Gradle1/executor63native133/15s (55 tâches, 7 exécutées), log SHA256
`2dee678cfb926a6894f8a8b6ccb95f04510fea32f6ebce5b2aad526d46c9d257`.
Graphe/interpréteur commun partiellement écrits, émetteur à raccorder; définition
préparée/binding/layout final non implémentés à ce checkpoint. Aucun rendu positif
des nouveaux domaines ni budget B n'est encore démontré.

Graphe/interpréteur/émetteur désormais compilés : gpu-plan et gpu-renderer exécutés,
SUCCESSFUL4s/37 tâches/7 exécutées, sans admission positive. R27 précise l'égalité
physique ancienne avant conversion : même recette de mots avec ranges finaux
relocalisés et un token pour l'unique slab final, puis bijection exacte avec
l'identité réelle, y compris les enfants image. Aucun packing/conversion précoce
ni clé approximative; interning, budget et régressions publiques restent à vérifier.

R28 ajoute uniquement le consommateur exact GradientAverageSrgbaF32 : surcharge
interne par range validé, même intégrale binaire originale straight-SRGB et même
arrondi final; anciens résultats conservés. Le slab mixte ne doit pas être
intégré en entier ni dans le domaine Lab/linear préparé. Coût Host de parcours/
copie/BigInteger retenu; témoins publics dégénérés et review restent requis.

Le layout standalone est maintenant écrit et compile (gpu-plan exécuté,
SUCCESSFUL1s/27 tâches/6 exécutées), sans binding/W3/Ready final. RED dégénérescence
vérifié UTC05:18:12.471Z : 24 cas, 8 contrôles SRGB PASS, 16 refus nouveaux domaines
(12 mapping interpolation, 4 Conical source_unimplemented), 0error/skip. Original
SRGB-average et moyenne de domaine sont bornés/disjoints avant Surface. Le cas
actuel mono-source ne prouve pas le range dans un slab mixte; ce témoin reste
requis. Capture native désormais vérifiée : Gradle1/executor64native133/17s FAILED,
55 tâches/12 exécutées/43 up-to-date, hash
`208f038c5a4d714af31fbd607f3b164c4e47ec4723acdd2e65650e05ed8afc93`.
La définition privée préparée compile séparément (gpu-plan exécuté,976ms,
27 tâches/6 exécutées), hash
`d7226cb871f3fa7fff4660239333e882e5f999945173edcaf74875a027acbd8b`.
L'agent rapporte ensuite le graphe affine/famille complet compilé après erreur
syntaxique conservée; logs exacts de cette dernière paire à vérifier. Preuve/
binding/W3/permit/Ready encore à raccorder, safe-divide projectif à compléter.
Aucune admission positive ni clôture numérique/budget Task5 à ce checkpoint.

R29 classe trois matches renderer rendus non exhaustifs par le nouveau binding
scellé : refus explicite V4 Unpromoted dans GPUPreparedMaterialProgram et les
provenances Text/Vertices, comme le refus filter V4 existant. Aucun code font ou
promotion texte/vertices; lower W5a déjà planifié refuse également cette entrée
legacy. gpu-plan compile, renderer FAILED5s/37 tâches/7 exécutées : simple erreur
d'exhaustivité, distincte de native133/RED public. Rendu final encore non validé.

Après les branches R29, gpu-plan et gpu-renderer réellement recompilés :
SUCCESSFUL42s/37 tâches/7 exécutées/30 up-to-date. L'agent rapporte la chaîne
définition→issuePrepared→binding privé→table rebasée/proof stockée, raw U32 et
stop storage colorV4 écrits. Ce n'est qu'une compile, pas une preuve d'admission/
portabilité. Bijection/construction finale Frame et W3 en raccordement; seed2,
autres lanes/composites, budget B, gates finales et review restent ouverts.

Binding final Frame désormais compilé (gpu-plan exécuté, SUCCESSFUL1s,
27 tâches/6 exécutées) : l'agent rapporte préparation→placements/root maps finaux→
bijection réelle slab/legacy/V4→construction validée. W3 recognition retient les
captures et anciens Ready; factory target/topology constructSources en cours,
non compilée. Aucun Ready/rendu public V4 ni seed lancé à ce checkpoint.

Premiers seeds Rect positifs — UTC05:53:55.953Z : LINEAR black/white et OKLAB
red/green midpoint,2PASS/0failure/error/skip après Render/Readback. Première
tentative : shader WGSL18 appelle w5c_local_point non déclaré, Gradle1/executor65
native134/6s FAILED,1skip/second non exécuté; ce n'est pas le teardown133.
Diagnostic systématique : ancien nom coordinate function sur source V4 dont le
graphe évalue déjà les coordonnées. Correction dans le stage prévu : identité
device point explicite, sans double transform ni mutation native/harness.
Nouvelle commande toujours FAILED Gradle1/executor66native133/7s,55 tâches/
8 exécutées/47 up-to-date; hash
`1f5d1f72072fdae1d2c2e33c15aa0b322b1352f5daa55af74348189a977d97d0`.
Manifest source pre/post identique rapporté, pas encore chaîne hash finale ROOT.
Seeds SRC bornés seulement : famille/lane DIFFERENCE, wrappers/filters, budget B,
dégénérescence shared-range, projectif/composites et review finale restent ouverts.

Matrice intermédiaire Rect — UTC05:56:05.510Z :125 cas,69PASS/56fail/0error/skip.
69 incluent20 contrôles famille SRGB et24 dégénérescences,8tiles,6counts2/16/17,
2transparent,2Rectwrappers,1mixeddomain,seed2 et4Line/Sweep Rect DIFFERENCE.
56 restants :42 non raccordés (32famille nonRect,8Pathfilters,2Pathwrappers),
10 refus numériques (2RadialRect,8Rectfilters),2SELECTConical/schema,2oneStop/null.
Pas de pixel mismatch dans les failures. FAILED Gradle1/executor67native133/1m1s,
55 tâches/6 exécutées/49 up-to-date; hash
`f4eb4f7f6460119e3a27edb182b12de66ed8262b8bef420e71a42d7e35b8d954`.
Node/domaine/faits de branche exacts requis avant correction numérique; pas de
nominal box/floor/clamp de complaisance. Garde commune réelle et original-SRGB/
Conical-validity-before-oneStop demeurent contraignants. All125 non-green;
autres lanes/composites/budget B/projectif/gates/review toujours ouverts.

Corrections numériques Rect, fixtures/tolérances inchangées — les traces publiques
UTC06:02:01.468Z (36tests/34fail/0error/skip, archive conservée) localisent deux
pertes de corrélation dans la preuve, pas un pixel mismatch : carré du même
scalaire traité comme deux valeurs indépendantes, puis poids complémentaires
du même `w` traités indépendamment. SAME interpréteur conserve maintenant
l'identité du facteur carré et le vrai `(1-w)*a+w*b` avec SAME poids clampé et
constantes préparées. Erreurs gamma/FTZ/FMA/reassociation conservées, ni alpha
floor, boîte nominale, modification shader de complaisance ou relâchement DIV/
sqrt positive-normal/garde zéro R19. ROOT vérifie ces deux joins source/WGSL.
Archive famille UTC06:04:01.305Z :40tests/32fail/0error/skip, les8 cellules Rect
LINEAR/OKLAB DIFFERENCE passent,32 cellules nonRect restent non raccordées.
Commande FAILED Gradle1/executor70native133/31s, hash
`a20470dc2c4262419ec520868ad9ba8c8e1f19d72ec520018b78885ae7a0a363`.
Archive filtres UTC06:06:06.836Z :16tests/8fail/0error/skip, Matrix/Table Rect8
passent; Path8 restent non raccordés. Commande FAILED Gradle1/executor71
native133/11s, hash
`53c12710958a28637af822fe9ce295896dae1756f0a0d84e9c2e1d23dbc2e930`.
Traces temporaires retirées selon le worker; retrait à confirmer sur diff final.
Coût/limite : règles corrélées spécifiques au graphe réel, erreurs conservatives
maintenues; fresh régressions affectées/review restent obligatoires. Les gates
ci-dessus ne clôturent ni Task5 ni tous les125 cas; one-stop, autres lanes/
composites, projectif, B et gates finales restent ouverts.

Gate ciblé après corrections Rect — archive UTC06:09:07.322Z :
64tests/40fail/0error/skip,24PASS (8familles Rect,8Matrix/Table Rect,
8counts dont les2one-stop);32familles nonRect et8Path filters non raccordés.
ROOT confirme header XML, log/hash
`305e116fced8fab833927c8cee21f3338b80e3e353a139af956b350d4773f156`,
FAILED Gradle1/executor72native133/43s,55tâches11exécutées44up-to-date.
Clamp01 et fast paths zéro/un sont les règles héritées inchangées dans le diff
final actuel; absence de traces temporaires vérifiée dans ColorSourceProof.
RRect capture/construction deferred en cours, autres obligations/gates ouvertes.

Tentative RRect — compilation FAILED1s puis correction SUCCESS5s,27tâches/
6exécutées. Archive publique UTC06:20:33.253Z :40tests/32fail/0error/skip,
8RectPASS,8RRect `w4b.plan.identity_invalid`,24Path/stroke
`unsupported.material.w5a.kind`. Aucun RRect natif positif à ce gate :
entrée composite Rect background+RRect AA appelle encore l'ancien construct,
transport source/inventaire composite commun en cours, sans rescue de fixture.
Log/hash
`820ec980ae950a0651e4e8af3b92932c4a9668f46c429112d368145c2409c5b9`,
FAILED Gradle1/executor73native133/51s,55tâches11exécutées44up-to-date.
Toutes obligations/gates finales Task5 restent ouvertes.

R30 — consommateur supplémentaire exact classifié avant édition :
W5bNativeGeometryGraphLowerer, packet joins Rect/AnalyticRect/RRect et Stroke.
L'ancienne exigence color lower/refus V4 doit céder au packed V4 authentifié
par le vrai graph; seul l'ancien slot géométrique peut rester Transparent.
La vraie source color-writing exige le propriétaire packed; pas de fallback,
nouvelle authority/cache/buffer/lifecycle/native workaround ni filtered H.
Coût si incorrect : refus/couleurs/provenance des scènes mixtes, à vérifier par
gates publiques destination/retained/old et Sol. ROOT joins source vérifiés,
pas encore correctif/compilation/rendu positif.

Jalon réel Rect+RRect Native — archive UTC06:27:41.359Z :
60tests/24fail/0error/skip,36PASS =20 contrôles SRGB +16 cellules
LINEAR/OKLAB×quatre familles×Rect/RRect, DIFFERENCE/destination/mutation/repeat.
Les24 failures restantes sont Path fill/stroke `unsupported.material.w5a.kind`;
ni identité RRect invalide, ni refus numérique/pixel mismatch à ce gate.
Log/hash
`af947a8119389e56e7f0e10e130c7d47fc3afe137372a5804b48205eb93cf711`,
FAILED Gradle1/executor74native133/47s,55tâches11exécutées44up-to-date.
Le worker rapporte le vrai passage inventaire Frame→préparation/proof→witness
géométrique→packed lookup; les pixels ne prouvent pas seuls l'ordre privé/
bijection/authentification, source review finale obligatoire. W4c direct/stencil,
Stroke/General/W4a/composites et témoins B/projectif/mixed-range/duplicates/
gates complètes+five compile+rapport/commit/Sol toujours ouverts.

R31 — W5aMaterialPlanLowerer supplémentaire classifié avant édition après
ROOT read du fichier complet et W4c resolveMaterialColor/packed lookup :
la branche neutralisant le seul slot géométrique pour ColorFilterV4 reconnaît
aussi GradientInterpolationBindingV4 après Opacity, avec source proof retenue.
Le vrai material() garde l'exigence packed/table/draw owner exact; W4c transporte
déjà ce packed V4. Aucun Transparent source fallback/évaluation Host, nouveau
buffer/cache/native lifecycle/filtered H/private test. Coût : refus/ownership
anciennes et nouvelles sources, à vérifier public direct/stencil/wrappers/retained
et Sol. Gate Pathfill vérifié ROOT : archive UTC06:31:36.373Z125tests/26fail/
0error/skip,99PASS dont8 nouveaux Matrix/Table DirectFill;8Stroke kind et
18unfilteredPath/wrapper schema encore refusés. Commande FAILED Gradle1/
executor75native133/1m21s,55tâches10exécutées45up-to-date, log/hash
`3b9218d953276e4b909a0236a76bfaae998f30e19e2dabdec283fa6dd96a01ba`.
Ce gate ne clôture pas Task5; R31 puis batch Stroke/General/W4a en cours.

TDD W4a/General supplémentaires : après premier setup AA mixte invalide
(UTC06:38:41.614Z3fail,1contrôle SRGB ancien en échec,2mapping RED;
native76FAILED13326s), contrôle W4a corrigé UTC06:40:03.279Z3tests2fail,
SRGBPASS/2nouveaux kind RED; native77FAILED1336s, hash
`a5efc35c129b0fa086722890cfc84a4868e7c4f22d6ad4d78dc531711ff816b3`.
General affine UTC06:41:28.210Z6tests4fail,2SRGBPASS/4nouveaux kind RED;
native78FAILED1338s, hash
`8b7171262b4d413c802d8c4d2c34dfef41e7bd9810c8c12336d6f40bd6209105`.
Tous0error/skip, archives et manifests pre/post identiques vérifiés ROOT.
Oracle coordonnées indépendant/disjoints-before-Surface; le pixel central
General est un fixed point du quarter-turn : preuve lane/interpolation/dst,
pas un discriminateur d'omission de coordonnées ni preuve projective.
40cellules obligatoires inchangées; source/coordonnées/gates restantes ouvertes.

R32 — seul General hasW5aMaterialPathContract de W5aMaterialGraphContract
supplémentaire classifié avant édition : Fill V4 inchangé, General Stroke V4
uniquement vraie leaf GradientInterpolationBindingV4 après Opacity +preuve/
table/coordonnées authentifiées. Canonical writer General déjà assigné utilise
le même discriminateur et référence table immutable réelle; filtered Stroke H
reste refusé. Helper historique PathDraw non modifié sans besoin réel séparé.
Coût witness/seal/provenance/compatibilité Fill/H à vérifier par public General/
coords/projective/retained/H et Sol; aucun élargissement geometry/AA4/native/
API/cache/buffer/lifecycle/harness/private tests.

Jalon six lanes — archive UTC06:49:55.659Z134tests/0failure/error/skip,
40cellules obligatoires LINEAR/OKLAB DIFFERENCE et contrôles SRGB positifs.
Log/hash
`3b8f66d926a2d9551b92e36a04ffd3ca2e04e15cbba1b5cc1f5de0239f2947e8`,
FAILED Gradle1/executor79native133/1m12s,55tâches11exécutées44up-to-date.
Compile couvrante SUCCESS5s37tâches7exécutées30up-to-date; manifests pre/post
complets45sources lus et même hash
`fed5acad947c788b1db218a67664d5ac0a0fa4d5d36ac68945d1be1ea7205664`
vérifiés ROOT, pas encore chaîne finale gates/staged/committed.
Task5 reste ouverte : composites clear/ordinary, B public causal, mixed-range
original-SRGB average/duplicates/changed values, coords non-fixed-point/projectif/H,
fresh old W5e/affected+five compile/Audit-reuse hooks/rapport complet/commit/Sol.

Supplément UTC06:56:16.436Z24tests16fail/0error/skip :8PASS rapportés
(4SRGB contrôles +4mixed-range average/duplicate/DECAL),8nouveaux domaines
mixedRect+lane DST réellement refusés sur callbacks clear-only,8autres failures
provider test HSL/OKLCH sans oracle supporté, non comptées RED production.
Correction provider liste explicite SRGB/LINEAR/OKLAB; SAME clear-sizing/budget/
null-table et deferred-empty factory raccordées aux callbacks prévus sous R24,
gate ciblée en cours. Log/hash
`761d7d76d24d19b567cd26f4b317a5fba8fc51ad5de9521987cb6ffd62f96e2e`,
FAILED Gradle1/executor80native1339s. Préparation après inventaire complet
inchangée; Task5/B/projectif/H/composites ordinary/gates finales non closes.

Clear-only positif — archive UTC06:58:24.322Z16tests/0failure/error/skip,
8nouveaux mixed-NoOp +4SRGB +2mixed-range average +2duplicate/DECAL.
Log/hash
`2bb3b60bb0fbe6e8e94594f4d69f8d0f9db9d656c05a0c2386e1941b716f0834`,
FAILED Gradle1/executor81native13313s, manifests pre/post identiques
`59eeb63960337c8998ef7321eedc1026c81b332a74fa8b6f1645708525452744`.
ROOT voit le vrai gap projectif : ancien BinaryParts/fractionDivide/frexp/ldexp
avec validité cumulée/point-zeroing contre Divide ordinaire dans nouveau graphe.
Correction SAME operation owner/proof/emitter sous paths déjà assignés, vraies
conditions/certificats/reset/clamp; ni nominal bounds ni division exacte.
RED public coords non-fixed-point/projectif/w0 requis, B/composite ordinary/
full gates/rapport/commit/Sol restent ouverts.

Projectif corrigé — archive UTC07:06:37.750Z6tests/0failure/error/skip.
Le Min/Max conserve les extrema ordinaires et limite la branche both-subnormal
aux seules intersections subnormales, sans clamp ajouté au shader.
Log/hash `a89e3828e6b6a20af85fb85b55641c886cacac2fda42886ca3f56a6520d55227`;
commande FAILED Gradle1/executor85native1337s; manifests identiques
`ef7aa6c537a558696387bf634c883d8e8e82470ba82f2498324cfe7ab11d3568`.
La preuve complète du nouveau safeDivide et de sa validité cumulée reste à relire.

Budget fixe B=1600000 — archive UTC07:09:08.696Z6tests/2failures/0error/skip,
quatre assertions budget positives : shared17 et distinct2 admis, distinct17
refusé précisément `resource.material.gradient.stop-budget`, récupération
et retained renders vérifiés aux mêmes limites, contrôles healthy8MiB.
Les deux autres échecs sont des fixtures ordinary oracle-unbounded avant Surface,
pas un RED production; correction ciblée du nouveau témoin avant changement.
Log/hash `591fadb11f91d4464f956b73158921574193d4f4710d3e3e24215b550eb460e7`;
commande FAILED Gradle1/executor86native13318s; manifests identiques
`b2943e3c8a5c1439c1ddd38c5d7ca3e637620e2bf154cfaa5ecb95792da6318f`.
Composites ordinary, refus H, gates finales fraîches, cinq compiles, chaîne de
hashes finale, rapport complet, commit provisoire et review Sol encore requis.

Composite ordinary — témoin endpoint borné avant Surface : archive89 UTC
07:12:35.242Z2tests/2failures/0error/skip, vrais refus schema avant changement.
Après factory commune (compile1s), archive90 UTC07:15:48.659Z2tests/2failures :
la table partagée injecte `GradientStopData` dans la lane Path solid sans stops.
Correction du consommateur réel dans RenderGraph/MaterialPlan déjà assignés,
sans nouveau resource owner; conserver références unary/filter/image-child
réellement atteintes, un seul slab global et comparaison inventaire avant permit.
Logs89/90 : `0787a88df194cc0139589d492cfba7ca763368033ac2e8fec95fa131ab186766`
/ `fe79fa67643ab1e555b6f86ef6882bd11f116180ca2737e21d735cb30c91b985`.
Commandes FAILED Gradle1/native1334s/6s, distinctes des résultats d'assertions.
Task5 encore en cours; gates finales et review indépendante non livrées.

R33 — six fichiers initialement Modify passent en Audit/reuse après lecture ROOT
des raccords réels : snapshots CoordinateNodeV2, templates numeric family,
General packed source, scratch W4b/W4d pure géométrie, candidate delegate.
Chemins exacts, hooks et coûts dans le plan durable; aucune exemption globale
ni hunk artificiel. RenderGraphConstruction est réellement modifié et non exempt.
Gates finales fraîches et review Sol doivent encore vérifier chaque raccord.

Composite ordinary corrigé — archive91 : deux nouveaux cas UTC07:17:58.281Z
et ancien Ordering1 UTC07:18:00.207Z, tous0failure/error/skip.
Refus H/changed — archive92 UTC07:19:26.849Z18tests/0failure/error/skip.
Logs91/92 : `0fdedd53790b739fd48c5c54050748d44ce74c97b6610869ead98e87d9958b79`
/ `ee70f7208ac7ca62b8752d237e476a27c8d342e3f19017e2a8e04f5f44a813e8`.
Commandes FAILED Gradle1/native1338s/9s. Sources désormais figées pour les
régressions complètes prévues et cinq compiles; rapport/commit/review restent ouverts.

Gate combinée93 — dix archives XML :327tests/5failures/0errors/skips,
322PASS;4refus sur Gradient180 et1sur Ordering10, tous submit/readback-staging
aggregate budget, sans mismatch de descriptor. W5a5/W5b3/W5c5/W5d12/
W5eDecoded14/Families27/Shader27/ColorFilter44 passent.
Log `d7ae73d1e875123bd32a1fb0ceb33001add214fa18bffba71b619243402852f6`,
FAILED Gradle1/executor93native1337m57; sources pré/post identiques
`6ef563c00ec19004e0f2178e0ff2bbf84d16924426ff58d697b1e45ff87fd5fb`.
Lecture ciblée : le pool additionne les bytes résidents scratch/readback au frame
budget physique; pool et budget sont inchangés depuis BASE5fa. Cela ne prouve pas
à lui seul que les cinq échecs précèdent cette workload. Aucune suite GREEN ni
réussite de commande; préserver la limite combinée, pas de workaround runtime,
augmentation de B, reset/GC/dispose/cache/native lifecycle/harness.
Sweep a déjà de vrais guards axes dans son graphe; preuve Atan2 normal/max-|x|
et témoins publics à durcir, puis gates affectées logiquement groupées à source
identique et cinq compiles. Rapport final, hashes, commit et review encore ouverts.

Atan2 durci — ROOT relit le vrai graphe Sweep (guards axes avant appel eager)
et le correctif strict normal(x/y), maxabs(x)≤2^126. Nouveau témoin public :
cinq cardinaux bornés rendus deux fois, puis x=2^127 refus attendu et récupération.
Archive95 UTC07:33:58.980Z2tests/2failures (refus manquant), puis archive96
UTC07:34:42.456Z2tests/0failure/error/skip après correction minimale.
Logs95/96 : `71c3166686ae50869d3dcf3622b087192fea8f73acf84fad35bf04a64ef37478`
/ `22d73eada11985f00d3153c1807023fb30523eb38236d6ccff70ad2d6771eedf`.
Commandes FAILED Gradle1/native1335s/5s; schema/emitter/geometry/budgets inchangés.
Sources figées pour groupes complets de vérification, cinq compiles et livraison.
Limite combinée93 conservée, pas de workaround native; Task5 non close.

Task5 CLOSED sur sa tranche fonctionnelle bornée — commit `2e7867bcc2d3ea63ede3f327bcca2472e369edc1`
sur BASE5fa, tree `5d856da0b43183cf20b176fa0dc2bfc750d0d0b9`.
ROOT a lu le rapport final complet308lignes/79551B, hash
`d3f183c9cd37553d5db73b8e71835b2a86e25bf0a7fa55f34b272d5baccaa1e0`.
45files4001+/647−; sources testées/post-gates/stagées/commit identiques
`e76688e035c1f565bd40650164ef22ece85fa9cb1ef4193f8425930c7906fe07`;
21comparaisons de manifests0, même SHA
`fb02620e45df3fc536554ea758c440990247dfa675bf9b41cef62cec7c84c1b7`.

Archives finales standard98–102 :329tests/0failure/error/skip vérifiés ROOT,
182interpolation+44filters+10ordering+68W5e+25anciens. Chaque commande FAILED
Gradle1/native133, durées3m21/23s/2m27/37s/1m34; cinq compiles standalone
SUCCESS incrémentales/targets UP-TO-DATE,636/602/600/628/624ms.
Échec combiné93 conservé5refus staging;97launcher spécial path exclu/remplacé102,
pas de workaround runtime ni augmentation de B. R33 six Audit/reuse exacts,
tous autres Modify/Create réellement présents; vérification figée avant review.
La fresh Sol/high spec+quality approuve le diff exact607610B,
`085e5be115c45779c26f7eb8b706ef7a493e74ca941cba2f26c1448535633fa0`.
Zéro Critical/Important, sole Minor de warnings hérités; aucun correctif de code
nécessaire. Le verdict ne prouve pas l'antériorité des cinq refus staging93 :
limite combinée conservée ouverte et commandes natives toujours FAILED.
Task6 peut enchaîner; tâches6–8 et review globale restent ouvertes.

Le [plan W5f](../../plans/2026-09-14-w5f-color-filters-implementation-plan.md)
prépare huit livraisons séquentielles sur `codex/w5f-color-filters`, à partir de
W5e `7a06459dde8fb3ba0a4c8287a8405bc1994ce883`. Les douze color filters
non-runtime et les cinq domaines d'interpolation rejoignent l'autorité material
commune, avec preuves publiques Rect/Path fill/images et les nouvelles interpolations
sur les quatre lanes gradients déjà promues. La relecture indépendante Astra a
produit cinq Important, sans Critical/Minor. Les cinq corrections sont intégrées:
certificat source authentifié/corrélé, achromatisme polaire exact, préflight au
premier snapshot, inventaire complet des readers V4 et matrice publique attribuée.
La confirmation ciblée a marqué quatre ADDRESSED et la couverture PARTIAL pour
une seule dépendance future; son ajustement local prescrit est appliqué, sans
nouvelle décision produit ni boucle de review. L'exécution séquentielle SDD a
démarré sur le worktree isolé existant. Astra a livré Task1 Matrix/Solid/Opacity
Rect dans `efe36e95eb73a5cd2e43fabb35375b01d31b4629`, après de vrais RED publics.
Sol approuve sa conformité et sa qualité, sans Critical/Important. La correction
documentaire du certificat numérique `b535d3f554163ed9f013070b17d446ebc5e9122e`
est confirmée ADDRESSED par l'unique re-review ciblée, sans nouvelle régression;
Task1 est close dans son périmètre. Le témoin Paint alpha non unitaire distinguant l'ordre
alpha/filtre est désormais livré par Task2 et vérifié par Sol. Les warnings préexistants restent
visibles, sans suppression. Cette approbation n'étend pas les preuves aux sources
gradient/image ou aux composites des tâches suivantes. Aucune gate complète,
intégration ou PR W5f n'est close.
Font, codecs externes et tests d'infrastructure restent exclus; les réserves W5e
ci-dessous sont conservées. La future PR W5f sera stackée sur W5e #2399, sans
modifier la PR parente.

### Preuves Task1 — 14 septembre 2026

Le run forcé final compte huit méthodes publiques passées, sans skip, failure ou
error d'assertion: six Matrix et deux cas W5a Solid/Opacity exacts. Les XML portent
les timestamps UTC du 13 septembre `23:56:09.529Z` et `23:56:07.771Z` respectivement.
Les nouveaux témoins exigent Render/Readback avant comparaison et couvrent
translation normalisée, mélange/clamp, alpha0/1/nonunit, mutation conservée,
répétition, couverture Rect fractionnaire et destination-read DIFFERENCE.
**Gradle exit1 / worker8 exit133 / BUILD FAILED**: ce n'est pas une commande verte.
La compilation standalone forcée de color-management, render-ir, gpu-plan,
gpu-renderer et kanvas réussit séparément, exit0. Le contrôle indépendant frais
après correction documentaire réussit aussi, exit0, en1s (6 tâches exécutées,
32 up-to-date). Aucun contournement du teardown.

À sa livraison, Task1 limitait temporairement V4 aux frames de la seule famille Rect,
y compris leurs draws ordonnés et destination-read. Task2 supprime cette restriction
en séparant la construction immuable non publiée des lanes de leur `Ready` final:
interning/proofs finaux, inventaire réel commun, permit frame/device, puis packing
et publication. Un packing local suivi d'un budget composite tardif ne clôt pas
ce gap; il ne peut pas être différé à la clôture W5f. ABI V1/V2/V3 inchangées.

### Livraison Task2 — 14 septembre 2026

Astra livre `231eae9809f7c535eaf75ec84153215a4f0f24c9`, depuis
`3dce607442c7851d980109973ac09cd675d95090`, après baseline et RED publics véritables:
Compose/Lerp et ordre interne/externe/Paint alpha, transport Rect/Path direct-stencil/
General/destination-read. Les sources d'alpha zéro suivies d'un filtre restaurant
l'alpha ne sont plus éliminées. Les certificats complets traversent les enfants
Compose/Lerp et l'émission utilise ce même graphe arrondi.

Pour les composites ordinary/native, la construction immutable non publiée est
séparée du `Ready` final. L'interning et les proofs finales, l'inventaire réel unique
target/geometry/stops/scratch et le permit frame/device précèdent le packing V4.
Le même propriétaire opaque transmet les payloads réellement lus par les graphs
finales. Sol valide ces joints R11/R13 et le témoin R12, qualité Approved, sans
Critical/Important. Son verdict spec initial Not compliant vise uniquement neuf
fichiers `Modify` sans hunk; R14 les réconcilie comme audits/réutilisations de leurs
contrats vérifiés, sans retirer d'exigence ou modifier du code. Le finding original
et les limites d'inspection demeurent. La réconciliation documentaire séparée
`02c63c14defb9645fb2bc736d1c2a363ddfa39ad` reçoit l'unique confirmation ciblée:
ADDRESSED, spec Compliant, qualité Approved, sans nouveau défaut. Task2 est close
dans son périmètre; les obligations R11/R12/R13 sont livrées et vérifiées.

Les six XML frais UTC `01:11:35.169Z`–`01:12:03.894Z` comptent27 méthodes passées:
dix nouveaux Ordering, six Matrix conservés et onze anciens cas W5a/b/c/d affectés.
Aucun skip, failure ou error d'assertion. Les témoins nouveaux exigent une attente
indépendante bornée avant Surface et Render/Readback, avec mutation/order/alpha,
DIFFERENCE pour chaque Matrix/Compose/Lerp Rect/Path, General direct/stencil et
contrôle/refus budget causal à géométrie/target/draw count identiques.
**Gradle exit1 / executor27 exit133 / BUILD FAILED37s**: commande non verte.
Compilation séparée cinq modules exit0/914ms, contrôle root frais au même commit
exit0/1s (5executed/33up-to-date): pas de forced-clean compilation ni contournement.
Les anciennes topologies prepared/W4e et toute l'arithmétique oracle partagée ne
sont pas exhaustivement revalidées par cette review. Filtres RRect/stroke et autres
sources/effets non promus restent fermés. Task3 Table/Lighting/transfers/Blend est
livrée provisoirement par Astra depuis
`5350f70b8c974e38838e6f882240a8aec76c61ff` au commit
`d1851aed60ebdaca8bf2f279c9fe6c2454073596`. La review indépendante Sol conclut
specification/quality **Needs fixes**, un seul Important I1 : le node `Sqrt(0)`
est certifié exact sans garantie d'accuracy portable WGSL; l'oracle partage ce
raccourci. Pas d'échec pixel actuel affirmé. Le controller a vérifié le finding;
Astra a livré le correctif round1 `d5e7a30011cf36311a778fe7cbd6e460acb168d4`;
la re-review Sol ciblée marque I1 ADDRESSED, specification/quality Approved,
sans nouveau Critical/Important/Minor actionable. La review initiale reste historique.
Task3 est close sur sa tranche bornée Rect/Path-fill, pas sur tous les RGBA.
Les tâches4–8 et la review globale W5f restent ouvertes. Aucun push/PR W5f/merge.

Livraison initiale Task3 :64 assertions publiques PASS dans neuf classes, sans failure/error/skip,
UTC02:27:53.354Z–02:31:02.004Z :39filters (dont29Blend individuellement nommés),
10Ordering,11anciensW5a/b/c/d et4W5e formats/Atlas/ImageShader. Les témoins
Rect/Path direct/stencil incluent alpha0/1/nonunitaire, mutation, permutation du
filtre avec Matrix, indices Table arrondis et DIFFERENCE sur destination colorée
translucide; Sol confirme ces témoins et les joins d'ownership, sans fermer I1.
**Commande FAILED Gradle1 / executor43 exit133 /3m37**, XMLprocess1failure distinct.
Compilation séparée cinq modules exit0/853ms; controller exit0/805ms, targets
UP-TO-DATE, pas de forcedclean. Diff exact17fichiers687+/86−; sources inchangées
après les gates, index vide et seuls les trois docs controller modifiés.
L'implémentation globale W5f et sa review ne sont pas closes.

Gap numérique découvert pendant Task3, encore ouvert : avec SOFT_LIGHT et un
canal straight nul à alpha non unitaire, la division WGSL autorise une borne
subnormale négative; le `select` existant évalue aussi `sqrt(cb)`, hors domaine.
Le refus de la preuve portable est donc légitime. La fixture à canal positif
peut fournir un témoin borné requis, mais ne ferme pas ce domaine public plausible.
Pas de clamp shader, plancher alpha ou modification de formule pour masquer
le gap. À reprendre si les GMs/tests d'intégration Skia rencontrent ce cas;
aucune prétention de couverture de tous les RGBA ou d'ISO. Le profil reste celui
de la [spécification WGSL épinglée](https://www.w3.org/TR/2026/CRD-WGSL-20260831/),
§§15.7.4.1/17.5.58. Sol confirme le diagnostic et ses limites, sans audit historique
exhaustif. R19 autorise une vraie garde lazy égalité-zéro dans le graph V4 commun
de l'adaptation du sqrt partagé, consommée par preuve et emission; le sqrt nu et
l'oracle ne peuvent plus supposer zéro exact. Pas de changement des formules
legacy, d'epsilon/clamp, de guard shader isolée ni de nouveau domaine promu.
Coût : la topologie/certificat doit réellement changer; une garde mal conditionnée
pourrait masquer le gap négatif/subnormal ou laisser s'exécuter le sqrt non borné.
Témoins SoftLight zero/alpha0 et nonunitaire, régressions publiques affectées et
re-review Sol le vérifient pour I1. R19 est livré par Astra et approuvé par Sol,
avec les limites de domaine et d'audit historique conservées.

Correctif I1/R19 : diff séparé exact3fichiers32+/32− depuis d1851aed vers d5e7a300,
garde égalité-zéro réelle dans le graph V4 commun, pas dans le seul shader.
Le sqrt nu exige désormais son domaine positif normal/reciprocal borné; l'oracle
raw refuse zéro/zéro-croisement, seul filterBlend modélise indépendamment la garde
réellement émise. Formules/parser/évaluateur V1–3, ABI/ownership, toutes fixtures
publiques inchangés. Coût : comparaison/branche et nouvelle identité structurelle
du graph; domaine oracle historique plus strict, pas d'approbation legacy globale.
Avant garde : oracle SOFT_LIGHT non borné AVANT Surface (28/29), puis vrai RED
public `numeric-domain-unbounded` (28/29); ne pas confondre les deux.
Après garde :64 assertions fraîches PASS/0failure/error/skip UTC02:53:13.150Z–
02:56:24.857Z, dont39filters/29Blend et10Ordering, mêmes témoins alpha0/1/nonunitaire,
mutation/ordre/destination/Rect/direct/stencil. **Commande FAILED Gradle1 /
executor46 native133 /3m38**, XMLprocess1failure distinct. Cinq compilations
autonomes exit0/815ms; controller exit0/764ms, targetsUP-TO-DATE/pas de forcedclean.
Hash source avant/après gate/staging/commit identique, index vide, seulement3docs
controller dirty; aucun changement source après lancement. Sol ciblée Approved,
I1 corrigé, aucun nouveau défaut actionable. Task3 close sur sa tranche; aucun gap
numérique plus large implicitement résolu. Task4 HSLA/HighContrast/Luma/Overdraw
en cours avec un nouvel agent Astra/high depuis le BASE propre
`a2c9964a607f373e2b0111222447a501293f3ece`; pas de décision supplémentaire,
de validation Task4 ou de nouvelle promotion implicite.

R20 vérifié avant édition : records HSLA80B/presets, interpréteur commun
Floor/Round/HSL et reader existant des quatre nouveaux kinds sont les trois
raccords supplémentaires autorisés. Trois hooks seal/snapshot/scene déjà corrects
sont réutilisés sans hunk artificiel; nouveau contrôle HSLA20 avant scan/copie
dans le premier préflight central et le compiler IR. Risques records/indices/
branche/keys/reader retenus, à vérifier par pixels publics et review Sol;
pas d'exception globale ni d'approbation historique exhaustive. Task4 non close.

Task4 avant production : baseline Matrix6+Ordering10 PASS16, commande FAILED
executor47 native133 /21s. Cinq nouveaux RED publics UTC03:11:55.753Z,5failures,
0error/skip : HSLA/HighContrast/Luma/Overdraw attendus bornés avant Surface mais
preuves Render/Readback absentes, et exception HSLA19 absente. Pas de défaut pixel
inventé ni d'échec oracle avant Surface. Commande FAILED executor48 native133 /5s.
Implémentation commune en cours; aucune validation/review Task4 close.

Checkpoint Task4 : cinq méthodes étendues PASS UTC03:17:16.955Z,0failure/error/skip,
commande FAILED executor50 native133 /36s; ce n'est pas encore le gate final69.
R21 : seul fichier Ordering réutilisé inchangé après vérification ROOT des vrais
ordres Matrix avec chacun des quatre nouveaux kinds dans le test principal,
attendus disjoints et trois lanes natives. Ses dix cas General/composite restent
obligatoires au gate frais. R22 : risque portable identifié en self-review,
Divide→Floor ne prouve pas un modulo entier exact aux seams HSL. Correction
commune autorisée : modulo secteur I32 vérifié, modulo1 x-floor(x), modulo2
multiply .5 puis Floor; mêmes recipes/keys/rebase/proof/emitter, aucun nouvel
owner/ABI/périmètre. Vérifier intégralité/range conversion/remainder normalisé,
seams publics et gates complets/Sol. Pas de faux RED pixel ni de claim native GREEN.

Gate final Task4 vérifié ROOT dans neuf XML : Filter44+Ordering10+
W5a3/W5b3/W5c1/W5d4+W5eDecoded2/Families1/ImageShader1=69PASS,0failure/error/skip,
UTC03:25:37.567Z–03:29:26.028Z. Failure synthétique du process séparée,
executor53 exit133; commande BUILD FAILED4m13,55tasks6executed49up-to-date.
Compilation séparée BUILD SUCCESSFUL820ms,38tasks5executed33up-to-date,
les cinq cibles compileKotlin UP-TO-DATE, pas de clean-rebuild. Rapport final lu
ROOT à EOF et commit provisoire21ba47a2fdf8dc2b25b5bba1e0d58fed37695a9f,
parentBASEa2c9964a,11paths377+/12−; hash binarydiff identique avant/après gates
et commit1dcc8a1f87f68e240e5e1566cc2b3507c2fe710fb573434f2f1470542e8335fc.
Contrôle ROOT compilation séparée exit0/740ms,38tasks5executed33up-to-date,
toutes cinq cibles UP-TO-DATE. Worker libéré; Sol indépendante spec+quality
approuve ce seul commit : Task4 close dans sa tranche bornée. Aucun Critical/
Important; Minor warnings native-access/Unsafe préexistants suivis pour un travail
toolchain distinct, sans suppression ni access override ni nouvelle boucle de fix.
Verdict intégral conservé, pas de réaudit historique exhaustif. R20/R21 raccords
et reuse vérifiés, R22 vrai Floor intégral/range±2^24 avant I32/remainder normalisé/
F32 exact et mêmes keys/rebase/proof/emitter vérifiés. Nomenclature math inchangée.

Task4 couvre chacun des quatre nouveaux kinds sur Rect/direct/stencilPathfill,
vrais alpha0/1/.25 avant filtre, source nécessitant clamp, BOTH ordres kind-Matrix
avec contre-factuels disjoints, mutation caller Matrix/repeat2/destination colorée
translucide et final DIFFERENCE. HSLA caller-array mutation/six secteurs/max-ties/
gray delta0/tours hue/unclamped S1.5 L1.25; Overdraw six buckets/RGB noir-blanc
fourni réellement au filtre/alpha précédant1→2/255 muté; HSLA19/21NaN priorité
longueur/NaN20Infinity20 et récupération SAME Surface. Attendus bornés avant
Surface, Render/Readback publics requis. Limites Floor±2^24/modulus1..2^24 et
diviseurs sous-normaux conservatrices, pas tout HSLA fini/RGBA ni ISO global.
Task5 LINEAR/OKLAB en cours avec un nouvel Astra/high depuis BASE propre
5fa8a61712d37442f50471389220a18ff6b7aca5, après clôture documentaire Task4
exact3docs105+/13− et checks0. Vrai leaf V4, mêmes coordinates/32B stop slab/
source proof/permit/native owners, nouvelles preuves publiques familiales prévues,
pas de nouveau choix produit ni de promotion filtre RRect/stroke.
Worker seul propriétaire Gradle pendant cette tranche; ROOT et reviewer n'en
lancent pas en parallèle. Tasks5–8/reviewglobale/PR W5f encore ouvertes.

R23 avant édition Task5 : deux raccords supplémentaires concrets autorisés,
ColorOperationGraphV1 pour le vrai prefix coordinates/stops/selection et
ColorSourceProofV1 pour la taille source exacte (ancien4words/nonfilter) et le même
interpréteur arrondi/corrélé. Pas de nouvelle autorité ni boîte SRGB nominale.
La séquence préparation stops/budget agrégé du frame est en cours de trace :
normalize produit actuellement une table liée en amont, footprint exige sa preuve,
et le composite contrôle l'inventaire final seulement à publish. Aucun permis
factice ni préparation anticipée n'est autorisé; phase minimale à confirmer.

Trace précise : RenderGraphConstruction exige déjà MaterialPlanTable;
PackedFrameSourcesV4.issue mesure une preuve finale avant son permit, et le
composite interne/rebase cette table avant de calculer target/geometry/stops/scratch
réels. Normalize n'a pas capabilities/budget; un simple contrôle stop-count ne
ferme pas le budget du frame. Raccord ciblé en rédaction, option construction
metadata-only interne séparée de la table publique, relue une fois par Astra avant
production. Aucun binding/proof/permit fictif ni deuxième planner autorisé.
Git status ROOT03:45:21Z confirme seulement les trois docs ROOT modifiés,
pas d'édition source/test Task5 à ce point; baseline worker seule Gradle.

Premiers tests Task5 (édition test-only, production inchangée) : deux midpoints
LINEAR/OKLAB RED UTC03:46:33.533Z,2failures0error/skip, attendus et discriminateur
SRGB bornés avant Surface, puis refus public
unsupported.material.mapping.gradient_interpolation à Surface.render. Pas de
pixelMismatch ni oracleUnbounded; commande FAILED executor55native133/4s séparée.
Baseline17PASS (Matrix6+Ordering10+W5c mixed1), executor54native133/23s.
Ces deux cas ne ferment pas la matrice familiale; raccord de construction/review
Astra encore en préparation avant production, ROOT aucun Gradle.

Matrice test-only étendue : XMLUTC04:04:26.996Z62tests,20contrôles SRGB PASS,
42failures0error/skip (40nouveauxdomaines +2seeds). Quatre familles × Rect/RRect/
directFill/stencilFill/stroke pour chaque domaine, SRGB avec les mêmes fixtures.
Refus de matériau pour LINEAR/OKLAB, pas de pixel mismatch ni réparation geometry.
ROOT lit la classe complète : attendus/domain-discriminator/mutation bornés et
disjoints avant Surface. Les cellules utilisent encore SRC final; la destination
colorée est écrasée et ne ferme donc pas le blend dépendant de destination requis.
Correction complète demandée au même agent, sans retirer ce RED intermédiaire.
Production inchangée; ROOT vérifie /private/tmp/w5f-task5-red-families.log,
Gradle1/executor56native133/21s FAILED,55tasks7executed48up-to-date. Hash exact
27b7c1cea9dd31183496b183b5308976ddf0aaf04fcd8c3542305903f024b80d.

Amendement Task5 révisé lu intégralement par ROOT, puis une relecture indépendante
Astra/high ciblée lancée. Production toujours en pause; Gradle libéré et inactif.
Deux décisions internes à vérifier : refs symboliques dans la construction non
émise et bijection exacte des allocations avant/après préparation. Les factories
standalone/ordinary/native et six entrées de lanes doivent conserver les mêmes
calculs géométriques, budgets, refus et validation finale. Le budget public B est
une donnée de fixture à fixer avec ses contrôles, pas une preuve déjà passée.
Pas de nouveau choix produit ni de nouvelle boucle de validation globale.

Matrice renforcée test-only XMLUTC04:08:42.404Z60tests/40failures0error/skip :
20contrôles SRGB avec vrai DIFFERENCE PASS;40LINEAR/OKLAB admission RED.
ROOT vérifie l'attendu, discriminateurs SRGB/mutationWhite à alpha128 et
destination-vs-clear bornés/disjoints avant Surface. Background127red72 dessiné
SRC, puis DIFFERENCE sur chaque cellule; aucun claim de pixels nouveaux PASS.
Exact log /private/tmp/w5f-task5-red-destination-white-mutation.log/hash
748e86013d8f8be44824c24556b1aa6c90f1b69bb0c26521473ed654157ca6c4 :
Gradle1/executor59native133/41s FAILED,55tasks7executed48up-to-date.
Tentatives de fixture antérieures (worker) UTC04:06:24.367Z et04:07:52.606Z :
60oracles non bornés avant Surface, pas60RED production; reformulation données
SRC-background/mutationWhite sans élargir les bornes. Historique conservé.

R24 : Astra ciblé With fixes,0Critical/5Important/1Minor; les deux représentations
internes sont réalisables. ROOT vérifie les raccords existants et transmet toutes
les corrections ensemble au même agent, doc-only : factory/lecteurs/candidate
transport validants; topologie compiler-owned avec source General séparé de son
enveloppe et sélection native avant witnesses; PlanPasses V4 RRect/stroke et remap
coordinates; identité physique V1–V3 historique structure+packing+slab sans nouveau
byte-array; recette exacte d'interner/2048/chaînes contiguës précomptée avant stops.
Exception explicite pour les autorités anciennes déjà résolues et retenues,
aucune autorité émise pour pending. Exact3Create+12Modify supplémentaires dans le
plan durable; autres consommateurs Task5 initiaux inchangés. Production attend
la lecture complète des contrats corrigés par ROOT, pas une nouvelle review globale.
Coûts : parcours stops/collisions, metadata interner/topologie retenue, validation
finale et préparation unique par range; pas de réévaluation des paths. B/public
positives/numeric closure restent à démontrer; native133 et limites antérieures ouvertes.

ROOT relit intégralement le raccord corrigé I1–I5+Minor (hash
25d775c87d43ed088bfd3a9c1401f0f404ff801c78a5086e367457eb8a5a68f7),
vérifie tous les contrats et ferme cette correction de plan. Contrats complets
intégrés au plan durable existant, exact3Create+12Modify R24 classifiés.
Reprise séquentielle Task5 autorisée au même agent, avec TDD public/fresh gates/
five compiles/provisional commit puis Sol spec+quality. Gradle exclusivement
worker pendant la reprise. Pas de deuxième relecture globale ni d'arbitrage user.
Aucune production LINEAR/OKLAB positive démontrée à ce point; budget B, numeric
closure, autres witnesses et Tasks5–8/reviewglobale/PR restent ouverts.

Checkpoint reprise Task5 : worker rapporte l'extraction I4/I5 dans MaterialPlan
et RawMaterialRequirementsV2, recette interner exacte et parcours raw-word commun
mesure/identité/packing, également child-image V3. Compilation gpu-plan en cours,
aucune admission LINEAR/OKLAB nouvelle ni review code acquise. Les gates finaux
doivent donc inclure les anciens consommateurs publics W5e formats/blends/A8/
Shader/Nine/Lattice/Atlas pertinents, en plus de la matrice interpolation et des
filters/order/W5c-d. Aucun travail codec/GM/infrastructure ajouté.

Premier log compile I4/I5 vérifié ROOT : BUILD SUCCESSFUL1s/27tasks6executed
21up-to-date, gpu-plan compile réellement exécuté, aucune assertion publique.
Worker ajoute les recipes communes2021/Host F32+StrictMath/cbrt signé/inverse
cube deux multiplications, adaptateur graph excluant cbrt Host-only; second compile
SUCCESSFUL1s rapporté. Régression publique en cours : ancien17 +classes W5e
DecodedImage/ImageShader/ImageFamilies complètes, source figée pendant ce run.
Pas encore de nouveau domaine admis ni de budget B/proof closure/review code.

Régression extraction85PASS vérifiée ROOT dans six XML,0failure/error/skip,
UTC04:22:49.410Z–04:25:17.744Z : W5c1/Matrix6/Ordering10 +W5eDecoded14/
ImageFamilies27/ImageShader27. Exact log/hash
6e64b4ab48b9d8c7f1e5a577fc1db9536f021dc2190c751628ea4f8e788f8be4 :
Gradle1/executor60native133/2m54s FAILED,55tasks11executed44up-to-date.
Worker conserve les quatre hashes source pendant ce run; nouvelle extraction
captured-source en cours ensuite. Ces85PASS ne sont ni le gate final Task5 ni
l'admission/preuve/budget des nouveaux domaines.

Checkpoint construction : deux records internes ajoutés (MaterialSourceConstructionV4,
SourceDeferredRenderConstructionV4) et extraction du même validateur topology/
resources/lifetimes/geometry RenderGraph, d'après worker. Compile gpu-plan en cours.
Six candidates pas encore branchées, final-layout owner à faire; aucune nouvelle
admission LINEAR/OKLAB. Contrats R24 et review finale du code toujours requis,
pas de nouvelle décision, nouveau fichier hors liste ou obstacle concret signalé.

ROOT vérifie le log deferred-topology : compile FAILED1s, nullable V1 coordinate
à MaterialSourceConstructionV4.kt155:21. Même agent informé, correction respectant
l'ancien owner nullable et nouveau compile frais requis; aucun succès de cette
étape affirmé. Pas de RED fonctionnalité ni d'exit133 pour cette erreur compile.

Compile correction nullable vérifié ROOT SUCCESSFUL4s/27tasks6executed21up-to-date,
sans !!/owner inventé (None conservé si null). Erreur initiale FAILED1s conservée.
V4 destination-copy RRect/stroke ensuite modifié, donc prochain compile/gate frais
nécessaire; aucune nouvelle admission ni closure numérique établie.

R25 : exact Modify GPUW5cGradientStopNativeV1 autorisé après lecture ROOT du
packer commun position+3zeros+straightSrgb, buffer32B littleendian/offset0/owner
existant. V4 doit y écrire le tuple préparé authentifié/taggé sans changer bytes
SRGB, réservés zéro ou single native owner. GradientPlanV1.of actuel impose0..1 :
son ancien contrat reste strict; aucun clamp Lab ni relâchement global de boîte
SRGB. Tag/recipe/original+preparedbits/rebase/packing/proof doivent rester liés.
Coût : mauvais tuple/discriminator invalide l'autorité numérique et les pixels;
signedLab/mixed-domain et vieux SRGB Native publics +Sol final requis, pas de
lifecycle/native-ownership/harness/config ou tests privés ajoutés.

Inventory test-only11RED XMLUTC04:44:55.013Z0error/skip vérifié ROOT. Lecture des
tests1/2/16/17stops/mixed-domain/transparentRGB et contre-factuels bornés/disjoints
avant Surface. Refus mapping5/stop-count6, pas de pixel mismatch. Exact log/hash
533307272f53aa48da5619944ca927140096706551c830a91382991e32cb3cf4 :
Gradle1/executor61native133/9s FAILED,55tasks11executed44up-to-date.
Ces cas restent RED, autres wrappers/tile/duplicates/filters/budget/gate final ouverts.

Réserves de fixtures à conserver : Table inverse donnant alpha0 avant un SafeU
peut franchir le domaine portable du diviseur; inverse standalone et compositions
à endpoint alpha positif ne ferment pas cette variante. HUE entrée alpha0 avec
source filter verte opaque est non borné dans l'oracle AVANT Surface, pas un refus
production démontré. Son seul témoin entrée alpha0 utilise la véritable garde
sourcealpha0; les cas alpha1/nonunitaire/ordre/destination gardent la source non nulle.
Les discontinuités Dodge/Burn abandonnées restent non vérifiées, même après
correction de l'oracle conservant les deux branches atteignables. Pas de tousRGBA/ISO.

## W5e — tranche fonctionnelle close, intégration réservée

La branche `codex/w5e-decoded-images`, empilée sur W5d, implémente les neuf tâches du
[plan W5e](../../plans/2026-09-12-w5e-decoded-images-implementation-plan.md), selon le
[design approuvé](../../specs/2026-09-12-w5e-decoded-images-design.md). La revue
indépendante Task9 est close; la revue complète a produit quatre findings, corrigés
ensemble dans `6c3b3b5aa`. L'unique re-review Sol ciblée les marque tous ADDRESSED,
sans nouvelle régression Critical/Important/Minor; spec compliance et code quality
APPROVED. Cette clôture fonctionnelle ne signifie ni succès Gradle, ni branche prête
à fusionner, ni compatibilité quasi isopixel globale. La PR demandée est un brouillon
unique basé sur `codex/w5d-gradient-addressing`, dépendant de la PR W5d #2398.

DrawImage, ImageNine, ImageLattice, Atlas, et ImageShader sur Rect/Path fill ont une
autorité MaterialV3 commune, un évaluateur de texels partagé et une preuve numérique
liée aux opérations exécutées. Sont couverts RGBA/BGRA/SRGBA/A8,
OPAQUE/PREMUL/UNPREMUL, SRGB/LINEAR_SRGB/DISPLAY_P3 (SRGBA exige SRGB),
Nearest/Linear/Cubic et les quatre modes CLAMP/REPEAT/MIRROR/DECAL par axe pour les
image shaders. Nine et Atlas restent Nearest conformément à leur API; Lattice
conserve son sampling déclaré. Les limites numériques et les capabilities ne sont
pas supprimées pour rendre ces familles nominalement universelles.

Nine et Lattice régulière sélectionnent leurs cellules dans une géométrie extérieure
unique, sans coutures AA internes. Lattice explicite et Atlas conservent leurs
contributions ordonnées, leur owner logique original et leur inventaire exact;
plus de neuf cellules restent distinctes. Les coordonnées de géométrie restent
dans `:math` avec les types I/F32/64. Atlas transforme le rectangle local
`[0,w]×[0,h]`, utilise l'origine source uniquement pour le sampling et applique
entry color/source blend avant paint alpha/final blend. Les preuves publiques
couvrent également les mélanges avec les lanes des vagues précédentes.

Capture et Picture préservent sampling, stride, payload immuable et les aliases
uniquement quand tous les faits et tous les octets correspondent. Picture10/schema4
conserve les tags ordinaires/externe et les lectures historiques8/9. Les snapshots
conservent les octets d'attachment et leur format/couleur réels, avec une provenance
typée `TRANSFER_ENCODED_LINEAR_PREMUL`, distincte du PREMUL source-space ordinaire.
Un nouveau tag d'image transporte cette distinction: les lecteurs10 intermédiaires
plus anciens le refusent; aucune compatibilité forward skippable ni réparation
heuristique d'anciens snapshots mal étiquetés. Full/subset, copy/reinterpret,
Picture.playback et Atlas translucides ont des témoins publics. Un témoin distinct
4×3 non uniforme contrôle les copies full/subset `[1,1,3,3]` sur plusieurs lignes
RGBA/BGRA, copy/reinterpret et replay Picture. Le nouveau tag géométrique Nine13
exige schema4; les profils historiques8/9 et la normalisation ImagePatch restent
valides, les archives anciennes portant ce nouveau tag sont rejetées.

Après admission image, capture/plan/submit refusés sont terminaux, sans continuation
legacy. Avant admission d'une frame complète, les lowerers historiques restent
explicitement des compatibilités sémantiques pour les formats/effects exclus, pas
des transports « physical-only ». Ils refusent un attachment snapshot avec
`unsupported.image.prepared.premultiplication` plutôt que d'inventer une seconde
conversion. Cette conservation est intentionnelle: l'autorité unique est celle des
lanes promues, pas le retrait global du legacy prévu en W8. Le correctif final
transmet sampling explicite et stride déclaré aux compatibilités non admises,
y compris leurs constructeurs synthétiques: Nine/Atlas **legacy** gardent leur
Linear historique, distinct du Nearest des lanes **promues**.

### Clôture des reviews

Le plan a été relu indépendamment par Astra avant l'implémentation. Les neuf tâches
ont leurs reviews Sol closes; Sol n'a effectué aucune implémentation. La review
globale sur `57d7bc2d5..a51c1d3ea` a demandé deux corrections Important et deux Minor.
Une seule vague Astra complète et une seule re-review Sol sur `a51c1d3ea..6c3b3b5aa`
ont fermé sampling legacy, stride, copies discriminantes et garde schema4 Nine.
Aucun finding de cette vague n'est différé et aucune seconde boucle n'est ouverte.
Les limites ci-dessous demeurent des gaps d'exécution/intégration, non des findings
silencieusement effacés par l'approbation du code.

### Vérification publique fraîche — 13 septembre 2026

```bash
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5e*' --tests '*W5dGradientAddressingSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
```

Compilation séparée du code final `6c3b3b5aa`: exit0, contrôle indépendant en908ms.
La sélection forcée complète après les quatre corrections compte **262 méthodes,
260 passées, deux skips AA4, aucune failure/error XML d'assertion**.

| Suite publique | Méthodes | Passées | Skips | Timestamp XML UTC |
| --- | ---: | ---: | ---: | --- |
| W5ePictureImageSamplingTest | 13 | 13 | 0 | 17:04:24.825Z |
| W5aMaterialSurfacePixelTest | 48 | 47 | 1 | 17:04:24.917Z |
| W5bBlendSurfacePixelTest | 50 | 50 | 0 | 17:04:36.633Z |
| W5cGradientSurfacePixelTest | 27 | 27 | 0 | 17:05:01.006Z |
| W5dGradientAddressingSurfacePixelTest | 41 | 40 | 1 | 17:05:43.929Z |
| W5eDecodedImageSurfacePixelTest | 14 | 14 | 0 | 17:07:05.578Z |
| W5eImageConvergenceSurfaceTest | 15 | 15 | 0 | 17:07:26.033Z |
| W5eImageFamiliesSurfacePixelTest | 27 | 27 | 0 | 17:07:27.630Z |
| W5eImageShaderSurfacePixelTest | 27 | 27 | 0 | 17:07:55.892Z |

La commande termine en 6m11s, **Gradle exit1 / worker142 exit133 / BUILD FAILED**.
L'XML synthétique de processus (17:04:22.192Z) compte une failure distincte. Les
skips gardent `w4d.general.texture-sample-support-unavailable`. Le crash natif
post-assertions reste le teardown AppKit/GLFW hors main thread diagnostiqué en
lecture seule; ni reset/dispose ajouté aux tests ni workaround ne le masque.
Le contrôle indépendant final convergence/Picture du même code commité compte
28/28 méthodes passées, 0skip/failure/error d'assertion
(Picture13 XML17:11:37.994Z, convergence15 XML17:11:38.098Z), y compris
l'assertion publique Render/Readback avant lecture des pixels. Gradle reste exit1,
worker143exit133, BUILD FAILED6s; failure synthétique17:11:36.495Z distincte.
Les neuf XML de la sélection complète ont été inspectés et leurs faits conservés
avant ce dernier run ciblé, qui remplace les fichiers XML courants. Le run Task9
antérieur comptait256/254/2; ce n'est pas la preuve finale après correctifs.

Les témoins de récupération distinguent capture transactionnelle suivie d'un rendu
sur la **même Surface**, et refus de budget/numérique suivi d'une nouvelle Surface
sur le même runtime. Le second ne prouve pas un rollback de cible. Surface n'expose
pas de close public: les renders répétés et la Surface suivante ne prouvent pas une
fermeture explicite. Aucun test d'infrastructure/fake device/panne native injectée.

### Budgets, identité et ownership

La revue statique contrôle les clés upload par format logique/physique, dimensions
et octets des lignes utiles, sans SourceId, padding, sampling, tiles ni provenance
sémantique. Le cache texture/view est préfixé par génération device, limité à
128 entrées/64MiB, avec LRU uniquement sur les entrées sans lease; la quarantine
reste facturée. Un miss ne publie pas d'entrée partielle, mais ne restaure pas les
victimes déjà évincées si une allocation ultérieure échoue. Les budgets sont
pessimistes même en cas de hit. Les clés pipeline sont structurelles, sans pixels
ni valeurs Cubic; les pipelines source restent per-attempt, pas un cache device
persistant revendiqué. Les leases sont détenues jusqu'à completion/rollback,
transférées dans un seul journal; seules les ressources cache-owned survivent à une
completion réussie. Les échecs de cleanup sont conservés en quarantine et une
failure queue retire conservativement la génération. Ces constats statiques ne
sont pas des mesures publiques d'allocations, de hits ou de pannes natives.

### GM décodés ciblés — quatre failures de rendu, un défaut de registre

Les cinq sources utilisent des octets décodés en mémoire ou des snapshots Surface;
aucune charge font/codec externe. Chaque tentative utilise le runner exact-name
existant et son diagnostic PIXEL, sans changer fixtures, références, thresholds,
renders ou dashboard. Leur seuil `minSimilarity=0.0` n'est qu'un smoke gate, pas
une preuve ISO. Les sept tests de filtre sélectionnés par le wildcard historique
ne comptent pas comme preuve pixel W5e.

| GM | Résultat courant | Classification |
| --- | --- | --- |
| nearest_half_pixel_image | Gradle1, GM failure1, 16:24:13.629Z; pas de score | `invalid.surface.prepared.image-lowerer-authority` dans le snapshot producteur; mélange DrawColor/images non promu, gap d'intégration common/legacy |
| image-shader | Gradle1, GM failure1, 16:25:40.138Z; pas de score | `unsupported.stroke.rect_anti_alias` dans le snapshot producteur, gap W4/W5h |
| localmatriximageshader | Gradle1, GM failure1, 16:27:14.041Z; pas de score | `w4d.general.command-not-migrated`: le wrapper GM transforme les Rect traduits en Paths AA avec CTM identité; construction W4/AA4 non admise |
| alpha_image | Gradle1, initialization failure1, 16:28:51.451Z; GM non exécuté | Classe absente du registre `META-INF/services`; aucun argument JUnit. Quand enregistrée, ColorFilter/BlurMask restent W5f/W5h |
| draw_image_set | Gradle1, GM failure1, 16:30:06.318Z; pas de score | `unsupported.image.native_binding` avant admission W5e: frame avec ColorFilter/combinaisons W5f/W5h |

Ces tentatives échouent avant génération du manifest: aucun `agentSummary`
disponible, aucun score inventé.
Les cinq commandes ont été tentées séquentiellement: quatre GM ont réellement
échoué au rendu, `alpha_image` n'a pas été exécuté. Aucun skip/refus silencieux,
aucun succès à seuil0% revendiqué. Le fichier de scores est resté byte-identique.
`image-surface` est explicitement exclu car il
charge LiberationSans et dessine des labels Font. Font, codecs externes,
`jpg-color-cube`, mipmaps/anisotropic, régénération GM/dashboard et baseline globale
restent hors de cette gate.

### Gaps maintenus, sans les confondre avec une famille non implémentée

- AA4 positif reste indisponible sur le backend actuel.
- Teardown natif exit133, absence de close public et rollback target-level après
  refus numérique ne sont pas prouvés/fermés par les assertions publiques.
- Clip Picture générique garde son écart hérité; le témoin Nine emploie le clip
  de destination équivalent et ne prouve pas le clip enregistré générique.
- Atlas borne le payload complet et les chemins arithmétiques réellement exécutés:
  certains contenus non-unit-alpha/P3, gradients et blends non séparables peuvent
  refuser `unsupported.material.image.numeric-domain-unbounded`. Ce n'est pas une
  suppression nominale de ces modes, ni un clamp ou une formule alternative.
- Formats/mélanges/effects non promus gardent la frontière legacy avant admission;
  leurs combinaisons avec snapshots typés peuvent refuser explicitement.
- Le legacy conserve son refus historique de payload au-delà de `rowBytes*height`;
  la correction du stride n'élargit pas cette convention. Ses copies complètes
  avant validation restent une dette explicite, pas une garantie de budget W5e.
- W5f filters/couleurs restantes, W5g blend-children/noise et W5h runtime effects/H,
  layers W6 et convergence globale W7/retrait legacy W8 restent ouverts.

## Historique W5d — gradients et adressage

W5d couvre Linear/Radial/Sweep/Conical SRGB, CLAMP/REPEAT/MIRROR/DECAL, `WithLocalMatrix`, `CoordClamp`, coordonnées ordonnées et moyenne dégénérée sur Rect, RRect analytique, Path fill/stroke et General. Task 7 ferme frame mixte et allocations uniques; le correctif Task 8 ferme capture, identité Sweep et transport General. La sélection conjointe finale du 12 septembre 2026 compte 166 méthodes, 164 passées, deux skips AA4 et aucune failure/error XML. Gradle reste exit 1 avec worker natif exit 133 après les assertions : ce n'est pas un succès de commande. La re-review globale indépendante reste distincte de cette preuve et de l'auto-review.

## Correctif W5d Task 8 — capture, Sweep et General

Trois témoins publics ont été RED contre `e76c9bc1c75023584424709be47b9d377c4712b1` avant production : `admittedFamiliesPreserveInvalidCoordinatesForPreciseRefusal`, `sweepFullCoveragePreservesRequestedTileBudgetIdentity` et `generalHardEdgePathTransportsOrderedCoordinates`. La table capture couvre quatre familles × quatre modes × matrix/subset non fini (32 cas), avec diagnostic W5d exact et récupération. Le prédicat reste limité à SRGB et au wrapper grammar admis.

Sweep full-coverage conserve le requested tile original dans le graphe, le program et l'autorité numérique, avec effective CLAMP. Stop normalization et tile lowering gardent la sémantique CLAMP. La Surface 17×1, 64 lanes Rect/RRect, quatre Sweep à 20 paires matrix/clamp et 60 Solid, rend à 4 MiB. À 1 577 000, quatre demandes CLAMP identiques tiennent; les quatre demandes distinctes refusent précisément `resource.material.gradient.coordinate-uniform-budget`, puis récupèrent. Seul ce comportement public distingue les structures, sans clé/bytes privés; les pixels full-coverage des quatre modes restent couverts par l'oracle existant.

General transporte désormais les coordonnées V2 depuis `SealedDraw` vers `GeneralPathDraw`, le seal canonique, les remaps/blends W5b, l'autorité prepared et le source stage partagé. Les quatre variantes publiques direct, stencil, Rect→General destination-read et General-only destination-read rendent trois fois les pixels bleus/cyan attendus sous rotation réelle. Les extents distincts évitent seulement la collision target-ID déjà différée. Le contrat legacy reste fermé; les producteurs stencil n'acquièrent pas de material binding.

L'auto-review a identifié le catch générique General qui masquait les refus Raw. Après ruling et RED public dédié, un catch de `RawMaterialRequirementsV2.Refusal` conserve son code exact. Le workload final `generalCoordinateUniformBudgetRefusesPreciselyAndRecovers` utilise une Surface 23×1 et neuf Paths EVEN_ODD de 911 contours rectangulaires chacun : géométrie valide, aucun nouveau staging de grande taille. À 1 582 000, le contrôle sans wrappers rend; les 20 paires matrix/clamp ajoutées refusent avec le code W5d exact; la même instance saine récupère à 4 MiB. L'essai 512×256, vert isolément mais perturbé par le staging résident en suite, et les essais mono-Path refusés par les limites réelles ne sont pas présentés comme clôture.

`GradientTileOperationNodeV2.ClampF32`, jamais émis, son arm de rejet et son commentaire obsolète sont retirés. La ligne README historique 160/158/65 536 est remplacée par l'état courant. Aucun checkbox du plan historique n'est modifié.

Vérification finale forcée (même commande conjointe que ci-dessous) :

| Classe | Méthodes | Passées | Failures | Errors | Skips | Timestamp XML UTC, 12 septembre 2026 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| W5aMaterialSurfacePixelTest | 48 | 47 | 0 | 0 | 1 | 15:33:19.409Z |
| W5bBlendSurfacePixelTest | 50 | 50 | 0 | 0 | 0 | 15:33:31.840Z |
| W5cGradientSurfacePixelTest | 27 | 27 | 0 | 0 | 0 | 15:33:57.005Z |
| W5dGradientAddressingSurfacePixelTest | 41 | 40 | 0 | 0 | 1 | 15:34:41.227Z |
| Total | 166 | 164 | 0 | 0 | 2 | — |

Le narrow final compte six méthodes, cinq passées et un skip (XML 15:28:24.568Z); l'affecté W5c/W5d compte 68 méthodes, 67 passées et un skip sans failure/error. La conjointe finale termine en 3m56s, Gradle exit 1 / worker 95 exit 133. Compilation séparée `:gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel` : exit 0; diff-check propre.

Les deux skips gardent `w4d.general.texture-sample-support-unavailable: W4d.2 four-sample color support is unavailable`. Le transport AA4 et son inventaire de stop slab sont raccordés par lecture, sans capability synthétique; aucune preuve AA4 positive n'est revendiquée. Owners W5c, materializer, pools, rollback/completion et ABI des stops restent inchangés. Gaps equal-extent et intervalle Conical B-cross-zero conservateur maintenus. Aucune suite d'infrastructure GPU, GM/dashboard/Skia integration/font/codec ou baseline n'est exécutée.

## Historique W5d Task 7 — agrégats et preuves publiques

Les cinq méthodes ajoutées sont `mixedFramePreservesOrderAcrossFamiliesTilesWrappersLanesAndBlends`, `coordinateUniformBudgetRefusesPreciselyAndRecovers`, `mixedCoordinateTopologiesRemainSemanticallyDistinct`, `capturedSubsetsAndStopsIgnorePostRecordMutation` et `authenticAa4OrPreciseSkip`. La frame mixte superpose Linear REPEAT Rect → Radial MIRROR RRect → Sweep DECAL Path fill → Conical CLAMP Path stroke, avec matrices locales, clamps, `Opacity(0.5)` et `DIFFERENCE`. Les témoins de chaque lane, la transparence au-dessus du seam Sweep et l'ordre inverse sont vérifiés par pixels; le pixel composé utilise l'enveloppe indépendante W5b existante. Aucun program key privé n'est inspecté. Deux topologies clamp/matrix coexistent dans une frame rouge/bleue, rendue trois fois. Une `Picture` reste rouge après mutation du subset et des stops appelants.

Le workload de budget final contient 64 lanes Rect/RRect alternées : 32 gradients à deux stops et 20 paires matrix/clamp, puis 32 Solid identiques. Tous les contrôles utilisent la même Surface 11×1. Il rend sous 4 MiB, refuse à `RenderConfig.frameLocalBudgetBytes = 1_590_000` avec `resource.material.gradient.coordinate-uniform-budget`, puis récupère sur le même runtime; le cycle est répété. Au même budget, le workload sans wrappers et celui répétant la même source wrapped rendent exactement les mêmes pixels rouges. Les petits seuils historiques 65 536/49 700 étaient perturbés par le staging déjà résident dans l'ordre de suite : le ruling a imposé une fenêtre causale au-dessus de ce high-water public, sans modification de pool, reset ou harness. Les extents distincts évitent seulement le gap target-ID différé.

Deux témoins publics adjacents ferment la revue : `coordinateBudgetPreservesNonUniformOwnerDiagnostic` garde `resource-limit.w5b.destination-budget` à 49 152 quand le non-uniforme ne tient déjà plus ; `w5aOnlyCompositeBudgetsUniqueSourcesAndRecovers` force 64 lanes Solid/Opacity SRC_OVER, admet la source identique à 1 574 000, refuse les valeurs distinctes avec `w5a.composite.unsupported` et récupère à 4 MiB. Le RED final contre `ff114d` avait trois failures précises : surcompte de la source répétée, mauvais propriétaire et refus W5a-only tardif au submit.

`RawMaterialRequirementsV2` centralise tailles, packing, identité de valeur, additions/multiplications I64 contrôlées, conversions U32/Int et capacités réelles. Le stage natif consomme exactement ses octets et son identité canonique ; les agrégats dédupliquent donc comme le materializer existant, pas par draw ni par domaine de preuve géométrique. Ils vérifient d'abord non-uniformes + sources non-V2 uniques avec le diagnostic propriétaire, puis l'ajout V2 unique : le refus W5d est causal. Le composite W5a-only ferme son inventaire après interning : target/readback une fois, géométrie par lane, slab de stops une fois sans surcompter `GradientStopData`, uniformes uniques une fois. Son lowerer garde la même distinction. Les sources restent à offset zéro non dynamique ; aucun nouveau chemin de rendu ni owner n'est introduit. Les raccords supplémentaires Raw/stage/coordonnées V1 internes/lowerer ont été explicités dans le rapport de correction.

L'audit structure/valeurs confirme : famille, requested/effective tile, version du tile graph, présence de moyenne et séquence de tags coordonnées restent structurels; stops/ranges, tuples famille, opacity, bits matrices/subsets et moyenne restent dans les seals de valeurs. Raw authentifie de nouveau le plan et sa plage avant packing; un source-stage V2 incohérent refuse avec `schema.material.gradient.coordinate-plan` avant allocation native. La preuve de ces frontières internes reste une lecture de production; les tests observent exclusivement les API publiques.

Les limites physiques viennent de `device.limits`, transportées par `GPUCapabilities.toPlanCapabilitySnapshot`; aucune valeur ni capability AA4 de test n'est ajoutée. Le résultat réel AA4 est `w4d.general.texture-sample-support-unavailable: W4d.2 four-sample color support is unavailable`; le test vérifie ce code et la récupération avant son skip. La branche AA4 positive avec pixels exacts reste non exercée sur cet adapter.

L'ownership réutilise W5c sans nouveau handle, buffer ou cache W5d : `GPUW5aSourceOwnedHandlesV2` détient uniformes, layouts, pipelines et bind groups; `materializeGradientStopsV1` alloue/upload un seul stop buffer, emprunté par les groupes de la frame. Le payload transfère l'ensemble avec `PayloadOwnedCompletion`; l'exception de materialization conserve le draft dans le rollback existant. Après submit/completion, les fermetures réussies sont retirées et les fermetures incertaines restent en quarantine pour reprise. L'ABI du stop buffer reste deux vec4, 32 bytes par stop. Les renders répétés et la récupération après refus sont publics; aucune panne d'allocation/device loss ni lifetime de handle n'est simulée. L'ownership terminal ne reprend pas de continuation legacy.

La régression forcée utilise :

```sh
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
```

| Classe | Méthodes | Passées | Failures | Errors | Skips | Timestamp XML UTC, 12 septembre 2026 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| W5aMaterialSurfacePixelTest | 48 | 47 | 0 | 0 | 1 | 14:31:02.075Z |
| W5bBlendSurfacePixelTest | 50 | 50 | 0 | 0 | 0 | 14:31:13.587Z |
| W5cGradientSurfacePixelTest | 27 | 27 | 0 | 0 | 0 | 14:31:38.403Z |
| W5dGradientAddressingSurfacePixelTest | 37 | 36 | 0 | 0 | 1 | 14:32:22.244Z |
| Total | 162 | 160 | 0 | 0 | 2 | — |

Le RED des workloads finaux est XML 14:28:02.215Z, trois failures contre la base ; le narrow GREEN est XML 14:29:09.075Z, trois passés sans skip/failure/error. La sélection conjointe ci-dessus termine en 3m50s avec `Gradle Test Executor 79` exit 133 et Gradle exit 1 (`BUILD FAILED`). Les essais intermédiaires en échec restent détaillés dans le rapport ; aucune suite isolée n'a été substituée à cette preuve conjointe. Aucun contournement native-access/Unsafe, reset du runtime ou masquage de crash n'est ajouté.

La compilation séparée `rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel` termine `BUILD SUCCESSFUL`, exit 0. `rtk git diff --check` est propre. Cette auto-review Task 7 ne remplace pas une revue globale indépendante de la stack.

Réserves maintenues : collision d'IDs de cible equal-extent entre composite W5a et session W5b sur le même runtime; intervalle Conical B-cross-zero conservateur pouvant donner un faux `DomainUnbounded`; absence de preuve AA4 positive et de panne native injectée. Le finding adjacent Minor Task 4, `GradientTileOperationNodeV2.ClampF32` public jamais émis/rejeté, était différé sans aggravation à la clôture Task 7; il est retiré par le correctif Task 8 ci-dessus. W5e images, W5f non-SRGB/filters, W5g et W5h/H restent ouverts. Aucun GM/dashboard/régénération/Skia/font/codec/baseline/`jpg-color-cube` n'a été exécuté; seuls ce document et `refactor/README.md` portent le suivi durable.

## Historique W5c

W5c est close au niveau fonctionnel sur `codex/w5c-gradients`, empilée sur W5b : les quatre gradients CLAMP/SRGB sans local matrix sont promus sur Rect, RRect analytique, Path fill et Path stroke. La clôture Task 7 est complétée par le correctif Task 8 `d5b9307a0`, qui scelle le tuple F32 Linear et ajoute un pixel public biaxial discriminant. Sa vérification finale du 12 septembre 2026 comptait 125 méthodes, 124 passées, un skip AA4 et aucune failure/error XML, avec Gradle exit 1 et crash natif post-assertions exit 133. Les sections suivantes conservent cet historique antérieur à W5d.

## Frontière et preuves publiques W5c

| Source CLAMP/SRGB | Lanes promues et preuves |
| --- | --- |
| Linear | Rect intégrale/fractionnaire, RRect analytique, Path fill direct/stencil et stroke/hairline; coordonnées locales, endpoints implicites, hard stops, 1/2/16/17 stops, mutation après Picture; tuple F32 scellé et pixel biaxial sensible à la fusion/réassociation |
| Radial | Les quatre lanes; rayon nul/petit/négatif, singleton, interpolation et composition Opacity/blend |
| Sweep | Les quatre lanes; angles écran clockwise, spans partiels/étendus, limites et dégénérescences, hard stops et Opacity/blend |
| Conical | Les quatre lanes; plus grande racine valide, masque des fragments sans racine, singleton conservant le masque, branches linéaire/concentrique/dégénérée et Opacity/blend |

`mixedGradientFramePreservesOrderRangesOpacityAndBlend` intercale Linear Rect → Radial RRect analytique avec `Opacity(0.5)` et `DIFFERENCE` → Sweep Path fill → Conical Path stroke. Linear/Sweep réutilisent une séquence exacte; Radial/Conical partagent une seconde séquence distincte de 17 stops. Chacun des quatre draws intermédiaires possède un pixel non recouvert, vérifié dans les deux ordres. Trois recouvrements entre voisins ont chacun un contre-exemple d'ordre inversé, avec attentes indépendantes et couleurs différentes. Les draws initial/final ont aussi leurs témoins propres. Aucune assertion de range, buffer, compteur, scope ou détail privé ne remplace ces pixels.

`gradientFrameBudgetRefusesThenRuntimeRecovers` rend un gradient de 257 stops sous un budget public de 1 MiB, reçoit `resource.material.gradient.stop-budget` à `RenderConfig.frameLocalBudgetBytes = 4096`, puis rend immédiatement les mêmes pixels bleus exacts sur la Surface valide, sans interrompre le runtime/backend. La Surface refusée est distincte : son recording est append-only et sa configuration immuable, sans opération publique pour retirer la frame refusée ou changer le budget.

`authenticStorageCapabilityEitherRendersOrRefusesTyped` ne consulte l'adapter de production qu'à travers `Surface.render()`. Son budget logiciel explicite de 1 MiB suffit à sa petite frame de 17 stops. Le résultat authentique observé est `exact pixels rendered`, sans skip. La branche conditionnelle accepte seulement `unsupported.material.gradient.storage-capability` pour feature/limite/bindings indisponibles, ou `resource.material.gradient.stop-budget` pour une taille physique de buffer/binding insuffisante. Cette branche de refus n'a pas été exercée sur cet adapter; aucune capability n'a été injectée.

## Ressources et ownership W5c

L'audit de production confirme l'interning ordonné par première occurrence, la déduplication des séquences normalisées exactes, les plages réécrites et les autorités numériques rebasées dans la table de frame. Les clés structurelles des programmes excluent toujours données et nombre de stops. Les bindings/ranges, le contenu canonique du slab, les versions de programme/ABI et les coordonnées/provenances scellées restent portés par les autorités de frame existantes.

Le comptage brut précède les copies de recording/capture. Les bornes des séquences normalisées et des plages U32, les tailles hôte, les additions/multiplications de bytes, les tailles physique et de binding, les nombres de bindings et le budget combiné sont contrôlés avant readiness. Le buffer storage de frame est alloué/uploadé une fois par le source materializer, emprunté par les consommateurs et retenu jusqu'à leur completion. Les handles restent dans les journals existants de fermeture/rollback/quarantaine. Ces invariants sont une inspection de production, pas des tests d'infrastructure ni une simulation de panne native.

Après admission, les quatre familles et les quatre lanes consomment le material scellé; la soumission propriétaire n'a pas de continuation legacy. Aucun chemin promu supplémentaire à supprimer n'a été trouvé dans Task 7. Les classes de gradients historiques restent nécessaires avant admission pour les combinaisons W5d/W5f/W5h; les chemins image/font/codec n'ont pas été modifiés.

## Historique W5c Task 7 — avant le correctif Linear

Avant toute modification de production, la commande exacte Step 2 a finalement passé 3/3 méthodes sur la base `27427d799`. Les deux premières tentatives avaient 2 passées et une expectation `Unbounded` dans le fixture mixte : opacité fixed-function, puis soustraction destination-read près de zéro. Ces échecs de domaine d'oracle ne sont pas des RED de production. Le fixture retenu conserve l'opacité et un blend destination-read, sans changer l'oracle ni élargir sa borne singleton/deux codes adjacents. La production reste inchangée dans Task 7.

```sh
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.mixedGradientFrame*' --tests '*W5cGradientSurfacePixelTest.gradientFrameBudget*' --tests '*W5cGradientSurfacePixelTest.authenticStorageCapability*' --no-parallel --rerun-tasks
```

## Correctif et reviews W5c Task 8

La review globale Sol initiale était `NOT READY` : Linear recalculait `dx`, `dy` et `length²` dans le shader alors que W5 §7.2 exige leur tuple F32 preflight scellé. Le commit `d5b9307a0` — `fix(gpu): consume sealed W5c linear tuple` — capture, authentifie et sérialise `linearDx`, `linearDy`, `linearX2`, `linearY2`, `linearLen2`, `linearLength` et `linearDegenerate`. WGSL consomme directement les valeurs nécessaires. L'identifiant structurel Linear porte `uniform-v2-numeric-v2`; le buffer uniforme existant contient les deux vecteurs supplémentaires, sans nouveau binding ni second buffer.

`linearBiaxialHardStopConsumesRoundedPreflightLength` a fait RED sur la production intacte : bleu attendu, rouge rendu à cause de la fusion/réassociation du calcul de `length²`. L'attente est dérivée d'opérations Kotlin Float explicites et d'une sélection locale des stops, sans factory Linear de production ni élargissement de tolérance. Le témoin passe après le correctif. La re-review du finding le marque `addressed`; la review distincte du lifecycle des ressources est `READY`. Ces conclusions restent distinctes du résultat de la commande Gradle.

## Vérification finale W5c Task 8 — 12 septembre 2026

Le controller a exécuté les commandes suivantes après le correctif :

```sh
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
```

Compilation : `BUILD SUCCESSFUL`, exit 0. La sélection publique forcée produit les XML frais suivants; tous les timestamps sont en UTC le 12 septembre 2026 :

| Classe | Méthodes | Passées | Failures | Errors | Skips | Timestamp XML |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| W5cGradientSurfacePixelTest | 27 | 27 | 0 | 0 | 0 | 00:51:35.732Z |
| W5bBlendSurfacePixelTest | 50 | 50 | 0 | 0 | 0 | 00:51:10.841Z |
| W5aMaterialSurfacePixelTest | 48 | 47 | 0 | 0 | 1 | 00:50:58.809Z |
| Total | 125 | 124 | 0 | 0 | 1 | — |

La capability W5c authentique rend `exact pixels rendered`.

Le seul skip AA4 est `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output`, refus authentique `w4d.general.texture-sample-support-unavailable`. Les autres skips AA4 de l'historique W3/W4 ci-dessous ne font pas partie de cette sélection.

Après toutes les assertions, `Gradle Test Executor 263` sort avec **133** et Gradle avec **1** (`BUILD FAILED`). C'est la caveat native/AppKit déjà suivie; les résultats XML n'en font pas un succès de commande. Aucun contournement du harness, reset de runtime ou masquage du crash n'a été ajouté. Les warnings JVM native-access/Unsafe et CoreAnalytics historiques restent distincts des résultats des méthodes.

## Limites et suite après W5c

- Le gap d'intégration connu entre les IDs de cible du composite historique W5a et de la session W5b, avec deux Surfaces de même extent sur le même runtime/backend, reste différé. Il n'est pas nécessaire de le corriger pour exprimer honnêtement la gate mixte destination-read; aucune garantie générale sur cette transition de routes n'est ajoutée.
- L'oracle Conical reste conservateur lorsque l'intervalle de B traverse zéro : il peut produire un faux `DomainUnbounded`, jamais un faux `Bounded`. Aucune tolérance élargie ni prétention de conformance exhaustive.
- AA4 et les capabilities physiques absentes ne sont jamais simulés; allocation failure, device loss et récupération après panne native ne sont pas prouvés par injection.
- À cette étape historique, W5d était la prochaine stack; sa clôture REPEAT/MIRROR/DECAL, `WithLocalMatrix` et `CoordClamp` est désormais décrite en tête. W5f conserve LINEAR/OKLAB/HSL/OKLCH et les filters, W5h les gradients Point(s)/Text/Vertices/Mesh et autres cellules H. W5e images, W5g blend-children/noise et les autres runtime effects W5h restent planifiés.
- Aucune suite GM/dashboard/render-regeneration/Skia/font/codec/`jpg-color-cube` n'a été exécutée. Les modules peuvent compiler transitivement. Seuls ce status et `refactor/README.md` reçoivent la documentation durable; les rapports d'agents restent dans le workspace SDD ignoré.

## Historique W5b — référence antérieure à W5c

W5a Solid/Opacity est close sur son périmètre. W5b, son nettoyage Task 7 et sa boucle de reviews Task 8 ont été implémentés et vérifiés sur `codex/w5b-blends`, empilée sur `codex/w5a-solid-opacity`. Les deux reviews Sol indépendantes W5b sont `READY`; la PR empilée W5b est `#2396`. Les sections historiques suivantes décrivent cette clôture avant W5c.

## Périmètre public promu W5b

Le `BlendPlan` scellé est l'autorité finale après admission. `FinalBlendPlanner` classe dans `:gpu-plan`; `W5bBlendPlanLowerer` traduit ce plan sans relire le paint public. La source reste RGBA linéaire prémultipliée et le résultat reste `D + coverage × (blend(S,D) − D)`. `DST` est un `NoOp`; `PLUS` est fixed-function sur couverture full/scissor 1× avec clamp authentifié, et destination-read sur couverture scalaire. Les copies GPU portent target, device generation et `DestinationVersionI64`, sans réutilisation après une écriture intermédiaire, avec bounds conservateurs et row pitch/budget contrôlés. Aucune lecture CPU du target ne participe au blend.

| Famille promue | Preuve publique conservée |
| --- | --- |
| Rect intégrale, fractional Rect, RRect analytique | fixed-function, `DST`, destination-read avec alpha non trivial et couverture analytique originale |
| Path fill direct et stencil-cover | mêmes trois classes de blend, ordre et mutation du Path après capture `Picture`; producteurs stencil sans material/final blend effectif |
| Path stroke et hairline, transforms généraux hard | géométrie W4 inchangée, mêmes classes de blend et capture mutable; AA4 reste un refus natif explicite |
| W4e clips complexes, masques scalaires et inverse/D24S8 | consommateurs couleur promus; préfixes stencil/mask conservés, producteur 2×2 et quantification R8 originaux |
| Point/Points | fan/hairline et limite de 64 points conservés; les 45 cellules historiques DrawPoint sont fermées sur trois commandes successives |
| Text A8 déjà résolu | fixture existante `128/255`, fixed-function/`DST`/destination-read, mutation glyphs/positions; aucune génération de font ni promotion des glyphs couleur/LCD |
| Vertices avec/sans couleurs et Mesh sans programme | modulation de la source avant blend final, trois classes de blend, mutation positions/couleurs/indices; `MeshProgram` demeure hors promotion |
| Frame mixte Rect → Point → RRect → Path → A8 → Vertices | chaque famille observée, ordre contradictoire exclu, huit mutations publiques après capture, `DST` élidé; témoins de cible retenue et frames entièrement `DST` transparents |

## Fermeture des 45 cellules DrawPoint

La dette historique est fermée pour exactement `{PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY}` × `{UNCLIPPED, SCISSOR, ALPHA_MASK}`. Chaque cellule garde ses trois draws et son oracle indépendant. Le gate dédié est filtrable sans charger de fixture font/image :

```sh
rtk proxy ./gradlew :kanvas:test --tests '*GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix' --no-parallel --max-workers=1 --rerun-tasks -q
```

La caractérisation forcée Task 7 avant et après nettoyage, le 11 septembre 2026, a passé 146 méthodes publiques : W5b 45/45, W5a 47 sélectionnées dont 1 skip, W3/W4 `GPUPlanSurfacePixelTest` 53 sélectionnées dont 1 skip, et le gate DrawPoint (1 méthode, 45 cellules). Total : 144 passées, 2 skips, 0 failure/error. Le dernier run complet a terminé à 15:29:51 UTC, exit 0. Le filtre DrawPoint seul, lui aussi forcé, a terminé à 15:33:29 UTC : 1 méthode, 45 cellules, aucun failure/error/skip. Cette fermeture ciblée ne requalifie pas une baseline globale et ne prétend pas avoir rejoué les anciennes suites d'infrastructure.

## Ownership et compatibilité restante

Le routeur conserve uniquement les continuations candidate/capture-limit/`GapNotMigrated` antérieures à l'ownership. La soumission d'un token authentifié n'a plus accès à une continuation legacy. Dans W4e, les trois retours de construction `GapNotMigrated` deviennent `GapOnPromotedScope` après authentification d'un successor W5b ou d'une élision `NoOp`, en conservant exactement le diagnostic. Les color consumers W4c/W4d/General/W4e abaissent leur blend scellé; leur ancien choix booléen/nullable vers `SRC_OVER` est supprimé. Si un source-stage destination-read perd son seal W5b, le materializer refuse et utilise son rollback existant au lieu de produire la source seule.

| Sites de production audités | Ownership et échéance |
| --- | --- |
| `EffectiveMaterialPlanner`, `FinalBlendPlanner`, compilers W3/W4 et bridges Core/A8/Vertices | classification avant `Ready`; un candidat material local ne vaut pas admission géométrique. Les aliases explicites `LegacySrcOverV1` des graphs historiques conservent leurs witnesses |
| `GpuPlanTaskListLowerer`, lowerers W4a–W4e/W5b, witnesses et prepared task-list builder | plans W5b consommés et validés; aucune reclassification après ownership. Les champs `SRC_OVER` des producteurs couleur-disabled et du véritable clear initial ne sont pas des fallbacks de draw |
| `GPUBlendPlanning.GPUBlendPlanner` et projection analytique | compatibilité seulement avant admission ou pour familles non promues. Retrait à leur promotion W5c–W5h; toutes les cellules `H` au plus tard W5h |
| `GPUOpMapper`, `AnalysisContracts` et leurs wrappers `canonicalBlendPlan`/`canonicalPlan` | admission géométrique/recording legacy préalable au seal Core, ou dispatch non promu. Ils ne récupèrent jamais une frame W5b refusée; retrait du chemin promu à son admission, retrait legacy final W8 |
| `GPUPreparedTextLowerer`/`GPUTextA8RoutePlanner`, `GPUPreparedVerticesLowerer` | plan préparé prioritaire et obligatoire sur les produits promus. Branche legacy uniquement sans ownership; primitive vertex blend interne distinct du blend final. Autres matériaux : W5c–W5h/H |
| `GPUPreparedDrawImageLowerer`, Atlas/ImageGrid, image dispatch, glyphs couleur/LCD | hors W5b : images déjà décodées W5e et final-blend Image origin `H` avant W5h; glyphs hors A8 admis gardent leur refus/capability, font toujours exclue |
| `GPUPreparedMaterialProgram`, `BlendWgslBuilder`, runtime-child/filter helpers et formules CPU legacy | blends à l'intérieur de la source, pas le blend final W5b; migrations W5f–W5h. Les dispatchers WGSL legacy à défaut source ne sont pas sélectionnés par la formule W5b validée |
| `GPUIntermediatePlanner`, `compositeBlendPlan`, layer/composite capture, mask/image-filter dispatch | compatibilité layer/spatiale W6, hors promotion W5b. Le défaut composite-label historique n'est jamais une issue d'une frame W5b admise |
| Native pipeline/cache defaults, `GPUW5aSourceStageNativeV2`, preflight/executor catches | plan/ABI et ressources exacts après ownership; défauts historiques seulement hors W5b ou producteurs. Échec natif terminal avec journal de rollback, jamais `SRC_OVER`, transparent ou source-only substitué |

Cet audit est une inspection de production, pas une assertion de source shape. Le comportement étant déjà vert, Task 7 utilise l'exception de caractérisation avant/après autorisée pour les invariants non falsifiables via `Surface`; aucun RED artificiel ni test d'infrastructure n'est ajouté. Les rapports temporaires et le ledger exhaustif restent dans le workspace SDD ignoré. Aucun ancien document durable n'a été supprimé : l'historique W5a ci-dessous conserve ses preuves et ses limites.

## Vérification et limites W5b

Les compilations ciblées sont `:render-ir:compileKotlin`, `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin` et `:kanvas:compileKotlin`, forcées avec `--rerun-tasks --no-parallel --max-workers=1`. La régression publique reprend exactement la sélection Task 6 : toute W5b, les 47 méthodes Surface W5a (sans le test immutable-graph) et les 53 gates W3/W4 publics autorisés, plus GREEN45. Aucun test sur scopes, counters, packets, bindings ou détails internes ne sert de preuve. `:gpu-renderer:compileTestKotlin` a des erreurs historiques de sources de tests périmées et reste exclu des preuves.

Clôture fraîche Task 8 au commit `e470bcee8`, le 11 septembre 2026 : les quatre compilations principales forcées sont vertes de 17:52:43 à 17:53:42 UTC. La régression publique complète forcée est verte de 17:53:51 à 17:56:12 UTC avec 151 méthodes sélectionnées, 149 réussies, 2 skips AA4 authentiques et 0 failure/error. GREEN45 rejoué seul est vert de 17:56:31 à 17:58:19 UTC : une méthode, 45 cellules, aucun failure/error/skip.

Les reviews Task 8 ont fermé sept findings Important : copies destination bornées avec origine non nulle et version `DestinationVersionI64`; branches `COLOR_DODGE`/`COLOR_BURN` sans division singulière évaluée avidement; matérialisation ordonnée de plusieurs runs Vertices/Mesh; décision de clear après culling; indexation linéaire des ressources; cache de pipeline Vertices local à la frame, à ownership unique et clé typée indépendante des valeurs d'uniformes. Les deux re-reviews Sol sont `READY`, sans finding Critical/Important restant.

Deux skips AA4 authentiques dans cette sélection : `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output` avec `w4d.general.texture-sample-support-unavailable`, et `W4e public Path AA4 uses only binary fixtures after its exact native capability boundary` avec `w4e.clip.sample-count-unavailable`. Le troisième skip de la vérification historique W5a ci-dessous n'est pas inclus dans la sélection W5b; aucune réussite ni capability AA4 n'est simulée.

`WgslFloatEnvelopeV1` accepte seulement un singleton ou deux codes RGBA8 adjacents, calculés analytiquement avec destination corrélée. Les fixtures W5a arbitraires 17/18 et 9/16 avec alpha Paint `253/255`, ainsi que les contre-exemples W5b dont les intervalles se chevauchent ou dépassent cette borne, restent `Unbounded` et ne sont pas des gates. Aucun seuil empirique ni garantie universelle sur tous les backends n'en découle.

La preuve de budget utilise un input W5b valide de deux Rects puis `resource-limit.w5b.destination-budget` à 1150 bytes et des pixels de récupération. Le display list public est append-only et `Surface.config` immuable : la récupération utilise des Surfaces distinctes sur le même runtime/backend ininterrompu, puis rejoue la Surface valide. La configuration prepared n'expose ni remplacement de capabilities ni budget agrégé injectables. Ces branches typées, les limites I64, la comptabilité physique pré-allocation et la libération/quarantaine native sont inspectées statiquement; ni device loss ni allocation failure ne sont prouvés par injection. Les ABIs admis utilisent uniforms, textures échantillonnées et samplers; aucun storage buffer inutilisé n'est exigé.

Le warning natif préexistant `Context leak detected, CoreAnalytics returned false` est toujours émis sans failure/error, avec les warnings JVM native-access/Unsafe. Les modules font peuvent se compiler transitivement; aucune suite font/codec/GM/dashboard/render/baseline/Skia/`jpg-color-cube` n'est exécutée. Le target `:kanvas` reste JVM, sans tâche JS/Node authentique. Le gap legacy Rect-gradient + RRect hard-edge `uniform slab` reste reporté. W5c vient ensuite; W5d matrices/tile, W5e images, W5f filters, W5g blend-children/noise et W5h runtime effects/H restent ouverts.

Minors explicitement différés : le seuil `1e-10` de `SetSat` reste partagé par l'implémentation et l'oracle et devra être réévalué avant l'expansion des sources; `GeneralPathDraw.withBlend` conserve un cast de l'autorité material legacy; le test public budget/recovery Task 6 vérifie aussi les pixels du primer avant le checkpoint refusal/recovery prévu par le brief. Aucun de ces points ne bloque les gates publics W5b actuels.

## Historique W5a — référence antérieure à W5b

Révision de production initiale : `7dbaf8cdf672e836f6ec6d77b1734cb68b6669db` (« admit W5a frame materials after geometry validation »), continuation du correctif global `39ff21985bd1407958d3ba1e909a74bbedf50010` sur `e0b1f39ce23a8badbd10074eb308908a26725280`. La vérification de cette vague couvre aussi les commits de recovery `64e6429c`, `582606d7`, `cbd8ab5e`, `33c54c09`, `9891e117` et `9aa924e5c`. Les cinq findings Important, les minors et les résidus d'admission/noms publics des scoped re-reviews sont traités dans cette même vague. Les deux re-reviews globales Task 8 sont désormais `READY`; W5a est close pour son périmètre, sans élargir les gates aux suites hors périmètre.

### Gates publiques W5a

W5a implémente `Transparent`, `Solid` et `Opacity` sous `SRC_OVER`. Chaque draw promu porte une `MaterialV1` vers une table immuable. La frontière W3 différée a été vérifiée en production : la capability historique exige table `null` et uniquement `LegacyColorV1`; `W5A_CAPABILITY_ID` exige une table présente et uniquement `MaterialV1`. Les formes hybrides sont refusées.

| Cellule publique | Preuve retenue |
| --- | --- |
| Rect hard-edge / fractional | fixtures nested-opacity et fractional Rect (Tasks 1–2) |
| RRect analytique | fixture fractional RRect et observation d'une couverture exacte 0.75 dans les frames mixtes |
| Path fill direct / stencil cover | fixtures Path (Task 3), plus chacune des deux variantes de frame mixte |
| Path stroke / hairline | fixtures publiques stroke et hairline (Task 3) |
| Point / Points | trois commandes, points multiples et hairline (Task 4) |
| Text pré-résolu | fixtures A8 et mutation d'une liste de glyphs déjà résolus (Task 5), sans génération de font |
| Vertices, avec/sans couleurs vertex | fixtures Picture et mutation des tableaux publics (Task 6) |
| Prepared frames mixtes | Rect → Point → Rect, Rect → A8 Text → Rect, Rect → Vertices → Rect et public Mesh sans programme : trois sources/opacités distinctes, ordre observé par l'oracle et mutation des collections publiques après capture |
| RRect + stroke/hairline | RRect → Path stroke ou hairline → RRect, géométrie W4d native, mutation du Path après Picture et pixels de composition |
| Bornes publiques | 512 runs natifs rendus avant/après le refus précoce de 513 runs sur une autre Surface du même runtime/backend; 683 matériaux point identiques dédupliqués, 683 chaînes distinctes de trois entrées refusées à la vraie borne 2048 |
| Admission avant interning | 683 Rect hors cible à chaînes distinctes sont élidées avant la table commune et seul le Point valide rend; Rect non finie + Point conserve `unsupported.core_primitive.geometry.non_finite_transform`; 683 candidats Vertices non finis gardent `unsupported.vertices.transform` |
| Frame Rect + RRect + Path | `Picture playback composes planned Rect RRect and Path bindings in recorded order` : trois bindings distincts, composition indépendante, observation AA, puis comparaison de tous les bytes après mutation du Path |
| Ordre intercalé et stencil | `native mixed stencil frame preserves interleaved Rect bindings and captured mutation` : Rect → RRect → Path stencil-cover → Rect, réutilisation d'un binding et comparaison de tous les bytes après mutation |
| Opacité identique, children distincts | `native mixed equal opacity preserves distinct Solid children and nested chains` : Rect rouge et RRect bleu à opacité 0.5, puis Path rouge et Rect bleu avec chaînes shader/Paint imbriquées et children réutilisés non adjacents; quatre observations pixel indépendantes |
| Refus puis récupération du runtime/backend | `public W5b gradient refusal leaves the runtime able to render a later W5a frame` : W5a valide → gradient refusé `unsupported.material.w5a.kind` → W5a valide, sur trois instances Surface partageant le même runtime/backend sans dispose intermédiaire, et pixels avant/après identiques |
| Absence d'ownership W5a | `hard edge gradient RRect outside W5a retains legacy pixels after caller stop mutation` : RRect hard-edge hors admission W4b, gradient rouge/bleu capturé en Picture, mutation des stops vers vert, pixels legacy rouge/bleu conservés |

### Composition native Task 7 W5a

La capability distincte `w5a-native-rect-rrect-path-composite-v1` est sélectionnée après les capabilities standalone existantes. Elle partitionne les commandes en runs natifs ordonnés, conserve leurs indices publics et confie Rect à W3, RRect analytique à W4b, Path fill à W4c et stroke/hairline à W4d. Le choix Path suit aussi les sémantiques de paint et l'admission native, pas la seule classe de géométrie. W4c n'accepte plus de conversion Rect/RRect en Path. La borne de 512 runs est vérifiée immédiatement après l'inventaire linéaire et avant toute `SceneSnapshot` par lane, y compris si une source material ultérieure serait refusée.

`W5aCompositePlanV1` possède les graphs de lanes, la table material internée et les réservations communes. Les refs locales sont remappées exactement dans la table de frame; la copie des draws conserve les faits de géométrie/raster sans reconstruction sémantique. Le graph composite utilise cette représentation hiérarchique typée, sans fabriquer une topologie standalone.

Correction d'interning : la clé d'une entrée inclut son child canonique et donc toute sa chaîne de bindings accessible, pas seulement sa structure et son alpha local. Puisque V1 désigne le child à `ref - 1`, toute nouvelle chaîne dont le child réutilisé n'est pas adjacent est copiée contiguë avant son parent. Les remaps canoniques restent déterministes, les bindings copiés défensivement et la borne de 2048 entrées contrôlée avant chaque ajout. La preuve RED Task 7 a produit du rouge dans le pixel bleu (`channel=0 observed=188 expected=[0]`); elle reste verte avec les chaînes imbriquées et le nouveau fragment.

`W5aPreparedFrameMaterialRegistry` remplace le bridge historiquement nommé CorePoint. Ses candidats Core locaux capturent uniquement le paint, sans acquisition de la table de frame ni admission géométrique implicite. Le mapper/lowerer existant, le recorder, la collecte des sémantiques et le preflight prepared vertices effectuent l'admission réelle : élision hors cible, transform, tessellation, limites et autres refus précèdent l'interning. Tous les `geometryRefusal` du lowerer sont propagés avant le recorder, sans filtre ad hoc; une Rect non finie conserve ainsi son diagnostic géométrique exact au lieu de devenir un refus de capture material ou de contrat de frame.

Seuls les payloads réellement admis Rect/RRect/Path/Points/A8 Text/Vertices et Mesh sans programme rejoignent ensuite la table commune. Text et Vertices sont abaissés une fois; leurs payloads sont remappés uniquement côté material, en authentifiant l'identité exacte du source-stage. Le witness Core vérifie également l'identité de la source locale et de sa ref globale. Aucune géométrie, tessellation, préparation d'atlas, inventaire ou artifact n'est rejoué. Les glyphs couleur/non-A8 et les géométries refusées n'acquièrent pas d'ownership W5a.

Correction de l'ownership des refus : les compilers natifs conservent séparément leurs refus de material et continuent les contrôles de géométrie, couverture, clip, état, provenance et limites sémantiques de toute la scène. Seule leur réussite complète permet d'émettre `GpuPlanSelection.MaterialOnlyRefusal`, lié à la scène, à la cible et à la capability. Les seams W4e et composite propagent ce résultat après leur propre admission; le backend vérifie l'identité scène/cible. Un `GapNotMigrated` ordinaire reste legacy : le routeur ne rescane plus les shaders et ne transforme plus des diagnostics accumulés en ownership. La nouvelle preuve RED échouait sur le faux terminal `unsupported.material.w5a.kind`; elle est GREEN sans modifier la géométrie ni substituer un material.

Le lowerer réutilise chaque lowerer et assembler natif avec un witness composite explicite. Il conserve les enveloppes d'origine pour le preflight exact, puis transporte seulement les ranges scellés vers les indices de la frame. Un witness de frame vérifie cible, readback, préparations, ordre, packets, états raster, dépendances et budget. La frame prépare une seule cible et un seul staging, efface au premier rendu puis charge l'attachement, et conserve les paires stencil atomiques.

Toutes les capacités V/I/U arrondies, y compris le scratch Rect W3 de cette composition, et les leases D24S8 sont comptées avant l'allocation. Un pool de session dédié aux lanes composites utilise la factory native existante avec la borne de 512 lanes; le pool standalone à trois slots reste inchangé. Le materializer réutilise les implémentations natives W3/W4b/W4c/W4d, partage un seul buffer readback, rassemble leurs leases sous un lifecycle commun et retient les journals de rollback si le nettoyage doit être retenté.

Les premières preuves RED ont révélé les anciennes exigences « toute la frame appartient à une seule lane », puis la limite des trois slots et la priorité de sélection devant W4b à 512 draws. Les corrections ajoutent une autorité composite et une gestion propre des ressources; elles ne relâchent pas les enveloppes standalone.

### Audit des compilations alternatives Solid/Opacity W5a

Le renderer génère maintenant le source-stage WGSL directement depuis chaque DAG numérique scellé, dans l'ordre des dépendances : sRGB→linear, prémultiplication et Opacity opèrent sur les bindings bruts en F32. `W5aMaterialPlanEvaluator` a été supprimé. Les anciens slots couleur de géométrie sont neutres, pas une seconde autorité. La queue du DAG est authentifiée à la couverture existante, au blend prémultiplié `SRC_OVER` et à l'attachement sRGB/clamp/UNORM8.

La partition native V2 conserve intégralement le group 0 de chaque lane et ajoute un group 1, binding 0, uniform non dynamique, dans les seuls fragments color-writing. Stencil/mask producers ne reçoivent ni source ni binding material; W4e inverse conserve son préfixe stencil atomique. Les pipelines composés conservent géométrie, constantes, entrypoints, raster, attachment et couverture. Text/Vertices utilisent ce même générateur dans leur composition material authentique, sans overlay core supplémentaire. Aucun compute prépass n'est introduit.

Le seal géométrique historique reste inchangé; une partition material V2 dérivée des packets immuables est ajoutée au budget agrégé avant toute allocation. Les raw buffers identiques sont dédupliqués et vivent jusqu'à completion; layouts/pipelines/bindings et buffers sont dans le journal de rollback commun. Coût exact des fixtures 512 : W4a `164100 + 16 = 164116` bytes (un Solid), W4b `164100 + 2×16 = 164132` bytes (deux Solids). Aucun slack arbitraire et aucun coût masqué; un caller ayant choisi exactement l'ancien budget doit compter ce nouveau matériel.

| Site de production | Décision W5a |
| --- | --- |
| compilers W3/W4a/W4b/W4c/W4d/W4e | `EffectiveMaterialPlanner` produit table/ref sur W5a; l'adaptateur historique conserve seulement `LegacyColorV1` |
| lowerers natifs + source V2 | transport de table/ref, génération du fragment depuis le DAG, bindings bruts; aucune évaluation CPU du résultat |
| composite Task 7 | interning structure + bindings + chaîne child canonique et remap exact, adjacency V1 conservée; aucune nouvelle évaluation du shader ni conversion de géométrie |
| bridges core/text/vertices | interning frame-wide après admissions, remaps d'émissions scellées et `compileW5a*`; les lanes non promues conservent leur entrée générique |
| `GPUMaterialMapper` | Opacity legacy reste un refus `OPACITY_CHILD`; aucun aplatissement Solid après sélection W5a |
| images, glyphs couleur, MeshProgram et dispatchs legacy restants | hors promotion W5a; `DrawMesh` sans programme utilise réellement la route publique vertices et dispose d'une preuve de mutation dédiée |

Cet audit porte sur le code de production. Les assertions W5a ajoutées dans les tests compiler/lowerer, SceneArchiveCodec et DisplayOpSceneAdapter ont été retirées du diff complet de branche; seules des adaptations mécaniques de tests historiques restent, sans servir de preuve W5a et sans exécuter de suite codec/infrastructure. Les preuves conservées sont exclusivement publiques. Si CPU et GPU sont tous deux dans l'enveloppe, aucun RED black-box ne distingue honnêtement l'architecture : la review de production prouve alors DAG→fragment, les tests prouvent pixels/enveloppe/mutation.

L'audit exhaustif des déclarations publiques ajoutées depuis la base empilée inclut les nombres dans les collections, maps, tableaux et types nullables. Les résidus `MaterializedSolidV2.premultipliedRgbaF32` et `issue(refsByCommandIdI32, sourcePlansByCommandIdI32)` sont corrigés; les KDoc décrivent le slot géométrique neutre et le source-stage fragment. Les signatures historiques inchangées et les overrides imposés par Kotlin ne sont pas présentés comme de nouvelles APIs W5a.

### Enveloppe numérique des preuves publiques W5a

Le cas public Rect puis Point à source ARGB `(197, 211, 79, 41)`, opacité shader `0.5` et paint `173/255` a isolé le RED `channel=2 observed=18 expected=[17]`. L'audit de la disposition brute, du binding et de l'expression DAG n'a pas révélé de divergence : l'oracle appliquait à tort la précision du `pow` WGSL à l'attachement fixed-function. La fixture 17/18 a été retirée de la suite car sa borne portable élargie est `Unbounded`; elle reste un exemple documenté, non un gate assoupli.

L'oracle indépendant distingue désormais les deux étapes. La source reste bornée par les opérations F32/WGSL du DAG. La conversion d'attachement utilise la référence sRGB réelle, calculée par arithmétique décimale dirigée et racines rationnelles, puis les bornes officielles : erreur totale d'encodage RGB strictement inférieure à un code et erreur de décodage, mesurée après ré-encodage exact, au plus un demi-code ([Metal, §8.7.7, 4 juin 2026](https://developer.apple.com/metal/Metal-Shading-Language-Specification.pdf)). Cette enveloppe contient l'encodage D3D limité à `0.6` code; l'alpha linéaire conserve aussi la borne FLOAT→UNORM de `0.6` code ([D3D 11.3, §3.2.3.6–8](https://microsoft.github.io/DirectX-Specs/d3d/archive/D3D11_3_FunctionalSpec.htm)). Il ne s'agit pas d'une tolérance ajoutée à un arrondi préalable. Le bleu de la régression admet analytiquement les seuls codes adjacents `{17, 18}`.

La couverture multiplie la source dans le fragment avant les facteurs fixed-function `One`/`InvSrcAlpha`. D3D11.3 §17.5 autorise une précision target-format mais ne fixe ni lattice ni schedule : l'oracle ferme donc le blend sur la grille RGBA8 minimale autorisée et F32, avec facteurs, produits et destination corrélée, plutôt qu'une enveloppe F32 seule. Les formes ordinaires/FMA, les erreurs F32 dirigées sur tout l'intervalle et FTZ restent incluses. Aucune mesure empirique ni seuil de similarité n'entre dans cette dérivation.

La première propagation des bornes officielles a rendu 13 anciennes fixtures multi-draw `Unbounded`. Une recherche déterministe par endpoints et grille fixed-point a retenu seulement des témoins à fond primaire opaque, couche blanche avec opacité/alpha Paint non triviaux, puis primaire opaque : ils préservent l'ordre, la capture et les mutations, tout en satisfaisant la règle stricte du singleton ou de deux codes adjacents. Un contre-exemple public en ordre inversé reste rejeté dans la preuve à trois Points. La combinaison fractionnaire 9/16 avec deux opacités et alpha Paint `253/255` reste explicitement `Unbounded`; le témoin 9/16 retenu garde les deux opacités mais un alpha Paint exact. Les conversions fixed-function peuvent élargir au-delà de deux codes l'enveloppe d'une scène arbitraire; ces fixtures sélectionnées ne prouvent pas une borne universelle. Un tel résultat reste `Unbounded`, jamais un succès assoupli. Aucun pipeline destination-read n'a été ajouté.

### Vérification historique W5a

Vérification JVM du correctif global Task 8 et de sa continuation, fraîche et sérielle, sur la série `7dbaf8c` → `9aa924e5c` :

```bash
rtk proxy ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --max-workers=1
rtk proxy ./gradlew :kanvas:test --tests '*W5aMaterialSurfacePixelTest' --tests '*GPUPlanSurfacePixelTest' --no-parallel --max-workers=1
rtk git diff --check
```

Résultat : les deux commandes Gradle sont `BUILD SUCCESSFUL`; 119 tests publics, 116 passés, 0 failure/error, 3 skips AA4 authentiques. Répartition : W5a 48 tests (47 passés, 1 skip); GPUPlan 71 tests (69 passés, 2 skips), avec les deux fixtures 512, W4e inverse, toutes les frames mixtes et les régressions admission/numérique. Les compteurs ci-dessus sont la source de vérification; les timestamps XML ne sont pas une source documentaire. `rtk git diff --check` est propre.

Skips exacts : `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output` (`w4d.general.texture-sample-support-unavailable`), `W4e public Path AA4 uses only binary fixtures after its exact native capability boundary` et `W4e public mixed hard and Path AA4 inverse consumers keep distinct D24S8 domains` (tous deux `w4e.clip.sample-count-unavailable`).

La commande planifiée `:kanvas:jsNodeTest` est absente : `:kanvas` applique `buildsrc.convention.kotlin-jvm` et l'inventaire Gradle ne publie aucune tâche JS/Node. Aucun substitut de test d'infrastructure n'a été exécuté.

### Limites de la clôture W5a

- Les trois skips AA4 restent attachés à l'indisponibilité native documentée; aucune capability ni réussite AA4 n'est simulée.
- SolidColor, Opacity et Paint sont immuables. La mutation publique observable porte sur Path, tableaux vertices et listes glyphs après capture.
- Les preuves numériques multi-draw concernent les entrées dont l'enveloppe indépendante officielle reste bornée à deux codes adjacents; les autres entrées restent explicitement `Unbounded`, sans affaiblissement de l'assertion ni prétention de conformance exhaustive de tous les backends.
- Les dépendances font se compilent transitivement, mais aucune suite font/codec/GM/dashboard/baseline/Skia/`jpg-color-cube` n'a été exécutée. Les fixtures text utilisent seulement des glyphs déjà résolus.
- Aucun test d'infrastructure n'a servi de preuve. Les deux re-reviews globales Sol indépendantes de Task 8 sont `READY`; la validation reste fondée sur la revue de production et les pixels publics autorisés.
- Le run public final émet aussi `Context leak detected, CoreAnalytics returned false`, sans failure/error ni correspondance dans les sources du repository; warning natif non attribué à un défaut du correctif, conservé explicitement dans le rapport plutôt que présenté comme absent.
- Une variante exploratoire non retenue, gradient Rect suivi de gradient RRect hard-edge, atteint legacy mais y rencontre `invalid.preflight.core_primitive_direct_geometry_resources` (uniform slab). Ce refus de ressources legacy distinct, suivi comme gap non bloquant, reste hors de ce correctif; la preuve retenue concerne la RRect seule demandée.
- Cette clôture W5a précédait W5b; le périmètre actuel W5b et la suite W5c sont décrits en tête de document.
