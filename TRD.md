# TRD — Personal Nutrition & Training Tracker (Android)

**Version** 1.0
**Companion to** PRD.md, DESIGN.md
**Target** Single-user Android app, sideloaded release `.apk`

---

## 1. Stack

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Kotlin 2.0+ | Native, no bridge, best camera and background story |
| UI | Jetpack Compose + Material 3 | Single-file screens, easy theming, fast iteration |
| Min / target SDK | 26 / 35 | 26 covers everything relevant and unlocks the modern APIs |
| Persistence | Room (SQLite) | Local-only by design, migrations, Flow queries |
| Async | Coroutines + Flow | Standard |
| DI | Hilt | Keeps ViewModels and repos wired without ceremony |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Gemini REST is a plain HTTPS JSON API |
| Camera | CameraX | Reliable capture and preview |
| Images | Coil | Thumbnails in the diary |
| Charts | Vico (Compose-native) | Weight, calorie and PR charts |
| Prefs | DataStore (Proto or Preferences) | Settings, unit choices, day-start |
| Secrets | EncryptedSharedPreferences (Jetpack Security) | Gemini API key at rest |
| Work | WorkManager | Backup reminders, deferred uploads |
| Build | Gradle KTS, version catalog | |

**No Firebase. No Play Services dependency beyond what CameraX pulls. No analytics, no
crash reporting, no ads.** Anything that phones home other than the Gemini endpoint is a
defect.

## 2. Architecture

Single-module MVVM with a repository layer. One module keeps navigation and build times
simple for a personal app; package boundaries do the organising.

```
com.yash.tracker
├── MainActivity.kt              // single activity, Compose host
├── di/                          // Hilt modules
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/                 // FoodDao, LogDao, ProductDao, MealDao,
│   │   │                        // WorkoutDao, ProfileDao, WeightDao
│   │   ├── entity/
│   │   └── seed/                // asset import + JSON models
│   ├── remote/
│   │   ├── GeminiService.kt     // Retrofit interface
│   │   ├── GeminiClient.kt      // request assembly, retry, timeout
│   │   ├── prompts/             // system prompts as string constants
│   │   └── dto/                 // request/response DTOs + schemas
│   ├── repository/
│   │   ├── FoodRepository.kt
│   │   ├── LogRepository.kt
│   │   ├── ProductRepository.kt
│   │   ├── MealRepository.kt
│   │   ├── WorkoutRepository.kt
│   │   ├── ProfileRepository.kt
│   │   └── BackupRepository.kt
│   └── prefs/
├── domain/
│   ├── model/                   // UI-facing models, unit-safe
│   ├── nutrition/
│   │   ├── PlanCalculator.kt    // BMR, TDEE, targets, macros, water, timeline
│   │   ├── PortionResolver.kt   // household measures → grams
│   │   └── MacroMath.kt
│   ├── workout/
│   │   ├── VolumeCalculator.kt
│   │   ├── OneRepMax.kt         // Epley
│   │   └── MetCalories.kt
│   └── matching/
│       ├── FoodMatcher.kt       // local catalogue matching before any network call
│       └── CorrectionMemory.kt
├── ui/
│   ├── theme/                   // Color.kt, Type.kt, Shape.kt, Theme.kt
│   ├── components/              // rings, cards, steppers, set rows
│   ├── onboarding/
│   ├── dashboard/
│   ├── logging/                 // sheet, camera, recognition, entry editor
│   ├── foods/                   // search, products, saved meals
│   ├── workout/
│   ├── progress/
│   └── settings/
└── util/
```

**Rules:**
- Composables never touch repositories or Room directly. ViewModel → repository → DAO/remote.
- All money-shot logic (plan math, portion resolution, volume, 1RM) lives in `domain/` as
  pure functions with no Android imports, so it's unit-testable without a device.
- One `AppNavHost` with type-safe routes. Bottom bar: Today · Foods · Workout · Progress ·
  Settings.
