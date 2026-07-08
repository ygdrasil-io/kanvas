# Design: phase 4 batching des passes simples

## Objectif

Traiter toute la phase 4 de `refactor/05-roadmap.md` avec une tranche complete
mais conservative.

Le but est de regrouper les draws compatibles sans modifier les cas complexes.
La phase couvre :

- la creation d'un vrai `GPUPassBatcher` ;
- le support des fills solides et gradients simples ;
- les cuts explicites pour destination-read, saveLayer, filters et text complex ;
- les dumps de decisions de batch ;
- l'encodage depuis `GPUPassCommandStream` ;
- la garde lifetime : une ressource materialisee non retenue par
  `GPUQueueManager` coupe ou refuse le batch.

Les chemins existants restent disponibles comme fallback. Cette phase ne
cherche pas a batcher les blends avances, les layers, les filters, le texte
complexe, les copies, les readbacks ou les uploads.

## Pourquoi cette phase est necessaire

Les phases precedentes ont pose le socle :

- `GPUCaps` centralise les faits backend utiles ;
- `GPUResourceProvider` materialise les payloads et ressources simples ;
- `GPUQueueManager` retient les ressources jusqu'a completion GPU ;
- `GPUDrawPacketStream` et `GPUPassCommandStream` existent deja comme frontiere
  dumpable avant encodage.

Le runtime natif peut deja enregistrer plusieurs draws dans un render pass dans
certains chemins, notamment les fullscreen uniform draws. Mais la decision de
grouper n'est pas encore formalisee par un `GPUPassBatcher` complet : il manque
les cuts par target/blend/destination-read, les dumps de decision, et la preuve
que les ressources d'un batch restent retenues par la queue.

La phase 4 rend cette decision explicite :

```text
GPUDrawPacketStream
  -> GPUPassBatcher
  -> GPUPassBatchPlan
  -> GPUPassCommandStream
  -> GPUCommandEncoderPlan
  -> GPUQueueManager
```

## Choix retenu

Nous retenons l'approche **phase 4 complete, conservative end-to-end**.

Le batcher est introduit dans le package `passes` comme un composant
backend-neutral. Il ne cree aucun handle GPU et ne materialise aucune ressource.
Il lit seulement les packets, leurs diagnostics, les faits de target/pipeline,
et la preuve de retention fournie par l'integration execution/resource.

La premiere integration runtime couvre uniquement les familles simples :

- fills solides ;
- gradients simples deja routes comme fullscreen/uniform payload ;
- draws consecutifs sans destination-read, layer, filter, text complex, copy,
  upload ou readback.

Les cas non couverts coupent le batch avec une raison stable et continuent sur
le chemin existant.

## Architecture

### `GPUPassBatcher`

Responsabilites :

- prendre un `GPUDrawPacketStream` ordonne ;
- conserver l'ordre de rendu ;
- identifier les sequences compatibles ;
- couper le batch aux frontieres dangereuses ;
- produire un `GPUPassBatchPlan` dumpable ;
- refuser avant l'encodage groupe quand une ressource materialisee attendue n'a
  pas de retention queue prouvee.

Le batcher ne depend pas de `wgpu4k`, du runtime natif, ni des handles backend.
Il peut dependre des contrats `passes` et de petits faits de retention fournis
par `execution` sous forme de labels dump-safe.

### `GPUPassBatchPlan`

Resultat global du batcher pour un stream :

- stream source ;
- liste des batches acceptes ;
- liste des cuts/refus ;
- compteurs acceptes/coupes ;
- diagnostics aggregates ;
- evidence queue/lifetime associee aux batches acceptes.

Le plan est une preuve. Il ne garantit pas que le backend a soumis du travail.
La soumission reste prouvee par `GPUCommandEncoderPlan`, `GPUCommandSubmission`
et `GPUQueueManager`.

### `GPUPassCommandStream`

`GPUPassCommandStream.fromDrawPacketStream` reste le fallback simple.

La phase ajoute une entree explicite depuis `GPUPassBatchPlan`. Pour un batch
accepte, le command stream emet :

```text
beginRenderPass
  packet 1: set pipeline, set bind group, scissor, draw
  packet 2: set pipeline, set bind group, scissor, draw
  ...
endRenderPass
```

Les optimisations d'elision de state sont hors scope. La premiere version peut
emettre le setup complet par packet pour garder le comportement evident.

## Regles de batching

Un batch peut regrouper des packets consecutifs si tous les faits suivants
sont vrais :

