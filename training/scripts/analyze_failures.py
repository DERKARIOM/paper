#!/usr/bin/env python3
"""Analyze failures in evaluation report."""
import json
import sys

report_path = sys.argv[1] if len(sys.argv) > 1 else 'training/reports/uvdoc_vs_v1mix5000_product.json'

with open(report_path) as f:
    data = json.load(f)

# Detect available model keys
model_keys = list(data['models'].keys())
print(f'Available models: {model_keys}')

# Find the "mix" model (could be named 'mix', 'v1mix1600', etc.)
# Use the first non-uvdoc model as the "mix" model
mix_key = None
uvdoc_key = None
for key in model_keys:
    if 'uvdoc' in key.lower():
        uvdoc_key = key
    else:
        mix_key = key

if mix_key is None:
    # If no non-uvdoc model found, try to find one with 'mix' in the name
    for key in model_keys:
        if 'mix' in key.lower():
            mix_key = key
            break

if mix_key is None or uvdoc_key is None:
    print(f'Error: Could not identify mix and uvdoc models from keys: {model_keys}')
    print('Expected one model with "uvdoc" in name and one other model.')
    sys.exit(1)

print(f'Using uvdoc model: {uvdoc_key}')
print(f'Using mix model: {mix_key}')
print()

# Analyze mix model failures
mix_samples = data['models'][mix_key]['samples']
uvdoc_samples = data['models'][uvdoc_key]['samples']

# Count failures by chosen_source for mix
mix_fail_corners = 0
mix_fail_mask = 0
mix_fail_images = []

for s in mix_samples:
    if s.get('fail'):
        repairs = s.get('repairs_applied', [])
        source = 'unknown'
        for r in repairs:
            if 'chosen_source:' in r:
                source = r.split(':')[1]
        if source == 'MASK':
            mix_fail_mask += 1
        elif source == 'CORNERS':
            mix_fail_corners += 1
        mix_fail_images.append((s['image'], s.get('fail_reason'), source))

print('=== MIX Model Failures ===')
print(f'Total failures: {mix_fail_corners + mix_fail_mask}')
print(f'  from CORNERS: {mix_fail_corners}')
print(f'  from MASK: {mix_fail_mask}')
print()

# Compare with uvdoc
uvdoc_fail_images = set()
for s in uvdoc_samples:
    if s.get('fail'):
        uvdoc_fail_images.add(s['image'])

mix_fail_images_set = set(img for img, _, _ in mix_fail_images)

# New failures in mix (not in uvdoc)
new_failures = mix_fail_images_set - uvdoc_fail_images
print(f'=== New failures in mix (not in uvdoc): {len(new_failures)} ===')
new_from_mask = 0
new_from_corners = 0
for img, reason, source in mix_fail_images:
    if img in new_failures:
        print(f'  {img}: {reason} (source: {source})')
        if source == 'MASK':
            new_from_mask += 1
        else:
            new_from_corners += 1

print()
print(f'New failures from MASK: {new_from_mask}')
print(f'New failures from CORNERS: {new_from_corners}')
