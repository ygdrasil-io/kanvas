# Design: GPU intermediate planner et activation destination-read

## Objectif

Traiter toute la phase 5 de `refactor/05-roadmap.md` avec une activation par
defaut orientee correction visuelle.

La phase couvre :

- la sortie de la logique procedural `scene/src/snap` du renderer ;
- un plan explicite pour destination-read ;
- les intermediaires de destination, layer, filter et MSAA ;
- le reuse d'intermediaires par le resource provider ;
- les decisions copy/render/compute selon `GPUCapabilities` ;
- l'integration progressive mais active de saveLayer, blends avances et MSAA ;
- les diagnostics de fallback ;
- la regeneration scenes/GM/dashboard comme validation anti-crash.

Cette phase n'est pas conservative sur la qualite visuelle : les snapshots et
scores peuvent changer dans les deux sens. Une degradation visuelle documentee
est acceptable parce qu'une phase de correction visuelle plus large est prevue
apres la roadmap. En revanche, les crashs, exceptions non documentees, stale
resources, active attachment sampling et aliasing WebGPU invalide restent
bloquants.

## Sources et contraintes

Sources actives :

- `refactor/05-roadmap.md`, phase 5 ;
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md` ;
- `.upstream/specs/gpu-renderer/28-layer-savelayer-execution.md` ;
- `.upstream/specs/gpu-renderer/15-draw-layer-planner-and-sort-policy.md` ;
- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md` ;
- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md` ;
- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md` ;
- `.upstream/target/high-performance-wgsl-pipeline-target.md` ;
- `.upstream/target/skia-like-realtime-renderer-target.md`.

Contraintes du projet :

- ne pas porter Ganesh ou Graphite ;
- ne pas reconstruire SkSL, son IR ou sa VM ;
- garder WebGPU comme backend GPU ;
- garder WGSL comme cible d'implementation shader ;
- ne pas utiliser de CPU readback comme fallback produit ;
- ne pas sampler l'active attachment pendant son ecriture ;
- garder les diagnostics stables pour les refus ;
- ne pas mettre de handles backend, d'adresses ou de valeurs uniforms dans les
  keys durables ;
- ne pas nommer les types runtime avec `Phase5` ;
- ne pas centrer l'architecture cible sur `RectOnly`, qui reste seulement un
  adaptateur de transition pour les scenes offscreen existantes.

## Choix retenu

Nous retenons l'approche **planner-first active par defaut**.

Un nouveau planner produit devient la source des decisions pour les copies de
destination, les intermediaires, les targets de saveLayer, les composites, les
resolves MSAA, les pass splits et les refus. Il orchestre les contrats deja
presents au lieu de recreer une architecture parallele.

Les routes coherentes sont activees par defaut. Les refus restent reserves aux
cas WebGPU invalides ou aux cas sans route executable :

- active attachment sampling ;
- framebuffer fetch ou input attachment non portable ;
- bounds non finies ou impossibles ;
- usage flags manquants ;
- target/intermediate generation stale ;
- mismatch de format, bounds ou sample count ;
- lifetime non retenue par la queue ;
- aliasing parent/source/destination ;
- pass split impossible ;
- shader, copy, composite ou resolve manquant.

Les imperfections visuelles passent dans les artifacts de validation sous forme
de deltas documentes, pas en refus automatique.

## Architecture

Le composant central est `GPUIntermediatePlanner`.

Il produit un `GPUIntermediatePlan` avant materialization et encodage. Ce plan
decrit les operations suivantes :

- `RenderToTarget` pour les draws directs ;
- `CreateIntermediate` pour un target de layer, filter, destination copy ou
  resolve ;
- `RenderLayerChildren` pour les enfants d'un saveLayer ;
- `CopyDestination` pour une destination snapshot ;
- `BindIntermediate` pour une texture separee deja validee ;
- `CompositeIntermediate` pour restaurer une layer/filter result dans le parent ;
- `ResolveMSAA` pour produire une cible single-sample ;
- `Refuse` pour les cas invalides.

Le planner ne materialise aucun handle GPU. Il porte les faits de validite :

- bounds et copy bounds ;
- target/intermediate generation ;
- format et color/premul facts disponibles ;
- usage labels ;
- sample count et resolve strategy ;
- lifetime class et owner scope ;
- destination-read strategy ;
- layer ordering token ;
- pass split et copy-before-sample ;
- copy bytes et live intermediate bytes ;
- reason code en cas de refus.

