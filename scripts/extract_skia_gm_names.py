#!/usr/bin/env python3
"""Extract all Skia GM names from .cpp files in a Skia gm/ directory."""

import argparse
import re
from pathlib import Path


REPO = Path(__file__).resolve().parent.parent
DEFAULT_GM_DIR_CANDIDATES = (
    REPO / "external" / "skia" / "gm",
    REPO.parent / "skia" / "gm",
)


def find_matching_brace(lines, start_line_idx, start_char_idx):
    depth = 0
    in_block_comment = False
    in_string = False
    string_char = None
    for i in range(start_line_idx, len(lines)):
        line = lines[i]
        start = start_char_idx if i == start_line_idx else 0
        j = start
        while j < len(line):
            ch = line[j]
            if in_string:
                if ch == '\\':
                    j += 2
                    continue
                if ch == string_char:
                    in_string = False
                    string_char = None
                j += 1
                continue
            if in_block_comment:
                if ch == '*' and j + 1 < len(line) and line[j + 1] == '/':
                    in_block_comment = False
                    j += 2
                    continue
                j += 1
                continue
            if ch in ('"', "'"):
                in_string = True
                string_char = ch
                j += 1
                continue
            if ch == '/' and j + 1 < len(line):
                if line[j + 1] == '/':
                    break
                if line[j + 1] == '*':
                    in_block_comment = True
                    j += 2
                    continue
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    return i, j
            j += 1
    return None, None


def strip_line_comment(line):
    idx = line.find('//')
    if idx != -1:
        in_str = False
        for k in range(idx):
            if line[k] == '"':
                in_str = not in_str
        if not in_str:
            return line[:idx]
    return line


def extract_simple_gm_names(content):
    names = set()
    pat = re.compile(
        r'DEF_SIMPLE_GM(?:_CAN_FAIL|_BG|_BG_CAN_FAIL|_BG_NAME|_BG_NAME_CAN_FAIL)?\s*\(\s*'
        r'(\w+)'
    )
    for m in pat.finditer(content):
        names.add(m.group(1))
    return names


def parse_class_definitions(lines):
    classes = {}
    for i, line in enumerate(lines):
        stripped = strip_line_comment(line).strip()

        m = re.search(
            r'class\s+(\w+)\s*(?::\s*public\s+(?:\w+(?:::))?\w+'
            r'(?:\s*,\s*public\s+(?:\w+(?:::))?\w+)*)?\s*\{',
            stripped
        )
        if not m:
            continue

        cls_name = m.group(1)
        if cls_name in ('sk_sp',): continue

        # Find the opening brace in the original line
        brace_pos = line.find('{')
        if brace_pos == -1:
            continue

        end_line, _ = find_matching_brace(lines, i, brace_pos)
        if end_line is not None:
            classes[cls_name] = (i, end_line)
    return classes


def extract_getname_from_class(lines, start_line, end_line):
    for i in range(start_line, end_line + 1):
        l = strip_line_comment(lines[i])
        if not re.search(r'(?:SkString|const\s+char\s*\*)\s+getName\(\)', l):
            continue
        brace_start = l.find('{')
        if brace_start == -1:
            continue
        body_end_line, body_end_char = find_matching_brace(lines, i, brace_start)
        if body_end_line is None:
            continue
        body_parts = []
        if i == body_end_line:
            body_parts.append(l[brace_start + 1:body_end_char])
        else:
            body_parts.append(l[brace_start + 1:])
            for j in range(i + 1, body_end_line):
                body_parts.append(lines[j])
            body_parts.append(lines[body_end_line][:body_end_char])
        body = ' '.join(body_parts)
        body = strip_line_comment(body).strip()

        # return SkString("literal");
        m = re.search(r'return\s+SkString\(["\']([^"\']+)["\']\)', body)
        if m:
            return ('literal', m.group(1))

        # return SkString(cond ? "a" : "b");
        m = re.search(r'return\s+SkString\(\w+\s*\?\s*["\']([^"\']+)["\']\s*:\s*["\']([^"\']+)["\']\)', body)
        if m:
            # Both branches of ternary — return first option (truthy branch)
            return ('literal', m.group(1))

        # return SkString(varName); — variable via SkString constructor
        m = re.search(r'return\s+SkString\((\w+)\)', body)
        if m:
            return ('variable', m.group(1))

        # return varName; — direct variable reference
        m = re.search(r'return\s+(\w+)\s*;', body)
        if m:
            return ('variable', m.group(1))

        # return SkStringPrintf("fmt", ...);
        m = re.search(r'return\s+(?:SkStringPrintf|printf)\(["\']([^"\']+)["\']', body)
        if m:
            return ('fmt', m.group(1))

        # var.printf("fmt", ...) / var.appendf / name.append("...")  followed by return var
        for var_prefix in [r'\w+', r'f\w+', r'name', r'str', r'fullName', r'descriptor']:
            m = re.search(
                r'\b(' + var_prefix + r')\.(?:printf|appendf?|prepend)\(["\']([^"\']+)["\']',
                body
            )
            if m:
                return ('fmt', m.group(2))

    return None, None


