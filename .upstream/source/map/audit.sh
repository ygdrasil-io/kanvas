#!/usr/bin/env python3
"""
Audit la map de correspondance Kotlin ↔ Skia pour un module donné.

Compare l'ensemble des symboles publics Kotlin (depuis les sources
`<module>/src/main/kotlin/**/*.kt`) à la colonne 1 (kotlin FQN) des
TSVs dans `.upstream/source/map/<module>/`.

Reporte :
- MISSING — symboles publics Kotlin sans entrée TSV (à backfill)
- STALE   — entrées TSV sans symbole Kotlin correspondant (à supprimer ou ré-aligner)

Usage :
    audit.sh <module>
    audit.sh math
    audit.sh kanvas-skia/foundation

Le script utilise une heuristique regex (pas de parser AST complet). Faux
positifs possibles sur :
- déclarations multi-lignes complexes
- classes imbriquées au-delà de 3 niveaux

Sortie : code 0 si MISSING vide, sinon 1.
"""
from __future__ import annotations

import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path


# --- Kotlin symbol extraction -------------------------------------------------

# Modifiers we strip from the start of a decl line. `public` is implicit so it's
# in the list but a missing modifier still means public.
_VISIBILITY_NON_PUBLIC = {"private", "internal", "protected"}
_MODIFIERS = (
    r"public|private|internal|protected|"
    r"open|abstract|final|sealed|data|inline|"
    r"operator|infix|tailrec|suspend|external|expect|actual|"
    r"override|companion|enum|annotation|inner|lateinit|const"
)
_DECL_RE = re.compile(
    rf"^(?P<indent>\s*)"
    rf"(?P<mods>(?:(?:{_MODIFIERS})\s+)*)"
    rf"(?P<kind>fun|val|var|class|object|interface|typealias)\b"
    rf"(?:\s*<[^>]+>)?\s+"
    rf"(?:\w+\s*\.\s*)?"  # receiver type for extension fns
    rf"(?P<name>[A-Za-z_][A-Za-z0-9_]*)"
)
_PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)")
_BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)
_LINE_COMMENT = re.compile(r"//[^\n]*")
_STRING_LITERAL = re.compile(r'"(?:[^"\\]|\\.)*"')


@dataclass
class Symbol:
    fqn: str
    file: Path
    line: int


def _strip(code: str) -> str:
    code = _BLOCK_COMMENT.sub("", code)
    code = _LINE_COMMENT.sub("", code)
    code = _STRING_LITERAL.sub('""', code)
    return code


def _is_public(mods_str: str) -> bool:
    parts = mods_str.split()
    return not any(m in _VISIBILITY_NON_PUBLIC for m in parts)


def extract_symbols(kt_file: Path, repo_root: Path) -> list[Symbol]:
    """Best-effort heuristic — public top-level + class members."""
    raw = kt_file.read_text(encoding="utf-8")
    stripped = _strip(raw)

    pkg = ""
    pkg_m = _PACKAGE_RE.search(stripped)
    if pkg_m:
        pkg = pkg_m.group(1)

    out: list[Symbol] = []
    # Scope stack of (class_name, brace_depth_at_entry)
    scope: list[tuple[str, int]] = []
    depth = 0

    raw_lines = raw.splitlines()
    for lineno, raw_line in enumerate(raw_lines, start=1):
        # Use stripped line for parsing, but keep raw lineno
        line = stripped.splitlines()[lineno - 1] if lineno - 1 < len(stripped.splitlines()) else ""

        # Pop scopes whose closing brace we've passed
        opens = line.count("{")
        closes = line.count("}")
        # Process closes BEFORE matching this line's decl (a `}` ends the parent scope)
        for _ in range(closes):
            depth -= 1
            while scope and scope[-1][1] >= depth + 1:
                scope.pop()

        m = _DECL_RE.match(line)
        if m and _is_public(m.group("mods")):
            name = m.group("name")
            kind = m.group("kind")
            # Build FQN
            scope_path = ".".join(s[0] for s in scope)
            if scope_path:
                fqn = f"{pkg}.{scope_path}.{name}" if pkg else f"{scope_path}.{name}"
            else:
                fqn = f"{pkg}.{name}" if pkg else name
            out.append(Symbol(fqn=fqn, file=kt_file.relative_to(repo_root), line=lineno))
            # If it's a class/object/interface, push to scope (it opens a brace later on same/next line)
            if kind in {"class", "object", "interface"}:
                # Push with depth+1 — we'll close it when we go back to current depth
                scope.append((name, depth + 1))

        depth += opens

    return out


# --- TSV parsing --------------------------------------------------------------

def load_tsv_kotlin_fqns(tsv_dir: Path) -> set[str]:
    out: set[str] = set()
    if not tsv_dir.is_dir():
        return out
    for tsv in tsv_dir.rglob("*.tsv"):
        for line in tsv.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            parts = line.split("\t")
            if len(parts) >= 1 and parts[0]:
                out.add(parts[0])
    return out


# --- Main ---------------------------------------------------------------------

def find_repo_root(start: Path) -> Path:
    cur = start.resolve()
    while cur != cur.parent:
        if (cur / ".git").exists() or (cur / "settings.gradle.kts").exists():
            return cur
        cur = cur.parent
    return start.resolve()


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <module>", file=sys.stderr)
        print(f"  e.g. {argv[0]} math", file=sys.stderr)
        print(f"  e.g. {argv[0]} kanvas-skia/foundation", file=sys.stderr)
        return 2

    module = argv[1].rstrip("/")
    script_dir = Path(__file__).resolve().parent
    repo_root = find_repo_root(script_dir)

    # Source dir: <module>/src/main/kotlin
    src_dir = repo_root / module.split("/")[0] / "src" / "main" / "kotlin"
    if not src_dir.is_dir():
        print(f"error: no src dir at {src_dir}", file=sys.stderr)
        return 2

    # For sub-paths like "kanvas-skia/foundation", further filter Kotlin files
    sub = "/".join(module.split("/")[1:])
    kt_files = sorted(src_dir.rglob("*.kt"))
    if sub:
        kt_files = [f for f in kt_files if f"/{sub}/" in str(f) or str(f).endswith(f"/{sub}")]

    # Extract Kotlin symbols
    kotlin_symbols: dict[str, Symbol] = {}
    for kt in kt_files:
        for sym in extract_symbols(kt, repo_root):
            kotlin_symbols[sym.fqn] = sym

    # Load TSV FQNs for this module
    tsv_dir = script_dir / module
    tsv_fqns = load_tsv_kotlin_fqns(tsv_dir)

    kotlin_set = set(kotlin_symbols.keys())
    missing = sorted(kotlin_set - tsv_fqns)
    stale = sorted(tsv_fqns - kotlin_set)

    print(f"Module:        {module}")
    print(f"Kotlin sources scanned:  {len(kt_files)}")
    print(f"Public symbols (heuristic): {len(kotlin_set)}")
    print(f"TSV entries:    {len(tsv_fqns)}")
    print()

    if missing:
        print(f"MISSING — {len(missing)} symbole(s) Kotlin sans entrée TSV :")
        for fqn in missing:
            s = kotlin_symbols[fqn]
            print(f"  {s.file}:{s.line}\t{fqn}")
        print()

    if stale:
        print(f"STALE — {len(stale)} entrée(s) TSV sans symbole Kotlin :")
        for fqn in stale:
            print(f"  {fqn}")
        print()

    if not missing and not stale:
        print("✓ Map complète et à jour.")
        return 0

    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
