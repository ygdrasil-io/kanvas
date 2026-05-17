# Audit — `SkColor4f.kt` ↔ `include/core/SkColor.h` (template `SkRGBA4f<kAT>`) + `src/core/SkColor.cpp`

## Statistiques
- 35 symboles audités (depuis la TSV)
- 33 alignés
- 2 divergences fonctionnelles
- 2 divergences SIMD-only (skip)
- 0 à-vérifier

## Divergences fonctionnelles

### `SkColor4f.toBytes_RGBA()` ↔ `SkColor4f::toBytes_RGBA`
- **Kotlin** : `math/src/main/kotlin/SkColor4f.kt:88-92`
- **C++** : `src/core/SkColor.cpp:147-149` (utilise `Sk4f_toL32`, `src/core/SkSwizzlePriv.h:53-60`)
- **Nature** : layout d'octets opposé dans le `uint32` retourné
- **Détail** :
  - Kotlin retourne `(R<<24) | (G<<16) | (B<<8) | A` (R en MSB, A en LSB).
  - C++ stocke (R, G, B, A) dans 4 octets mémoire consécutifs, puis lit ça comme `uint32_t` little-endian → la valeur entière est `(A<<24) | (B<<16) | (G<<8) | R` (R en LSB, A en MSB).
  - Le commentaire C++ "RGBA order (eg GrColor)" décrit l'ordre **mémoire**, pas l'ordre des bits dans la valeur entière.
- **Impact estimé** : correctness — toute roundtrip entre Kotlin et un buffer de bytes natif ou tout consumer attendant `GrColor` lira les composantes inversées. Bug latent si on échange ces uint32 avec du code C natif ou des shaders.

### `SkColor4f.Companion.FromBytes_RGBA(rgba)` ↔ `SkColor4f::FromBytes_RGBA`
- **Kotlin** : `math/src/main/kotlin/SkColor4f.kt:137-142`
- **C++** : `src/core/SkColor.cpp:151-155` (utilise `Sk4f_fromL32`, `SkSwizzlePriv.h:49-51`)
- **Nature** : symétrique du précédent
- **Détail** :
  - Kotlin lit `fR = ((rgba >>> 24) & 0xFF) / 255f` etc. (R en MSB).
  - C++ lit byte[0] (= `rgba & 0xFF`) comme R → `fR = (rgba & 0xFF) / 255f`.
  - Self-consistent côté Kotlin (`toBytes_RGBA(FromBytes_RGBA(x)) == x`), mais incompatible avec l'upstream.
- **Impact estimé** : correctness — voir ci-dessus. Les deux divergences vont ensemble : soit on inverse les deux pour aligner sur upstream, soit on documente la convention du port.

## SIMD-only (skip)

- `SkColor4f::FromColor` (C++ utilise `swizzle_rb(Sk4f_fromL32(bgra))`) — équivalent scalaire côté Kotlin produit le **même** résultat (`fR = SkColorGetR(c)/255`, etc.) car la double-swizzle annule l'inversion d'endianness. Aligned malgré la SIMD.
- `SkColor4f::toSkColor` (C++ `Sk4f_toL32(swizzle_rb(...))`) — idem : composition swizzle+endian → `0xAARRGGBB`, identique au Kotlin `SkColorSetARGB(A, R, G, B)`. Aligned malgré la SIMD.

## À vérifier

- Néant.

## Aligned (verified)

- Champs `fR, fG, fB, fA` — ordre identique (`SkColor.h:264-267`).
- `vec()` / `array()` retournent `[R, G, B, A]` (`SkColor.h:309, 318`).
- `operator[]` (`SkColor4f.kt:33-36` ↔ `SkColor.h:325-328`) : indices 0=R, 1=G, 2=B, 3=A.
- `isOpaque()` : `fA == 1.0f` (strict equality, aligned, `SkColor.h:345-348`).
- `fitsInBytes()` : ranges `[0,1]` (`SkColor.h:351-356`).
- `times(Float)` et `times(SkColor4f)` : composant-par-composant exact (`SkColor.h:292, 301`).
- `makeOpaque()`, `pinAlpha()`, `withAlpha()`, `withAlphaByte()` : algébriquement identiques (`SkColor.h:416-439`). Note : Kotlin `withAlphaByte(a: Int)` fait `(a and 0xFF) / 255f` ; upstream `a/255.f` avec `uint8_t a`. Aligned mais Kotlin tolère des Ints hors-range via mask.
- `premul()` : `(R*A, G*A, B*A, A)` exact (`SkColor.h:386-389`).
- `unpremul()` : edge-case `fA == 0` → `(0,0,0,0)` ; sinon `(R/A, G/A, B/A, A)`. Identique upstream (`SkColor.h:396-405`).
- `channelToByte` interne ↔ `Sk4f_toL32` arrondi : tous deux `pin(v*255 + 0.5, 0, 255)` puis cast int. Aligned.
- Constantes `SkColors::k*` : ordre/valeurs identiques (`SkColor.h:456-468`).
- `FromColor` : sémantique identique (R, G, B, A de l'ARGB packé / 255), malgré l'implémentation SIMD upstream.
- `FromPMColor`: trivial wrap autour `FromColor` — identique upstream (`SkColor.h:379`, juste un cast template).
