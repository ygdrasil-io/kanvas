# Analyse des ecarts Skia/Dawn vs Kanvas

Ce fichier compare les concepts observes dans Dawn avec l'etat actuel Kanvas et
les opportunites de refactor. Il ne propose pas de porter Graphite. Il cherche
les ecarts pratiques qui empechent Kanvas de tirer pleinement parti de WebGPU.

## Tableau principal

| Sujet | Dawn / Graphite | Kanvas actuel | Opportunite |
| --- | --- | --- | --- |
| Backend cible | Dawn proche WebGPU | WGPU direct | Bonne proximite, inspiration pertinente |
| Abstraction Graphite | Multi-backend, Skia-owned | WebGPU-only | Ne pas copier |
| Capacites | `DawnCaps` central | Constantes et decisions locales | Creer `WgpuCaps` |
| Pipeline key | Cles derivees des axes backend | Preimages Kanvas solides | Injecter caps reelles et garder les cles Kanvas |
| Command buffer | Passe preparee puis encodee | Encodage souvent par operation | Batch depuis `GPUPassCommandStream` |
| Resource provider | Reutilisation et retention | Contrats forts, runtime direct | Provider WGPU concret |
| Uniforms | Intrinsic buffers et caches | Uniform buffers par draw frequents | Uniform slab/ring |
| Bind groups | Caches single uniform et texture+sampler | Creation locale frequente | Cache par layout/ressource |
| Queue | Completion suivie | Submit direct dans plusieurs chemins | `WgpuQueueManager` |
| MSAA/resolve | Strategie selon caps | Intermediaires manuels | Planner explicite |
| Diagnostics | Erreurs backend controlees | Diagnostics forts mais pas partout backend | Relier error scopes aux decisions |
| Dashboard | Skia a infra interne | Kanvas a dashboard GM local | Ajouter metrics pipeline dans evidence |

## Ecart 1 : capacites backend

### Probleme

Sans couche de capacites centralisee, les chemins runtime peuvent prendre des
decisions differentes :

- taille texture maximale ;
- alignement bytes-per-row ;
- format supporte ;
- usages texture ;
- support MSAA ;
- support compute/storage ;
- support timestamp.

### Consequence

Les refus deviennent difficiles a expliquer. Les cles de pipeline peuvent aussi
manquer un fait backend important.

### Correction cible

Creer `WgpuCaps` :

```text
Adapter/device
  -> features
  -> limits
  -> format table
  -> WgpuCaps
  -> pipeline keys + resource plans + diagnostics
```

## Ecart 2 : encodage trop immediat

### Probleme

Kanvas rend souvent en appelant directement le target WGPU pour chaque operation
ou mini-sequence. Cela rend les chemins faciles a suivre localement, mais cela
perd la vision globale de la frame.

### Consequence

- trop de command encoders ;
- trop de render passes ;
- trop de submissions ;
- moins de possibilites de trier/batcher ;
- plus de textures intermediaires ;
- telemetry performance moins claire.

### Correction cible

Construire un batcher sur les objets Kanvas existants :

```text
DisplayOp
  -> GPUDrawPacket
  -> groupement compatible
  -> GPUPassCommandStream
  -> encodage WGPU
```

## Ecart 3 : ressources creees au mauvais niveau

### Probleme

Le recorder WGPU cree souvent directement les objets GPU. Cela donne un chemin
fonctionnel, mais le provider de ressources n'a pas la main.

### Consequence

- moins de cache ;
- moins de reutilisation ;
- fermeture locale au lieu de retention par soumission ;
- diagnostics provider non utilises ;
- difficile de mesurer l'impact de chaque draw.

### Correction cible

Faire du provider WGPU le point de passage obligatoire pour :

- uniform buffers ;
- vertex/index buffers temporaires ;
- textures upload ;
- samplers ;
- bind groups ;
- targets intermediaires ;
- staging/readback buffers.

## Ecart 4 : uniformes et petits payloads

