# Tracker

A single-user Android app for logging meals and gym sessions. One person, one phone: your diary never leaves the device and there is nothing to sign up for.

## What it does

- **Photo meal log** — photograph a plate, Gemini names each item and estimates its portion, you confirm or correct before it's saved. Nothing is written to the diary unedited.
- **Indian food aware** — dishes like roti, sabji, dal, poha, and idli are recognized and portioned in household units instead of being flattened into a generic "curry" estimate.
- **Packaged food from label photos** — scan a product's nutrition label once, reuse the exact numbers afterward instead of an AI guess.
- **Gym log** — set-by-set logging, with one-tap repeat when reps and weight match the last set.
- **Repeat meals** — previously logged meals are saved for two-tap re-entry.

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
2. Photo logging works immediately, on a key the proxy lends out. There is nothing to configure and no key to fetch.

## How photo logging is paid for

Calls go to the proxy in `server/`, which holds one Gemini key and forwards `generateContent` untouched. Requests carry a Firebase anonymous ID token; the proxy verifies it against this project and rejects anything else, so unzipping the APK does not get you a free Gemini endpoint.

Two consequences worth stating plainly:

- **Meal photos pass through that server.** Phone → proxy → Google, not phone → Google. The proxy never logs request or response bodies — only a truncated user id, the model, a status and a duration — but the hop exists, and there is no longer a setting that avoids it.
- **The proxy is a single point of failure for photo logging.** If it is down, photos cannot be read. The diary, the gym log and manual entry are all on-device and unaffected.
- **The key is deliberately free-tier.** Abuse costs availability, not money. There is no per-user metering yet; that is what to add before the key is ever a paid one. See `BACKLOG.md`.

Deploying it needs two environment variables, both in `server/.env.example`.

## Shipping a build to testers

```
./gradlew assembleRelease appDistributionUploadRelease
```

Signs with the release keystore and uploads to the `friends` group in Firebase App Distribution, with the last commit subject as the release notes. Testers are managed in the Firebase console, not in the build file. Release signing needs `keystore.properties` and `tracker-release.jks` in the repo root — both gitignored, and losing them means no in-place updates ever again.

## License

MIT. See [LICENSE](LICENSE). Bundled fonts and data keep their own licences (see `app/src/main/assets/licences/`).
