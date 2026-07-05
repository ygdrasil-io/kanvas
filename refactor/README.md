# Rapport de refactor du pipeline GPU Kanvas

Ce dossier analyse ce que Kanvas peut apprendre du backend Dawn de Skia, sans
porter Graphite. Le but est de rendre le pipeline GPU Kanvas plus robuste,
plus rapide et plus facile a diagnostiquer, tout en gardant les choix
d'architecture actuels :

- WebGPU reste le backend GPU.
- WGSL reste la cible shader.
- Graphite et Dawn servent de references d'algorithmes, pas de code a copier.
- SkSL reste uniquement une surface de compatibilite Skia, pas un compilateur a
  reconstruire dans Kanvas.

Le rapport est volontairement separe en deux niveaux :

- ce `README.md` donne une synthese accessible ;
- les autres fichiers donnent les preuves, les ecarts et une roadmap plus
  precise.

## Lecture recommandee

1. Lire ce fichier pour comprendre les enjeux.
2. Lire [01-skia-dawn-lessons.md](01-skia-dawn-lessons.md) pour voir ce qui est
   interessant dans Dawn.
3. Lire [02-kanvas-current-state.md](02-kanvas-current-state.md) pour voir ou
   Kanvas en est aujourd'hui.
4. Lire [03-gap-analysis.md](03-gap-analysis.md) et
   [04-target-pipeline.md](04-target-pipeline.md) pour comprendre le refactor
   propose.
5. Lire [05-roadmap.md](05-roadmap.md) pour transformer l'analyse en travail
   concret.
6. Lire [06-diagrams.md](06-diagrams.md) si les schemas aident a visualiser.
7. Lire [07-source-map.md](07-source-map.md) pour retrouver les fichiers
   sources consultes.

## Synthese courte

Kanvas a deja une bonne direction : les specs disent explicitement de ne pas
porter Ganesh ou Graphite, de garder WebGPU, et de separer les contrats
Kanvas des classes Skia. Le code a deja des fondations utiles : des
`pipeline keys` (cles de pipeline) deterministes, des caches avec telemetry,
des diagnostics de refus, et une session WGPU partagee.

Le point faible principal est plus bas niveau : le runtime WGPU concret encode
encore trop souvent le rendu operation par operation. Beaucoup de chemins
creent des buffers, textures, samplers ou `bind groups` (groupes de liaisons)
au moment du draw. Cela fonctionne, mais cela limite la performance, complique
la gestion de duree de vie GPU, et rend les futurs chemins texte, image, blur,
destination-read et MSAA plus chers a stabiliser.

La lecon la plus utile de Skia/Dawn n'est donc pas Graphite lui-meme. C'est la
discipline WebGPU que Dawn impose derriere Graphite :

- une table de capacites claire ;
- un `command buffer` (tampon de commandes) qui batch les operations
  compatibles ;
- un provider de ressources qui reutilise buffers, textures, samplers et
  bind groups ;
- une queue de soumission qui sait quand le GPU a fini ;
- des strategies explicites pour les copies, readbacks, MSAA resolve et
  destination reads ;
- des erreurs backend capturees au bon endroit.

## Ce qu'il ne faut pas copier

Graphite contient beaucoup de couches concues pour Skia :

- abstraction multi-backend ;
- `DrawList`, `DrawPass`, `RenderStep` et `ResourceProvider` au sens Skia ;
- conversion SkSL vers WGSL ;
- cache de pipelines lie aux descriptors Graphite ;
- systeme complet de proxies, tasks, ownership et resource cache multi-API.

Ces idees ne doivent pas devenir des noms de packages Kanvas ni des classes
copiees. Kanvas a deja ses propres contrats : `GPUDrawPacket`,
`GPUPassCommandStream`, `GPUResourceProvider`, `GPUPipelineKeyPreimage`,
`GPUExecutionObjectCache`. Le refactor doit renforcer ces contrats, pas les
remplacer par Graphite.

## Ce qu'il faut reprendre

### 1. Une couche `WgpuCaps`

Aujourd'hui, certaines decisions WGPU sont encore implicites ou codees en dur.
Une couche `WgpuCaps` devrait centraliser :

- formats couleur/depth-stencil supportes ;
- usages texture/buffer autorises ;
- limites de taille texture ;
- alignements de copie et de uniform buffer ;
- support compute, storage buffer, timestamps, MSAA ;
- strategies disponibles pour load/resolve/destination-read ;
- faits de capacite injectes dans les cles de pipeline.

