# W75 — correction du bind group du path dst-read

## Résultat

Le finding W74 était un bug réel de matérialisation native, et non un refus
prévu de la route de clipping. Le cas `GPUPathClipRegressionTest` qui combine
un fond blanc et un path `DIFFERENCE` lit correctement la destination après la
correction.

La cause était un désaccord entre deux autorités d'identité :

- `mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity` associait bien le
  programme `PathStencilCoverDstRead` au composant bind-group
  `core-primitive-dst-read...:difference` ;
- `corePrimitiveNativeComponentIdentityOrNull` ne traitait les blends
  destination-read que pour `Role.Shading`. Un `Role.PathStencilCover` retombait
  donc à tort sur le composant standard `dynamic-uniform32-v2` ;
- le frame pool ne possédait que le bind group destination-read, ce qui faisait
  échouer la recherche exacte avec
  `An indexed CorePrimitive run requires an exact bind group per component identity`.

La correction minimale utilise l'identité déjà produite par le mapping lorsque
le programme est `PathStencilCoverDstRead`. Aucun fallback implicite, désactivation
du preflight ou changement de contrat de ressources n'a été ajouté.

## Preuve TDD et vérification

Le test de régression descriptor a d'abord échoué avant la correction :
`GPUWgpu4kCorePrimitivePipelineDescriptorTest.path stencil dst read cover uses
the exact dst read bind group component`.

Après correction :

```text
./gradlew --offline --no-daemon :gpu-renderer:test \
  --tests '*GPUWgpu4kCorePrimitivePipelineDescriptorTest.path stencil dst read cover uses the exact dst read bind group component'
BUILD SUCCESSFUL — 1 test passed

./gradlew --offline --no-daemon :kanvas:test --rerun-tasks \
  --tests '*GPUPathClipRegressionTest'
BUILD SUCCESSFUL — 4 tests passed

./gradlew --offline --no-daemon --max-workers=1 :gpu-renderer:test \
  --tests '*GPUWgpu4kCorePrimitivePipelineDescriptorTest'
BUILD SUCCESSFUL — 32 tests passed
```

Le cas rendu vérifie un oracle CPU indépendant :
`DIFFERENCE(rouge, blanc) = cyan` dans le triangle, et le blanc est conservé
hors du path. Il vérifie aussi le diagnostic de route
`route:destination-read:DrawPath:*` avec la raison `gpu-copy-then-formula`.

Les trois autres cas du test restent inchangés : deux refus de path-stencil
explicites et un rendu `DARKEN` par la route de copie destination.

## Portée de la preuve

La preuve GPU est maintenant positive pour ce cas précis : le path cover
destination-read est préparé et exécuté avec le bind group exact, avec le
rendu pixel vérifié et le diagnostic de route attendu. Les autres formes de
clipping, la couverture AA scalaire, les chemins hors budget et les modes sans
programme de formule restent soumis à leurs refus existants.

Aucun seuil de similarité, PNG généré ou fichier de `gpu-renderer-scenes` n'a
été modifié.
