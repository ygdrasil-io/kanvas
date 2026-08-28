# W33 — RadialGradient à trois stops

## Périmètre livré

La route publique `Surface` accepte maintenant un `FillRect` RGBA8 non-AA avec
un `RadialGradient` `CLAMP` à trois stops opaques, CTM et `localMatrix`
identités. Les routes radiales à un ou deux stops déjà présentes restent
inchangées.

La tranche est volontairement plus étroite que l'ABI CorePrimitive : quatre
stops ou plus sont refusés avant soumission avec
`unsupported.material.radial_gradient_stop_count`. Un radial à trois stops
avec `REPEAT`, AA, CTM non identité ou `localMatrix` non identité est aussi
refusé avant toute soumission. Il n'y a pas de claim pour RRect, Path, clip,
affine, filtre, ou d'autres tile modes.

## Vérification ABI et route

Le payload CorePrimitive, le packing uniforme et le WGSL portent déjà jusqu'à
seize stops. W33 n'élargit pas cette capacité interne : l'analyse n'ouvre le
troisième stop que pour la route publique bornée ci-dessus. Les autres routes
conservent leur admission à un ou deux stops.

## Preuve native promue

`radial-gradient-three-stops` est promu dans
`correctness/promoted/radial-gradient-three-stops/` pour le commit
`db290fc6703a00545a007f0acbb9718e96cada79`.

- Oracle CPU indépendant : décodage sRGB, interpolation linear-premultiplied,
  stockage sRGB RGBA8.
- GPU : `kanvas.surface.render`, une soumission, un draw et un bind pipeline.
- Diff : 0 pixel différent, delta maximal 0, similarité 100 %, tolérance 1 LSB.
- Diagnostics : aucun refus, `submissionDelta = 1`.

Commandes rejouables :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=radial-gradient-three-stops -PsourceCommit=db290fc6703a00545a007f0acbb9718e96cada79
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=radial-gradient-three-stops -PsourceCommit=db290fc6703a00545a007f0acbb9718e96cada79
./gradlew --no-daemon :integration-tests:gpu-evidence:promoteGpuEvidence -Pscene=radial-gradient-three-stops -PsourceCommit=db290fc6703a00545a007f0acbb9718e96cada79 -PpromotionReviewer=codex -PpromotionReason='W33 validates bounded three-stop clamp radial FillRect rendering.'
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
