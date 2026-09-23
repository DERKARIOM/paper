import pytest
import torch
from torch.utils.data import DataLoader

from training.docquad_m2.model import DocQuadNet256
from training.docquad_m2.synth_dataset import SyntheticDocQuadDataset, collate_with_meta
from training.docquad_m2.train_loop import overfit_sanity


def test_docquadnet_forward_shapes_large_cpu():
    model = DocQuadNet256(backbone="large", fpn_channels=96)
    x = torch.zeros(2, 3, 256, 256, dtype=torch.float32)
    out = model(x)
    assert out.mask_logits.shape == (2, 1, 64, 64)
    assert out.corner_heatmaps.shape == (2, 4, 64, 64)


@pytest.mark.parametrize("phase", [1, 2])
def test_overfit_sanity_loss_falls_on_synthetic_miniset_cpu(phase: int):
    # For the overfit sanity we use MobileNetV3-Small so that the test runs quickly on CPU.
    model = DocQuadNet256(backbone="small", fpn_channels=64)
    ds = SyntheticDocQuadDataset(n=8, seed=0, sigma=2.0)
    loader = DataLoader(ds, batch_size=4, shuffle=True, collate_fn=collate_with_meta)

    stats = overfit_sanity(model, loader, phase=phase, steps=20, lr=5e-3, device="cpu")
    # "significantly drop": conservatively at least 25% reduction in short runtime.
    assert stats.loss_end <= stats.loss_start * 0.75, (stats.loss_start, stats.loss_end)
