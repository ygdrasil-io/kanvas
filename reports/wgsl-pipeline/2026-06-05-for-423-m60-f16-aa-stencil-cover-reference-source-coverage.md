# FOR-423 - M60 F16 source/coverage verifiees contre reference scene

Linear: FOR-423

Brouillon memoire:
`global/kanvas/tickets/drafts/brouillon-ticket-for-423-m60-f16-comparer-source-coverage-verifiees-reference-scene`

Constat source:
`global/kanvas/findings/for-422-source-verifiee-correspond-scratch-et-mutation-finale`

Artefact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-reference-source-coverage-for423/m60-f16-aa-stencil-cover-reference-source-coverage-for423.json`

## Resultat

Classification globale: `verified-coverage-diverges-from-reference`.

FOR-422 avait montre que la source verifiee correspond au scratch color-target et que la mutation finale est reconstruite par `SrcOver`. FOR-423 compare maintenant cette source et son alpha de couverture avec la reference de scene: le masque CPU de couverture attendu et les pixels de reference.

Le stockage diagnostique, l'ecriture scratch et le blend final ne sont plus les suspects principaux. Le residu restant pointe vers l'axe couverture/stencil AA ou vers le choix de source par sous-dessin.

## Compteurs

| Mesure | Valeur |
|---|---:|
| Comparaisons bornees | 48 |
| Comparaisons decisives | 16 |
| Sources verifiees | 16 |
| References de couverture disponibles | 48 |
| 6 divergences de couverture | 6 |
| 10 divergences de source | 10 |
| 32 comparaisons incompletes | 32 |

Les 32 comparaisons incompletes restent dans l'artefact pour garder le perimetre stable, mais elles ne pilotent pas la classification globale car elles n'ont pas de source verifiee.

## Lecture simple

Sur 6 pixels decisifs, l'alpha source envoyee au blend vaut environ `96 / 255`, alors que la couverture CPU attendue vaut `160 / 255`. Cela prouve une divergence de couverture sur le chemin AA stencil-cover.

Sur 10 autres pixels decisifs, la couverture observee vaut bien `255 / 255`, mais la source verifiee ne correspond pas a la couleur de source attendue pour le bandeau de la scene. Cela indique aussi un probleme de routage source/sous-dessin, mais le signal prioritaire reste la divergence de couverture car elle explique directement l'alpha incorrecte des pixels partiels.

## Non-objectifs preserves

- Aucun changement de rendu par defaut.
- Aucune promotion M60 F16.
- Aucun changement de seuil, score, route ou fallback.
- Aucun dump massif WGSL ou framebuffer.
- Aucun changement `wgsl4k`.

## Prochaine etape

Le prochain ticket doit cibler la couverture AA stencil-cover M60 F16: identifier pourquoi l'alpha verifiee envoyee au blend diverge du masque CPU attendu sur les 6 pixels partiels, puis separer ce cas du routage source des 10 pixels opaques.

Validations attendues:

- `rtk python3 scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py`
- `rtk python3 scripts/validate_for422_m60_f16_aa_stencil_cover_verified_source_comparison.py`
- `rtk python3 scripts/validate_for421_m60_f16_aa_stencil_cover_verified_return_path_diagnostic.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for423-pycache python3 -m py_compile scripts/validate_for423_m60_f16_aa_stencil_cover_reference_source_coverage.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