Le renderer execute le plan. Il ne decide plus localement quels enfants vont
dans quelle texture secondaire, quand copier la destination, ou quand
compositer un intermediaire.

## Composants

### `GPUIntermediatePlanner`

Responsabilites :

- consommer les commandes normalisees ou un scene draw plan transitionnel ;
- appeler les planners existants pour destination-read, layer/saveLayer,
  blend, MSAA et resource policy ;
- produire un plan ordonne ;
- fermer les sort windows aux copies, layers, resolves et barriers ;
- garder les pass splits explicites ;
- produire des dumps deterministes.

Le planner ne remplace pas :

- `GPUDestinationReadStrategyPlanner` ;
- `GPUSaveLayerIsolatedTargetPlanner` ;
- `GPUBlendAllowlistPlanner` ;
- `GPUMsaa` ;
- `GPUPassBatcher` ;
- `GPUQueueManager`.

Il les compose et rend leurs decisions executables ensemble.

### `GPUIntermediateResourceProvider`

Extension ciblee du provider concret pour les textures intermediaires.

Responsabilites :

- create/reuse/refuse pour destination copy, layer target, filter intermediate
  et resolve target ;
- valider descriptor, generation, bounds, format, usage labels, sample count,
  lifetime et owner scope ;
- exposer create/reuse/refuse telemetry ;
- enregistrer les leases avec la bonne release policy ;
- refuser les ressources stale ou aliasing.

Le reuse est une preuve de performance seulement. Il ne prouve jamais la
correction sans generation, bounds, format, usage et lifetime valides.

### `GPUCompositePlanExecutor`

Responsabilites :

- transformer un `GPUIntermediatePlan` en command streams ou appels runtime ;
- utiliser les primitives runtime existantes :
  `createOffscreenTexture`, `encodeOffscreenTexture`, `drawCompositePass`,
  `drawBlendPass` ;
- encoder les copies avant sample ;
- emettre les restores de layer dans l'ordre ;
- connecter les operand bridges produits par les materializers ;
- refuser avant encodage si les ressources requises ne sont pas materialisees.

Ce composant remplace la logique procedural actuellement presente dans le
renderer de scenes offscreen.

### `GPUIntermediateTelemetry`

Responsabilites :

- compter les destination-read requirements par kind ;
- compter les strategies ;
- compter copies, copied bytes, pass splits et copy-before-sample ;
- compter intermediaires crees, reutilises, refuses et live bytes ;
- compter layer targets et layer composite routes ;
- compter MSAA targets et resolves ;
- compter refus par reason code ;
- exposer les dumps dans runtime/report evidence.

Les compteurs s'ajoutent aux compteurs phase 0-4 existants, sans les remplacer.

### Validation scene/GM

La validation utilise les taches et harness existants quand possible. Elle ne
creera pas une classe runtime `Phase5ValidationHarness`.

Responsabilites :

- regenerer les scenes offscreen pertinentes ;
- regenerer le dashboard ou les artifacts GM disponibles ;
- classifier pass, visual delta, expected unsupported, no-score et crash ;
- echouer la phase sur crash/exception non documente ;
- accepter les deltas visuels documentes ;
- produire un rapport PM/developpeur lisible.

## Flux de donnees

Flux cible :

```text
normalized draw commands / scene draw plan
  -> analysis
  -> GPUIntermediatePlanner
  -> GPUIntermediatePlan
  -> GPUIntermediateResourceProvider / existing materializers
  -> GPUPassCommandStream / runtime executor
  -> GPUQueueManager
  -> telemetry + validation artifacts
```

Pendant la transition, le renderer de scenes offscreen peut adapter son plan
actuel vers `GPUIntermediatePlanner`. Cette integration ne devient pas une
frontiere produit : `RectOnly*` reste un detail de scene tooling.

Les `PipelineKey` gardent seulement les axes qui changent la validite GPU :

- render step ;
- WGSL/module/layout ;
- target format ;
- blend state ;
- depth/stencil state ;
- sample count ;
- bind-group layout ;
- capability facts qui changent la validite.

Les faits suivants restent hors `PipelineKey` :