- ViewModels expose a single `StateFlow<UiState>` per screen. No `LiveData`.

## 3. Data model

Room, schema version 1. All timestamps are epoch millis UTC; all dates are `LocalDate`
stored as `yyyy-MM-dd` text. Weights stored in kg, heights in cm, energy in kcal,
macros in grams to one decimal, volumes in ml. Unit display conversion happens at the UI
layer only.

### 3.1 Profile and targets

```sql
CREATE TABLE profile (
  id INTEGER PRIMARY KEY,             -- always 1
  name TEXT NOT NULL,
  age INTEGER NOT NULL,
  sex TEXT NOT NULL,                  -- MALE | FEMALE
  height_cm REAL NOT NULL,
  goal TEXT NOT NULL,                 -- LOSE | MAINTAIN | GAIN
  goal_weight_kg REAL,                -- null when maintaining
  activity TEXT NOT NULL,             -- SEDENTARY|LIGHTLY|MODERATELY|VERY|ATHLETE
  pace TEXT NOT NULL,                 -- GENTLE | STEADY | AGGRESSIVE
  eating_style TEXT NOT NULL,         -- NONE|HIGH_PROTEIN|LOW_CARB|KETO|VEGETARIAN|VEGAN
  notes TEXT,                         -- allergies, health notes, passed to Gemini
  day_start_hour INTEGER NOT NULL DEFAULT 4,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE target (                 -- history of plan revisions; latest row is active
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  computed_at INTEGER NOT NULL,
  basis_weight_kg REAL NOT NULL,      -- the weight the plan was built on
  bmr INTEGER NOT NULL,
  tdee INTEGER NOT NULL,
  activity_factor REAL NOT NULL,
  kcal INTEGER NOT NULL,
  protein_g REAL NOT NULL,
  carbs_g REAL NOT NULL,
  fat_g REAL NOT NULL,
  water_ml INTEGER NOT NULL,
  weeks_to_goal INTEGER,              -- null when maintaining
  rate_lb_per_week REAL,
  deficit_capped INTEGER NOT NULL DEFAULT 0,
  floor_applied INTEGER NOT NULL DEFAULT 0,
  is_manual_override INTEGER NOT NULL DEFAULT 0
);
```

### 3.2 Food catalogue

```sql
CREATE TABLE food (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  alt_names TEXT,                     -- JSON array: transliterations, regional names
  category TEXT,                      -- GRAIN|DAL|VEG|DAIRY|MEAT|SNACK|SWEET|BEVERAGE|COMPOSITE
  source TEXT NOT NULL,               -- IFCT | USDA | AI_ESTIMATE | USER | PRODUCT
  source_ref TEXT,                    -- IFCT code, FDC id, or a URL
  is_composite INTEGER NOT NULL DEFAULT 0,   -- true for cooked dishes
  kcal_100g REAL NOT NULL,
  protein_100g REAL NOT NULL,
  carbs_100g REAL NOT NULL,
  fat_100g REAL NOT NULL,
  fibre_100g REAL,
  sugar_100g REAL,
  sodium_mg_100g REAL,
  default_portion_g REAL NOT NULL,
  portion_label TEXT,                 -- "1 roti", "1 katori", "1 medium bowl"
  is_verified INTEGER NOT NULL DEFAULT 0,
  times_logged INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_food_name ON food(name);

CREATE VIRTUAL TABLE food_fts USING fts4(name, alt_names, content=food);

CREATE TABLE portion_measure (        -- household measures → grams, per food or generic
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  food_id INTEGER,                    -- null = generic measure
  label TEXT NOT NULL,                -- "katori", "roti", "ladle", "glass", "scoop"
  grams REAL NOT NULL,
  FOREIGN KEY(food_id) REFERENCES food(id) ON DELETE CASCADE
);
```

### 3.3 Products

