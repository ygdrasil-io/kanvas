# GPU phases 0-2 et echafaudage queue

## Objectif

Cette session de coding doit terminer les phases 0 a 2 du rapport `refactor/`
et poser l'echafaudage de la phase 3 dans une seule PR. Le travail reste
agressif, mais il doit etre decoupe en gates internes pour garder une PR
lisible, testable et reversible par commit.

Le but n'est pas de commencer le batching generalise. La session doit d'abord
stabiliser les fondations qui rendent ce batching possible :

- baseline et instrumentation exploitable ;
- capabilities GPU centralisees ;
- provider de ressources GPU minimal mais concret ;
- queue manager minimal avec retention de ressources testee.

## Contraintes fortes

- Une seule PR pour toute la session.
- Plusieurs commits dans cette PR, un par gate logique.
- Aucun port Ganesh ou Graphite.
- Aucune abstraction multi-backend generique ajoutee.
- Aucun compilateur dynamique SkSL.
- Le wording public est strictement `GPU`.
- Ne pas ajouter les termes d'implementation concrete dans les docs, messages
  de tests, diagnostics, dumps, nouveaux noms publics ou nouveaux types.
- Si un fichier touche contient deja un message public qui fuit le nom de
  l'implementation concrete, le remplacer par `GPU`.
- Les noms existants de fichiers/classes qui fuitent deja l'implementation
  concrete ne sont pas propages. Leur renommage complet est une dette separee
  sauf si un gate doit les toucher pour faire passer les tests.
- Aucun PNG regenere ne doit etre commit sans justification visuelle explicite.

## Etat de depart

Le rapport `refactor/` decrit six phases. Le code actuel a deja avance sur
plusieurs points :

- `GPUBackendRuntimeTelemetry` compte deja render passes, submissions, command
  buffers, creations de buffers/textures/bind groups/samplers, queue writes et
  uniform slab events.
- `GPUCapabilities` et `GPULimits` existent deja, avec
  `minUniformBufferOffsetAlignment`, mais restent encore trop conservateurs et
  trop proches des constantes runtime.
- Les contrats de `GPUUniformSlab` et `GPUPayloadSlab` existent avec plans,
  diagnostics, dump lines et tests.
- Le test complet `:gpu-renderer:test` a un echec structurel restant :
  `GPURendererPackageBoundaryTest > gpu renderer production source satisfies
  package boundary rules`.

Violations package-boundary observees :

- import semantique depuis `execution` vers un helper text ;
- import semantique depuis `wgsl` vers `commands.GPUMaterialDescriptor` ;
- package root invalide dans `WgslReflection.kt` ;
- cycle `commands -> filters -> commands`.

Ces violations doivent etre fermees avant les gates runtime pour eviter de
construire sur des frontieres deja cassees.

## Approche retenue

Approche agressive cadree :

1. fermer la gate package-boundary ;
2. terminer la baseline phase 0 ;
3. completer les capabilities phase 1 ;
4. rendre le provider phase 2 minimal concret ;
5. ajouter l'echafaudage de queue phase 3 et brancher un chemin limite.

Chaque gate doit produire son propre commit et ses propres tests. Si une gate
devient instable, elle doit pouvoir etre revertee sans perdre les gates
precedentes.

## Gate 0 : package-boundary

### But

Remettre la suite `gpu-renderer` sur des frontieres propres avant d'ajouter de
nouveaux composants.

### Travail

- Supprimer les imports semantiques interdits entre `execution`, `wgsl`,
  `commands`, `filters` et `text`.
- Casser le cycle `commands <-> filters` par extraction d'un contrat neutre ou
  inversion de dependance minimale.
- Remettre `WgslReflection.kt` sous le package canonique du renderer ou isoler
  l'adaptation externe derriere un type renderer-owned.
- Ne pas changer le comportement de rendu pendant cette gate.

### Validation

- `rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest`
- Pas de nouveau wording public qui revele l'implementation concrete.

## Gate 1 : phase 0 baseline et instrumentation

### But

Transformer les compteurs deja ajoutes en baseline exploitable. La phase 0 est
terminee quand on peut lire clairement ce qu'une scene ou un GM consomme en
passes, submissions, command buffers et ressources.

### Travail

- Garder les compteurs runtime existants.
- Ajouter un rapport/dump de baseline stable et court.
- Relier les compteurs a une scene simple ou a une execution GM quand le
  chemin le permet.
- Ajouter les cache hit/miss deja disponibles dans la meme evidence ou dans une
  section voisine.
