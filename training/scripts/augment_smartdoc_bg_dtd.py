#!/usr/bin/env python3
"""
Deterministic Background Augmentation for SmartDoc Dataset using DTD textures.

This script replaces the background of SmartDoc document images with DTD texture images
while keeping the document (foreground) unchanged. Labels (corner coordinates) remain identical.

Usage:
    python augment_smartdoc_bg_dtd.py [--global-seed SEED] [--dilate PIXELS] [--feather PIXELS]
"""

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

import cv2
import numpy as np


def get_deterministic_seed(filename: str, global_seed: int) -> int:
    """Generate a deterministic seed from filename and global seed."""
    combined = f"{global_seed}_{filename}"
    hash_bytes = hashlib.sha256(combined.encode('utf-8')).digest()
    return int.from_bytes(hash_bytes[:4], byteorder='big')


def collect_dtd_images(dtd_dir: Path) -> list:
    """Collect all DTD texture images recursively."""
    extensions = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}
    images = []
    for root, _, files in os.walk(dtd_dir):
        for f in files:
            if Path(f).suffix.lower() in extensions:
                images.append(Path(root) / f)
    images.sort(key=lambda p: str(p))
    return images


def create_document_mask(image_shape: tuple, corners: list, dilate_px: int = 0, feather_px: int = 0) -> np.ndarray:
    """
    Create a binary mask for the document region defined by corners.
    
    Args:
        image_shape: (height, width, channels) of the image
        corners: List of 4 corner points [[x0,y0], [x1,y1], [x2,y2], [x3,y3]]
        dilate_px: Optional dilation in pixels (1-2 recommended)
        feather_px: Optional feathering via Gaussian blur (1-3 recommended)
    
    Returns:
        Float mask with values 0.0-1.0, where 1.0 is document area
    """
    h, w = image_shape[:2]
    mask = np.zeros((h, w), dtype=np.uint8)
    
    pts = np.array(corners, dtype=np.int32).reshape((-1, 1, 2))
    cv2.fillPoly(mask, [pts], 255)
    
    if dilate_px > 0:
        kernel_size = 2 * dilate_px + 1
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
        mask = cv2.dilate(mask, kernel, iterations=1)
    
    mask_float = mask.astype(np.float32) / 255.0
    
    if feather_px > 0:
        ksize = 2 * feather_px + 1
        mask_float = cv2.GaussianBlur(mask_float, (ksize, ksize), 0)
    
    return mask_float


def resize_cover(image: np.ndarray, target_width: int, target_height: int, rng: np.random.Generator) -> np.ndarray:
    """
    Resize image using cover strategy (fill entire target, crop excess).
    
    The image is scaled to cover the entire target area, then cropped deterministically.
    Always returns an image with exactly target_width x target_height dimensions.
    """
    src_h, src_w = image.shape[:2]
    
    scale_w = target_width / src_w
    scale_h = target_height / src_h
    scale = max(scale_w, scale_h)
    
    new_w = int(src_w * scale)
    new_h = int(src_h * scale)
    
    # Ensure scaled dimensions are at least as large as target
    new_w = max(new_w, target_width)
    new_h = max(new_h, target_height)
    
    resized = cv2.resize(image, (new_w, new_h), interpolation=cv2.INTER_LINEAR)
    
    if new_w > target_width:
        max_offset = new_w - target_width
        x_offset = rng.integers(0, max_offset + 1)
    else:
        x_offset = 0
    
    if new_h > target_height:
        max_offset = new_h - target_height
        y_offset = rng.integers(0, max_offset + 1)
    else:
        y_offset = 0
    
    cropped = resized[y_offset:y_offset + target_height, x_offset:x_offset + target_width]
    
    # Ensure exact dimensions (safety check for edge cases)
    if cropped.shape[0] != target_height or cropped.shape[1] != target_width:
        cropped = cv2.resize(cropped, (target_width, target_height), interpolation=cv2.INTER_LINEAR)
    
    return cropped


def alpha_blend(foreground: np.ndarray, background: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """
    Alpha blend foreground and background using mask.
    
    Args:
        foreground: Original image (document is foreground)
        background: DTD texture image
        mask: Float mask where 1.0 = foreground, 0.0 = background
    
    Returns:
        Blended image
    """
    mask_3ch = mask[:, :, np.newaxis]
    
    blended = foreground.astype(np.float32) * mask_3ch + background.astype(np.float32) * (1.0 - mask_3ch)
    
    return np.clip(blended, 0, 255).astype(np.uint8)


def process_image(
    image_path: Path,
    corners: list,
    dtd_images: list,
    global_seed: int,
    dilate_px: int,
    feather_px: int
) -> np.ndarray:
    """
    Process a single SmartDoc image with background replacement.
    
    Args:
        image_path: Path to the SmartDoc image
        corners: Document corner coordinates
        dtd_images: List of all DTD image paths
        global_seed: Global seed for determinism
        dilate_px: Mask dilation pixels
        feather_px: Mask feathering pixels
    
    Returns:
        Augmented image with replaced background
    """
    image = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"Could not load image: {image_path}")
    
    seed = get_deterministic_seed(image_path.name, global_seed)
    rng = np.random.default_rng(seed)
    
    dtd_idx = rng.integers(0, len(dtd_images))
    dtd_path = dtd_images[dtd_idx]
    
    dtd_image = cv2.imread(str(dtd_path), cv2.IMREAD_COLOR)
    if dtd_image is None:
        raise ValueError(f"Could not load DTD image: {dtd_path}")
    
    h, w = image.shape[:2]
    background = resize_cover(dtd_image, w, h, rng)
    
    mask = create_document_mask(image.shape, corners, dilate_px, feather_px)
    
    result = alpha_blend(image, background, mask)
    
    return result


