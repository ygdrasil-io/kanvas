# Design: phase 6 famille IMAGE GM

## Objectif

Traiter le debut de la phase 6 de `refactor/05-roadmap.md` par une vague large
sur la famille `IMAGE` du dashboard GM.

Cette vague ne declare pas le support complet des images. Elle transforme la
famille `RenderFamily.IMAGE` en inventaire exploitable et migre seulement les
rows qui peuvent utiliser le nouveau socle GPU sans melanger les corrections
algorithmiques lourdes avec le refactor backend.

Le resultat attendu est un pack d'evidence qui distingue :

- les rows `IMAGE` promues avec support prouve ;
- les rows qui rendent deja quelque chose mais restent instrumentees seulement ;
- les refus stables `expected-unsupported` ;
- les `no-score` separes des vrais fails ;
- les fails inattendus qui doivent rester visibles.

Le dashboard GM doit rester regenerable. Aucune promotion n'est acceptee sans
reference, artifact genere, diff/stat, diagnostics de route, diagnostics de
ressource/cache quand la migration les touche, et non-claim explicite.

## Sources et contraintes

Sources actives :

- `refactor/05-roadmap.md`, phase 6 ;
- `.upstream/target/high-performance-wgsl-pipeline-target.md` ;
- `.upstream/target/skia-like-realtime-renderer-target.md` ;
- `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md` ;
- `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md` ;
- `.upstream/specs/skia-like-realtime/04-performance-tiering-and-release-gates.md` ;
- `.upstream/specs/wgsl-pipeline/08-bitmap-image-rect-sampling.md` ;
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md` ;
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md` ;
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md` ;
- `reports/gpu-renderer/2026-06-14-m4-001-003-image-upload-codec-prepared.md` ;
- `reports/gpu-renderer/2026-07-08-gpu-phase-4-pass-batching.md` ;
- `reports/gpu-renderer/2026-07-08-gpu-intermediate-planner.md` ;
- `reports/wgsl-pipeline/2026-06-01-m66-gm-promotion-wave.md` ;
- `reports/wgsl-pipeline/m86-fidelity-burndown/evidence.md`.

Contraintes :

- ne pas porter Ganesh ou Graphite ;
- ne pas reconstruire SkSL, son IR ou sa VM ;
- garder WebGPU comme backend GPU ;
- garder WGSL comme cible shader ;
- ne pas utiliser de texture de compatibilite CPU-rendered cachee ;
- ne pas ajouter de codec, animation, mipmap, YUV, perspective, image-filter ou
  color-management large pour cette vague ;
- ne pas baisser les seuils globaux de similarite ;
- ne pas compter les rows `cpu-oracle` comme fidelite Skia-comparable ;
- garder les `no-score` separes des vrais fails ;
- garder les reason codes stables pour les refus ;
- prefixer les commandes shell avec `rtk`.

## Choix retenu

Nous retenons l'approche **large IMAGE classification + migration ciblee**.

La vague parcourt la famille `RenderFamily.IMAGE`, produit une classification
row par row, regenere le dashboard, documente les deltas par sous-famille, et
active uniquement les migrations image qui peuvent s'appuyer sur les phases 2 a
5 :

- texture/sampler cache du `GPUConcreteResourceProvider` ;
- materialisation de bindings texture/sampler dump-safe ;
- evidence create/reuse/refuse de ressources ;
- batching des packets image simples lorsque les invariants phase 4 sont
  prouves ;
- preservation de lifetime par `GPUQueueManager` ;
- diagnostics d'intermediaires phase 5 quand une image traverse layer/filter ou
  destination-read, sans promouvoir ces cas s'ils restent hors scope.

Cette approche est plus large qu'un fix image-rect, mais elle reste bornée :
l'inventaire peut couvrir toute la famille `IMAGE`, tandis que les changements
renderer restent limites aux chemins simples et prouvables.

## Architecture

La phase ajoute une orchestration d'evidence au-dessus des briques existantes.
Elle ne cree pas un second renderer image et n'ajoute pas de scripts Python.
L'orchestration d'evidence vit dans une librairie Gradle Kotlin dediee :
`:integration-tests:skia-evidence`.

Flux cible :

```text
SkiaGmRegistry IMAGE rows
  -> generated renders + dashboard data
  -> :integration-tests:skia-evidence classifier/report library
  -> per-row route expectations
  -> targeted migration hooks
  -> regenerated dashboard + evidence report
