# W7 — tinybitmap : composition encoded du port

4 octobre 2026. Branche `codex/w7-tinybitmap-encoded-parity`, base
`075405b7a5f195c6b3b0d2742bd480874506e9f1`, parent draft
[#2445](https://github.com/ygdrasil-io/kanvas/pull/2445).
Source qualifiée : `735d96af465a131fe5f6f992ce1d85588dbcf027`.
[Draft #2446](https://github.com/ygdrasil-io/kanvas/pull/2446) publiée,
empilée sur la branche de #2445, OPEN/draft et attachée au chat.
W7 reste ACTIVE ; pas de merge, GREEN global ou clôture de la parité.

## Correctif et décision déléguée

[Design](tinybitmap-encoded-design.md), [plan](tinybitmap-encoded-plan.md).
Au pin Skia `4f26f22daa4bf124e2999145f5caad4b10625580`, le GM utilise
un texel PREMUL rouge128/alpha128, fond221, alpha0.5, REPEAT/MIRROR,
nearest et drawPaint. Le SkPaint par défaut est non-AA. Le harness DM
bare8888 a un RasterSink N32 sans color space explicite ; le choix encoded
est une inférence limitée à ce contrat épinglé, pas une preuve du producteur
de la PNG historique.

Le GM local déclare maintenant SRGB_ENCODED et antiAlias=false.
Aucun défaut global Surface/GM, shader/planner/runtime/API, source,
dimension, alpha byte, tile mode, seuil ou tolérance ne change.
Le proxy drawRect pleine target est conservé. Le modèle public alpha128/255
donne le littéral [230,165,165,255] ; le contrôle LINEAR garde
[230,194,194,255]. L'alpha Skia0.5 exact donne un modèle [230,166,166,255].
Cette quantification reste une limite, sans attribution exclusive du delta
historique à l'alpha.

## Témoins GPU et RED causal

Le RED précède le correctif : deux tests encoded échouent sur G/B attendu165
contre194. Les deux contrôles indépendants passent. Aucun échec de
compilation ni refus GPU n'explique ce RED.

Après le correctif, 17/17 tests sélectionnés PASS :
TinyBitmap4, EncodedImage4, BitmapRect3, ChildPort3, ChildCausal1, Warmup2.
Aucun skip, erreur, timeout, signal ou runner133. La fixture est restée
figée après RED : `d1650c2963aeb04e2569cd82bf106a78940eef821c41a7be2b82035304010660`.
GM : `5e1375651d69086f750cee681a84e6abb102b2aa2a1e038c2afa04c333c4272b`.

Le registered renderer et deux frames sur la même Surface vérifient
l'intégralité des40000bytes RGBA8, 100×100, deux opérations stables,
2dispatches/0refus/aucun diagnostic et repeatability.
Scopes Render/Readback, QueueSubmitted avant CompletionSucceeded,
deltas par frame coordinator/encoder/buffer/submit/readback=1 ;
draws0 + drawIndexed2 correspondent aux2drawCalls, pipelineBinds4
aux4pipelines. Aucun reset/dispose/purge entre les deux frames ;
AfterAll uniquement en fin de classe. Contrôle LINEAR strict et contrôle
source des deux domaines conservés ; pas de faux ou test de texte/config.

RGBA encoded :
`d8d95c3bf9d98a4479ebf85604be243b92c4f306799b966ed371634b23a4fa0c`.
RGBA LINEAR inchangé :
`a1d14b67097c5a1235c25cd91acdfd57c2890f7896637d3472fd3fa6d3e9939b`.

## Comparaison historique, cas592 uniquement

Deux mesures fraîches [592,593), registry631/hash
`4ca8eea61451b1143fd3d15634d2c34e0c9ec31b74fd351ca30a69ee36f565d7`,
exit0/2dispatches/0refus/aucun diagnostic, dimensions100×100 :

| Mesure | Avant LINEAR | Après encoded |
| --- | ---: | ---: |
| Pixels exacts | 0 % | 0 % |
| Pixels à ±2 | 0 % | 100 % |
| SSIM luminance | 0.9949447881896182 | 0.999992805050309 |
| Erreur moyenne de canal normalisée | 0.054901960784313725 | 0.00196078431372549 |
| Delta maximal RGBA | [0,28,28,0] | [0,1,1,0] |

Le runner standard sélectionné exécute1test PASS, sans skip/erreur, et
mesure100.0 %. Son seuil historique0.0 et tolérance2 restent inchangés.
Le gain repose sur la mesure effective, pas sur ce seul verdict PASS.
Ce n'est pas une égalité bit-exacte ni un nouveau score agrégé du corpus.

La référence physique reste `c52d28dd7fe1d45b1650ad3332ed36df1acb60987d166c588653180cd7f53c01`.
Pillow12.2 confirme des octets stockés uniformes [204,162,158,255],
un ICC536bytes SHA `8ebad59a830cdaef8af6c1c4be7f3c091b2afc089da131a6c0aed2d4bc8c4266`,
et Author `DM unified Rec.2020`.
Ces octets bruts ne sont pas les canaux sRGB de comparaison : l'utilitaire
inchangé ComparisonUtils décode puis convertit le profil nommé par
matrix/TRC vers sRGB avant quantification. La révision et configuration
productrices ne sont pas établies par ce metadata. Les PNG actual/générée
portent sRGB/gamma et se décodent toutes deux en [230,165,165,255],
40000bytes/SHA d8d95c… identiques au témoin littéral GPU.

## Artifacts et périmètre gelé

Seul `generated-renders/image/tinybitmap.png` est régénéré :
`29ce792e59bd71e899caed0505a05c2edb691d83ed49102a531e6b525853d909`.
814PNG après : `e589c21c3c3eebd3787434064fe5a122f6dc2005cf4d6786b529b1c8fd2a2c6d`.
813PNG non-cibles inchangées :
`ca9ccd2e0596c72d277ca143b3b8dc92cf3dd3bdcb26751818b2fbd7d8bc8ba0`.
559scores, cible0.0→100.0 ;558autres valeurs inchangées :
`d9f2f6e711f0f81fc416d9116876ffaff7ae9e09a0fd24d76fd0004bc696563f`.
Fichier de scores : `ba0bd77609386acd8b77443d853d424d36edea08719b8e868b71a65fbc8dbd54`.
Son diff ne contient que timestamp et valeur tinybitmap.
La référence, les autres GM et les69chemins critiques restent gelés, sauf
les deux fichiers autorisés et les deux artifacts cibles.
Diagnostic inverse96cd et workspaces/archives privés conservés.

## Exécutions, audit et warnings

Une seule exécution native à la fois, --no-daemon, limite externe240s,
watchdog GM30s pour les mesures. Aucun write source/docs/Git pendant
runtime. Main a lu intégralement process/exit, tous events/stacks,
tous XML stdout/stderr et JSONL, puis effectué un post-seal séparé.
Les process groups propres à chaque run sont vides ; les autres daemons
de la machine n'ont pas été touchés. Binary/images sont conservés/hashés ;
les images TinyBitmap sont décodées, pas les9captures ChildSampling du
GREEN. JavaExec n'émet pas de JUnit/events : pas de tests inventés.

Archives sous `/private/tmp/kanvas-w7-inverse-inventory.hbWqUb` :

| Archive | Child | Exit | Gradle | Résultat |
| --- | ---: | ---: | ---: | --- |
| tinybitmap-domain-baseline-1 | 27478 | 0 | 16 s | case592 LINEAR, ±2=0 % |
| tinybitmap-domain-red-1 | 33452 | 1 | 21 s | 4 tests : 2 PASS / 2 échecs encoded attendus |
| tinybitmap-domain-green-1 | 35211 | 0 | 19 s | 17/17 PASS, 6 classes |
| tinybitmap-domain-after-1 | 38505 | 0 | 17 s | case592 encoded, ±2=100 % |
| tinybitmap-domain-generator-1 | 40679 | 0 | 14 s | 1 rendu / 0 échec, 2 dispatches / 0 refus |
| tinybitmap-domain-runner-1 | 41396 | 0 | 15 s | 1/1 PASS, tinybitmap=100.0 |

GREEN :34events,6XML96601bytes dont EncodedImage71000 intégralement
relu en chunks non tronqués. Warnings JVM native-access, LWJGL Unsafe et
Gradle hérités restent visibles ; aucun warning Kotlin du lot. Le premier
audit Pillow read-only a produit un warning getdata/JSONparse ; reprise
intégrale via get_flattened_data, sans modification d'image.

## Relecture et limites restantes

Relecture de tâche Sol du lot075405…820c05 : spec compliant, qualité Approved,
Critical0/Important0. Minor hérité : warnings native-access/LWJGL suivis dans
les logs. Main a résolu les points non vérifiables par diff (audits natifs,
pré/post-seals séparés,47fichiers conservés avec chemins/bytes/SHA exacts et
diagnostic inverse inchangé).

Relecture finale indépendante Sol du lot075405…f93d64 : publication draft
approuvée, Critical0/Important0/nouveauMinor0 ; warnings hérités reportés.
Le reviewer a vérifié les47archives,69seals, XML/events, JSONL et pixels
décodés. La chronologie passée d'exclusivité des writes et de lecture
intégrale reste une attestation du contrôleur. Sources Skia distantes
inaccessibles dans son environnement : octets upstream non authentifiés
indépendamment ; l'inférence bare8888 reste bornée, producteur PNG inconnu.
Pas de nouveau correctif requis. Main a scellé de nouveau69/814PNG/559scores sans
dérive avant publication ; aucun rerun natif ni changement de code.
Step9 : publication initiale vérifiée au commit distant
`d25099a8c59ac5919445a0726e31f5661f891f8d`, base
`codex/w7-promoted-image-contracts`, description comparée octet pour octet
au fichier publié et attachement vérifié. Mise à jour documentaire finale
seulement après ces vérifications ; code/artifacts restent ceux qualifiés.
Fonts, codecs externes et jpg-color-cube restent exclus. Les tests AA aux
frontières, admission off-target sans clip, compteur hétérogène,
SceneSnapshot/ownership stencil et fidélité des autres GM restent ouverts.
Le57/57 du lot parent reste son résultat historique, pas un run de ce lot.

Décisions prises sous carte blanche W7 : port encoded explicite, pas de
migration globale (risque : rework si le contrat de target retenu change) ;
contrôle LINEAR explicitement fixé, aucun pixel adapté après RED (risque :
les témoins stricts échouent si notre modèle est faux) ; déroulement SDD sans
attribuer une approbation documentaire fictive (risque : diff borné à revoir) ;
conservation des preuves/workspaces au lieu de cleanup (coût : scratch
supplémentaire, aucune perte de données).

## Inventaire complet des47fichiers privés

Chemins relatifs à chaque archive ; bytes et SHA-256. Les binaires ne sont
pas prétendus décodés. Commandes exactes et tous pre/post-seals conservés
dans `.superpowers/sdd/tinybitmap-encoded-plan/controller-evidence.md`.

### Baseline

```text
exit.json | 111 bytes | c46447e7dfabe9ab9e2c631d95a3d8257c079174cc3ddf3d6374ddf0e6916e41
parity/images/tinybitmap/actual.png | 345 bytes | b3f302353c21ca67bbf7b36d3c9db028d49d487806a367e98b119f14a326a4dd
parity/images/tinybitmap/diff.png | 343 bytes | 8e2b2290fb7707a9c613610035966c9fde804b10b4ad1f80a1e6519554431898
parity/slice-592-593.jsonl | 1426 bytes | 8b9c74bb7e6a7735d85d7ccf737cc7ea364d000b0f6cb91f2136d0365e4c85d1
process.log | 13688 bytes | af9bdbd0fcdcd40560cad2e4ec663e58efb03e196694a7b2aeac27ec7ede5688
```

### Red

```text
binary/output.bin | 5877 bytes | 60fbda4462ca4d10ea76c0e6b5f543ce8fd53fb56aa985301daf8daf6f002ff6
binary/output.bin.idx | 135 bytes | 64ab58b3690d463cce76d7e97b2a327df4f9f062696079e70e324afc915178d9
binary/results.bin | 3152 bytes | 4678755927ae4e572855cd22d331ab4bb9436adef5a77cd901910b9a34b1775e
events.jsonl | 4629 bytes | 8f047bd85336c5b53032bb78f2966441f4ab2c5dcef52ff4840305481b9c6465
exit.json | 111 bytes | 2aad941fd2d0d63913b540be401e22ff66e64a8071611f1ee326724d19f27010
process.log | 16352 bytes | d57d484d0f92fb29644d9e991cc3aae09cfe23c13cba3aeb29170385718633d5
xml/TEST-org.graphiks.kanvas.skia.W7TinyBitmapSourceSurfacePixelTest.xml | 9487 bytes | 8fc760315e2b1bfe9ddf6d2a64ca2b5ac1c284b4279f3b837d0025835061338e
```

### Green

```text
binary/output.bin | 92449 bytes | 4315d30626593f34cc25d5527f706b94cc4398ad7d5f0bef48b7d29d3a3b8c0f
binary/output.bin.idx | 574 bytes | fc904febbc890ddbbb1c39efb8162dcd172f9061473b48924d88760a7257603f
binary/results.bin | 2792 bytes | 3241934abd93a12fb10bfcfbdbd7826a292e76b954d53e0a52537abd1033f6c5
events.jsonl | 9575 bytes | f55b906e6f3bd862bb002dccebbfeee2fba853bbf50d090ac38b3f16ac3ea490
exit.json | 111 bytes | 44be6d51176a74650e472d0b6330a402859ad32e84cb2be60688e895597bd6ba
images/w7-child-sampling-control-half-linear.png | 207 bytes | a47771f9ad6d407d428748f1f9bb7b53fd1fd8aa832000445394ba4f76efd040
images/w7-child-sampling-control-half-nearest.png | 121 bytes | 9e38135b9c859bc60c008fdc5063887b3e0d3b045f8d6e1c5331d57323356774
images/w7-child-sampling-control-opaque-linear.png | 158 bytes | f276f912d28458a9d2092c12bd9b24b91a743f5634e971a1101d1af306a860aa
images/w7-child-sampling-control-opaque-nearest.png | 118 bytes | ec68b26e227f47781b2e27a614c180b57f6e1954319fb1a9ca24533ec186434e
images/w7-child-sampling-encoded-source.png | 379 bytes | 2e01df68bb65bef4020d116d14c410fe4fc7c3e13e4781398333f5b4a86a54a1
images/w7-child-sampling-explicit-stroke.png | 1556 bytes | 08e7da96949e0b869bbaeff99b64307413dc5c6a8a563790a23836ac3ba78e90
images/w7-child-sampling-legacy-open-fill.png | 885 bytes | fab080bc7fa141db59cecb049eeb9a83cc624b87494a98d86d4a4a01a27f55f3
images/w7-child-sampling-public-draw-line.png | 1556 bytes | 08e7da96949e0b869bbaeff99b64307413dc5c6a8a563790a23836ac3ba78e90
images/w7-child-sampling-real-gm.png | 2466 bytes | bc56fbc82962fc588230d3df030fe823dcb1638f2ed85ce127a6546ff48528ea
process.log | 15525 bytes | 2575e7c8ab6067e958023c0b91c5eefba86832108c49421fbb38ace087f1ceec
xml/TEST-org.graphiks.kanvas.skia.W7BitmapRectSourceSurfacePixelTest.xml | 2000 bytes | 19e04d1ae2fcf3d93e76c21bcd49cdf622603a0b8804d3e674b794b6b6b3fc23
xml/TEST-org.graphiks.kanvas.skia.W7ChildSamplingCausalSurfacePixelTest.xml | 4715 bytes | c2abe8bc804faeb7ac8e97eebafc67d328903b9be6e270a3b1ca68c7fb2c2612
xml/TEST-org.graphiks.kanvas.skia.W7ChildSamplingPortSurfacePixelTest.xml | 8797 bytes | 80ff0549b725cce2277a392ea29d415e11fd84f760eea4ce9b989221f140993c
xml/TEST-org.graphiks.kanvas.skia.W7EncodedImageShaderSurfacePixelTest.xml | 71000 bytes | 7ded50b1f28cb8efe70ae944efb69f542e93562f18dcef51b7425c0f8a3aa537
xml/TEST-org.graphiks.kanvas.skia.W7ReadbackBudgetWarmupSurfacePixelTest.xml | 3210 bytes | f65772f9135589d98bf69676349027386254c11bff3ce7b7418322ca07d24c52
xml/TEST-org.graphiks.kanvas.skia.W7TinyBitmapSourceSurfacePixelTest.xml | 6879 bytes | e18ddc75fe353555a6a4581e00dfea2ceb914274ee0114ad4ebe420a88463f32
```

### After

```text
exit.json | 111 bytes | da1774e374eb754f669f995b77d037108b16c0daa5ee3167b644273064fde83b
parity/images/tinybitmap/actual.png | 345 bytes | 29ce792e59bd71e899caed0505a05c2edb691d83ed49102a531e6b525853d909
parity/images/tinybitmap/diff.png | 343 bytes | 8e2b2290fb7707a9c613610035966c9fde804b10b4ad1f80a1e6519554431898
parity/slice-592-593.jsonl | 1432 bytes | f1690c941a06b0b7ee7b4c11a1976b73985a90b28c05603ea6f9a2d2540775cc
process.log | 13865 bytes | a19a766ea65fff48a41a51994685881bce70916be6a09f68d772514662317431
```

### Generator

```text
exit.json | 111 bytes | b71d824f449269c431724964c0a3520a1c51d6b3698a99d72e36843efd6fc256
process.log | 13898 bytes | 54134d0ccc78e557f95f06fef1f062d6e6ef53f58fd045701ecde3aa8fe4d6c4
```

### Runner

```text
binary/output.bin | 136 bytes | 1c34bb3a774ecaf726316b0f3c71310ea69d5f1df2807435316b01242588ab64
binary/output.bin.idx | 36 bytes | af90c314dad64b9fac95dd542edac302d4fcff3f8c92f763f42a73bae6beba9f
binary/results.bin | 112 bytes | 4e1b43dc0eaf77633f076f1a02d5fd055b0f315c484897fd4220d012ef936fb6
events.jsonl | 363 bytes | c2ddcb3be84b585040bdf9a27dbf77eed585dfe7c5fc980223280bdc2f9b6db7
exit.json | 111 bytes | ef14e76ba548a1a7b3c35fde56e6ebc35602bfb9af650b672bedfe3836cb5931
process.log | 13826 bytes | 0b5c9c9adeef753c23434a289da6e391af7decfbb481c5c34aa6f328c59cbcfb
xml/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml | 551 bytes | 951f1a931c661f563a9ddace5409b70def05563cb6f60714ca131c63ac4f2217
```
