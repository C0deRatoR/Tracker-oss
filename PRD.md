# PRD — Personal Nutrition & Training Tracker (Android)

**Version** 1.0
**Owner** Yash (sole user)
**Status** Ready for build
**Platform** Android only, sideloaded `.apk`, single device

---

## 1. What this is

A single Android app that tracks what I eat and what I lift. It replaces two habits that
currently live in separate apps: logging Indian meals with reasonably accurate macros, and
logging gym sessions set by set.

The defining interaction is the **photo log**: I photograph my plate, the app names each item
it sees and its portion, and I confirm or correct it before anything is saved. Nothing is
written to the diary without my eyes on it.

## 2. Who it's for

One person. Me. This shapes every decision:

- No accounts, no login, no sync, no multi-user data model.
- No onboarding funnel, no upsell, no analytics, no crash reporting.
- No moderation of content I enter, no privacy policy screen.
- Data lives in one SQLite database on one phone. Loss of the phone means loss of the data
  unless I've exported a backup — so backup has to be one tap and hard to forget.
- I supply my own Gemini API key. Cost control is my problem, but the app should not waste
  calls.

## 3. Goals

| Goal | How I'll know it worked |
| --- | --- |
| Logging a meal takes under 20 seconds | Photo → confirm → saved, no typing in the common case |
| Indian food is accurate, not approximated as "curry" | Roti, sabji, dal, poha, idli and similar named correctly with portion in household units |
| I trust the numbers | Every AI estimate is editable before saving, and shows where it came from |
| Repeat meals are near-instant | Saved meal logged in two taps |
| Packaged food is exact, not guessed | Products I've added use my label data, not an estimate |
| The gym log doesn't slow me down between sets | Logging a set takes one tap when reps and weight match the last set |

## 4. Non-goals

Explicitly out of scope for v1, listed so Claude Code doesn't build them:

- iOS, tablet layouts, wearables, widgets.
- Cloud sync, account system, sharing, social feed, friends, streaks-with-friends.
- Barcode scanning against an online database (label photo covers this instead).
- Recipe builder with step-by-step cooking instructions.
- Meal plan generation ("here's what to eat tomorrow").
- Step counting, sleep tracking, heart rate, Health Connect integration.
- Notifications and reminders. (Deferred to v1.1 — see §12.)
- Any medical claim or diagnosis.

---

## 5. Feature set

### 5.1 Onboarding interview

Runs once on first launch. One question per screen with a progress bar. Answers feed the
plan calculation and are all editable later in Settings.

**Screen 0 — Welcome.** Greeting, then the disclaimer, then a warm framing line, then Start.

> Hi! I'm your nutrition coach. Let's get your plan dialed in. 💪

> Quick note before we start: I'm an AI assistant, not a medical professional. Check with
> your primary care physician before starting any new diet or making changes for a medical
> or dietary condition.

> To kick things off, I'd love to ask you a few quick questions so I can get to know you and
> build a plan that actually fits.

**Questions, in order:**

| # | Question | Input | Notes |
| --- | --- | --- | --- |
| 1 | First name | Text | Required, used in greetings |
| 2 | Age | Number | 13–100 |
| 3 | Biological sex | Male / Female | Helper: needed for the metabolic math, not identity |
| 4 | Height | Number + unit toggle | cm or ft+in, stored as cm |
| 5 | Current weight | Number + unit toggle | kg or lb, stored as kg |
| 6 | Goal | Lose / Maintain / Gain | |
| 7 | Goal weight | Number | Skipped entirely if maintaining. Must be below current for lose, above for gain |
| 8 | Activity level | Sedentary / Lightly / Moderately / Very / Athlete | Each with a one-line description |
| 9 | Pace | Gentle / Steady / Aggressive | Helper explains the tradeoff |
| 10 | Eating style | No preference / High-protein / Low-carb / Keto / Vegetarian / Vegan | Affects macro split and photo-recognition priors |
| 11 | Foods to avoid / health notes | Free text, optional | Passed to Gemini as context; surfaced on the plan screen |

Choice questions auto-advance ~130 ms after selection. Text and number questions need Next.
Back is available from question 2 onward. Validation errors appear inline under the field,
never as a dialog.

