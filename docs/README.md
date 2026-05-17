# Documentation

Ce dossier est volontairement vide (la doc générée vit dans `<module>/build/dokka/`, gitignorée).

## Module `:math`

La doc API du module `:math` est générée par [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) en **deux formats** :

- **HTML** — site web complet (navigation latérale, recherche), publié sur GitHub Pages par CI
- **GFM** — GitHub-Flavored Markdown, accessible sous `/markdown/` sur le site et browsable directement dans le repo upstream

### Génération locale

```bash
# HTML (site)
./gradlew :math:dokkaHtml
open math/build/dokka/html/index.html

# GFM (markdown)
./gradlew :math:dokkaGfm
ls math/build/dokka/gfm/
```

Les deux formats se génèrent en ~5 s.

### Déploiement (GitHub Pages)

Le workflow [.github/workflows/docs.yml](../.github/workflows/docs.yml) déclenche automatiquement, à chaque push sur `master` qui touche `math/**`, la régénération + l'upload du site sur GitHub Pages.

URL : `https://ygdrasil-io.github.io/kanvas/` (sous-page `:math` à la racine).

**Setup repo (à faire une fois)** : dans Settings → Pages → Source = "GitHub Actions".

### Source du contenu

- KDocs des sources `math/src/main/kotlin/*.kt` — c'est la source primaire.
- [`math/module.md`](../math/module.md) — page d'overview du module (incluse via `includes.from(...)` dans `math/build.gradle.kts`).
- Liens vers les sources : Dokka injecte un `Sources →` qui pointe vers `https://github.com/ygdrasil-io/kanvas/blob/master/math/src/main/kotlin/<File>.kt#L<line>`.

### Configuration Dokka

Voir [`math/build.gradle.kts`](../math/build.gradle.kts).

- **Dokka 2.2.0** en **mode V1** (V2 ne supporte pas encore les publications GFM/Jekyll built-in).
- Plugin GFM scopé sur `dokkaGfm` uniquement (via la config `dokkaGfmPlugin`) — n'écrase pas le rendu HTML.
- Compat JDK 25 : Dokka 2.2.0 a embarqué un IntelliJ Platform à jour qui parse correctement la version JVM `25.0.1`.

### Roadmap

- [ ] Migrer en Dokka V2 quand GFM/Jekyll sera supporté nativement (V1 sera supprimé en Dokka 2.3+)
- [ ] Étendre la doc aux autres modules : `:kanvas-skia`, `:cpu-raster`, `:gpu-raster`
- [ ] Ajouter `externalDocumentationLink` vers `:kanvas-skia` pour résoudre les KDocs croisés (ex. `[SkShader]` dans `SkMatrix.invert`)
