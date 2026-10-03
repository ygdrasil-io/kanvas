# W7 — cause du gap AA diagonal

3 octobre 2026, branche codex/w7-aa-coverage-diagnostic, parent #2437/d12749b64.
[Design](diagonal-aa-diagnostic-design.md), [plan](diagonal-aa-diagnostic-plan.md).
Lot de diagnostic : **aucun changement produit ni gain corpus revendiqué**.

## Conclusion bornée

Le contour math et la tiny CTM ne causent pas le gap du témoin étudié.
Ligne source tiny, ligne en coordonnées écran et contour FILL littéral
produisent la même rampe native à y60 dans les deux domaines. Leurs pixels
intérieurs et extérieurs sont vérifiés indépendamment, pas seulement comparés
entre eux. Les repeats gardent tous les bytes et les scopes natifs.

La couverture observée sur transparent est64 puis255 aux cellules x106/107.
Ces valeurs valent dans LINEAR et SRGB_ENCODED. Modifier le domaine change
le RGB sur blanc, pas cette couverture :225/0 en LINEAR,191/0 en encoded.
La référence normalisée du vrai GM contient223 puis31/32 aux mêmes positions,
selon la qualification parent ; elle n'a pas été décodée/régénérée à nouveau.

| y60, première diagonale noire | x106 | x107 |
| --- | --- | --- |
| Alpha natif transparent, tous les contrôles | 64 | 255 |
| Aire idéale×255, ligne largeur5 | ≈36.56656 | ≈227.49458 |
| RGB natif sur blanc LINEAR | 225 | 0 |
| RGB natif sur blanc encoded | 191 | 0 |
| Référence sRGB normalisée, reçu parent | 223 | 31/32 |

L'aire idéale est le demi-triangle de côté2.5√2−3, puis le complément du
demi-triangle de côté4−2.5√2. Il ne s'agit que de deux cellules calculées,
pas d'un CPU renderer ni d'un oracle d'image. Les cinq échelles F32 donnent
une largeur écran4.999999655..5.000000055 ; la différence d'aire induite reste
inférieure à0.00000015, trop petite pour expliquer environ28 codes alpha.
L'aire idéale n'est pas exactement la référence Skia : backend et approximations
de sa génération restent inconnus. Ne pas promettre du bit-exact avec ce modèle.

## Frontière causale

La route encoded est promue, sans continuation legacy après Ready :
GPUPlanSurfaceRouter→ProductionGPUPlanSurfacePort→GpuRenderContext→compiler
chain→W4d general. W4dGeneralPathPlanCompiler:478 prépare le contour via
matrixF64.preparePathStrokeGeometryF32 dans math ; le graph lowerer consomme
son snapshot F32. L'audit initial GPUStroke legacy a été corrigé, il n'est
pas une preuve de la route actuelle.

aaGraph produit StencilAA4/Multisample4, stencil producer+color cover, puis
resolve final. Les clés/facts préparés portent le sample count ; aucune
contribution d'aire analytique de bord n'a été identifiée sur cette route.
Les niveaux natifs observés sont cohérents avec ce sampling ; les identités
natives d'attachments et les positions exactes de chaque sample ne sont pas
exposées. La conclusion causale porte sur ce témoin, pas sur tous les paths/GPU.

Source primaire Skia épinglée defc3a5a92966c32cb2a6a901e2fa3036a13bb8a :
[TeenyStrokesGM](https://github.com/google/skia/blob/defc3a5a92966c32cb2a6a901e2fa3036a13bb8a/gm/strokes.cpp)
concorde avec le port ;
[SkScan_AAAPath](https://github.com/google/skia/blob/defc3a5a92966c32cb2a6a901e2fa3036a13bb8a/src/core/SkScan_AAAPath.cpp)
décrit une intégration analytique de trapèzes avec approximations fixed-point.
Cela atteste une méthode upstream, pas le backend utilisé pour nos PNG.

## Qualification fraîche

- Math ciblé :1PASS, BUILD16s, exit0, aucun timeout/skip, XML complet.
- Suite complète math:matrix:jvmTest :311PASS,13XML, BUILD9s, exit0 sans
  timeout/skip/error ; toutes les identités des events et XML concordent.
- Surface native :2PASS, BUILD16s, exit0, aucun timeout/skip, XML complet.
  Dix scènes observées, chacune repeatée :20rendus natifs ;1dispatch pour les
  lignes/contour,2avec vrai Rect blanc ;0refus, Render+Readback partout.
  Dix rampes RGBA complètes, counters et scopes archivés dans stdout XML.
- Géométrie : quatre sommets littéraux vérifiés à3e-5 pixel pour cinq échelles
  et contrôle écran. Aucun attendu de pixels ajusté après runtime.
- Test math SHA2568d8fb134e3ccb6d0c15dad656e5150774cff9f15f75773e2e5b401b3206eef46.
- Test natif SHA2567d2e3a0d2c5a6fca89cd024efa85ec0bf6afb718f8e0b8f50664a3d7bcb7de98.

Raw receipts privés : /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/
diagonal-aa-math-1, diagonal-aa-native-1 et diagonal-aa-matrix-full-1. Watchdog240s inchangé, un runtime
à la fois ; full log/exit/events/XML/stdout lus dans un audit séparé après le
terminal original. Warnings hérités JDK/Gradle/LWJGL présents, pas output pristine.
Les assertions manquantes RGB+alpha et intérieur noir ont été complétées avant
le premier run natif ; aucun patch produit ni RED/GREEN fictif.

Produit, références, scores, registre631/scope443, budgets et sampling restent
byte-identiques au parent. Le diagnostic inverse-filter untracked SHA96cd8349
reste intact/exclu. Les suites parent globales RED/incomplètes restent suivies
dans la qualification #2437 ; aucune réussite globale ou W7 terminé déduits ici.

## Prochain changement à choisir

Ne pas multiplier les réglages MSAA/CTM pour reproduire un PNG. Le candidat
architectural est une couverture continue GPU pour une famille géométrique
générique certifiée dans math, en conservant le MSAA pour les familles non
encore couvertes. La composition reste une dimension séparée, explicitement
mesurée : une aire correcte composée en LINEAR ne devient pas automatiquement
une image Skia encoded correcte. Une revue stratégique Astra doit fixer la
plus petite étape produit utile et ses critères d'arrêt avant implémentation.
