#!/usr/bin/env python3
r"""
Post-traitement de la sortie GFM de Dokka pour la rendre amicale à MkDocs.

Dokka 2.2.0 (V1) GFM utilise plusieurs patterns non-standard qui rendent mal
quand consommés par un parseur markdown générique :

1. Ligne breadcrumb `//[...]/[.../]...` en haut de chaque page — pas du markdown.
2. Tag plateforme `[jvm]\` ou `[jvm]<br>` — visible comme texte brut.
3. Signatures de fonctions / classes sous forme `fun [name](url)(arg: [Type](url))`
   — markdown avec liens incrustés, pas de monospace, pas de coloration.
4. Listes mal indentées `- \n   item` — espaces parasites.
5. Liens internes pointant vers le `.md` (extension à supprimer pour MkDocs).

Usage :
    ./postprocess_dokka_gfm.py docs/api/math

Idempotent — relancer ne fait rien de plus.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

# -- patterns --

# Breadcrumb : `//[name](url)/[name](url)/...` sur la première ligne
RE_BREADCRUMB = re.compile(r"^//(?:\[[^\]]+\]\([^)]+\)/?)+\s*$", re.MULTILINE)

# Tag plateforme : `[jvm]\` (suivi de \n) ou `[jvm]<br>`
RE_PLATFORM_LINE = re.compile(r"^\[jvm\]\\?\s*$", re.MULTILINE)
RE_PLATFORM_BR = re.compile(r"\[jvm\]<br>")
RE_PLATFORM_INLINE = re.compile(r"\[jvm\]\\?")

# Liste avec espaces parasites : `- \n   content` → `- content`
RE_LIST_INDENT = re.compile(r"^(- )\s*\n\s{3,}(\S)", re.MULTILINE)

# Lignes "signature" qui commencent par mots-clés Kotlin et ne sont pas DÉJÀ
# dans un code block. On les emballe.
SIG_KEYWORDS = r"(?:data class|sealed class|enum class|abstract class|open class|inner class|annotation class|interface|object|class|fun|val|var|const val|operator fun|infix fun|inline fun|suspend fun|tailrec fun|external fun|abstract fun|open fun|override fun|typealias|constructor)"
RE_SIGNATURE_LINE = re.compile(rf"^(?P<sig>(?:{SIG_KEYWORDS})\s+\[[^\]]+\]\([^)]+\).*)$", re.MULTILINE)


def strip_md_links(text: str) -> str:
    """`[Float](url)` → `Float` — pour les signatures en code block."""
    return re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)


def wrap_signatures_in_code(content: str) -> str:
    """
    Emballe les blocs consécutifs de lignes-signatures dans un ```kotlin … ```.
    On détecte les lignes qui commencent par un mot-clé Kotlin + un nom-lien.
    """
    lines = content.splitlines(keepends=False)
    out: list[str] = []
    in_codefence = False
    i = 0
    while i < len(lines):
        line = lines[i]

        # Suivre les code fences existants pour ne pas les emballer deux fois.
        if line.lstrip().startswith("```"):
            in_codefence = not in_codefence
            out.append(line)
            i += 1
            continue

        if not in_codefence and RE_SIGNATURE_LINE.match(line):
            # Collecter le bloc contigu de signatures + lignes-vides
            block: list[str] = [line]
            j = i + 1
            while j < len(lines):
                nxt = lines[j]
                if RE_SIGNATURE_LINE.match(nxt) or nxt.strip() == "":
                    block.append(nxt)
                    j += 1
                else:
                    break
            # Retire les lignes vides terminales
            while block and block[-1].strip() == "":
                block.pop()
            # Strip les liens markdown des signatures (le code block n'en a pas besoin)
            cleaned = [strip_md_links(b) for b in block]
            out.append("```kotlin")
            out.extend(cleaned)
            out.append("```")
            # On a sauté `j - i` lignes mais on a écrit les block-len + 2 fences
            i = j
            continue

        out.append(line)
        i += 1
    return "\n".join(out) + ("\n" if content.endswith("\n") else "")


def process(text: str) -> str:
    # 1. Supprime le breadcrumb (MkDocs Material affiche déjà le chemin via la nav)
    text = RE_BREADCRUMB.sub("", text)
    # 2. Supprime les markers `[jvm]` sous toutes leurs formes
    text = RE_PLATFORM_LINE.sub("", text)
    text = RE_PLATFORM_BR.sub("", text)
    text = RE_PLATFORM_INLINE.sub("", text)
    # 3. Compacte les listes mal indentées
    text = RE_LIST_INDENT.sub(r"\1\2", text)
    # 4. Emballe les signatures dans des ```kotlin code blocks
    text = wrap_signatures_in_code(text)
    # 5. Collapse les triples newlines (artefacts du nettoyage)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


# -- Classification par famille pour le grouping nav (awesome-pages) ---------

# Pour le package `org.graphiks.math`. Chaque famille déclare :
#  - `classes`  : noms exacts des sous-dirs Dokka (classes/objets/enums)
#  - `prefixes` : préfixes de noms de fichiers top-level (fns + constantes)
#  - `files`    : noms exacts de fichiers top-level non-couverts par les préfixes
# Ordre dans le dict = ordre dans la nav.
FAMILIES: dict[str, dict[str, list[str]]] = {
    "Geometry": {
        "classes": [
            "-sk-point", "-sk-i-point", "-sk-vector", "-sk-i-vector",
            "-sk-point3", "-sk-rect", "-sk-i-rect", "-sk-size", "-sk-i-size",
        ],
        "prefixes": [],
        "files": [],
    },
    "Matrix": {
        "classes": ["-sk-matrix", "-sk-m44"],
        "prefixes": [],
        "files": [],
    },
    "Vector N-D": {
        "classes": ["-sk-v2", "-sk-v3", "-sk-v4"],
        "prefixes": [],
        "files": ["times.md"],  # Float.times(SkV2/3/4) extension operators
    },
    "Color": {
        "classes": [
            "-sk-color", "-sk-alpha", "-sk-color4f", "-sk-p-m-color",
            "-sk-color-matrix", "-sk-color-channel", "-sk-color-channel-flag",
        ],
        "prefixes": [
            "-sk-color-set-", "-sk-color-get-",
            "-sk-color-to-h-s-v", "-sk-h-s-v-to-color", "-sk-r-g-b-to-h-s-v",
            "-sk-pre-multiply-",
            "-s-k_-alpha-",       # SK_AlphaOPAQUE / SK_AlphaTRANSPARENT
            "-s-k_-color-",       # SK_ColorBLACK / etc.
        ],
        "files": ["color-to-r-g-b565.md"],
    },
    "Scalar": {
        "classes": ["-sk-scalar"],
        "prefixes": [
            "-sk-scalar-",
            "-s-k_-scalar",        # SK_Scalar1 / SK_ScalarPI / etc.
            "-sk-degrees-to-radians", "-sk-radians-to-degrees",
            "-sk-double-to-scalar", "-sk-float-to-scalar", "-sk-int-to-",
            "-sk-scalars-equal",
        ],
        "files": [],
    },
    "Pathops (double-precision)": {
        "classes": ["-sk-d-point", "-sk-d-vector", "-sk-d-line"],
        "prefixes": [
            "-almost-", "-not-almost-",
            "-roughly-equal-ulps", "-ulps-distance",
            "approximately_", "precisely_", "roughly_",
            "-b-u-m-p_-e-p-s-i-l-o-n",
            "-d-b-l_-e-p-s-i-l-o-n",
            "-f-l-t_-e-p-s-i-l-o-n",
            "-m-o-r-e_-r-o-u-g-h_-e-p-s-i-l-o-n",
            "-r-o-u-g-h_-e-p-s-i-l-o-n",
            "-w-a-y_-r-o-u-g-h_-e-p-s-i-l-o-n",
            "-sk-d-interp", "-sk-d-side", "-sk-d-sign", "-sk-pin-t",
        ],
        "files": ["between.md", "more_roughly_equal.md", "zero_or_one.md"],
    },
    "Skcms (color management)": {
        "classes": ["-skcms-transfer-function", "-skcms-matrix3x3", "-skcms-matrix3x4"],
        "prefixes": [],
        "files": [],
    },
}


def classify(name: str, is_dir: bool) -> str | None:
    """Renvoie le nom de la famille de `name`, ou None si non classifié."""
    for fam, rules in FAMILIES.items():
        if is_dir:
            if name in rules["classes"]:
                return fam
        else:
            if name in rules["files"]:
                return fam
            for prefix in rules["prefixes"]:
                if name.startswith(prefix):
                    return fam
    return None


def generate_package_pages(pkg_dir: Path) -> str:
    """Construit le YAML `.pages` pour le package root avec grouping par famille."""
    entries: list[tuple[str, bool]] = []
    for p in sorted(pkg_dir.iterdir()):
        if p.name in {"index.md", "package-list", ".pages"}:
            continue
        if p.is_dir():
            entries.append((p.name, True))
        elif p.suffix == ".md":
            entries.append((p.name, False))

    grouped: dict[str, list[tuple[str, bool]]] = {fam: [] for fam in FAMILIES}
    other: list[tuple[str, bool]] = []
    for name, is_dir in entries:
        fam = classify(name, is_dir)
        if fam is None:
            other.append((name, is_dir))
        else:
            grouped[fam].append((name, is_dir))

    def sort_key(t: tuple[str, bool]) -> tuple[int, str]:
        # Classes (dirs) avant les helpers/constantes (fichiers), puis tri alpha
        # — mais on insère un mini-tri sur les constantes pour qu'elles soient
        # en fin de famille (préfixe `-s-k_` chez Dokka).
        name, is_dir = t
        if is_dir:
            return (0, name)
        if name.startswith("-s-k_"):
            return (2, name)  # constantes SK_X tout à la fin
        return (1, name)

    lines = ["nav:", "  - index.md"]
    for fam, items in grouped.items():
        if not items:
            continue
        lines.append(f'  - "{fam}":')
        for name, _ in sorted(items, key=sort_key):
            lines.append(f"    - {name}")
    if other:
        lines.append('  - "Other":')
        for name, _ in sorted(other, key=sort_key):
            lines.append(f"    - {name}")
    return "\n".join(lines) + "\n"


# Un nom Kotlin de package qualifié : `org.graphiks.math`, `kotlin.collections`...
# (par opposition aux noms Dokka URL-encoded comme `-sk-point`, `-companion`).
RE_KOTLIN_PACKAGE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")


RE_H1 = re.compile(r"^#\s+(.+?)\s*$", re.MULTILINE)


def extract_h1(index_md: Path) -> str | None:
    """Renvoie le premier H1 d'un index.md, ou None si introuvable."""
    if not index_md.is_file():
        return None
    m = RE_H1.search(index_md.read_text(encoding="utf-8"))
    return m.group(1) if m else None


