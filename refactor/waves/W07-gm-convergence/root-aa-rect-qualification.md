# W7 — qualification des Rect hard avec root Path AA encoded

3 octobre 2026. Branche `codex/w7-root-aa-rect-admission`, parent draft
[#2436](https://github.com/ygdrasil-io/kanvas/pull/2436), base publiée
53bf9c55b8950c36eb14a40eb44626cb02d6020c.
[Design](root-aa-rect-design.md), [plan amendé](root-aa-rect-plan.md),
[corpus frais complet](root-aa-rect-8e44f0c8a.json).
Source/tests gelés et qualifiés au snapshot privé
8e44f0c8ad1ac65d97275d010ed083e1e44bbfcd ; BASE de Task2 ad8ebfde1,
pas HEAD~1. Les six blobs modifiés et H sont indépendamment byte-identiques
à ce candidat après toutes les invocations. Reviews Sol/Astra terminées ;
publication draft après vérification finale des blobs/base/head/body.

## Capacité locale, pas gain de parité

Admission commune whole-scene dans CompositionAdmissionV1, consommée par W4d :
root SRGB_ENCODED avec au moins un vrai Path AA admis. Un sibling (dessin voisin)
RECT/Geometry.Rect peut être FILL hard non-AA, solide direct SrcOver sans
effet, bounds exactes finies nonvides I32 et CTM identité/translation entière I32,
clip hard integer Rect/absent. Le contrat Path et son SetTransform axis-aligned
restent distincts et inchangés. La factory standalone possède explicitement
la projection Rect ; le constructeur Path-only ne l'emprunte pas.

MSAA4 corrélé conservé jusqu'au resolve final, source/origine/CTM inchangés.
Pas de nouvelle lane, factory, config ou format ; facts préparés/physical keys/
preflight et sources privées W4e/W6 inchangés. Pas de budget/floor/cap/sampling
ou enveloppe oracle modifiés. Autorité numérique exacte dans math:geometry,
RectProjectionF32 : coordinateF32ToExactI32OrNull et toExactRectI32OrNull,
finite→I64 dans I32→roundtrip F32, puis isEmpty64. Grands spans avec bords I32
valides conservés ; ancien W4d.integral délègue sans changement de sémantique.

Ancien cas entier noir Path puis Rect positif conservé exactement. Seule
fixture historique changée : hard-draw-rect devient fractional-hard-draw-rect
[0.5,0,12,12]. Rect-gradient/Rect-SRC restent des refus geometry ; exclusions
et transaction readPixels/discard/recovery restent exercées.

## RED causal, amendements et GREEN ciblé

Task1 ajoute uniquement de vrais témoins Surface/Picture et le vrai GM H.
Les contrôles LINEAR passent avant source ; les scènes encoded échouent à
l'admission geometry avant pixels. Sol Task1 puis contre-review fix1 ferment
les trois Important des témoins (C exact, RGB de H, clip avant save/CTM).
Task2 initial : 21/24PASS, trois échecs conservés : diagnostic Rect-SRC,
wrapper Picture opaque, budget27136 insuffisant. Pas d'attendus pixels ajustés.

Amendements après audit statique et avis ciblé Astra :

- C utilise le vrai Picture.playback memory/archive, tous144 pixels indépendants
  et ordre C inchangés. Les DEUX wrappers drawPicture restent des refus
  transactionnels explicitement testés, avec sentinelle et recovery direct C.
  Support wrapper/nested/painted/état extérieur/continuité autour du wrapper OPEN.
- G27392/B−127391 corrige le stencil hard256 omis : target256 + pools24576 +
  colorAA4 1024 + depthAA4 1024 + hardmask256 + harddepth256. Dérivation du graphe,
  pas recherche du budget passant sur GPU ni hausse de policy/allocation/lifetime.
- API exacte math écrite après RED missing-API : 41 diagnostics de compilation,
  zéro testcase, puis focused59PASS (30RectF32+21RectI32+8nouveaux).

Le run combiné25 finit tous les cas PASS/XML complets mais a wrapper124,
timed_out=true/enfant0, BUILD SUCCESSFUL3m59s : race de finalisation compatible
avec les faits, pas un exit propre. Reçu conservé ; mêmes sources/watchdog240s
requalifiés séparément : **mixed10PASS + parent15PASS, exits0 sans timeout/skip**.
Pixels indépendants, corrélation C, alpha translucide D, RGBA/BGRA, domaines
alternés, clip/CTM, vrais replays, budget B/B−1 et exclusions sont couverts.

## Mesure réelle teenyStrokes : domaine ≠ couverture

H final2PASS, exit0 sans timeout/skip, vrai runner fond Rect puis vrai GM,
deux domaines explicitement sélectionnés sur Surface. Référence inchangée
SHA25678cbf8bfe9b44e74f282311f517f86daa20ff0512f5770b6bc79819544a47597.

| Mesure native 400×800 | LINEAR | SRGB_ENCODED |
| --- | --- | --- |
| Pixel match à tolérance2 | 99.5228125% | 99.529375% |
| Pixel match exact | 99.08312500000001% | 99.3028125% |
| SSIM luminance | 0.9992640729353328 | 0.9997374465216886 |
| RGBA SHA256 | 33456023a5161000f9f8eb1d2006bf72288090ffae945519751bb32182e9a073 | 5c31d981844281820921b785fd3924759d354d2cda8e07e4a7f873140bce953b |

LINEAR exactement égal parent ; deux domaines11dispatch/0refus,
23Render+1Readback, repeat stable. Fullstdout et deux PNG finals byte-identiques
au diagnostic précédent ; toutes rampes conservées privées. Bord vertical188→128
avec référence128 ; diagonale225→191 avec référence223, et0 contre31/32.
Le gain à±2 n'est que0.0065625 point. Ni correction de couverture ni parité
universelle démontrée. Le vrai GM déclaré reste LINEAR, score93.2/tolérance2
inchangés ; aucun gain corpus ne découle de la probe encoded.

## Suites et dette de vérification explicites

| Invocation fraîche | Résultat |
| --- | --- |
| mixed10 / parent15 | 10PASS /15PASS, XML complets, exits0 |
| historique root/composition/layer | 49END=48PASS/1PictureFAIL, exit1 |
| H final | 2PASS, XML complet, exit0 |
| math:geometry:jvmTest sans filtre | 498START/END/PASS,41XML, exit0 |
| kanvas:test sans filtre,240s | 724START/END=686PASS/37FAIL/1interruption ; wrapper124/enfant143, pas XML final |
| gpu-plan:test sans filtre | 30diagnostics compileTestKotlin,0testcase, exit1 |
| gpu-renderer:test sans filtre | 17diagnostics compileTestKotlin,0testcase, exit1 |

Les49 résultats/identités/exceptions historiques sont exacts parent ; Picture
attend unsupported.composite.paint, reçoit w6a.layer.unsupported_child.
Les724 identités/statuts globaux atteints égalent parent ;32exceptions raw
égales,5seulement adresses RuntimeEffect/Diagnostics,37stacks égaux. Test
W5eDecodedImageSurfacePixelTest.formatsAlphaAndColorSpaceMatchOracle interrompu.
Pas de fermeture de l'univers actuel ; les nouveaux W7 ne sont pas qualifiés
par cette globale interrompue. Les30+17 erreurs de compilation sont exactement
égales parent, pas47tests échoués. Aucune réparation/suppression de tests
d'infrastructure. Globale et unités plan/renderer restent RED/non qualifiées.

Warnings JDK/Gradle/LWJGL et huit imagePatchRefusal hérités conservés, pas output
pristine. Backend/driver et identités natives d'attachments non exposés :
CannotVerify, pas d'inférence Metal. Borne générale de précision du resolve
reste OPEN : témoins empiriques figés, pas enveloppe démontrée tous inputs/GPU.

## Corpus631/443, aucun delta hors temps

Tranches0..607 /607..608 /608..631 : exits0/1/0, BUILD137s/40s/14s,
aucun timeout externe. vertices607 garde timeout render30s/Java124, sans
completion marker, compté eligible ; deux autres tranches complètes.
Registre631, scope443eligible/133font/54codec/1quarantaine inchangés.
**217rendus/194comparés,47≥99%,63≥95%,médiane77.45815728081598%**.
Outcomes194compared/175render_failed/50setup_failed/15rendered_uncompared/
8reference_dimension_mismatch/1timeout inchangés.

Toutes631fiches et présence de leurs champs sont égales au parent hors ONLY
elapsedMs/renderMs :18invariants par cas, diagnostics/outcomes/métriques et
217RGBA hashes inchangés ;7invariants de run inchangés. Date et rendererCommit
reflètent l'exécution fraîche. Summary identique hors sumCaseElapsedMs
158013→161281 ; histogramme exact111clés/226counts, même convention eligible
!rendered, pas de delta caché d'agrégation. Tous381PNG privés byte-identiques
parent ; aucune régénération PNG/scores/dashboard du dépôt justifiée.
Snapshot750007bytes/SHA256a698ebfe0ec98f48cbf5d369922d4edd42ec7746af1879cb3101ca225fdeeb32.

## Custody et méthode d'audit

Raw invocations sous /private/tmp/kanvas-w7-inverse-inventory.hbWqUb,
préfixe root-aa-rect-task2-* ; corpus root-aa-rect-corpus.8oiiSC, journaux
slice-*.jsonl. Commande commune : rtk proxy ruby bounded-run.rb ARCHIVE240
./gradlew --offline --no-daemon, evidenceinit/validationDir pour les Test tasks,
--rerun et sélections nommées dans les reçus privés du workspace du plan.
Corpus measureSkiaParity utilise from/to/timeout30/rendererCommit8e44/imagestrue.
Un seul runtime : original terminal, NEXT audit séparé complet avant suivant/edit.
Logs/XML/full exceptions/stdout/err/exit et hashes d'artefacts comptabilisés.
Sorties trop larges tronquées remplacées par lectures couvrantes ; aucun rerun
pour pallier une sortie tronquée. Globale : diff lossless du log contre parent
déjà entièrement lu, totalité des différences et cinq exceptions fraîches lues.
Stdout MSAA/H byte-equal aux archives déjà entièrement lues, equality indépendante.
XML math : seule décoration terminale [jvm] enlevée pour comparer les identités.
Tous607+23PARITY stdout sont comparés aux journaux, métriques et temps inclus.
Rulings et coûts conservés dans le ledger, aucune custody/workspace supprimée.
Diagnostic inverse-filter untracked SHA96cd8349 inchangé/exclu du staging.

## Reviews et suite du pilotage

Sol Task2 : spec conforme et quality Approved, C0/I0/M1 warnings hérités différés.
CannotVerify de portée disposés par le contrôleur : chronologie Task1 et hashes
de custody vérifiés, source/tests publiables byte-identiques au candidat natif,
contrats hors hunks inchangés et témoins parent15/mixed10/H couvrant les facts
au niveau des fixtures. Identités natives non exposées et enveloppe générale
du resolve restent OPEN, pas des faits déduits de ces passes.

Astra whole-branch finale53bf9c55b..d832a3365 : **C0/I0/M2, draft acceptable,
Ready to merge: No**. Admission/source→graph→prepared authority→physical keys→
continuation/preflight→executor statiquement cohérents pour ce delta ; Rect hard
masque/depth1 et couleurAA4 coexistent, resolve final unique, vues/roles/générations
validées, format target-authentifié, W4e/W6 privés inchangés. Ce contrôle dispose
le CannotVerify statique Task2, pas l'identité empirique des attachments ni la
précision native universelle. Tous les hunks humains lus ; JSON généré audité
exhaustivement : hunk byte-equal fichier/SHAa698,18812 comparaisons full-field
avec présence des clés,11358 invariants par cas et21 de run, zéro delta hors
temps ; summary recalculé exactement, y compris histogramme111/226.

M1 warnings hérités différés. M2 textes « only Path draws » dans les diagnostics
CompositionAdmissionV1:85/92 et KDoc H « GM's domain »:25 imprécis ; différés
explicitement pour cette draft afin de préserver les blobs exactement qualifiés.
Aucun nouveau Critical/Important ; aucune correction produit demandée et aucune
nouvelle vague GPU lancée sans changement comportemental. Ces libellés ne sont
pas une description exacte de la nouvelle famille/du diagnostic à domaine choisi.

Dispositions des comportements laissés hors verdict par Astra :

- Resolve universel : OPEN, preuve empirique bornée aux fixtures seulement.
- Backend/driver/identités natives : CannotVerify empirique OPEN ; contrôles
  statiques de formats/samples/vues ne deviennent pas une observation native.
- drawPicture nested/painted/occurrences/MSAA autour du wrapper : OPEN,
  seuls refus transactionnels des deux wrappers qualifiés ici.
- playback général avec clip/CTM interne/extérieur : OPEN ; C exact uniquement.
- AA diagonale et domaine GM : OPEN,191≠223, aucune migration de GM déduite.
- Globale37FAIL/PictureFAIL/30+17compiler errors et univers au-delà de724 :
  dette OPEN, aucune suite globale réussie ni réparation dans ce lot.
- vertices après30s : UNKNOWN, timeout compté sans extrapolation.
- Autres chantiers W7 : inchangés/OPEN ; font/codec/jpg exclusions conservées.
- Cross-platform math/backend : non exécuté, pas de matrice native revendiquée.
- Chronologie/custody : reçus originaux/audits complets contrôleur conservés,
  hashes sept blobs et protégé96cd indépendamment revérifiés par Astra.
- Remote/CI : publication à vérifier par le contrôleur ; CI non inspectée.

PR draft empilée sur#2436 après ces gates, pas merge/W7clos/globaleGREEN.
Fonts, codecs externes et jpg-color-cube hors périmètre conservés.

Prochain chantier utile : couverture/placement AA à géométrie et domaine
explicitement contrôlés. Ne pas attribuer le191 diagonal à la seule couleur,
ni migrer un GM vers encoded en espérant corriger ses masques. Wrapper Picture,
ComplexClip2 RRect, inverse/filter, crbug résiduel, provenance/taille de références,
légère régression pathops et dette des suites héritées restent suivis séparément.
