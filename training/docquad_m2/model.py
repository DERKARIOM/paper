from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Tuple

import torch
import torch.nn as nn
import torch.nn.functional as F
import torchvision


@dataclass(frozen=True)
class DocQuadNetOutputs:
    mask_logits: torch.Tensor
    corner_heatmaps: torch.Tensor


class _ConvBNAct(nn.Sequential):
    def __init__(self, in_ch: int, out_ch: int, k: int = 3, act: nn.Module | None = None):
        if act is None:
            act = nn.ReLU(inplace=True)
        p = k // 2
        super().__init__(
            nn.Conv2d(in_ch, out_ch, kernel_size=k, padding=p, bias=False),
            nn.BatchNorm2d(out_ch),
            act,
        )


class _LightFPN(nn.Module):
    """Very lightweight FPN (top-down, bilinear upsample, add).

    Expects feature maps with resolutions (64,32,16,8) or (64,32,16).
    Returns a 64×64 feature map.
    """

    def __init__(self, c2_ch: int, c3_ch: int, c4_ch: int, c5_ch: int | None, fpn_ch: int):
        super().__init__()
        self.lat2 = nn.Conv2d(c2_ch, fpn_ch, kernel_size=1)
        self.lat3 = nn.Conv2d(c3_ch, fpn_ch, kernel_size=1)
        self.lat4 = nn.Conv2d(c4_ch, fpn_ch, kernel_size=1)
        self.lat5 = nn.Conv2d(c5_ch, fpn_ch, kernel_size=1) if c5_ch is not None else None

        self.smooth2 = _ConvBNAct(fpn_ch, fpn_ch, k=3)

    def forward(
        self,
        c2: torch.Tensor,
        c3: torch.Tensor,
        c4: torch.Tensor,
        c5: torch.Tensor | None,
    ) -> torch.Tensor:
        p4 = self.lat4(c4)
        p3 = self.lat3(c3) + F.interpolate(p4, size=c3.shape[-2:], mode="bilinear", align_corners=False)
        p2 = self.lat2(c2) + F.interpolate(p3, size=c2.shape[-2:], mode="bilinear", align_corners=False)

        if self.lat5 is not None and c5 is not None:
            p5 = self.lat5(c5)
            p4 = p4 + F.interpolate(p5, size=c4.shape[-2:], mode="bilinear", align_corners=False)
            # p3/p2 were already computed; we propagate p5 correction deterministically via p4→p3→p2
            p3 = self.lat3(c3) + F.interpolate(p4, size=c3.shape[-2:], mode="bilinear", align_corners=False)
            p2 = self.lat2(c2) + F.interpolate(p3, size=c2.shape[-2:], mode="bilinear", align_corners=False)

        return self.smooth2(p2)


class DocQuadNet256(nn.Module):
    """DocQuadNet-256 (M2): MobileNetV3 + lightweight FPN + 2 heads.

    - `mask_logits`: logits (no sigmoid)
    - `corner_heatmaps`: logits (no sigmoid)
    """

    def __init__(self, backbone: str = "large", fpn_channels: int = 128):
        super().__init__()

        if backbone == "large":
            self.backbone = torchvision.models.mobilenet_v3_large(weights=None)
        elif backbone == "small":
            self.backbone = torchvision.models.mobilenet_v3_small(weights=None)
        else:
            raise ValueError("backbone must be 'large' or 'small'")

        # We only use `features` (classifier is not needed).
        self.backbone_features = self.backbone.features

        # Channel counts depend on the torchvision model. We infer them once via dummy forward.
        with torch.no_grad():
            ch = _infer_backbone_channels(self.backbone_features)

        self.fpn = _LightFPN(
            c2_ch=ch.c2,
            c3_ch=ch.c3,
            c4_ch=ch.c4,
            c5_ch=ch.c5,
            fpn_ch=fpn_channels,
        )

        self.mask_head = nn.Sequential(
            _ConvBNAct(fpn_channels, fpn_channels, k=3),
            nn.Conv2d(fpn_channels, 1, kernel_size=1),
        )
        self.corner_head = nn.Sequential(
            _ConvBNAct(fpn_channels, fpn_channels, k=3),
            nn.Conv2d(fpn_channels, 4, kernel_size=1),
        )

    def forward(self, x: torch.Tensor) -> DocQuadNetOutputs:
        if x.ndim != 4 or x.shape[1:] != (3, 256, 256):
            raise ValueError("input must have shape [B, 3, 256, 256]")

        feats = _collect_last_features_by_hw(self.backbone_features, x)
        # We expect at least these scales.
        c2 = feats[(64, 64)]
        c3 = feats[(32, 32)]
        c4 = feats[(16, 16)]
        c5 = feats.get((8, 8), None)

        p2 = self.fpn(c2, c3, c4, c5)
        mask_logits = self.mask_head(p2)
        corner_heatmaps = self.corner_head(p2)

        # Shapes must be exactly 64×64.
        if mask_logits.shape[-2:] != (64, 64) or corner_heatmaps.shape[-2:] != (64, 64):
            raise RuntimeError("internal error: expected 64×64 outputs")

        return DocQuadNetOutputs(mask_logits=mask_logits, corner_heatmaps=corner_heatmaps)


@dataclass(frozen=True)
class _BackboneChannels:
    c2: int
    c3: int
    c4: int
    c5: int | None


def _collect_last_features_by_hw(features: nn.Module, x: torch.Tensor) -> Dict[Tuple[int, int], torch.Tensor]:
    """Collects the last feature map per spatial resolution.

    This avoids fragile, version-dependent layer indices.
    """
    out: Dict[Tuple[int, int], torch.Tensor] = {}
    y = x
    for layer in features:
        y = layer(y)
        hw = (int(y.shape[-2]), int(y.shape[-1]))
        out[hw] = y
    return out


def _infer_backbone_channels(features: nn.Module) -> _BackboneChannels:
    dummy = torch.zeros(1, 3, 256, 256)
    feats = _collect_last_features_by_hw(features, dummy)
    # Minimum requirements for FPN (64/32/16). 8 is optional.
    for hw in [(64, 64), (32, 32), (16, 16)]:
        if hw not in feats:
            raise RuntimeError(f"backbone does not expose expected feature resolution {hw}")

    c2 = int(feats[(64, 64)].shape[1])
    c3 = int(feats[(32, 32)].shape[1])
    c4 = int(feats[(16, 16)].shape[1])
    c5 = int(feats[(8, 8)].shape[1]) if (8, 8) in feats else None
    return _BackboneChannels(c2=c2, c3=c3, c4=c4, c5=c5)