def extract_def_gm_codes(content):
    """Extract CODE from all DEF_GM(CODE) in content, handling nested parens.
    CODE includes the semicolon(s) inside the outer parens, e.g.
    DEF_GM(return new FooGM;)  → CODE = "return new FooGM;" """
    results = []
    idx = 0
    while True:
        start = content.find('DEF_GM', idx)
        if start == -1:
            break
        paren = content.find('(', start)
        if paren == -1:
            break
        depth = 0
        i = paren
        while i < len(content):
            c = content[i]
            if c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    results.append(content[paren + 1:i])
                    idx = i + 1
                    break
            elif c in ('"', "'"):
                q = c
                i += 1
                while i < len(content):
                    if content[i] == '\\':
                        i += 2
                        continue
                    if content[i] == q:
                        break
                    i += 1
            i += 1
        else:
            break
    return results


def trace_variable_from_constructor(lines, class_start, class_end, var_name):
    """Try to find how a member variable is initialized by looking at the
    constructor initializer list and constructor body."""
    # Find the constructor
    # First, extract the class name from the class definition line
    cls_line = lines[class_start]
    m = re.search(r'class\s+(\w+)', cls_line)
    if not m:
        return None
    cls_name = m.group(1)

    # Find constructor definition (must have same name as class, within the class body)
    for i in range(class_start + 1, class_end + 1):
        l = strip_line_comment(lines[i])
        # match: ClassName(...) : var("literal") ... {
        # or:    ClassName(...) { var = "literal"; ... }
        if not re.search(r'\b' + cls_name + r'\s*\(', l):
            continue

        # Check if this is the constructor (not a destructor)
        if '~' + cls_name in l:
            continue

        brace_pos = l.find('{')
        if brace_pos == -1:
            continue

        # Check initializer list for var("literal") or var{"literal"}
        init_part = l[:brace_pos]
        # Split on initializer list (after ':')
        colon_pos = init_part.find(':')
        if colon_pos != -1:
            init_list = init_part[colon_pos + 1:]
            vm = re.search(r'\b' + var_name + r'\s*[{(]["\']([^"\']+)["\']', init_list)
            if vm:
                return ('literal', vm.group(1))

            # fName(suffix) where suffix is a parameter
            vm2 = re.search(r'\b' + var_name + r'\s*\(\s*(\w+)\s*\)', init_list)
            if vm2:
                param_name = vm2.group(1)
                # Check if the parameter is passed a string literal in the constructor call
                # We need to find DEF_GM calls to this class
                return ('param', param_name)

        # Check constructor body for simple assignments
        body_end_line, body_end_char = find_matching_brace(lines, i, brace_pos)
        if body_end_line is None:
            continue

        body_parts = []
        if i == body_end_line:
            body_parts.append(lines[i][brace_pos + 1:body_end_char])
        else:
            body_parts.append(lines[i][brace_pos + 1:])
            for j in range(i + 1, body_end_line):
                body_parts.append(lines[j])
            body_parts.append(lines[body_end_line][:body_end_char])
        body = ' '.join(body_parts)
        body = strip_line_comment(body)

        # Find: var.set("literal") or var = "literal" or var.append("literal")
        am = re.search(r'\b' + var_name + r'\.set\(["\']([^"\']+)["\']', body)
        if am:
            return ('literal', am.group(1))

        am = re.search(r'\b' + var_name + r'\s*=\s*["\']([^"\']+)["\']', body)
        if am:
            return ('literal', am.group(1))

        am = re.search(r'\b' + var_name + r'\.printf\(["\']([^"\']+)["\']', body)
        if am:
            return ('fmt', am.group(1))

        am = re.search(r'\b' + var_name + r'\.appendf?\(["\']([^"\']+)["\']', body)
        if am:
            return ('fmt', am.group(1))

        # var.prepend("literal")
        am = re.search(r'\b' + var_name + r'\.prepend\(["\']([^"\']+)["\']', body)
        if am:
            return ('fmt', am.group(1) + '%s')  # approximate

    return None


def unresolved_name(class_name):
    return f'<unresolved:{class_name}>'


def make_name_key(name):
    """Sort helper: place unresolved (<...>) names at end."""
    if name.startswith('<'):
        return (1, name)
    return (0, name)


