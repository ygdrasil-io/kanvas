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
