# Documentation

Le site doc combine **Dokka** (qui produit du GFM markdown depuis les KDocs Kotlin) et **MkDocs Material** (qui rend ce markdown en site HTML).

## Layout

```
docs/
├── index.md         ← landing page (committée)
├── README.md        ← ce fichier
└── api/             ← API doc générée (gitignored)
    └── math/        ← :math via :math:dokkaGfm
mkdocs.yml           ← config MkDocs (committée)
_site/               ← sortie HTML finale (gitignored, déployée sur Pages)
```

## Génération locale

Prérequis : Python 3 + `pip install mkdocs-material`.

```bash
# 1. Génère le GFM via Dokka
./gradlew :math:dokkaGfm

# 2. Copie dans l'arbre docs/
mkdir -p docs/api/math
cp -r math/build/dokka/gfm/. docs/api/math/

# 3. Sert localement (http://localhost:8000)
mkdocs serve
```

Pour un build statique :

```bash
mkdocs build
open _site/index.html
```

## Déploiement (GitHub Pages)

[`.github/workflows/docs.yml`](../.github/workflows/docs.yml) déclenche automatiquement à chaque push sur `master` qui touche `math/**`, `docs/**`, `mkdocs.yml` ou le workflow lui-même.

URL : `https://ygdrasil-io.github.io/kanvas/`.

**Setup repo (à faire une fois)** : Settings → Pages → Source = "GitHub Actions".

## Configuration Dokka

Voir [`math/build.gradle.kts`](../math/build.gradle.kts).

- **Dokka 2.2.0** en **mode V1** (V2 ne supporte pas encore GFM/Jekyll built-in).
- Seul `dokkaGfm` est configuré — `dokkaHtml` reste exécutable mais n'est plus utilisé : le rendu HTML est fait par MkDocs Material.
- Compat JDK 25 : Dokka 2.2.0 embarque un IntelliJ Platform à jour.
- Source links injectés : chaque symbole de la doc renvoie au .kt sur master avec ancre `#Lxx`.

## Configuration MkDocs

Voir [`mkdocs.yml`](../mkdocs.yml).

- Thème **Material** (light + dark, navigation expansible, search)
- Extensions : `admonition`, `tables`, `pymdownx.details`, `pymdownx.superfences`, `pymdownx.tabbed`, `toc` avec permalinks
- `docs_dir: docs` / `site_dir: _site`

## Roadmap

- [ ] Étendre la doc API aux modules `:kanvas-skia`, `:cpu-raster`, `:gpu-raster` (chaque module ajoute son `:dokkaGfm` + un `docs/api/<module>/` correspondant)
- [ ] `externalDocumentationLink` Dokka pour résoudre les KDocs croisés inter-modules (ex. `[SkShader]` dans `SkMatrix.invert` — warning bénin actuel)
- [ ] Migrer en Dokka V2 quand GFM natif débarque (V1 sera removed en 2.3+)
- [ ] Plugin `mkdocs-awesome-nav` si la nav auto-générée devient trop touffue
