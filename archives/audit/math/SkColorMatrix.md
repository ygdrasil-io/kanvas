# Audit — `SkColorMatrix.kt` ↔ `include/effects/SkColorMatrix.h` + `src/effects/SkColorMatrix.cpp`

## Statistiques
- 11 symboles audités (depuis la TSV)
- 11 alignés
- 0 divergence fonctionnelle
- 0 divergence SIMD-only (skip)
- 0 à-vérifier

## Divergences fonctionnelles

Aucune dans le périmètre TSV. Le cœur algorithmique est porté à l'identique.

## SIMD-only (skip)

- Néant — `SkColorMatrix.cpp` est entièrement scalaire upstream.

## À vérifier

- Néant.

## Aligned (verified)

- **Constructeur 20-args** (`SkColorMatrix.kt:40-50` ↔ `SkColorMatrix.h:25-32`) : ordre row-major identique.
- **`setIdentity`** (`SkColorMatrix.kt:53-59` ↔ `SkColorMatrix.cpp:67-70`) : fill(0) + 4 diagonales = 1.
- **`setScale`** (`SkColorMatrix.kt:65-71` ↔ `SkColorMatrix.cpp:72-78`) : identique. Default `aScale = 1f` aligned avec `SkColorMatrix.h:38`.
- **`postTranslate`** (`SkColorMatrix.kt:74-79` ↔ `SkColorMatrix.cpp:80-85`) : `fMat[kX_Trans] += dx` x4. Bit-à-bit identique.
- **`setSaturation`** (`SkColorMatrix.kt:106-116` ↔ `SkColorMatrix.cpp:105-116`) : mêmes constantes `kHueR=0.213, kHueG=0.715, kHueB=0.072` ; mêmes lignes `(R+sat, G, B)`, `(R, G+sat, B)`, `(R, G, B+sat)`. La séquence FP `R = kHueR * (1f - sat)` est strictement préservée. Aligned.
- **`setRowMajor` / `getRowMajor`** (`SkColorMatrix.kt:119-128` ↔ `SkColorMatrix.h:48-49`) : copyInto/copy_n. Aligned.
- **`preConcat` / `postConcat`** (`SkColorMatrix.kt:137-147` ↔ `SkColorMatrix.h:43-44`) :
  - Kotlin `preConcat(other)` → `setConcat(this, other)`. C++ idem (`*this, mat`).
  - Kotlin `postConcat(other)` → `setConcat(other, this)`. C++ idem (`mat, *this`).
  - Ordre `outer × inner` strictement identique.
- **`setConcatInto`** (`SkColorMatrix.kt:205-231` ↔ `SkColorMatrix.cpp:37-65` `set_concat`) : algorithme bit-à-bit. Même gestion de l'aliasing par scratch buffer (`outer === result || inner === result` → `target = tmp`, puis `copyInto`). Même séquence des 5 multiplications par cellule (`outer[j+0]*inner[i+0] + ... + outer[j+3]*inner[i+15]`) puis la colonne de translation (`+ outer[j+4]`). FP order identique.
- **`equals` / `hashCode`** : `contentEquals` / `contentHashCode` — équivalents au comportement implicite C++ `std::array<float, 20>` (égalité par valeur).

## Notes mineures (hors TSV)

- Kotlin ajoute `setRGB2YUV` / `setYUV2RGB` qui **hardcodent uniquement JPEG-Full** (`SkColorMatrix.kt:87-99`), tandis qu'upstream expose `RGBtoYUV(SkYUVColorSpace) / YUVtoRGB(SkYUVColorSpace)` avec dispatch sur `cs` (`SkColorMatrix.cpp:13-23`). Hors scope TSV — réduction API consciente pour Phase 1. Les constantes JPEG-Full vérifiées contre `src/core/SkYUVMath.cpp` (kJPEG_Full_SkYUVColorSpace = 0).
- Kotlin ajoute `operator times` qui n'a pas d'équivalent upstream — convenience.
- Le constructeur Kotlin `SkColorMatrix(values: FloatArray)` n'a pas d'équivalent direct upstream (mais `setRowMajor` est sémantiquement équivalent).
