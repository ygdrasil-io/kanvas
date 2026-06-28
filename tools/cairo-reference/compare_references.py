#!/usr/bin/env python3
"""Compare Cairo-reference PNGs against committed GPU render.png files."""

import struct, zlib, sys, os, math

def read_png(path):
    with open(path, 'rb') as f:
        data = f.read()
    i = 8
    chunks = {}
    while i < len(data):
        length = struct.unpack('>I', data[i:i+4])[0]
        ct = data[i+4:i+8].decode('ascii', errors='replace')
        chunks[ct] = data[i+8:i+8+length]
        i += 12 + length
        if ct == 'IEND': break

    ihdr = chunks['IHDR']
    w, h = struct.unpack('>II', ihdr[0:8])
    bit_depth, color_type = ihdr[8], ihdr[9]
    raw = zlib.decompress(chunks['IDAT'])

    bpp = 4 if color_type in (4, 6) else (3 if color_type == 2 else 1)

    def recon(a, b, c):
        p = a + b - c
        pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
        return a if pa <= pb and pa <= pc else (b if pb <= pc else c)

    stride = 1 + w * bpp
    prev = [0] * (w * bpp)
    pixels = []

    for y in range(h):
        row_start = y * stride
        ft = raw[row_start]
        filtered = list(raw[row_start + 1 : row_start + stride])

        rec = [0] * (w * bpp)
        for x in range(w * bpp):
            a = rec[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            v = filtered[x]
            if ft == 0: pass
            elif ft == 1: v = (v + a) & 0xFF
            elif ft == 2: v = (v + b) & 0xFF
            elif ft == 3: v = (v + (a + b) // 2) & 0xFF
            elif ft == 4: v = (v + recon(a, b, c)) & 0xFF
            rec[x] = v

        for x in range(w):
            px_start = x * bpp
            r, g, b = rec[px_start], rec[px_start+1], rec[px_start+2]
            a = rec[px_start+3] if bpp == 4 else 255
            pixels.append((r, g, b, a))
        prev = rec[:]

    return w, h, pixels


def compare_pngs(cairo_path, gpu_path, tolerance=8):
    """Compare two PNGs, return (pass, total_pixels, max_diff, similarity, mean_diff)."""
    w1, h1, p1 = read_png(cairo_path)
    w2, h2, p2 = read_png(gpu_path)

    if (w1, h1) != (w2, h2):
        return False, 0, 255, 0.0, 255, f"Dimension mismatch: ({w1}x{h1}) vs ({w2}x{h2})"

    total = w1 * h1
    max_diff = 0
    sum_diff = 0
    diff_pixels = 0

    for i in range(total):
        r1, g1, b1, a1 = p1[i]
        r2, g2, b2, a2 = p2[i]
        dr = abs(r1 - r2)
        dg = abs(g1 - g2)
        db = abs(b1 - b2)
        da = abs(a1 - a2)
        d = max(dr, dg, db, da)
        if d > max_diff:
            max_diff = d
        sum_diff += (dr + dg + db + da) / 4.0
        if d > tolerance:
            diff_pixels += 1

    mean_diff = sum_diff / total
    similar_pixels = total - diff_pixels
    similarity = similar_pixels / total if total > 0 else 0.0

    passed = similarity >= 0.99
    detail = (
        f"  similarity: {similarity:.6f}  ({'>=' if passed else '<'} 0.99, tol={tolerance})\n"
        f"  pixels diff >{tolerance}: {diff_pixels}/{total} ({100*diff_pixels/total:.2f}%)\n"
        f"  max per-channel diff: {max_diff}/255\n"
        f"  mean per-channel diff: {mean_diff:.3f}/255"
    )
    return passed, total, max_diff, similarity, mean_diff, detail


def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
    cairo_dir = os.path.join(os.path.dirname(__file__), 'out')
    gpu_base = os.path.join(repo_root, 'reports', 'gpu-renderer-scenes', 'offscreen')

    scenes = [
        ('scissor-overlay', True, 'Has Clip + FillRects'),
        ('rounded-rect-solids', True, 'Has 3x FillRRect varying radii'),
        ('linear-gradient-lanes', True, 'Has 3x LinearGradient'),
        ('translucent-card-overlap', True, 'Has overlapping alpha FillRects'),
        ('savelayer-isolated', True, 'Has SaveLayer with content+shadow card'),
        ('savelayer-group-alpha', True, 'Has SaveLayer with groupAlpha=0.5'),
    ]

    all_pass = True
    for scene_id, has_cairo, desc in scenes:
        cairo_png = os.path.join(cairo_dir, f'{scene_id}.png')
        gpu_png = os.path.join(gpu_base, scene_id, 'render.png')

        print(f'\n=== {scene_id} ===')
        print(f'  {desc}')

        if not os.path.exists(cairo_png):
            print(f'  SKIP: Cairo PNG not found: {cairo_png}')
            continue
        if not os.path.exists(gpu_png):
            print(f'  SKIP: GPU render.png not found: {gpu_png}')
            print(f'  (Cairo output available at {cairo_png})')
            continue

        passed, total, max_diff, sim, mean, detail = compare_pngs(cairo_png, gpu_png)
        if passed:
            print(f'  PASS')
        else:
            print(f'  FAIL')
            all_pass = False
        print(detail)

    if all_pass:
        print(f'\n{"="*50}\nALL SCENES PASSED\n{"="*50}')
    else:
        print(f'\n{"="*50}\nSOME SCENES FAILED\n{"="*50}')

    return 0 if all_pass else 1


if __name__ == '__main__':
    sys.exit(main())
