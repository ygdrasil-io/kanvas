# W24 — composition de clips bornée

## Route attestée

La route publique `Kanvas Surface` compose déjà les clips complexes dans
l'ordre d'enregistrement : les producteurs du `CoverageMask` appliquent
`Intersect` ou `Difference`, et les géométries de path conservent leur
`inverseFill` et leur règle de remplissage. Les preuves promues des routes
élémentaires qui composent ce chemin restent :

- `correctness/promoted/clip-path-triangle-difference-solid/` : différence
  hard-path, oracle CPU, GPU, diff, statistiques et diagnostics ;
- `correctness/promoted/clip-path-inverse-axis-y-translated-asymmetric-solid-rrect/` :
  inverse path consommé par un primitive public ;
- `correctness/promoted/clip-rrect-two-bands/` : réutilisation ordonnée d'un
  clip par plusieurs draws.

Ces bundles sont des preuves natives WebGPU, avec oracle CPU indépendant,
captures CPU/GPU, diff et télémétrie de la route `kanvas.surface.render`.
W24 ne crée pas une seconde route de rendu : il borne la composition existante.

## Budget et refus avant draw

`RenderConfig.maxClipStackDepth` limite désormais la profondeur de la pile à
huit éléments par défaut. `GPUClipCoveragePlanner` compare cette profondeur
avant de calculer ou matérialiser l'intermédiaire de masque et renvoie
`unsupported.clip.depth_budget` quand elle est dépassée. Les deux autres
bornes restent appliquées dans le même préflight :

- `maxPathVertices` contrôle le total d'arêtes/vertices des paths
  (`unsupported.clip.vertex_budget`) ;
- `maxClipIntermediateBytes` contrôle l'allocation du masque et de son
  depth/stencil (`unsupported.clip.intermediate_budget`).

Le test de contrat W24 utilise volontairement un budget intermédiaire de
1 octet avec une pile de profondeur 3 et une limite de 2 : il obtient le refus
de profondeur. Cela atteste l'ordre du préflight et l'absence d'allocation ou
de draw avant le refus.

## Reproduction

```text
./gradlew --no-daemon :kanvas:test \
  --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageContractsTest
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```

## Non-claims

Cette vague ne généralise pas les clips à profondeur arbitraire, ne crée pas
de fallback silencieux et ne modifie pas les routes image/text déjà refusées
pour un clip complexe. Elle conserve le runtime headless/offscreen WebGPU.

La matérialisation native d'un `CoverageMask` à plusieurs producteurs reste
explicitement refusée. Une pile publique rect-only imbriquée
`Intersect + Intersect + Difference` atteint le preflight et reçoit
`invalid.preflight.core_primitive_direct_geometry_resources`. Lorsqu'un
producteur inverse path exige depth/stencil, le refus est
`unsupported.recording.core_primitive_clip_mask_depth_stencil_topology_unavailable`
: il requiert la topologie full-target depth/stencil B3.4, absente du runtime
actuel. Ce n'est donc pas promu comme support rendu dans cette vague.

## Investigation B3.4 — blocage natif explicite

L'enquête confirme que `wgpu4k` possède les primitives WebGPU nécessaires :
le runtime bas niveau peut créer `Depth24PlusStencil8`, l'attacher à un
`RenderPassDepthStencilAttachment` et exécuter des passes stencil. Le blocage
ne relève donc pas de cette dépendance, mais de la route publique
`gpu-renderer` :

- le builder refuse encore `depthStencilRequired` pour un `CoverageMask` ;
- le prepared route ferme ce profil couleur-only et le frame pool interdit
  l'emprunt simultané du masque et de `ClipDepthStencil` ;
- le materializer sélectionne aujourd'hui soit la voie masque, soit la voie
  clip-stencil, jamais une seule pass avec les deux ressources et leurs
  durées de vie scellées.

Une alternative plus petite a aussi été évaluée : abaisser uniquement un
triangle convexe hard/Winding/non-inverse, placé après un `Rect Intersect`,
vers un producteur couleur `DstOut`. Son résultat serait exact pour ce seul
cas (`rect ∩ non-triangle`). Elle ne peut toutefois pas être ajoutée comme un
petit flag : elle requiert un shader à attribut vertex, une autorité de
géométrie scellée, des uploads V/I, `DrawIndexed`, des offsets de buffers
pour les consommateurs existants et une adaptation du pool/préflight. C'est
une nouvelle voie de matérialisation, sans preuve Surface à ce stade.

Décision de la vague : aucun support path+CoverageMask n'est annoncé ni
promu. Les variantes path, inverse, AA, plusieurs contours, MSAA, trois
producteurs ou plus, et tout stack demandant `ClipDepthStencil` restent
explicitement refusés. Une vague ultérieure pourra choisir soit une
matérialisation B3.4 complète avec preuve Surface CPU/GPU/diff/statistiques,
soit une mini-route triangle accompagnée de la même preuve, mais pas une
acceptation déclarative intermédiaire.
