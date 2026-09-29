# Task 2 — stencil, frontières et budget AA-mask

## Implémentation

`PlanW4dAaCoverageSourceBindingV1` accepte maintenant soit la phase directe, soit le couple
`MultisampleStencilProducer` / `MultisampleStencilColorCover`. Le couple reste un seul owner
`FilterCoverageSourcePass` et une seule passe native MSAA4 : clear transparent + stencil zero,
producer, cover/test-reset, puis resolve `CoverageSource` uniquement sur le cover terminal.

La recipe, le layout, l’autorité préparée, les packets, operands et validations scellent les deux
command groups et le D24S8 propre à l’occurrence. Les clé native AA-cover sont exactement
color + resolve terminal + depth/stencil + V/I/U par groupe. Les chemins W4d couleur et le raster
stencil historique restent distincts. La source coverage conserve son scissor brut jusqu’à la
localisation W6b ; le scissor/clip final est donc local et borné par la recette filtrée.

Les nouveaux témoins publics couvrent anneau winding/halo/phases fractionnaires, bord offscreen et
clip, chronologie de siblings, second rendu, et B/B−1 avec sentinel/readback/recovery.

## Budget dérivé avant exécution

Triangle 96 : raw `64×64`; blur H `74×64`; V/styled/shaded/materialized `74×74`.
`root 96²×4 + raw 64²×(resolve4 + AA4 16) + H×4 + 4×halo×4 = 225344`;
readback `512×96=49152`; W4 V/I/U `16384+4096+4096`; uniforms W6/W5 `16+16`;
**B=299104**, B−1=299103.

Anneau 128 : raw `96×96`; support `3σ=6.9`; H `110×96`; V/styled/shaded/materialized
`110×110`. `root 128²×4 + raw 96²×(resolve4 + AA4 16 + D24S8 AA4 16) + H×4 +
4×halo×4 = 633152`; readback `512×128=65536`; mêmes V/I/U et uniforms;
**B=723296**, B−1=723295. Ces valeurs ont été envoyées au contrôleur avant le premier run budget;
aucun peak planner ni recherche par seuil n’a été utilisé.

## Runs et archives

- RED `task2-red`: exit 1, 7 tests / 3 failures attendues (stencil et offscreen non admis).
- Compiles ciblées: premier renderer compile exit 1 (erreurs Kotlin de destructuring), correction;
  compilations/tests suivants ont porté les diagnostics de progression archivés.
- Diagnostics `task2-green-probe`, `task2-native-diagnostic`, `task2-native-site-graphvalidation`,
  `task2-native-site-init`, `task2-native-site-owner`, `task2-stack-trace`, `task2-require-site`,
  `task2-native-keys`, `task2-native-stencil-contract`: exits 1, sites localisés puis supprimés.
- `task2-native-keys-resolve`: exit 0, 7/7; `task2-budget`: exit 0; `task2-green`: exit 0, 8/8.
- `task2-related`: exit 1, 75/12 (gate AA couleur inversé et resolve legacy indu); `task2-related-rerun`:
  exit 0, 75/75. `final` et `final-text`: exit 0, 75/75.
- Tentative globale unique `full-suite-240`: watchdog Ruby local 240 s (car `timeout` absent),
  exit 124; Gradle/test worker a été borné. XML/events: 724 identités closes, 680 pass, 43 fail,
  1 skipped. Comparaison baseline: 724 communes, aucune nouvelle failure; seul changement
  `W5eDecodedImageSurfacePixelTest.formatsAlphaAndColorSpaceMatchOracle()` passé→skipped à la borne.
  `cubicDrawImageMatchesMitchellNetravaliOracle()` n’a pas été atteint dans cette candidate.

Commandes utilisent `rtk proxy ./gradlew :kanvas:test --offline --no-build-cache`, l’init isolé
`/private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle` et les archives
`/private/tmp/kanvas-w7-aa-mask.9OewPc/*`.

## Contrôles finaux, warnings et limites

`git diff --check` est propre. Warnings conservés à chaque Gradle : `System::load`,
`Unsafe::objectFieldOffset`, dépréciations Gradle 10; pas de changement outillage. La globale
reste rouge/incomplète historiquement et a été interrompue par sa borne : aucune parité globale
ni suite entièrement verte n’est revendiquée. Tous les processus Gradle/GPU ont rendu un exit
terminal avant handoff; runtime idle.

Fichiers modifiés : binding coverage W4d, compiler W4d, construction/validation W6a, autorités/
lowering/materializer/encoder/preflight renderer, et `W7AaMaskBlurSurfacePixelTest`.
