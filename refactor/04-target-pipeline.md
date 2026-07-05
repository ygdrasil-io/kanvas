# Architecture cible proposee

Cette proposition de pipeline cible reste dans les contraintes Kanvas :

- pas de port Graphite ;
- pas de SkSL dynamique ;
- WebGPU/WGSL direct ;
- diagnostics explicites ;
- CPU et GPU partagent la semantique de rendu autant que possible.

## Objectif

Passer d'un runtime qui encode souvent chaque operation directement a un runtime
qui suit ce flux :

```text
DisplayList
  -> analyse
  -> material lowering
  -> draw packets
  -> resource planning
  -> pass batching
  -> command stream
  -> WGPU encoding
  -> queue submission
  -> completion + resource recycling
```

Le refactor doit etre progressif. Les chemins existants restent utilisables
pendant la migration.

## Composants cibles

### `WgpuCaps`

Responsabilite :

- lire les features et limits WebGPU ;
- construire une table de formats ;
- exposer les alignements ;
- exposer les strategies supportees ;
- fournir des capability facts aux cles de pipeline ;
- produire des diagnostics en cas d'operation impossible.

Ce composant ne rend rien. Il informe les autres.

### `WgpuResourceProvider`

Responsabilite :

- materialiser les plans de ressources Kanvas ;
- reutiliser buffers, textures, views, samplers et bind groups ;
- gerer uniform slab/ring ;
- retenir les ressources jusqu'a completion GPU ;
- produire telemetry hit/miss/create/refuse ;
- respecter `WgpuCaps`.

Il doit devenir le passage normal entre `GPUPassCommandStream` et les handles
WGPU.

### `GpuPassBatcher`

Responsabilite :

- prendre des `GPUDrawPacket` ou commandes normalisees ;
- regrouper seulement ce qui est legal ;
- respecter l'ordre de rendu ;
- isoler destination-read, saveLayer, filtres, readback, compute et copies ;
- produire un `GPUPassCommandStream`.

Le batcher ne doit pas cacher les decisions importantes. Ses decisions doivent
etre dumpables.

### `WgpuCommandEncoder`

Responsabilite :

- prendre `GPUPassCommandStream` ;
- demander les ressources au provider ;
- encoder begin pass, set pipeline, set bind group, set buffer, draw ;
- encoder copies/readbacks hors render pass ;
- ouvrir des error scopes aux endroits utiles ;
- renvoyer un command buffer pret a soumettre.

Ce composant remplace progressivement les encodages directs disperses.

### `WgpuQueueManager`

Responsabilite :

- soumettre les command buffers ;
- attribuer un `submissionId` ;
- retenir les ressources ;
- detecter la completion GPU ;
- liberer ou recycler les ressources ;
- fournir telemetry de submission.

Il doit rester minimal au debut. Le but n'est pas de creer un scheduler
complexe, seulement une duree de vie correcte.

### `IntermediatePlanner`

Responsabilite :

- planifier destination-read ;
- planifier saveLayer ;
- planifier filtres et blur ;
- choisir textures intermediaires ;
- choisir copy/render/compute selon `WgpuCaps` ;
- produire diagnostics et telemetry.

Au debut, ce planner peut couvrir seulement les cas deja presents dans
`GPURenderer`, puis s'etendre.

Exemple de decision simple :

```text
drawRect srcOver opaque
  -> pas de destination-read explicite
  -> fixed-function blend possible
  -> batch compatible si target/clip/pipeline le permettent
```

Exemple de `saveLayer` ou blend qui lit la destination :

```text
saveLayer(bounds, paint)
  -> allouer ou reutiliser layerTexture(bounds)
  -> rendre les draws enfants dans layerTexture
  -> si le paint final demande la destination:
       snapshot scene -> snapTexture
       composer layerTexture + snapTexture -> scene
     sinon:
       composer layerTexture -> scene
  -> liberer/recycler layerTexture apres completion GPU
  -> produire diagnostics: textures, copies, fallback, submissionId
```

## Flux cible detaille

```text
1. Recording
   DisplayList -> commandes normalisees

2. Analysis
   Determiner bounds, clip, blend, besoin destination-read, payloads

3. Material lowering
   Paint/Shader -> descriptors Kanvas + WGSL/material snippets

4. Packetisation
   Chaque draw devient un GPUDrawPacket avec cles et metadata

5. Resource planning
   Le provider sait ce qu'il devra materialiser

6. Batching
   Les packets compatibles deviennent une pass

7. Encoding
   Le command stream devient WGPU commands

8. Submit
   La queue soumet et retient les ressources

9. Completion
   Les ressources temporaires sont recyclees
```

