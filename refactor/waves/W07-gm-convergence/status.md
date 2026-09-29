# W07 — diagnostic GM provisoire

PR draft empilée : [#2410](https://github.com/ygdrasil-io/kanvas/pull/2410),
sur la PR W6 [#2409](https://github.com/ygdrasil-io/kanvas/pull/2409).

## Lot pointillés — 29 septembre 2026

Renderer `5f971f750`, pour une PR draft empilée sur #2412 : l'égalité des
pointillés immuables est réparée dans `math`, sans desserrer les seals.
Le [snapshot final](dash-5f971f750.json) conserve **164/443 rendus**, tous
identiques pixel à pixel à #2412, **142 comparaisons** et **26 cas à ≥99 %**.
Les 631 identités, scopes, références, seuils et trois timeouts sont inchangés.

L'amélioration de précision AA a été **retirée après avis Astra** : malgré
le gain sur `circle_sizes`, elle faisait perdre `parsedpaths` et
`perspective_clip`. Le [plan et le diagnostic](aa-dash-repair-plan.md)
préservent l'expérience ; **le défaut AA reste ouvert**.

**77 tests publics ciblés et 476 tests math geometry passent** sur le code
final, Gradle 0. La tentative de suite Kanvas complète est inachevée : 39 échecs observés,
puis arrêt d'un calcul long dans la preuve CPU d'un test de gradient W5d.
Les [limites de validation](pilotage.md#validation-du-lot-et-limite-de-la-suite-complète)
sont explicites. W7 et les gates W6 ne sont pas clos ; aucune merge
readiness n'est revendiquée.

## Lot standalone rect/path — 29 septembre 2026

PR draft [#2412](https://github.com/ygdrasil-io/kanvas/pull/2412),
renderer `718445e6e`, empilée sur #2411 : **164/443** rendus (+41),
**142** comparaisons (+37), **26** cas à ≥99 % de pixels ±2/canal (+6).
Les 123 anciens rendus restent disponibles, 118 identiques bit à bit.
Les références, scènes, seuils, exclusions et trois timeouts restent inchangés.

Le [bilan détaillé](pilotage.md#lot-standalone-rectpath--29-septembre-2026)
documente les cinq anciens rendus modifiés, notamment le recul de
`circle_sizes`, et les deux nouveaux rendus à 0 % liés à des ports non fidèles.
La médiane appariée des 105 anciennes comparaisons reste 54,64 %.
**69 tests ciblés passent**, mais le test historique de pointillé à phase
négative reste en échec, reproduit avec le routage historique et diagnostiqué.
Le [plan](stroke-routing-plan.md) et le [snapshot](strokes-718445e6e.json)
conservent la preuve. W7 reste ouvert, sans revendication de parité globale
ni de merge readiness.

## Pilotage et mesure fraîche — 29 septembre 2026

PR draft empilée : [#2411](https://github.com/ygdrasil-io/kanvas/pull/2411)
sur #2410.

Le [pilotage](pilotage.md) remplace le comptage de rendus comme indicateur
unique : **123/443** GMs éligibles produisent une image, **105** peuvent être
comparées aux références actuelles ; **20** de ces comparaisons atteignent
99 % de pixels à ±2 par canal. Les défauts de port et de dimensions restent
visibles. Les scores historiques et les références ne sont pas modifiés.

Les suites publiques W6/W7 disposent explicitement du runtime GPU à leur
fin : **49/49 tests passent, Gradle exit 0**. Le crash `133` décrit ci-dessous
est historique pour ces deux suites. W7 et les gates W6 ne sont pas clos.

## Checkpoint AA W6/W7 — historique

Les phases `PathRenderPass` AA W6 sont maintenant reconnues par leur autorité
`W4dAaSource`, séparément des anciens seals de paire stencil W4c/W4d. Le
`PreparedGPUFrame` vérifie l'usage du D24S8, le `load/store` producteur/cover
et l'unique operand emprunté. Le validateur natif garde l'égalité stricte des
indices de vues stencil, contrôle leur identité entre les deux phases, ainsi
que le `resolve`, la cible couleur et leurs opérations de charge. L'autorité
de source AA contrôle désormais l'égalité exacte du `load/store` sémantique,
et non seulement sa nullité. La phase directe reste sans profondeur.

Sur cet hôte, les **9/9 assertions JUnit W7 AA** passent, dont les quatre
témoins stencil/alpha auparavant bloqués ; les deux contrôles publics W6
adjacents passent aussi (**2/2**). `:gpu-renderer:compileKotlin` et
`:kanvas:compileTestKotlin` passent. **La tâche Gradle `:kanvas:test` reste
rouge** : l'exécuteur natif quitte avec `133` après les assertions. Aucun
inventaire GM, score, PNG ni dashboard n'a été régénéré ; ces témoins locaux
ne ferment pas W7, les gates W6 2A1/2B ni la question de conformité ISO.

## Checkpoint natif sRGB 4×/resolve — historique

Une sonde autonome temporaire, sur la session wgpu4k de cet hôte (Apple M2 Max),
a exécuté deux fois un rendu `RGBA8UnormSrgb` 4× avec resolve sRGB 1×, lecture
du pixel rouge `[255, 0, 0, 255]` et aucune erreur de validation. Elle a réussi
avec et sans attachement `Depth24PlusStencil8` 4×. Cela prouve ce couple
format/resolve sur cette session, pas tous les adaptateurs ni la sémantique
stencil complète de W7.

Le worktree courant contient une détection une fois par session GPU : la table
annonce sRGB 4×/resolve seulement si les deux rendus natifs, la complétion de
queue, les scopes de validation et les pixels lus réussissent ; sinon elle
conserve `{1}` sans resolve. La relecture Astra a fait corriger le contrôle du
`Result` de queue et l'unicité du `popErrorScope`. L'ancien test de table `{1}`
constante a été retiré ; la preuve de comportement reste dans les tests publics
`Surface`, sans nouveau test d'infrastructure. La sonde et ce relevé sont
inclus dans la PR draft #2410 ; ils ne ferment pas les gates globaux.

L'activation a révélé et permis de corriger des seals W6/W7 jusque-là masqués
par le refus de capacité : opérations V/I du `Path` AA, classification de
`PathAaColorComposite`, comptage d'un draw stencil public une seule fois, et
absence de template de blend couleur pour le producteur stencil. Le témoin
`DirectTriangle` a maintenant un budget exact **B = 26 980 octets** : root
196, readback 1 792, layer 4×4 64, AA 4× 256, resolve 64, pools V/I/U
16 384/4 096/4 096, uniforme W4d 16 et uniforme de source solide 16. La
borne B admet et B−1 refuse avant écriture du sentinel ; la même `Surface`
reste réutilisable après refus.

Rejeu intermédiaire : W7 JUnit **9 tests, 5 passés, 4 échoués** ; les quatre
échecs stencil/alpha atteignent `invalid.preflight.prepared_frame` sur le seal
historique `Prepared path seal, unified pair, writable attachment use,
load/store, and native operand must agree exactly`. Les deux contrôles W6
adjacents passent (JUnit **2/0/0/0**). La tâche Gradle sort toujours 1, avec
l'exécuteur natif `133` après les assertions. Les pixels W7 directs, l'ordre
des enfants, la translation et B/B−1 étaient positifs sur cet hôte. Le
checkpoint plus récent ci-dessus traite ce gate de seal préparé et celui des
operands natifs ; **W7 n'est pas terminé ni merge-ready**.
Fonts, codecs, `jpg-color-cube`, renders/scores/dashboard GM et gates W6
2A1/2B restent hors de ce checkpoint.

## Checkpoint des operands W6/W7 après relecture Astra

Le commit `bd5eba11a6fc42c064c8279f2e3b788c77654509` rétablit la clé D24S8
des passes stencil W6 génériques `StencilGeometryProducerV3`/`StencilCover` et
reconnaît le `depthStencil` scellé des `PathRenderPass` AA W7 dans le seal des
operands. Il ne modifie ni le format couleur ni la table de capacités GPU.
La relecture Astra ciblée ne relève aucune nouvelle régression dans ce diff.

Le témoin public `path fill stroke and hairline retain stencil through a
translated layer` échouait avant correction à `Surface.render()` sur
`invalid.preflight.encoder_lowering` (clé D24S8 empruntée absente). Après
correction, ses pixels passent, ainsi que le contrôle W6 inverse-even-odd
et deux témoins W7 de refus de capacité : XML JUnit W6 `2/0/0/0`, W7
`2/0/0/0`. La tâche Gradle demeure en échec car l'exécuteur natif quitte
avec le code `133` après les assertions. Les témoins W7 atteignent encore
`w4d.general.texture-sample-support-unavailable` : ils ne prouvent ni pixels
AA positifs ni budget B/B−1 sur ce runtime. Une sonde native sRGB 4× + resolve
serait un travail diagnostique distinct ; aucune capacité n'est inférée du
seul matériel Mac et aucune conformité ISO n'est revendiquée.

## Portée et preuve

Ce relevé ouvre le diagnostic W7 sur le commit `b99e321c6bf6b7776fefd69a56fbe7439cc7a9cb`, empilé sur W6 `fc6e57209a69de43f57854886853714ccb1cad58`.
Le commit W7 adapte seulement trois fixtures de lighting à l'API 3D déjà publiée ;
`:integration-tests:skia:compileTestKotlin` passe et la revue Sol du diff ne relève
aucun problème. L'inventaire a été généré par
`generateSkiaGmInventory` avec une sortie non suivie dans
`/private/tmp/w7-provisional-source-inventory-b99e321c6b.json`.
Ni rendu de référence, ni score, ni dashboard n'ont été régénérés.

Le relevé reste **provisoire** : les gates W6 2A1/2B ne sont pas closes et
`jpg-color-cube` reste en quarantaine `quarantined-resource-limit`, filtrée
avant setup et rendu. Fonts et codecs sont exclus du périmètre. Les scores
enregistrés sont historiques ; l'audit `strict=true`, `orphanCount=0` vérifie
seulement leur cohérence de registre, pas la similarité des pixels actuels.

## Checkpoint Task 4 — budget AA et refus hors route

Le commit `b8b035b329163dba37f21c6de7a85d4cd851e383` ajoute les témoins publics
W7 du budget et des filtres, ainsi que le refus W6 explicite qui empêche qu'un
`Path` AA filtré dans une layer perde sa provenance en
`w6a.layer.unsupported_child`. Image et mask filter retournent maintenant
`w6a.layer.unsupported_spatial_filter` avant que W6b ne retire leur payload
pour une voie enfant W4d. Cela ne constitue ni une route image-filter
`ResolvedColor`, ni une implémentation de `ResolvedCoverage` ; ces deux travaux
restent différés.

L'estimation initiale `B = 27 756` du témoin 7×7 `DirectTriangle` traitait
à tort la layer et la source AA comme des surfaces 7×7, et omettait le second
uniforme de 16 octets. Le checkpoint natif ci-dessus corrige ces dimensions
aux bornes conservatrices 4×4 du triangle et établit **B = 26 980** sans
rabais d'aliasing ou de cache. `DirectTriangle` ne déclare pas de D24S8 ; le
budget W6 reste checked-I64 et charge toute ressource déclarée, staging et
ressources des autres lanes inclus, sans économie de lifetime.

Lors de ce checkpoint antérieur, `GPUBackendRuntimeNative` publiait sRGB 4×
comme `{1}`. Le test
public observe donc d'abord exactement
`w4d.general.texture-sample-support-unavailable` et ne peut pas atteindre
l'allocation native ni établir ici que B admet et B−1 refuse. L'oracle
capable-backend conserve ce contrôle B/B−1, son sentinel atomique et la
réutilisation de `Surface`, mais aucune réussite B/B−1 n'est revendiquée sur
cette machine. Les compiles `:gpu-plan:compileKotlin`,
`:gpu-renderer:compileKotlin` et `:kanvas:compileTestKotlin` passent. Le
sélecteur filtre passe avec Gradle 0 ; le sélecteur B passe côté JUnit mais
Gradle termine 1 car le processus natif quitte 133 après l'assertion.
Les sept autres sélecteurs publics W7 (triangle, concave/even-odd/stencil,
ordre, alpha et translation) ont aussi chacun une assertion JUnit passée puis
ce même exit natif 133 : cette observation ne rend pas la suite Gradle verte.

Un inventaire frais a été produit uniquement dans
`/private/tmp/w7-task4-gm-inventory.json` par
`generateSkiaGmInventory -Pgm.inventoryOutput=...` (succès, 4 min 10 s), sans
PNG, référence, score ni dashboard. Il compte 631 GMs, 443 éligibles, 124
rendables et 262 échecs de rendu. La sonde antérieure de 44 chemins AA n'a pas
conservé ses identités, donc aucune comparaison par GM ne serait honnête. Le
relevé frais contient encore 52 `w6a.layer.unsupported_child`, sans permettre
d'identifier ces GMs aux 44 historiques, et observe aussi
`imagefilters_xfermodes` sur le nouveau refus
`w6a.layer.unsupported_spatial_filter`. Cette observation GM et le témoin
`Surface` n'autorisent aucune promesse de réparation globale. Les gates W6 2A1
(leases/programmes natif) et 2B restent ouverts ; aucune conformité ISO W7
n'est déclarée.

Après la revue Sol, le gate D24S8 est rendu dépendant de la stratégie dans la
seule source W6 `allowAaColorSource` : `DirectTriangle` n'exige plus une
capacité D24S8 qu'il ne déclare pas, tandis que `StencilCover` exige toujours
D24S8 4× et son opération stencil. Le W4d AA autonome conserve son préflight
et budget depth conservateurs. Le témoin B vérifie aussi désormais la
récupération de la même `Surface` après le refus sRGB 4×, avec un draw
hard-edge et ses pixels/scopes ; cela ne transforme pas cette preuve en B−1.

## Résultats observés

| Mesure | Inventaire W0–W2 suivi | W7 initial | W7 après correction lifetime |
| --- | ---: | ---: | ---: |
| GMs enregistrées | 631 | 631 | 631 |
| Éligibles | 450 | 443 | 443 |
| Exclues codec / font / quarantaine | 54 / 126 / 1 | 54 / 133 / 1 | 54 / 133 / 1 |
| `Surface.render()` tenté | 379 | 386 | 386 |
| Rendu disponible | 83 | 89 | 124 |
| Échec terminal de rendu | 296 | 297 | 262 |
| Setup échoué, tous scopes | 75 | 60 | 60 |
| Éligibles sans tentative | — | 57 | 57 |

Sept GMs sont passées d'`eligible` à `excluded-font` ; ce changement de
dénominateur doit rester visible dans toute comparaison. Par identité de GM,
46 GMs auparavant non rendues rendent désormais, mais 40 auparavant rendues
ne rendent plus. Ce n'est donc pas une progression monotone, malgré le gain
net de six rendus. Parmi les 40 pertes, 25 échouent sur l'invariant de durée
de vie des ressources.

## Premiers groupes de causes

Les nombres ci-dessous décrivent l'inventaire W7 **initial** et comptent les échecs terminaux des GMs éligibles, sauf
la dernière ligne, qui concerne le setup. Une GM n'est comptée qu'à son premier
diagnostic ; ce regroupement n'établit pas encore la cause racine.

| Diagnostic initial | GMs | Lecture provisoire |
| --- | ---: | --- |
| `w6a.layer.unsupported_child` | 54 | Admission des enfants layer/source/geometry à étudier comme axe transversal. |
| `Resource lifetime must be non-empty` | 31 | Invariant `PlanResource.of` ; 25 anciennes réussites perdues, priorité de diagnostic. |
| `geometry.path.fan_budget_exceeded` | 23 | Limite de topologie path, à distinguer d'une erreur de géométrie. |
| `scalar_aa_not_promoted` | 18 | Promotion de couverture AA non admise. |
| `runtime_effect.unregistered_semantics` | 12 | Contrat d'enregistrement des effets runtime à classifier. |
| `unsupported.pipeline.capability_missing` en setup | 14 | Capacité GPU et taille de ressource à distinguer d'un manque de route. |

Les 57 éligibles sans tentative sont des échecs de setup, dont des stubs
explicites et des contraintes de taille GPU. Aucune gate « 100 % exécutées »,
« 95 % conformes » ou « zéro refus non classifié » ne peut être revendiquée
sur ce relevé. Aucune mesure fraîche de similarité n'a été faite.

## Ordre de triage proposé

1. ~~Tracer les 31 lifetimes vides jusqu'au producteur du `PlanResource`.~~
   Corrigé et mesuré ci-dessous ; conserver un test pixel représentatif.
2. Décomposer les 54 refus `w6a.layer.unsupported_child` par type exact
   d'enfant et contrat W6 ; ne pas élargir l'admission à l'aveugle.
3. Distinguer les limites explicites de ressources et les stubs des manques
   sémantiques réutilisables (path, AA, materials, runtime effects).
4. Après fermeture des gates W6 et stabilisation du périmètre, produire une
   nouvelle baseline de rendus/scores et seulement alors mesurer la conformité
   pixel, la colorimétrie, les meshes et les combinaisons rares.

Ces étapes constituent un triage, pas encore un plan d'implémentation approuvé.

## Correction bornée de la durée de vie W5e

Le diagnostic a localisé les lifetimes vides dans `RenderGraph.issueW5e` :
le wrapper W5a composite ne porte aucune passe, alors que ses lanes en portent.
La durée de vie des images décodées utilise désormais la somme I32 vérifiée
des passes de ces lanes (et garde le nombre de passes direct hors composite).
Dans `FrameSourceLayoutV4.prepareImageFrame`, le pic natif W5a est calculé
explicitement à partir de sa géométrie, des stops et du bruit ; le budget
extérieur W5e conserve intégralement images, runtime storage et uniformes.
Les invariants de `PlanResource.of` et l'égalité de budget du lowerer W5a
restent stricts.

Un test de pixels public sur `bitmap_premul` a échoué avant le correctif sur
`Resource lifetime must be non-empty`, puis a rendu les pixels de référence
après celui-ci, avec cinq dispatches, zéro refus et aucun diagnostic.
L'inventaire frais, écrit hors dépôt dans
`/private/tmp/w7-post-lifetime-inventory.json`, mesure 124 rendus disponibles
contre 89 avant correction : 35 gains, aucune perte. Les 31 refus de lifetime
ont disparu ; quatre autres GMs auparavant refusées sur
`invalid.material.image.contract` rendent aussi. Ce relevé ne mesure toujours
pas la similarité des 35 nouveaux rendus et ne régénère ni scores, ni
références, ni dashboard. Les gates W6 demeurent ouvertes.

La vérification ciblée `:integration-tests:skia:test` du GM passe. Trois cas
publics image, noise/gradient/image et runtime passent également leurs
assertions, mais leur processus `:kanvas:test` quitte avec le code natif 133
après les tests. Le cas public `sharedImageKeepsClampMatrixOrderAndMixedFrameStorage`
échoue sur `failed.frame-coordinator.preflight` ; le même diagnostic et la même
sortie 133 ont été reproduits isolément sur le HEAD W7 avant cette correction.
Ces résultats ne constituent donc pas une suite Kanvas verte et ce problème
préexistant reste distinct du correctif de lifetime.
