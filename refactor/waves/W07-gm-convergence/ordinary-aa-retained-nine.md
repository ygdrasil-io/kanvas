# W7 — résultats des neuf GM ordinary-AA retenus

## Portée et conclusion

Cette mesure qualifie séparément l’admission ordinary-AA conservée après W6. Elle ne valide pas la fidélité globale du renderer. La cohorte fixe contient les indices `[34,64,210,213,233,317,338,470,472]`; les neuf étaient `render_failed` dans la baseline. Sur le HEAD mesuré `f62e195fcf729b26f4e7ebd3d3306ef8b8c8ad90`, trois ont été rendus puis comparés, et six ont encore échoué avant comparaison. Il n’y a donc ni résultat corpus, ni admission générale, ni achèvement de la tâche de fidélité d’origine.

| Index | GM | Baseline → W7 | Détail W7 |
| ---: | --- | --- | --- |
| 34 | `backdrop_hintrect_clipping` | `render_failed` → `render_failed` | Échec `w6a.layer.unsupported_child`, `drawIndexI32=2` (identique au premier index de diagnostic baseline). |
| 64 | `blurcircles2` | `render_failed` → `compared` | Baseline : `w6a.layer.unsupported_child`, `drawIndexI32=22`; 55 dispatchés, 0 refus en W7; 60.844850329781835 % à ±2. |
| 210 | `dropshadow_pseudopersp` | `render_failed` → `compared` | Baseline : `w6a.layer.unsupported_child`, `drawIndexI32=5`; 4 dispatchés, 0 refus en W7; 66.78876170655568 % à ±2. |
| 213 | `emboss` | `render_failed` → `render_failed` | `w6a.layer.unsupported_child`, premier index 1 → 5. La progression du diagnostic ne constitue pas un rendu complet. |
| 233 | `filterfastbounds` | `render_failed` → `render_failed` | Échec de budget image W6b : 1 141 545 688 octets requis, budget inchangé de 1 073 741 824. Diagnostic initial baseline à l’index 73; aucun rendu complet. |
| 317 | `imagefiltersbase` | `render_failed` → `render_failed` | `w6a.layer.unsupported_child`, premier index 32 → 36. Pas de résultat natif. |
| 338 | `inverse_windingmode_filters` | `render_failed` → `render_failed` | Baseline : `w6a.layer.unsupported_child`, `drawIndexI32=1`; W7 : `w4e.clip.capability-unavailable`, W5b requiert la topologie W4e single-sample admise. |
| 470 | `rotate_imagefilter` | `render_failed` → `render_failed` | `w6a.layer.unsupported_child`, premier index 3 → 7. Pas de résultat natif. |
| 472 | `rrect_blurs` | `render_failed` → `compared` | Baseline : `w6a.layer.unsupported_child`, `drawIndexI32=10`; 15 dispatchés, 0 refus en W7; 55.0775 % à ±2. |

Les trois nouveaux rendus sont des gains mesurés de disponibilité pour ces GM précis. Les six échecs restent des échecs. Un premier index de diagnostic plus tardif (ou un diagnostic différent) ne prouve pas que le GM entier a été rendu. `declaredContractPass=true` dans les reçus signifie que la condition déclarée du comparateur est satisfaite; cela ne signifie ni near-ISO, ni fidélité globale, ni validation de la scène corrigée comme équivalente.

## Configuration et identité contrôlées

La baseline physique est `root-aa-rect-8e44f0c8a.json`, renderer commit `8e44f0c8ad1ac65d97275d010ed083e1e44bbfcd`. Le registre compte 631 entrées, empreinte `4ca8eea61451b1143fd3d15634d2c34e0c9ec31b74fd351ca30a69ee36f565d7`, dont 443 éligibles; les exclusions demeurent 133 fonts, 54 codecs et `jpg-color-cube`. Le domaine, références physiques, dimensions, tolerance, `minSimilarity`, `requiresZeroRefusals`, scopes et seuils des neuf reçus sont ceux de la baseline. Chaque tranche déclare le même HEAD réel, le même hash de registre, `Mac OS X`/`aarch64`/Java `25.0.1`, timeout GM 30 s et plage `[i,i+1)`.