- Eviter les logs par draw en mode normal.

### Validation

- Test unitaire du dump baseline.
- Smoke GPU offscreen montrant des deltas non nuls de passes/submissions.
- Dashboard GM toujours generable.
- Aucune semantique de rendu modifiee.

## Gate 2 : phase 1 capabilities GPU

### But

Faire de `GPUCapabilities` la source des faits de capacite et d'alignement
utilises par les plans et diagnostics.

### Travail

- Enrichir `GPUCapabilities` et `GPULimits` sans exposer l'implementation
  concrete.
- Centraliser les constantes runtime encore dispersees quand elles affectent
  validity, planning ou diagnostics.
- Faire passer l'alignement des uniform slabs par
  `capabilities.limits.minUniformBufferOffsetAlignment`.
- Ajouter des diagnostics stables pour refus de format, usage, taille et
  alignment.
- Exposer les facts utiles aux pipeline/resource keys sans y mettre de valeurs
  uniforms.

### Validation

- Tests capabilities avec alignement 256 octets.
- Tests capabilities avec alignement plus fin.
- Tests diagnostics format/usage/size/alignment.
- Smoke GPU offscreen vert.
- Les dumps restent backend-neutral et sans handle brut.

## Gate 3 : phase 2 provider GPU minimal concret

### But

Faire passer les ressources frequentes par un provider concret, en commencant
par uniforms, null buffer, bind group uniform et texture/sampler. Cette gate
doit reduire la creation locale dans le runtime pour les chemins cibles, sans
reecrire tout `GPURenderer`.

### Travail

- Ajouter un provider concret renderer-owned.
- Brancher uniform slab/ring sur les plans existants.
- Ajouter null buffer.
- Ajouter cache bind group single-uniform.
- Ajouter cache texture+sampler simple.
- Inclure device generation dans les cles.
- Ajouter telemetry hit/miss/create/refuse.
- Brancher le provider sur un ou plusieurs chemins runtime limites deja
  couverts par smoke tests.

### Validation

- Tests alignement uniform.
- Tests cache hit/miss.
- Tests stale generation.
- Tests refus budget/usage/alignment.
- Scene simple : creations de buffers/bind groups baissees ou expliquees par
  evidence.
- Aucun changement visuel attendu.

## Gate 4 : phase 3 queue manager scaffold

### But

Poser le modele de submission et retention avant tout batching generalise. La
queue ne doit pas devenir un scheduler complexe dans cette session.

### Travail

- Ajouter `GPUQueueManager` minimal ou equivalent renderer-owned.
- Attribuer un `submissionId` stable.
- Retenir les ressources associees a une submission.
- Liberer/recycler seulement apres completion simulee ou observee.
- Retenir readback/staging buffers sur le chemin cible.
- Ajouter telemetry submit/wait/release.
- Brancher prudemment un chemin offscreen/readback ou submission telemetry.

### Validation

- Tests de retention : une ressource n'est pas release avant completion.
- Tests de release apres completion.
- Smoke readback.
- Le chemin offscreen reste fonctionnel.
- Le dashboard GM reste generable.

## Non-objectifs

- Pas de pass batching generalise dans cette session.
- Pas de migration complete de `GPURenderer`.
- Pas de refactor large des familles GM.
- Pas de correction algorithmique blur/text/path sauf si un test de gate le
  demande directement.
- Pas de renommage massif de tous les symboles historiques qui fuitent
  l'implementation concrete, sauf si necessaire pour ne pas ajouter de nouveau
  wording ou pour faire passer les tests touches.

## Strategie de tests finale

La PR doit viser :

- `rtk ./gradlew :gpu-renderer:test`
- `rtk ./gradlew :integration-tests:skia:generateSkiaScan --args='--from 0 --to 8 --timeout 20'`
- `rtk ./gradlew :integration-tests:skia:generateSkiaDashboard`

Si un test reste skippe pour raison d'environnement, la raison doit etre
explicite. Si un test echoue, l'echec doit etre documente dans la PR avec une
decision claire : corriger, sortir du scope, ou bloquer la merge.

## Criteres de succes

- `GPURendererPackageBoundaryTest` est vert.
- La phase 0 a une baseline exploitable.
- Les capabilities GPU pilotent au moins les alignements et diagnostics cibles.
- Le provider concret gere uniforms, null buffer, bind group uniform et
  texture/sampler sur un scope limite.
- La queue a un modele de retention teste.
- Les dumps et diagnostics restent backend-neutral.
- La PR unique reste decoupee en commits de gates.
