#!/usr/bin/env python3
"""Quick sanity check for Cairo reference PNGs."""

import struct, zlib, sys, os

def read_png_rgb(path):
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
    color_type = ihdr[9]
    raw = zlib.decompress(chunks['IDAT'])
    bpp = 3 if color_type == 2 else 4
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
            pixels.append((rec[px_start], rec[px_start+1], rec[px_start+2]))
        prev = rec[:]
    return w, h, pixels

def check(path):
    if not os.path.exists(path):
        print(f'  NOT FOUND')
        return False
    w, h, px = read_png_rgb(path)
    cx, cy = w // 2, h // 2
    r, g, b = px[cy * w + cx]
    nonzero = sum(1 for p in px if p != (0,0,0))
    pct = 100 * nonzero // len(px) if len(px) > 0 else 0
    print(f'  {w}x{h}  center=({r},{g},{b})  non-black={pct}%')
    return nonzero > 0

if __name__ == '__main__':
    scenes = sys.argv[1:] if len(sys.argv) > 1 else [
        'savelayer-isolated', 'savelayer-group-alpha', 'simple-scene',
        'scissor-overlay', 'rounded-rect-solids', 'linear-gradient-lanes',
        'translucent-card-overlap',
    ]
    base = os.path.join(os.path.dirname(__file__), 'out')
    all_ok = True
    for name in scenes:
        path = os.path.join(base, f'{name}.png')
        print(f'--- {name} ---')
        if not check(path):
            all_ok = False
    sys.exit(0 if all_ok else 1)