## Regles de batching

Un batch peut regrouper des draws si :

- meme target ;
- meme format target ;
- meme sample count ;
- ordre de rendu legal ;
- pas de destination-read entre eux ;
- pas de readback/copy intercale ;
- etats compatibles ou changeables dans la meme pass ;
- ressources materialisables avant encodage.

Un batch doit etre coupe si :

- saveLayer commence ou finit ;
- filtre demande une texture intermediaire ;
- blend avance lit la destination ;
- un upload/copy/readback est necessaire ;
- le clip impose une strategie incompatible ;
- le target change.

## Uniform slab

Le modele cible pour les petits uniforms :

```text
Frame uniform slab
  [draw 1 payload][padding][draw 2 payload][padding][draw 3 payload]

Bind group
  -> meme buffer
  -> offset dynamique
```

Chaque offset doit etre aligne sur `WgpuCaps.minUniformBufferOffsetAlignment`.
La valeur peut etre 256 octets sur D3D12, mais le provider ne doit pas la coder
en dur : elle vient des limits du backend. Si l'alignement expose est plus fin,
la slab peut reduire le padding et augmenter sa densite.

Si les offsets dynamiques ne sont pas disponibles ou pas adaptes, le provider
peut choisir une variante :

- plusieurs slabs ;
- bind group cache par plage ;
- buffer dedie pour un cas particulier ;
- refus explicite si la contrainte backend est bloquante.

## Bind group cache

Les cles de cache doivent rester simples :

- layout id ;
- buffer id + offset/size si necessaire ;
- texture view id ;
- sampler descriptor id ;
- device generation ;
- capability class.

Les valeurs uniformes elles-memes ne doivent pas creer une nouvelle pipeline.
Elles peuvent creer une nouvelle plage de slab, mais pas une nouvelle cle de
render pipeline.

## Error handling

Chaque etape doit pouvoir refuser proprement :

| Etape | Refus typique |
| --- | --- |
| `WgpuCaps` | feature absente, format non supporte |
| Material lowering | shader/wrapper non supporte |
| Resource provider | budget, usage texture invalide, stale generation |
| Batcher | ordre non batchable |
| Encoder | validation WebGPU |
| Queue | submit/readback/map failure |

Le refus doit contenir :

- code stable ;
- message lisible ;
- route/famille concernees ;
- fallback choisi ou absence de fallback ;
- lien vers evidence/test si disponible.

## Testing cible

### Tests unitaires

- `WgpuCaps` : features/limits simules ;
- uniform slab : alignement, overflow, reuse ;
- bind group cache : hit/miss/stale generation ;
- batcher : ordre preserve, coupes legales ;
- planner : destination-read et saveLayer.

### Tests runtime

- smoke WGPU offscreen ;
- pipeline cache telemetry ;
- readback avec completion ;
- ressources retenues jusqu'a completion.

### Tests GM/dashboard

- suivre nombre de passes/submissions par GM ;
- suivre cache hit/miss par famille ;
- comparer familles blur, composite, image, text, path ;
- refuser les promotions sans diagnostics.

## Migration de `GPURenderer`

`GPURenderer` ne doit pas etre remplace en une fois. Migration proposee :

1. instrumenter le nombre de passes/submissions ;
2. brancher `WgpuCaps` sans changer le rendu ;
3. brancher `WgpuResourceProvider` pour uniforms/bind groups simples ;
4. brancher `WgpuQueueManager` pour retenir les ressources jusqu'a completion ;
5. batcher les rectangles/gradients simples ;
6. batcher les images simples ;
7. deplacer destination-read vers `IntermediatePlanner` ;
8. migrer texte/path/blur quand les primitives sont stables.

## Definition d'un refactor reussi

Le refactor est reussi si :

- les tests existants restent verts ou les ecarts sont documentes ;
- le dashboard GM est regenerable ;
- les diagnostics expliquent les refus ;
- le nombre de passes/submissions baisse sur les scenes simples ;
- les caches ont des hit/miss observables ;
- les ressources temporaires ne sont pas fermees avant completion GPU ;
- aucun nom ou ownership Graphite ne fuit dans l'API Kanvas.
