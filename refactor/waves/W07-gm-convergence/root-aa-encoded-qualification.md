# W7 — qualification root Path AA encodée

2 octobre 2026. Lot sur #2435, base produit6f059f0dcf364cc351f984573f1fa9b7610ba337.
Source/tests qualifiés au snapshot privé28adb36d36038c6e9ab68334009c7b0d7c841335.
[Design](root-aa-encoded-design.md), [plan](root-aa-encoded-plan.md),
[snapshot frais complet](root-aa-encoded-28adb36d3.json).

## Résultat local, pas parité Skia

La capacité publique SRGB_ENCODED accepte une frame root contenant au moins
un Path AA : Paths non inverses à segments linéaires, FILL ou STROKE fini
positif BUTT/MITER, solide/SrcOver, sans effet, clip hard Rect entier ou absent,
CTM axis-aligned fini non singulier. Fond Path hard permis ; autres familles
par mélange et frame hard-only Path non ouvertes implicitement.
Les defaults LINEAR, les sources privées W4e/W6 et les GM restent inchangés.

MSAA4 retient les quatre états jusqu'au resolve final. Le domaine traverse
source, graphe et ressources ; clés, continuation, preflight et validation
native dérivent format/interprétation des mêmes facts authentifiés. La
capability RGBA8Unorm MSAA4/resolve utilise ses propres observations natives.
Aucun élargissement de seal, budget, cap, epsilon, enveloppe ou fallback.
Pas de nouveau helper géométrique produit hors math.

## Témoins natifs et review de tâche

Oracle indépendant à quatre masques, positions WebGPU épinglées et modèles
SrcOver/store existants, attentes calculées avant GPU. Contrôles LINEAR3PASS
et encoded3refus causaux avant source ; guards9refus causaux avant source.
Deux erreurs de compilation produit sont corrigées sans toucher les attentes
(local cast Kotlin stable et lookup dans la liste immutable des resourceFacts).

Après correction : **15/15 natifs PASS, XML15/0/0/0, Gradle0, sans skip/timeout**.
Noir/rouge/mixte R128G64B32A128 ; transparent/blanc ; masques répétés et
complémentaires ; RGBA/BGRA et targets explicites ; cache alterné ; vrais
Picture memory/archive ; chaque pixel producer validé avant snapshots
SOURCE_SPACE/full/subset et replay ; translation/clip device, tiny reciprocal
CTM et anisotropie ; exclusions/sentinelles/recovery ; budget exact
B26,880/B−126,879 et recovery empty à2,304bytes. Surface conservée/rejouée,
Render+Readback réels, zéro refus avant pixels. L'empty recovery seul autorise
zéro draw, sans faux travail.

Sol Task1/Task2 : spec conforme, quality Approved, aucun Critical/Important.
Minor warnings hérités conservés. L'identité disponible est Apple M2 Max,
nonfallback, JVM25.0.1/Gradle9.2.0/macOS26.6.2. Backend/driver et identités
d'attachments non exposés : CannotVerify, pas d'inférence « Metal car Mac ».
**Borne générale de précision native du resolve OPEN** : qualification empirique
des fixtures figées, pas une enveloppe conservatrice démontrée de tous les
inputs/backends. L'oracle exact ne contient pas d'erreur native de resolve.

## Contrôles historiques et suites non vertes

| Invocation | Résultat réel |
| --- | --- |
| W7SurfaceCompositionPixelTest | 23PASS, XML23/0/0/0, Gradle0 |
| W7MixedRootAaRectSurfacePixelTest | 6PASS1FAIL, XML7/1/0/0, Gradle1 |
| W7RootAaSurfacePixelTest | 10PASS, XML10/0/0/0, Gradle0 |
| W7AaPathLayerSurfacePixelTest | 9PASS, XML9/0/0/0, Gradle0 |
| :kanvas:test sans filtre, borne240s | 724START/END,686PASS37FAIL1interruption, wrapper124/enfant143, XML absent |
| :gpu-plan:test sans filtre | compileTestKotlin FAIL,30diagnostics,0tests, Gradle1 |
| :gpu-renderer:test sans filtre | compileTestKotlin FAIL,17diagnostics,0tests, Gradle1 |

Les49contrôles donnent48PASS1échec Picture hérité, message/type/stack
strictement égaux à l'archive parent ; aucun changement d'attendu.
La globale retrouve les37mêmes échecs :32messages raw identiques,5ne
diffèrent que par des adresses RuntimeEffect/Diagnostics,37stacks égaux.
Un test decoded-image formatsAlpha... est interrompu plutôt que le cubic
historique : SUCCESS→SKIPPED pour le premier, cubic non atteint.
Les cas W7 sont qualifiés par les sélections indépendantes, pas par cette globale.

