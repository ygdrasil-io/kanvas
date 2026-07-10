# Correctif P1: classification ICC stricte de la facade codec

## Resultat

Le finding P1 de `.superpowers/sdd/final-review-last-p1-fixes.md` est corrige.
La facade n'applique plus la tolerance globale de `24 / 65536` autour des
gamuts nommes. Elle reconnait explicitement, pour sRGB, Display P3 et Rec.2020:

- la matrice canonique;
- la matrice produite par le round-trip ICC avec transfert sRGB;
- la matrice produite par le round-trip ICC avec transfert lineaire.

Chaque forme autorisee est comparee avec une tolerance etroite de `2 / 65536`.
Une matrice arbitraire proche ne beneficie donc plus de la derive necessaire au
round-trip Rec.2020.

## TDD

### Rouge

Le test negatif de `KanvasCodecColorSpaceTest` a d'abord ete remplace par la
reproduction du finding: ajout de `3 / 65536` a la premiere composante et
retrait de `3 / 65536` a la deuxieme composante de chaque ligne de sRGB, ce qui
preserve D50, puis passage par `SkICC.WriteToICC`, `skcmsParse` et
`SkColorSpace.make`.

Commande:

```text
rtk ./gradlew :codec:api:test \
  --tests 'org.graphiks.kanvas.codec.KanvasCodecColorSpaceTest.nearby unknown gamut is refused instead of retagged as sRGB' \
  --rerun-tasks --no-daemon
```

Resultat attendu observe:

```text
KanvasCodecColorSpaceTest > nearby unknown gamut is refused instead of retagged as sRGB() FAILED
1 test completed, 1 failed
BUILD FAILED
```

### Vert cible

Apres le changement minimal de classification, la classe de tests ciblee
preserve les round-trips ICC sRGB, linear sRGB, Display P3 et Rec.2020, tout en
refusant la matrice proche non canonique.

```text
rtk ./gradlew :codec:api:test \
  --tests 'org.graphiks.kanvas.codec.KanvasCodecColorSpaceTest' \
  --rerun-tasks --no-daemon

18 tests completed, 0 failed
BUILD SUCCESSFUL
```

## Verification

Suite API complete:

```text
rtk ./gradlew :codec:api:test --rerun-tasks --no-daemon

21 tests completed, 0 failed
BUILD SUCCESSFUL
```

Controle du diff:

```text
rtk git diff --check
```

La commande termine avec le code de sortie 0 et sans diagnostic.

## Perimetre

Les changements fonctionnels sont limites a:

- `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/KanvasCodec.kt`;
- `codec/api/src/test/kotlin/org/graphiks/kanvas/codec/KanvasCodecColorSpaceTest.kt`.

Le present fichier est le rapport demande pour le correctif.
