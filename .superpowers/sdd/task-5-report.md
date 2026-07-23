# Task 5 — Progressive JPEG multicomposant

## Résultat

- Ajout de `decodeProgressiveDct(frame)` : stockage des coefficients par composante et bloc, puis IDCT, upsampling et composition existante.
- Prise en charge des scans DC et AC interleaved/non-interleaved, refinement, EOB runs et DRI/restart markers. Les prédicteurs DC et EOB runs sont réinitialisés aux frontières de restart.
- Le chemin progressif de `JpegCodec` consomme désormais les échantillons communs (`composePixels` et sortie F16 existante) au lieu du pipeline grayscale dédié, retiré sans duplication.
- Diagnostics stables exposés par `JpegDocument.decode` : `jpeg.progressive.scan.duplicate`, `.refinement-order`, `.order`, `.incomplete` et `.table`.

## TDD et régression

- RED initial : une fixture 3-composantes DC+AC retournait `kUnimplemented` avec l’ancien décodeur grayscale-only.
- Une passe complète a révélé une régression du fixture grayscale AC EOB-run. La validation traitait à tort les zéros AC non initialisés d’une refinement scan à bande élargie comme une erreur d’ordre. La validation autorise désormais ce cas AC, tout en gardant le DC strict ; le fixture historique est vert.

## Vérification

Commande exécutée après les changements :

```text
rtk ./gradlew :codec:jpeg:test --no-daemon
```

Résultat : `BUILD SUCCESSFUL` — 81 tests JPEG, dont `JpegProgressiveDecodeTest` (couleur DC/AC, refinement/EOB-run, restart interval et diagnostics).

## Portée

`JpegCodec.kt` a été modifié avec l’autorisation explicite de raccorder le nouveau décodeur progressif à `composePixels` et aux sorties existantes. Aucun fallback, AWT, ImageIO, JNI ou `java.desktop` n’a été ajouté.

## Correctif P1 — Grille de coefficients MCU paddée

- Cause : la grille de coefficients progressive était dimensionnée depuis le plan de samples visible. Une image 17×8 avec Y 2×1 et Cb/Cr 1×1 possède deux MCU horizontaux : Y requiert donc quatre blocs, alors que le plan visible Y (17 samples) n’en réservait que trois. Le second MCU déclenchait `ArrayIndexOutOfBoundsException`.
- Correctif : la grille de coefficients est désormais dimensionnée par le nombre de MCU paddés, multiplié par le sampling horizontal et vertical de chaque composante. Les buffers de samples restent dimensionnés à la zone visible ; l’IDCT ignore les samples hors cadre.
- Régression ajoutée : fixture progressive 17×8 Y 2×1 / CbCr 1×1, scan DC interleaved, scans AC non-interleaved et DRI=1. Elle vérifie l’absence d’exception, les samples Y visibles et les pixels RGB aux deux extrémités horizontales.

### TDD et vérification du correctif

- RED observé avant le correctif : `ArrayIndexOutOfBoundsException` depuis `decodeProgressiveScan` sur la fixture subsamplée non alignée MCU.
- GREEN :

```text
rtk ./gradlew :codec:jpeg:test --no-daemon --tests org.graphiks.kanvas.codec.jpeg.JpegProgressiveDecodeTest
rtk ./gradlew :codec:jpeg:test --no-daemon
```

Résultats : `BUILD SUCCESSFUL` ; test ciblé (5 tests) et suite JPEG complète (81 tests) verts.