Les suites unitaires des deux modules sont bloquées par des tests obsolètes :
PictureAggregateOwner/arguments RenderGraph, material nullable, ancienne clé
W3structuralPipelineKey et branche nativeNoOp manquante. Les5blobs de tests
et signatures API concernées sont identiques à6f. Aucune qualification unitaire
ne découle de leur compilation refusée ; dette de vérification OPEN, merge non
qualifié. Pas de test d'infrastructure ajouté, modifié ou écarté.

## Corpus frais, strictement inchangé

Registre631, éligibles443, font133/codec54/quarantaine1 conservés. Tranches
0..607 /607..608 /608..631 aux sorties Gradle0/1/0,136s/39s/11s, sans timeout
externe. vertices607 garde son watchdog render30s/Java124 et aucune completion
marker dans cette tranche ; il est compté, pas exclu. Les autres tranches
ont leur completion marker et les631indices uniques sont complets.

**217rendus/194comparés,47≥99%,63≥95%,médiane77.45815728081598%**.
Outcomes éligibles194compared/175render_failed/50setup_failed/
15rendered_uncompared/8reference_dimension_mismatch/1timeout.
Les631fiches sont identiques au [parent](path-ctm-4e4b699a6.json) en excluant
uniquement elapsedMs/renderMs :18invariants, présence des champs,
diagnostics, outcomes,217hashes RGBA et métriques inchangés. Les7invariants
de run sont identiques ; seul rendererCommit et les dates/temps reflètent
la nouvelle exécution. Aucun gain GM ni perte d'admission revendiqué.
Pas de delta visuel à inspecter ; aucune régénération PNG/scores/dashboard
du dépôt justifiée. Références, GM et compositionDomain inchangés.

### Résumés agrégés des diagnostics

L'égalité des 631 fiches hors elapsedMs/renderMs ne signifie pas que les
résumés agrégés sont égaux. Les seuls deltas de summary sont
sumCaseElapsedMs154921→158013 et firstFailureDiagnostics. L'histogramme parent
correspond exactement aux 234 eligible avec diagnostic présent (115 clés) ;
l'histogramme frais correspond exactement aux 226 eligible !rendered (111
clés), formule du summarize-parity.mjs committé. Le helper est inchangé depuis
parent4e4b ; aucune affirmation n'est faite sur la génération de l'artefact
historique.

Clés/occurrences agrégées retirées :
route:destination-read:DrawVertices:9: gpu-copy-then-formula (1) ;
GM=256x128, reference=800x1000 (4) ; GM=960x720, reference=960x1200 (1) ;
GM=720x740, reference=980x740 (1) ; GM=128x128, reference=800x1000 (2).
Ce sont 9 cas inchangés rendered=true, pas des diagnostics perdus.
unknown:1 est ajouté pour le timeout vertices607 inchangé, sans diagnostic.
Tous les diagnostics par fiche sont présents et inchangés. Cette différence
de convention de résumé n'indique aucun gain ni aucune perte du renderer.

Snapshot frais SHA256d68202a79d5e03f45762860e125dea7694ed71c7ec8b0bf02fca6a2b58e2d030.
Journaux privés root-aa-encoded-corpus.eXKR8e/slice-* conservés sous
/private/tmp/kanvas-w7-inverse-inventory.hbWqUb.
Tous les runtimes : terminal original, puis audit séparé des messages,
counts, XML ou JSONL, process.log et exit avant le suivant.
La première vérification textuelle PARITY différait uniquement pour une notation
scientifique JVM/Ruby ; le nombre identique et la ligne entière sont audités,
sans rerun natif ni changement de données.

## Livraison et dettes

Revue finale Astra : C0/I0/M2, draft acceptable, merge/W7 non qualifiés.
Une seule vague documentaire ferme M1 ; contre-revue ciblée Sol : M1 ADDRESSED,
aucun nouveau C/I/M, M2 warnings hérités DEFERRED.
Draft [#2436](https://github.com/ygdrasil-io/kanvas/pull/2436) publiée/rattachée sur#2435,
base codex/w7-scaled-stroke-diagnostic/6f059f0dc et head produit initial
7d4a1b7f58069a4e2cf7370719d35f1b6b45bc18 distants vérifiés, description exacte.
Le code/tests publié est byte-identique aux blobs qualifiés28adb36d3.
Les commits ultérieurs de suivi ne changent pas ces blobs. CI non inspectée.
Ni merge, ni globale verte, ni W7 clos.
AA sampling/placement, précision du resolve, consommation GM encoded,
Picture/refus hérités, RRect/ComplexClip2, inverse/filter, crbug résiduel,
provenance/taille des références et légère régression pathops restent OPEN.