```sql
CREATE TABLE product (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  brand TEXT,
  name TEXT NOT NULL,
  kcal_100g REAL NOT NULL,
  protein_100g REAL NOT NULL,
  carbs_100g REAL NOT NULL,
  fat_100g REAL NOT NULL,
  fibre_100g REAL, sugar_100g REAL, sodium_mg_100g REAL,
  serving_g REAL,
  serving_label TEXT,                 -- "1 scoop", "3 biscuits"
  ingredients TEXT,
  label_photo_uri TEXT,
  barcode TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

Products are authoritative: an entry referencing a product never gets its macros
recalculated from an estimate.

### 3.4 Diary

```sql
CREATE TABLE log_entry (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  date TEXT NOT NULL,                 -- yyyy-MM-dd, resolved against day_start_hour
  logged_at INTEGER NOT NULL,
  meal_type TEXT NOT NULL,            -- BREAKFAST | LUNCH | DINNER | SNACK
  source TEXT NOT NULL,               -- PHOTO | TEXT | MEAL_TEMPLATE | PRODUCT | MANUAL
  photo_uri TEXT,
  user_hint TEXT,                     -- what I typed alongside the photo
  note TEXT,
  grounding_source TEXT,              -- one-line provenance shown on the entry
  kcal REAL NOT NULL,                 -- denormalised totals for fast dashboard reads
  protein_g REAL NOT NULL,
  carbs_g REAL NOT NULL,
  fat_g REAL NOT NULL,
  fibre_g REAL,                       -- summed over whichever items stated it; null = none did
  sugar_g REAL,
  sodium_mg REAL,
  micro_kcal REAL NOT NULL DEFAULT 0  -- calories covered by items that stated any of the three
);
CREATE INDEX idx_entry_date ON log_entry(date);

CREATE TABLE log_item (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  entry_id INTEGER NOT NULL,
  food_id INTEGER,                    -- nullable: free-form AI item
  product_id INTEGER,
  name TEXT NOT NULL,                 -- frozen at log time
  quantity REAL NOT NULL,
  unit TEXT NOT NULL,                 -- G | ML | PIECE | KATORI | ROTI | SCOOP | CUP
  grams REAL NOT NULL,                -- resolved
  kcal REAL NOT NULL,
  protein_g REAL NOT NULL,
  carbs_g REAL NOT NULL,
  fat_g REAL NOT NULL,
  fibre_g REAL,                       -- null where the source never stated it
  sugar_g REAL,
  sodium_mg REAL,
  ai_confidence REAL,                 -- 0..1, null if manual
  was_edited INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY(entry_id) REFERENCES log_entry(id) ON DELETE CASCADE
);
```

Macros are **frozen** on the item at save time. Changing a food's catalogue values later
never rewrites history.

`fibre_g`, `sugar_g` and `sodium_mg` are **nullable on purpose**. A null means nobody measured
it, which is not zero — reading an absent sugar figure as "no sugar" would have the meal review
praising a dessert. They come from the tables first (a product's label, then a catalogue row
matched exactly by name) and from the model's own estimate only when neither knows.
`micro_kcal` is what makes a total honest: it says how much of the entry's energy came from
items that stated anything, so a figure drawn from one item out of four can be labelled as
such. The entry's four values are rolled up by `LogDao`, never by callers.

The table wins **per field, not all-or-nothing**. Half a real catalogue is `AI_ESTIMATE` rows
remembered from earlier readings, which state nothing; letting a matched row's blanks win
would mean the model could answer the question and be ignored, and the blank would never be
filled. What a reading supplies is written back onto the food it names (`fillMissingMicros`),
so the same dish is never read twice — but only onto `AI_ESTIMATE` rows. A bundled INDB or
USDA row whose source never measured sugar keeps its blank, because the row states where its
numbers come from and mixing a guess into one labelled USDA would make that claim false.

`MIGRATION_6_7` backfills everything logged before these columns existed, from the product or
catalogue row each item already points at, or from an exact name match where exactly one food
answers to it. It writes only columns that are null, skips weightless rows (scaling by zero
grams would state a confident 0 g of sugar), and recomputes `micro_kcal`. Plenty stays blank —
rows pointing at `AI_ESTIMATE` foods learned before the prompt asked for these figures have
nothing behind them to copy.

### 3.5 Saved meals

```sql
CREATE TABLE meal_template (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  photo_uri TEXT,
  default_meal_type TEXT,
  kcal REAL NOT NULL, protein_g REAL NOT NULL, carbs_g REAL NOT NULL, fat_g REAL NOT NULL,
  fibre_g REAL, sugar_g REAL, sodium_mg REAL, micro_kcal REAL NOT NULL DEFAULT 0,
  times_logged INTEGER NOT NULL DEFAULT 0,
  last_logged_at INTEGER,
  created_at INTEGER NOT NULL
);

