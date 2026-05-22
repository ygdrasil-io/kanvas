#!/usr/bin/env python3
"""
Audit la map de correspondance Kotlin ↔ Skia pour un module donné.

Compare l'ensemble des symboles publics Kotlin (depuis les sources
`<module>/src/{main,test}/kotlin/**/*.kt`) à la colonne 1 (kotlin FQN)
des TSVs dans `.upstream/source/map/<module>/`.

Reporte :
- MISSING — symboles publics Kotlin sans entrée TSV (à backfill)
- STALE   — entrées TSV sans symbole Kotlin correspondant (à supprimer ou ré-aligner)

Usage :
    audit.sh <module>
    audit.sh math
    audit.sh kanvas-skia/foundation

Le script utilise une heuristique regex (pas de parser AST complet) avec
prise en charge de :
- scope tracking (class, object, interface, companion object) — anonyme
  ou nommé
- function bodies (les val/var locaux à l'intérieur sont ignorés)
- primary-ctor val/var (rattachés à la classe)
- noms de tests entre backticks (`my test` → my test)

Limitations connues — faux positifs/négatifs possibles sur :
- déclarations sur une ligne (`data class X(val a: Int, val b: Int)`)
- expression bodies avec lambda braces (`fun f() = { ... }`)
- enum entries (besoin d'une syntaxe dédiée)

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
    rf"(?P<name>[A-Za-z_][A-Za-z0-9_]*|`[^`]+`)"
)
# Anonymous companion object: `public companion object {` or `companion object Foo {`
_COMPANION_RE = re.compile(
    rf"^\s*(?:(?:public)\s+)?companion\s+object\b(?:\s+(?P<name>[A-Za-z_]\w*))?"
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
    # Preserve line count so raw lineno aligns with the stripped buffer.
    def _keep_newlines(m: re.Match[str]) -> str:
        return "\n" * m.group(0).count("\n")

    code = _BLOCK_COMMENT.sub(_keep_newlines, code)
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
    # Scope stack of (class_name, brace_depth_at_entry).
    # `brace_depth_at_entry` is the depth *outside* the scope — when depth
    # drops back to this value the scope is popped.
    scope: list[tuple[str, int]] = []
    # Stack of brace depths that correspond to function bodies (or init/getter/setter
    # blocks). Decls inside these are local vars, not class members — skip them.
    fun_body_depths: list[int] = []
    depth = 0
    # Pending scope name to push when the next '{' is seen (handles multi-line
    # class declarations like `class A(... ) {` where '{' lands on a later line).
    pending_scope: str | None = None
    # Flag: the next '{' enters a function body (for skipping local-var decls).
    pending_fun_body = False
    # Tracks paren depth (open `(` minus close `)`) so we can treat `val`/`var`
    # decls inside a class primary constructor's `(...)` block as class members,
    # not top-level locals.
    paren_depth = 0
    # When > 0, we're inside a class header's primary constructor parens —
    # decls here use the *pending_scope* class as their parent.
    in_primary_ctor = False
    primary_ctor_scope: str | None = None

    stripped_lines = stripped.splitlines()
    raw_lines = raw.splitlines()
    for lineno, raw_line in enumerate(raw_lines, start=1):
        line = stripped_lines[lineno - 1] if lineno - 1 < len(stripped_lines) else ""

        # Inside a function body? Local vals/vars don't count as public API.
        in_fun_body = bool(fun_body_depths)

        # Companion object detection first (the main regex requires a name
        # which fails on anonymous `companion object {`).
        cm = _COMPANION_RE.match(line)
        if cm and not in_fun_body:
            pending_scope = "Companion"
        else:
            m = _DECL_RE.match(line)
            if m and not in_fun_body:
                name = m.group("name")
                # Strip backticks from quoted test names: `my test` -> my test
                if name.startswith("`") and name.endswith("`"):
                    name = name[1:-1]
                kind = m.group("kind")
                is_public = _is_public(m.group("mods"))
                # Effective scope: if we're inside a primary-ctor `(...)`,
                # decls (val/var) belong to the pending class.
                effective_scope = scope
                if in_primary_ctor and primary_ctor_scope and kind in {"val", "var"}:
                    effective_scope = scope + [(primary_ctor_scope, depth)]
                # Emit only public symbols, but always track scope/body — non-public
                # funs still introduce local-var scopes that should be skipped.
                if is_public:
                    scope_path = ".".join(s[0] for s in effective_scope)
                    if scope_path:
                        fqn = f"{pkg}.{scope_path}.{name}" if pkg else f"{scope_path}.{name}"
                    else:
                        fqn = f"{pkg}.{name}" if pkg else name
                    out.append(Symbol(fqn=fqn, file=kt_file.relative_to(repo_root), line=lineno))
                if is_public and kind in {"class", "object", "interface"}:
                    pending_scope = name
                    primary_ctor_scope = name
                elif kind == "fun":
                    # Function bodies — if a '{' follows, the body opens.
                    # Expression-body funs (`fun f(): T = expr`) have no body
                    # block. Heuristic: scan the line for the *first* `)` at
                    # paren-depth 0 (closes the param list) and check whether
                    # any `=` follows it on the same line. Default-value `=`
                    # inside params (e.g. `fun f(x: Int = 0)`) never gets
                    # picked up because it sits inside parens.
                    pending_fun_body = True
                    if "{" not in line:
                        pd = 0
                        for i, ch in enumerate(line):
                            if ch == "(":
                                pd += 1
                            elif ch == ")":
                                pd -= 1
                                if pd == 0:
                                    if "=" in line[i + 1 :]:
                                        pending_fun_body = False
                                    break

        # Track paren depth for primary-constructor detection.
        opens_paren = line.count("(")
        closes_paren = line.count(")")
        new_paren_depth = paren_depth + opens_paren - closes_paren
        # Entering primary-ctor parens: pending_scope was just set to a class
        # and `(` opens before `{`.
        if pending_scope is not None and primary_ctor_scope == pending_scope:
            if new_paren_depth > 0 and "{" not in line:
                in_primary_ctor = True
        # Exit primary-ctor when parens close back to 0.
        if in_primary_ctor and new_paren_depth == 0:
            in_primary_ctor = False
            primary_ctor_scope = None
        paren_depth = new_paren_depth

        # Process braces *after* matching, so a single-line `fun a() { ... }`
        # doesn't corrupt the scope stack. Use net delta: only pop when
        # cumulative depth dips below a scope's recorded entry depth.
        opens = line.count("{")
        closes = line.count("}")
        new_depth = depth + opens - closes
        # If '{' appears on this line and a scope was pending, push it now —
        # entry depth is the depth *outside* the scope, i.e. `depth + (1 brace consumed by the scope opening)`.
        # We approximate: push on first '{' seen with the current outer depth.
        if pending_scope is not None and opens > 0:
            scope.append((pending_scope, depth))
            pending_scope = None
        if pending_fun_body and opens > 0:
            fun_body_depths.append(depth)
            pending_fun_body = False
        # else: multi-line function header (`fun foo(\n  a: T,\n) {`) — keep
        # the flag set until a '{' is seen on a later line.
        # Pop scopes whose entry depth >= new_depth (we've closed past them).
        while scope and scope[-1][1] >= new_depth:
            scope.pop()
        while fun_body_depths and fun_body_depths[-1] >= new_depth:
            fun_body_depths.pop()
        depth = new_depth

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

    # Source dirs: <module>/src/main/kotlin AND <module>/src/test/kotlin.
    # Tests are mapped in their own *Test.tsv files; including both keeps the
    # "stale" set focused on genuinely orphaned entries.
    module_root = repo_root / module.split("/")[0]
    src_main = module_root / "src" / "main" / "kotlin"
    src_test = module_root / "src" / "test" / "kotlin"
    if not src_main.is_dir() and not src_test.is_dir():
        print(f"error: no src dir at {src_main} or {src_test}", file=sys.stderr)
        return 2

    # For sub-paths like "kanvas-skia/foundation", further filter Kotlin files
    sub = "/".join(module.split("/")[1:])
    kt_files: list[Path] = []
    for root in (src_main, src_test):
        if root.is_dir():
            kt_files.extend(root.rglob("*.kt"))
    kt_files.sort()
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