- concrete resource handles ;
- target/intermediate generation ;
- copy bytes ;
- cache hit/miss ;
- texture contents ;
- uniform values ;
- transient buffer offsets.

## Plans explicites attendus

### `srcOver` simple

Plan :

```text
Draw solid/gradient srcOver
  -> GPUBlendAllowlistPlanner: FixedFunctionBlend
  -> GPUDestinationReadRequirement.FixedFunctionBlend
  -> GPUDestinationReadStrategy.FixedFunction
  -> RenderToTarget(root)
  -> no destination texture
  -> no copy
```

Ce plan peut rester batchable quand les autres invariants de phase 4 sont
valides.

### Shader blend avec destination-read

Plan pour `Screen` ou `Multiply` :

```text
Draw shader-blend
  -> GPUBlendAllowlistPlanner: ShaderBlendWithDstRead
  -> GPUDestinationReadStrategyPlanner: TargetCopySnapshot ou BindIntermediate
  -> CopyDestination si TargetCopySnapshot
  -> BindIntermediate avec texture/view/sampler separes
  -> drawBlendPass ou shader blend equivalent
  -> copy/intermediate counters
```

Cette route est activee quand le plan destination-read, les bounds, la
generation, les usages, le shader et la binding ABI sont valides. Elle ne doit
plus rester bloquee par `unsupported.blend.shader_route_unvalidated` une fois
ces preuves presentes.

### `saveLayer` avec snapshot/intermediate

Plan :

```text
SaveLayer(scope)
  -> CreateIntermediate(layer-target)
  -> clear transparent or accepted init route
  -> RenderLayerChildren(layer-target)
  -> optional source/filter intermediates
  -> CompositeIntermediate(parent)
  -> release or retain by lifetime
```

Pour un restore composite qui observe la destination parent, le composite porte
un `GPUDestinationReadPlan` :

```text
Composite layer into parent
  -> TargetCopySnapshot or SampleExistingIntermediate for parent destination
  -> shader/fixed-function composite route
  -> stable ordering token
```

Les scenes `savelayer-isolated` et `savelayer-group-alpha` restent des oracles
prioritaires pour verifier que le layer est vraiment isole et que l'alpha de
groupe est applique au composite.

### Intermediaires et reuse

Un intermediaire est reusable seulement si tous ces faits matchent :

- descriptor hash ;
- bounds/copy bounds ;
- target or source generation ;
- format/color interpretation ;
- usage labels ;
- sample count ;
- lifetime class ;
- owner scope ;
- active attachment separation ;
- invalidation policy.

Sinon le provider cree une nouvelle ressource ou refuse.

### MSAA

MSAA est integre dans `GPUTargetState`, `GPURenderPipelineKey`,
`GPUIntermediatePlan` et les layer/intermediate descriptors.

Plan :

```text
Render with sampleCount > 1
  -> GPUMultisamplePlan
  -> multisample target
  -> render pass
  -> ResolveMSAA to single-sample target/intermediate
  -> sample or present resolved target
```

Un mismatch de sample count entre layer/intermediate/destination-read refuse au
lieu de produire une texture samplee invalide.

## Error handling

Les resultats sont classes ainsi :

- `Executed`: route executee sans limitation connue ;
- `ExecutedWithVisualDelta`: route executee avec diff/snapshot change ;
- `ExecutedWithKnownLimitation`: route executee avec limite documentee ;
- `RefuseDiagnostic`: route invalide ou non implementee ;
- `ExpectedUnsupported`: refus attendu et stable ;
- `CrashOrException`: echec bloquant.

Les visual deltas ne bloquent pas cette phase s'ils sont documentes dans les
artifacts. Les crashs et exceptions non documentees bloquent la phase.

Reason codes a conserver ou ajouter :

- `unsupported.destination_read.active_attachment_sampled` ;
- `unsupported.destination_read.target_generation_stale` ;
- `unsupported.destination_read.copy_usage_missing` ;
- `unsupported.destination_read.texture_binding_missing` ;
- `unsupported.destination_read.pass_split_illegal` ;
- `unsupported.destination_read.shader_route_unvalidated` quand le shader ou
  l'ABI manque vraiment ;
