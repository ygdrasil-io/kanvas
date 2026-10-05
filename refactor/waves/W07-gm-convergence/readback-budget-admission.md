# W7 — admission readback indépendante du warmup

3 octobre 2026. Branche `codex/w7-readback-budget-admission`, base
`60ce1489115fbed58804f8a91aace4292c406086`, parent draft
[#2443](https://github.com/ygdrasil-io/kanvas/pull/2443).
Qualification ciblée : aucun merge, GREEN global ou clôture W7.

## Défaut reproduit et décision architecturale

Un rendu public solid 3×3, budget fixe 888, échouait après deux warmups
3×3 rouge puis 2×2 bleu. Le même rendu sur la même Surface réussissait
au replay ; l'ordre inverse passait immédiatement. Les pixels attendus
sont des buffers littéraux opaques, indépendants du renderer et des PNG.

Les traces temporaires établissent le mécanisme sur ce témoin : le pool
choisit un staging réutilisable de 768 bytes et compte zéro nouvelle
allocation. Le shared LRU purge justement ce candidat. Le retry doit
alors allouer 768 bytes, mais 512 bytes encore libérables restent résidents :
1316 > 888. Le provider s'arrêtait après ce seul retry. Un replay public
purgeait les 512 bytes restants et réussissait.

Le provider recalcule désormais l'admission après chaque purge qui
diminue strictement les bytes physiques résidents. La boucle s'arrête à
l'acceptation, à un autre refus ou dès qu'aucune purge autorisée ne progresse.
La résidence décroissante et les candidats existants finis bornent les
itérations ; aucun retry temporel, compteur arbitraire ou budget augmenté.

Seul `GPUConcreteResourceProvider.kt` change côté produit. Le helper privé
retourne un Boolean de progression réelle ; le shared LRU, les calculs
BigInteger, catégories, générations, pools et règles de lifecycle restent
inchangés. Readback n'évince que `Releasable` ; scratch conserve ses états
`Available/CompletedAvailable` et son admission à un retry. Rien de Submitted,
mapping-owned ou Quarantined ne devient libérable. Une réservation finale
acceptée reste journalisée une seule fois. Aucun nouveau diagnostic ou API.

Protéger le candidat via une nouvelle identité d'admission pool/provider
aurait nécessité un changement plus large ; le correctif ciblé suffit au
défaut démontré. Une purge/disposal dans les fixtures ou un budget relevé
aurait effacé le contrat : ces options n'ont pas été appliquées.

## Qualification native

| Exécution | Résultat | Portée |
| --- | --- | --- |
| Témoin avant correctif | 1 PASS / 1 FAIL | Premier rendu larger-first refuse ; replay observé, échec non masqué |
| Témoin gelé + contrôles après correctif | 16/16 PASS | Warmup2, Encoded4, Tiny3, Bitmap3, ChildPort3, ChildCausal1 |
| Contexte public original | 48 PASS / 4 FAIL sur 52 | Composition25/25 ; ImageShader23/27 |
| Nettoyage cosmétique du témoin | 2/2 PASS | Suppression de deux `!!` inutiles, aucun autre octet changé |

Dans les deux ordres, les huit buffers warmup/premier rendu/replay ont leurs
pixels complets attendus, 1 dispatch, 0 refus, 1 draw call, 2 pipelines et
aucun diagnostic. SHA RGBA : rouge3×3
`1d5d8d0381af6e24a87dac11b8f882a96a22e4215cadd2b282a24642662cc0d8` ;
bleu2×2
`d37a4c2900bb85dfd24592c0b52e8dcd72e0ea45c47fa2db0cf1c4c132c80c66` ;
vert3×3
`12d7fb01d772ff6956aafcf092bb374eae554678621ed33a2a26aea23187e410`.

Le test inchangé `encodedPlainLayerBudgetIsExactAndOneByteLessRecovers`
passe dans la sélection originale des 52 méthodes, non uniquement isolé :
B=888 accepté et replay identique ; B−1=887 refuse avec le diagnostic attendu,
préserve le sentinel puis récupère. Le gap de ce contexte est requalifié.
Le précurseur exact de l'ancien run complet n'a pas été tracé ; le mécanisme
causal est établi sur le témoin minimal, sans prétendre reconstruire tout
l'historique ni garantir tous les ordres possibles du corpus.

Les quatre FAIL hérités restent explicitement ouverts :
`rrectImageShaderRetainsExistingPublicRefusal`,
`pathHairlineImageShaderRetainsExistingPublicRefusal`,
`pathStrokeImageShaderRetainsExistingPublicRefusal`,
`verticesImageShaderRetainsExistingPublicRefusal`.
Ils attendent une exception qui n'est plus émise. Leur reproduction sur
baseline est conservée dans la qualification du parent ; aucun skip ou
changement de contrat artificiel dans ce lot.

## Contrôles, suivi et limites

Témoin RED puis GREEN gelé :
`623ef512a138454ce6ece0e77e30f8c6880127fdd02bcc2446d3472a6bcac49c`.
Version finale après les deux suppressions `!!`, requalifiée :
`ac4948e92cb6cf9a9d9c420428966787c1699e58b49dc7828d87f70da0dc1005`.
Provider qualifié :
`00f15cc01eb37afcc750a0ca847543c7701367c638c04a07450523ef84ca0333`.
Les deux traces ont été retirées byte-identically avant tout correctif.

Chaque run est séquentiel, borné à 240 s ; aucun timeout, signal, skip ou
erreur de test. Le run 52 termine exit1 en 2m22s à cause des quatre FAIL,
pas à cause d'un blocage. Les autres runs après correctif terminent exit0
en 29s et 18s. Inventaires complets, process/exit, tous les events/stacks,
XML stdout/stderr audités, puis post-seals séparés sur 66 chemins et artifacts.
Ces archives et le reçu SDD sont locaux et non versionnés : un clone ou la
PR seuls ne les fournit pas. Le reçu durable compact ci-dessous est versionné
et ne remplace pas les archives brutes complètes.

Archives privées conservées sous
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb` :
`readback-budget-warmup-1`, `readback-budget-warmup-trace-1`,
`readback-admission-green-1`, `readback-admission-regressions-1`,
`readback-admission-cleanup-1`. Reçu détaillé :
`.superpowers/sdd/readback-admission-progress-60ce-plan/controller-evidence.md`.

Les 814 PNG et les 559 scores restent inchangés ; aucun gain de parité Skia
mesuré ni nouveau relevé agrégé. La propriété de ressources non libérables
est vérifiée par les filtres existants et la condition de progression,
sans nouvelle injection de lifecycle ni test d'infrastructure.
Les deux warnings non-null nouveaux ont disparu lors de la recompilation ;
warnings JVM/LWJGL/Gradle hérités toujours visibles, sortie pristine non revendiquée.
Pas de suite bare globale : la qualification reste ciblée et les dettes globales
précédentes demeurent. Fonts, codecs externes et `jpg-color-cube` exclus ;
géométrie math I/F32/64 inchangée.

Plan exécuté : témoin public RED → trace causale/restauration → correctif
provider minimal → 16 contrôles GPU → contexte52/B−1 → nettoyage/requalification
→ relecture indépendante et publication draft stackée. Les étapes de
publication/relecture sont suivies par le ledger et la PR, pas assimilées
à une clôture W7. Prochains écarts : contrats W5e hérités, fidélité des GMs,
AA/couverture/flous et provenance des références déjà suivis dans le pilotage.

## Reçu durable des runs natifs

Ce reçu versionné conserve les empreintes des fichiers de preuve et les
résultats synthétiques. Les archives brutes intégrales, sorties standard/erreur,
événements et stacks restent locales, non versionnées, et ne sont pas reproduites
ici. Les fichiers listés sont identifiés par leur nom relatif dans l'archive.

| Portée | Résultat audité | Fichier | Octets | SHA-256 |
| --- | --- | --- | ---: | --- |
| Contrôles core | 16/16 PASS | `events.jsonl` | 8945 | `310fba36e6dd00cc8567c3b1c3ddc942ca07873c9db6a72c26d58de208fe963b` |
| Contrôles core | 16/16 PASS | `exit.json` | 111 | `90e3e7200bf5aa9f9c26f71fe3c4a7c55e8c67ee958729f76491647fd6a1fd62` |
| Contrôles core | 16/16 PASS | `process.log` | 15364 | `28e295fc166912226a9413b70a92fa4bd4cc444e9e9de31b71d79e55624ed202` |
| Contrôles core | 16/16 PASS | `xml/TEST-org.graphiks.kanvas.skia.W7BitmapRectSourceSurfacePixelTest.xml` | 2001 | `e193703ef3f255e0ab4e0a0168517f262f5b185f12a57c9857c3fd812240c4ed` |
| Contrôles core | 16/16 PASS | `xml/TEST-org.graphiks.kanvas.skia.W7ChildSamplingCausalSurfacePixelTest.xml` | 4715 | `c48d7869382cdb7d706c943b3e918c0f67a8ca60782611d79ec85d881da023c4` |
| Contrôles core | 16/16 PASS | `xml/TEST-org.graphiks.kanvas.skia.W7ChildSamplingPortSurfacePixelTest.xml` | 8797 | `9f4546fd1c479442ff65f8c7e8a5ab56d91bed769b619a479d7c4a8e3116c2e6` |
| Contrôles core | 16/16 PASS | `xml/TEST-org.graphiks.kanvas.skia.W7EncodedImageShaderSurfacePixelTest.xml` | 70999 | `eef2da60696a6aa5e97e7cbffa8158559113120a2b1e8f6ed7b66508764831e6` |
| Contrôles core | 16/16 PASS | `xml/TEST-org.graphiks.kanvas.skia.W7ReadbackBudgetWarmupSurfacePixelTest.xml` | 3210 | `2ece6e6c834201c58ca6b84b125273197d16bcfdba2d43a8c0f34bf30f0a142e` |
| Contrôles core | 16/16 PASS | `xml/TEST-org.graphiks.kanvas.skia.W7TinyBitmapSourceSurfacePixelTest.xml` | 2517 | `d3815352deb9eb50d34a66e1ac0a9b2a594aa14774d95e5f1478073f042f4a9d` |
| Contexte public | 48/52 PASS, 4 FAIL hérités | `events.jsonl` | 32843 | `68a9d4e77d74c21acb1e38e3134d235afaef9dab07aa332213e0d534e78baafb` |
| Contexte public | 48/52 PASS, 4 FAIL hérités | `exit.json` | 111 | `99bacbcec8879a41f44768bc85b49d82c76112549328251812eff3f7f81766f0` |
| Contexte public | 48/52 PASS, 4 FAIL hérités | `process.log` | 17106 | `9eb060cf7755122adf665fbea25c3482e65a15d768a4e562902f3aa5e157151b` |
| Contexte public | 48/52 PASS, 4 FAIL hérités | `xml/TEST-org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest.xml` | 9418 | `6eeb273222d19f4872de4b2db030f2143e6e687f795dec6f1b0b42b749a6ffb0` |
| Contexte public | 48/52 PASS, 4 FAIL hérités | `xml/TEST-org.graphiks.kanvas.surface.W7SurfaceCompositionPixelTest.xml` | 4982 | `7dd8530b72da545299888889501d608ed25ec7e48684d0fdb4aa68c5a176e20f` |
| Nettoyage cosmétique | 2/2 PASS | `events.jsonl` | 1126 | `f597fdde6a272889e21f0bade986d06ab84ca1babfcdc9d7b391484007912cf6` |
| Nettoyage cosmétique | 2/2 PASS | `exit.json` | 111 | `a760c356476f456e4e58f43cad6120f533471d61ccae8c2ae7386ac8846cd0ca` |
| Nettoyage cosmétique | 2/2 PASS | `process.log` | 13987 | `0ac44b8c966fe1e26794494fa919eaa656e88b3511117644392eb7c94993c206` |
| Nettoyage cosmétique | 2/2 PASS | `xml/TEST-org.graphiks.kanvas.skia.W7ReadbackBudgetWarmupSurfacePixelTest.xml` | 3209 | `0d5e0c90cfb852ccad1cfbc8bd2804cbe4903c5306511103e990f4dff2f78fb7` |

Extrait causal contrôlé du témoin (identifiants générés, tailles et faits de
diagnostic observés uniquement) :

```text
W7_READBACK_REUSE_TRACE reusable=readback-staging:1 requestedNewBytes=0 facts={requestedBackingBytes=0, residentBytes=1280, reservedBytes=0, inFlightBytes=0, mappingOwnedBytes=0, reclaimableBytes=1280, quarantinedBytes=0, aggregatePeakBytes=1316, configuredAggregateBudgetBytes=888, category.CanonicalTarget=36, category.RetainedMsaaColor=0, category.RetainedMsaaDepthStencil=0, category.FrameLocalMsaaColor=0, category.FrameLocalMsaaDepthStencil=0, category.LayerTarget=0, category.FilterTarget=0, category.DestinationSnapshot=0, category.ReadbackStaging=1280, category.ReusableScratch=0}
W7_SHARED_EVICTION_TRACE code=unsupported.readback_staging.aggregate_budget_exceeded facts={requestedBackingBytes=0, residentBytes=1280, reservedBytes=0, inFlightBytes=0, mappingOwnedBytes=0, reclaimableBytes=1280, quarantinedBytes=0, aggregatePeakBytes=1316, configuredAggregateBudgetBytes=888, category.CanonicalTarget=36, category.RetainedMsaaColor=0, category.RetainedMsaaDepthStencil=0, category.FrameLocalMsaaColor=0, category.FrameLocalMsaaDepthStencil=0, category.LayerTarget=0, category.FilterTarget=0, category.DestinationSnapshot=0, category.ReadbackStaging=1280, category.ReusableScratch=0} managedResident=1280 target=852 candidates=[ReadbackStaging/readback-staging:1/768]
W7_READBACK_REUSE_TRACE reusable=none requestedNewBytes=768 facts={requestedBackingBytes=768, residentBytes=512, reservedBytes=0, inFlightBytes=0, mappingOwnedBytes=0, reclaimableBytes=512, quarantinedBytes=0, aggregatePeakBytes=1316, configuredAggregateBudgetBytes=888, category.CanonicalTarget=36, category.RetainedMsaaColor=0, category.RetainedMsaaDepthStencil=0, category.FrameLocalMsaaColor=0, category.FrameLocalMsaaDepthStencil=0, category.LayerTarget=0, category.FilterTarget=0, category.DestinationSnapshot=0, category.ReadbackStaging=1280, category.ReusableScratch=0}
```

Le témoin établit le progrès d'éviction nécessaire à l'admission, sans étendre
la causalité à chaque ordre du corpus ni reconstruire l'historique complet du
contexte. Le résultat 48/52 et les quatre refus W5e hérités restent un important
gate de qualification, séparé de ce correctif ; le lot n'est pas globalement
GREEN et ne clôt pas W7. Les warnings JVM/LWJGL/Gradle hérités restent visibles ;
aucune sortie pristine n'est revendiquée.
