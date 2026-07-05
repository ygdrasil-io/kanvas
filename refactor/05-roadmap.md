# Roadmap de refactor

Cette roadmap transforme l'analyse en phases courtes. Chaque phase doit pouvoir
etre revue et testee sans attendre une reecriture complete du renderer.

## Phase 0 : baseline et instrumentation

### Objectif

Mesurer l'etat actuel avant de changer les chemins runtime.

### Travail

- Compter render passes par frame/GM.
- Compter command buffers et submissions.
- Compter creations de buffers, textures, samplers, bind groups.
- Exposer hit/miss des caches existants.
- Lier ces chiffres au dashboard ou a un rapport local.

### Validation

- Une scene simple montre ses passes/submissions.
- Le dashboard GM reste generable.
- Aucune semantique de rendu ne change.

### Risque

Instrumentation trop verbeuse. Garder des compteurs agreges par famille et par
frame, pas des logs par pixel/draw sauf mode debug.

## Phase 1 : `WgpuCaps`

### Objectif

Centraliser les faits backend.

### Travail

- Creer `WgpuCaps`.
- Lire limits/features disponibles depuis le device/adapter expose par la
  couche WGPU.
- Remplacer les constantes dispersees quand possible.
- Exposer les facts utiles aux pipeline keys.
- Ajouter diagnostics de refus pour format/usage/size/alignment.
- Exposer `minUniformBufferOffsetAlignment` pour la strategie de slab.

### Validation

- Tests unitaires sur caps simules.
- Smoke WGPU offscreen toujours vert.
- Les cles de pipeline incluent les facts utiles mais pas les valeurs uniforms.
- Les refus mentionnent la capacite manquante.
- Les tests couvrent au moins un alignement 256 octets et un alignement plus
  fin.

### Risque

Changer trop tot les cles de pipeline peut invalider beaucoup de snapshots.
Utiliser un renderer salt/version explicite.

## Phase 2 : `WgpuResourceProvider` minimal

### Objectif

Faire passer les ressources les plus frequentes par un provider concret.

### Travail

- Uniform slab/ring pour petits payloads.
- Padding base sur `WgpuCaps.minUniformBufferOffsetAlignment`, pas sur une
  constante globale.
- Null buffer.
- Cache bind group single-uniform.
- Cache texture+sampler simple.
- Device generation dans les cles.
- Telemetry hit/miss/create/refuse.

### Validation

- Tests d'alignement uniform.
- Tests cache hit/miss/stale generation.
- Scene simple : moins de creations de buffers/bind groups.
- Aucun changement visuel attendu.

### Risque

Un provider trop general au debut ralentirait le chantier. Commencer par les
cas les plus frequents : uniform + texture/sampler.

## Phase 3 : queue manager et resource lifetime

### Objectif

Retenir les ressources jusqu'a completion GPU avant de generaliser le batching.

### Travail

- Ajouter `WgpuQueueManager` minimal.
- Associer `submissionId` aux ressources.
- Suivre completion.
- Retenir readback/staging buffers.
- Recycler les ressources terminees.
- Ajouter telemetry de submit/wait.

### Validation

- Tests de retention : ressource liberee seulement apres completion simulee.
- Smoke readback.
- Pas de regression sur offscreen/window.

### Risque

Les APIs de completion peuvent varier selon backend natif/web. Encapsuler la
difference dans le queue manager.

## Phase 4 : batching des passes simples

### Objectif

Regrouper les draws compatibles sans toucher aux cas complexes.

### Travail

- Creer ou activer un `GpuPassBatcher`.
- Supporter fills solides et gradients simples.
- Garder destination-read, saveLayer, filters et text complex hors batch au
  debut.
- Produire dumps de decisions de batch.
- Encoder depuis `GPUPassCommandStream`.
- Guard explicite : si une ressource materialisee n'est pas retenue par le
  `WgpuQueueManager`, le batcher coupe ou refuse au lieu de recycler tot.

### Validation

- Tests ordre de rendu.
- Tests batch cut sur target/blend/destination-read.
- Scene de rectangles : moins de passes/submissions.
- GM impact nul ou explique.

### Risque

Le batching peut casser l'ordre visuel ou exposer des bugs de lifetime. Regle
stricte : si doute, couper le batch et garder le chemin existant.

## Phase 5 : planner destination-read / intermediates / MSAA

### Objectif

Sortir la logique `scene/src/snap` du renderer procedural.

### Travail

- Definir un plan explicite pour destination-read.
- Documenter au moins un plan `srcOver` simple et un plan `saveLayer` avec
  snapshot destination.
- Reutiliser textures intermediaires via provider.
- Ajouter decisions copy/render/compute selon `WgpuCaps`.
- Integrer saveLayer et blends avances progressivement.
- Produire diagnostics de fallback.

### Validation

- Tests de plan pour blend avance.
- Tests de reuse intermediaire.
- GM composite compares avant/apres.
- Compteurs de copies/intermediaires visibles.

### Risque

Les cas composite melangent fidelite et performance. Garder une premiere phase
fonctionnellement equivalente avant d'optimiser.

## Phase 6 : migration progressive des familles GM

### Objectif

Utiliser le nouveau socle pour ameliorer les familles dashboard.

### Travail

- Images simples : texture cache + batching.
- Text : atlas/lifetime plus stable.
- Path : coverage/intermediaires plus explicites.
- Blur/filter : planner intermediaire + ressources reutilisees.
- Runtime-effect : diagnostics et WGSL registered descriptors.

### Validation

- Dashboard regenere.
- Deltas par famille documentes.
- Aucune promotion sans reference ou refus explicite.
- Les no-score restent distingues des vrais fails.

### Risque

Melanger refactor backend et fixes algorithmiques peut rendre les regressions
difficiles a comprendre. Faire une famille a la fois.

## Ordre recommande

```text
0. Baseline
1. WgpuCaps
2. ResourceProvider minimal
3. Queue/lifetime
4. Pass batching simple
5. Intermediate planner
6. Familles GM
```

## Criteres de succes globaux

- Moins de passes/submissions sur scenes simples.
- Moins de creations de bind groups par frame.
- Reuse measurable des uniform buffers.
- Diagnostics plus precis pour formats/features/fallbacks.
- Readbacks relies a une submission.
- Dashboard GM regenerable apres chaque phase.
- Pas de port Graphite ni de dependance SkSL dynamique.

## Ce qu'il faut eviter

- Refaire tout `GPURenderer` en une seule PR.
- Ajouter une abstraction multi-backend generique.
- Copier les noms Graphite comme architecture publique.
- Optimiser avant d'avoir les compteurs.
- Promouvoir un rendu qui ignore silencieusement local matrix, color filter ou
  working color space.
- Cacher les fallbacks dans le runtime sans diagnostic stable.