### Probleme

Les uniform bytes sont souvent ecrits dans un buffer cree pour le draw. WebGPU
supporte mieux une strategie ou plusieurs payloads partagent un buffer avec
alignement et offset.

### Consequence

- cout CPU ;
- pression sur allocations ;
- bind groups non reutilises ;
- cache moins efficace.

### Correction cible

Uniform slab/ring :

```text
Uniform payload A -> offset 0
Uniform payload B -> offset 256
Uniform payload C -> offset 512
             meme GPUBuffer
```

Les offsets doivent respecter les limites de `WgpuCaps`.

## Ecart 5 : queue et lifetime

### Probleme

Une ressource WGPU ne doit pas etre consideree libre seulement parce que le code
CPU a fini de l'encoder. Elle doit rester vivante jusqu'a la fin de la
soumission GPU qui l'utilise.

### Consequence

- risque sur readback ;
- risque sur textures temporaires ;
- purge cache trop tot ou trop tard ;
- impossibilite de produire des stats de frame fiables.

### Correction cible

`WgpuQueueManager` minimal :

```text
submit(commandBuffer, resources)
  -> submissionId
  -> completion callback/future
  -> release resources when done
```

## Ecart 6 : destination-read et intermediaires

### Probleme

Les operations qui lisent la destination ou passent par un layer ont besoin
d'une planification claire. Aujourd'hui, une partie de cette logique est dans
`GPURenderer`.

### Consequence

- logique difficile a reutiliser ;
- cout des copies moins visible ;
- differents chemins peuvent diverger ;
- diagnostics moins precis.

### Correction cible

Un planner explicite :

- decide s'il faut snapshot la destination ;
- choisit texture intermediaire reutilisable ou nouvelle ;
- choisit copy, render pass ou compute ;
- documente le fallback ;
- expose la telemetry.

## Ecart 7 : material lowering

### Probleme

Certaines informations paint/shader peuvent etre ignorees dans le lowering.

### Consequence

Un rendu peut sembler "passer par GPU" tout en ayant perdu une semantique
importante. Cela complique la lecture du dashboard.

### Correction cible

Relier chaque perte de semantique a une decision explicite :

- supporte ;
- refuse avec diagnostic ;
- fallback CPU ;
- approximation marquee ;
- route experimentale non promue.

## Ce qui est deja aligné

Kanvas est deja aligne sur plusieurs lecons :

- les cles sont Kanvas-owned ;
- les specs interdisent de copier Graphite ;
- `GPUPassCommandStream` existe comme concept cible ;
- `GPUResourceProvider` existe comme contrat ;
- les caches d'execution ont telemetry ;
- les diagnostics refusent par defaut.

L'ecart n'est donc pas conceptuel. Il est dans la materialisation runtime.

## Risques si on ne refactor pas

| Risque | Impact |
| --- | --- |
| Multiplication des passes | Performances faibles sur scenes reelles |
| Allocations par draw | Instabilite CPU et GC/ressources natives |
| Fallbacks implicites | Dashboard difficile a interpreter |
| Pas de caps centralisees | Bugs backend selon device |
| Lifetime locale | Readbacks/intermediaires fragiles |
| GPURenderer trop gros | Chaque nouvelle famille augmente la complexite |

## Risques du refactor

| Risque | Mitigation |
| --- | --- |
| Refactor trop large | Phases petites, chemins existants conserves |
| Nouveau provider trop ambitieux | Commencer par uniforms/bind groups |
| Batching casse l'ordre de rendu | Sort windows strictes et tests ordre |
| Caps changent les cles | Versionner rendererSalt/capabilityFacts |
| Diagnostics verbeux | Codes stables, dumps utiles mais courts |

## Conclusion

La meilleure opportunite est de rapprocher le runtime WGPU concret des contrats
deja ecrits. Dawn montre comment faire cela efficacement, mais les noms, les
objets publics et les dumps doivent rester Kanvas.
