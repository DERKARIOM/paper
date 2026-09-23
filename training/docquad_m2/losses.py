from __future__ import annotations

import torch
import torch.nn.functional as F


def corners_bce_logits_loss(pred_corner_logits: torch.Tensor, target_corner_heatmaps: torch.Tensor) -> torch.Tensor:
    """BCEWithLogits auf Corner-Heatmaps.

    Spezifikation (FIX): `corner_heatmaps` sind *Logits* (Sigmoid nur im Postprocessing).
    Targets sind 0/1 bzw. 0..1 Heatmaps.
    """
    if pred_corner_logits.shape != target_corner_heatmaps.shape:
        raise ValueError("pred/target corner shapes must match")
    return F.binary_cross_entropy_with_logits(pred_corner_logits, target_corner_heatmaps)


def mask_bce_logits_loss(pred_mask_logits: torch.Tensor, target_mask: torch.Tensor) -> torch.Tensor:
    if pred_mask_logits.shape != target_mask.shape:
        raise ValueError("pred/target mask shapes must match")
    return F.binary_cross_entropy_with_logits(pred_mask_logits, target_mask)


def mask_iou(pred_mask_logits: torch.Tensor, target_mask: torch.Tensor, thresh: float = 0.5) -> torch.Tensor:
    """IoU als Metrik (nicht als Loss)."""
    pred = (torch.sigmoid(pred_mask_logits) > thresh).to(dtype=torch.float32)
    tgt = (target_mask > 0.5).to(dtype=torch.float32)
    inter = (pred * tgt).sum(dim=(-2, -1))
    union = (pred + tgt - pred * tgt).sum(dim=(-2, -1)).clamp_min(1.0)
    return inter / union