- `unsupported.layer.target_usage_missing` ;
- `unsupported.layer.sample_count_mismatch` ;
- `unsupported.layer.parent_read_aliasing` ;
- `unsupported.intermediate.bounds_mismatch` ;
- `unsupported.intermediate.format_mismatch` ;
- `unsupported.intermediate.sample_count_mismatch` ;
- `unsupported.intermediate.generation_stale` ;
- `unsupported.msaa.adapter_capability` ;
- `unsupported.msaa.alpha_to_coverage` ;
- `unsupported.msaa.resolve_unavailable`.

## Integration progressive

L'implementation peut avancer en tranches, mais la phase reste un seul objectif
coherent :

1. ajouter les contrats `GPUIntermediatePlan` et dumps ;
2. connecter destination-read copy/intermediate et shader blend ;
3. connecter saveLayer offscreen/composite via le plan ;
4. ajouter provider/reuse d'intermediaires ;
5. integrer MSAA dans target state, key et resolves ;
6. remplacer la logique procedural de scenes par l'execution du plan ;
7. exposer telemetry et rapports ;
8. regenerer scenes/GM/dashboard.

A chaque tranche, les routes activees par defaut doivent soit s'executer, soit
refuser avec un reason code stable. Les flags de produit ne sont pas le chemin
normal de cette phase.

## Tests unitaires

Ajouter ou etendre des tests pour :

- destination-read target copy, existing intermediate, layer isolation et refus ;
- blend `Screen`/`Multiply` avec destination-read accepte ;
- saveLayer target allocation, child scope, restore order, group alpha ;
- provider intermediaire create/reuse/refuse ;
- mismatch generation, bounds, format, sample count, usage labels ;
- command stream pass split et copy-before-sample ;
- no active attachment sampling ;
- MSAA target state, pipeline key axis, resolve plan et refus capability ;
- telemetry counters et dump determinism ;
- material/pipeline keys sans concrete resource identity.

## Validation runtime et artifacts

Validation obligatoire :

- scenes `savelayer-isolated`, `savelayer-group-alpha`, `dst-read-strategy` ;
- gate boards destination-read et saveLayer ;
- scenes avec blends et intermediaires quand ajoutees ;
- dashboard scenes/offscreen ;
- dashboard ou suite GM disponible ;
- rapport listant snapshots/scores modifies ;
- rapport listant crashs/exceptions, expected unsupported, no-score et visual
  deltas.

Critere de fin :

- tests cibles verts ;
- regeneration sans crash/exception non documente ;
- deltas visuels visibles et classes ;
- compteurs copies/intermediaires/pass splits/MSAA visibles ;
- aucun sampling actif de l'attachment ;
- aucun CPU readback fallback produit ;
- aucun handle backend dans les dumps.

## Non-goals

- Ne pas finir la correction visuelle globale dans cette phase.
- Ne pas promettre une compatibilite Skia complete pour tous les saveLayer,
  filters, blends et MSAA.
- Ne pas ajouter framebuffer fetch ou input attachment non portable.
- Ne pas cacher les copies, pass splits ou intermediaires dans le payload
  gathering.
- Ne pas creer un fallback produit via CPU-rendered texture.
- Ne pas appeler les classes runtime `Phase5*`.
- Ne pas transformer `RectOnly*` en architecture cible.

## Risques

### Ordre de rendu

Les copies, layers et composites peuvent casser l'ordre visuel. Mitigation :
ordering tokens, pass splits explicites, tests de command stream et scenes
oracles.

### Lifetime et reuse

Un reuse incorrect peut sampler une texture stale. Mitigation : generation,
bounds, format, sample count, lifetime et queue retention dans le plan et le
provider.

### Regressions visuelles

La phase accepte des regressions visuelles documentees. Mitigation : artifacts
regeneres, classification des deltas et rapport de suivi pour la phase de
correction post-roadmap.

### Explosion de scope

Toute la phase 5 est large. Mitigation : un seul axe architectural,
`GPUIntermediatePlanner`, avec integration en tranches et validations a chaque
tranche.

## Questions fermees

- Activation par defaut : oui.
- Regeneration scenes/GM/dashboard : obligatoire comme anti-crash.
- Deltas visuels : acceptes s'ils sont documentes.
- Degradations visuelles : acceptables dans cette phase si elles ne crashent
  pas et si elles sont visibles dans les artifacts.
- Classes nommees `Phase5*` : non.
- Architecture cible centree sur `RectOnly*` : non.
