# Module math

Couche math pure de Kanvas — types et primitives mathématiques (points, rectangles, matrices, vecteurs, couleurs, scalaires) réutilisables hors de l'écosystème Skia.

Le namespace `org.graphiks.math` est volontairement distinct de `org.skia.*` pour refléter cette indépendance.

Chaque symbole public de `:math` qui mirror un symbole Skia upstream est listé dans la map de correspondance Kotlin ↔ C++ sous [`.upstream/source/map/math/`](https://github.com/ygdrasil-io/kanvas/tree/master/.upstream/source/map/math). Format TSV 4 colonnes : `kotlin FQN | cpp FQN | kotlin path:line | cpp path:line`. Voir la [spec](https://github.com/ygdrasil-io/kanvas/tree/master/.upstream/source/map/README.md).

# Package org.graphiks.math

Tous les types et helpers du module — la couche est volontairement plate, un seul package.

Familles de types :

- **Points 2D entiers/flottants** : `SkIPoint`, `SkPoint`, `SkPoint3` (et le typealias `SkVector` = `SkPoint`)
- **Rectangles** : `SkIRect` (entier), `SkRect` (flottant)
- **Tailles** : `SkISize`, `SkSize`
- **Matrices** : `SkMatrix` (3×3 affine + perspective), `SkM44` (4×4 projection 3D)
- **Vecteurs N-D** : `SkV2`, `SkV3`, `SkV4`
- **Couleurs** : `SkColor` (ARGB packé), `SkColor4f` (RGBA flottant), `SkColorMatrix` (5×4 transfo)
- **Scalaires** : `SkScalar` typealias + helpers (`SkScalarFloor`, `SkScalarRoundToInt`, etc.)
- **Pathops double-précision** : `SkDPoint`, `SkDVector`, `SkDLine`, `SkPathOpsTypes` (~90 helpers `approximately_*` / `precisely_*` / `roughly_*`)
- **Skcms types** : `SkcmsTransferFunction`, `SkcmsMatrix3x3`, `SkcmsMatrix3x4` (color-management primitives)
