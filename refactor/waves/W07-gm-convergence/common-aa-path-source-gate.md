# W7 — gate native source PATH AA : falsifier initial et reprise

État actuel : la validation focalisée est GREEN (9/9), couvrant la source
layer et le budget B=25 312 / B−1 (`budget.w5g.composed-uniform`). Le run
contextuel est 119/120 ; le run global est incomplet et ne constitue pas
GREEN. Ces preuves portent sur les sources produit `W6aLayerPlanCompiler.kt`
(SHA-256 `f152690c3249a6a6b0223847e32310af2514a5035f0a2ff49717fe4ba32b5d5d`)
et `W4dGeneralPathPlanCompiler.kt`
(SHA-256 `1fcb3a87283a58be8a97d8ee83271e3b2121eb32d60042ce4f59b3ed4f04ff62`)
avant le nettoyage mineur ; la requalification de ce cleanup attend le
contrôleur. Les preuves historiques ci-dessous restent inchangées.

4 octobre 2026. Produit inchangé à2485cfb ; HEAD documentaire6ac4fb193.
Cette preuve invalide la gate requise par common-aa-path-source-plan.md,
mais ne clôt ni W7 ni le diagnostic du blur. Aucun gain corpus revendiqué.

## Résultat réellement mesuré

| Cas public | Résultat | Portée de la preuve |
| --- | --- | --- |
| Root seul, widths2/1 | PASS | 8buffers4096bytes entiers, completion native/replay/surfaces fraîches |
| Root témoin du test layer, widths2/1 | PASS | 8buffers supplémentaires, mêmes oracles indépendants |
| Plain saveLayer, widths2/1 | RED attendu | 8refus `w6a.layer.unsupported_child`, scope0, PATH/AA, aucun filtre propre |
| Root avec sibling mask blur | RED inattendu | Completion native puis erreur pixel(0,0), width2/premier rendu seulement |

Au témoin filtré, bleu attendu[0,0,255,255], observé[0,0,186,125].
Rect bleu[-16,-16,48,48], NORMAL sigma1, clip hard[0,0,4,4], Surface32×32
LINEAR. L'oracle exige le support opaque constant autour des pixels
visibles ; la frontière géométrique n'est pas proche. Cet argument est à
confronter au chemin source/clip/domain du produit, pas à remplacer par
les bytes observés. Aucun nouveau correctif n'est encore validé.

Le contrôle filtré a13scopes :11Render,1LayerComposite,1Readback ;10draws et12pipelineBinds, submit1/readbackCopy1, aucun diagnostic.
Sa completion ne prouve pas les pixels ; les replays restants et width1
n'ont pas été atteints. Tous les root qualifiés ont Render+Readback,
submission/completion,2drawIndexed et3pipelineBinds, submit1/readbackCopy1.

JVM/JUnit3tests :1PASS,2FAIL,0skip,0error. Compilation réussie. Les warnings
native-access, Unsafe et Gradle deprecated restent dans les logs.
Le GPU est disponible ; ce n'est pas un `GPU runtime is unavailable`.

## Autorité, audit et intégrité