### 5.2 Plan calculation

Computed when the last question is answered, recomputed when any input changes, and offered
for recompute when logged weight moves more than 2 kg from the weight the plan was built on.

**BMR — Mifflin-St Jeor**
```
men:   BMR = 10 × kg + 6.25 × cm − 5 × age + 5
women: BMR = 10 × kg + 6.25 × cm − 5 × age − 161
```

**TDEE** = BMR × activity factor
`sedentary 1.2 · lightly 1.375 · moderately 1.55 · very 1.725 · athlete 1.9`

**Daily calorie target**

- **Lose** — a deficit sized to roughly 0.5–1% of bodyweight per week.
  `gentle 250 · steady 500 · aggressive 750` kcal/day, capped at `5 × bodyweight_lb` per day
  so it never exceeds 1% of bodyweight weekly. Hard floor: **1,500 kcal for men, 1,200 for
  women.** If either the cap or the floor bites, say so on the results screen in one line.
- **Maintain** — TDEE.
- **Gain** — surplus of `gentle 250 · steady 325 · aggressive 400` kcal/day.

Round the final target to the nearest 10.

**Macros**

- **Protein** — 0.8–1.0 g per lb of bodyweight. Use 1.0 if the goal is lose, or activity is
  athlete, or eating style is high-protein. Otherwise 0.8.
- **Fat** — ~0.35 g per lb of bodyweight.
- **Carbs** — the remaining calories.
- **Keto override** — carbs fixed at 30 g, fat absorbs the remainder.
- **Low-carb override** — carbs capped at 100 g, surplus calories moved to fat.
- **Floors** — carbs never below 60 g (except keto/low-carb), fat never below 0.2 g/lb. If a
  floor forces a change, take it from the other macro, never from protein.

**Water** — 0.5–1.0 oz per lb of bodyweight, scaled by activity
(`sedentary 0.55 · lightly 0.6 · moderately 0.65 · very 0.75 · athlete 0.85` oz/lb),
displayed in litres to one decimal.

**Timeline** — only shown when losing or gaining.
```
weekly gap        = |TDEE − target| × 7
lbs per week      = weekly gap ÷ 3500
weeks to goal     = |current lb − goal lb| ÷ lbs per week, rounded to whole weeks
```
Sanity check before display: a ~400 kcal/day deficit must show roughly 0.7–0.9 lb/week. If
the computed rate contradicts the deficit, the calculation is wrong — recompute rather than
display it. Omit the timeline entirely when maintaining.

**Methodology note.** The results screen carries a collapsible "Methodology" section naming
exactly these standards: BMR via Mifflin-St Jeor, TDEE via standard activity multipliers,
~3,500 kcal per pound for the timeline, and protein/fat targets from common sports-nutrition
guidelines. No alternative formulas anywhere in the app.

**Results screen** shows: the daily calorie number as the hero, the three macro rings, water,
BMR, TDEE, activity multiplier, timeline (if applicable), any cap/floor notices, an eating-style
note where relevant, a summary of my answers, the Methodology section, and a copyable plan block:

```
MY PLAN (calculated [DATE]):
- Goal: [goal] (from [current wt] toward [goal wt])
- Daily calories: [X]
- Protein: [X]g  | Carbs: [X]g  | Fat: [X]g  | Water: [X]L
```

### 5.3 Dashboard

The home screen. Everything above the fold answers "how am I doing today".

- **Calorie ring** — large, centre. Shows consumed in the middle, remaining below. Fills as
  the day goes. Turns amber past 100%, red past 110%. Exercise calories, if a workout was
  logged today, are shown as a separate small "+X earned" chip but are **not** automatically
  added to the target — the ring stays honest. Tapping the chip adds them for the day if I
  choose.
- **Macro rings** — three smaller rings under the calorie ring: protein, carbs, fat. Each
  shows `Xg / Yg`.
- **Water** — a row of tappable glass icons, default 250 ml each, target from the plan. Long
  press to remove one. Custom amount via a small sheet.
- **7-day history strip** — seven bars, one per day, height mapped to calories against target,
  colour-coded under/on/over. Tap a bar to jump to that day.
