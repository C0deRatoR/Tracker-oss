# Design PRD — Personal Nutrition & Training Tracker

**For** UI/UX designer
**From** Yash
**Version** 2.1 — the design has landed; see §0.1 for what is now built
**Platform** Android only (phone, portrait)
**Companion docs** PRD.md (product scope), TRD.md (engineering), BACKLOG.md (deliberately deferred)

---

## 0. What changed since version 1.0

Version 1.0 was a brief for an app that hadn't been built. This one is a brief for an app that
**works today**, on a phone, with a month of real data in it. Everything below is either built
and running or explicitly marked as not built.

That changes your job in one important way: **you are not inventing screens, you are replacing
the surface of screens that already function.** The app currently runs on bare Material 3
defaults — no colour work, no type scale, no spacing system, no considered layout. It is
deliberately ugly and completely functional. All colour, type and shape live in one place in
the code (`ui/theme/`), so what you deliver maps to tokens rather than to a rewrite.

New since 1.0, and specified below:

- An **in-app camera screen** (§7.4) — the system camera couldn't be told to use the back lens,
  so capture is now ours and needs designing.
- A **first-launch import screen** (§7.1) — the food catalogue seeds on first run.
- The **exercise library is 819 exercises**, not a hand-made list of 100 (§7.7).
- **Products** are built, including label capture (§7.6).
- **Progress** is built, with specific cards (§7.8).
- **Backup and restore** are built, including a destructive restore confirmation and a
  dashboard reminder (§7.9).
- **Correction memory** is visible in Settings (§7.9).
- A **premium section with actual teeth** (§3), because "premium" as an adjective is not a brief.

---

## 0.1 What changed in 2.1 — the design is in

The "Precision Soft Organic" design pack has been applied across every screen. The app no
longer runs on Material 3 defaults. The pack itself (`stitch_untitled_project.zip` — screens,
exported HTML and its `DESIGN.md` token table) is deliberately not in the repo; keep it
alongside. Its token table is reproduced in full in `ui/theme/Color.kt` and `Type.kt`, so
nothing below depends on having the zip to hand.

What landed, and where it lives:

- **Palette** — `ui/theme/Color.kt`, taken verbatim from the pack's token table: a warm paper
  ground (`#FBF9F4`), pure-white cards, hairline outlines, one saturated blue (`#2B5BF5`) held
  back for what is actually actionable. The dark palette deliberately inverts Material's
  container ladder, because in this design a card floats *above* the page in both themes.
- **Type** — `ui/theme/Type.kt`. Geist is bundled (`res/font/`, SIL OFL 1.1) rather than
  downloaded, with tabular figures on every style: every number in this app sits in a column
  that changes while you watch it.
- **Shape and motion** — `ui/theme/Shape.kt` (24dp cards) and `ui/theme/Motion.kt`, which holds
  one motion vocabulary — an expo-out "arriving" curve, two springs, and `springClick`, the
  press-in tap that replaces Material's ripple on objects.
- **Component kit** — `ui/components/`: `Lux.kt` (cards, eyebrow labels, pills, metrics),
  `Actions.kt` (four button weights, no outlined buttons), `Controls.kt` (segmented toggle,
  choice cards, stepper, fields), `Indicators.kt` (the energy ring, bars, counters),
  `Scaffolding.kt` (top bars and the tab bar, whose selection is one pill that travels).
  Screens are assembled from these; a screen reaching for a raw `Card` or `Button` has drifted.

Two layout rules the pack does not state but the build needs:

- The tab bar's height is consumed in `AppNavHost` (`consumeWindowInsets`), so a screen's own
  `imePadding()` is not added on top of it. Without that, an open keyboard leaves a dead strip
  exactly one tab bar tall.
- Lists whose contents are replaced wholesale — search results, the diary on a day change —
  do not use `animateItem()`. It animates a replacement as a move and leaves a gap.

Still true from 2.0: all colour, type and shape live in `ui/theme/`, so a re-skin is a
token change, not a rewrite.

The rows below marked *not built* are unchanged — they are still missing features, not missing
styling.

