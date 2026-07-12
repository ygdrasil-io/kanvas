# Task 7 — Routes GPU restantes vers le moteur de clip

## Livraison

- Chaque `DrawText` pris en charge (atlas A8, outline shader/stroke et couches COLR) encode désormais sa source dans la cible transparente avec `SrcOver`, puis laisse `GPUClipExecution.renderWithClip` réaliser l’unique composite vers la scène. Les glyphes bitmap COLR non encodables sont dégradés explicitement, sans rectangle de bounds synthétique.
- `DrawVertices` et `DrawMesh` partagent un encodeur de source : les chemins repassent par la route path existante, les vertices texturés appliquent la CTM puis la conversion canvas→NDC, normalisent BGRA→RGBA et forcent `GPUBlendMode.SRC_OVER`. Un `Mesh.program`, une CTM projective, les couleurs/indices non texturés, les index texturés invalides et les modes texturés strip/fan restent explicitement refusés.
- `DrawPicture` développe ses enfants puis ré-entre le routeur de haut niveau sans déclencher de dispatch direct ni compter de lease supplémentaire. La route supportée est volontairement bornée aux enfants sans clip capturé et sans paint de picture : les paint/clip internes sont refusés explicitement plutôt que rendus avec une sémantique incorrecte.
- `DrawPoints`, `DrawDRRect`, `DrawImageNine`, `DrawImageLattice` et `DrawAtlas` restent groupés dans une seule source par draw logique.
- Le routeur est exhaustif pour les `DisplayOp` actuelles : toute nouvelle branche doit recevoir une route explicite avant de pouvoir atteindre un dispatch historique direct.

## Refus et leases

- Aucun fallback bounds-only ou CPU n’a été ajouté.
- Les dégradations existantes pour les vertices sans image/pixels, les glyphes bitmap COLR et le texte sans Typeface/scaler restent sans dispatch/composite final.
- `GPUClipUsePrepass` compte déjà un masque par `DisplayOp` logique ; les tests couvrent les sous-parties (points/cells/sprites/glyphes) sans lease par élément.
- Les blends non sûrs et destination-read restent sous la politique Task 8 : refus avant source/composite.

## Preuves

- RED initial : le test de vertices source échouait car `dispatchTexturedVertices` ne supportait ni override `SrcOver` ni résultat booléen.
- Correction vérifiée : `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageDispatchTest --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest`, y compris le refus préalable des index texturés invalides.
- Suite complète vérifiée sur la livraison initiale : `rtk ./gradlew :kanvas:test` (succès, 2026-07-12) ; les corrections de revue ont leur vérification fraîche ciblée ci-dessous.
- Nouveaux scénarios GPU réels : texte atlas + vertices texturés BGRA avec assertion de pixel transformé ; refus Mesh-program/Picture paint/clip/text outline non encodable/variants vertices non encodés ; picture borné, mesh sans programme et toutes les routes multi-part sous clip complexe, avec un composite source par draw logique et zéro bypass direct complexe.

## Suivi de revue indépendante

- Le premier sous-dispatch d’une source `Mask` est maintenant le seul à recevoir un clear transparent : `sourceHasContent` devient vrai après chaque encodeur source réussi. Les pixels confirment que les glyphes outline, points, cells `ImageNine` et sprites `Atlas` restent tous visibles.
- Le préflight récursif de `DrawPicture` refuse avant `clip_mask_acquire` tout `paint` ou clip capturé porté par un `DrawPicture` imbriqué. Les refus de `Mesh.program` passent également avant toute acquisition de lease/source.
- Les diagnostics de vertices texturés utilisent désormais l’opération logique stable (`drawVertices` ou `drawMesh`) au lieu d’un `Paint.toString()` ou d’une famille héritée.
- RED/GREEN : le test de points multi-parties échouait avec une première sous-passe transparente ; les préflights Picture et le nom `drawMesh` échouaient aussi avant correction. Vérification fraîche : `rtk ./gradlew :kanvas:test --rerun-tasks --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest` (22 tests, succès).