CREATE TABLE meal_template_item (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  template_id INTEGER NOT NULL,
  food_id INTEGER, product_id INTEGER,
  name TEXT NOT NULL,
  quantity REAL NOT NULL, unit TEXT NOT NULL, grams REAL NOT NULL,
  kcal REAL NOT NULL, protein_g REAL NOT NULL, carbs_g REAL NOT NULL, fat_g REAL NOT NULL,
  fibre_g REAL, sugar_g REAL, sodium_mg REAL,
  FOREIGN KEY(template_id) REFERENCES meal_template(id) ON DELETE CASCADE
);
```

### 3.6 Water and weight

```sql
CREATE TABLE water_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  date TEXT NOT NULL, logged_at INTEGER NOT NULL, ml INTEGER NOT NULL
);

CREATE TABLE weight_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  date TEXT NOT NULL UNIQUE, weight_kg REAL NOT NULL, note TEXT, logged_at INTEGER NOT NULL
);
```

### 3.7 Workouts

```sql
CREATE TABLE exercise (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  muscle_group TEXT NOT NULL,         -- CHEST|BACK|LEGS|SHOULDERS|ARMS|CORE|FULL_BODY|CARDIO
  equipment TEXT NOT NULL,            -- BARBELL|DUMBBELL|MACHINE|CABLE|BODYWEIGHT|OTHER
  type TEXT NOT NULL,                 -- STRENGTH | CARDIO
  met_value REAL NOT NULL DEFAULT 5.0,
  is_custom INTEGER NOT NULL DEFAULT 0,
  default_rest_sec INTEGER NOT NULL DEFAULT 90,
  notes TEXT
);

CREATE TABLE routine (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL, note TEXT, created_at INTEGER NOT NULL
);

CREATE TABLE routine_exercise (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  routine_id INTEGER NOT NULL, exercise_id INTEGER NOT NULL,
  position INTEGER NOT NULL, target_sets INTEGER, target_reps_low INTEGER, target_reps_high INTEGER,
  FOREIGN KEY(routine_id) REFERENCES routine(id) ON DELETE CASCADE
);