---

## 1. What you're designing

An Android app that does two things for one person: tracks food with macros, and tracks gym
sessions set by set.

The signature interaction is **photograph the plate → the app names what it sees → correct
anything wrong → save**. Everything else in the food half exists to make that loop faster or to
cover the cases where a photo won't do.

This is a personal tool, not a product going to market. No signup, no paywall, no onboarding
funnel to optimise, no marketing surface, no growth metric. Every screen you design is a screen
one person uses several times a day, forever. **Design for the hundredth use, not the first.**

## 2. Who uses it

One user. Male, 20s, Mumbai. Desk job, trains four to five days a week, eats mostly home-cooked
Indian food with occasional restaurant meals and packaged items — oats, peanut butter, whey.

Two contexts matter and they pull in opposite directions:

**At the table, after serving food.** One hand free, phone at chest height, mildly impatient
because the food is getting cold. Shoot, glance, correct one thing, save. Under twenty seconds.

**In the gym, between sets.** Phone propped on a bench or held in a sweaty hand, read at arm's
length, brain half-occupied, 60–90 seconds of rest. Log a set without focusing. Large targets,
big type, no fine motor control assumed.

If a screen works in both contexts it's probably right.

## 3. What "premium" means here

This is the part of the brief I most want you to take literally. Premium is not a mood board;
it is a set of decisions that cost effort and show. The app should feel like a well-made
everyday object — a good pen, a mechanical watch face, a Braun scale — not like a SaaS
dashboard and not like a gym app.

**Premium here is restraint plus craft, in that order.**

### 3.1 The things that will actually make it feel expensive

1. **Typographic craft, especially numerals.** This app is 70% numbers. Use a face with true
   **tabular lining figures** and turn them on for every changing number — calorie totals,
   weights, timers, set rows. Numbers must not shuffle horizontally as they change. This is the
   single highest-leverage decision in the whole design, and almost every fitness app gets it
   wrong. (Tabular figures ≠ monospace. Monospace text is still banned; see §13.)
2. **Optical alignment over mathematical alignment.** Large numerals need their own optical
   correction — a 52 dp figure aligned by bounding box looks wrong next to a 14 dp label. Do
   the work.
3. **One weight of shadow, used almost never.** Depth should come from surface tone, spacing
   and hairlines, not from drop shadows on every card. If two elevations are visible at once,
   justify it.
4. **A real spacing scale, applied without exception.** 4 dp base. The reason cheap apps look
   cheap is that their gaps are 13, 17 and 22 px with no logic.
5. **Generous, confident negative space** — but not at the cost of §7.5, where density is the
   requirement. Premium is not the same as sparse; a dense screen laid out with conviction is
   more expensive-feeling than a sparse one hedging.
6. **Dark mode designed first, or at least equally.** It is what gets used at 6 a.m. in a gym
   and at 11 p.m. in bed. Dark mode that is just the light palette inverted is the clearest
   tell of a cheap app. True black is not required and probably wrong; consider elevation-tinted
   near-blacks.
7. **Restrained, purposeful colour.** The accent is `#2563EB`. In a premium reading of this app,
   accent appears on perhaps two elements per screen. Macro identities need a second channel
   anyway (§12), so resist the urge to give protein/carbs/fat three saturated hues.
8. **Motion that acknowledges rather than performs.** 150–250 ms, ease-out, on user action only.
   The rest timer's final seconds and a ring settling into place are the only moments that
   deserve any flourish, and even those should be quiet.
9. **Copy is part of the design.** Every string in this app is currently written in a specific
   voice — plain, direct, never apologetic, occasionally dry. Read the app before rewriting it.
   You have permission to rewrite any of it; you do not have permission to make it chirpy.

### 3.2 What premium is not, in this project

Not gradients. Not glassmorphism. Not a serif display face used decoratively. Not an
illustration style. Not a loading animation with personality. Not dark-mode-with-neon. Not
"delight." The app earns trust by being accurate and fast; the design's job is to make that
legible, not to add warmth on top of it.

## 4. Design principles

Arguments to settle disagreements with, in priority order.