- **Food diary** — today's entries grouped by meal (Breakfast, Lunch, Dinner, Snacks). Each
  entry shows its items, total kcal, and a thumbnail if it came from a photo. Swipe to
  delete, tap to edit.
- **Today's workout** — if one exists, a compact card: name, duration, volume, calories.
- **Log button** — a persistent FAB that opens the log sheet (§5.4).

Pull to refresh does nothing. Changing the date is done through the history strip or a date
chip in the header.

### 5.4 Food logging

The log sheet offers four routes, in this order of prominence:

#### a) Photo → recognise → confirm (primary)

1. Tap camera. Shoot or pick from gallery. Optional one-line hint field ("dinner at home",
   "restaurant thali") that gets passed along.
2. The image is sent to Gemini with the Indian-food system prompt.
3. **Recognition screen** — the app lists what it read, immediately and item by item:

   ```
   2 × Roti (whole wheat, medium)        ~120 g    240 kcal
   Paneer sabji                           200 g    380 kcal
   Greek yogurt                           150 g     97 kcal
   ────────────────────────────────────────────────────────
   Total                                           717 kcal
   P 41g · C 58g · F 34g
   ```

   Each row shows the item name, portion, calories, and a confidence dot (green ≥0.8, amber
   0.5–0.8, red <0.5). Low-confidence rows are expanded by default so they're the first thing
   I look at.
4. **Everything is editable here, before saving.** Per row I can: change the name, change the
   quantity or unit, change grams, override any macro, delete the row, or swap it for a
   matching item from my catalogue or products. I can add a row the model missed. I can
   change the meal type and the date/time.
5. Save. The photo is stored locally alongside the entry. The corrections I make are recorded
   (§5.8) so the same dish is recognised better next time.

This screen is the product. It must feel fast and it must never save behind my back.

#### b) Type what I ate

Free text: `2 eggs and toast`, `1 katori dal, 2 roti, salad`. Same recognition screen, same
edit-before-save flow. Text goes to Gemini only when the parser can't match every item against
the local catalogue — exact matches skip the network entirely.

#### c) Saved meals

Meals I eat repeatedly, saved with exact items and portions. "Had this meal" logs the whole
thing in two taps. Created either from scratch or by saving any diary entry as a meal
("Save as meal" on an entry). Each saved meal stores name, optional photo, item list with
portions, and cached totals. Editable and deletable. Sorted by most-logged.

#### d) Products (packaged food I own)

For things like a specific brand of oats, protein powder, peanut butter, bread, or biscuits.

- Add a product by **photographing the nutrition label** (Gemini parses the panel into
  per-100 g values plus serving size), or by typing the values manually.
- Fields: brand, product name, per-100 g kcal/protein/carbs/fat/fibre/sugar/sodium, serving
  size in g and its household label ("1 scoop", "3 biscuits"), ingredients text, photo.
- Once added, the product appears in search and can be selected on the recognition screen to
  replace an AI estimate — so "oats" becomes *my* oats with *my* label's numbers.
- Products are marked as exact and are never overwritten by an AI estimate.

#### Accuracy policy

- **Common whole foods and obvious Indian dishes** — estimate instantly from the bundled
  reference table (IFCT-derived). No network call, no delay.