CREATE TABLE workout_session (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  routine_id INTEGER, name TEXT NOT NULL, date TEXT NOT NULL,
  started_at INTEGER NOT NULL, ended_at INTEGER,
  total_volume_kg REAL NOT NULL DEFAULT 0,
  kcal_burned INTEGER NOT NULL DEFAULT 0,
  note TEXT, is_finished INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE workout_set (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER NOT NULL, exercise_id INTEGER NOT NULL,
  position INTEGER NOT NULL, set_index INTEGER NOT NULL,
  reps INTEGER, weight_kg REAL, rpe REAL,
  distance_m REAL, duration_sec INTEGER,     -- cardio
  is_warmup INTEGER NOT NULL DEFAULT 0,
  is_completed INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY(session_id) REFERENCES workout_session(id) ON DELETE CASCADE
);

CREATE TABLE personal_record (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  exercise_id INTEGER NOT NULL,
  type TEXT NOT NULL,                 -- MAX_WEIGHT | EST_1RM | MAX_VOLUME | MAX_REPS
  value REAL NOT NULL, date TEXT NOT NULL, session_id INTEGER
);
```

### 3.8 Correction memory

```sql
CREATE TABLE correction (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ai_name TEXT NOT NULL,              -- what the model said
  corrected_food_id INTEGER,
  corrected_product_id INTEGER,
  corrected_name TEXT NOT NULL,
  typical_grams REAL,
  hit_count INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL
);
CREATE INDEX idx_correction_ainame ON correction(ai_name);
```

## 4. Seed data

Shipped as gzipped JSON in `assets/seed/`, imported on first launch inside a single
transaction with a progress screen.

```
assets/seed/
├── foods_ifct.json.gz        // Indian ingredients, IFCT 2017 derived
├── foods_usda.json.gz        // generic and imported items
├── dishes_indian.json.gz     // cooked composite dishes, per-serving
├── portions.json.gz          // household measures → grams
└── exercises.json.gz         // exercise library with MET values
```

Seed record shape:

```json
{
  "name": "Roti (whole wheat)",
  "alt_names": ["chapati", "phulka"],
  "category": "GRAIN",
  "source": "IFCT",
  "source_ref": "A-014",
  "is_composite": true,
  "per_100g": { "kcal": 297, "protein": 9.7, "carbs": 57.4, "fat": 3.5, "fibre": 9.4 },
  "default_portion_g": 40,
  "portion_label": "1 roti"
}
```

Import runs in a `WorkManager` one-time job with a Compose progress screen. Bulk insert
with `insertAll` in chunks of 500 inside one transaction; the FTS table is populated after.
Target: under 6 seconds for ~3,000 rows on a mid-range device.

**Sourcing note for whoever builds the seed files:** IFCT 2017 (ICMR-NIN) is the reference
for Indian ingredient composition; USDA FoodData Central for the rest. Cooked-dish rows are
derived from standard recipes over IFCT ingredients — record the derivation in `source_ref`
so a wrong number can be traced.

## 5. Gemini integration

### 5.1 Configuration

- Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- Auth: `x-goog-api-key` header. Never in the URL, never logged.
- Default model ID: **`gemini-3.8-flash`**, stored in DataStore and editable in Settings.
  (Gemini 2.5 models are being shut down in October 2026 — do not target them. Because model
  IDs move, the field is user-editable and the app must not hardcode it in more than one
  place.)
- Timeouts: connect 10 s, read 25 s. One automatic retry on 5xx or timeout with a stricter
  prompt; no retry on 4xx.
- Image preprocessing before upload: rotate by EXIF, resize longest edge to 1024 px, JPEG
  quality 85, base64 inline. Keeps requests near 200–400 KB.

### 5.2 Structured output

Every call uses `responseMimeType: "application/json"` with an explicit `responseSchema`.
Never parse free text. The schema for meal recognition:

```json
{
  "type": "object",
  "properties": {
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name":            { "type": "string" },
          "name_local":      { "type": "string" },
          "quantity":        { "type": "number" },
          "unit":            { "type": "string", "enum": ["g","ml","piece","katori","roti","cup","tbsp","scoop"] },
          "grams_estimate":  { "type": "number" },
          "kcal":            { "type": "number" },
          "protein_g":       { "type": "number" },
          "carbs_g":         { "type": "number" },
          "fat_g":           { "type": "number" },
          "confidence":      { "type": "number" },
          "basis":           { "type": "string", "enum": ["reference","branded","search","estimate"] },
          "source_note":     { "type": "string" }
        },
        "required": ["name","quantity","unit","grams_estimate","kcal","protein_g","carbs_g","fat_g","confidence","basis"]
      }
    },
    "meal_type_guess": { "type": "string", "enum": ["BREAKFAST","LUNCH","DINNER","SNACK"] },
    "notes": { "type": "string" }
  },
  "required": ["items"]
}
```

### 5.3 System prompt — meal recognition

Held in `prompts/MealRecognition.kt`. Substance:

- You identify food in photographs for an Indian user logging meals. Name dishes the way an
  Indian home cook would: roti, paratha, dal tadka, paneer sabji, poha, idli, sambar, curd —
  not "flatbread" or "lentil curry".
- Estimate portions in household measures where natural (roti count, katori, ladle) and
  always also give a gram estimate. Standard references: 1 roti ≈ 40 g, 1 katori ≈ 150 ml,
  1 ladle of dal ≈ 120 g, 1 cup cooked rice ≈ 150 g. Use plate and cutlery in frame for scale.
- Nutrition values come from IFCT 2017 for Indian foods and USDA for generic ones. Set
  `basis` honestly: `reference` for standard foods, `branded` when you know a specific
  product, `search` when grounded by search, `estimate` when genuinely guessing.
- Set `confidence` honestly. A guess at 0.4 that the user corrects is far better than a
  confident wrong number.
- Return one row per distinct food. Do not merge a thali into one row.
- Output JSON matching the schema. No prose, no markdown fences.

The user's dietary notes, eating style, and top corrections (§5.6) are appended as context.

### 5.4 Prompts by call type

| Call | Model input | Output |
| --- | --- | --- |
| Meal photo | Image + optional hint + user context | Meal recognition schema |
| Text meal | Text + user context | Same schema |
| Nutrition label | Image of the panel | Product schema: per-100 g values, serving size and label, ingredients |
| Grounded lookup | Item name, Google Search grounding enabled | Single-item schema plus `source_note` with the source name |

### 5.5 Call policy

Network calls cost money and time, so:

1. **Text logging** first runs `MealTextParser` over what was typed and resolves every part
   against the user's products and then the food catalogue, by exact name or transliteration.
   If every part resolves, **no API call is made.** One unresolved part sends the whole
   sentence to the model: half a meal answered locally would be one entry written from two
   readings.
2. **Photo logging** always calls — that's the point. Nothing can be looked up before
   something has said what is in the frame.
3. **Saving a confirmed meal writes what it named into the catalogue** as
   `source = AI_ESTIMATE`, macros normalised to per 100 g, so the same meal typed tomorrow
   takes path 1. Only on save: a discarded reading leaves nothing behind (PRD §7.3), and a
   confirmed row carries the user's corrections rather than the model's first guess.
4. **Grounding is enabled only** when the user's hint contains "look it up" or "verify", and
   only while grounding is switched on in Settings — it is the one lever over what a single
   call costs.
5. Responses cache by `(model, prompt version + input hash)` for 30 days in a small
   `ai_cache` table. The key deliberately excludes the prompt *text*: the prompt carries the
   profile and the last twelve corrections, so hashing it emptied the cache every time a row
   was edited. Label readings are cached the same way; label *failures* are not, because a
   second attempt at a panel means the photo was the problem.
6. Not yet built: a daily call counter in Settings.

### 5.6 Failure handling

| Failure | Behaviour |
| --- | --- |
| No network | Skip the call, open manual entry prefilled with the hint, keep the photo attached |
| 401 / 403 | "Your Gemini key was rejected" with a Settings shortcut |
| 429 | "Rate limited — try again in a minute", manual entry offered |
| 5xx / timeout | One retry, then fall back to manual entry |
| Malformed JSON | One retry with `Return only valid JSON matching the schema.` prepended, then manual |
| Empty `items` | "I couldn't find food in that photo" with retake and manual options |
| Missing macros on a row | Recompute from `grams_estimate` × catalogue match; if no match, mark the row red and require an edit before save |

**Never** show a raw stack trace or JSON blob to the user. **Never** save an entry as a side
effect of a failed recognition.

## 6. Domain logic (must be pure and unit-tested)

`PlanCalculator` implements exactly the formulas in PRD §5.2. Required tests:

```
Mifflin male:   30y, 180cm, 80kg → BMR 1780
Mifflin female: 30y, 165cm, 60kg → BMR 1320
TDEE moderately active male above → 2759
Lose + steady → 2259 target; deficit 500; ~1.0 lb/week
Deficit cap:   50 kg (110 lb) person, aggressive → capped at 550, not 750
Calorie floor: small female, aggressive → floored at 1200, floor_applied = true
Timeline sanity: 400 kcal/day deficit → 0.7–0.9 lb/week (assert the range)
Maintain → no timeline emitted
Keto → carbs exactly 30 g, fat absorbs remainder, protein untouched
Low-carb → carbs ≤ 100 g
Carb floor → carbs ≥ 60 g for non-keto, taken from fat not protein
```

`PortionResolver` resolves `(quantity, unit, food)` → grams, checking food-specific
`portion_measure` rows before generic ones. `MetCalories` uses
`kcal = MET × weight_kg × hours`. `OneRepMax` uses Epley: `w × (1 + reps/30)`.

`MealReviewer` scores a meal out of 100 from four densities, weighted 2/1/1/1. The card shows
each nutrient's **plain amount** — 37 g, 410 mg — and a one-word verdict; the densities are what
the verdict comes from and are deliberately not on screen:

```
Sugar    sugar ÷ fibre, 5:1 clean → 20:1 indefensible. Falls back to sugar's share
         of the carbs (20% → 60%) when no fibre figure exists. Weighted double.
