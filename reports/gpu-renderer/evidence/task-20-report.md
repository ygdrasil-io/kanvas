# W34 — SweepGradient à trois stops

## Périmètre livré

La route publique `Surface` accepte maintenant un `FillRect` RGBA8 non-AA avec
un `SweepGradient` `CLAMP` à trois stops opaques, CTM et `localMatrix`
identités. Les routes sweep à un ou deux stops déjà présentes ne changent pas.

Cette tranche reste volontairement plus étroite que l'ABI CorePrimitive :
quatre stops ou plus sont refusés avant soumission par
`unsupported.material.sweep_gradient_stop_count`. Un sweep à trois stops avec
`REPEAT`, AA, CTM non identité ou `localMatrix` non identité est également
refusé avant toute soumission. Il n'y a aucun claim pour RRect, Path, clip,
affine, filtre, ni autres tile modes.

## Vérification ABI et route

Le payload CorePrimitive, le packing uniforme et le WGSL supportent déjà
jusqu'à seize stops. W34 ne généralise pas cette capacité interne :
l'analyse n'ouvre le troisième stop que pour le `FillRect` public borné. Les
autres consommateurs restent limités à un ou deux stops.

Le `REPEAT` sweep à deux stops conserve son diagnostic historique. Le cas à
trois stops est fermé par le contrôle de nombre de stops avant son admission
dans la route : la politique reste stable sans modifier les contrats W32/W33.

## Preuve native promue

`sweep-gradient-three-stops` est promu dans
`correctness/promoted/sweep-gradient-three-stops/` pour le commit
`fb13555e006712ee9a7aa09de054c49de868ec05`.

- Oracle CPU indépendant : décodage sRGB, interpolation linear-premultiplied,
  stockage sRGB RGBA8.
- GPU : `kanvas.surface.render`, une soumission, un draw et un bind pipeline.
- Diff : 0 pixel différent, delta maximal 0, similarité 100 %, tolérance 1 LSB.
- Diagnostics : aucun refus, `submissionDelta = 1`.

Commandes rejouables :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=sweep-gradient-three-stops -PsourceCommit=fb13555e006712ee9a7aa09de054c49de868ec05
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=sweep-gradient-three-stops -PsourceCommit=fb13555e006712ee9a7aa09de054c49de868ec05
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=sweep-gradient-three-stops -PsourceCommit=fb13555e006712ee9a7aa09de054c49de868ec05 -PpromotionReviewer=codex -PpromotionReason='W34 validates bounded three-stop clamp sweep FillRect rendering.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
