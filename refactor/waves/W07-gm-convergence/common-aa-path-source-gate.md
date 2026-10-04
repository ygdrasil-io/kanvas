# W7 — gate native source PATH AA : falsifier initial et reprise

État actuel : la gate fraîche après SolidRect qualifie root et contrôle,
avec layer RED attendu. La preuve initiale reste ci-dessous ; la reprise
ne la réécrit pas et ne prouve encore ni source layer ni nouveau budget.

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
