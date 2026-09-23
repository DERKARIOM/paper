#!/usr/bin/env python3
"""Analyze CORD receipt images and find narrow ones."""
import argparse
from PIL import Image
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description='Analyze CORD receipt aspect ratios')
    parser.add_argument('--cord-dir', type=str, default='training/data/CORD',
                        help='Path to CORD dataset')
    parser.add_argument('--min-aspect', type=float, default=2.0,
                        help='Minimum h/w aspect ratio to consider as narrow')
    parser.add_argument('--top-n', type=int, default=20,
                        help='Show top N narrowest receipts')
    args = parser.parse_args()

    cord_dir = Path(args.cord_dir)
    
    # Collect all images and their aspect ratios
    images = []
    for split in ['train', 'test', 'dev']:
        img_dir = cord_dir / split / 'image'
        if img_dir.exists():
            for img_path in img_dir.glob('*.png'):
                try:
                    with Image.open(img_path) as img:
                        w, h = img.size
                        aspect = h / w  # height/width ratio (>1 = narrow/tall)
                        images.append({
                            'path': str(img_path),
                            'name': img_path.name,
                            'split': split,
                            'width': w,
                            'height': h,
                            'aspect': aspect
                        })
                except Exception as e:
                    print(f'Error reading {img_path}: {e}')

    print(f'Found: {len(images)} images')
    print()

    # Sort by aspect ratio (highest = narrowest)
    images.sort(key=lambda x: x['aspect'], reverse=True)

    print(f'=== TOP {args.top_n} NARROWEST RECEIPTS (highest h/w ratio) ===')
    print()
    for img in images[:args.top_n]:
        print(f"  {img['aspect']:.2f}  {img['width']}x{img['height']}  {img['split']}/{img['name']}")

    print()
    print('=== STATISTICS ===')
    ratios = [x['aspect'] for x in images]
    print(f'Aspect Ratio (h/w): min={min(ratios):.2f}, max={max(ratios):.2f}, mean={sum(ratios)/len(ratios):.2f}')

    # Count narrow receipts
    narrow = [x for x in images if x['aspect'] >= args.min_aspect]
    print(f'Narrow receipts (h/w >= {args.min_aspect}): {len(narrow)}')
    
    print()
    print('=== NARROW RECEIPT PATHS ===')
    for img in narrow[:50]:
        print(img['path'])


if __name__ == '__main__':
    main()
