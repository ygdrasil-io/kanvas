#!/usr/bin/env python3
"""
Audit la map de correspondance Kotlin ↔ Skia pour un module donné.

Compare l'ensemble des symboles publics Kotlin (depuis les sources
`<module>/src/{commonMain,commonTest,main,test}/kotlin/**/*.kt`) à la colonne 1 (kotlin FQN)
des TSVs dans `.upstream/source/map/<module>/`.

Reporte :
- MISSING  — symboles publics Kotlin sans entrée TSV (à backfill)
- STALE    — entrées TSV sans symbole Kotlin correspondant (à supprimer ou ré-aligner)
- EXCLUDED — symboles ignorés (Kotlin-idiom + whitelist `_ignore.txt`)

Exclusions automatiques (Kotlin-idiom sans contrepartie upstream) :
- `override fun equals(other: Any?): Boolean`
- `override fun hashCode(): Int`
- `override fun toString(): String`

Exclusions explicites :
- Fichier `<.upstream/source/map>/<module>/_ignore.txt` — une FQN Kotlin par
  ligne. Lignes vides et `# commentaires` autorisées. Sert pour les
  symboles Kotlin-original (constantes de pratique, factory methods sans
  Skia counterpart, tests qui n'ont pas d'analogue C++ direct, …).

Usage :
    audit.sh <module>
    audit.sh math
    audit.sh kanvas-skia/foundation

Le script utilise une heuristique regex (pas de parser AST complet) avec
prise en charge de :
- scope tracking (class, object, interface, companion object) — anonyme
  ou nommé
- function bodies (les val/var locaux à l'intérieur sont ignorés)
- primary-ctor val/var (rattachés à la classe), aussi bien en
  multi-ligne (`class X(\n  val a: Int,\n) {`) qu'en single-line
  (`data class X(val a: Int, val b: Int)`)
- enum entries (`enum class X { kA, kB }` single ou multi-ligne)
- noms de tests entre backticks (`my test` → my test)

Limitations connues — faux positifs/négatifs possibles sur :
- expression bodies avec lambda braces (`fun f() = { ... }`)
- enum entries après un `;` (méthodes membres d'enum)

Sortie : code 0 si MISSING et STALE vides, sinon 1. STALE et MISSING
sont tous deux lint-enforcing depuis M6.
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
# Primary-constructor `val`/`var` parameters, scanned anywhere inside the
# class header's `(...)`. Matches `(public )?(val|var)\s+<name>`. Visibility
# modifiers other than `public` (or missing) make the param non-public.
_CTOR_PARAM_RE = re.compile(
    r"(?:(?P<vis>public|private|internal|protected)\s+)?"
    r"(?P<kind>val|var)\s+(?P<name>[A-Za-z_]\w*)"
)
# Enum entry — a bare identifier (optionally followed by `(...)` for entries
# with constructor args or `{ ... }` for entries with bodies). Matched only
# inside an `enum class` scope, before the body's `;` separator (which marks
# the end of the entry list and the start of regular member declarations).
_ENUM_ENTRY_RE = re.compile(
    r"^\s*(?P<name>[A-Za-z_]\w*)\s*(?:[(,;{]|$)"
)
_BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)
_LINE_COMMENT = re.compile(r"//[^\n]*")
_STRING_LITERAL = re.compile(r'"(?:[^"\\]|\\.)*"')


@dataclass
class Symbol:
    fqn: str
    file: Path
    line: int
    # Why this symbol is auto-excluded (None if it should be audited).
    excluded_reason: str | None = None


# Names whose `override fun <name>(): T` is always a Kotlin/Java idiom override
# (Any/Object methods) and never maps to a Skia upstream symbol.
_OVERRIDE_OBJECT_METHODS = {"equals", "hashCode", "toString"}


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
    seen_fqns: set[str] = set()

    def _emit(fqn: str, line_no: int, reason: str | None = None) -> None:
        if fqn in seen_fqns:
            return
        seen_fqns.add(fqn)
        out.append(
            Symbol(
                fqn=fqn,
                file=kt_file.relative_to(repo_root),
                line=line_no,
                excluded_reason=reason,
            )
        )

    # Scope stack of (class_name, brace_depth_at_entry, is_enum, enum_entries_done).
    # `brace_depth_at_entry` is the depth *outside* the scope — when depth
    # drops back to this value the scope is popped.
    # `is_enum` flags `enum class` scopes so we extract entries from their body.
    # `enum_entries_done` flips to True once a `;` is seen, signalling the
    # transition from entry list to regular member declarations.
    scope: list[list] = []  # each item: [name, entry_depth, is_enum, entries_done]
    # Stack of brace depths that correspond to function bodies (or init/getter/setter
    # blocks). Decls inside these are local vars, not class members — skip them.
    fun_body_depths: list[int] = []
    depth = 0
    # Pending scope name to push when the next '{' is seen (handles multi-line
    # class declarations like `class A(... ) {` where '{' lands on a later line).
    pending_scope: str | None = None
    pending_scope_is_enum = False
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

        # Enum entries — only inside an `enum class` body, before any `;`.
        # Entries are bare identifiers (optionally followed by `(...)` for
        # entries with ctor args or `{ ... }` for entries with bodies),
        # separated by commas. Lines containing `;` end the entry section.
        in_enum_body = (
            bool(scope)
            and scope[-1][2]              # is_enum
            and not scope[-1][3]          # entries_done not yet seen
            and not in_fun_body
            and not in_primary_ctor
            and depth == scope[-1][1] + 1  # we're directly inside the enum's body
        )
        if in_enum_body:
            # Skip lines that look like a member decl (the `_DECL_RE` path
            # below handles those, e.g. enum entries with member methods after
            # `;`). Otherwise scan for bare-identifier entries.
            stripped_line = line.strip()
            if stripped_line and not _DECL_RE.match(line) and not _COMPANION_RE.match(line):
                em = _ENUM_ENTRY_RE.match(line)
                if em:
                    entry_name = em.group("name")
                    # Exclude Kotlin keywords that could match (companion, object…)
                    if entry_name not in {
                        "companion", "object", "init", "constructor",
                        "private", "public", "internal", "protected",
                    }:
                        scope_path = ".".join(s[0] for s in scope)
                        fqn = f"{pkg}.{scope_path}.{entry_name}" if pkg else f"{scope_path}.{entry_name}"
                        _emit(fqn, lineno)
            if ";" in line:
                scope[-1][3] = True  # entries_done

        # Companion object detection first (the main regex requires a name
        # which fails on anonymous `companion object {`).
        cm = _COMPANION_RE.match(line)
        if cm and not in_fun_body:
            pending_scope = "Companion"
            pending_scope_is_enum = False
        else:
            m = _DECL_RE.match(line)
            if m and not in_fun_body:
                name = m.group("name")
                # Strip backticks from quoted test names: `my test` -> my test
                if name.startswith("`") and name.endswith("`"):
                    name = name[1:-1]
                kind = m.group("kind")
                is_public = _is_public(m.group("mods"))
                mods_parts = m.group("mods").split()
                # Effective scope: if we're inside a primary-ctor `(...)`,
                # decls (val/var) belong to the pending class.
                effective_scope: list = scope
                if in_primary_ctor and primary_ctor_scope and kind in {"val", "var"}:
                    effective_scope = scope + [[primary_ctor_scope, depth, False, False]]
                # Emit only public symbols, but always track scope/body — non-public
                # funs still introduce local-var scopes that should be skipped.
                if is_public:
                    scope_path = ".".join(s[0] for s in effective_scope)
                    if scope_path:
                        fqn = f"{pkg}.{scope_path}.{name}" if pkg else f"{scope_path}.{name}"
                    else:
                        fqn = f"{pkg}.{name}" if pkg else name
                    # Auto-exclude `override fun equals/hashCode/toString` —
                    # those are Kotlin/Java idiom overrides of Any/Object,
                    # never mapped to a Skia upstream symbol.
                    excluded_reason: str | None = None
                    if (
                        kind == "fun"
                        and "override" in mods_parts
                        and name in _OVERRIDE_OBJECT_METHODS
                    ):
                        excluded_reason = "override-Any-method"
                    _emit(fqn, lineno, excluded_reason)
                if is_public and kind in {"class", "object", "interface"}:
                    pending_scope = name
                    pending_scope_is_enum = (kind == "class" and "enum" in mods_parts)
                    primary_ctor_scope = name
                    # Single-line enum body — `enum class X { kA, kB }` opens
                    # and closes `{}` on one line, so the scope is pushed and
                    # popped within this iteration and the regular enum-body
                    # path never fires. Parse entries inline from the braces.
                    if pending_scope_is_enum and "{" in line and "}" in line:
                        ob = line.find("{")
                        cb = line.rfind("}")
                        if 0 <= ob < cb:
                            body = line[ob + 1 : cb]
                            # Strip after a `;` — entries end there.
                            if ";" in body:
                                body = body.split(";", 1)[0]
                            for raw_entry in body.split(","):
                                entry = raw_entry.strip()
                                # An entry may carry `(args)` or `{ … }` but
                                # those are stripped at the first non-ident
                                # character.
                                em2 = re.match(r"([A-Za-z_]\w*)", entry)
                                if em2:
                                    entry_name = em2.group(1)
                                    scope_path = ".".join(s[0] for s in scope + [[name, depth, True, False]])
                                    efqn = f"{pkg}.{scope_path}.{entry_name}" if pkg else f"{scope_path}.{entry_name}"
                                    _emit(efqn, lineno)
                    # Single-line primary ctor — `class X(val a, val b) {…}` on
                    # one line. Extract val/var params now, since the multi-line
                    # `in_primary_ctor` path below only triggers when `{` lands
                    # on a later line. Scan everything after the first `(`.
                    open_paren_idx = line.find("(")
                    if open_paren_idx != -1:
                        # Slice from `(` onwards; the params section ends at the
                        # matching `)`. Walk char-by-char to find it.
                        pd = 0
                        end_idx = -1
                        for i in range(open_paren_idx, len(line)):
                            if line[i] == "(":
                                pd += 1
                            elif line[i] == ")":
                                pd -= 1
                                if pd == 0:
                                    end_idx = i
                                    break
                        if end_idx != -1:
                            params_segment = line[open_paren_idx + 1 : end_idx]
                            for pm in _CTOR_PARAM_RE.finditer(params_segment):
                                vis = pm.group("vis")
                                if vis in _VISIBILITY_NON_PUBLIC:
                                    continue
                                pname = pm.group("name")
                                pscope_path = ".".join(
                                    s[0] for s in scope + [[name, depth, False, False]]
                                )
                                pfqn = f"{pkg}.{pscope_path}.{pname}" if pkg else f"{pscope_path}.{pname}"
                                _emit(pfqn, lineno)
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
            scope.append([pending_scope, depth, pending_scope_is_enum, False])
            pending_scope = None
            pending_scope_is_enum = False
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


def load_ignore_set(tsv_dir: Path) -> set[str]:
    """Load `_ignore.txt` — one Kotlin FQN per line. `#` starts a comment."""
    out: set[str] = set()
    f = tsv_dir / "_ignore.txt"
    if not f.is_file():
        return out
    for raw in f.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            out.add(line)
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

    # Source dirs: MPP layout first, then legacy JVM layout.
    # Tests are mapped in their own *Test.tsv files; including both keeps the
    # "stale" set focused on genuinely orphaned entries.
    module_root = repo_root / module.split("/")[0]
    src_roots = [
        module_root / "src" / "commonMain" / "kotlin",
        module_root / "src" / "commonTest" / "kotlin",
        module_root / "src" / "main" / "kotlin",
        module_root / "src" / "test" / "kotlin",
    ]
    existing_src_roots = [root for root in src_roots if root.is_dir()]
    if not existing_src_roots:
        roots = " or ".join(str(root) for root in src_roots)
        print(f"error: no src dir at {roots}", file=sys.stderr)
        return 2

    # For sub-paths like "kanvas-skia/foundation", further filter Kotlin files
    sub = "/".join(module.split("/")[1:])
    kt_files: list[Path] = []
    for root in existing_src_roots:
        kt_files.extend(root.rglob("*.kt"))
    kt_files.sort()
    if sub:
        kt_files = [f for f in kt_files if f"/{sub}/" in str(f) or str(f).endswith(f"/{sub}")]

    # Extract Kotlin symbols
    kotlin_symbols: dict[str, Symbol] = {}
    for kt in kt_files:
        for sym in extract_symbols(kt, repo_root):
            kotlin_symbols[sym.fqn] = sym

    # Load TSV FQNs and ignore-list for this module
    tsv_dir = script_dir / module
    tsv_fqns = load_tsv_kotlin_fqns(tsv_dir)
    ignore_fqns = load_ignore_set(tsv_dir)

    # Auto-excluded symbols (Kotlin-idiom heuristics on the symbol itself).
    auto_excluded = {
        fqn for fqn, sym in kotlin_symbols.items() if sym.excluded_reason is not None
    }
    # Explicit exclusions from `_ignore.txt`. Only count entries that actually
    # match a current Kotlin symbol — anything else is reported as STALE.
    explicit_excluded = ignore_fqns & set(kotlin_symbols.keys())
    excluded = auto_excluded | explicit_excluded

    kotlin_set = set(kotlin_symbols.keys()) - excluded
    missing = sorted(kotlin_set - tsv_fqns)
    # STALE: TSV entries OR ignore entries with no matching Kotlin symbol.
    stale_tsv = tsv_fqns - set(kotlin_symbols.keys())
    stale_ignore = ignore_fqns - set(kotlin_symbols.keys())
    stale = sorted(stale_tsv | stale_ignore)

    print(f"Module:        {module}")
    print(f"Kotlin sources scanned:  {len(kt_files)}")
    print(f"Public symbols (heuristic): {len(kotlin_symbols)}")
    print(f"  - auto-excluded (override Any methods): {len(auto_excluded)}")
    print(f"  - explicit-excluded (_ignore.txt):      {len(explicit_excluded)}")
    print(f"  - audited (remaining):                  {len(kotlin_set)}")
    print(f"TSV entries:    {len(tsv_fqns)}")
    print()

    if missing:
        print(f"MISSING — {len(missing)} symbole(s) Kotlin sans entrée TSV :")
        for fqn in missing:
            s = kotlin_symbols[fqn]
            print(f"  {s.file}:{s.line}\t{fqn}")
        print()

    if stale:
        print(f"STALE — {len(stale)} entrée(s) TSV/ignore sans symbole Kotlin :")
        for fqn in stale:
            src = []
            if fqn in stale_tsv:
                src.append("tsv")
            if fqn in stale_ignore:
                src.append("ignore")
            print(f"  [{','.join(src)}] {fqn}")
        print()

    if not missing and not stale:
        print("✓ Map complète et à jour.")
        return 0

    # Both MISSING and STALE are now lint-enforcing. STALE entries indicate
    # TSV/ignore rows pointing at Kotlin symbols that no longer exist (renames,
    # deletions, or upstream-only artifacts) and should be cleaned up.
    return 1 if (missing or stale) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