**1. The number is the hero.** Calories remaining. Weight on the bar. One dominant number per
screen, everything else in support. If two numbers compete, one is in the wrong place.

**2. Nothing saves without the user seeing it.** AI estimates are provisional until confirmed,
and provisional must look visibly different from saved. This is the trust mechanism of the whole
app — if the user ever suspects something was logged behind their back, the app is dead.

**3. Speed is the feature.** Two taps to log a repeat meal. One tap to log a repeat set. Count
the taps in every daily loop you design and justify each one.

**4. Honest, not encouraging.** This is a measuring instrument. No badges, no confetti, no
"great job", no red shame days. The number is the feedback.

> **A tension to resolve, not ignore:** the app does show a "day streak" count and does announce
> personal records. Both are deliberate and both are *facts*, not rewards — a streak is a count
> of days logged, a PR is a heavier lift than before. Design them as data. If either starts
> looking like a prize, you've taken it too far. Getting this line right is a real design problem
> and I'd like your view on it.

**5. Premium warmth, not clinical.** See §3. Most fitness apps are either aggressive gym-bro dark
or sterile medical white. This should be neither.

## 5. Fixed constraints

Do not redesign these; they're locked by the engineering build.

| Constraint | Value |
| --- | --- |
| Accent colour | `#2563EB` — the palette gets built around it |
| Platform | Android, Material 3 as the underlying interaction model |
| Orientation | Portrait only |
| Theme | Full light **and** dark, both first-class |
| Navigation | Bottom bar, five tabs: Today · Foods · Workout · Progress · Settings |
| Minimum touch target | 48 dp; 56 dp for anything used in the gym |
| Locale | English, Indian number grouping (1,247), metric primary with imperial toggle |
| Type | Your call — but it must have tabular lining figures. Argue your choice. |

Everything else — layout, hierarchy, components, motion, empty states, the neutral palette, the
dark theme — is yours.

## 6. Deliverables

| # | Deliverable | Notes |
| --- | --- | --- |
| 1 | Design system in Figma | Colour tokens (light + dark), type scale incl. numeral handling, spacing scale, elevation, radii, icon set |
| 2 | Component library | Every component in §7, all states, as variants |
| 3 | High-fidelity screens | Both themes, every screen in §8 |
| 4 | Key flows as prototypes | The five flows in §9, clickable |
| 5 | State coverage | Loading, empty, error, offline for every screen that has them |
| 6 | Motion spec | Short doc or video for the moments in §11 |
| 7 | Handoff | Dev-mode Figma, exported SVG icons, tokens as JSON |

Deliver in that order. I'd rather review the system and three key screens early than everything
at once at the end.

## 7. Component library

Design as variants with every state (default, pressed, disabled, focused, error, selected,
loading) in both themes.

- **Progress ring** — large (calorie) and small (macro). Must handle 0%, mid, exactly 100%, and
  over-target. Decide what over-target looks like: unmissable, never punishing.
- **Card** — base container plus a hero variant for the plan/calorie card.
- **Buttons** — primary, secondary, text, destructive, icon-only, extended FAB.
- **Choice row** — full-width selectable card with title, optional description, radio dot.
- **Text input** — text, number, and number-with-unit-toggle (kg/lb, cm/ft-in).
- **Stepper** — portions and reps.
- **Food item row** — the most important component in the app. See §8.5.
- **Confidence indicator** — three levels, must work without colour alone.
- **Diary entry card** — meal name, item summary, calorie total, optional photo thumbnail,
  swipe-to-delete.
- **Search result row** — must distinguish three sources in one list: the bundled catalogue, the
  user's own products, and saved meals. Products are *exact*; catalogue rows are reference data;
  both currently render as one plain row and shouldn't.
- **Set row** — set number, previous session's numbers, weight, reps, complete toggle. The
  complete toggle is a 56 dp target.
