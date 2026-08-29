# W102 — stroke diagonal sous clip path Winding natif

## Objectif

Prouver qu’un `DrawPath` stroke butt/miter diagonal peut être rendu par la
route native lorsqu’il est composé avec un hard path clip `Winding` en
`ClipOp.INTERSECT`.

## Diagnostic initial

Le scénario était correctement identifié par l’analyse comme
`native.path_stroke.stencil_cover`, mais la préparation refusait la
composition avec :

```text
unsupported.recording.core_primitive_path_stencil_clip
```

Après l’autorisation du consumer exact, le second refus a révélé que la
géométrie restait classée `StencilCoverage1x`, puis le validateur de scope
refusait le mélange de géométrie. Le stroke concerné est pourtant déjà
abaissé en un contour fermé de huit sommets : il peut être consommé comme
des triangles directs sous le stencil du clip, sans second attachment stencil.

## Correction

La correction est bornée au cas authentifié suivant : un seul segment,
butt/miter, sans dash, `Winding`, non inverse, huit sommets et indices
`[0,1,2,0,2,3]`, sous un `StencilCoverage` single-sample. Ce cas est alors
classé `FullOrScissor`, admis par le consumer clip-stencil direct et validé
par le native direct route. Les autres strokes conservent leurs refus
existants ; aucun fallback silencieux ni portage d’architecture n’est ajouté.

## Preuve

Le test utilise une cible offscreen 32×32, un clip triangle device-space
`(7.25,6.25)-(30.25,6.25)-(7.25,29.25)` et un segment diagonal
`(5.25,8.25)->(21.25,20.25)`, largeur `4`, anti-aliasing désactivé.
Il vérifie le clip `StencilCoverage`, les opérations Winding
`IncrementWrap`/`DecrementWrap`, la comparaison consumer `NotEqual`, puis une
préparation native, un submit et un readback. Le résultat RGBA complet est
comparé à un oracle CPU indépendant fondé sur les centres de pixels,
l’appartenance barycentrique au triangle et la distance au segment.

Le cas `SQUARE` sous le même clip est également testé : il reste refusé avec
le diagnostic stable `unsupported.recording.core_primitive_path_stencil_clip`,
ce qui verrouille la limite de la promotion au lieu de la masquer par un
fallback.

Commande validée :

```text
rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.diagonal butt miter stroke under winding path clip renders natively'
```

Résultat : `PASSED`, `BUILD SUCCESSFUL`, un submit natif et un readback.

La suite ciblée (positif W102, refus SQUARE et régression scissor) passe aussi
avec `BUILD SUCCESSFUL`.

## Limites

Cette correction ne généralise pas les caps round/square, les joins bevel,
les dash, les strokes multi-contours, l’anti-aliasing, les clips imbriqués ni
les transformations non couvertes. Aucun seuil, PNG ou artefact de
`gpu-renderer-scenes` n’a été modifié.