- meme target ;
- meme `targetStateHash` ;
- role `Shading` ;
- famille supportee : solid fill ou gradient simple ;
- render-step, pipeline, binding layout et fixed state compatibles ;
- changement de pipeline autorise dans la meme render pass ;
- aucun token ou diagnostic de destination-read ;
- aucun saveLayer begin/end, layer composite ou layer target prepare ;
- aucun filter/intermediate ;
- aucun text/glyph complex ;
- aucun packet copy/upload/readback/compute ;
- meme device/resource generation attendue ;
- toutes les leases necessaires au batch sont prouvees comme retenues jusqu'a
  submission completion.

Un batch doit etre coupe si l'un des faits suivants apparait :

- target different ;
- target state different ;
- blend/fixed state incompatible ;
- destination-read ;
- saveLayer, layer composite ou target layer ;
- filter/intermediate ;
- text/glyph complex ;
- copy/upload/readback barrier ;
- resource generation stale ;
- ressource materialisee non retenue par la queue ;
- doute sur l'ordre visuel.

La regle de decision est volontairement stricte : si le batcher ne peut pas
prouver que le groupement est neutre, il coupe.

## Gradients simples

Un gradient simple est eligible seulement si :

- il est deja route comme uniform payload ou raw uniform draw ;
- il n'a pas besoin de destination-read ;
- il n'utilise pas un color filter non prouve ;
- il n'utilise pas une local matrix ou un working color space non prouve dans
  cette route ;
- il partage un target et un fixed state compatibles avec le batch courant.

Les gradients radiaux, sweep ou les gradients avec tile/color-space semantics
non prouves restent hors batch pour cette phase, sauf s'ils passent deja par un
packet simple explicitement marque eligible.

## Donnees et dumps

Ajouter des contrats backend-neutral :

- `GPUPassBatchPlan` ;
- `GPUPassBatch` ;
- `GPUPassBatchCut` ;
- `GPUPassBatchQueueGuard`.

Un type interne `GPUPassBatchDecision` peut etre ajoute seulement s'il garde les
chemins accepte/coupe normalises. Les contrats publics attendus pour la phase
restent le plan, le batch, le cut et la queue guard.

Les dumps doivent rester deterministes et ne jamais exposer de handle brut.

Exemples de lignes :

```text
passes.batch-plan stream=packet-stream-main pass=main-pass accepted=1 cuts=0 packets=4 diagnostics=none
passes.batch id=batch-1 target=rgba8-premul-msaa1 packets=packet-1,packet-2 pipelines=render:solid-fill
passes.batch-cut before=packet-2 after=packet-3 code=unsupported.batch.destination_read
passes.batch-queue-guard batch=batch-1 retained=true retainedRefs=lease:uniform-slab:fullscreen:abc,lease:bind-group:def
```

Les reason codes stables incluent :

- `unsupported.batch.destination_read` ;
- `unsupported.batch.save_layer` ;
- `unsupported.batch.filter_intermediate` ;
- `unsupported.batch.text_complex` ;
- `unsupported.batch.copy_or_readback` ;
- `unsupported.batch.upload_barrier` ;
- `unsupported.batch.target_changed` ;
- `unsupported.batch.blend_or_fixed_state_changed` ;
- `unsupported.batch.unretained_materialized_resource` ;
- `invalid.batch.missing_pipeline_key` ;
- `stale.batch.resource_generation`.

## Queue et lifetime

La phase 4 ne doit pas affaiblir la phase 3.

Pour chaque batch accepte, le plan doit pouvoir lier les ressources
materialisees a des labels de retention qui seront passes a
`GPUQueueManager.submit(...)`.

Si une ressource provider a une politique `submission-complete` et que la route
ne prouve pas qu'elle sera retenue, le batcher doit couper ou refuser avant
l'encodage groupe avec :

```text
unsupported.batch.unretained_materialized_resource
```

Les ressources des batches acceptes continuent d'etre collectees dans les
leases de frame, puis retenues par la submission. Les uniform slabs, bind groups
et targets ne sont jamais recycles plus tot parce qu'un batch a ete groupe.

## Integration runtime

La premiere integration concrete se fait sur les chemins simples du runtime
natif :

- fullscreen solid rect draws ;
- fullscreen uniform payload draws utilises par les gradients simples ;
- scenes rectangles qui utilisent deja un target unique et un render pass simple.

Le runtime doit :