- **Rest timer bar** — countdown plus −15 s / +15 s / Skip, readable from arm's length.
- **Water tracker** — a quantity control that is neither a slider nor a text field.
- **Camera viewfinder chrome** — see §7.4 and §8.4.
- **Bottom sheet** — with drag handle. Used for logging, routines, weight entry, product editing.
- **Bottom nav** — five items, active and inactive.
- **Chips, banners, snackbars, dialogs, skeletons.**
- **Charts** — line (weight with goal line and 7-day average), bar (calories vs target), and a
  compact 7-day strip for the dashboard. All must be legible at 360 dp without pinch-zoom.
- **Destructive confirmation dialog** — used by restore, which overwrites everything.

## 8. Screens

**Build status, so you know what you are looking at.** Everything marked *built* runs on a
phone today, with the 2.1 design applied (§0.1), and can be screenshotted or installed on
request — go and look before redesigning it, because several screens do things the old brief
never described. Everything marked *not built* needs designing from this document alone.

| Screen | Status |
| --- | --- |
| First-launch import | built |
| Onboarding — welcome, 11 questions, results | built |
| Today (dashboard) | built, incl. the backup reminder |
| Camera (plate and label) | built — labelled viewfinder, ring shutter, per-caller hint |
| Recognition & confirm | built, incl. swapping a row for a product |
| Foods search | built |
| Products list and editor | built |
| Saved meals | built |
| Workout landing, routine sheet, live session, summary | built |
| Exercise library browse/filter | built — muscle and equipment filters, ranked search |
| Exercise detail (history, PRs) | built — muscles, how-to, bests, every session |
| Progress — weight, calories, averages | built, with a 7/30/90/all-time window |
| Workout volume trend | built — four-week tonnage, on Progress |
| Settings, incl. backup, restore, correction memory | built |
| Backup code screen | built |


### 8.1 First launch — import

Before onboarding, the app imports its food catalogue: roughly 8,600 foods, 819 exercises and
the household measures. It takes a few seconds and shows progress with a label that changes
("Indian dishes", "Generic foods", "Exercises").

This is the first thing the user ever sees. It currently looks like a progress bar with text
above it. It is also the only screen in the app that is genuinely a *wait*, and it happens
exactly once — so it can carry a little more personality than anything else here, as long as it
doesn't promise a tone the rest of the app won't keep.

### 8.2 Onboarding — 13 screens

Welcome, eleven questions, results. Runs once.

**Welcome** carries a warm greeting, a medical disclaimer, a friendly framing line, then Start.
The disclaimer must be taken seriously without making the screen feel like a legal form. That
tension is the design problem.

**Questions** — one per screen with a progress indicator. Inputs vary: free text (name), number
(age), single choice (sex, goal, activity, pace, eating style), number with unit toggle (height,
weight), optional long text (health notes). Some carry a helper line explaining why they're
asked. Activity level has five options that all need distinguishing.

Decisions I need from you: how the progress indicator behaves when a question is skipped (goal
weight is skipped when maintaining); whether choice questions auto-advance; where validation
errors sit without the layout jumping.

**Results** presents the calculated plan: daily calorie target as hero, three macro targets, a
water target, the underlying numbers (BMR, maintenance, activity multiplier), expected timeline,
a summary of answers, a methodology disclosure naming the formulas, and a copyable summary.
That's a lot for one screen — hierarchy is the whole job. It should feel like receiving
something, not like reading a report.

### 8.3 Today (dashboard) — the most-seen screen

Answers "how am I doing today" without scrolling:

- Calorie ring, dominant, consumed and remaining against target.
- Three macro rings: protein, carbs, fat — grams consumed of grams targeted.
- Water against target.
- A workout indicator when one was logged today, showing calories burned. It is **not** added to
  the day's allowance — it's an optional tap. Design it as an offer, not an alert.
- A seven-day history strip, tappable to jump to a past day.
- The diary, grouped by meal, each entry showing items, calorie total, and photo thumbnail.
- A prominent way to log food.
- A date header that makes it obvious when you're looking at a past day.
- **A backup reminder banner** when no backup has been taken in 14 days. Dismissible. It is the
  only nagging element in the app; it should feel like a quiet note, not an alert — but it must
  not be so quiet it gets ignored, because the thing it prevents is losing everything.