Pour chaque cas ci-dessous, le nom, provider, famille, dimensions, `referenceName`, empreinte SHA-256 de référence, tolérance, seuil, refus requis, domaine de composition et scope ont été lus dans le reçu `kind=gm` et confrontés à l’identité correspondante de la baseline. Tous sont `eligible`, `trusted`, `LINEAR`, `tolerance=2`, `requiresZeroRefusals=false`; les seuils `minSimilarity` sont conservés individuellement. Les hashes d’actualités ne sont présents que pour les trois rendus comparés.

| i | identité (provider; famille; dimensions) | Référence SHA-256; seuil minimum | Ops; dispatch/refus; outcome | Actual RGBA SHA-256; exact / ±2 / déclaré / SSIM luminance |
| ---: | --- | --- | --- | --- |
| 34 | `org.graphiks.kanvas.skia.gm.composite.BackdropHintrectClippingGm`; COMPOSITE; 512×1024 | `05a2025fce7223420d876b739276c6e6bcc7b650f568a078eac2dfc85bef2480`; 0.0 | 35; — / —; `render_failed` | — |
| 64 | `org.graphiks.kanvas.skia.gm.blur.BlurCircles2Gm`; BLUR; 730×1350 | `57680c49964fa6989acebf8526498cf799f9eaf07ad3d5c3cd8dfd7f87146964`; 49.2 | 109; 55 / 0; `compared` | `2c239fabc210472e7a6aa3942190800796ac9f9d6a81463184d4589cc3f10c92`; 48.97108066971081 / 60.844850329781835 / 60.844850329781835 / 0.913971350294999 |
| 210 | `org.graphiks.kanvas.skia.gm.composite.DropShadowPseudoPerspGm`; COMPOSITE; 155×155 | `f48ab07359115b038c05001126fd4ae10d92d30f39a50517359d867b7a82c523`; 0.0 | 6; 4 / 0; `compared` | `79a560812e56d90b4b674f1386e3ed2e63412db7319c0a5f300f28079233d436`; 66.7429760665973 / 66.78876170655568 / 66.78876170655568 / 0.9425840346735577 |
| 213 | `org.graphiks.kanvas.skia.gm.blur.EmbossGm`; BLUR; 600×120 | `c07a3de5652b2104068a265fa6ee28974502b56cc76814ac0bc3e4291deda51b`; 55.5 | 12; — / —; `render_failed` | — |
| 233 | `org.graphiks.kanvas.skia.gm.composite.FilterFastBoundsGm`; COMPOSITE; 900×700 | `c27a8d461603b75a21a13f14b4537d8a82fd1ffc103f26944246a62d089a6c8a`; 0.0 | 117; — / —; `render_failed` | — |
| 317 | `org.graphiks.kanvas.skia.gm.composite.ImageFiltersBaseGm`; COMPOSITE; 700×500 | `287a753e293645014079c1a3e32771f027fab1eed1558adf1da57c8fc32f8ec3`; 0.0 | 145; — / —; `render_failed` | — |
| 338 | `org.graphiks.kanvas.skia.gm.blur.InverseWindingmodeFiltersGm`; BLUR; 256×100 | `2fc568e3400295002cb85b50b4b3099d4a074015009066f0e200e96c16081d8d`; 0.0 | 22; — / —; `render_failed` | — |
| 470 | `org.graphiks.kanvas.skia.gm.composite.RotateImageFilterGm`; COMPOSITE; 500×500 | `3353a240b5949afdf94c346a59e2ffa7c346e832ed7f10b7729c8a5dbd129f6b`; 0.0 | 12; — / —; `render_failed` | — |
| 472 | `org.graphiks.kanvas.skia.gm.blur.RRectBlurGm`; BLUR; 300×400 | `3327fa6254d219f5a23c5bdcdab30f0f363da1834353ff246f7e0b5ce66beaaa`; 0.0 | 15; 15 / 0; `compared` | `8138738456382c12ac5f26cfa2b938d420f660d0a91148071ed85e7774b6eef2`; 52.21916666666667 / 55.0775 / 55.0775 / 0.6624135636987509 |