```

Responsabilites :

- `integration-tests/skia` reste proprietaire du dashboard GM, des PNG
  references/generated/diff, et des taches `generateSkiaRenders`,
  `generateSkiaRendersFor`, `generateSkiaDashboard`.
- `integration-tests/skia-evidence` reste proprietaire de la classification
  row-level, du schema d'evidence, de l'export JSON/CSV/Markdown et du CLI
  Gradle `generateGpuPhase6ImageFamilyEvidence`.
- `gpu-renderer/images` reste proprietaire des plans decode/upload/sampler,
  des codec gates et des diagnostics image.
- `gpu-renderer/resources` reste proprietaire de la materialisation
  texture/sampler/cache et des leases.
- `gpu-renderer/passes` reste proprietaire du batching.
- La vague phase 6 IMAGE produit les contrats de classification, les tests de
  policy, les rapports d'evidence, et les integrations ciblees vers ces
  composants existants.

Composants conceptuels :

### `ImageGmFamilyInventory`

Classe les GM `IMAGE` par sous-famille a partir du registre, du dashboard et
des facts disponibles. Le nom de GM peut alimenter le tri initial, mais la
decision finale doit etre justifiee par reference, generated render, score,
diagnostics, route ou refus stable.

Sous-familles initiales :

```text
simple-image-rect
strict-nearest-linear
bitmap-shader-affine
local-matrix-affine
texture-cache-candidate
sampler-policy-candidate
codec-gated
animation-gated
mipmap-gated
yuv-gated
perspective-gated
image-filter-gated
color-management-gated
readpixels-or-snapshot-gated
```

### `ImageGmMigrationPolicy`

Transforme les facts de chaque row en une classification :

- `promoted-support` ;
- `instrumented-existing` ;
- `expected-unsupported` ;
- `no-score` ;
- `unexpected-fail`.

La policy ne doit jamais promouvoir une row depuis un nom ou une sous-famille
seule. Elle doit refuser les capacites hors scope avec un reason code stable et
un non-claim.

### `ImageGmEvidenceReport`

Ecrit le rapport JSON/Markdown de phase 6 IMAGE depuis
`:integration-tests:skia-evidence` :

- counters globaux et par sous-famille ;
- tables pass/fail/no-score ;
- rows promues et non-claims ;
- rows instrumentees seulement ;
- refus stables ;
- vrais fails inattendus ;
- liens vers artifacts dashboard quand ils existent ;
- deltas de score quand un score precedent existe ;
- impact texture/sampler/cache/batching pour les rows migrees.

Le module lit le JSON du dashboard avec `kotlinx.serialization-json` en mode
`JsonObject`/`JsonElement`. Il n'utilise pas le compiler plugin serialization
tant que les schemas de dashboard restent partiellement souples.

## Regles de classification

### `promoted-support`

Une row peut etre promue seulement si elle a :

- reference disponible ;
- generated PNG disponible ;
- diff/stat artifact disponible ;
- score exploitable et au-dessus du seuil de la row ;
- fallback reason `none` pour la route GPU promue ;
- diagnostics de route CPU/GPU ou justification claire de reference ;
- diagnostics de texture/sampler/cache quand la migration utilise le provider ;
- diagnostics de batching quand le batcher accepte la row ;
- non-claim explicite pour les surfaces image hors scope.

Les rows promues peuvent compter comme support de largeur `IMAGE`. Elles ne
comptent comme fidelite Skia que si `referenceKind` est Skia-comparable selon
`.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`.

### `instrumented-existing`

Une row entre ici si elle rend et a un score exploitable, mais qu'elle manque
d'evidence phase 6 suffisante :

- route/cache/batching non prouve ;
- referenceKind non comparable ;
- artifact diff/stat incomplet ;
- changement renderer absent ;
- row utile comme baseline mais pas encore promouvable.

Cette categorie peut ameliorer l'operabilite du dashboard, pas le support
declare.

### `expected-unsupported`

Une row est `expected-unsupported` si elle touche une capacite hors phase 6 ou
dependency-gated et que le refus est stable.

Reason codes representatifs :

- `dependency.image.codec.unregistered` ;
- `dependency.image.codec.external_not_allowed` ;
- `dependency.image.codec.version_nondeterministic` ;
- `dependency.image.decode.provenance_missing` ;
- `unsupported.image.mipmap_budget_exceeded` ;
- `unsupported.image.yuv_color_space` ;
- `unsupported.image.yuv_converter_wgsl_unvalidated` ;
- `unsupported.image.tile_mode` ;
- `unsupported.transform.perspective_route_rejected` ;
- `unsupported.filter.node_unimplemented` ;
- `unsupported.destination_read.strategy_unaccepted` ;
- `unsupported.texture.active_attachment_sampled`.

Chaque refus doit expliquer pourquoi la row reste visible et quel follow-up la
debloquerait.

### `no-score`

Une row reste `no-score` si la comparaison n'est pas possible :

- reference manquante ;
- generated PNG manquant ;
- mismatch de taille ;
- decode/comparison impossible ;
- row skipped avant render ;
- artifact absent du dashboard.

`no-score` n'est pas un fail. La phase doit le compter separement.

### `unexpected-fail`

Une row est un fail inattendu si :

- reference et generated PNG existent ;
- la comparaison est possible ;
- le score est sous seuil ;
- aucun refus stable ne justifie l'etat ;
- aucune policy ne la limite a `instrumented-existing`.

Les fails inattendus restent visibles dans le rapport et dans les next actions.

## Migration renderer ciblee

La vague large ne doit pas corriger tous les problemes image. Les migrations
autorisees sont celles qui prouvent le socle phases 2 a 5.

### Texture et sampler cache

Pour les rows `simple-image-rect`, `strict-nearest-linear`, et les fixtures
image repetees :

- materialiser les textures et samplers par `GPUConcreteResourceProvider` ;
- exposer create/reuse/refuse via telemetry provider ;
- garder device generation dans les cles de cache resource ;
- exclure handles, pixel content, row bytes et uniform values des cles durables ;
- prouver que les leases texture/sampler restent retenues jusqu'a completion
  submission quand la route est executee.

### Batching image simple

Un packet image peut etre eligible au `GPUPassBatcher` seulement si :

- meme target ;
- meme target state ;
- role `Shading` ;
- source texture/sampler deja materialisee et retenue ;
- sampler mode supporte dans cette row ;
- pas de destination-read ;
- pas de saveLayer/filter/intermediate ;
- pas de readback, upload barrier ou copy non retenu ;
- meme device/resource generation attendue ;
- ordre visuel prouve.

Si un invariant manque, le batcher coupe avec une raison stable. Il ne doit pas
silencieusement reordonner ou supprimer un packet image.

### Rows instrumentees seulement

`bitmap-shader-affine`, `local-matrix-affine` et `sampler-policy-candidate`
peuvent etre classees et instrumentees sans promotion tant que l'evidence
route/cache/batching/reference n'est pas complete.

### Refus explicites

Les cas codec, animation, mipmap, YUV, perspective, image filter, readpixels,
snapshot, picture shader complexe et color-management avance restent visibles
et refusables. La phase ne doit pas ajouter de substitut court-terme pour
effacer ces rows.

## Donnees et artifacts

Artifacts attendus :

- `reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md` ;
- `reports/gpu-renderer/phase-6-image-family/evidence.json` ;
- `reports/gpu-renderer/phase-6-image-family/classification.csv` si un export
  tabulaire simplifie la revue.

Producteurs attendus :

- `:integration-tests:skia:generateSkiaDashboard` produit le dashboard source ;
- `:gpu-renderer:generateGpuPhase6ImageResourceEvidence` produit l'evidence
  texture/sampler/cache ciblee ;
- `:integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence`
  agrege ces sources et ecrit les artifacts finaux.

Le JSON d'evidence doit contenir :

- schema version ;
- generation command ;
- dashboard source path ;
- total `IMAGE` rows ;
- counters par classification ;
- counters par sous-famille ;
- promoted rows ;
- instrumented rows ;
- expected unsupported rows ;
- no-score rows ;
- unexpected fails ;
- row-level `referenceKind` quand disponible ;
- artifact paths pour reference/generated/diff ;
- route/cache/batching diagnostics quand disponibles ;
- non-claims globaux.

Le Markdown doit etre lisible en revue PR et PM :

- resume ;
- counters ;
- sous-familles ;
- promotions ;
- refus ;
- no-score ;
- fails inattendus ;
- impact resource/cache/batching ;
- commandes de validation ;
- limites et next actions.

## Validation

Validation dashboard :

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard
```