The hard part: fitting that on a small phone without becoming a grid of equal-weight tiles.
Something must dominate and something must recede.

### 8.4 Camera — new, not in v1.0

Capture is now in-app, because the system camera on this phone could not be told to use the back
lens. This screen exists twice: photographing a plate, and photographing a nutrition label.

It currently has a viewfinder, a circular shutter, and a Cancel — and nothing else. Needs:

- Chrome that stays out of the way of the thing being framed.
- A distinction, or a deliberate lack of one, between the two contexts. A nutrition label wants
  to be framed tightly and held still; a plate does not. Consider whether the label variant
  deserves a framing guide.
- Clear feedback that focus has locked, since blurry label photos are the main failure mode.
- The shutter must be reachable one-handed and unmistakable.
- What happens between the shutter and the result — the capture and the several-second upload
  are one continuous wait to the user, and currently they see the camera vanish and a spinner
  appear elsewhere. That seam is yours to fix.

### 8.5 Recognition & confirm — the critical screen

**Spend the most time here.** This is where the product's promise is kept or broken.

The user has just photographed a plate. The app returns what it thinks it sees:

```
2 × Roti (whole wheat, medium)   ~80 g     240 kcal
Paneer sabji                      200 g     380 kcal
Greek yogurt                      150 g      97 kcal
```

Requirements:

- Items appear as fast as possible — the reading is the reward. Design the waiting state so it
  feels like reading, not loading.
- **Every value is editable before anything saves**: name, quantity, unit, grams, each macro.
  Delete a row, add a missed row, change meal type, change date and time.
- Each row carries a confidence level. Low-confidence rows should draw the eye first — the user
  should never hunt for the thing that needs fixing. Consider expanding them by default.
- A running total that updates live as edits are made.
- Provenance: one quiet line saying where the numbers came from.
- Discarding must be genuinely easy but must not lose an accidentally-swiped edit.

Design the row in two states — collapsed (glanceable, four values) and expanded (fully editable).
The transition is worth prototyping.

> **Not built yet:** swapping a row for one of the user's products ("this oats is *my* oats").
> Design it anyway — it needs an entry point on the row that doesn't clutter it, and it's the
> feature that turns an estimate into a measurement.

### 8.6 Foods tab

Search runs across the bundled catalogue and the user's products in one list, ordered with
products first. Saved meals are reachable from the logging sheet.

The design problem: **three kinds of row in one list.** A product is exact, transcribed from a
packet. A catalogue row is reference data with a source (INDB, USDA). A saved meal is a
composite. They currently all look identical apart from a subtitle. Either make the distinction
carry visually, or argue for segmentation.

**Products** — the user's packaged food: brand, name, per-100 g macros, serving size and its
household label ("2 Tbsp.", "1 scoop"), ingredients, label photo. Needs:

- A list that reads as *theirs* rather than as more database rows.
- An editor. It is currently a long bottom sheet of thirteen text fields, which works and looks
  like a form from 2009. The per-100 g block and the serving block want different treatment, and
  the four required fields (kcal, protein, carbs, fat) want distinguishing from the optional ones.
- The label-capture entry point at the top, and the state where a scan has just filled the form
  in — the user needs to see *what changed* so they can check it against the packet.
- A notice for a specific case: when a pack prints only per-serving figures, the app divides them
  down to per 100 g and says so. That line matters — it's the app admitting to a calculation.
- An error state for "no nutrition panel in that photo", which is common and not the user's fault.

**Saved meals** — meals eaten repeatedly with exact items and portions. Create, edit, delete, and
log in as few taps as possible.

### 8.7 Workout tab

- **Landing** — routines, a way to start an empty session, and recent sessions. A routine card
  shows its exercises and when it was last done.
- **Routine editor** — currently a bottom sheet: name, a search box, chips for chosen exercises.
  Ordering and per-exercise targets (sets, rep range) need designing.