`—` signifie que le renderer n’a produit ni pixels comparables ni compte dispatch/refus; ces valeurs ne sont pas zéro. Dans les trois reçus comparés, les dimensions de référence correspondent aux dimensions du GM et `declaredContractPass=true`. Les propriétés `exactPixelMatch`, `pixelMatchTolerance2`, `pixelMatchDeclaredTolerance`, `ssimLuminance`, `meanAbsoluteChannelErrorNormalized` et `maxChannelDelta` restent celles des JSONL immuables; les valeurs de concordance principales sont données ci-dessus sans arrondi supplémentaire.

## Échecs et changement des premiers diagnostics

Les six cas non comparés n’ont pas d’`actualRgbaSha256`, de métrique pixels, ni de comparaison possible. Les reçus courants conservent les diagnostics complets; en bref :

- 34 : `w6a.layer.unsupported_child`, `drawIndexI32=2`, child PATH AA, sans payload de filtre.
- 213 : même diagnostic de classe, `drawIndexI32=5`, PATH AA et payload filtre présent (baseline 1).
- 233 : `w6b.filter.frame_budget_exceeded`; la demande dépasse le budget fixe (baseline premier diagnostic `drawIndexI32=73`).
- 317 : `w6a.layer.unsupported_child`, `drawIndexI32=36`, PATH AA et payload filtre présent (baseline 32).
- 338 : `w4e.clip.capability-unavailable`; le mélange final W5b demande la topologie W4e single-sample.
- 470 : `w6a.layer.unsupported_child`, `drawIndexI32=7`, PATH AA et payload filtre présent (baseline 3).

Les diagnostics `w6a.layer.unsupported_child` partagent le même sens détaillé dans la baseline et les reçus actuels : le segment de layer sort des voies admises pour la géométrie/source enfant. Ils précisent `scopeI32=root`, `origin=PATH`, `coverage=ANTIALIASED`, `pathFillRule=WINDING`, ainsi que `ownsW6b` et la présence du payload de filtre. La chaîne de raisons enfant mentionne `unsupported.prepared-vertices.frame`, `unsupported.w5b.point-frame-geometry`, `unsupported.material.image.slice`, puis les voies non migrées W3, W4a, W4b, W4c, W4d, W4d.2 et W4e; les messages spécifiques de chaque voie sont conservés sans modification dans les JSONL. Pour 338, la raison W7 n’est plus ce diagnostic enfant : l’admission de mélange final W5b est refusée parce que la topologie W4e single-sample requise est indisponible. Pour 233, la baseline échouait sur l’enfant au draw 73, alors que le reçu W7 rapporte le refus de budget exact indiqué ci-dessus. Aucune des deux évolutions n’est comptée comme rendu complet.

Les baseline et reçus actuels donnent toujours `render_failed` pour ces six. Les seuls changements de statut sont donc les trois nouveaux résultats `compared`. L’avancée des indices de diagnostic 213, 317 et 470 décrit un point d’arrêt ultérieur dans la scène, non la réussite du rendu complet. L’index de 233 a cédé la place à un refus de budget, sans élargissement de budget.

## Reçus conservés et inspection visuelle

