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

## Fix round 1

### Frontières corrigées

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
  borne l'admission mapper de `FillRRect` hard `clipPath` à `Winding` sur la
  géométrie path et son producer. `EvenOdd` reste donc sur le refus
  `unsupported.clip.complex_stack`.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitiveClipStencilNativeRoute.kt`
  répète la frontière d'authority : `RRect` analytique accepte seulement le
  producer `Winding` et un `SolidColor` dont l'alpha prémultiplié est exactement
  `1f`. Ceci évite qu'un appel direct de la route contourne l'analyse.
- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
  authentifie le `Uniform80` par le semantic seal, par les 80 octets reconstruits
  depuis ce semantic exact, et par le plan de slab exact. Il ne compare plus le
  bloc paint historique de 32 octets au payload analytique.

### Diagnostic systématique Uniform80

Reproduction du premier échec Surface après l'authentification initiale :

```text
GPUPreparedSurfaceTerminalException:
invalid.preflight.core_primitive_clip_stencil_prepared_route:
Prepared clip-stencil uniform slab authority was substituted.
```

Commande :

```text
rtk ./gradlew --no-daemon :kanvas:test --tests '*opaque identity rrect renders inside one hard path clip stencil scope*' --console=plain
```

Evidence par frontière, de `semantic` vers le GPU :

1. L'analyse garde le `FillRRect` opaque/identité/1× sous path `Winding` et le
   builder construit le semantic prepared exact.
2. `GPUCorePrimitivePreparedFrameTaskListBuilder` appelle
   `buildCorePrimitiveAnalyticShapeUniform` et place ses **80 octets** dans
   `GPUCorePrimitiveAnalyticShapeUniformSeal`; le `payloadRef.uniformBlock` du
   semantic demeure le bloc paint normal de **32 octets**.
3. Le snapshot conserve l'identité du semantic et la copie immuable des 80 octets.
   Le consumer historique analytic hors clip reconstruit déjà ces octets depuis le
   semantic avant de faire `hasExactPayload`.
4. La préflight initiale de cette nouvelle route comparait à tort le bloc 32-octets
   au seal 80-octets et refusait avant la materialization. Le correctif reconstruit
   donc le même Uniform80 puis vérifie `hasExactSemantic`, `hasExactPayload` et
   `GPUUniformSlabPlan.hasExactPayloads`.
5. `GPUCorePrimitiveClipStencilPreparedSlabAuthority` copie exclusivement le
   payload du seal dans le slab, et le materializer upload ce slab exact. Comme la
   préflight refusait auparavant, aucun bind ni draw natif n'avait encore lieu.

Hypothèse unique confirmée : une divergence **préflight/ABI** (source de payload
32 vs 80), et non un défaut mapper, snapshot, pipeline ou shader. Le test RED
minimal a d'abord exigé la préflight `Prepared` pour le frame builder réel tout en
prouvant `32 B` semantic, `80 B` reconstruit et égal au seal; il a échoué à
`GPUFramePreflighterTest.kt:1121` avec le diagnostic ci-dessus. Le correctif unique
de la préflight l'a rendu vert, ainsi que le rendu Surface public.

### Tests ajoutés ou renforcés

- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUCorePrimitiveClipStencilNativeRouteTest.kt` : refus `EvenOdd`, refus alpha
  translucide, refus `DRRect` naturellement couvert, et acceptation inverse
  `Winding` sans abaissement triangles.
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt` : baseline builder réel `32 B → Uniform80`, et substitutions semantic/payload/plan toutes refusées sans side effect.
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageSurfaceTest.kt` : `EvenOdd` RRect refusé; le cas positif vérifie diagnostics vides,
  `opsRefused == 0`, pipeline et draw call positifs, plus les pixels attendus.

Commandes exécutées :

- RED : `rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*analytic rrect clip stencil uniform80 requires its exact semantic payload and plan*' --console=plain`
  — `BUILD FAILED`, 1 test échoué à la baseline `Prepared` (diagnostic preflight
  ci-dessus).
- GREEN ciblé : `rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GPUCorePrimitiveClipStencilNativeRouteTest*' --tests '*analytic rrect clip stencil uniform80 requires its exact semantic payload and plan*' :kanvas:test --tests '*opaque identity rrect renders inside one hard path clip stencil scope*' --tests '*even odd hard path clip rrect remains outside the analytic rrect admission*' --console=plain`
  — `BUILD SUCCESSFUL`; 14 tests de route, 1 test Uniform80 et 2 tests Surface
  affichés `PASSED`.
- Suite requise : `rtk ./gradlew --no-daemon :integration-tests:gpu-evidence:test --console=plain`
  — `BUILD SUCCESSFUL`; 250 tests, 1 skip existant, 0 failure et 0 error.
- `rtk git diff --check` — sortie vide.

Limite de signal public : `RenderResult` expose diagnostics, refus, nombre de
pipelines et draw calls, mais pas l'identité exacte du programme stencil-read ni un
compteur de fallback CPU. Le test Surface n'invente donc pas ces assertions; les
tests de route/preflight établissent le pipeline analytic et le hand-off natif,
pendant que le Surface établit l'absence de diagnostic/refus observable.

## Fix round 2

`GPUCorePrimitiveClipStencilNativeRoute` contrôle désormais les deux autorités de
fill avant la validation générique du producer : pour tout consommateur `RRect`,
`path.fillRule` **et** `stencil.producer.fillRule` doivent être `Winding`. Le test
de route forge le payload dissocié `path=Winding` / `producer=EvenOdd` et exige le
refus stable `unsupported.native-core-primitive.clip-stencil.rrect-fill-rule`.

TDD : avant ce contrôle, ce test RED échouait avec
`expected rrect-fill-rule but was invalid.native-core-primitive.clip-stencil.producer-state`;
après le correctif, `rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*GPUCorePrimitiveClipStencilNativeRouteTest*' --console=plain`
est `BUILD SUCCESSFUL` (14 tests). La suite `:integration-tests:gpu-evidence:test`
et `rtk git diff --check` sont exécutés pour ce round avant commit.
