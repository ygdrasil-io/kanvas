# Map de correspondance Kotlin ↔ Skia upstream

Cette arborescence trace, **hors du code Kotlin**, la correspondance entre chaque symbole Kotlin de `kanvas` et son équivalent C++ dans le repo Skia upstream (`https://github.com/google/skia`).

Le format est conçu pour être **requêtable depuis un terminal** (TSV, séparateur tab, grep/awk natif) et **bi-directionnel** (Kotlin → C++ et C++ → Kotlin).

## Format

TSV à 4 colonnes, **pas de header**, séparateur `\t` :

```
<kotlin FQN>	<cpp FQN>	<kotlin path:line>	<cpp path:line>
```

### Colonnes

| # | Colonne | Format | Exemples |
|---|---|---|---|
| 1 | `kotlin` | FQN du symbole Kotlin | `org.graphiks.math.SkPoint`, `org.graphiks.math.SkPoint.length`, `org.graphiks.math.SkPoint.Companion.Length`, `org.graphiks.math.SkPath.Verb.kMove`, `org.graphiks.math.SkColorSetARGB` |
| 2 | `cpp` | Symbole Skia upstream qualifié | `SkPoint`, `SkPoint::length`, `SkColorSetARGB`, `SkPath::Verb::kMove_Verb`, `SK_AlphaOPAQUE` |
| 3 | `kotlin path:line` | Chemin **relatif au repo kanvas** + ligne | `math/src/main/kotlin/SkPoint.kt:45` |
| 4 | `cpp path:line` | Chemin **relatif au repo Skia upstream** + ligne | `src/core/SkPoint.cpp:42` |

### Conventions

- **FQN Kotlin** : utiliser le nom qualifié complet. Pour les constructeurs : `Class.<init>`. Pour les méthodes de `companion object` : `Class.Companion.method`.
- **Symbole C++** : utiliser `::` pour séparer le scope. Pour les overloads ambigus, ajouter la signature : `SkPoint::scale(float)`. Pour les enum values, qualifier par le type : `SkPath::Verb::kMove_Verb`.
- **path:line** : un seul numéro de ligne (celui de la déclaration). Pas de range. Pas d'URL — la résolution se fait via `_resolve_url.sh`.

## Layout

```
.upstream/source/map/
├── README.md              ← ce fichier
├── _resolve_url.sh        ← path:line → URL clickable
├── audit.sh               ← liste les symboles publics Kotlin sans entrée TSV
└── <module>/
    └── <KotlinSource>.tsv ← un fichier par .kt
```

Un TSV **par source Kotlin** — diff git lisible, scope d'une PR limité.

## Qui doit être listé

**Obligatoire** : tout symbole `public` Kotlin qui mirror un symbole Skia upstream.
- `class`, `object`, `interface`, `enum class`, `typealias`, `data class`, `value class`, `sealed class`
- `fun`, `operator fun`, top-level fun
- `val`, `var`, `const val`
- Enum entries
- Constructeurs publics (FQN `Class.<init>`)
- Fichiers Kotlin qui portent un `.h`/`.cpp` upstream → 1 ligne « fichier-symbole » avec kotlin = nom du fichier sans extension

**Facultatif** : `private` / `internal` — seulement si la traçabilité aide.

**Exclu** : symboles Kotlin-original sans contrepartie upstream (extensions ergonomiques, helpers idiomatiques). En cas de doute, ne pas lister.

## Requêtes terminal — exemples

Toutes les requêtes utilisent grep / awk / cut natifs. Le glob `**/*.tsv` requiert `shopt -s globstar` en bash (`zsh` l'a par défaut).

### 1. Kotlin → C++

```bash
grep -P '^org\.graphiks\.math\.SkPoint\.length\t' .upstream/source/map/**/*.tsv | cut -f2,4
```

### 2. C++ → Kotlin

```bash
awk -F'\t' '$2=="SkPoint::length"' .upstream/source/map/**/*.tsv | cut -f1,3
```

### 3. Tous les symboles Kotlin qui viennent d'un header donné

```bash
awk -F'\t' '$4 ~ /^include\/core\/SkPath\.h/' .upstream/source/map/**/*.tsv
```

### 4. Tous les symboles Kotlin d'une source Kotlin

```bash
cat .upstream/source/map/math/SkPoint.tsv
```

### 5. Rollup multi-module

```bash
cat .upstream/source/map/**/*.tsv | wc -l            # total de symboles mappés
cut -f2 .upstream/source/map/**/*.tsv | sort -u      # tous les symboles C++ uniques
```

### 6. Symboles Kotlin partageant un même C++ (factories, overrides, …)

```bash
awk -F'\t' '{print $2}' .upstream/source/map/**/*.tsv | sort | uniq -d
```

### 7. URL clickable depuis une requête

```bash
awk -F'\t' '$2=="SkPoint::length" {print $4}' .upstream/source/map/**/*.tsv \
  | xargs -I{} .upstream/source/map/_resolve_url.sh {}
```

## URL upstream — pin de version

URL de base :

```
URL_BASE=https://github.com/google/skia/blob/main/
```

Aujourd'hui on suit `main`. Quand on pinnera un commit upstream, on remplacera `main` par le SHA ici et dans `_resolve_url.sh`. **Seule source de vérité — pas d'URL en dur dans les TSVs.**

## Backfill

Convention validée ; mise en conformité du contenu en PRs séparées, un module à la fois :

| PR | Cible |
|---|---|
| Map-1 (cette PR) | Spec + README + helpers shell |
| Map-2 | `math/` |
| Map-3 | `kanvas-skia/foundation/` |
| Map-4 | `kanvas-skia/core/` |
| Map-5 | `cpu-raster/pathops/` |
| Map-6 | `cpu-raster/codec/` + `effects/` + reste |
| Map-7 | `skia-integration-tests/` (~461 GMs, scriptable depuis `kanvas/src/generated/tests/`) |

## Vérification d'une PR de backfill

1. `./gradlew build` reste vert (aucun code Kotlin touché)
2. `./.upstream/source/map/audit.sh <module>` → la liste des symboles publics manquants doit être vide (ou ne contenir que des Kotlin-original)
3. Spot-check : exécuter les 6 requêtes types ci-dessus, vérifier qu'elles retournent du sens
4. `_resolve_url.sh` sur 3 lignes au hasard → l'URL ouvre sur le bon symbole upstream
