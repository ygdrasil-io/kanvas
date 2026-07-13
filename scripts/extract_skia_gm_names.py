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


def strip_comments_preserve_strings(text):
    result = []
    i = 0
    in_string = False
    string_char = None
    while i < len(text):
        ch = text[i]
        if in_string:
            result.append(ch)
            if ch == '\\' and i + 1 < len(text):
                result.append(text[i + 1])
                i += 2
                continue
            if ch == string_char:
                in_string = False
                string_char = None
            i += 1
            continue
        if ch in ('"', "'"):
            in_string = True
            string_char = ch
            result.append(ch)
            i += 1
            continue
        if ch == '/' and i + 1 < len(text):
            next_ch = text[i + 1]
            if next_ch == '/':
                i += 2
                while i < len(text) and text[i] != '\n':
                    i += 1
                continue
            if next_ch == '*':
                i += 2
                while i + 1 < len(text) and not (text[i] == '*' and text[i + 1] == '/'):
                    i += 1
                i += 2
                continue
        result.append(ch)
        i += 1
    return ''.join(result)


def extract_simple_gm_names(content):
    names = set()
    content = strip_comments_preserve_strings(content)
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
        bases = []
        if ':' in stripped:
            inheritance = stripped.split(':', 1)[1].rsplit('{', 1)[0]
            for base in inheritance.split(','):
                base = base.strip()
                if not base:
                    continue
                bases.append(base.split()[-1].split('::')[-1])

        # Find the opening brace in the original line
        brace_pos = line.find('{')
        if brace_pos == -1:
            continue

        end_line, _ = find_matching_brace(lines, i, brace_pos)
        if end_line is not None:
            classes[cls_name] = {
                'start': i,
                'end': end_line,
                'bases': bases,
            }
    return classes


def extract_getname_pattern_from_class(lines, start_line, end_line):
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
            return ('literal', m.group(1), body)

        # return SkString(cond ? "a" : "b");
        m = re.search(r'return\s+SkString\(\w+\s*\?\s*["\']([^"\']+)["\']\s*:\s*["\']([^"\']+)["\']\)', body)
        if m:
            # Both branches of ternary — return first option (truthy branch)
            return ('literal', m.group(1), body)

        # return SkString(varName); — variable via SkString constructor
        m = re.search(r'return\s+SkString\((\w+)\)', body)
        if m:
            return ('variable', m.group(1), body)

        # return varName; — direct variable reference
        m = re.search(r'return\s+(\w+)\s*;', body)
        if m:
            return ('variable', m.group(1), body)

        # return SkStringPrintf("fmt", ...);
        m = re.search(r'return\s+(?:SkStringPrintf|printf)\(["\']([^"\']+)["\']', body)
        if m:
            return ('fmt', m.group(1), body)

        # var.printf("fmt", ...) / var.appendf / name.append("...")  followed by return var
        for var_prefix in [r'\w+', r'f\w+', r'name', r'str', r'fullName', r'descriptor']:
            m = re.search(
                r'\b(' + var_prefix + r')\.(?:printf|appendf?|prepend)\(["\']([^"\']+)["\']',
                body
            )
            if m:
                return ('fmt', m.group(2), body)

        return None, None, body

    return None, None, None


def extract_getname_from_class(lines, start_line, end_line):
    kind, value, _ = extract_getname_pattern_from_class(lines, start_line, end_line)
    return kind, value


def extract_def_gm_codes(content):
    """Extract CODE from all DEF_GM(CODE) in content, handling nested parens.
    CODE includes the semicolon(s) inside the outer parens, e.g.
    DEF_GM(return new FooGM;)  → CODE = "return new FooGM;" """
    results = []
    content = strip_comments_preserve_strings(content)
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


def find_matching_delimiter(text, start_idx, opener='(', closer=')'):
    depth = 0
    in_string = False
    string_char = None
    i = start_idx
    while i < len(text):
        ch = text[i]
        if in_string:
            if ch == '\\':
                i += 2
                continue
            if ch == string_char:
                in_string = False
                string_char = None
            i += 1
            continue
        if ch in ('"', "'"):
            in_string = True
            string_char = ch
            i += 1
            continue
        if ch == opener:
            depth += 1
        elif ch == closer:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return None