Main seul exécute le runtime, aucun worker build/test/Git. Wrapper/init
inchangés ; --no-daemon, single-fork, borne240s. Archive privée conservée :
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/common-aa-path-gate-red-20261004-1`.
PID/PGID9117, exit1, timed_out=false, aucun signal ; Gradle17s. Groupe propre
vide après terminal. Aucun autre daemon terminé ou modifié.

| Artefact complet lu | Bytes/lignes | SHA-256 |
| --- | --- | --- |
| process.log |19610/204|0ae4a751189bfd00ec4afb703c689603309079954dd4d7d3ddaf23c745d4ef11|
| exit.json |110|678af6f0300bdace5bada812525d812fe6c895fabdf7803182d2c8e213f89040|
| events.jsonl |12580/6|0cbfdb437f81a535e6537ff856d7dbf0b9e9605fd45785375036dd4e86782c51|
| JUnit XML |36898/70|3877675464f0d3c368fd0864f2f55ab79ca18010d2b2f8f12b721a261b9df9fd|

Tous START/END et XML concordent. stdout16688bytes/stderr0 inclus ; inventaire
binary conservé/hashé. Un affichage combiné XML a tronqué303tokens : main
relit23..45, couvrant le manque, avant post-seal. Une vérification read-only
Ruby filter_map a échoué sur la version système ; relancée avec map/compact,
résultat ownPGID vide. Ces échecs d'audit ne deviennent pas des preuves GPU.

Post-seal effectué dans une invocation séparée après audit complet :
69sources/fixtures historiques, nouvelle fixture, docs/ledger/report/package,
HEAD,814PNG,559scores,1004références physiques/631statuses, wrapper/init et
inverse untracked protégée tous identiques au pre-seal. Aucune écriture
produit/docs/Git pendant native ou avant la fin audit+post-seal. Les reçus
pre-seals.json et post-seals-and-audit.json gardent les maps exactes.
Fixture nouvelle SHA b6206a00d47b37a8ebd85486741853798089bdbf18efdb8eea70e68bd85494c7,
11206bytes ; inverse SHA96cd8349c5b08032fe7374566e4b220edd931e881ee1e3e3cbbfcdaf92bfd747,
6504bytes préservée/non indexée.

## Disposition

L'extension common AA PATH reste STOP avant produit : la condition
root+contrôle natifs/fullbuffer PASS n'est pas satisfaite. Le refus layer
est établi, mais le réemploi du consommateur W6 ne peut être qualifié par
un contrôle aux pixels incorrects. Oracles/caps/budgets/validators inchangés.
I1hint observable et I2nouveau budget ont été corrigés dans le plan ; leur
re-review indépendante reste pending, aucun B natif nouveau qualifié.
M1root plain-layer-owned sans W6b différé ; M2les17préfixes sont14root/3child,
12filteredroot/5unfiltered, aucun ROI établi.

Astra est sollicité pour remonter source/clip/support/domain et recommander
le prochain discriminant minimal. La tentative de re-review Sol n'a pas
pu démarrer (agent thread limit) ; aucune approbation supplémentaire inventée.
Le code produit demeure inchangé. Pas de merge, globalGREEN ou W7complete.

## Reprise après fondation SolidRect — 4 octobre 2026

Produit867cd2146, HEAD documentaire5df3c9e8c22f5b5e4de9e23739113bb4a678b026.
Même fixture SHA b6206a00d47b37a8ebd85486741853798089bdbf18efdb8eea70e68bd85494c7,
11206bytes ; aucune attente ajustée. Classe entière rejouée par main,
wrapper/init figés, no-daemon/singlefork/240s, sans publication.

| Test/scènes | Résultat frais |
| --- | --- |
| Root seul, widths2/1, 4rendus chacun | PASS, 8buffers entiers natifs |
| Contrôles root du test layer | PASS, 8buffers entiers natifs |
| Root avec sibling filtré, widths2/1, 4rendus chacun | PASS, 8buffers entiers natifs |
| Plain layer, widths2/1, 4tentatives chacun | 8refus w6a.layer.unsupported_child, scope0/PATH/AA/sans filtre |

JUnit3tests :2PASS/1FAIL/0skip/0error. Les24positifs prouvent pixels
RGBA8/4096bytes, submission/completion, draw/pipeline, Render+Readback,
replay et Surfaces fraîches. Le test layer échoue uniquement au refus
attendu ; ses8tentatives n'ont pas produit de buffer natif. Le contrôle
filtré ne masque plus le défaut de scope.

Archive : /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/common-aa-path-resume-gate-20261004-1.
PID/PGID64832 vide après terminal ; exit1, aucun timeout/signal,
Gradle14s. Audit main intégral process/events/XML/stdout20696bytes/
stderr0, inventaire binary et ownPGID, puis postseal séparé exact.

| Artefact | Bytes/lignes | SHA-256 |
| --- | --- | --- |
| process.log |17939/189|0304ca8354251fc03272d3211087021ef8bb0f6b658776c840bd1f5a79413872|
| exit.json |111|7258e1eff1dc6c213cb8545621d0890b2994ac2c9aeb549f66e869b85e440d2a|
| events.jsonl |10767/6|f91b88bba40e82e68ceeab6cd998fe3e848c35631e1c9fb80035ed2a6fa570ad|
| JUnit XML |38748/57|21d98f9657f5dde085367fed820572221d3b3bdf05b3cd1b194232e0f4bb1ab4|

Seal7682fichiers suivis/protégés et workspace propre au plan :
767159fc872810e4c272285e8808fb8d69b67decbe001a5d6ad0059ec32e041b,
aucun absent ; gitlink séparé inchangé. 814PNG,559scores,1004références/
631statuses, wrapper/init et inverse protégés byteexact. Warnings
native-access/Unsafe/Gradle conservés ; aucun GPU skip.

Re-review Sol ciblée53f2121a2..6ac4fb193 : I1/I2/M1/M2 ADDRESSED,
C0/I0/M0. B25296 contrôlé statiquement, pas GREEN natif. Step3
discriminants autorisée sur branche locale codex/w7-common-aa-layer-source
depuis5df ; Step4 produit reste conditionnée à leur RED authentique.
La gate originale est gelée ; les discriminants vont dans une classe
publique compagnon. Publication toujours en attente d'accord explicite.
W7 non terminé, aucune nouvelle admission GM ou globalGREEN revendiqué.

## Discriminants RED qualifiés — 4 octobre 2026

HEAD3c2194175e6ff8a48a3e89c0244377bad9f4455d, produit867cd2146 inchangé.
Compagnon public W7CommonAaPathSourceDiscriminantsSurfacePixelTest :
SHA eca425d70d2e615acadfdc566779bbeccda6b5f7e808118b555fb4565315b1da,
23071bytes ; gate b6206 inchangée. Archive fraîche
/private/tmp/kanvas-w7-inverse-inventory.hbWqUb/common-aa-path-expanded-red-20261004-3.

JUnit9tests :4PASS/5FAIL attendus/0skip/0error. Les contrôles root,
filtered sibling et encoded root passent avec pixels entiers et replay.
Les huit négatifs propres (FILL/STROKE mask/image filter, STROKE shader,
PLUS, AA clip, fractional hard clip) passent avec refus précis, sentinels
inchangés puis récupération bleue native/replay même Surface. Encoded
layer refuse à unsupported.surface.composition.layer et récupère.
46buffers natifs complets sont qualifiés ;17refus sont enregistrés.

Les cinq méthodes restantes échouent à w6a.layer.unsupported_child,
scope0/PATH/AA/sans filtre propre. Les quatre nouvelles méthodes positives
arrêtent au premier rendu : les scénarios suivants alpha/order/hardclip et
la frontière B−1 restent UNOBSERVED, pas implicitement validés. B25296
reste uniquement dérivé/revu statiquement jusqu'au GREEN produit.

Les préparations précédentes ne sont pas des RED causaux : expanded-red-1
échoue à compiler trois constantes de fixture ; expanded-red-2 valide les
négatifs mais échoue au contrôle encoded root. Ce dernier enregistrait un
clip hard deux fois : Canvas le capture en Complex, refusé par l'admission
encoded existante. Le writer a retiré uniquement l'enveloppe save/clip/
restore redondante ; drawStroke garde son clip, admission/oracles inchangés.
Ces archives sont conservées, aucun produit n'a été modifié avant RED3.

Exit1/no timeout/signal/Gradle18s, ownPGID74344 vide. FULL audit log237lignes,
18events/messages/stacks,2XML/attributs/failures byteexact/stdout complets
15766+20696bytes/stderr0 et inventaire binary hashé. Une troncation du
message event4 relue ; PS sandbox EPERM suivi du contrôle readonly propre
escaladé. Postseal séparé exact7688fichiers/manifest
b83cc2144d736304b81a480b6a14aba9207d1daded67fbe9273426c2cc9b6a85,
plus PNG/scores/refs/protected/private inchangés.

| Artefact | Bytes/lignes | SHA-256 |
| --- | --- | --- |
| process.log |26791/237|47cb86755bfcee092d8425fc588b260b6999ee8d7f07d6d205ab0818e2478095|
| events.jsonl |22944/18|47e1274d1d161c4ce99b6ba8f3285eff928cd5aabd54fd536a8c858eae512559|
| exit.json |111|2427f1efacf8a09ed75b4817ca35bde17e64de7e1c16d58d5cd7358a07e49ec0|
| XML discriminants |30568|3b8220154b95c02a587584c92ef8c4915fd195a77a3bedcc03e32015a1e728c0|
| XML gate |38747|331334c71964337ffe4ca924cd73d60967a76e1afd81c2a9f80588bb39cc0988|
| binary/output.bin |36777|4184929e270588df436c989066c81ddb9c6b50b5469e9f23260ce95fa15a97e6|
| binary/output.bin.idx |170|6ae1bf6a60968389a3df941219640850b361960c041204d5bebf44bab920368d|
| binary/results.bin |31631|e9820e3b7fbaf612c7f246f14ca9159b0bdb7c01601520c1654da93149ff8c0e|

Step3 faite ; Step4 autorisée sous le contrat ordinary commun, sans
nouvelle lane ni effacement de filtre. Les deux fixtures sont gelées
avant produit. GREEN/context/review et corpus frais restent requis ;
aucun gain GM, publication, globalGREEN ou W7complete anticipé.


## Qualification focalisée et contexte — 4 octobre 2026

Produit source commun W6a f152690c3249a6a6b0223847e32310af2514a5035f0a2ff49717fe4ba32b5d5d / W4d 1fcb3a87283a58be8a97d8ee83271e3b2121eb32d60042ce4f59b3ed4f04ff62, depuis base5df3c9e8 ; gate b6206 préservée.

Premier GREEN :8PASS/1FAIL budget à25296. Diagnostic Astra statique indépendant :Solid16+tail-alpha16, layout composé32 ; dérivation initiale avait omis le second slot. Réouverture bornée explicite de la seule formule/owner du companion, ancien snapshot eca425 et raw run conservés. B25312, B−1W5g, relus Sol C0/I0/M0 AVANT run ; aucune scène/oracle pixel/tolérance/cap/budget produit changée. Companion actuel4907607ed56a89509d06d1665b786a93385d4f492764e0408672037ada54a5c2.

Run common-aa-path-green-20261004-2 :9/9PASS,0skip/error,108fullbuffers natifs,10refus enregistrés. B25312 passe les quatre rendus complets ; B−1W5g/sentinel/discard/recovery/replay PASS. Tous axes alpha source/restore/ordre/doublon/coordonnées/clip/négatifs/encoded/root/contrôle filtré PASS. Exit0/no timeout/signal/Gradle20s ; ownPGID84889 vide. Audit integral log/events/XML/stdout lossless/inventaires puis postseal séparé exact7694/8078c93d0b80a9961c1b985d7bfbad03d57ea966896baf609a6107522d1a6919. Archives sous /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/.

| Artefact GREEN2 | Bytes | SHA-256 |
| --- | ---: | --- |
| binary/output.bin | 62331 | 391f5402983e386d1c5e559ea2a238710f7ada0cedef402aae89ae4cc6931c1c |
| binary/output.bin.idx | 302 | b536822b8bfe1e93a84a0c9587b0aea89e1f47b93b99e9aa03d5ec7d11ca710e |
| binary/results.bin | 1272 | 2d5aaea3f5f26d3a9d6e90aea5bdf6d2ad0e871fd7b966e87b015d7ea3e1455c |
| events.jsonl | 5065 | b255c03e4a46dec6477dc1e30630c7c977eac9bf51f53f2b2ca3abaf71711e4f |
| exit.json | 111 | ecb5aa23459bf01f13217f8a2b0417e9defcf89e2eb699f4911b716b877a9b37 |
| process.log | 9409 | 64f1073444b5c38b0a6e24cff2350b8be1b0eb2f214e0a8e33ad0d74cdd27090 |
| xml/TEST-org.graphiks.kanvas.surface.W7CommonAaPathSourceDiscriminantsSurfacePixelTest.xml | 46824 | ce05847e20d7edc5d78ef07db1712b9228165e26791220cab53e8cc7ab3dca6e |
| xml/TEST-org.graphiks.kanvas.surface.W7CommonAaPathSourceSurfacePixelTest.xml | 17159 | edbb4899ef3c56f57c87ebcbc7efdd0b8d4115f75b0711a31dca8fb46b5aba54 |

Contexte common-aa-path-context-20261004-1 :120tests/119PASS/1FAIL historique/0skip/error. Deferred72, budgetrecovery10, restore14, AAFILL9, ordinary5 tous PASS. Bounds emptyCompositeClipDoesNotMaskUnsupportedBackdropAndSameSurfaceRecovers échoue comme le contexte SolidRect fix1 (event entier identique hors at). Les capability branches des anciens tests restent des limites et non de nouvelles preuves positives. Exit1/no timeout/signal/Gradle25s, ownPGID85381 vide ; audit integral et postseal séparé exact7695/1d4f87fc27f91351928bdfed1db5f0bb72a22ea892d4fdec9502ba7d8e7f976b.

| Artefact contexte | Bytes | SHA-256 |
| --- | ---: | --- |
| binary/output.bin | 0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| binary/output.bin.idx | 1 | 6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d |
| binary/results.bin | 17436 | 82000514ddbeda1ce4eafee5fb28587ec3836403feecfb7456cfa1d02941d905 |
| events.jsonl | 69651 | 6cc26f7aa8b39d0edcb5bc8e3c6ee3ef5587630f732b234787d003bde2e1d491 |
| exit.json | 111 | 1b650f78b466e5ee290c287573510248292224c82f49ae4ff59f4b08df9729c2 |
| process.log | 25401 | 15ee3e340daa16421a748377a95c9d8644b3d3bb393f47b8db833977ab4f58ec |
| xml/TEST-org.graphiks.kanvas.surface.W6aLayerBoundsSurfacePixelTest.xml | 3182 | c43bdbf5e2e1a550e1fb2f4467c7368d85338530f2e4e0fcdd549342fbdd304e |
| xml/TEST-org.graphiks.kanvas.surface.W6aLayerBudgetRecoverySurfacePixelTest.xml | 2084 | 598dc4746bffc083c77b935cd4e7b8eae90d00ff35b1d91f69c2ef470acf9696 |
| xml/TEST-org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest.xml | 2636 | f5e0253b0b43f18f72909963994b4c725812ab223996530fea925aa4d918eb31 |
| xml/TEST-org.graphiks.kanvas.surface.W7AaDeferredBlendSurfacePixelTest.xml | 10137 | 39e92527a87677f76a6c2ecef423e5a95796fa555ec3a7939a4d310c6dc5a526 |
| xml/TEST-org.graphiks.kanvas.surface.W7AaPathLayerSurfacePixelTest.xml | 1638 | 05de0108ef7dd207f5a13ceb9032c01047fdb3abfa62a45eb91b0245bb36bc90 |
| xml/TEST-org.graphiks.kanvas.surface.W7W6OrdinaryAaPathSourceSurfacePixelTest.xml | 1167 | 1f42366ee65bb223659e73c66163cb5b142bbd67329d91713932c8ae6ecb0620 |

Tentative suite complète du module common-aa-path-global-20261004-1 :limite240 atteinte, wrapper124/child143 ;727tests terminés (690SUCCESS/36FAILURE/1SKIPPED d'interruption). W5eDecodedImageSurfacePixelTest.formatsAlphaAndColorSpaceMatchOracle interrompu ; tests suivants non couverts. XML absents car non finalisés. Les36identités d'échecs sont déjà présentes dans la tentative globale antérieure,31textes/stacks exacts et5messages d'identité d'objets distincts ; aucun replay du parent courant, aucune garantie globale d'absence de régression. Audit log complet par delta lossless (2011lignes anciennes exactes+5littéraux),1454events entiers parsés/comparés et différences lues, binary inventoriés, ownPGID86185 vide ; postseal séparé exact7696/33c09db1d301dd2f68eb8d8a1eaf6fad432c0a4eae9e56a1971586d072ec5103. Tous noms d'échecs sont conservés dans le reçu contrôleur, pas omis du bilan.

| Artefact globale interrompue | Bytes | SHA-256 |
| --- | ---: | --- |
| binary/output.bin | 2252 | dc8ce37179881fca2c791446b8e178b3e13b9b9b5c34158080bc5170f683025e |
| binary/output.bin.idx | 180 | b32668efb2ec6f42472af463ed8ecd347aa2d6334738791dd84b8c22d3d06437 |
| binary/results.bin | 98300 | 7d464d8d169b3235c6167d5595247e1ebd040ea8fd038aff4689b774bff36877 |
| events.jsonl | 445517 | b3478d854635c839f28724eaa01c42f77dd69ffe37b17b61f979fa891705eaad |
| exit.json | 114 | 19711dfbdaf52a950125052078c8c463f2097d487e49060e29742365be8991b5 |
| process.log | 122861 | 3174e4e01e82d8ea00b049888e7facf8bbfe1e4ab367422ba7fda641fb20b4b8 |

Les références/scores/PNG/protected/helpers n'ont changé dans aucun run. Source GREEN focalisée, contexte nonGREEN ; review Task1/corpus frais/publication encore requis. Aucun gain GM ou W7complete déduit de ces tests.
