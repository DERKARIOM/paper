
#!/usr/bin/env python3
"""
CJK OTF → TTF (PDFBox friendly) with automatic pre-subset to avoid 65k glyph limit.
Also supports TTF input and optional subsetting.

Requirements:
  - fonttools (pyftsubset/ttx): pip install fonttools  OR  brew install fonttools
  - fontforge CLI:               brew install fontforge

Typical usage:
  # SC: OTF -> [subset OTF] -> TTF (no second subset)
  python build_noto_cjk_ttf_v3.py --in NotoSansCJKsc-Regular.otf --out NotoSansSC.ttf --preset sc-min --no-second-subset

  # TC: OTF -> [subset OTF] -> TTF -> subset TTF
  python build_noto_cjk_ttf_v3.py --in NotoSansCJKtc-Regular.otf --out NotoSansTC.ttf --preset tc-min

  # Use real text for exact subset
  python build_noto_cjk_ttf_v3.py --in NotoSansCJKsc-Regular.otf --out NotoSansSC.ttf --text-file mychars.txt --no-second-subset
"""
import argparse
import os
import sys
import shutil
import subprocess
import tempfile
from fontTools.ttLib import TTFont
from fontTools.subset import main as pyftsubset_main

PRESETS = {
    # Minimal but useful ranges (adjust if needed).
    "sc-min": "U+0020-007E,U+00A0-00FF,U+2000-206F,U+3000-303F,U+FF00-FFEF,U+4E00-9FFF",
    "tc-min": "U+0020-007E,U+00A0-00FF,U+2000-206F,U+3000-303F,U+FF00-FFEF,U+4E00-9FFF",
    "sc-bmp": "U+0020-007E,U+00A0-00FF,U+2000-206F,U+3000-303F,U+FF00-FFEF,U+4E00-9FFF,U+3400-4DBF",
    "tc-bmp": "U+0020-007E,U+00A0-00FF,U+2000-206F,U+3000-303F,U+FF00-FFEF,U+4E00-9FFF,U+3400-4DBF",
}

def have(exe):
    return shutil.which(exe) is not None

def run_pyftsubset(args_list):
    # Programmatic call without 'pyftsubset' token
    pyftsubset_main(args_list)

def ff_convert_otf_to_ttf(otf_path, ttf_path):
    ff = shutil.which("fontforge")
    if not ff:
        raise RuntimeError("FontForge CLI not found. Install with: brew install fontforge")
    script = (
        "import fontforge, sys\n"
        "f = fontforge.open(sys.argv[1])\n"
        "try:\n"
        "    # Flatten CID if present\n"
        "    if getattr(f, 'cidfontname', None):\n"
        "        f.cidFlatten()\n"
        "except Exception:\n"
        "    pass\n"
        "f.encoding = 'UnicodeFull'\n"
        "for g in f.glyphs():\n"
        "    try:\n"
        "        g.unlinkRef()\n"
        "    except Exception:\n"
        "        pass\n"
        "f.generate(sys.argv[2])\n"
    )
    cmd = [ff, "-lang=py", "-c", script, otf_path, ttf_path]
    subprocess.run(cmd, check=True)

def check_ttf_tables(path):
    try:
        tf = TTFont(path)
        has_glyf = 'glyf' in tf
        has_loca = 'loca' in tf
        num_glyphs = tf['maxp'].numGlyphs if 'maxp' in tf else -1
        tf.close()
        return has_glyf and has_loca, has_glyf, has_loca, num_glyphs
    except Exception as e:
        return False, False, False, -1

def human_size(n):
    for unit in ["B","KB","MB","GB"]:
        if n < 1024.0:
            return f"{n:3.1f} {unit}"
        n /= 1024.0
    return f"{n:.1f} TB"