- **Exercise library — 819 exercises.** This is new and it is a real information design problem.
  They carry muscle group, equipment (barbell, dumbbell, cable, machine, bodyweight, other) and
  type. Searching "curl" returns dozens. Needs filtering, or grouping, or both — and it needs to
  work while standing at a machine.
  > **Constraint, and I need you to design around it, not through it:** there are **no exercise
  > illustrations available**. Every free source is either proprietary, scraped with unknown
  > provenance, or share-alike licensed. Strong's illustrations are not usable. The legal option
  > is a small set of **equipment-type icons** — six or so — which is what I'd like you to design.
  > Do not spec per-exercise artwork; it cannot be delivered.
  >
  > **Done (2.1):** six equipment marks drawn for this project live in `res/drawable/`, and
  > `EquipmentMark` picks one from `exercise.equipment`. They are what makes the list scannable
  > in place of artwork.
- **Live session** — the gym screen. Per exercise, a set table: set number, the previous
  session's numbers, weight, reps, and a completion tick. The previous numbers are what make a
  repeat set one tap, so they must be readable but clearly not the input. Warmup sets marked
  separately. RPE optional. A rest timer starts automatically on completion, runs per-exercise
  (3:00 for bench, 2:30 for overhead press), and offers −15 s / +15 s / Skip. It must be readable
  from arm's length while the phone sits on a bench.
- **Session summary** — duration, total volume, sets, estimated calories, best set per exercise,
  and any personal records broken. PRs get a moment, but a restrained one. Note that the first
  time an exercise is ever done, *nothing* is called a record — a baseline is not an achievement,
  and the design should never imply otherwise.
- **Exercise detail** — history and personal records for one movement. Not built yet; design it.

### 8.8 Progress

Built, and currently four stacked cards that need hierarchy:

- **Weight** — current weight as hero, change since the first weigh-in, a line chart with a 7-day
  average and the goal as a reference line, and a "log weight" entry point. The chart is hidden
  until there are two weigh-ins, because one point is not a trend. Below, the weigh-in list,
  each deletable.
- **Calories** — 7-day average, target, adherence percentage, day streak. Adherence is defined as
  days within 10% of target, and the card says so in a caption. That caption is doing real work;
  don't design it away.
- **Averages** — kcal and macros over 7 and 30 days, each labelled with **how many days were
  actually logged**, because an average over two logged days is not a weekly average. Another
  caption doing real work.
- Workout volume trend — not built; design it.

### 8.9 Settings

Profile (all onboarding answers, editable, with recalculate), targets (accept or override), API
key with a connection test, appearance, units, meal time windows, and:

- **What I've learned** — the correction memory. When the user renames something on the confirm
  screen, the app remembers and stops making that mistake. Shown as a plain list they can prune.
  This is one of the few places the app talks about itself; it should feel like a ledger, not a
  feature boast.
- **Backup** — export, restore, last-backup line, and the backup code.
  - **The backup code screen deserves real attention.** Six words are the only way to decrypt a
    backup. They are shown on demand and must be written down somewhere that is not the phone —
    because the phone is the thing the backup exists to survive. Design a screen that makes
    someone actually do that rather than tapping past it.
  - **Restore is destructive and irreversible.** It replaces every meal, weigh-in and workout
    with the file's contents. The confirmation currently says so plainly and asks for the code in
    the same dialog. Make it impossible to do by accident and still possible to do while anxious.
- **Sources** — attribution for the food and exercise data. Legally required, quietly placed.

## 9. Flows to prototype

1. **First run** — import, welcome, eleven questions, results.
2. **Photo log** — dashboard → camera → capture → wait → recognition → edit one row → save →
   dashboard updated.
3. **Repeat meal** — dashboard → log sheet → saved meal → logged. Prove the two-tap claim.
4. **Gym session** — start a routine → log three sets on one exercise including one that differs
   from last time → rest timer → next exercise → finish → summary.
5. **Add a product from a label** — products → add → camera → capture → form fills → correct one
   value → save → find it in search.

## 10. States

Every screen needs its unhappy paths designed, not left to engineering.

**Loading** — prefer skeletons shaped like the incoming content over spinners. The recognition
wait is the most important loading state in the app.

