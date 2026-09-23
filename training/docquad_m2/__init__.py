"""M2 (Model/Training) for DocQuadNet-256.

Important (Specification):
- Input: NCHW `[B, 3, 256, 256]`, float32, RGB 0..1
- Outputs:
  - `mask_logits`: `[B, 1, 64, 64]` (logits; sigmoid only in postprocessing)
  - `corner_heatmaps`: `[B, 4, 64, 64]` (value range 0..1; for peak finding)
"""