- **Branded, packaged, or restaurant items, or anything genuinely uncertain** — ground the
  numbers via Gemini with Google Search grounding enabled, preferring published sources
  (USDA FoodData Central, the brand's own nutrition information, IFCT). Log the numbers and
  show the source in one short line on the entry.
- **If I say "look it up" or "verify"** — search regardless and show the source.
- **Never stall on a failed lookup.** If grounding returns nothing clean within the timeout,
  fall back to the best reference estimate, save it, and say so in one line on the entry.

### 5.5 Food catalogue and Indian data

The bundled database ships with the app and works fully offline.

- **Source of record:** Indian Food Composition Tables 2017 (ICMR–National Institute of
  Nutrition, Hyderabad) for Indian ingredients, and USDA FoodData Central for imported and
  generic items. Every bundled row carries its source.
- **Cooked-dish layer:** IFCT covers raw ingredients, so the seed data also includes a
  cooked-dish table (roti, paratha, dal tadka, rajma, chole, paneer butter masala, idli,
  dosa, upma, poha, biryani, khichdi, sambar, curd, etc.) with per-serving values derived
  from standard recipes, plus a household-measure mapping (1 roti ≈ 40 g, 1 katori ≈ 150 ml,
  1 ladle dal ≈ 120 g).
- Items Gemini identifies that aren't in the catalogue are written into it as `source =
  AI_ESTIMATE`, so the second time I eat that dish it's local and instant.
- Search covers name, common alternate names, and my own products. Hindi/Marathi transliterated
  names are matched where they exist in the seed data.

### 5.6 Workout tracker

Shares the app shell, sits on its own tab.

- **Exercise library** — bundled list covering the common barbell, dumbbell, machine,
  cable and bodyweight movements, each with muscle group, equipment, type (strength or
  cardio), and a MET value for calorie estimation. I can add my own exercises.
- **Routines** — named templates (Push, Pull, Legs, Upper, Lower, whatever) holding an
  ordered exercise list with target sets and rep ranges. Start a routine to prefill a session.
- **Live session** — pick a routine or start empty. For each exercise, a set table with
  reps, weight, and an optional RPE. **The last session's numbers are prefilled**, so
  repeating a set is one tap. Warmup sets flagged separately and excluded from volume.
  A rest timer starts automatically on set completion, with the duration remembered per
  exercise.
- **Cardio** — duration, distance, and estimated calories from MET × bodyweight × time.
- **Session summary** — duration, total volume (kg × reps), sets completed, estimated
  calories, any PRs hit.
- **History and PRs** — per-exercise history charts (top set weight, estimated 1RM via
  Epley, total volume) and a personal-record list. New PRs are called out in the session
  summary.
- Calories burned flow to the dashboard as the "+X earned" chip described in §5.3.

### 5.7 Progress and history

- **Weight log** — manual entry with a line chart, goal weight drawn as a reference line,
  and a 7-day moving average so daily noise doesn't read as failure.
- **Calorie history** — daily consumed vs target, weekly averages, adherence percentage.
- **Macro trends** — average protein/carbs/fat over 7 and 30 days.
- **Streak** — days logged, shown quietly. Not gamified, no confetti.

### 5.8 Correction memory

Every time I edit an AI estimate on the recognition screen, the app records the original
guess and my correction. On subsequent recognitions, matching items are resolved against my
corrections first, so the app converges on how I actually eat. Visible in Settings as a
plain list I can prune.

### 5.9 Settings

- **Profile** — every interview answer, editable, with a Recalculate plan action.
- **Targets** — accept the computed plan or override any of the four numbers manually. An
  override is labelled as such on the dashboard.
- **Gemini API key** — entered here, stored encrypted. A Test connection button. A model ID
  field so the model can be changed without a rebuild.
- **Appearance** — Light / Dark / Follow system.
- **Units** — metric or imperial for weight and height, independently.
- **Meal windows** — the time ranges that decide the default meal type for a new entry.
- **Backup** — export, restore, and the backup code (§5.10).
- **Data** — clear cache, delete all photos older than N days, delete everything.

### 5.10 Backup and restore

Because there is no cloud, backup must be trivial and safe.

- **Export** — writes a single encrypted `.ntbak` file (the whole database plus photos)
  to `Downloads/`, then opens the system share sheet so it can go to Drive, WhatsApp, or
  anywhere else.
- **Backup code** — generated once at setup: six words from a fixed wordlist, shown on a
  screen that insists I write it down. The file is encrypted with a key derived from this
  code. Restoring requires both the file and the code.
- **Reminder** — if no export has happened in 14 days, a dismissible banner appears on the
  dashboard.
- **Restore** — pick the file, enter the code, confirm the overwrite. Restore is destructive
  and says so.

---

## 6. Screen inventory

| Screen | Purpose |
| --- | --- |
| Onboarding (welcome + 11 questions + results) | First run only |
| Dashboard | Today at a glance, food diary |
| Log sheet | Route picker: photo, text, saved meal, product, manual |
| Camera / picker | Capture the plate |
| Recognition & confirm | Review and correct before saving — the critical screen |
| Entry editor | Edit a saved diary entry |
| Food search | Catalogue + products + saved meals |
| Product list / editor | Manage packaged foods |
| Saved meal list / editor | Manage repeat meals |
| Workout tab | Routines, start session, recent sessions |
| Live session | Set logging with rest timer |
| Session summary | Volume, calories, PRs |
| Exercise detail | History charts and PRs |
| Progress | Weight, calories, macro trends |
| Settings + subscreens | Profile, targets, API key, appearance, backup, data |

## 7. Acceptance criteria

The build is done when all of these are true on a physical device:

1. Fresh install runs the interview, and the results screen numbers match the formulas in
   §5.2 when hand-checked against a worked example.
2. A photo of a mixed Indian plate produces named items with portions in under 8 seconds on
   a normal connection, and every field on that screen is editable before saving.
3. Discarding a recognition saves nothing — no entry, no photo, no catalogue write.
4. Airplane mode: dashboard, diary, saved meals, products, catalogue search, all workout
   features, and manual logging work. Photo logging queues or fails with a clear message
   that offers manual entry.
5. Logging a saved meal takes two taps from the dashboard.
6. A product added from a label photo is selectable on the recognition screen and its macros
   replace the estimate exactly.
7. A workout set can be logged in one tap when it matches the previous session's set.
8. Export produces a file that a fresh install can restore with the backup code, with all
   entries, photos, products, meals and workouts intact.
9. Dark mode is complete — no unstyled surfaces, no black-on-black text.
10. An invalid or missing API key produces a clear message pointing to Settings, never a
    crash or a silent failure.
11. The app cold-starts to an interactive dashboard in under 2 seconds.

## 8. Edge cases to handle explicitly

- Photo with no food in it → "I couldn't find food in that photo" with retake and manual
  options.
- Photo with a food I've never logged → estimate, save to catalogue, mark as AI estimate.
- Ambiguous portion ("some rice") → default to the standard household portion and flag the
  row amber.
- Entry logged at 1 a.m. → belongs to the previous day if before the configured day-start
  (default 4 a.m.).
- Editing an entry from a past day → totals and history recompute.
- Deleting a product that entries reference → entries keep their frozen macros; the link is
  dropped.
- Goal reached (weight crosses goal) → prompt to set a new goal or switch to maintain.
- Gemini returns malformed JSON → one retry with a stricter instruction, then fall back to
  manual entry with the raw text prefilled.
- API quota exhausted → clear message, manual logging still fully available.

## 9. Content and tone

- Sentence case everywhere. No ALL-CAPS labels.
- Buttons say what happens: "Save entry", not "Submit". "Log meal", not "Confirm".
- Errors explain what happened and what to do, without apologising.
- Empty states invite action: "Nothing logged yet. Photograph your first meal."
- The AI is never anthropomorphised beyond the coach framing in onboarding.
- The medical disclaimer appears in onboarding and on the plan screen. Nowhere else — it
  shouldn't nag.

## 10. Success measures (personal)

- I log at least two meals a day without it feeling like work.
- I correct fewer recognition rows in month two than month one.
- Weight moves within ±30% of the predicted rate over eight weeks.

## 11. Build phases

**Phase 1 — Skeleton and food core**
Room schema, seed data import, onboarding, plan math, dashboard with rings and diary,
manual entry, catalogue search. No AI yet. Ships usable.

**Phase 2 — Gemini**
API key storage, photo capture, recognition screen with full editing, text parsing,
label parsing for products, grounding for branded items, correction memory.

**Phase 3 — Repeats and products**
Saved meals, product management, quick-log paths, water tracking, 7-day strip.

**Phase 4 — Workouts**
Exercise library, routines, live session, rest timer, history, PRs, calories to dashboard.

**Phase 5 — Polish**
Progress charts, weight log, backup and restore, dark mode audit, empty and error states,
release signing.

## 12. Deferred to v1.1

Reminder notifications, a home screen widget, Health Connect for steps, meal-time-based
suggestions, and voice logging.

---

*This document specifies a personal tracking tool. It is not a medical device and makes no
clinical claims. All targets are population-average estimates.*
