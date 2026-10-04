# W7 — gate native source PATH AA : extension non autorisée

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
