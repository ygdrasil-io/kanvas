# FOR-393 M60 F16 sourceFacingLocalBandLane shader metadata

Decision: `M60_F16_SOURCE_FACING_LANE_SHADER_METADATA_AUDITED`

Classification: `requires-new-uniforms`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-facing-lane-shader-metadata-for393/m60-f16-source-facing-lane-shader-metadata-for393.json`

FOR-393 inspecte `StencilCoverAaPolygonDraw`, `buildStencilCoverAaDrawResources`
et `aa_stencil_cover.wgsl` pour savoir si la metadata
`sourceFacingLocalBandLane` peut etre derivee cote shader, par fragment, sans
changer le rendu.

## Resultat

Statut JSON: `requires-new-uniforms`.

Le chemin courant ne peut pas exporter la lane avec les uniformes existants.
Il faut ajouter des uniformes ou une table equivalente de metadata de bande
avant de raccorder le predicate au runtime.

Le garde FOR-392 est preserve :

- garde: `kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled` ;
- active par defaut: `false` ;
- hook runtime: `false` ;
- probe FOR-380 draw-wide utilise: `false` ;
- residuel avec garde demande: `2014` ;
- delta avec garde demande: `0`.

## Donnees disponibles avant shader

- solid color premul/unpremul source color
- viewport size
- edgeCount
- fillType
- directed path edge segments as edges[256] vec4f(Ax, Ay, Bx, By)
- cover quad vertices
- stencil winding partition via inside/outside cover pipelines
- scissor rectangle
- clipShapeBounds
- clipShapeRadiiKind
- colorFilter payload
- draw-wide targetColorSpaceBlend flag
- host-side stroke style diagnostics: strokeWidth, cap, join

## Donnees disponibles dans le shader

- fragment position frag.xy
- supersampled_path_cov(frag.xy)
- clip_cov(frag.xy)
- edge list and edgeCount
- fillType
- inside/outside stencil-cover side selected by pipeline
- draw-wide targetColorSpaceBlend flag

## Donnees manquantes

- `strokeBand per fragment or per band`
- `bandLocalX per fragment`
- `band xStart/xEnd bounds`
- `band identity to cap/join mapping`
- `sourceFacingLocalBandLane boolean`

## Pourquoi le raccord runtime reste unsafe

Le predicate FOR-391 demande `strokeBand` et `bandLocalX` :

`(strokeBand == round-round && bandLocalX >= 39) || (strokeBand == butt-bevel && bandLocalX <= 17)`.

Ces champs existent dans les preuves de scene, ou `bandLocalX` vient de
`membership.pixel.x - band.xStart`, mais ils ne sont pas packs dans l'uniforme
AA stencil-cover. Le shader voit `frag.xy`, les segments de bord, la couverture
et le clip ; cela ne suffit pas a identifier de facon stable la bande de stroke
ni son origine locale. Router le garde FOR-392 vers le vieux controle
draw-wide FOR-380 serait donc unsafe et recreerait la regression full-scene
deja prouvee.

## Point d'extension minimal

Owner: `StencilCoverAaPolygonDraw uniform packing`

Packer: `buildStencilCoverAaDrawResources`

Shader: `aa_stencil_cover.wgsl Uniforms`

Champs minimaux :

- diagnostic lane metadata enable flag, disabled by default
- bounded band metadata table: xStart, xEnd, strokeBandId/capJoinId
- or equivalent segment/band metadata sufficient to derive bandLocalX from frag.xy

## Compteurs conserves

- pixels selectionnes par le predicate ideal: `8` ;
- ameliores recuperes: `8/8` ;
- regressions incluses: `0/8` ;
- residuel ideal si metadata runtime disponible: `2014 -> 1949`.

## Non-objectifs

Aucune correction M60 F16, activation de predicate, modification WGSL runtime,
fallback general, scoring, seuil, promotion ou scene non liee n'est effectuee.
