# W7 — source SolidRect avant mask blur : qualification

4 octobre 2026. Produit mesuré : 867cd214695ee534d5145b052a69c14a21cacf46.
Branche codex/w7-common-aa-path-source, base du lot
8c675db40881acf724887de2fb32a814f5f53770 ([draft parent #2447](https://github.com/ygdrasil-io/kanvas/pull/2447)).
[Spec](solidrect-mask-source-design.md), [plan](solidrect-mask-source-plan.md),
[checkpoint complet](solidrect-mask-source-corpus.json).
Publication et review large encore en attente à cette révision documentaire.

## Résultat et portée

Le clip visible ne sert plus de géométrie source au mask blur SolidRect.
W3 conserve un snapshot RectI32 de la géométrie avant target/clip ; les rebinds
matériel, blend, commandes et origine préservent ce fait. W6 le consomme
contextuellement, intersecté avec la demande inverse existante. La recette
native fullscreen reste authentifiée par la contenance de cette allocation
dans la vraie géométrie. Visible/scissor ordinaires inchangés ; aucune
géométrie reconstruite dans le renderer ni nouveau compiler.

Preuve ciblée : 5/5 tests PASS, 24 rendus/relectures GPU natifs, buffers RGBA
entiers de 4096 octets, replay et récupération sur la même Surface.
Les deux anciens contrôles positifs PATH AA restent intacts et passent
aux widths 2/1, soit 16 rendus/relectures supplémentaires dans le contexte.
Le contrôle plain-layer PATH AA n'est pas qualifié par ce lot.

**Le corpus ne progresse pas dans ce lot.** Les 631 fiches sont identiques
au snapshot 2485cfb, présence des champs comprise, en excluant uniquement
elapsedMs/renderMs. Cela inclut admissions, diagnostics, dispatch/refus,
hashes RGBA, références, domaines et toutes les métriques pixels.
Aucune régression observée dans ce corpus fixe, pas une garantie globale.

## RED causal, GREEN et budgets

Avant produit, le clip intérieur donne [0,0,186,125] au pixel (8,8), contre
bleu opaque [0,0,255,255] ; le vrai bord géométrique échoue à (10,8), red159
contre149 ±1. Trois tests : contrôle ordinaire PASS, deux filtrés FAIL.
Les 16 tentatives natives complètent ; les assertions de replay filtré
restent non atteintes après le premier échec d'oracle, pas de PASS extrapolé.

Après correction, l'oracle opaque intérieur et l'oracle indépendant sept
taps sigma1 du vrai bord passent sur les buffers entiers, deux renders de
la même Surface et deux Surfaces fraîches. Le coin géométrique garde son
falloff [189,0,186,255] : pas de padding opaque, clamp ni renormalisation.
Le contrôle sans filtre garde le cropping aux clips bord et intérieur.

Boundary public fixé AVANT produit/native, après diagnostic Astra indépendant :
B17456 = root4096 + staging8192 + W6 uniform16 + BLUE source16 + textures5136.
Les six textures de la source 10×10 coûtent 400+640+4×1024 ; W6d leases0.
B passe quatre renders ; B−1=17455 refuse exactement
w6b.filter.frame_budget_exceeded:, garde les 4096 octets sentinel0x5a,
puis discard et bleu plein passent deux renders/replays sur la même Surface
au même budget. Aucune calibration GPU ni relaxation des caps/budgets.
Le pool scratch W3 historique reste une dette d'accounting distincte.

La review produit a trouvé une intersection source/demande vide atteignable
dans un saveLayer au clip parent non vide. Le fallback ancien restaurait
toute la source hors demande. Cinquième test : clip parent[0,0,4,4], layer
plain, bleu[16,16,24,24]/NORMALsigma1, demande[-3,-3,7,7]. RED corrigé :
NoSuchElementException Key0 missing, sentinel intacte, deux récupérations
fullblue natives PASS. Le premier RED avait une erreur de fixture (clip
extérieur non restauré) ; elle a été corrigée par save/restore équilibré,
pas par une modification d'oracle ou de produit.

Fix1 refuse seulement direct MaskBlur + SolidRect déballé + intersection
vide, avant évaluation/allocation, avec le InvalidBounds existant traduit
en GPUPlanSurfaceTerminalException / w6b.filter.invalid_bounds:.
Sentinel et deux récupérations natives/replay PASS. Les quatre premiers
témoins et B/B−1 sont inchangés. C'est un refus conservateur TRANSITOIRE,
pas la sémantique Skia finale du dessin entièrement hors halo ; autres
familles/fallbacks et terminal no-op inchangés.

## Contexte et suite globale : limites explicites

Contexte frais après fix1 : 54 tests, 53 PASS, 1 FAIL, aucun skip/erreur.
Les 54 identités/resultats/exceptions (type/message/stack) sont identiques
au contexte pré-fix1 en retirant seulement l'horodatage at des événements.
Échec historique conservé :
W6aLayerBoundsSurfacePixelTest.emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers().
Il attend IllegalStateException, mais readPixels retourne true ; sa première
scène ne contient aucun draw source, seulement clip vide/backdrop Blur.
Le contexte entier n'est donc pas GREEN.

Une seule tentative globale, AVANT fix1 : limite240s atteinte, wrapper124/
child143, 728 identités terminées (691 SUCCESS/36 FAILURE/1 interruption
SKIPPED). W5eDecodedImageSurfacePixelTest.cubicDrawImageMatchesMitchellNetravaliOracle()
interrompu, XML non finalisés. Les tests ultérieurs ne sont pas couverts.
Aucun replay complet du parent permettant d'attribuer les 36 échecs : pas
de claim suite globale GREEN, sans régression globale, prêt à merger ou W7 fini.

Reçus privés des RED et de la tentative globale, sous
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb` :

| Archive | Process log SHA256 | Events SHA256 | XML SHA256 | Exit SHA256 |
| --- | --- | --- | --- | --- |
| `solidrect-mask-source-replay-red-20261004-2` (RED causal) | `a8b15c93bb31d1ad36981efb54821e2cbcdf7ce17cdafaf6bc6941fb362b56c0` | `72b92073c1f6835abccd12ea59cd581321a9415c320f441583d1d0499ea1bc22` | `5a75ca7c17e5099bc2913fa9d2cef10a4be561b464c849005ccc419a6b494844` | `d8c21137e526c2164e04c94069bfbed3374b01588dd60fc55bd58a0d2ca0fe58` |
| `solidrect-empty-demand-red-20261004-2` (RED2, intersection vide) | `4b688664c9bcd271618775ce07aefe5c924a278eea648a90ca852f2e8736a4bb` | `33d0bf53066aecd542fda9042a384a7204ad80aa5c1bc554347cd86fccdcd732` | `c2cd867f9e9ce2a9344d4069d632f6cd127272a9cfe3dd42fed343d203eb239a` | `1a1ec513cdd3d3a5d4792309971d8c0fc9d71c9cd3bc6295ca279e4e059f693c` |
| `solidrect-mask-source-global-20261004-1` (tentative globale pré-fix1) | `41aef1ec711f9fc1139fa986346b574a91b6758be84585e8f24802cb28e4fe75` | `4c538c77fb08384efb48a6baef2ff70f1efaef3fc35ca3191f3b5d24c84d7afe` | **absent : XML non finalisé** | `5f1ca77190fdb5e5495f13370a2710f18afc5cb1c0d342d5599687276e0e928d` |

Ces empreintes ont été recalculées en lecture seule sur les fichiers d'archives
nommés. L'absence d'XML pour la globale reflète l'interruption à la limite,
pas un reçu finalisé.

## Mesure complète 631/443

Les cinq slices fixes sont [0,200), [200,400), [400,607), [607,608),
[608,631), images=false, timeout30s par GM/240s wrapper, no-daemon.
Snapshot frais : 740715 octets,
SHA256 63f69928d3359eb5ed9f8a71488a92a46389b904ebbcbef2bba67a89d9cc8bb5.
Baseline 2485cfb : SHA256
ecef91f79e25940f9fabe2e2c624734a9fa2bcaa861391f91f0a8c7b4dcd74f8.
Registre631 SHA256
4ca8eea61451b1143fd3d15634d2c34e0c9ec31b74fd351ca30a69ee36f565d7.
Summarizer existant inchangé SHA256
087cdae285a914f6942ee82fee0cd28f074b370e8063c5f75936dca2db596999.

| Mesure | Baseline → après |
| --- | --- |
| Éligibles / exclus font / codec / jpg | 443 / 133 / 54 / 1 → identiques |
| Entrés en rendu | 393 → 393 (392 attempted=true + timeout607 sans ce champ) |
| Rendus / comparés | 220 / 197 → 220 / 197 |
| Render failed / setup failed | 172 / 50 → 172 / 50 |
| Non comparés / mauvaises dimensions / timeout | 15 / 8 / 1 → identiques |
| ≥95 % / ≥99 % des pixels à ±2 | 65 / 49 → 65 / 49 |
| Médiane des comparés à ±2 | 77,91666666666667 % → identique |
| Fiches avec changement hors temps | 0 / 631 |

vertices607 atteint encore son watchdog30s, JavaExec worker124/Gradle et
wrapper1 ; l'enveloppe240s n'a pas expiré. Le processus est terminé, sans
marker complete pour cette slice. Il reste dans443, pas de nouvelle exclusion.
Les 195 PASS du contrat historique et les 343 seuils zéro parmi443 ne
constituent pas la parité Skia. Les premiers diagnostics ne sont ni des
causes uniques ni des gains futurs promis. Les timings ne sont pas un benchmark.

## Reproduction et intégrité des reçus

Archives locales conservées sous /private/tmp/kanvas-w7-inverse-inventory.hbWqUb.
Le préfixe S ci-dessous désigne solidrect-mask-source-corpus-867cd2146-20261004-.
Pour chaque slice : terminal, lecture intégrale console/exit/inventaire,
vérification du PGID propre vide, audit de tous les champs du journal, puis
postseal SÉPARÉ exact avant le run suivant ou toute écriture.
Les 631 champs non temporels sont comparés sans projection de clés contre
la baseline authentifiée ; timings/identités/markers/console contrôlés
séparément. Lecture dédupliquée sémantiquement, pas lecture plate répétée
de chaque fiche JSON inchangée. JavaExec n'émet ni XML JUnit ni events Test :
leur absence est conservée, pas présentée comme une sortie finalisée.

| Archive | PGID vide | Exit wrapper / child | Journal SHA256 |
| --- | --- | --- | --- |
| S0-200 | 48145 | 0 / 0 | e20b751e4c54832d19376700ee172d88b652d4b238fe0ede83b90f29c0a4cb2d |
| S200-400 | 48695 | 0 / 0 | 62cb8d70da11a10a337e7f5fcb3f06fd1aec3a9c73d96b26bbbda9ff16182980 |
| S400-607 | 49188 | 0 / 0 | 46f92fb2a93d53fa205750fa30d630b75b88e1c9faac4e81a9cc278a5f697af2 |
| S607-608 | 49696 | 1 / 1 | 93288d7efe243c161688375e117146a9c8e5a1aae9e7a2bf3ef84e219dd944b9 |
| S608-631 | 51366 | 0 / 0 | 0ec650efea374ba3a193c8dc0983e9e56172e8353d468f8c2dfba5b35cfdc38a |

| Archive | Console SHA256 | Exit SHA256 |
| --- | --- | --- |
| S0-200 | 3faa2fc42c77ad13d5e76ad08567e7e3e7f26dd2542a75528c31b2c86ef38305 | 85aad1ec9d7db152e904848160e6e9a49aadb8ca6fc193e10cef367e5df59334 |
| S200-400 | e109051558fd1bc32636958b4d25750f5ce1f2089d434cf4810bff46b0d2ae14 | ba54fec8438aa9f9a5757b43f2fe601ff4d94f5b75bb99a898b2391f4ee1c14d |
| S400-607 | 09f9f8cb1d5da64323b57da4de5f13f8c238bc73155de4b095b31e267ea168fa | 395dd563b4a4e1781ef937aa7e5274da11cabe4ec7e1a5f77f91a8f5392bf262 |
| S607-608 | 7bce33442f694a4298a5bba11dcf1e17c278d7d319b5246e40dcb3f4b8544d66 | 107c2ce8d6cb1abb0ed44fc65e0850edec587c792738535704cd424995f8f602 |
| S608-631 | f1370807fe3fdb6374542f9a6e94139d109135e3069b3a23455d8d4555c262ee | 42a929a5bd3d2c85bdecc2297111e66cf9c9e5f83b0f0b7ccc32d612d6e1a89b |

Les commandes utilisent bounded-run.rb240 + ordinary-gm-evidence.init.gradle :

    ./gradlew --no-daemon --console=plain -I<init> :integration-tests:skia:measureSkiaParity -Pgm.parityOutput=<archive>/parity -Pgm.parityFrom=<from> -Pgm.parityTo=<to> -Pgm.parityTimeout=30 -Pgm.rendererCommit=867cd214695ee534d5145b052a69c14a21cacf46 -Pgm.parityImages=false -Pw7.validationDir=<archive>
    node refactor/waves/W07-gm-convergence/summarize-parity.mjs <cinq-journaux> <nouveau-snapshot>

Pour le GREEN/context frais : même wrapper/init/private validationDir, avec
:kanvas:test --rerun et le filtre *W7SolidRectMaskBlurSourceSurfacePixelTest* ;
contexte W6aLayerBounds, W6aLayer, W6aLayerBudgetRecovery, W6bMaskShaderTable,
W6bBudgetRecovery + les deux méthodes positives inchangées de W7CommonAaPathSource.
Archives fix1-green-20261004-1 et fix1-context-20261004-1 sous le préfixe
solidrect-mask-source-. Log/events/XML GREEN SHA256 respectifs :
5f5854179243c96c782b3d54b14f470ce8acd576e203bdf56f476501ffb4ac28,
363fc1ad28a32475f14069c2d347bcae334c22e8822241a050a3895c9c2fbba4,
2ce6dc2594281357d6e4bd5f54a2447221afae22cf2585de7d3b48b049573e3e.
Log/events contexte : 89c8521b6ccfe0a406409a6c3e21380670cdc79939674f3f7c341b9943a34195,
c0262a8efaea4d5703c5892d3337f6523a23818adac11bb9551853c71e355a78.
Ces runs ont été audités intégralement (dont XML/stdout/stderr/inventaires)
et séparément postscellés ; détails des RED/globale dans les reçus privés.

Postseal corpus commun : HEAD867, 7687 chemins tracked/protégés/own-workspace,
manifest 9e61230d32b99fbd70e13a08fa4933641dd7769a35da42c298770738173fa23b,
gitlink wgsl4k séparé, aucun missing. La fixture nouvelle9a2058e2 est gelée
depuis RED corrigé ; anciens contrôles b6206a00 et inverse96cd8349 intacts.
814 PNG : e589c21c3c3eebd3787434064fe5a122f6dc2005cf4d6786b529b1c8fd2a2c6d.
559 scores : ba0bd77609386acd8b77443d853d424d36edea08719b8e868b71a65fbc8dbd54.
1004 références : 1fe843ea38ab34eaa9b49fa59eb872b2cee671e849e8645f5408e2bca510a7c9.
Les 631 statuts/hashes de référence sont inchangés. Aucun PNG/score/ref écrit.
Warnings hérités native-access/Unsafe/Gradle restent visibles.

## Reviews, décisions et suite

Preflight Sol puis correction des coûts/rebinds/replay : permission de
commencer le produit, pas une qualification prématurée. Review Task1 de
16dcf6320→1759669bf : C0/I1/M1. Fix1 1759669bf→867cd2146 : I1/M1 ADDRESSED,
C0/I0/M0, Approved ciblé ; aucun verdict large inventé.

| Constat de preflight | Disposition consignée | Limite / portée |
| --- | --- | --- |
| I1 — dérivation du coût par l'ancien chemin legacy | **ADDRESSED pour la preflight** : route active W6a/W6b, six textures RGBA8 et boundary prospectif `B=17 456` / `B−1=17 455` documentés. | Dérivation statique prospective ; elle ne prouvait ni la topologie finale ni le coût natif avant les témoins GREEN/B−1. Les anciens chiffres 256/1600/784 octets et 336 uniform bytes ne décrivent pas ce chemin. |
| I2 — clones/rebinds SolidRect pouvant perdre le fait source | **ADDRESSED pour le périmètre de preflight** : les sites réels supplémentaires (`RenderGraphConstruction.kt`, `W5bGeometryLanePlanV3.kt`, `W5bDestinationGraph.kt`) sont nommés avec les chemins déjà ciblés et les frames device/layer. | La préservation effective et l'invariant de contenance relevaient de l'implémentation Step 3 et de sa review ; aucune approbation produit ne découle du seul audit de périmètre. |
| I3 — assertion du premier pixel interrompant la capture RED | **ADDRESSED pour la capture du gate** : fixture corrigée pour collecter quatre renders par scène ; les 16 tentatives natives ont été capturées. | En RED, l'oracle complet filtré s'arrête au premier mismatch : l'égalité replay des buffers filtrés n'est pas établie par cette capture ; les preuves GREEN doivent l'établir. |
| M1 — ambiguïté entre coordonnées de l'ancien coin et du nouveau centre | **ADDRESSED** : `[-3,-3,7,7]`, origine `(-3,-3)`, est explicitement l'ancien contrôle coin `[0,0,4,4]`; le nouveau centre et le vrai bord gardent leurs coordonnées propres. | Clarification documentaire seulement ; aucune extension d'oracle ni de géométrie. |
| M2 — lecture d'un pixel avant validation de la forme du buffer | **ADDRESSED** : `printEvidence` valide dimensions 32×32 et longueur 4096 avant `rgbaAt`. | Robustesse du diagnostic seulement ; l'assertion oracle complète reste inchangée. |

Suivi documentaire de la preflight-fix1 : la phrase de livraison désormais
autorise à **commencer Step 3** après le RED audité. Cette permission de démarrer
le changement borné n'est ni l'approbation Task 1, ni un statut GREEN ; les
preuves de coûts, de replays, de contexte et de non-régression gardent leurs
gates distinctes.

Décisions déléguées : snapshot source contextuel plutôt que compiler nouveau
(coût : fait immutable/transport supplémentaire) ; conservation des clones
réels (coût : audit multi-fichiers) ; collecte de toutes les captures avant
assertion (coût : fixture plus longue) ; boundary B/B−1 indépendant (coût :
témoin GPU supplémentaire) ; refus transitoire hors demande (coût : admission
Skia encore incomplète) ; correction du save/restore de fixture (coût : RED
supplémentaire). Harnais original/fresh Luna bloqué par agent thread limit :
un siège Luna existant a assuré le fix borné, rapport séparé, sans code du
contrôleur (coût : exception documentée à l'isolation de contexte). Tous les
rapports/raw archives sont conservés ; aucune approbation humaine inventée.

Prochain lot : source commune PATH AA sans filtre dans un plain saveLayer,
après publication/review de cette fondation. L'ancien refus plain-layer et
l'admission entièrement hors halo restent ouverts. Les 17 anciens préfixes
PATH AA ne promettent pas 17 gains ; corpus et pixels devront mesurer le gain
réel après admission. W7 ACTIVE, pas de merge ni clôture.
