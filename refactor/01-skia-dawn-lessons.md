# Lecons utiles de Skia Dawn

Ce fichier resume ce que le backend Dawn de Skia apporte d'interessant pour
Kanvas. Dawn est interessant parce qu'il parle une langue proche de WebGPU :
device, queue, command encoder, render pass encoder, bind groups, buffers,
textures, samplers, pipeline layouts, render pipelines.

La couche Graphite au-dessus de Dawn reste une reference d'organisation, mais
pas une architecture a porter.

## Vue d'ensemble

Dans Skia, Graphite prepare une representation backend-near. Le backend Dawn
transforme ensuite cette representation en operations proches de WebGPU :

- creation de pipelines ;
- creation de bind groups ;
- encodage de render/compute/copy passes ;
- soumission sur queue ;
- retention des ressources jusqu'a completion ;
- adaptation aux capacites reelles du device.

Pour Kanvas, la partie interessante commence surtout au moment ou Graphite a
deja fait son travail. Autrement dit : il faut s'inspirer de Dawn comme backend
WebGPU, pas de Graphite comme framework complet.

## `DawnCaps` : le centre des decisions backend

`DawnCaps` regroupe les faits de capacite du backend :

- formats supportes ;
- usages texture possibles ;
- sample counts ;
- support storage buffers ;
- support compute ;
- alignements ;
- support timestamp query ;
- support load/resolve ;
- support MSAA render-to-single-sampled ;
- construction des cles de pipeline.

Ce point est majeur pour Kanvas. Sans equivalent `GPUCaps`, le code finit par
mettre des constantes dans le runtime et par prendre des decisions locales.
C'est dangereux parce que deux chemins peuvent croire des choses differentes
sur le meme device.

Le modele a reprendre :

```text
Device WebGPU
  -> lecture des features et limites
  -> table GPUCaps
  -> pipeline keys
  -> resource plans
  -> diagnostics/fallbacks
```

Ce qu'il ne faut pas reprendre :

- les cles Graphite exactes ;
- les formats internes Skia ;
- les chemins SkSL.

## `DawnCommandBuffer` : encoder beaucoup, puis soumettre

`DawnCommandBuffer` prend des passes preparees et les encode dans un command
buffer. Il ne soumet pas chaque draw independamment.

Idees importantes :

- une render pass commence seulement quand les attachments et l'etat sont
  connus ;
- les draw commands sont rejouees dans l'ordre prepare ;
- les uniform buffers et bind groups sont synchronises avant le draw ;
- les copies texture/buffer sont separees des render passes ;
- les compute passes suivent une logique similaire ;
- les readbacks et maps asynchrones sont attaches a la soumission.

Pour Kanvas, le message est simple : il faut separer la construction logique du
rendu et l'encodage GPU. Un `GPUPassCommandStream` doit devenir la source
principale de l'encodage, plutot que d'avoir des fonctions de rendu qui creent
directement des objets GPU au fil de l'eau.

## Render pass et operations compatibles

Dawn encode plusieurs draw commands dans une meme render pass quand c'est legal.
Il evite de terminer la pass trop tot. C'est important parce que WebGPU aime les
passes coherentes :

- meme target ;
- meme format ;
- etat compatible ;
- textures et buffers deja prepares ;
- scissor/viewport changes encodes comme commandes, pas comme nouvelles passes.

Kanvas peut appliquer le meme principe sans copier les classes Dawn :

```text
Draws compatibles
  -> un batch
  -> une render pass
  -> plusieurs setPipeline / setBindGroup / draw
```

Les operations non compatibles restent separees :

- destination-read ;
- saveLayer ;
- filtre qui demande un intermediaire ;
- readback ;
- compute ;
- upload/copy.

## Uniforms et bind groups

Un point tres concret dans Dawn : les petites donnees uniformes ne sont pas
gerees comme une allocation fraiche par draw.

Dawn utilise notamment :

- un gestionnaire d'intrinsic constants ;
- des buffers reutilises ;
- un petit null buffer ;
- des bind groups single-uniform caches ;
- des bind groups texture+sampler caches ;
- des offsets dynamiques quand le layout le permet.

Pour Kanvas, cela suggere un `GPUResourceProvider` avec :

- une uniform slab ou ring buffer ;
- un alignement lu depuis `GPUCaps.minUniformBufferOffsetAlignment` ;
- une cle de cache par layout + buffer + plage ;
- une cle de cache par texture view + sampler descriptor ;
- une strategie claire pour les ressources nulles ;
- des compteurs de hit/miss/creation.