- construire ou reutiliser les packets simples existants ;
- appeler `GPUPassBatcher` avant le command stream ;
- enregistrer les dump lines du batch plan dans la telemetry phase 0/runtime ;
- encoder les batches acceptes via `GPUPassCommandStream` ;
- conserver le chemin existant pour les cuts ;
- passer les leases materialisees a `GPUQueueManager.submit(...)`.

La preuve "moins de passes/submissions" doit venir d'une scene rectangles
simple. Si le backend GPU est indisponible dans l'environnement de test, le test
peut etre skipped avec une raison stable, mais les tests unitaires de batcher
doivent rester executables.

## Telemetry

La telemetry runtime ajoute des compteurs simples :

- batch plans ;
- batches acceptes ;
- batch cuts ;
- packets batchables ;
- taille minimale/maximale/moyenne des batches ;
- cuts par reason code.

Ces compteurs completent les compteurs existants de render passes, submissions,
command buffers, resource provider et queue manager. Ils ne remplacent pas les
dumps detailles.

## Error handling

Un batch cut n'est pas une erreur fatale. C'est le comportement normal pour
preserver la semantique visuelle.

Un refus terminal est reserve aux cas ou le command stream groupe serait
dangereux ou incoherent :

- pipeline key manquante pour un packet `Shading` ;
- generation stale ;
- ressource materialisee requise mais non retenue ;
- target ou role impossible a encoder dans une render pass simple.

Les refus doivent rester diagnostiques. Ils ne doivent pas rerouter vers un
rendu CPU cache dans une texture, ni masquer un support manquant.

## Tests

### Tests unitaires

Ajouter `GPUPassBatcherTest` pour couvrir :

- groupe de fills solides consecutifs compatible ;
- groupe de gradients simples compatible ;
- ordre de rendu conserve ;
- cut sur target change ;
- cut sur blend/fixed state change ;
- cut sur destination-read ;
- cut sur saveLayer/layer ;
- cut sur filter/intermediate ;
- cut sur text complex ;
- cut sur copy/upload/readback ;
- refus ou cut sur ressource materialisee non retenue ;
- dump lines deterministes et backend-neutral ;
- packet sans pipeline key refuse avec `invalid.batch.missing_pipeline_key`.

### Tests command stream

Ajouter ou adapter les tests `GPUDrawPacketCommandStreamTest` pour prouver :

- un `GPUPassBatchPlan` accepte produit un `GPUPassCommandStream` avec une seule
  render pass ;
- les packets coupes restent encodes par batches separes ou par le fallback ;
- les diagnostics du batch plan apparaissent dans le command stream ;
- les operand bridges restent coherents avec les packets sources.

### Tests runtime

Ajouter un smoke runtime, skippe si le backend GPU est indisponible, qui :

- rend une scene de rectangles simple ;
- compare les compteurs avant/apres ;
- montre moins de render passes/submissions qu'un chemin non batche ou une
  baseline explicite de la scene ;
- verifie que la queue retient les leases jusqu'a completion ;
- verifie que les pixels restent identiques ou que l'impact GM est explique.

### Dashboard GM

La phase doit laisser le dashboard GM generable. Si des images changent, le
rapport doit expliquer si le changement vient d'une correction attendue, d'une
variation de route ou d'un refus explicite. Aucune promotion ne doit etre faite
sans evidence reference/CPU/GPU/diff/stat.

## Non-objectifs

- Ne pas batcher destination-read.
- Ne pas batcher saveLayer.
- Ne pas batcher filters/intermediates.
- Ne pas batcher text complex.
- Ne pas introduire un scheduler GPU general.
- Ne pas porter Graphite ou Ganesh.
- Ne pas introduire SkSL dynamique.
- Ne pas cacher les fallback dans le runtime.
- Ne pas optimiser l'elision de state avant les preuves de correction.

## Criteres de succes

- Les fills solides compatibles sont batchables.
- Les gradients simples compatibles sont batchables.
- Les cuts target/blend/destination-read/saveLayer/filter/text/copy/readback
  sont testes.
- Les decisions de batch sont dumpables.
- `GPUPassCommandStream` peut etre produit depuis un batch plan.
- Une scene rectangles montre moins de passes/submissions ou fournit un skip
  stable quand le backend GPU est absent.
- Les ressources materialisees d'un batch sont retenues par `GPUQueueManager`.
- Le dashboard GM reste generable.
- Aucun changement visuel non explique.