def split_top_level_args(text):
    args = []
    current = []
    paren_depth = 0
    brace_depth = 0
    bracket_depth = 0
    in_string = False
    string_char = None
    i = 0
    while i < len(text):
        ch = text[i]
        if in_string:
            current.append(ch)
            if ch == '\\' and i + 1 < len(text):
                current.append(text[i + 1])
                i += 2
                continue
            if ch == string_char:
                in_string = False
                string_char = None
            i += 1
            continue
        if ch in ('"', "'"):
            in_string = True
            string_char = ch
            current.append(ch)
            i += 1
            continue
        if ch == '(':
            paren_depth += 1
        elif ch == ')':
            paren_depth -= 1
        elif ch == '{':
            brace_depth += 1
        elif ch == '}':
            brace_depth -= 1
        elif ch == '[':
            bracket_depth += 1
        elif ch == ']':
            bracket_depth -= 1
        elif ch == ',' and paren_depth == 0 and brace_depth == 0 and bracket_depth == 0:
            arg = ''.join(current).strip()
            if arg:
                args.append(arg)
            current = []
            i += 1
            continue
        current.append(ch)
        i += 1
    tail = ''.join(current).strip()
    if tail:
        args.append(tail)
    return args


def parse_new_class_args(code, class_name):
    m = re.search(r'return\s+new\s+(?:\w+::)?' + re.escape(class_name) + r'\s*\(', code)
    if not m:
        return None
    paren_start = code.find('(', m.start())
    if paren_start == -1:
        return None
    paren_end = find_matching_delimiter(code, paren_start)
    if paren_end is None:
        return None
    return split_top_level_args(code[paren_start + 1:paren_end])


def find_constructor_signature(lines, class_info, class_name):
    start = class_info['start']
    end = class_info['end']
    for i in range(start + 1, end + 1):
        l = strip_line_comment(lines[i])
        if '~' + class_name in l:
            continue
        match = re.search(r'\b' + re.escape(class_name) + r'\s*\(', l)
        if not match:
            continue
        signature_parts = []
        in_block_comment = False
        in_string = False
        string_char = None
        paren_depth = 0
        for j in range(i, end + 1):
            line = lines[j]
            start_char = match.start() if j == i else 0
            k = start_char
            while k < len(line):
                ch = line[k]
                if in_string:
                    signature_parts.append(ch)
                    if ch == '\\' and k + 1 < len(line):
                        signature_parts.append(line[k + 1])
                        k += 2
                        continue
                    if ch == string_char:
                        in_string = False
                        string_char = None
                    k += 1
                    continue
                if in_block_comment:
                    if ch == '*' and k + 1 < len(line) and line[k + 1] == '/':
                        in_block_comment = False
                        k += 2
                        continue
                    k += 1
                    continue
                if ch in ('"', "'"):
                    in_string = True
                    string_char = ch
                    signature_parts.append(ch)
                    k += 1
                    continue
                if ch == '/' and k + 1 < len(line):
                    if line[k + 1] == '/':
                        break
                    if line[k + 1] == '*':
                        in_block_comment = True
                        k += 2
                        continue
                if ch == '(':
                    paren_depth += 1
                elif ch == ')' and paren_depth > 0:
                    paren_depth -= 1
                if ch == '{' and paren_depth == 0:
                    return ''.join(signature_parts).strip()
                signature_parts.append(ch)
                k += 1
            signature_parts.append(' ')
    return None


def parse_constructor_signature(signature, class_name):
    ctor_start = signature.find(class_name)
    if ctor_start == -1:
        return None
    paren_start = signature.find('(', ctor_start)
    if paren_start == -1:
        return None
    paren_end = find_matching_delimiter(signature, paren_start)
    if paren_end is None:
        return None

    params_text = signature[paren_start + 1:paren_end]
    remainder = signature[paren_end + 1:].strip()
    param_defs = []
    for param in split_top_level_args(params_text):
        param = param.strip()
        if not param or param == 'void':
            continue
        default = None
        if '=' in param:
            before_default, default = param.split('=', 1)
            param = before_default.strip()
            default = default.strip()
        name_match = re.search(r'(\w+)\s*$', param)
        if not name_match:
            continue
        param_defs.append((name_match.group(1), default))

    initializers = []
    if remainder.startswith(':'):
        for initializer in split_top_level_args(remainder[1:].strip()):
            init_match = re.match(r'(?:(?:\w+::)*)?(\w+)\s*([({])(.*)([)}])\s*$', initializer, re.S)
            if not init_match:
                continue
            initializers.append({
                'name': init_match.group(1),
                'args': split_top_level_args(init_match.group(3)),
            })

    return {
        'params': param_defs,
        'initializers': initializers,
    }


