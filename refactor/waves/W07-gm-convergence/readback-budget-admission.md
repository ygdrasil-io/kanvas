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
