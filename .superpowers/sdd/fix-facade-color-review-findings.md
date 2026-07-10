# Finding 10 - correction des façades colorimétriques

## Verdict

Finding 10 était valide. Les deux façades perdaient le tag source sans
transformer les pixels:

- `KanvasCodec.toKanvasColorSpace()` remplaçait tout profil nonlinear non-sRGB
  par `ColorSpace.SRGB`;
- `CodecImageDecoder` codait `ColorSpace.SRGB` en dur après le décodage.

## Cycle TDD

Commande RED:

```text
./gradlew :codec:api:test --tests 'org.graphiks.kanvas.codec.*ColorSpaceTest'
```

Résultat avant modification de production: 8 tests exécutés, 8 échecs
attendus. Les régressions rouges couvraient Display P3, Rec.2020 SDR, Rec.2020 PQ,
Rec.2020 HLG, les refus de transfert/gamut inconnus et le chemin
`CodecImageDecoder`.

Deux cas de frontière ajoutés ensuite couvrent le profil Display P3 sérialisé
et le refus de la courbe BT.2020 native. La commande finale exécute donc 10
tests avec 0 échec.

## Implémentation

`KanvasCodec.kt` fournit désormais un mapping partagé:

- les transferts sRGB et linear sont reconnus avec la même tolérance que la
  façade `SkColorSpace` et peuvent conserver les gamuts sRGB, Display P3 ou
  Rec.2020;
- PQ est reconnu pour les profils cICP canoniques sRGB, Display P3 et Rec.2020;
- HLG est reconnu pour le profil cICP canonique Rec.2020 autorisé par H.273;
- les autres transferts, gamuts, profils HDR et profils déjà refusés lèvent une
  `IllegalArgumentException` explicite au lieu de produire un faux tag sRGB.

La représentation Rec.2020 SDR testée est la combinaison exacte disponible
dans Kanvas: primaires Rec.2020 et transfert sRGB SDR. La courbe BT.2020 native
n'a pas de valeur dans `TransferFunction`; elle suit donc le refus explicite
`transfer` plutôt que d'être présentée comme sRGB.

`CodecImageDecoder` appelle ce même mapping sur le `SkBitmap` décodé. Il
convertit uniquement le refus connu en
`ImageDecodeResult.Failure("codec.color-space-unsupported:<reason>")`. La copie
RGBA reste inchangée et aucun color transform (transformation colorimétrique)
n'est ajouté.

## Vérification

```text
./gradlew :codec:api:test :codec:test
```

Résultat: `BUILD SUCCESSFUL`; 10 tests `codec:api` et 4 tests d'assemblage
`codec` passent. Tous les modules codec downstream requis par `:codec:test`
recompilent avec le nouveau mapping.

Le contrôle `git diff --check` ne signale aucune erreur. Le diff de ce commit
est limité aux deux façades autorisées, aux tests associés, aux dépendances
JUnit minimales de `codec/api` et à ce rapport. Les modifications concurrentes
sous `codec/png` n'ont pas été touchées.