**Empty** — every list starts empty. Each empty state invites the specific next action. Write the
copy; I'll edit it.

**Error** — inline, where the failure happened. State what happened and what to do, without
apologising. Cover: no network, rejected API key, rate limited, no food found in the photo, no
nutrition panel in the photo, malformed response, quota exhausted, wrong backup code, a backup
file that isn't one.

**Offline** — food photo logging and label scanning are the only things that stop working.
Everything else — diary, search, products, saved meals, all workout features, manual entry,
backup — stays fully usable. Communicate that precisely; don't grey out the app.

## 11. Motion

Sparing, almost always in response to a user action. Specify:

- Ring fill on entry, and ring change when a new entry lands.
- Recognition rows arriving.
- Set completion, and the rest timer's countdown including its final seconds.
- A personal record being hit — brief, restrained.
- Sheet and screen transitions.
- The camera-to-result seam (§8.4).

No looping ambient animation, no decorative parallax, no entrance animation on every card.
Include reduced-motion alternatives.

## 12. Accessibility

- Both themes meet 4.5:1 for body text and 3:1 for large text and non-text indicators. Dark-mode
  secondary text is where this usually breaks — check it.
- No meaning carried by colour alone. Confidence levels and macro identities need a second channel.
- Dynamic type to 200% without truncation; layouts wrap.
- Every icon-only control needs a screen-reader label.
- The gym screens must be usable with reduced attention and imperfect aim. That's an accessibility
  problem even for a fully-abled user.

## 13. Anti-brief

Rejected on sight:

- Gamification. Badges, levels, confetti, mascots, celebration screens.
- Grading language or judgemental colour. A 300-calorie overage is information, not a failure.
- The generic fitness-app look: neon-on-black, hexagons, muscle silhouettes, aggressive
  uppercase, motivational quotes.
- Every piece of content chopped into identical rounded cards with the same shadow.
- Gradient washes as decoration. One gradient in the whole app, on the plan hero, is the budget.
- ALL-CAPS labels, tracked-out eyebrow text, **monospace type for data** (tabular figures in a
  proportional face is what's wanted — see §3.1), arrows appended to button labels.
- Fake precision. "717 kcal" from a photograph is a fiction dressed as a fact. Help me not
  present estimates as measurements.
- Emoji beyond a very small deliberate set.
- Anything that implies the app is pleased with the user.

## 14. Open questions

I'd like a point of view rather than a default:

1. Should the calorie ring show consumed or remaining as its primary number? Argue it.
2. How should over-target read — unmissable but not punishing?
3. Is the seven-day strip better as bars, dots, or something else at 360 dp?
4. Recognition rows: cards or a single table? What survives editing better?
5. Rest timer: full-screen takeover, persistent bar, or in-context card? The phone is on a bench,
   not in a hand.
6. Can search unify catalogue, products and saved meals in one list without confusing them?
7. What typeface, and does it have real tabular figures at every weight you plan to use?
8. **819 exercises with six equipment icons and no illustrations** — how does that library not
   feel like a spreadsheet?
9. Streak and personal records are facts, not rewards (§4.4). Where exactly is that line, and how
   do you hold it visually?
10. The camera exists twice, for a plate and for a label. One screen or two?

## 15. Acceptance

Done when:

- Every screen in §8 exists in both themes with all states from §10.
- All five flows in §9 are clickable prototypes.
- The component library covers §7 with full variant coverage.
- The repeat-meal flow measurably takes two taps; the repeat-set flow takes one.
- Contrast passes in both themes, verified rather than assumed.
- Numerals are tabular everywhere they change, verified on the calorie ring and the set table.
- A developer can build a screen from the file without asking about spacing, colour, or state.

## 16. Practicalities

- Figma, shared with dev mode enabled.
- Design at 393 × 852 dp. **Check 360 dp** for the dashboard and the live session specifically —
  that's where they break first.
- Name layers and components properly; an engineer reads these files.
- The app is installable and running — ask for a build or screenshots of any screen before
  redesigning it. Several screens do things the old brief didn't describe.
- Weekly review is enough. Send work in progress rather than finished sections.
