# Task 1 — Wave 8 : hard `clipPath` + analytic RRect

## Résultat

La voie native WebGPU accepte désormais un unique consommateur `RRect` analytique,
opaque, non-AA et sans transformation dans la portée d'un unique `clipPath` dur
(`Winding`, stencil, 1×). Le `RRect` reste une géométrie analytique : il n'est pas
abaissé en `DirectTriangles`. `DRRect` n'est pas admis par cette extension.

Les trois seuls nouveaux cas publics sont :

- `clip-path-solid-rrect`
- `clip-path-asymmetric-solid-rrect`
- `clip-path-ellipse-solid-rrect`

Le catalogue contient maintenant exactement 51 cas : 49 rendus et 2 refus.

## Chaîne implémentée

1. Le mapper/semantic analysis autorise seulement la combinaison strictement
   bornée : `FillRRect`, `SRC_OVER`, `SolidColor` alpha 1, identité, 1×, sans AA,
   sans mask filter et avec un `StencilCoverage` hard path identité.
2. La prepared task conserve la topologie `AnalyticRRect`; elle prépare le quad de
   bounds et le payload analytique `Uniform80` sans passer par les triangles directs.
3. Le snapshot immuable de stencil accepte soit le slab historique `Uniform32/592`,
   soit le nouveau seal analytique `Uniform80`. Celui-ci est limité à un seul
   consommateur, sans prefix draw : aucun mélange implicite des ABI n'est permis.
4. Le materializer envoie ce plan/slab exact; le descriptor sélectionne les nouveaux
   programmes stencil-read `ClipStencilConsumerAnalyticRRectRegular` ou `Inverse`.
   Ils réemploient le shader analytique RRect et l'état stencil du consommateur.
5. L'authority/native route vérifie à nouveau type `RRect`, `SolidColor`, 1× et les
   règles de clip/stencil avant tout bind ou draw.

## Tests et TDD

Les tests RED ont été observés avant les hand-offs de production :

- le catalogue a d'abord échoué sur 48/46/2 au lieu de 51/49/2;
- la route native refusait le consommateur `RRect`;
- le rendu Surface public refusait d'abord le complex stack, puis révélait
  successivement la frontière mapper, l'ABI/shader `Uniform80` et la preflight
  prepared-route;
- la suite complète a ensuite révélé un invariant de catalogue restant à 48/46,
  corrigé sans étendre le scope.

La première sortie Surface observable est devenue verte après classification de ces
frontières : le problème final était la preflight prepared-route qui ne reconnaissait
que les slabs `Uniform32/592`, donc classé `snapshot/ABI`, et non un défaut de shader
ou de rasterisation. Le test Surface confirme 0 diagnostic, orange à l'intérieur de
l'intersection et transparent à l'extérieur.

L'oracle `SurfaceSrgbClipPathRRectCpuOracle` est indépendant : il effectue la
membership triangle winding et RRect aux centres de pixels, sans normalizer, ABI,
WGSL ni résultat GPU. Le test asymétrique vérifie pixels intérieur/extérieur et
exactement 1075 pixels orange. Les trois cas catalogue portent `delta = 0`.

Refus unitaires conservés : AA, MSAA, matériau gradient, géométrie non admise
(donc `DRRect`) et pile de clips multiple. Les chemins de triangles directs gardent
leur pipeline distinct.

## Vérification exécutée

- `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --console=plain`
  — `BUILD SUCCESSFUL`, 250 tests terminés, 1 skip existant.
- `rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GPUCorePrimitiveClipStencilNativeRouteTest*' --rerun-tasks --console=plain`
  — `BUILD SUCCESSFUL`, 13 tests de route native, y compris acceptation analytique
  RRect et refus bornés.
- `rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GPUCorePrimitiveClipStencilNativeRouteTest*' :kanvas:test --tests '*opaque identity rrect renders inside one hard path clip stencil scope*' --console=plain`
  — `BUILD SUCCESSFUL`; le test Surface public est vert.
- `rtk git diff --check` — aucune erreur d'espaces.

## Non-actions et limites explicites

- Aucune evidence n'a été générée, promue ou poussée; `gpu-renderer-scenes` n'a pas
  été modifié.
- Pas de transformation, AA, MSAA, gradient, `DRRect`, ou clips multiples dans ce
  scope.
- Le nouveau slab `Uniform80` est volontairement sans prefix/background draw dans la
  même portée. Cette limite maintient l'immuabilité et évite de mélanger les ABI;
  une composition multi-consommateurs nécessiterait une décision de design séparée.