def main():
    parser = argparse.ArgumentParser(
        description='Deterministic Background Augmentation for SmartDoc using DTD textures'
    )
    parser.add_argument(
        '--global-seed',
        type=int,
        default=42,
        help='Global seed for deterministic processing (default: 42)'
    )
    parser.add_argument(
        '--dilate',
        type=int,
        default=2,
        help='Mask dilation in pixels (default: 2)'
    )
    parser.add_argument(
        '--feather',
        type=int,
        default=2,
        help='Mask feathering (Gaussian blur) in pixels (default: 2)'
    )
    parser.add_argument(
        '--smartdoc-dir',
        type=str,
        default=None,
        help='Path to SmartDoc images directory'
    )
    parser.add_argument(
        '--labels-file',
        type=str,
        default=None,
        help='Path to SmartDoc labels JSONL file'
    )
    parser.add_argument(
        '--dtd-dir',
        type=str,
        default=None,
        help='Path to DTD images directory'
    )
    parser.add_argument(
        '--output-dir',
        type=str,
        default=None,
        help='Path to output directory'
    )
    
    args = parser.parse_args()
    
    script_dir = Path(__file__).parent.parent
    
    smartdoc_dir = Path(args.smartdoc_dir) if args.smartdoc_dir else script_dir / 'data' / 'smartdoc'
    labels_file = Path(args.labels_file) if args.labels_file else script_dir / 'labels' / 'smartdoc_labels.jsonl'
    dtd_dir = Path(args.dtd_dir) if args.dtd_dir else script_dir / 'data' / 'dtd' / 'images'
    output_dir = Path(args.output_dir) if args.output_dir else script_dir / 'data' / 'smartdoc_bg_aug'
    
    output_images_dir = output_dir / 'images'
    output_labels_file = output_dir / 'labels.jsonl'
    
    if not smartdoc_dir.exists():
        print(f"Error: SmartDoc directory not found: {smartdoc_dir}", file=sys.stderr)
        sys.exit(1)
    
    if not labels_file.exists():
        print(f"Error: Labels file not found: {labels_file}", file=sys.stderr)
        sys.exit(1)
    
    if not dtd_dir.exists():
        print(f"Error: DTD directory not found: {dtd_dir}", file=sys.stderr)
        sys.exit(1)
    
    output_images_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"SmartDoc directory: {smartdoc_dir}")
    print(f"Labels file: {labels_file}")
    print(f"DTD directory: {dtd_dir}")
    print(f"Output directory: {output_dir}")
    print(f"Global seed: {args.global_seed}")
    print(f"Mask dilation: {args.dilate}px")
    print(f"Mask feathering: {args.feather}px")
    print()
    
    print("Collecting DTD images...")
    dtd_images = collect_dtd_images(dtd_dir)
    print(f"Found {len(dtd_images)} DTD texture images")
    
    if len(dtd_images) == 0:
        print("Error: No DTD images found", file=sys.stderr)
        sys.exit(1)
    
    print("Loading labels...")
    labels = []
    with open(labels_file, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line:
                labels.append(json.loads(line))
    print(f"Loaded {len(labels)} labels")
    
    labels.sort(key=lambda x: x['image'])
    
    print()
    print("Processing images...")
    
    output_labels = []
    
    for i, label in enumerate(labels):
        image_name = label['image']
        corners = label['corners']
        
        image_path = smartdoc_dir / image_name
        
        if not image_path.exists():
            print(f"Warning: Image not found, skipping: {image_path}", file=sys.stderr)
            continue
        
        try:
            augmented = process_image(
                image_path,
                corners,
                dtd_images,
                args.global_seed,
                args.dilate,
                args.feather
            )
            
            output_name = Path(image_name).stem + '.png'
            output_path = output_images_dir / output_name
            
            cv2.imwrite(str(output_path), augmented)
            
            output_label = {
                'image': output_name,
                'corners': corners
            }
            output_labels.append(output_label)
            
            if (i + 1) % 100 == 0 or (i + 1) == len(labels):
                print(f"Processed {i + 1}/{len(labels)} images")
                
        except Exception as e:
            print(f"Error processing {image_name}: {e}", file=sys.stderr)
            continue
    
    print()
    print(f"Writing labels to {output_labels_file}...")
    with open(output_labels_file, 'w', encoding='utf-8') as f:
        for label in output_labels:
            f.write(json.dumps(label, ensure_ascii=False) + '\n')
    
    print()
    print("Done!")
    print(f"Output images: {output_images_dir}")
    print(f"Output labels: {output_labels_file}")
    print(f"Total processed: {len(output_labels)} images")


if __name__ == '__main__':
    main()
