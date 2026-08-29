# W34 — SweepGradient à trois stops

## Périmètre livré

La route publique `Surface` accepte maintenant un `FillRect`
`RGBA8_UNORM_SRGB` non-AA avec un `SweepGradient` `CLAMP` à trois stops
opaques, CTM et `localMatrix` identités, et un clip `WideOpen`. Les routes
sweep à un ou deux stops déjà présentes ne changent pas.

Cette tranche reste volontairement plus étroite que l'ABI CorePrimitive :
quatre stops ou plus sont refusés avant soumission par
`unsupported.material.sweep_gradient_stop_count`. Un sweep à trois stops avec
`REPEAT`, AA, CTM non identité ou `localMatrix` non identité est également
refusé avant toute soumission. Les targets `RGBA8_UNORM` et `BGRA8_UNORM`, un
clip `DeviceRect` ou complexe, RRect, Path, affine, filtre et les autres tile
modes restent hors périmètre.

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
`1794a51493002de2bfad121bd274f032d8fbbb83`.

- Oracle CPU indépendant : décodage sRGB, interpolation linear-premultiplied,
  stockage `RGBA8_UNORM_SRGB` (readback RGBA8 encodé sRGB).
- GPU : `kanvas.surface.render`, une soumission, un draw et un bind pipeline.
- Diff : 0 pixel différent, delta maximal 0, similarité 100 %, tolérance 1 LSB.
- Diagnostics : aucun refus, `submissionDelta = 1`.

Commandes rejouables :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=sweep-gradient-three-stops -PsourceCommit=1794a51493002de2bfad121bd274f032d8fbbb83
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=sweep-gradient-three-stops -PsourceCommit=1794a51493002de2bfad121bd274f032d8fbbb83
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=sweep-gradient-three-stops -PsourceCommit=1794a51493002de2bfad121bd274f032d8fbbb83 -PpromotionReviewer=codex -PpromotionReason='W34 proves bounded three-stop clamp sweep FillRect on the public sRGB Surface target.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