Fibre    g per 1,000 kcal, 14 good → 3 poor (the dietary-guideline density)
Protein  share of the energy, 25% good → 8% poor
Salt     mg sodium per 1,000 kcal, 600 good → 2,300 poor
```

Everything is a density or a ratio, never an absolute, so a meal is not marked down for being
large. Sugar is judged against fibre rather than alone because a bare sugar figure cannot
separate a banana from a barfi. A plate whose figures cover under 60% of its calories returns
`Unrated` rather than a low score.

`SessionAnalyst` reads a finished session back. The muscle split is counted in **sets**, not
kilos, so bodyweight work does not vanish from it. Each exercise is compared as an `Effort` —
`Load` (estimated 1RM), `Reps` or `Time` — and two efforts only compare when they are the same
kind; weight × reps is zero on both sides of a pull-up, so a single number would have every
bodyweight session tie for ever.

`NextWorkoutPlanner` picks the muscle group with the best claim on the next session: rest
first, then neglect. A group worked inside `RECOVERY_DAYS` (2) is not a candidate however
little it has had; among what is rested, the least-worked in the last seven days wins, with
the longest rest breaking ties. CARDIO and FULL\_BODY are excluded from the recommendation and
still count as work when logged.

## 7. Photo storage

- Files in `context.filesDir/photos/{entryId}_{timestamp}.jpg`, never in shared storage.
- Stored copy: longest edge 1024 px, JPEG 80. A separate 256 px thumbnail for the diary.
- Deleting an entry deletes its files.
- Settings offers "delete photos older than N days" (default off), which nulls `photo_uri`
  but keeps the entry.

## 8. Backup and restore

**Format** — `.ntbak`, a single file:

```
[ 8 bytes magic "NTBAK001" ][ 16 bytes salt ][ 12 bytes IV ][ AES-256-GCM ciphertext ]
```

Plaintext is a ZIP containing `data.json` (a full dump of every table, schema version
included) and `photos/`.

**Key derivation** — PBKDF2-HMAC-SHA256, 210,000 iterations, over the six-word backup code
normalised to lowercase and joined by single spaces.

**Backup code** — six words drawn from a bundled 2,048-word list, generated with
`SecureRandom` at setup, displayed once on a screen that requires an explicit "I've written
this down" confirmation, and always re-viewable in Settings.

**Export** — write to `Downloads/` via MediaStore, then launch the system share sheet.
**Restore** — SAF file picker, code entry, explicit destructive-overwrite confirmation,
schema-version check with migration or a clear refusal, then restart the app.
**Reminder** — WorkManager checks weekly; if the last export is older than 14 days, a
dismissible dashboard banner appears. No notification in v1.

## 9. Security

- API key in `EncryptedSharedPreferences` (AES-256-GCM, keystore-backed master key).
- `android:allowBackup="false"` and no `usesCleartextTraffic`.
- Network security config pinning HTTPS only, `generativelanguage.googleapis.com` allowed.
- No logging of request bodies, image bytes, or the key in release builds. Strip all
  `Log.d` via ProGuard rules.
- Room database is not separately encrypted (single-user device, device encryption is the
  boundary). Note this as a conscious tradeoff; SQLCipher can be added later without a
  schema change.

## 10. Permissions

| Permission | Why | When requested |
| --- | --- | --- |
| `CAMERA` | Meal and label photos | On first camera use, with rationale |
| `INTERNET` | Gemini calls | Install time |
| `READ_MEDIA_IMAGES` | Gallery picker (API 33+) | Use the Photo Picker instead where possible to avoid it entirely |

No location, no contacts, no storage write beyond MediaStore for export.

## 11. Performance budgets

| Metric | Budget |
| --- | --- |
| Cold start to interactive dashboard | < 2 s |
| Dashboard recomposition on a new entry | < 16 ms frame |
| Catalogue search results | < 100 ms (FTS4) |
| Photo capture → recognition screen | < 8 s on 4G |
| Seed import (first launch) | < 6 s |
| APK size | < 40 MB including seed data |

Dashboard reads come from denormalised totals on `log_entry`, never a per-item SUM across
the diary. Charts read pre-aggregated queries, not full table scans.

## 12. Testing

- **Unit** — everything in `domain/`. `PlanCalculator` is non-negotiable; the assertions in
  §6 are the acceptance test for the plan math.
- **DAO** — Room in-memory tests for the diary, totals, day-boundary resolution, and cascade
  deletes.
- **Parser** — golden-file tests feeding recorded Gemini JSON (including malformed and empty
  payloads) through the mapping layer.
- **UI** — Compose tests for the onboarding flow end to end and for the recognition screen's
  edit-then-save path.
- Manual device pass against PRD §7 before each release build.

## 13. Build and release

```bash
./gradlew assembleRelease
```

- Signing config from a local `keystore.properties` that is gitignored. Generate the keystore
  once with `keytool` and back it up with the app data — losing it means no in-place updates.
- R8 minify and resource shrink on, with keep rules for kotlinx.serialization models and Room.
- `versionCode` increments per build, `versionName` semantic.
- Output `app/build/outputs/apk/release/app-release.apk`, transferred to the phone directly.
  Installing requires "install unknown apps" for the source app once.
- No Play Store, no App Bundle, no Play App Signing.
- Debug builds use a `.debug` application ID suffix so both can coexist on the device.

## 14. Implementation order for Claude Code

Each step should compile, run, and be verifiable on device before moving on.

1. Project scaffold, Hilt, theme, navigation shell with five empty tabs, dark mode wired.
2. Room schema, DAOs, seed import with progress screen. Verify row counts.
3. `PlanCalculator` plus its full unit test suite. Tests green before any UI.
4. Onboarding flow, all 11 questions, results screen, profile and target persistence.
5. Dashboard: rings, water, 7-day strip, diary list reading real data.
6. Manual entry and catalogue search with FTS. App is now usable without AI.
7. Settings: API key storage, model ID, appearance, units, test connection.
8. Gemini client, schemas, prompts. Text recognition first (easier to debug), then photo.
9. Recognition and confirm screen with complete per-row editing. This screen gets the most
   care of anything in the app.
10. Products, including label-photo parsing.
11. Saved meals and quick-log paths.
12. Correction memory feeding back into matching.
13. Workout: library, routines, live session with rest timer, summary, PRs.
14. Progress: weight log, charts, trends.
15. Backup, restore, backup code, reminder banner.
16. Polish pass: empty states, error states, dark mode audit, performance check, release signing.
