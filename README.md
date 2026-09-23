# Tracker

A single-user Android app for logging meals and gym sessions. One person, one phone: your diary never leaves the device and there is nothing to sign up for.

## What it does

- **Photo meal log** — photograph a plate, Gemini names each item and estimates its portion, you confirm or correct before it's saved. Nothing is written to the diary unedited.
- **Indian food aware** — dishes like roti, sabji, dal, poha, and idli are recognized and portioned in household units instead of being flattened into a generic "curry" estimate.
- **Packaged food from label photos** — scan a product's nutrition label once, reuse the exact numbers afterward instead of an AI guess.
- **Gym log** — set-by-set logging, with one-tap repeat when reps and weight match the last set.
- **Repeat meals** — previously logged meals are saved for two-tap re-entry.
- **What to eat next** — up to four plates built from what is left of the day. The engine is deterministic: a beam search over your own foods, scored on the macro gap, with fibre, salt and sugar-to-fibre as a smaller term. It also weighs which meal you usually eat each food at, avoids repeating what you already ate today, and uses your own split of the day across meals. Catalogue dishes you have never logged are only added, and marked "new", when your own food cannot close the gap.
- **Training analysis** — a rolling seven-day read of every muscle. Secondary muscles count as half a set, graded against the 10–20 hard sets a week band. It also covers movement patterns, push/pull and quad/hamstring balance, rep ranges, RPE, and lifts that are stalling or going backwards. The result is a ranked list of several suggestions, each with exercises to fix it, taken from your own history first. The session summary shows the same breakdown for one workout and what the week still needs.
- **Routine templates** — Full body, Upper / Lower and Push / Pull / Legs. Each is fitted to your onboarding answers: activity level picks the split, goal sets the sets and reps, and age or a sedentary start swaps in machine and dumbbell lifts.
- **Explain** — on either suggestion screen, a tap asks Gemini for a short note on the findings above it. It is sent only the calculated findings, never the diary, and is never called unless you tap.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room (local SQLite persistence, single on-device database)
- Hilt (DI), Retrofit (networking)
- Gemini API for photo → food/portion recognition, reached through a small Vercel proxy in `server/` that lends every install one shared key
- ExerciseDB (AscendAPI's free tier) for exercise animations, fetched live and never stored: the phone asks it for the exercise id of each library row on screen, and nothing else about you. Offline, rows fall back to an equipment icon.

## Status

Sideloaded `.apk`. A short list of testers gets builds through Firebase App Distribution. See `PRD.md`, `TRD.md`, and `DESIGN-PRD.md` for the full product/technical spec and `BACKLOG.md` for what's in flight.

## Setup

1. Open in Android Studio, build and sideload the APK to your device. Nothing to sign up for — the app signs itself in anonymously on first launch.
2. Photo logging works immediately, on a key the proxy lends out. Nobody has to fetch one.
3. Optional: put your own Gemini key in Settings (free from [AI Studio](https://aistudio.google.com/apikey)). It is sealed with a key generated inside the Android Keystore that never leaves it, and it changes where calls go — straight to Google, on your quota, bypassing the proxy entirely. Clearing it goes back to shared.

## How photo logging is paid for

Calls go to the proxy in `server/`, which holds one Gemini key and forwards `generateContent` untouched. Requests carry a Firebase anonymous ID token; the proxy verifies it against this project and rejects anything else, so unzipping the APK does not get you a free Gemini endpoint.

Two consequences worth stating plainly:

- **Meal photos pass through that server.** Phone → proxy → Google, not phone → Google. The proxy never logs request or response bodies — only a truncated user id, the model, a status and a duration — but the hop exists. Setting your own key removes it.
- **The key is deliberately free-tier.** Abuse costs availability, not money. There is no per-user metering yet; that is what to add before the key is ever a paid one. See `BACKLOG.md`.

Deploying it needs two environment variables, both in `server/.env.example`.

## Shipping a build to testers

```
./gradlew assembleRelease appDistributionUploadRelease
```

Signs with the release keystore and uploads to the `friends` group in Firebase App Distribution, with the last commit subject as the release notes. Testers are managed in the Firebase console, not in the build file. Release signing needs `keystore.properties` and `tracker-release.jks` in the repo root — both gitignored, and losing them means no in-place updates ever again.

## License

MIT. See [LICENSE](LICENSE). Bundled fonts and data keep their own licences (see `app/src/main/assets/licences/`).