def main():
    ap = argparse.ArgumentParser(description="Make PDFBox-friendly TTF from CJK OTF, with safe pre-subset to avoid 65k glyph limit.")
    ap.add_argument("--in", dest="in_font", required=True, help="Input font (.otf or .ttf)")
    ap.add_argument("--out", dest="out_font", required=True, help="Output TTF path")
    ap.add_argument("--preset", choices=list(PRESETS.keys()), help="Unicode preset for subsetting (recommended for OTF)")
    ap.add_argument("--unicodes", help="Custom unicode spec like 'U+0020-007E,U+4E00-9FFF'")
    ap.add_argument("--text-file", help="UTF-8 file containing text to keep (overrides preset/unicodes if set)")
    ap.add_argument("--no-second-subset", action="store_true", help="Skip second TTF subset stage (only pre-subset OTF then convert)")
    ap.add_argument("--min-glyphs", type=int, default=500, help="Sanity threshold for output TTF numGlyphs (default 500)")

    args = ap.parse_args()
    in_path = args.in_font
    out_path = args.out_font

    if not os.path.isfile(in_path):
        print(f"ERROR: input not found: {in_path}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)

    ext = os.path.splitext(in_path)[1].lower()
    tmpdir = tempfile.mkdtemp(prefix="cjk2ttf_")

    try:
        if ext == ".otf":
            # Stage A: Pre-subset the OTF (CFF) to reduce glyphs below 65k
            subset_otf = os.path.join(tmpdir, "subset.otf")
            if args.text_file:
                print(f"[A] Pre-subset OTF by text-file → {subset_otf}")
                run_pyftsubset([
                    in_path,
                    "--output-file=" + subset_otf,
                    "--layout-features=*",
                    "--no-hinting",
                    "--desubroutinize",
                    "--text-file=" + args.text_file,
                ])
            else:
                if args.unicodes:
                    u = args.unicodes
                else:
                    # default to sc-min if no preset given
                    u = PRESETS.get(args.preset or "sc-min")
                print(f"[A] Pre-subset OTF by unicodes ({u}) → {subset_otf}")
                run_pyftsubset([
                    in_path,
                    "--output-file=" + subset_otf,
                    "--layout-features=*",
                    "--no-hinting",
                    "--desubroutinize",
                    "--unicodes=" + u,
                ])

            # Stage B: Convert subset OTF -> TTF via FontForge
            ttf_intermediate = os.path.join(tmpdir, "intermediate.ttf")
            print(f"[B] Convert subset OTF → TTF via FontForge: {subset_otf} → {ttf_intermediate}")
            ff_convert_otf_to_ttf(subset_otf, ttf_intermediate)

            ok, has_glyf, has_loca, num_glyphs = check_ttf_tables(ttf_intermediate)
            if not ok or num_glyphs < args.min_glyphs:
                print(f"ERROR: Converted TTF invalid or too few glyphs (glyf={has_glyf}, loca={has_loca}, numGlyphs={num_glyphs}).", file=sys.stderr)
                sys.exit(2)

            # Stage C: Optionally subset the TTF again (typically not needed if Stage A already did it)
            if args.no_second_subset:
                print(f"[C] Skipping second subset. Writing TTF → {out_path}")
                shutil.copyfile(ttf_intermediate, out_path)
            else:
                if args.text_file:
                    print(f"[C] Subset TTF by text-file → {out_path}")
                    run_pyftsubset([
                        ttf_intermediate,
                        "--output-file=" + out_path,
                        "--layout-features=*",
                        "--no-hinting",
                        "--glyph-names",
                        "--retain-gids=no",
                        "--passthrough-tables",
                        "--text-file=" + args.text_file,
                    ])
                else:
                    u2 = args.unicodes or PRESETS.get(args.preset or "sc-min")
                    print(f"[C] Subset TTF by unicodes ({u2}) → {out_path}")
                    run_pyftsubset([
                        ttf_intermediate,
                        "--output-file=" + out_path,
                        "--layout-features=*",
                        "--no-hinting",
                        "--glyph-names",
                        "--retain-gids=no",
                        "--passthrough-tables",
                        "--unicodes=" + u2,
                    ])

        else:
            # Input is already TTF
            if args.text_file or args.unicodes or args.preset:
                # Subset TTF directly
                if args.text_file:
                    print(f"[T] Subset TTF by text-file → {out_path}")
                    run_pyftsubset([
                        in_path,
                        "--output-file=" + out_path,
                        "--layout-features=*",
                        "--no-hinting",
                        "--glyph-names",
                        "--retain-gids=no",
                        "--passthrough-tables",
                        "--text-file=" + args.text_file,
                    ])
                else:
                    u = args.unicodes or PRESETS.get(args.preset or "sc-min")
                    print(f"[T] Subset TTF by unicodes ({u}) → {out_path}")
                    run_pyftsubset([
                        in_path,
                        "--output-file=" + out_path,
                        "--layout-features=*",
                        "--no-hinting",
                        "--glyph-names",
                        "--retain-gids=no",
                        "--passthrough-tables",
                        "--unicodes=" + u,
                    ])
            else:
                print(f"[T] Copy TTF as-is → {out_path}")
                shutil.copyfile(in_path, out_path)

        # Final verification
        ok, has_glyf, has_loca, num_glyphs = check_ttf_tables(out_path)
        if not ok or num_glyphs < args.min_glyphs:
            print(f"[✓] Output path: {out_path}")
            print(f"[X] Verification failed: glyf={has_glyf}, loca={has_loca}, numGlyphs={num_glyphs} (< {args.min_glyphs}).", file=sys.stderr)
            sys.exit(3)

        print("[✓] Success.")
        print(f"Output: {out_path}")
        try:
            in_size = os.path.getsize(in_path)
            out_size = os.path.getsize(out_path)
            print(f"Size: {human_size(in_size)} → {human_size(out_size)}")
        except Exception:
            pass

    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)

if __name__ == "__main__":
    main()
