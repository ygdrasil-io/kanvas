# W7 — qualification des contrats image promus

Lot borné, base `84a7c8a3f31ac35c6b53e692f408f5a6db230d03`, branche `codex/w7-promoted-image-contracts`, [draft #2445](https://github.com/ygdrasil-io/kanvas/pull/2445) stackée sur [draft #2444](https://github.com/ygdrasil-io/kanvas/pull/2444). Décisions déléguées par la carte blanche W7 ; pas d'approbation humaine inventée. Reviews de tâches et finale approuvées pour publication draft. W7 ACTIVE, ni merge ni GREEN global.

## Résultat qualifié

Le lot conserve les quatre scènes originales W5e, remplace leurs attentes de refus devenues obsolètes par des assertions strictes là où l'oracle est établi, et ajoute des compagnons distincts hors égalité géométrique. Le vrai Point image reste refusé avec le même diagnostic ; sentinel64bytes intact et récupération/replay image Rect sur la même Surface, sans reset ni purge.

Trois adaptateurs Surface corrigés : comptage des propriétaires Vertices/Mesh survivants depuis les packets Render de shading finaux ; exclusion du seul clear synthétique authentifié ; transport de scopes et telemetry typed de la completion native, vérification attempt/outcome, publication des deltas natifs réels. Les rôles de packets puis les événements lifecycle conservent chacun leur ordre relatif ; cette concaténation n'est pas un entrelacement chronologique. Les gardes output/readback/bytes/rétention restent présentes. Runtime, planner, math, sampling/AA, budgets et politiques d'admission inchangés.

Qualification ciblée : **10/10 PASS**, puis contexte exact **57/57 PASS** (Composition25 + Image29 + Evidence3), zéro skip/erreur, exit0, pas de timeout ni signal ni runner133. Le contrôle inchangé `encodedPlainLayerBudgetIsExactAndOneByteLessRecovers` conserve B888/B−1, sentinel et récupération. Aucun corpus global exécuté ou gain de parité revendiqué.


## Review de tâches

Review indépendante Sol du checkpoint `61ae9d813a4f1db0d6845ad6b746f63f1d1216df` : Task1 spec compliant/quality Approved ; Task2 spec compliant/quality Approved, Critical0/Important0. Un minor hérité : warnings décrits ci-dessus. Les points « cannot verify from diff » concernent les preuves main-only et la publication ultérieure, pas un défaut produit : main a effectué les audits et post-seals décrits, recontrôlé69chemins/814PNG/559scores et conservé les46preuves privées. Rapport privé `.superpowers/sdd/promoted-image-contracts-plan/task-coupled-review.md`, pas de build/Git/native par le reviewer.


## Review finale et publication

Review finale indépendante Sol du lot `84a7c8a3..e0d4e834f5415c18174198ede62763de6a50509b` : Critical0/Important0, publication draft approuvée, merge non approuvé. Seul minor différé : warnings hérités. Les quatre comportements laissés ouverts par le reviewer restent suivis : cellules originales ambiguës, admission sans clip/négatifs singuliers, compteur homogène toutes familles, corpus/font/codecs exclus. Main confirme ces périmètres avec leurs coûts : qualification visuelle et global GREEN non établis ; aucune dette effacée par le PASS ciblé.

[PR draft #2445](https://github.com/ygdrasil-io/kanvas/pull/2445) créée et attachée, stack sur #2444/`codex/w7-readback-budget-admission`. Le reçu de publication ajouté après le gate final ne change que documentation/suivi ; les cinq SHA Kotlin du checkpoint et les69chemins/814PNG/559scores restent inchangés. Les rapports privés de tâches/final et les46preuves sont conservés. Aucun merge, rebase, force-push ou nettoyage destructif.

## Empreintes du checkpoint natif

Fichiers produit dans `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/` ; tests dans `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/`.

| Fichier | SHA-256 |
| --- | --- |
| GPUPreparedSurfaceFrameBuilder.kt | `169cabb7c40c47f0fe4bb374d6e0fef3cdca88c2677b2dd83abdbd1b00fda5b5` |
| GPUPreparedSurfaceFrameExecution.kt | `394401d600bb72c917a3e2e48f3ac461a5e0250a7083833bbde90430f2f8aaab` |
| GPUPreparedSurfaceProductRouter.kt | `0d2ae9c14f30be58a75366f74179930ba6d4132d5f296aac9d95db660c10cc1d` |
| W5eImageShaderSurfacePixelTest.kt | `918fc35e41a8039b1fdd9cc4ab02f972ec2b5cd9adb86c30d09ee27abf5c58eb` |
| W7PreparedImageEvidenceSurfacePixelTest.kt | `e7c3352a7c01efd38e0e9d1b6dcf31e02f3738cf6ec835fbbf3256f2fecd90c5` |

Les deux runs GREEN sont pré/post-scellés sur69 chemins : les66 chemins hérités + execution/router/newtest. Seuls les trois adaptateurs et les deux fixtures autorisés diffèrent du parent. Les archives et workspaces privés ainsi que le diagnostic inverse non suivi `96cd8349c5b08032fe7374566e4b220edd931e881ee1e3e3cbbfcdaf92bfd747` sont conservés, non ajoutés au commit.

| Artefact immuable | SHA-256 |
| --- | --- |
| 814 PNG repo-relatifs | `01d066c60238d44e0aaaf3f03302180962765a50adbc4cc7435d19fcbd6f473f` |
| 813 PNG non-cibles | `ca9ccd2e0596c72d277ca143b3b8dc92cf3dd3bdcb26751818b2fbd7d8bc8ba0` |
| tinybitmap rendu | `b3f302353c21ca67bbf7b36d3c9db028d49d487806a367e98b119f14a326a4dd` |
| tinybitmap référence | `c52d28dd7fe1d45b1650ad3332ed36df1acb60987d166c588653180cd7f53c01` |
| fichier559scores, cible0.0 | `49c7f1262668fa761e9dbd2f87696eea7e9fe0e8307b225eec8833fa1609fb16` |
| autres558valeurs | `d9f2f6e711f0f81fc416d9116876ffaff7ae9e09a0fd24d76fd0004bc696563f` |

## Exécutions et diagnostic honnête

| Archive | Child | Exit | Durée Gradle | Résultat |
| --- | --- | --- | --- | --- |
| promoted-image-contracts-native-1 | 49690 | 1 | 23 s | 7 : 2 PASS, 5 FAIL ; runner exit133 |
| promoted-image-contracts-native-2 | 67208 | 1 | 20 s | 7 : 5 PASS, 2 FAIL ; pas de133 |
| promoted-evidence-red-1 | 79820 | 1 | 17 s | 3 FAIL ; pas de133 |
| promoted-evidence-red-2 | 82062 | 1 | 17 s | 1 FAIL ; pas de133 |
| promoted-evidence-green-1 | 96505 | 0 | 32 s | 10/10 PASS |
| promoted-evidence-context-1 | 98089 | 0 | 2 min13 s | 57/57 PASS |

Tous les runs sont main-owned, séquentiels, fresh archive, `--no-daemon`, outer240. Main a lu intégralement process/exit/tous START-END/toutes stacks/tous XML stdout-stderr, inventorié les fichiers et contrôlé l'absence de ses processus natifs, puis effectué un post-seal dans un appel séparé. Les sorties d'outil tronquées des native1/contexte ont été relues intégralement fichier par fichier avant clôture de l'audit ; aucun rerun pour remplacer une preuve illisible. Les binaires sont retenus/empreintés, pas présentés comme décodés.

Native1 : trois erreurs de fixture comparaient l'identité des facades Path défensivement copiées. Correction par valeur math PathF32, autres champs et token source conservés. Deux vrais RED Vertices comptent0 au lieu de1 ; pixels corrects insuffisants. Runner133 distinct : rapport macOS `/Users/chaos/Library/Logs/DiagnosticReports/java-2026-10-03-225814.ips`,84171bytes, SHA `8348ecdbfe0f9fc4b37b83e1d3e091997a87113bd89e0acc815c29514ef7ee7d`, PID51288, Thread-5, SIGTRAP/NSWindow close « Must only be used from the main thread ». Main a lu header/outcome/ASI/triggered-thread frames/registers, pas toutes les autres threads des84kbytes ; rapport externe non copié dans l'archive. Le pattern public existant AfterAll dispose après la classe, jamais entre replay/récupération ; native2 et tous les suivants n'ont plus133.

Native2 : cinq fixtures PASS, deux Vertices RED accounting/evidence. RED1 nouvelle classe : visible bleu mais count0vs1 ; zero-survivor clear mais count1vs0 (vrai draw de clear synthétique) ; mixed refuse avant résultat. Cause : off-target sans scissor n'a pas d'autorité de clip-culling, et W5h décline cette topologie vers le fallback image refusé. Correction de fixture : clipRect viewport non-AA explicite, sans changer géométrie/oracle/count. RED2 produit le rouge exact et deux draws GPU, mais count1vs2 et traces publiques absentes. Ces RED précèdent les trois corrections produit ; pas d'oracle ajusté au GPU.

GREEN10 conserve26 buffers RGBA complets dans stdout (18 fixtures W5e,8 nouvelle classe). DrawVertices et vrai DrawMesh public : blue4bytes/count1, scopes Upload/Upload/Render/Readback. Mixed : red4bytes/count2, Upload/Upload/Render/Render/Readback. Zero : clear4bytes/count0, Render/Readback,1 vrai draw indexed d'initialisation. Chaque replay vérifie frameCoordinator/encoder/commandBuffer/submit/readbackCopy=1, QueueSubmitted avant CompletionSucceeded, draws+drawIndexed et pipelineBinds cohérents avec RenderStats. targetCreations peut légitimement1→0 ; égalité des pixels et assertions par frame, pas égalité artificielle de toutes les maps de cache.

Contexte57 :25/25 Composition,29/29 Image,3/3 Evidence. Les deux anciennes méthodes tileModesApplyPerTapOnBothAxes72.941s et cubicTileBoundaries25.931s expliquent l'essentiel du temps, pas un hang. Compilation test UP-TO-DATE dans ce run.

Warnings conservés : JVM restricted native access, LWJGL Unsafe et dépréciations Gradle. Recompilation GREEN : warnings hérités dans GPUPreparedSurfaceFrameBuilderTextTest (unnecessary!!), ProductNativeSmokeTest/RouterTest (unsigned opt-in, deprecated RuntimeEffect, conversion/!!) et VerticesRefusalMatrixTest (deprecated RuntimeEffect). Aucun warning nouveau des cinq fichiers modifiés ; sortie non pristine, dette héritée à suivre, pas d'édition cachée de tests fonts exclus.

## Commandes exactes

Exécuter depuis le worktree W7 isolé. Les répertoires d'archive déjà existants ne doivent pas être réutilisés : choisir un nouveau nom et changer ensemble argument wrapper et w7.validationDir pour une nouvelle qualification.

### promoted-image-contracts-native-1

```sh
rtk proxy ruby /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/bounded-run.rb /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-image-contracts-native-1 240 ./gradlew --no-daemon --console=plain -I/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/ordinary-gm-evidence.init.gradle :kanvas:test --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.rrectImageShaderAdmissionAndNonTopLeftPixelProbe --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pathStrokeImageShaderAdmissionAndFullPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pathHairlineImageShaderAdmissionAndDiagnosticBoundaryRows --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.hairlineImageShaderTieFreeCompanionsHaveExactPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.verticesImageShaderAdmissionAndDiagnosticDiagonal --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.verticesImageShaderTieFreeCompanionsHaveExactPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pointsImageShaderRetainsExistingPublicRefusal -Pw7.validationDir=/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-image-contracts-native-1
```

### promoted-image-contracts-native-2

```sh
rtk proxy ruby /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/bounded-run.rb /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-image-contracts-native-2 240 ./gradlew --no-daemon --console=plain -I/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/ordinary-gm-evidence.init.gradle :kanvas:test --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.rrectImageShaderAdmissionAndNonTopLeftPixelProbe --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pathStrokeImageShaderAdmissionAndFullPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pathHairlineImageShaderAdmissionAndDiagnosticBoundaryRows --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.hairlineImageShaderTieFreeCompanionsHaveExactPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.verticesImageShaderAdmissionAndDiagnosticDiagonal --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.verticesImageShaderTieFreeCompanionsHaveExactPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pointsImageShaderRetainsExistingPublicRefusal -Pw7.validationDir=/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-image-contracts-native-2
```

### promoted-evidence-red-1

```sh
rtk proxy ruby /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/bounded-run.rb /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-red-1 240 ./gradlew --no-daemon --console=plain -I/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/ordinary-gm-evidence.init.gradle :kanvas:test --tests org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest -Pw7.validationDir=/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-red-1
```

### promoted-evidence-red-2

```sh
rtk proxy ruby /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/bounded-run.rb /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-red-2 240 ./gradlew --no-daemon --console=plain -I/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/ordinary-gm-evidence.init.gradle :kanvas:test --tests org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest.mixedVerticesExcludeCulledAndNoOpOwners -Pw7.validationDir=/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-red-2
```

### promoted-evidence-green-1

```sh
rtk proxy ruby /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/bounded-run.rb /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-green-1 240 ./gradlew --no-daemon --console=plain -I/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/ordinary-gm-evidence.init.gradle :kanvas:test --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.rrectImageShaderAdmissionAndNonTopLeftPixelProbe --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pathStrokeImageShaderAdmissionAndFullPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pathHairlineImageShaderAdmissionAndDiagnosticBoundaryRows --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.hairlineImageShaderTieFreeCompanionsHaveExactPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.verticesImageShaderAdmissionAndDiagnosticDiagonal --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.verticesImageShaderTieFreeCompanionsHaveExactPixels --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.pointsImageShaderRetainsExistingPublicRefusal --tests org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest -Pw7.validationDir=/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-green-1
```

### promoted-evidence-context-1

```sh
rtk proxy ruby /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/bounded-run.rb /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-context-1 240 ./gradlew --no-daemon --console=plain -I/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/ordinary-gm-evidence.init.gradle :kanvas:test --tests org.graphiks.kanvas.surface.W7SurfaceCompositionPixelTest --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest --tests org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest -Pw7.validationDir=/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/promoted-evidence-context-1
```

## Limites encore ouvertes

- Originaux :8 cellules des lignes centrales de hairline,4 cellules diagonales de triangle et TL RRect restent diagnostiques. Les compagnons exacts ne qualifient ni le snapping Skia ni le propriétaire AA du RRect. Voir [design](promoted-image-contracts-design.md) et ses sources primaires WebGPU.
- Le compteur conserve l'historique core/text/atlas/flat-only layer, plus les propriétaires Vertices distincts : pas une migration générale vers des opérations logiques homogènes toutes familles.
- Admission Vertices off-target sans clip et négatifs singuliers par famille restent non qualifiés. Numeric controls existants inchangés PASS ; aucun diagnostic inventé ou promotion implicite.
- Fonts/codecs externes/jpg-color-cube exclus ; aucun test infrastructure/mock/fake/skip/CPU fallback ; thresholds/validators/budgets/caps inchangés.
- Aucun render/score régénéré, aucun gain corpus mesuré. Tinybitmap exact/±2 historique toujours0 malgré son SSIM ; provenance des PNG, AA/coverage et autres dettes du [pilotage](https://github.com/ygdrasil-io/kanvas/blob/36350563f48485598009d61a1707f7cff0ff7e94/refactor/waves/W07-gm-convergence/pilotage.md) restent ouvertes.

## Inventaires des preuves privées

Racine locale `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb`. Les46 fichiers ci-dessous sont disponibles dans cette session, non versionnés et non garantis accessibles depuis GitHub. Les commandes/résultats/empreintes sont le reçu durable ; SHA d'un fichier n'est pas son contenu complet. Préserver ces archives. Wrapper `bounded-run.rb` SHA `b06c94d3775c5763b4153e256b5cf304086a38c7cad8e4dd97e56b09a4e2a4bd`, init `ordinary-gm-evidence.init.gradle` SHA `8d541cd70814c6e93afc25558a1f86c5f78e1a7df143f834910566eb4ca4d917`.

### promoted-image-contracts-native-1

| Chemin relatif | Bytes | SHA-256 |
| --- | --- | --- |
| binary/output.bin | 8111 | `8d20fb6e6f57c01258a465104339f0fa544928dddc875206aebaa75ad131afa2` |
| binary/output.bin.idx | 234 | `81c75aeefca8d80122879a31bc9f2d4d0a31b65f2a2c7d7cf4b693a070f0efbd` |
| binary/results.bin | 19888 | `b9d1a01102b79b4fec4535dd58462594376b1be4d7da312db403611a911e3077` |
| events.jsonl | 15005 | `e6a69c2045de46079cd7002ffcdab706bd91b68fd5b0e10dc031f395de6543a7` |
| exit.json | 111 | `9e6838f40bd5a09b132d0446f8892f8c0cd92bbdf4b39d44bb292e2c0308a481` |
| process.log | 20273 | `bbdad9f0e0b600c3306d35e12fe84372f9f21115cc0f52c3c2ae242c69238dc8` |
| xml/TEST-Gradle-Test-Run--kanvas-test.xml | 3032 | `8336bca28cc3efc5d37e2911b7a172c0fddc298eb2aa0eb5a16906fdd1825785` |
| xml/TEST-org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.xml | 26188 | `e0e0338b52266d267768032486de6025453f8eec82268f627c8a70b755ed1168` |

### promoted-image-contracts-native-2

| Chemin relatif | Bytes | SHA-256 |
| --- | --- | --- |
| binary/output.bin | 12861 | `55181fe9a7c5e2b349190a19a2140bc0acf202b2fb1454baf61174ee6938054e` |
| binary/output.bin.idx | 234 | `754b90d1daf303cc8739d123d3ab3b7269758590a7abf1545a95cc720e9b6fa2` |
| binary/results.bin | 3891 | `12a267aa05f6b6b0c3847c19155c819da40775fbaaee9db7b88e4f0037229e01` |
| events.jsonl | 6523 | `4c8d41a96ccd647e4d8861c8821aefd5ba0eedcb86f7fb03b5b0a5a5febb0097` |
| exit.json | 111 | `c183add791ebdbdf4ef3674e022dff15a2c968cdbff2b25822c32588d1e34ebd` |
| process.log | 11776 | `92b184f859a32543032f0db5782fc65af70c43386e664fd0c91e1e06cdd1cc13` |
| xml/TEST-org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.xml | 17371 | `0c80601395b71a7c8f8ad78af2ca8020439b87f1107ad945a66c48a51b390a2c` |

### promoted-evidence-red-1

| Chemin relatif | Bytes | SHA-256 |
| --- | --- | --- |
| binary/output.bin | 826 | `9316f7b8be1e9eb3bcedbd27998a2ff191fa697ede0a206a283fb5d0c7567767` |
| binary/output.bin.idx | 69 | `6001174f25a508ed8150c1fc4e0ce8c0f792e5d030d1424bd8743ea81a6d19b5` |
| binary/results.bin | 4743 | `276292bd27453cd6b9aeb19c9019f3de40ec0c44eee345d8d8e906978a7eefe2` |
| events.jsonl | 5480 | `e74cf5ab5a908ee3171b2c90f54a30e14752fb2a25913511f4a6cae0af8cb3d3` |
| exit.json | 111 | `61d65656f1b14d6ba389006064391659d7df43b39b8e5eb60dbb64b9fd2d6aca` |
| process.log | 12374 | `163b0a2380980846990788b30730a6b8d68c28938dfd68d7f752a74ef1f6552e` |
| xml/TEST-org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest.xml | 6160 | `844f5d421d17c0d688eed70fac1c31636c7e80d22dfa955c204c2b7688e0325e` |

### promoted-evidence-red-2

| Chemin relatif | Bytes | SHA-256 |
| --- | --- | --- |
| binary/output.bin | 414 | `37a88f3e11298157fbff32c6011d0e1d40f2cce2002a4023a6a5997f184b0a81` |
| binary/output.bin.idx | 36 | `316e6cf5e786731386664e737d7235d2c405e5b86067adebd99883f412db69c0` |
| binary/results.bin | 1624 | `0de2508e60333c127d98f45aa43cff5f17425810689b7972799e7c5bfef9939e` |
| events.jsonl | 1814 | `2c7ef97e0b7042943c2e70ff40fdbd299d1a940f4711fe9c8bad4bad443826df` |
| exit.json | 111 | `b951313729ba675a8d043f7c18f2f7cdc53df85b431b49b66804bcf815163db2` |
| process.log | 9930 | `de2eefa4a441a6bf4bdec13935d4b54890577b7cd1d02b22d861d2311c9eb42f` |
| xml/TEST-org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest.xml | 2406 | `23adf378a887a9a7485d1ba60c828a5f796c52ff426d7c727457d1e4e749022f` |

### promoted-evidence-green-1

| Chemin relatif | Bytes | SHA-256 |
| --- | --- | --- |
| binary/output.bin | 27952 | `3c64294df2d644e00abe0c36df530adf32305f450dc1c58457f99cc7a67c5bd8` |
| binary/output.bin.idx | 335 | `54e0582fd585dfb63f956e7f090f5018526789e0f89ef3f1ef928169dfbe7511` |
| binary/results.bin | 1369 | `a79adfb57d683518f0307a4dca97d27703e77fa129d836011e34fa4642a9e95b` |
| events.jsonl | 5436 | `9b9734b9fd97d9a2b6a4091a61dd559437c49da105bbb08837746ec028877c34` |
| exit.json | 111 | `2e6a1fd25d52a3bd9aaa71c32dd30d5b76f4616418c18fb18b126f999d742829` |
| process.log | 29640 | `c27b504046db789b34547ea4e166605b31c52421d1b3702cdab8855b069f041b` |
| xml/TEST-org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.xml | 20661 | `4053ffcd6e8a093c07b9dc1c1e232a6e0cbbb5c27ce153d31880009c2515b87b` |
| xml/TEST-org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest.xml | 9448 | `bc959a633c6e9eb0c10b5ed0ad9322ca528b2761456319fff83946792f40fa80` |

### promoted-evidence-context-1

| Chemin relatif | Bytes | SHA-256 |
| --- | --- | --- |
| binary/output.bin | 28977 | `697bc6aa671c50f9190b10a28eaf09d0e6935d6fce4d85864a3c56adffaae0a9` |
| binary/output.bin.idx | 370 | `06e0c1c6a4033534791f309ff60184b7a726b19cd50a935c411dbf1955356282` |
| binary/results.bin | 7256 | `b4bc048e69368220c4d891df20ee3a5523a32a090e26a588cdf3f3c992b09d6b` |
| events.jsonl | 31401 | `e69efc7526bc1962d2a0ea287c75fc34f11640b12491adfc980ed8883c1992d7` |
| exit.json | 111 | `f2b66da77eb665a7a2d4a85c09c31d98412b2e44f34045dc910c38f8952ce182` |
| process.log | 13914 | `ab3c42c2a595fa6306e999230dd5a3741a6fc47600b7091b653155d4a60b6166` |
| xml/TEST-org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.xml | 24051 | `2b7d349dbb03ecc8780b3328fb1ef870e43ae5b1c1e5004881ca9d6688e2ce89` |
| xml/TEST-org.graphiks.kanvas.surface.W7PreparedImageEvidenceSurfacePixelTest.xml | 9445 | `4d782b9000e779071c7d2cac9460514b18a377d4eea8085cad796becc56805f4` |
| xml/TEST-org.graphiks.kanvas.surface.W7SurfaceCompositionPixelTest.xml | 5474 | `95371834314cc55688433fc7531b308815c78ae8d21dbaf61971ee1f403c72b3` |
