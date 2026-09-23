#!/usr/bin/env -S uv run --quiet python
"""Convert USDA FoodData Central into the app's generic-food and Indian-dish seeds.

Two datasets, two jobs:

  SR Legacy (7,793 foods, CSV)  -> foods_usda.json
      Generic and raw ingredients: eggs, milk, oats, chicken, rice, vegetables, nuts.
      Everything INDB does not cover, since INDB ships only composed recipes.

  FNDDS survey foods (JSON)     -> dishes_fndds.json
      The ~20 generic Indian dishes USDA analysed directly, which matter because they
      include the deep-fried items INDB gets wrong. USDA puts samosa at 310 kcal/100 g
      against INDB's 577, and puri at 409 against 738.

Both are public domain (CC0).

Usage:  tools/usda_to_seed.py <sr-legacy-csv-dir> <fndds-json> <out-dir>
"""

from __future__ import annotations

import csv

import json
import re
import sys
from pathlib import Path

CITATION = "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central."

# FoodData Central nutrient ids.
NUTRIENTS = {
    1008: "kcal",
    1003: "protein",
    1005: "carbs",
    1004: "fat",
    1079: "fibre",
    2000: "sugar",
    1093: "sodium_mg",
}

# USDA food groups mapped onto the app's coarse categories. Anything unmapped stays null
# rather than being forced into an ill-fitting bucket.
CATEGORY_MAP = {
    "Dairy and Egg Products": "DAIRY",
    "Cereal Grains and Pasta": "GRAIN",
    "Baked Products": "GRAIN",
    "Breakfast Cereals": "GRAIN",
    "Legumes and Legume Products": "DAL",
    "Vegetables and Vegetable Products": "VEG",
    "Fruits and Fruit Juices": "VEG",
    "Poultry Products": "MEAT",
    "Beef Products": "MEAT",
    "Pork Products": "MEAT",
    "Lamb, Veal, and Game Products": "MEAT",
    "Finfish and Shellfish Products": "MEAT",
    "Sausages and Luncheon Meats": "MEAT",
    "Sweets": "SWEET",
    "Beverages": "BEVERAGE",
    "Snacks": "SNACK",
    "Soups, Sauces, and Gravies": "COMPOSITE",
    "Fast Foods": "COMPOSITE",
    "Meals, Entrees, and Side Dishes": "COMPOSITE",
    "Restaurant Foods": "COMPOSITE",
}

# Raw ingredients legitimately reach extremes an assembled dish cannot: oil is 884 kcal and
# 100 g fat per 100 g. So this bound only catches genuine nonsense.
MAX_KCAL_100G = 950.0

INDIAN_DISH = re.compile(
    r"\b(samosa|pakora|bhatura|poori|puri|jalebi|vada|kachori|gulab jamun|dosa|idli|"
    r"paratha|naan|biryani|chutney|raita|dal|paneer|halwa|ladoo|barfi|burfi|chaat|"
    r"upma|poha|pulao|korma|saag|tandoori|masala|tikka)\b",
    re.I,
)


def read_csv(path: Path):
    with path.open(newline="", encoding="utf-8-sig") as handle:
        yield from csv.DictReader(handle)


def best_portion(portions: list[dict]) -> tuple[float, str] | None:
    """Prefer a portion that names a real household unit over a bare 'quantity'."""
    candidates = []
    for portion in portions:
        grams = portion.get("grams")
        label = (portion.get("label") or "").strip()
        if not grams or not (1.0 <= grams <= 1500.0):
            continue
        vague = not label or "not specified" in label.lower()
        candidates.append((vague, grams, label))

    if not candidates:
        return None
    candidates.sort(key=lambda c: (c[0],))
    _, grams, label = candidates[0]
    return round(grams, 1), label


def convert_sr_legacy(csv_dir: Path) -> list[dict]:
    categories = {
        row["id"]: row["description"] for row in read_csv(csv_dir / "food_category.csv")
    }

    foods: dict[str, dict] = {}
    for row in read_csv(csv_dir / "food.csv"):
        foods[row["fdc_id"]] = {
            "description": row["description"],
            "category": CATEGORY_MAP.get(categories.get(row["food_category_id"], "")),
            "per_100g": {},
            "portions": [],
        }

    for row in read_csv(csv_dir / "food_nutrient.csv"):
        food = foods.get(row["fdc_id"])
        if food is None:
            continue
        try:
            key = NUTRIENTS[int(row["nutrient_id"])]
        except (KeyError, ValueError):
            continue
        try:
            food["per_100g"][key] = round(float(row["amount"]), 2)
        except (TypeError, ValueError):
            continue

    for row in read_csv(csv_dir / "food_portion.csv"):
        food = foods.get(row["fdc_id"])
        if food is None:
            continue
        try:
            grams = float(row["gram_weight"])
        except (TypeError, ValueError):
            continue
        label = " ".join(
            part for part in (row.get("portion_description"), row.get("modifier")) if part
        ).strip()
        food["portions"].append({"grams": grams, "label": label})

    out = []
    for fdc_id, food in foods.items():
        kcal = food["per_100g"].get("kcal")
        if kcal is None or not (0.0 <= kcal <= MAX_KCAL_100G):
            continue

        portion = best_portion(food["portions"])
        grams, label = portion if portion else (100.0, None)

        out.append(
            {
                "name": food["description"],
                "alt_names": [],
                "category": food["category"],
                "source": "USDA",
                "source_ref": f"FDC:{fdc_id}",
                "is_composite": False,
                "per_100g": food["per_100g"],
                "default_portion_g": grams,
                "portion_label": label or None,
            }
        )
    return out


def convert_fndds(path: Path) -> list[dict]:
    payload = json.load(path.open(encoding="utf-8"))
    survey = payload["SurveyFoods"]

    out = []
    for food in survey:
        description = food.get("description", "")
        if not INDIAN_DISH.search(description):
            continue

        per_100g = {}
        for nutrient in food.get("foodNutrients", []):
            nid = nutrient.get("nutrient", {}).get("id")
            key = NUTRIENTS.get(nid)
            if key is not None and nutrient.get("amount") is not None:
                per_100g[key] = round(float(nutrient["amount"]), 2)

        if per_100g.get("kcal") is None:
            continue

        portions = [
            {
                "grams": p.get("gramWeight"),
                "label": p.get("portionDescription") or p.get("modifier") or "",
            }
            for p in food.get("foodPortions", [])
        ]
        portion = best_portion(portions)
        grams, label = portion if portion else (100.0, None)

        out.append(
            {
                "name": description,
                "alt_names": [],
                "category": "COMPOSITE",
                "source": "USDA",
                "source_ref": f"FDC:{food['fdcId']}",
                "is_composite": True,
                "per_100g": per_100g,
                # USDA analysed these directly, so they override INDB's fried-oil errors.
                "is_verified": True,
                "default_portion_g": grams,
                "portion_label": label or None,
            }
        )
    return out


def write(path: Path, source: str, foods: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"source": source, "citation": CITATION, "licence": "CC0", "foods": foods}
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(foods):>5} foods -> {path} ({path.stat().st_size / 1024:.0f} KB)")


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2

    csv_dir, fndds_path, out_dir = (Path(a) for a in sys.argv[1:4])
    write(out_dir / "foods_usda.json", "USDA_SR_LEGACY", convert_sr_legacy(csv_dir))
    write(out_dir / "dishes_fndds.json", "USDA_FNDDS", convert_fndds(fndds_path))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