Validation evidence :

```bash
rtk ./gradlew :integration-tests:skia-evidence:test
rtk ./gradlew :integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence
```

Validation renderer ciblee :

```bash
rtk ./gradlew :gpu-renderer:test --tests "*Image*"
rtk ./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.family=IMAGE
```

Le plan d'implementation doit verifier que `generateSkiaRendersFor` applique
reellement le filtre `RenderFamily.IMAGE`. Si le task wiring transmet deja
`-Pgm.family=IMAGE` mais que le generateur ne consomme pas encore l'argument
`--family`, la phase doit d'abord corriger ou contourner ce filtre de facon
documentee. Toute adaptation doit rester visible dans le rapport.

Gates :

- le dashboard se regenere sans crash ;
- `no-score` est separe de `unexpected-fail` ;
- aucune promotion sans reference/generated/diff/stat/route evidence ;
- tout `expected-unsupported` a un reason code stable ;
- les rows migrees exposent counters texture/sampler/cache/batching ;
- aucune baisse globale de seuil ;
- aucun nouveau support large pour codec, animation, mipmap, YUV, perspective,
  image filter, picture shader complexe ou color-managed decode ;
- `rtk git diff --check` est propre.

## Performance et telemetry

La preuve performance reste modeste pour cette premiere vague large.

