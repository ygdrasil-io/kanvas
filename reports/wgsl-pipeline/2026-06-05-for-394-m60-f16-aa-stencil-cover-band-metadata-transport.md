# FOR-394 M60 F16 AA stencil-cover band metadata transport

Decision: `M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_RECORDED`

Classification: `diagnostic-transport-added-not-connected`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-band-metadata-transport-for394/m60-f16-aa-stencil-cover-band-metadata-transport-for394.json`

FOR-394 ajoute un transport diagnostique borne de metadata de bande dans le
chemin `StencilCoverAaPolygonDraw` / `buildStencilCoverAaDrawResources` /
`aa_stencil_cover.wgsl`. Le transport reste eteint par defaut et ne modifie pas
la couleur finale.

## Resultat

Statut JSON: `diagnostic-transport-added-not-connected`.

Le chemin AA stencil-cover solid-color expose maintenant deux slots uniformes
diagnostiques a la fin du layout. Le shader peut deriver `bandLocalX` par
`floor(frag.xy.x) - m60F16BandMetadata0.bandXStart` et peut evaluer le helper
`sourceFacingLocalBandLane`, mais `fs_inside` et `fs_outside` ne lisent pas ces
helpers.

## Garde diagnostique

- garde: `kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled` ;
- active par defaut: `false` ;
- ecrit des metadata quand eteint: `false` ;
- limite au M60 F16 reconnu: `true`.

Conditions de transport :

- System property enabled
- RGBA16Float intermediate
- targetColorSpaceBlend active
- experimental stroke cap/join renderer active
- surface size 192x128
- solid SrcOver paint without shader/colorFilter/maskFilter/pathEffect
- strokeWidth == 10
- expected M60 cap/join/source-color tuple

## Layout transporte

- taille avant: `4272` bytes ;
- taille apres: `4304` bytes ;
- compatibilite: append-only trailing uniform extension; default zeros do not feed fs_inside/fs_outside output.

- `m60F16BandMetadata0` offset `4272` : enabled, bandXStart, bandXEnd, strokeBandId
- `m60F16BandMetadata1` offset `4288` : capId, joinId, sourceFacingLeftMaxLocalX, sourceFacingRightMinLocalX

Champs transportes :

- `enabled`
- `bandXStart`
- `bandXEnd`
- `strokeBandId`
- `capId`
- `joinId`
- `sourceFacingLeftMaxLocalX`
- `sourceFacingRightMinLocalX`

## Observabilite shader

- `bandLocalX` derivable cote shader: `True` ;
- `sourceFacingLocalBandLane` observable cote shader: `True` ;
- raccord couleur finale: `False` ;
- raccord couverture: `False` ;
- entry points consommant la metadata: `0`.

## Garde FOR-392 preserve

- garde runtime: `kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled` ;
- correction runtime appliquee: `false` ;
- probe FOR-380 draw-wide bloque: `true` ;
- residuel avec garde demande: `2014` ;
- delta: `0`.

## Compteurs conserves

- pixels selectionnes par le predicate ideal: `8` ;
- ameliores recuperes: `8/8` ;
- regressions incluses: `0/8` ;
- residuel ideal si metadata runtime activee plus tard: `2014 -> 1949`.

## Non-objectifs

Aucune correction M60 F16, activation de predicate runtime, connexion a la
couleur finale, modification de fallback, score, seuil, promotion ou scene non
liee n'est effectuee.

## Risque restant

Transport is observable but not yet proven by a shader readback route; future activation still needs a lane-specific runtime validation.

Prochaine extension minimale: add diagnostic readback or a non-color side-channel proving the shader helper observes the expected lane before any correction hook.