Cela evite les hypotheses cachees et rend les fallbacks plus propres.

### 2. Un vrai provider de ressources WGPU

Dawn reutilise fortement les petits objets GPU : uniform buffers, null buffers,
bind groups single-uniform, bind groups texture+sampler, intrinsic constants.

Kanvas devrait ajouter un `WgpuResourceProvider` concret qui materialise les
contrats existants au lieu de laisser le recorder creer beaucoup de ressources
locales. Le gain attendu est double :

- moins d'allocations et moins de `queue.writeBuffer` par draw ;
- une duree de vie GPU plus explicite.

### 3. Un batcher de passes

Le runtime actuel sait rendre, mais il soumet souvent des passes courtes :
une operation, une texture intermediaire, une soumission. Dawn montre qu'il faut
grouper les draws compatibles avant l'encodage.

Kanvas devrait introduire un `GpuPassBatcher` ou equivalent, branche sur les
contrats existants :

```text
DisplayList
  -> analyse / material lowering
  -> GPUDrawPacket
  -> GPUPassCommandStream
  -> WGPU encoder
```

Le but n'est pas de tout batcher. Les operations avec destination-read,
saveLayer, filtre ou blend avance peuvent rester multi-pass. Mais les fills,
images simples, gradients simples et draws compatibles doivent pouvoir partager
une pass.

### 4. Un `QueueManager` minimal

Dawn suit les soumissions GPU et recycle les ressources seulement quand le GPU
a termine. Kanvas a deja une session WGPU partagee, mais il lui manque une
couche explicite de soumission :

- numero de submission ;
- ressources retenues jusqu'a completion ;
- readbacks attaches a la bonne soumission ;
- telemetry sur temps de creation pipeline, cache hit/miss, wait GPU ;
- purge safe des ressources temporaires.

### 5. Un planner destination-read / MSAA / intermediates

Les blends avances, filtres, blur, saveLayer et certains chemins texte/image
ont besoin de textures intermediaires. Dawn gere ces cas avec des decisions
basees sur les caps. Kanvas doit faire pareil, mais avec ses propres objets :

- `scene`, `src`, `snap` ne doivent pas rester seulement des conventions dans
  `GPURenderer` ;
- les copies et resolves doivent etre planifies ;
- chaque fallback doit produire un diagnostic stable.

## Enjeu pour le dashboard Skia GM

Le dashboard local genere indique :

- total : 517 GM ;
- passing : 422 ;
- failing : 13 ;
- no score : 82 ;
- similarite moyenne : 39.8 ;
- generation : `2026-07-06T00:34:26.23618`.

Le refactor propose ne corrigera pas automatiquement toutes les differences de
pixels. Les familles blur, text, path, image, runtime-effect et composite ont
aussi besoin d'ameliorations algorithmiques. Mais sans socle pipeline plus
stable, chaque correction restera plus chere :

- trop de passes rendent les cas composites fragiles ;
- trop d'allocations reduisent la stabilite performance ;
- un manque de caps rend les refus/fallbacks difficiles a expliquer ;
- une duree de vie GPU floue rend les readbacks et textures intermediaires
  plus risques.

## Priorites recommandees

1. Creer `WgpuCaps` et remplacer les hypotheses codees en dur par des faits de
   capacite.
2. Ajouter un `WgpuResourceProvider` concret pour uniform slabs, null buffers,
   textures, samplers et bind groups reutilisables.
3. Brancher un batcher de passes sur `GPUDrawPacket` /
   `GPUPassCommandStream`.
4. Ajouter une queue de soumission avec suivi de completion et retention des
   ressources.
5. Formaliser le planner destination-read/MSAA/intermediaires.
6. Migrer progressivement `GPURenderer` vers ce chemin, famille par famille,
   avec preuves dashboard.

## Decision proposee

Le refactor doit etre incremental :

- ne pas reecrire tout le renderer ;
- ne pas introduire une copie de Graphite ;
- renforcer d'abord les couches qui existent deja ;
- garder les chemins actuels comme reference/fallback pendant la migration ;
- mesurer chaque phase avec tests unitaires, smoke tests WGPU et dashboard GM.

Le premier chantier utile est donc : `WgpuCaps` + `WgpuResourceProvider`.
Ce duo pose les bases pour tout le reste sans changer immediatement la
semantique de rendu.
