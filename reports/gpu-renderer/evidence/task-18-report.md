# W32 — LinearGradient à trois stops

## Périmètre livré

La route publique `Surface` accepte maintenant un `FillRect` RGBA8 non-AA avec un `LinearGradient` `CLAMP` à trois stops opaques, axe et transform identité, bornes entières. La route conserve les gradients `REPEAT` déjà promus et les gradients `CLAMP` à deux stops.

La tranche ne généralise pas les autres routes : les gradients à quatre stops ou plus restent refusés avant soumission par `unsupported.material.mapping.linear_gradient_stop_count`. Les modes hors borne et les transforms non pris en charge gardent leurs refus existants.

## Vérification ABI

L'ABI native était déjà capable de transporter jusqu'à seize stops : payload `LinearGradient`, packing des uniforms, réflexion WGSL, shader `stop_data`, cache/pipeline et materializer. Le verrou supprimé est uniquement le préflight PreparedMapping historique qui exigeait deux stops. L'analyse n'ouvre trois stops que pour `FillRect`; les autres routes restent fermées.

## Preuve native promue

`linear-gradient-three-stops` est promu dans `correctness/promoted/linear-gradient-three-stops/` pour le commit `2cf27941d3668b2c522720389e7d687ac54bba9e`.

- Oracle CPU indépendant : décodage sRGB, interpolation en linear-premultiplied, stockage sRGB RGBA8.
- GPU : une soumission, un draw et un bind de pipeline via `kanvas.surface.render`.
- Diff : 0 pixel différent, delta maximal 0, similarité 100 % avec tolérance 1 LSB.
- Diagnostic : aucun refus, `submissionDelta = 1`.

Commandes rejouables :

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence -Pscene=linear-gradient-three-stops -PsourceCommit=2cf27941d3668b2c522720389e7d687ac54bba9e
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyGeneratedGpuEvidence -Pscene=linear-gradient-three-stops -PsourceCommit=2cf27941d3668b2c522720389e7d687ac54bba9e
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
