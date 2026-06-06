# FOR-459 - M60 F16 production cover color attachment acceptance

## Resultat

Classification : `production-cover-color-attachment-rejects-zero-stencil-targets`.

La sonde opt-in `kanvas.webgpu.m60F16ProductionCoverColorAttachmentAcceptanceFor459.enabled`
montre que le cover `inside` de production ne modifie pas une color attachment
(cible couleur) de diagnostic sur les six pixels M60 F16 ou les preuves
FOR-453/FOR-457 indiquent `stencilValue=0` et ou FOR-458 indique un rejet
fixed-function attendu (`GPUCompareFunction.NotEqual`, reference `0`, read mask
`255`).

## Preuves

Artefact :
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-production-cover-color-attachment-acceptance-for459/m60-f16-production-cover-color-attachment-acceptance-for459.json`

Compteurs principaux :

- `for453DiagnosticStencilZeroTargetCount=6`
- `productionBoundFor457MatchingZeroTargetCount=6`
- `productionCoverInsideExpectedRejectStateCount=6`
- `insideShaderEmissionOnDiagnosticZeroStencilCount=6`
- `colorAttachmentBeforeAfterAvailableTargetCount=6`
- `colorAttachmentChangedTargetCount=0`
- `colorAttachmentUnchangedTargetCount=6`
- `for442DecisionSourceUsedCount=0`

Pour le premier pixel cible `(92, 75)`, la cible couleur de diagnostic reste a
`[0.0, 0.0, 0.0, 0.0]` avant et apres le cover `inside` de production.

## Interpretation

FOR-459 ne corrige pas M60 F16 et ne modifie pas le rendu par defaut. La sonde
rejoue un chemin diagnostic separe pour isoler la color attachment observable.

Le resultat confirme l'hypothese la plus prudente apres FOR-458 : l'emission
shader `inside` est une observation avant rejet fixed-function, pas une preuve
d'ecriture couleur effective par le cover `inside`.

## Contraintes preservees

- Pas de changement de seuil ou de score.
- Pas de changement de politique de fallback.
- Pas de modification de `PipelineKey`.
- Pas de modification du WGSL de production.
- Pas de modification de `wgsl4k`.
- Pas de promotion FOR-447.
- FOR-442 reste exclu comme source de decision.
- Pas de revendication de support complet pour stroke cap/join.

## Suite unique

Ouvrir un ticket cible pour simplifier la chaine d'evidence autour de
`shader-capture-before-reject` et eviter de traiter l'emission shader seule
comme une acceptation du cover.