Le padding de la slab doit etre une decision backend. D3D12 impose
typiquement 256 octets pour les offsets dynamiques uniformes ; si Metal ou
Vulkan exposent un alignement plus fin via WebGPU, Kanvas doit pouvoir en
profiter sans changer les shaders.

Le benefice attendu est important parce que le runtime actuel cree souvent
des uniform buffers, bind groups, textures et samplers directement dans le
recorder.

## `DawnResourceProvider` : materialiser, reutiliser, retenir

Dawn separe la demande de ressource et la ressource GPU concrete. Le provider
decide s'il peut reutiliser, creer, refuser ou purger.

Ce pattern correspond deja aux specs Kanvas, mais le runtime GPU concret ne
l'exploite pas encore assez.

Les idees a reprendre :

- un seul endroit responsable des buffers, textures, samplers et bind groups ;
- des ressources temporaires recyclees ;
- des ressources retenues jusqu'a la fin GPU ;
- des ressources cachees invalidees quand le device change ;
- des refus explicites au lieu de comportements silencieux.

## `DawnQueueManager` : savoir quand le GPU a fini

Dawn ne considere pas qu'une ressource est libre uniquement parce que le code
CPU a fini d'encoder. Il suit les soumissions et attend la completion.

Kanvas devrait avoir un equivalent minimal :

- `submissionId` ;
- liste de ressources retenues ;
- callback/future de completion ;
- purge des ressources terminees ;
- readbacks lies a une soumission precise ;
- `tick()` pour faire avancer les completions.

Cela reduit le risque de fermer trop tot une ressource encore utilisee par le
GPU, surtout sur les chemins natifs et les readbacks.

## MSAA, resolve et destination-read

Dawn adapte ses strategies aux capacites :

- load/resolve natif si disponible ;
- emulation par blit/draw si necessaire ;
- render area si supportee ;
- chemins speciaux pour attachments MSAA.

Kanvas a deja des patterns `scene`, `src`, `snap` pour certains blends avances.
Mais ces patterns devraient devenir des plans explicites, pas rester disperses
dans `GPURenderer`.

Une bonne cible Kanvas :

```text
Operation avec destination-read
  -> planifier snapshot destination
  -> rendre la source
  -> appliquer le blend/filter
  -> composer dans la scene
  -> produire diagnostics et telemetry
```

## Graphics pipeline

Dawn construit les render pipelines a partir :

- du layout de bindings ;
- du vertex layout ;
- du format target ;
- du blend state ;
- du depth/stencil state ;
- du sample count ;
- du module shader.

Kanvas a deja une bonne base de `pipeline key`. La lecon Dawn est plutot de
s'assurer que chaque axe qui change la pipeline est dans la cle, et que les
valeurs uniformes qui ne changent pas le layout restent hors cle.

Cela evite deux erreurs :

- trop peu de cles : reuse incorrect d'un pipeline ;
- trop de cles : explosion du cache pour des valeurs qui devraient rester des
  uniforms.

## Telemetry et erreurs backend

Dawn a des chemins pour verifier les erreurs backend, suivre la creation de
pipelines et lire des stats GPU selon les capacites.

Kanvas a deja des diagnostics forts, mais devrait les connecter plus bas :

- erreurs de creation pipeline ;
- erreurs de bind group layout ;
- erreurs de texture format/usage ;
- erreurs de submit ;
- echec de map/readback ;
- temps de creation pipeline ;
- hit/miss cache ;
- nombre de passes et submissions par frame.

## Resume des lecons transposables

| Sujet | Idee Dawn | Adaptation Kanvas |
| --- | --- | --- |
| Capacites | `DawnCaps` centralise features/limits | `GPUCaps` alimente cles, plans et diagnostics |
| Encodage | `DawnCommandBuffer` encode des passes preparees | encoder depuis `GPUPassCommandStream` |
| Ressources | `DawnResourceProvider` reutilise et retient | `GPUResourceProvider` concret |
| Uniforms | intrinsic buffers, null buffer, caches bind group | uniform slab/ring + caches bind group |
| Queue | completion de soumission | `GPUQueueManager` minimal |
| MSAA/readback | strategie selon caps | planner destination-read/MSAA/intermediaires |
| Erreurs | error checker backend | error scopes + diagnostics Kanvas |

## Limite importante

Dawn est utile parce qu'il est proche de WebGPU. Graphite est utile comme
source d'idees de tri, de batching et de materialisation. Mais Kanvas ne doit
pas importer la taxonomie Graphite. Les noms, contrats et dumps doivent rester
Kanvas-owned.
