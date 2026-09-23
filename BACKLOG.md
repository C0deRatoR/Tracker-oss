# Backlog

Things deliberately not built yet, and why. Everything here is a decision, not a gap in the
plan — the PRD and TRD stay the source of truth for what is in scope.

## Exercise images or icons

**Asked for:** small illustrations next to each exercise, the way Strong has them.

**Status:** not built. No free source can legally be bundled.

| Source | Licence | Verdict |
| --- | --- | --- |
| Strong's own illustrations | proprietary | No. Shipped once anyway, then removed — see below. |
| yuhonas/free-exercise-db images | none stated | No. The text is Unlicense and that is what we use; the images were scraped, provenance unknown, some traceable to bodybuilding.com whose terms forbid reproduction. |
| wger | CC BY-SA per record | Possible, but share-alike plus per-image attribution for ~800 rows is a lot of obligation for decoration. |
| Everkinetic | GPL-3.0 | No — copyleft over an app that is otherwise unlicensed private code. |
| ExRx | proprietary | No. |

**The legal path**, if this is worth doing: a small set of equipment-type icons — barbell,
dumbbell, machine, cable, bodyweight — drawn as vector drawables and picked by
`exercise.equipment`. Six icons instead of eight hundred illustrations, and it still gives
the list something to scan by. That is a UI decision, so it belongs in the Claude Design
pass rather than here.

**Done:** the six marks live in `res/drawable/ic_equipment_*.xml`.

**Still open, and worth revisiting:** wger's image set is the only one with a usable licence.
Measured rather than assumed — 374 images covering 273 of its 862 exercises, all CC-BY-SA 3
or 4, static PNGs rather than gifs. About a third coverage, with attribution and share-alike
obligations that only bite on distribution. Worse, only 8 of our 808 rows match wger's by
name, so importing it is a hand-matching job on top of the licence work. Nothing free and
comprehensive exists for gifs or video.

**Done instead:** a "Watch form on YouTube" action on the exercise detail screen. It covers
every movement, always shows current video, and copies nothing — linking is not reproduction.

**Overridden, deliberately, on request.** The catalogue was rebuilt from Strong's own — read
off an installed copy on the owner's phone by `tools/strong/` — and Strong's list preview
images came with it, 232 of them in `assets/exercise_art`. That is the first row of the table
above, and the verdict on it has not changed: the artwork is Strong's, and so is the
instruction text now bundled alongside it. Names, body parts and equipment categories are
facts about movements and carry no such claim; the pictures and the prose do.

**Reversed, and the seam held.** Both are gone. `assets/exercise_art` is deleted, the builder
no longer emits an `art` field, and `ExerciseArt` falls back to the equipment mark exactly as
designed. Instructions now come from free-exercise-db through the `MUSCLE_SOURCE` pairing,
which turned out to *improve* coverage rather than cost it: 232 of 253 rows carry steps,
against 75 from Strong.

The scraper went too. `tools/strong/scrape_thumbs.py` is deleted and `collect.py` no longer
writes art or instructions into the catalogue, so re-running the scrape cannot quietly put
either back. `strong_catalogue.json` keeps only names, body parts and categories — the facts,
which is all that was ever defensible.

ExerciseDB was evaluated as the replacement and rejected: its repo carries no data at all
(two files), the 11,000 exercises are a commercial RapidAPI product, and the licence is
AGPL-3.0 — the same copyleft objection that ruled out Everkinetic above, with a network
clause on top.

**Revisit later: AscendAPI's ExerciseDB v1.** A free, no-signup, no-key tier exists — 1,500
exercises with gifs, one call away. Not pursued yet because their own docs rule out exactly
what we'd want it for: caching is "only permitted if your plan explicitly allows it", the
free tier doesn't, and their media URLs rotate weekly (Mondays 00:00 UTC) and "should never
be stored" on a non-caching plan. So this tier gives live hotlinking with weekly-expiring
URLs, not bundleable assets — offline breaks, and every exercise image is a network call to
a third party. A paid plan or their "whitelabel URLs" offer might lift the caching
restriction; worth a follow-up if the equipment-icon approach ever feels insufficient.

**Done, on request, within those terms.** 184 of 253 rows now show ExerciseDB's animation: a
still first frame in the library, the gif itself on the detail screen, credited to AscendAPI
there as the free tier requires. The catalogue bundles only ids (`EXERCISEDB_ID` in
`tools/build_exercises.py`, hand-matched, stored in `art`) — ids are the one thing the docs
call permanent. The URL is fetched when a row is on screen and held in memory for the
session; Coil's disk cache is off, so nothing ExerciseDB serves is written to the phone. The
69 rows left out have no honest match (sport, most band work, the Olympic lifts) and keep
their equipment mark. The costs stated above are real and accepted: offline there are no
pictures, each row on screen is a call to a third party, and the API throttles a burst of
about ten calls (the app queues and waits out the 429). Terms allow personal, non-commercial
use only — revisit before this is ever sold.

## Exercise instructions and muscles — done

Instructions come from Strong along with the art: 75 of the 253 rows carry them. Most of the
rest are cardio and the machine variants of a lift whose free-weight row already has the
cues.

Muscles, force, mechanic and level come from free-exercise-db, and had to be put back — the
Strong rebuild dropped them while leaving the columns and the seed parser expecting them, so
every row shipped with five nulls. 235 of 253 rows carry them again. The 18 that do not are
cardio and sport ("Yoga", "Hiking") plus a few movements free-exercise-db does not carry.

Two rules hold that import together, both in `tools/build_exercises.py`:

- The pairing is a **written-out table**, not fuzzy matching. The closest string to "Back
  Extension" is "Leg Extensions", and hanging the wrong muscles off an exercise is worse
  than hanging none. Every pair was read and accepted; the build fails if the table names a
  row free-exercise-db no longer has.
- **Strong's body part still decides `muscle_group`.** free-exercise-db derives its own from
  whichever muscle it happens to list first, which files the squat under quadriceps and the
  deadlift under lower back. That is right as detail and wrong as a heading.

`topUpExercises` writes the five fields onto rows that are already seeded, so a phone that
has been here since v1 gets them without being re-seeded.

## Exercise detail: things that could still go on it

Raised as "anything else what we can add in that?" and not built. None of them block
anything; they are listed so the choice is a choice.

**An estimated-1RM chart over time.** The data is already stored — every logged set carries
weight and reps, and `OneRepMax.epley` already runs over them for the bests. What is missing
is only the graph. Cheapest of the four, and the one that answers "am I actually getting
stronger on this" rather than "what did I do last time".

**Similar movements.** A row of alternatives sharing the same primary muscle but a different
`equipment` — what to do when the rack is taken. Needs no new data, just a query and a
ranking rule.

**Your own notes per exercise.** Free text the user writes ("elbows flare on set 3", "bench
at notch 4"). One nullable column and a text field. The catalogue's own how-to is generic by
definition; this is the part only the user can write.

**Common mistakes and cues.** No free source carries these — free-exercise-db has the steps
but not the failure modes. Writing them by hand for 808 rows is not realistic; writing them
for the thirty or so movements actually used would be. Deliberately last: it is the only one
of the four that needs content rather than code.

## Label parsing: the awkward cases are still unproven

Reading a real packet works — a jar of Pintola dark-chocolate peanut spread came back as 587
kcal, 24 g protein, 27 g carbs, 43.3 g fat with a 32 g serving, which matches the pack. What
has not been seen yet is any of the cases the prompt has special rules for: a panel printing
per-serving figures only, energy given in kJ, or sodium printed as salt. Each of those needs a
packet that does it.

## Giving the app to other people — done for a handful

**Status:** built. Firebase App Distribution carries signed release APKs to a console-managed
`friends` group; `./gradlew assembleRelease appDistributionUploadRelease` is the whole ship
step. The plugin is build-time only — no Firebase SDK is linked into the app, and the app ID
rather than a `google-services.json` configures it, so nothing about what runs on a phone
changed.

Testers live in the group, not in `app/build.gradle.kts`, so adding or dropping one is a
console click rather than a commit. Release notes come from the last commit subject.

Testers bring nothing: photo logging runs on the shared key behind the proxy. See below.

**No longer carries anything of Strong's.** The art and the instruction prose were briefly
shipped to the three testers as a deliberate exception; both are now gone and the catalogue
is names, public-domain how-to, and nothing else. This stopped being a distribution question.

Putting it on Play is still a different job, and these are its actual gates:

- A privacy policy at a public URL. The app takes photos and holds health data, so this is
  required, not optional. It also has more to disclose than it used to: the diary stays on
  the device, but meal and label photos now travel phone → our proxy → Google rather than
  straight to Google.
- A Data safety form declaring exactly that: what is collected, what leaves the device, and
  that the user can delete it.
- A Health apps declaration, because the app tracks nutrition and body weight.
- ~~Strong's art and instructions gone~~ — done.

**Also coming, and not code:** Google's developer verification reaches certified devices
globally in 2027 (Brazil, Indonesia, Singapore and Thailand from 30 September 2026). Once it
lands, even a hand-delivered APK has to be registered to a verified developer identity. India
is unaffected today.

## The shared Gemini key, and what is not built around it

**Status:** built. `server/` holds one Gemini key and forwards `generateContent` untouched.
The app signs in anonymously to Firebase and sends that ID token; the proxy verifies it
against this project and refuses everything else, so a stripped APK is not a free Gemini
endpoint.

The Settings key field is gone too, on request. It survived the first cut as an escape hatch —
the user's own quota, and a way around a proxy outage — but asking someone to go and fetch an
API key is exactly the friction the proxy exists to delete, and keeping it meant carrying a
second route through every failure message for the few who would ever have used it. The cost
is stated plainly: no proxy, no photo logging, and no way for a user to route around it.

**Rejected:** shipping six free keys in the APK and rotating them. Three reasons, in order of
how much they mattered. The keys are extractable — R8 renames symbols, not string constants,
so `strings classes.dex` finds them. Multiplying free-tier projects to dodge a quota is
circumvention under Google's API terms, and the account at risk is the one now holding the
Firebase project and the tester group. And the arithmetic never supported it: four people at
roughly eight calls a day each sit about twenty times under one key's daily allowance, with
the prompt cache cutting even that.

**Deliberately not built: per-user metering.** The proxy counts nothing. It does not need to
while the key is free-tier — the worst an abuser achieves is exhausting a daily quota that
costs nothing, and the blast radius is four known people losing photo logging until midnight
Pacific. Adding a counter means a store (Upstash, Firestore) and a migration for a problem
that does not exist at this size.

This is the first thing to build if either of two things changes: the key becomes paid, or
the app goes anywhere public. At that point the ID token is already the right handle to meter
on — it is a stable per-install id the proxy can throttle or refuse individually.

**Also new, and worth saying out loud:** meal photos now pass through a server the owner runs
rather than going phone-to-Google. The proxy logs no request or response body, only a
truncated uid, the path, a status and a duration. But it is a real change to the app's
privacy story, and on Play it moves the Data safety answer from "sent to a third party" to
"collected by us".
