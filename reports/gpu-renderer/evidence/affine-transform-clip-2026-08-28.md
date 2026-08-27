# Affine transform clip — 2026-08-28

## Résultat

La route WebGPU de production accepte désormais un clip `Path` hard borné
capturé sous une translation, un scale uniforme positif ou un scale non
uniforme fini et non singulier. La géométrie du clip est figée en coordonnées
device au moment de la capture; le CTM peut donc être remis à l'identité avant
le consumer.

La preuve `affine-path-clip-color` exerce ce dernier cas par l'API publique
`Surface`/`Canvas`: clip rectangulaire sous `setMatrix(sx=.75, sy=.5)`,
`resetMatrix`, puis `drawColor`. Son oracle CPU indépendant compare le buffer
RGBA8 exact et les artefacts générés incluent capture, diff, stats et route.

## Limites et refus stables

Les matrices non finies, singulières et avec perspective refusent avant toute
soumission avec respectivement `unsupported.transform.non_finite`,
`unsupported.transform.affine_singular` et `unsupported.transform.perspective`.
Les tests publics de clip couvrent `scale(0f, 1f)`, `scale(NaN, 1f)` et
`scale(+Inf, 1f)` suivis de `clipPath(...)`, `resetMatrix()` et `drawColor`.

Le clip hard à skew, rotation ou concat générale reste explicitement hors de
cette promotion: `unsupported.clip.path_transform`, avant toute soumission.
Les tests publics `skew(...)` et `rotate(...)` capturent ce refus après
`clipPath(...)` et `resetMatrix()`.
Un essai triangulaire skewé a produit 12 pixels de bord différents de l'oracle
CPU (similarité 99.70703125 %); il n'est ni publié ni promu. Ceci évite de
confondre une route native active avec une compatibilité de convention de
rastérisation non prouvée.

## Vérifications

- `:kanvas:test` (tests `GPUClipCoverageSurfaceTest`, `GPUPreparedSurfaceFrameBuilderTest`, `CanvasTest`)
- `:gpu-renderer:test` (test builder CorePrimitive)
- `:integration-tests:gpu-evidence:test` (catalogue)
- génération ciblée des preuves `affine-path-clip-color` et `perspective-transform-refusal`

Les preuves ne concernent ni `gpu-renderer-scenes`, ni Ganesh/Graphite, ni
SkSL dynamique, ni native windowing.