Attendu pour les rows migrees :

- create/reuse/refuse texture/sampler visible ;
- bind group churn documente si observable ;
- pass batching accepte ou coupe avec reason code stable ;
- nombre de render passes/submissions stable ou justifie ;
- aucune augmentation cache/pipeline non expliquee.

Les mesures derivees d'un inventaire ou d'un ledger restent report-only. Elles
ne deviennent pas des gates mesurees tant qu'un runtime observe ne les produit
pas avec metadata de lane, host, adapter, samples et quarantine policy.

## Non-goals

Cette phase ne fait pas :

- support complet de la famille `IMAGE` ;
- nouveau codec ou decodeur ;
- support animation ;
- support YUV/multiplanar ;
- support mipmap/aniso/cubic ;
- support perspective image ;
- support image-filter DAG ;
- support picture shader complexe ;
- support color-management avance ;
- CPU-rendered compatibility texture ;
- changement global de seuils ;
- dynamic SkSL ou compilation SkSL.

## Risques et mitigations

Risque : l'approche large produit surtout du tri sans migration renderer.
Mitigation : exiger au moins une preuve texture/sampler cache ou batching sur
des rows image simples avant de clore la phase.

Risque : les `no-score` masquent des fails reels.
Mitigation : garder les causes `no-score` detaillees et les separer des
generated/reference presents mais sous seuil.

Risque : un refus remplace une regression.
Mitigation : un `expected-unsupported` doit citer la capacite hors scope et un
reason code deja reconnu ou ajoute avec test de policy.

Risque : la phase melange image filters et image drawing.
Mitigation : les GM `IMAGE` qui exercent des filter DAGs restent
`image-filter-gated` ou sont renvoyees vers une phase filter dediee.

Risque : les rows `cpu-oracle` gonflent la fidelite Skia.
Mitigation : reporter `referenceKind` et exclure les `cpu-oracle` du compteur
Skia-comparable.

## Definition of done

La phase 6 IMAGE est terminee quand :

- le dashboard GM a ete regenere ;
- l'inventaire `IMAGE` large est classe par row ;
- les counters pass/fail/no-score et sous-familles sont publies ;
- les rows promues ont evidence complete ;
- les rows hors scope ont refus stable ou restent `no-score` avec cause ;
- les migrations image simples exposent provider/cache/batching telemetry ;
- le rapport JSON/Markdown est ecrit sous `reports/gpu-renderer/` ;
- les tests/policies ciblees et `rtk git diff --check` passent ;
- les non-claims restent explicites dans le rapport final.
