# Correction P1 - classification ICC des gamuts de facade

## Verdict

Le P1 de `final-review-color-metadata.md` est corrige. Les profils ICC sRGB et
Rec.2020 produits par `SkICC.WriteToICC(...)`, reparses par `skcmsParse(...)`,
puis rouverts par `SkColorSpace.make(...)` conservent maintenant leur tag
Kanvas representable. `CodecImageDecoder` ne retourne plus
`codec.color-space-unsupported:gamut` pour un ICC sRGB serialise.

## Root cause

`SkColorSpace.toKanvasColorSpace()` comparait les matrices `toXYZD50` des
profils reparses aux gamuts nommes avec la tolerance des transfer functions,
soit `2 / 65536`. La normalisation et la quantification du round-trip ICC
deplacent davantage certaines composantes. L'ecart maximal mesure pour
Rec.2020 est `3.0225515E-4`, soit environ 19,8 pas `s15Fixed16`.

## Cycle TDD

### RED

Des tests ont d'abord ete ajoutes pour les routes reelles
`SkICC.WriteToICC -> skcmsParse -> SkColorSpace.make`:

- sRGB avec transferts sRGB et linear;
- Rec.2020 avec transferts sRGB et linear;
- passage d'un ICC sRGB rouvert dans `CodecImageDecoder`, avec verification du
  tag et des samples RGBA.

La commande ciblee a produit cinq echecs attendus: quatre refus `gamut` dans
`KanvasCodecColorSpaceTest` et un echec du decoder dans
`CodecImageDecoderColorSpaceTest`.

### GREEN

La classification des matrices a ete centralisee dans
`classifyNamedGamut()`. Elle utilise une tolerance ICC dediee de
`24 / 65536`, juste au-dessus de l'ecart Rec.2020 mesure. La tolerance des
transfer functions reste inchangee a `2 / 65536`.

Une regression negative perturbe sRGB de `64 / 65536` sur une composante et
verifie que ce gamut inconnu reste refuse, en plus du test existant avec la
matrice identite.

## Verification

```text
./gradlew :codec:api:test \
  --tests org.graphiks.kanvas.codec.KanvasCodecColorSpaceTest \
  --tests org.graphiks.kanvas.codec.CodecImageDecoderColorSpaceTest \
  --rerun-tasks --no-daemon
BUILD SUCCESSFUL in 24s - 21 tests passes

./gradlew :math:jvmTest :kanvas:test :codec:api:test :codec:test \
  :codec:png:test :color-management:test --rerun-tasks --no-daemon
BUILD SUCCESSFUL in 25s - 83 taches executees

git diff --check
exit 0
```

`CodecImageDecoder.kt` n'a pas eu besoin d'etre modifie: il reutilise deja
`toKanvasColorSpace()`. Les changements concurrents observes dans
`codec/png/` sont hors ownership et ne font pas partie de cette correction.
