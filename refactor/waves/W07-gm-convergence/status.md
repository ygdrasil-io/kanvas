# W07 — diagnostic GM provisoire

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

Le témoin 7×7 `DirectTriangle` publie son budget indépendant
`B = 27 756` octets, obtenu sans rabais d'aliasing ou de cache : root RGBA8
196, staging readback 1 792 (sept lignes alignées à 256), layer RGBA8 196,
couleur AA 4× 784, `PathAaResolvedColor` 196, pools V/I/U 16 384/4 096/4 096,
uniforme solide 16. Cette dérivation correspond aux allocations déclarées de
la fixture (pas de D24S8, car `DirectTriangle` n'emploie pas le stencil) ; le
budget W6 reste checked-I64 et charge toute ressource déclarée, y compris les
staging et ressources des autres lanes, sans économie de lifetime.

Sur cet hôte, `GPUBackendRuntimeNative` publie sRGB 4× comme `{1}`. Le test
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