def build_param_env(param_defs, arg_values):
    env = {}
    for index, (name, default) in enumerate(param_defs):
        if index < len(arg_values):
            env[name] = arg_values[index]
        elif default is not None:
            env[name] = default
    return env


def resolve_expr(expr, env):
    expr = expr.strip()
    if expr in env:
        expr = str(env[expr]).strip()
    if expr == 'true':
        return True
    if expr == 'false':
        return False
    quoted = re.fullmatch(r'["\']([^"\']*)["\']', expr)
    if quoted:
        return quoted.group(1)
    return expr


def normalize_cpp_symbol(value):
    return re.sub(r'\s+', '', value)


def labels_match(actual_value, label_value):
    actual_norm = normalize_cpp_symbol(str(actual_value))
    label_norm = normalize_cpp_symbol(str(label_value))
    return (
        actual_norm == label_norm
        or actual_norm.endswith(label_norm)
        or label_norm.endswith(actual_norm)
    )


def class_matches_or_inherits(class_name, target_name, classes):
    if class_name == target_name:
        return True
    class_info = classes.get(class_name)
    if not class_info:
        return False
    return any(class_matches_or_inherits(base, target_name, classes) for base in class_info['bases'])


def get_getname_info(class_name, classes, lines, seen=None):
    if seen is None:
        seen = set()
    if class_name in seen:
        return None
    seen.add(class_name)

    class_info = classes.get(class_name)
    if not class_info:
        return None

    kind, value, body = extract_getname_pattern_from_class(lines, class_info['start'], class_info['end'])
    if kind is not None or body is not None:
        return class_name, kind, value, body

    for base in class_info['bases']:
        base_info = get_getname_info(base, classes, lines, seen)
        if base_info is not None:
            return base_info
    return None


def resolve_member_value(class_name, owner_class_name, member_name, classes, lines, arg_values, seen=None):
    if seen is None:
        seen = set()
    state_key = (class_name, owner_class_name, member_name, tuple(arg_values))
    if state_key in seen:
        return None
    seen.add(state_key)

    class_info = classes.get(class_name)
    if not class_info:
        return None
    signature = find_constructor_signature(lines, class_info, class_name)
    if signature is None:
        return None
    constructor = parse_constructor_signature(signature, class_name)
    if constructor is None:
        return None
    env = build_param_env(constructor['params'], arg_values)

    if class_name == owner_class_name:
        for initializer in constructor['initializers']:
            if initializer['name'] == member_name and initializer['args']:
                return resolve_expr(initializer['args'][0], env)

    for initializer in constructor['initializers']:
        init_name = initializer['name']
        if init_name in class_info['bases'] and class_matches_or_inherits(init_name, owner_class_name, classes):
            base_args = [resolve_expr(arg, env) for arg in initializer['args']]
            resolved = resolve_member_value(
                init_name,
                owner_class_name,
                member_name,
                classes,
                lines,
                base_args,
                seen,
            )
            if resolved is not None:
                return resolved
    return None


def resolve_constructor_state(class_name, classes, lines, arg_values):
    class_info = classes.get(class_name)
    if not class_info:
        return {}
    signature = find_constructor_signature(lines, class_info, class_name)
    if signature is None:
        return {}
    constructor = parse_constructor_signature(signature, class_name)
    if constructor is None:
        return {}
    env = build_param_env(constructor['params'], arg_values)
    state = {}
    for initializer in constructor['initializers']:
        if initializer['name'] in class_info['bases']:
            continue
        if initializer['args']:
            state[initializer['name']] = resolve_expr(initializer['args'][0], env)
    return state


