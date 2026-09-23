import numpy as np
import pytest

from training.docquad_m1.letterbox import LetterboxTransform
from training.docquad_m1.targets import generate_corner_heatmaps_64, generate_mask_target_64


def test_letterbox_landscape_scale_offsets_and_roundtrip():
    t = LetterboxTransform.create(400, 200, 256, 256)
    assert t.scale == pytest.approx(0.64)
    assert t.offset_x == pytest.approx(0.0)
    assert t.offset_y == pytest.approx(64.0)

    p = np.array([200.0, 100.0], dtype=np.float32)
    q = t.forward(p)
    assert q[0] == pytest.approx(128.0)
    assert q[1] == pytest.approx(128.0)

    p2 = t.inverse(q)
    assert p2[0] == pytest.approx(float(p[0]), abs=1e-5)
    assert p2[1] == pytest.approx(float(p[1]), abs=1e-5)


def test_letterbox_portrait_scale_offsets():
    t = LetterboxTransform.create(200, 400, 256, 256)
    assert t.scale == pytest.approx(0.64)
    assert t.offset_x == pytest.approx(64.0)
    assert t.offset_y == pytest.approx(0.0)

    p = np.array([100.0, 200.0], dtype=np.float32)
    q = t.forward(p)
    assert q[0] == pytest.approx(128.0)
    assert q[1] == pytest.approx(128.0)


def test_letterbox_forward_inverse_random_points_max_error_small():
    rng = np.random.default_rng(0)
    src_w, src_h = 4032, 3024
    t = LetterboxTransform.create(src_w, src_h, 256, 256)

    pts = np.empty((1000, 2), dtype=np.float32)
    pts[:, 0] = rng.random(1000, dtype=np.float32) * (src_w - 1)
    pts[:, 1] = rng.random(1000, dtype=np.float32) * (src_h - 1)

    pts2 = t.inverse(t.forward(pts))
    max_err = float(np.max(np.abs(pts2.astype(np.float64) - pts.astype(np.float64))))
    assert max_err <= 1e-4, f"max_err={max_err}"


def test_corner_heatmaps_argmax_near_corners_div4_in_64_space():
    corners256 = np.array(
        [
            [64.0, 64.0],
            [192.0, 64.0],
            [192.0, 192.0],
            [64.0, 192.0],
        ],
        dtype=np.float32,
    )

    hm = generate_corner_heatmaps_64(corners256, sigma=2.0)
    assert hm.shape == (4, 64, 64)

    expected64 = corners256.astype(np.float64) / 4.0
    for c in range(4):
        peak = int(np.argmax(hm[c]))
        py, px = divmod(peak, 64)
        # Heatmap grid is located at pixel centers (i+0.5, j+0.5)
        x64 = float(px) + 0.5
        y64 = float(py) + 0.5
        assert abs(x64 - float(expected64[c, 0])) <= 0.5 + 1e-6
        assert abs(y64 - float(expected64[c, 1])) <= 0.5 + 1e-6


def test_corner_heatmaps_peak_value_is_one_when_corner_hits_pixel_center():
    # 66/4 = 16.5 → hits pixel center exactly.
    corners256 = np.array(
        [
            [66.0, 66.0],
            [190.0, 66.0],
            [190.0, 190.0],
            [66.0, 190.0],
        ],
        dtype=np.float32,
    )

    hm = generate_corner_heatmaps_64(corners256, sigma=2.0)
    tl = hm[0]
    peak = int(np.argmax(tl))
    py, px = divmod(peak, 64)
    assert px == 16
    assert py == 16
    assert float(tl[py, px]) == pytest.approx(1.0, abs=1e-6)


def test_mask_target_axis_aligned_rectangle_expected_area():
    corners256 = np.array(
        [
            [64.0, 64.0],
            [192.0, 64.0],
            [192.0, 192.0],
            [64.0, 192.0],
        ],
        dtype=np.float32,
    )

    mask = generate_mask_target_64(corners256)
    assert mask.shape == (64, 64)
    assert int(mask.sum()) == 32 * 32


def test_mask_target_full_image_all_ones():
    corners256 = np.array(
        [
            [0.0, 0.0],
            [255.0, 0.0],
            [255.0, 255.0],
            [0.0, 255.0],
        ],
        dtype=np.float32,
    )
    mask = generate_mask_target_64(corners256)
    assert int(mask.sum()) == 64 * 64