def build_inventory(gm_dir: Path):
    cpp_files = sorted(gm_dir.glob('*.cpp'))

    # Build a per-file cache of class definitions: class_name -> (file_lines, start, end)
    file_classes_cache = {}  # filepath -> {class_name: (start_line, end_line)}

    for fp in cpp_files:
        with fp.open('r', errors='replace') as f:
            lines = f.readlines()
        file_classes_cache[fp] = parse_class_definitions(lines)

    all_names = set()
    total_simple = 0
    total_def_gm = 0
    unresolved = []

    for fp in cpp_files:
        with fp.open('r', errors='replace') as f:
            content = f.read()
        lines = content.splitlines(keepends=True)
        fname = fp.name

        # 1. DEF_SIMPLE_GM variants
        simple_names = extract_simple_gm_names(content)
        all_names.update(simple_names)
        total_simple += len(simple_names)

        # 2. DEF_GM class-based — with nested paren matching
        def_gm_codes = extract_def_gm_codes(content)
        for code in def_gm_codes:
            new_m = re.search(r'return\s+new\s+(?:\w+::)?(\w+)', code)
            if not new_m:
                continue
            cls_name = new_m.group(1)
            total_def_gm += 1

            classes = file_classes_cache[fp]
            resolved = None

            if cls_name in classes:
                start, end = classes[cls_name]
                kind, val = extract_getname_from_class(lines, start, end)
                if kind == 'literal':
                    resolved = val
                elif kind == 'variable':
                    tr = trace_variable_from_constructor(lines, start, end, val)
                    if tr and tr[0] == 'literal':
                        resolved = tr[1]
                    elif tr and tr[0] == 'param':
                        param_name = tr[1]
                        arg_m = re.search(r'["\']([^"\']+)["\']', code)
                        if arg_m:
                            resolved = arg_m.group(1)
                        else:
                            unresolved.append((fname, cls_name, f'param:{param_name}',
                                               'param unresolved'))
                            resolved = unresolved_name(cls_name)
                    else:
                        arg_m = re.search(r'["\']([^"\']+)["\']', code)
                        if arg_m:
                            resolved = arg_m.group(1)
                        else:
                            unresolved.append((fname, cls_name, val, 'variable unresolved'))
                            resolved = unresolved_name(cls_name)
                elif kind == 'fmt':
                    arg_m = re.search(r'["\']([^"\']+)["\']', code)
                    if arg_m:
                        arg = arg_m.group(1)
                        if '%s' in val:
                            if val.startswith('%s'):
                                resolved = arg + val[2:]
                            elif val.endswith('%s'):
                                resolved = val[:-2] + arg
                            else:
                                resolved = val.replace('%s', arg)
                        elif '%i' in val or '%d' in val:
                            resolved = f'<fmt:{val}>'
                        else:
                            resolved = val
                    else:
                        unresolved.append((fname, cls_name, val, 'fmt unresolved'))
                        resolved = unresolved_name(cls_name)
                else:
                    unresolved.append((fname, cls_name, kind, 'no getName'))
                    resolved = unresolved_name(cls_name)
            else:
                unresolved.append((fname, cls_name, None, 'class not found'))
                resolved = unresolved_name(cls_name)

            all_names.add(resolved)

    return {
        'cpp_files': cpp_files,
        'sorted_names': sorted(all_names, key=make_name_key),
        'total_simple': total_simple,
        'total_def_gm': total_def_gm,
        'unresolved': unresolved,
    }


def extract_gm_names(gm_dir: Path) -> set[str]:
    return set(build_inventory(gm_dir)['sorted_names'])


def resolve_default_gm_dir():
    for candidate in DEFAULT_GM_DIR_CANDIDATES:
        if candidate.is_dir():
            return candidate
    return None


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--names', action='store_true', help='print only sorted GM names')
    parser.add_argument(
        '--gm-dir',
        type=Path,
        help='path to the Skia gm/ directory to scan',
    )
    args = parser.parse_args()

    gm_dir = args.gm_dir
    if gm_dir is None:
        gm_dir = resolve_default_gm_dir()
        if gm_dir is None:
            parser.error(
                '--gm-dir is required when no default Skia gm directory is available '
                'under the repository checkout'
            )
    elif not gm_dir.is_dir():
        parser.error(f'--gm-dir is not a directory: {gm_dir}')

    args.gm_dir = gm_dir
    return args


def main():
    args = parse_args()
    inventory = build_inventory(args.gm_dir)
    sorted_names = inventory['sorted_names']

    if args.names:
        for n in sorted_names:
            print(n)
        return

    print(f"Files scanned: {len(inventory['cpp_files'])}")
    print(f"Total unique GM names: {len(sorted_names)}")
    print(f"  DEF_SIMPLE_GM variants: {inventory['total_simple']}")
    print(f"  DEF_GM class-based: {inventory['total_def_gm']}")
    print()

    unresolved_actual = inventory['unresolved']
    if unresolved_actual:
        print(f"Unresolved ({len(unresolved_actual)} entries, explicit placeholder name kept):")
        for f, cls, kind, reason in unresolved_actual:
            print(f"  {cls} in {f}: {reason} (kind={kind})")
        print()

    resolved_via_param = [n for n in sorted_names if n.startswith('<param:') or n.startswith('<fmt:')]
    if resolved_via_param:
        print(f"Partially resolved (param/fmt patterns): {len(resolved_via_param)}")
        for n in resolved_via_param:
            print(f"  {n}")
        print()

    print("First 20 names (alphabetical):")
    for n in sorted_names[:20]:
        print(f"  {n}")
    print("...")
    print("Last 20 names:")
    for n in sorted_names[-20:]:
        print(f"  {n}")


if __name__ == '__main__':
    main()
