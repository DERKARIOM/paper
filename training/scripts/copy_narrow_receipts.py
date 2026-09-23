#!/usr/bin/env python3
"""Copy narrow receipts from CORD datasets to my_receipts folder."""

from PIL import Image
import shutil
import os

out_dir = 'training/data/my_receipts'
os.makedirs(out_dir, exist_ok=True)

copied = []

# CORD 1: Aspect Ratio >= 2.0
cord1_dirs = ['training/data/CORD/train/image', 'training/data/CORD/test/image', 'training/data/CORD/dev/image']
for d in cord1_dirs:
    if not os.path.isdir(d):
        continue
    for f in os.listdir(d):
        if not f.lower().endswith(('.png', '.jpg', '.jpeg')):
            continue
        path = os.path.join(d, f)
        img = Image.open(path)
        w, h = img.size
        aspect = h / w
        if aspect >= 2.0:
            dst = os.path.join(out_dir, f'cord1_{f}')
            shutil.copy2(path, dst)
            copied.append((f'cord1_{f}', aspect))

# CORD 2: Aspect Ratio >= 1.7
cord2_dirs = ['training/data/CORD 2/train/image', 'training/data/CORD 2/test/image', 'training/data/CORD 2/dev/image']
for d in cord2_dirs:
    if not os.path.isdir(d):
        continue
    for f in os.listdir(d):
        if not f.lower().endswith(('.png', '.jpg', '.jpeg')):
            continue
        path = os.path.join(d, f)
        img = Image.open(path)
        w, h = img.size
        aspect = h / w
        if aspect >= 1.7:
            dst = os.path.join(out_dir, f'cord2_{f}')
            shutil.copy2(path, dst)
            copied.append((f'cord2_{f}', aspect))

print(f'Kopiert: {len(copied)} Quittungen nach {out_dir}')
for name, asp in sorted(copied):
    print(f'  {name} (aspect={asp:.2f})')