def evaluate_switch_constructed_name(body, state):
    switch_match = re.search(r'switch\s*\(\s*(\w+)\s*\)\s*\{(.*?)\}', body, re.S)
    if not switch_match:
        return None

    switch_var = switch_match.group(1)
    switch_value = state.get(switch_var)
    if switch_value is None:
        return None

    switch_body = switch_match.group(2)
    selected_var = None
    selected_value = None
    selected_mode = None

    for case_match in re.finditer(
        r'case\s+(.*?)(?<!:):(?!:)\s*(.*?)(?=case\s+.*?(?<!:):(?!:)|default\s*:|$)',
        switch_body,
        re.S,
    ):
        if not labels_match(switch_value, case_match.group(1).strip()):
            continue
        case_body = case_match.group(2)
        direct_assign = re.search(r'(\w+)\s*=\s*"([^"]+)"', case_body)
        if direct_assign:
            selected_var = direct_assign.group(1)
            selected_value = direct_assign.group(2)
            selected_mode = 'replace'
            break
        append_assign = re.search(r'(\w+)\s*\+=\s*"([^"]+)"', case_body)
        if append_assign:
            selected_var = append_assign.group(1)
            selected_value = append_assign.group(2)
            selected_mode = 'append'
            break
        append_call = re.search(r'(\w+)\.append\("([^"]+)"\)', case_body)
        if append_call:
            selected_var = append_call.group(1)
            selected_value = append_call.group(2)
            selected_mode = 'append'
            break

    if selected_var is None or selected_value is None:
        return None

    prefix = ''
    init_match = re.search(r'SkString\s+' + re.escape(selected_var) + r'\("([^"]*)"\)', body)
    if init_match:
        prefix = init_match.group(1)
    prepend_match = re.search(r'\b' + re.escape(selected_var) + r'\.prepend\("([^"]+)"\)', body)
    if prepend_match:
        prefix = prepend_match.group(1) + prefix

    suffix = ''
    for cond_match in re.finditer(
        r'if\s*\(\s*(\w+)\s*\)\s*\{(.*?)\}',
        body,
        re.S,
    ):
        if not state.get(cond_match.group(1)):
            continue
        append_match = re.search(
            r'\b' + re.escape(selected_var) + r'(?:\.append\("([^"]+)"\)|\s*\+=\s*"([^"]+)")',
            cond_match.group(2),
        )
        if append_match:
            suffix += append_match.group(1) or append_match.group(2)

    if selected_mode == 'append':
        return prefix + selected_value + suffix
    return prefix + selected_value + suffix


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


def is_placeholder_name(name):
    return name.startswith('<')


def make_name_key(name):
    """Sort helper: place placeholder (<...>) names at end."""
    if is_placeholder_name(name):
        return (1, name)
    return (0, name)


def build_inventory(gm_dir: Path):
    cpp_files = sorted(gm_dir.glob('*.cpp'))

    # Build a per-file cache of class definitions: class_name -> (file_lines, start, end)
    file_classes_cache = {}  # filepath -> {class_name: {start, end, bases}}

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
                getname_info = get_getname_info(cls_name, classes, lines)
                if getname_info is None:
                    kind = None
                    val = None
                    owner_class_name = None
                    getname_body = None
                else:
                    owner_class_name, kind, val, getname_body = getname_info
                def_gm_args = parse_new_class_args(code, cls_name) or []
                if kind == 'literal':
                    resolved = val
                elif kind == 'variable':
                    if owner_class_name is not None:
                        resolved = resolve_member_value(
                            cls_name,
                            owner_class_name,
                            val,
                            classes,
                            lines,
                            def_gm_args,
                        )
                    else:
                        resolved = None
                    if resolved is None and getname_body is not None:
                        state = resolve_constructor_state(cls_name, classes, lines, def_gm_args)
                        resolved = evaluate_switch_constructed_name(getname_body, state)
                    if resolved is None:
                        start = classes[cls_name]['start']
                        end = classes[cls_name]['end']
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

    sorted_names = sorted(all_names, key=make_name_key)
    authoritative_names = [name for name in sorted_names if not is_placeholder_name(name)]

    return {
        'cpp_files': cpp_files,
        'sorted_names': sorted_names,
        'sorted_authoritative_names': authoritative_names,
        'total_simple': total_simple,
        'total_def_gm': total_def_gm,
        'unresolved': unresolved,
    }


def extract_gm_names(gm_dir: Path) -> set[str]:
    return set(build_inventory(gm_dir)['sorted_authoritative_names'])


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
    sorted_names = inventory['sorted_authoritative_names']

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
        print(f"Unresolved ({len(unresolved_actual)} entries, excluded from authoritative names):")
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
