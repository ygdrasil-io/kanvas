# W110 — cap round sous clip path Winding

## Résultat

Le cas reste explicitement refusé. Le segment horizontal `(6,16) → (26,16)`
de largeur 4, cap `round`, sur grille intégrale et sans anti-aliasing, est bien
admissible dans la preuve native sans clip. Sous `ClipOp.INTERSECT` Winding, la
préparation refuse avec :

`unsupported.recording.core_primitive_path_stencil_clip`

## Cause

Le round pixel-exact repose sur l’outline polygonal existant : un corps et deux
fans de cap avec la tessellation fixe `ROUND_CAP_TESSELLATION_SEGMENTS`. Cette
géométrie arrive comme `StrokeStencilEdgeFan`. La composition actuelle d’un
stroke avec un clip `StencilCoverage` n’autorise que le consumer
`DirectTriangles` à couverture `FullOrScissor`, avec une topologie et un proof
fermés. Elle n’accepte pas un edge fan de cap rond dans la même attachement
stencil.

Étendre cette route demanderait un nouveau contrat de topologie multi-triangles
et une validation pixel-exacte spécifique à l’intersection du polygone avec le
clip. Ce n’est pas une correction locale sûre ; aucun workaround ou fallback
silencieux n’a été ajouté.

## Preuve de refus

Le test `round cap stroke under winding path clip remains explicitly refused`
vérifie que l’analyse conserve la route candidate
`native.path_stroke.stencil_cover`, puis que la préparation retourne exactement
le refus stable ci-dessus. Le test hors clip conserve par ailleurs l’oracle CPU
indépendant disque/polygone pour la variante round déjà supportée.

Commande ciblée :

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryNativeSmokeTest.round cap stroke under winding path clip remains explicitly refused'
```

Résultat : `BUILD SUCCESSFUL`, test PASS.

La classe complète `GPUFramePathApiInventoryNativeSmokeTest` a également passé
avec succès, avec tous les tests PASS. Aucun changement de production, seuil,
PNG ou `gpu-renderer-scenes` n’a été effectué ; le rapport W109 reste inchangé.
