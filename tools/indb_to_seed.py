#!/usr/bin/env -S uv run --quiet --with openpyxl python
"""Convert the Indian Nutrient Databank into the app's bundled dish seed.

INDB (Vijayakumar A, Dubasi HB, Awasthi A, Jaacks LM. Development of an Indian Food
Composition Database. Curr Dev Nutr. 2024;8(7):103790) is CC BY 4.0 and carries 1,014
cooked Indian recipes with nutrients per 100 g and per serving. That cooked-dish layer is
the part no other free dataset provides.

Usage:  tools/indb_to_seed.py <path-to-INDB-repo> <output.json>
"""

from __future__ import annotations


import json
import re
import sys
from pathlib import Path

import openpyxl

SOURCE = "INDB"
CITATION = (
    "Vijayakumar A, Dubasi HB, Awasthi A, Jaacks LM. Development of an Indian Food "
    "Composition Database. Curr Dev Nutr. 2024;8(7):103790. CC BY 4.0."
)

# Per-100 g columns we keep. INDB carries 40+ nutrients; the app tracks these.
PER_100G = {
    "kcal": "energy_kcal",
    "protein": "protein_g",
    "carbs": "carb_g",
    "fat": "fat_g",
    "fibre": "fibre_g",
    "sugar": "freesugar_g",
    "sodium_mg": "sodium_mg",
}

SERVING_PREFIX = "unit_serving_"

# Used to back out serving weight from the per-serving and per-100 g values, in descending
# order of reliability. Energy is near-always present; water-based dishes need a fallback.
SCALE_KEYS = ["energy_kcal", "carb_g", "protein_g", "fat_g"]

# INDB counts the entire deep-frying oil as absorbed into the dish, which wrecks every fried
# recipe: its bhatura is 793 kcal/100 g at 83 g fat and 1.6 g protein, for a wheat bread.
# About 23% of rows are affected and they are internally consistent, so an energy-vs-macros
# check does not catch them. These bounds do. Rejected dishes are reported, not silently
# dropped, and the common ones are re-added from cited sources instead.
MAX_KCAL_100G = 600.0
MIN_KCAL_100G = 20.0
MAX_FAT_100G = 35.0
MAX_SERVING_KCAL = 1200.0
MIN_SERVING_KCAL = 10.0
MAX_SERVING_G = 600.0
MIN_SERVING_G = 15.0
ATWATER_TOLERANCE = 0.15


def implausible(per_100g: dict, serving_g: float | None) -> str | None:
    """Returns why a row should be rejected, or None if it looks like real food."""
    kcal = per_100g.get("kcal")
    if kcal is None or not (MIN_KCAL_100G <= kcal <= MAX_KCAL_100G):
        return f"kcal/100g out of range ({kcal})"

    fat = per_100g.get("fat")
    if fat is not None and fat > MAX_FAT_100G:
        return f"fat {fat}g/100g suggests uncounted frying oil"

    carbs, protein = per_100g.get("carbs"), per_100g.get("protein")
    if None not in (carbs, protein, fat):
        atwater = 4 * carbs + 4 * protein + 9 * fat
        if abs(atwater - kcal) / kcal > ATWATER_TOLERANCE:
            return f"macros imply {atwater:.0f} kcal, row says {kcal:.0f}"

    if serving_g is not None and not (MIN_SERVING_G <= serving_g <= MAX_SERVING_G):
        return f"serving weight {serving_g}g implausible"

    if serving_g is not None:
        serving_kcal = kcal * serving_g / 100.0
        if not (MIN_SERVING_KCAL <= serving_kcal <= MAX_SERVING_KCAL):
            return f"serving is {serving_kcal:.0f} kcal"

    return None


def number(value) -> float | None:
    """INDB uses 'NA' and 'Tr' for missing and trace values."""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip()
    if not text or text.upper() in {"NA", "N", "TR", "-"}:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def split_names(raw: str) -> tuple[str, list[str]]:
    """'Hot tea (Garam Chai)' -> ('Hot tea', ['Garam Chai'])."""
    name = " ".join(str(raw).split())
    match = re.match(r"^(.*?)\s*\(([^()]*)\)\s*$", name)
    if not match:
        return name, []

    base = match.group(1).strip()
    alts = [
        part.strip()
        for part in re.split(r"[/,;]| or ", match.group(2))
        if part.strip() and part.strip().lower() != base.lower()
    ]
    return (base or name), alts


def serving_grams(row: dict) -> float | None:
    """Back out the serving weight: per-serving value over per-100 g value, times 100."""
    for key in SCALE_KEYS:
        per_100g = number(row.get(key))
        per_serving = number(row.get(SERVING_PREFIX + key))
        if per_100g and per_serving and per_100g > 0 and per_serving > 0:
            grams = per_serving / per_100g * 100.0
            if 5.0 <= grams <= 2000.0:
                return round(grams, 1)
    return None


def convert(repo: Path) -> list[dict]:
    workbook = openpyxl.load_workbook(repo / "INDB.xlsx", read_only=True)
    sheet = workbook["Nutrient Data"]

    rows = sheet.iter_rows(values_only=True)
    header = [str(c).strip() if c is not None else "" for c in next(rows)]

    dishes: list[dict] = []
    rejected: list[tuple[str, str]] = []

    for values in rows:
        row = dict(zip(header, values))
        raw_name = row.get("food_name")
        if not raw_name:
            continue

        name, alt_names = split_names(raw_name)
        grams = serving_grams(row)
        unit = (row.get("servings_unit") or "").strip()

        per_100g = {}
        for out_key, in_key in PER_100G.items():
            value = number(row.get(in_key))
            if value is not None:
                per_100g[out_key] = round(value, 2)

        reason = implausible(per_100g, grams)
        if reason:
            rejected.append((name, reason))
            continue

        dishes.append(
            {
                "name": name,
                "alt_names": alt_names,
                # INDB rows are cooked recipes; the app has no finer category for them and
                # inventing one would be fake precision.
                "category": "COMPOSITE",
                "source": SOURCE,
                "source_ref": str(row.get("food_code") or "").strip(),
                "is_composite": True,
                "per_100g": per_100g,
                "default_portion_g": grams if grams else 100.0,
                "portion_label": f"1 {unit}" if unit else None,
            }
        )

    workbook.close()
    return dishes, rejected


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    repo, out_path = Path(sys.argv[1]), Path(sys.argv[2])
    dishes, rejected = convert(repo)

    payload = {"source": SOURCE, "citation": CITATION, "licence": "CC BY 4.0", "foods": dishes}
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))

    with_portion = sum(1 for d in dishes if d["portion_label"])
    total = len(dishes) + len(rejected)
    print(f"{len(dishes)} of {total} dishes -> {out_path} ({out_path.stat().st_size / 1024:.0f} KB)")
    print(f"  {with_portion} carry a household portion label")
    print(f"  {len(rejected)} rejected as implausible")

    # Beside the tool, not beside the output: assets/ ships inside the APK.
    report = Path(__file__).resolve().parent / "rejected_dishes.txt"
    report.write_text(
        "\n".join(f"{name}\t{reason}" for name, reason in sorted(rejected)),
        encoding="utf-8",
    )
    print(f"  rejections listed in {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
