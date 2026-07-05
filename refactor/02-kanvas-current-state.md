# Etat actuel de Kanvas

Ce fichier resume l'etat observe cote Kanvas. Il distingue les fondations deja
solides des zones ou le runtime WGPU concret contourne encore les contrats plus
ambitieux des specs.

## Contraintes d'architecture deja posees

Les documents cible du repo sont clairs :

- ne pas porter Ganesh ou Graphite ;
- ne pas reconstruire le compilateur SkSL ;
- garder WebGPU comme backend GPU ;
- garder WGSL comme cible shader ;
- utiliser des implementations Kotlin/WGSL enregistrees pour les runtime
  effects ;
- produire des diagnostics explicites pour les refus et fallbacks ;
- separer la semantique CPU/WebGPU des details backend.

Cela donne une bonne direction. Le refactor propose ne change pas ces choix.
Il les rend plus concrets dans le runtime WGPU.

## Pipeline keys et caches : base solide

Kanvas possede deja une base importante :

- `GPUPipelineKeyPreimage.Render` decrit les axes d'une pipeline ;
- les capability facts sont triees avant hash ;
- le renderer salt est inclus ;
- `GPUExecutionObjectCache` separe key hash, subject hash et device
  generation ;
- les decisions de cache produisent de la telemetry ;
- les generations stale sont refusees.

Ce point est deja tres proche de ce qu'il faut. Il ne faut pas le remplacer par
les cles Graphite/Dawn. Il faut plutot connecter les futures capacites WGPU et
les vrais objets backend a ces contrats.

## Ressources : contrats forts, runtime encore direct

Les contrats de ressources sont prudents :

- provider refusal-first ;
- materialisation explicite ;
- diagnostics stables ;
- payload telemetry ;
- validation des operands ;
- distinction entre plan, decision et ressource materialisee.

Mais le runtime WGPU concret cree encore beaucoup d'objets localement dans le
recorder :

- buffers uniformes ;
- bind groups ;
- samplers ;
- textures temporaires ;
- pipelines locaux pour certains chemins vertex/textures.

Cela cree un ecart entre la bonne architecture de contrats et le chemin
runtime effectivement utilise.

## Runtime WGPU : ce qui fonctionne

Le runtime WGPU concret fournit deja :

- une session WGPU partagee ;
- des targets offscreen et window ;
- des caches de shader modules, bind group layouts, pipeline layouts et render
  pipelines ;
- une validation scope autour de la creation de pipeline ;
- un readback RGBA depuis staging buffer ;
- des chemins solides pour plusieurs familles de draw.

Ces points sont a conserver.

## Runtime WGPU : limites actuelles

Les limites principales sont structurelles :

1. **Passes trop courtes**
   `GPURenderer` appelle souvent `encodeOffscreenTexture` pour une operation ou
   une petite sequence. Cela augmente le nombre de command encoders, render
   passes et submissions.

2. **Ressources creees dans le recorder**
   Le recorder cree souvent les buffers, bind groups, samplers et textures au
   moment du draw. Cela contourne le modele `GPUResourceProvider`.

3. **Capacites implicites**
   Certaines limites sont codees en dur, par exemple l'alignement de copie ou
   une taille texture maximale. Une couche `WgpuCaps` rendrait ces decisions
   explicites.

4. **Duree de vie GPU trop locale**
   Les `resourceScope` locaux aident a fermer les objets, mais ne remplacent
   pas un suivi par completion GPU. Une ressource peut etre encodee par le CPU
   alors que son utilisation GPU n'est pas encore terminee.

5. **Planner intermediaire disperse**
   Les strategies `scene`, `src`, `snap` existent, mais elles sont codees dans
   `GPURenderer`. Un planner explicite rendrait les blends avances, saveLayer,
   filtres, destination-read et MSAA plus comprehensibles.

## Material lowering : attention aux pertes silencieuses

Le mapping Paint/Shader vers material descriptor est une zone sensible. Certains
wrappers sont actuellement simplifies :

- local matrix ;
- color filter ;
- working color space ;
- coord clamp ;
- blend shader non supporte qui peut retomber sur le shader source.

Ces comportements peuvent etre acceptables temporairement s'ils sont refuses ou
diagnostiques au bon niveau. Ils deviennent dangereux s'ils sont silencieux
dans les chemins promus.

Le refactor backend ne suffit donc pas. Il doit rester couple a une politique
de diagnostics : si une information Skia-like est ignoree, le dashboard doit
pouvoir l'expliquer.

## GPURenderer : role actuel

`GPURenderer` joue aujourd'hui plusieurs roles :

- orchestration du target offscreen ;
- creation de textures intermediaires ;
- dispatch par type de DisplayOp ;
- gestion destination-read/blends ;
- final composite ;
- readback.

Ce melange est comprehensible pour un chemin fonctionnel, mais il rend le
batching et la resource lifetime difficiles. A terme, `GPURenderer` devrait
orchestrer des plans plus explicites au lieu de materialiser directement autant
de passes.

## Dashboard GM observe

Le dashboard local indique :

| Mesure | Valeur |
| --- | ---: |
| Total GM | 517 |
| Passing | 422 |
| Failing | 13 |
| No score | 82 |
| Similarite moyenne | 39.8 |
| Generation | `2026-07-06T00:34:26.23618` |

Ces chiffres doivent etre lus prudemment :

- un refactor pipeline ameliore surtout les performances, la stabilite et la
  capacite a debug ;
- les differences de pixels blur/text/path demandent aussi des corrections
  algorithmiques ;
- les no-score signalent souvent des surfaces non encore couvertes ou non
  referencees.

## Forces actuelles

| Zone | Pourquoi c'est fort |
| --- | --- |
| Pipeline keys | Preimages deterministes et canonicalisees |
| Execution cache | Hit/miss/create/failure/stale visibles |
| Diagnostics | Refus explicites dans les contrats |
| Specs | Direction coherente et anti-port Graphite claire |
| Runtime WGPU | Session partagee, caches backend, smoke paths |
| Dashboard | Mesure GM deja disponible pour suivre les regressions |

## Faiblesses a traiter

| Zone | Probleme | Effet |
| --- | --- | --- |
| Caps | Pas de `WgpuCaps` central | Hypotheses dispersees |
| Passes | Trop d'encodage par operation | Overhead CPU/GPU |
| Ressources | Creation directe dans recorder | Cache et lifetime faibles |
| Queue | Pas de manager de completion explicite | Recyclage plus risque |
| Planner | Destination-read/intermediaires disperses | Blends/filtres fragiles |
| Lowering | Certaines infos paint/shader simplifiees | Fallbacks difficiles a expliquer |

## Lecture globale

Kanvas n'a pas besoin d'un changement de cap. La direction est bonne. Le besoin
est maintenant de faire converger les contrats existants et le runtime WGPU
concret :

```text
Contrats GPU deja bons
  + runtime WGPU plus discipline
  + diagnostics relies au backend
  = pipeline plus rapide, plus stable, plus explicable
```