Les neuf JSONL sont des tranches unitaires, indépendantes du journal corpus :
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/retained-aa-nine-f62e195fc/slice-34-35.jsonl`, `slice-64-65.jsonl`, `slice-210-211.jsonl`, `slice-213-214.jsonl`, `slice-233-234.jsonl`, `slice-317-318.jsonl`, `slice-338-339.jsonl`, `slice-470-471.jsonl` et `slice-472-473.jsonl`.

Pour chaque index numérique `i`, l’archive `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/retained-aa-nine-{i}-1/` conserve `process.log` et `exit.json`. Les neuf ont terminé avec `child_exit=0`, `wrapper_exit=0`, sans timeout ni signal. Le contrôleur a audité le log complet, l’exit et les trois lignes JSONL de chaque tranche avant de passer à la suivante. La tâche Gradle est `JavaExec` : aucune sortie JUnit XML ou événement de test n’est attendue; le code 0 du processus n’a pas été transformé en PASS de test.

Images ouvertes et réellement inspectées :

- `blurcircles2` : `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/retained-aa-nine-f62e195fc/images/blurcircles2/actual.png`, `diff.png`; référence `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas/integration-tests/skia/src/test/resources/reference/blurcircles2.png`. L’actual reprend la grille de cercles floutés et paraît globalement proche à l’œil; le diff rouge révèle toutefois des écarts sur les contours et étendues des nombreuses taches, cohérents avec 60.844850329781835 % à ±2.
- `dropshadow_pseudopersp` : `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/retained-aa-nine-f62e195fc/images/dropshadow_pseudopersp/actual.png`, `diff.png`; référence `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas/integration-tests/skia/src/test/resources/reference/dropshadow_pseudopersp.png`. L’actual a des coins carrés tandis que la référence présente un rectangle arrondi et une ombre plus étendue; le diff souligne le contour et ne ressemble pas à une concordance visuelle forte, malgré `declaredContractPass=true` avec le seuil minimum égal à 0.
- `rrect_blurs` : `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/retained-aa-nine-f62e195fc/images/rrect_blurs/actual.png`, `diff.png`; référence `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas/integration-tests/skia/src/test/resources/reference/rrect_blurs.png`. La référence contient les panneaux étiquetés `drawRRect` / `diff` / `drawPath`; l’actual corrigé comporte une grille sans panneau central `diff`. Les deux côtés présentent les rangées colorées et floutées, mais la composition de scène diffère. Le diff rouge couvre de grandes zones, cohérent avec 55.0775 % à ±2. Cette mesure n’établit donc pas la fidélité complète de la scène RRect corrigée.

Aucun actual/diff de GM échoué n’a été ouvert ni revendiqué comme inspecté. Les neuf reçus, images et références restent conservés à leurs emplacements; ils n’ont pas été régénérés.

## Scellés et limites

Le contrôleur rapporte le HEAD complet `f62e195fcf729b26f4e7ebd3d3306ef8b8c8ad90` et la comparaison exacte avant/après de douze empreintes SHA-256; aucune dérive de source, fixture, référence ou score n’est signalée. Empreintes consignées dans `controller-evidence.md` :

```text
52ae760650c990d78b4abe8dd3cd185d6be9fab0e9bae35648d1a6e555023385  gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt
8df7de96e05f31edc5622e3d5b0d857061e93d7b45b30607d024bfc3d83c78b2  gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt
9b0c21e7360582ed2eca3b2b9a4e7ae2ef6be7c5a8c1af1e16db5209fd1d4f3b  kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/CanvasExtensions.kt
990239175a030f0a2b23a17e6068133a10e0959f786e8ac836b7c77264e64218  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt
d089d359ece7cb174f532a497659624c638e839b5a3c6a1d4b0c1585ebf5d5c0  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/blur/RRectBlurGm.kt
13e1a92ae254fe945962047f8c269f0476f1bdbd13d1e93e7db8901f88cc4f43  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7RRectBlurPortSurfacePixelTest.kt
023f1e5b02d0b81e8d505a64a7cd3aa7d4c17f9fc202e0646ed9ab10cbe9b8dc  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7W6OrdinaryAaPathSourceIntegrationTest.kt
2dac75682b98b48028b3c09499ad895c3ea14771c19d6a5e110f461c9349f863  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmParityCheckpoint.kt
ef99b286fbeb18c5e9aa0abd2eaeef334e8903f8c44d3ade6a1f2e2fe6d02846  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmConformance.kt
087cdae285a914f6942ee82fee0cd28f074b370e8063c5f75936dca2db596999  refactor/waves/W07-gm-convergence/summarize-parity.mjs
18396eabdc6296aa16a9c949e0ec64ff707a60a722ea7139cfe05346995f758a  integration-tests/skia/test-similarity-scores.properties
96cd8349c5b08032fe7374566e4b220edd931e881ee1e3e3cbbfcdaf92bfd747  integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7InverseFilterDiagnosticSurfaceTest.kt (fichier protégé, laissé intact)
```

Le budget, les polices/codecs exclus, `jpg-color-cube`, les seuils et les scores historiques demeurent ceux de la baseline physique. Les dettes de fidélité pleine, panneau central, blur, limites fonts, renderer global rouge/incomplet et le test true-mask déjà rouge en baseline restent ouvertes. `declaredContractPass` et cette cohorte ne lèvent aucune de ces dettes. Aucune mesure de corpus complet n’est incluse ici.