def write_pages_files(api_root: Path) -> int:
    """Écrit les `.pages` aux niveaux pertinents. Renvoie le nombre de fichiers."""
    n = 0

    # Racine API : titre simple.
    (api_root / ".pages").write_text("title: API\n", encoding="utf-8")
    n += 1

    # Niveau module (`docs/api/<module>/`) : titre = `:<module>` (convention Gradle).
    for module_dir in sorted(api_root.iterdir()):
        if not module_dir.is_dir():
            continue
        (module_dir / ".pages").write_text(f"title: ':{module_dir.name}'\n", encoding="utf-8")
        n += 1

    # Package roots : tout dir dont le nom matche un nom de package Kotlin
    # (`org.graphiks.math`, etc.). Y emballe `.pages` avec le grouping par
    # famille + titre `package`.
    for pkg_dir in api_root.rglob("*"):
        if not pkg_dir.is_dir():
            continue
        if not RE_KOTLIN_PACKAGE.match(pkg_dir.name):
            continue
        body = f"title: {pkg_dir.name}\n" + generate_package_pages(pkg_dir)
        (pkg_dir / ".pages").write_text(body, encoding="utf-8")
        n += 1

        # Pour chaque classe sous le package : extrait l'H1 de son index.md et
        # nomme la section avec ce nom (sinon MkDocs affiche le nom URL-encoded
        # tel quel : "sk i point" au lieu de "SkIPoint").
        for class_dir in pkg_dir.iterdir():
            if not class_dir.is_dir() or not class_dir.name.startswith("-"):
                continue
            title = extract_h1(class_dir / "index.md")
            if not title:
                continue
            (class_dir / ".pages").write_text(f"title: {title}\n", encoding="utf-8")
            n += 1
            # Récursif aux companion / sous-classes
            for sub in class_dir.rglob("index.md"):
                sub_dir = sub.parent
                if sub_dir == class_dir:
                    continue
                if not sub_dir.name.startswith("-"):
                    continue
                sub_title = extract_h1(sub)
                if sub_title:
                    (sub_dir / ".pages").write_text(f"title: {sub_title}\n", encoding="utf-8")
                    n += 1
    return n


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <dir>", file=sys.stderr)
        return 2
    root = Path(argv[1])
    if not root.is_dir():
        print(f"error: not a directory: {root}", file=sys.stderr)
        return 2

    n_processed = 0
    n_changed = 0
    for md in root.rglob("*.md"):
        original = md.read_text(encoding="utf-8")
        cleaned = process(original)

        # Patch h1 des fichiers "constructeur" : Dokka génère par classe un
        # `-<class>.md` au stub `# <Class>\nconstructor(...)`. Le h1 duplique
        # le nom de classe et apparaît deux fois dans la nav (la classe + sa
        # sous-section constructor portent le même label). On renomme.
        if md.stem == md.parent.name and md.parent.name.startswith("-"):
            cleaned = re.sub(r"^#\s+.+$", "# constructor", cleaned, count=1, flags=re.MULTILINE)

        n_processed += 1
        if cleaned != original:
            md.write_text(cleaned, encoding="utf-8")
            n_changed += 1

    print(f"Processed {n_processed} files, modified {n_changed}.")

    # Génère les `.pages` files pour awesome-pages — root API si on est appelé
    # sur le rep API ; sinon, sur le rep passé.
    # Heuristique : on prend le parent commun des `index.md` qui ressemblent à
    # une racine API. Plus simple : si l'arg contient `api/`, prendre l'ancêtre
    # qui se nomme `api`. Sinon utiliser `root`.
    api_root = root
    for ancestor in [root, *root.parents]:
        if ancestor.name == "api":
            api_root = ancestor
            break
    n_pages = write_pages_files(api_root)
    print(f"Wrote {n_pages} .pages files under {api_root}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
