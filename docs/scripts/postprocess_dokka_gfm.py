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
        n_processed += 1
        if cleaned != original:
            md.write_text(cleaned, encoding="utf-8")
            n_changed += 1

    print(f"Processed {n_processed} files, modified {n_changed}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
