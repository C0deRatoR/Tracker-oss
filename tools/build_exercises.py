#!/usr/bin/env python3
"""Build the bundled exercise library from the Strong catalogue.

Two sources:

  tools/strong_catalogue.json  253 rows read off Strong on the phone by tools/strong/ --
                               names, body parts and categories only. Strong is the
                               reference because its catalogue is the one that matches what
                               is actually in a gym: real names, one row per equipment
                               variant, and no long tail of movements nobody performs.
                               Those are facts about movements and carry no copyright. Its
                               instruction prose and preview art did, and are gone: see
                               BACKLOG.md.
  tools/seed_exercises.json    96 hand-built rows whose MET values come from the Compendium
                               of Physical Activities and whose rest defaults were chosen
                               per exercise. Only those two numbers are taken from here now;
                               the names themselves are Strong's.
  EXERCISEDB_ID                ExerciseDB (AscendAPI free tier) ids, one per row that has an
                               honest match, emitted as `art`. Ids only: the app fetches the
                               animation itself at run time, because the free tier's terms
                               forbid storing what it returns.
  yuhonas/free-exercise-db     876 rows under the Unlicense (public domain). Six fields
                               are taken: primary and secondary muscles, force, mechanic,
                               level, and the instructions. Text only -- the repo's images
                               were scraped and their provenance is unknown, so none of
                               them are used. Reaching them through MUSCLE_SOURCE rather
                               than by name is also what makes the coverage good: 232 of
                               253 rows carry steps this way, against 75 from Strong.

Strong's body part still decides muscle_group. free-exercise-db's is derived from whichever
muscle it happens to list first, which is what put "Barbell Squat" under quadriceps and
"Deadlift" under lower back; it is fine as detail and wrong as a heading.

This replaces a build over yuhonas/free-exercise-db, which shipped 808 rows. That set was
broad but wrong in the ways that matter on a gym floor -- "3/4 Sit-Up" as a catalogue entry,
nine near-duplicate rows per press, and muscle groups derived from whichever muscle happened
to be listed first.

Usage:
    uv run tools/strong/collect.py <scrape-dir>   # refresh tools/strong_catalogue.json
    curl -sL -o /tmp/fedb.json \
      https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json
    uv run tools/build_exercises.py /tmp/fedb.json
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOGUE = ROOT / "tools" / "strong_catalogue.json"
HAND_BUILT = ROOT / "tools" / "seed_exercises.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "seed" / "exercises.json"

# Bumped whenever the library changes, so an install that has already seeded knows to top
# itself up instead of staying on the version it first imported. 6 was the first Strong-based
# library, and the first to retire rows as well as add them; 7 re-runs the top-up so the
# fifteen rows that matched an old spelling ("Pull-Up") take the library's name; 8 re-runs it
# again to move trained history off the old rows the library renamed outright ("Pec Deck");
# 9 corrects one of those targets — the one-arm side lateral is a cable movement; 10 puts
# back the muscles, force, mechanic and level that the Strong rebuild dropped, which
# topUpExercises writes onto rows that are already seeded; 12 carries the ExerciseDB ids in
# `art`, which the same top-up writes onto seeded rows.
LIBRARY_VERSION = 12

MUSCLE_GROUP = {
    "chest": "CHEST",
    "back": "BACK",
    "shoulders": "SHOULDERS",
    "arms": "ARMS",
    "legs": "LEGS",
    "core": "CORE",
    "cardio": "CARDIO",
    "full body": "FULL_BODY",
    # Strong files the barbell lifts under their own heading. They are full-body movements
    # and the app has no Olympic group to put them in.
    "olympic": "FULL_BODY",
    "other": "FULL_BODY",
}

# What the bracket in a Strong name says the movement is loaded with. More specific than the
# category -- Strong files cable and machine work alike under "Machine/Other" -- so it wins.
BY_BRACKET = {
    "barbell": "BARBELL",
    "dumbbell": "DUMBBELL",
    "kettlebell": "DUMBBELL",
    "cable": "CABLE",
    "machine": "MACHINE",
    "smith machine": "MACHINE",
    "plate loaded": "MACHINE",
    "assisted": "MACHINE",
    "treadmill": "MACHINE",
    "indoor": "MACHINE",
    "captain's chair": "MACHINE",
    "bodyweight": "BODYWEIGHT",
    "knees": "BODYWEIGHT",
    "band": "OTHER",
    "plate": "OTHER",
    "stability ball": "OTHER",
}

BY_CATEGORY = {
    "barbell": "BARBELL",
    "dumbbell": "DUMBBELL",
    "machine/other": "MACHINE",
    "bodyweight": "BODYWEIGHT",
    "weighted bodyweight": "BODYWEIGHT",
    "assisted bodyweight": "MACHINE",
    "reps only": "BODYWEIGHT",
    "cardio": "OTHER",
    "duration": "BODYWEIGHT",
}

# Cardio rows whose category says nothing about what you stand on.
MACHINE_WORDS = ("treadmill", "elliptical", "bike", "cycling", "rowing", "stair", "ski erg")

# Names people say in a gym that the catalogue does not carry.
ALIASES = {
    "Bench Press (Barbell)": ["bench", "flat bench"],
    "Squat (Barbell)": ["back squat"],
    "Deadlift (Barbell)": ["conventional deadlift"],
    "Romanian Deadlift (Barbell)": ["rdl"],
    "Overhead Press (Barbell)": ["ohp", "military press", "shoulder press"],
    "Lat Pulldown (Cable)": ["pulldown"],
    "Seated Row (Cable)": ["cable row", "low row"],
    "Chest Fly (Machine)": ["pec dec", "pec deck"],
    "Rear Delt Reverse Fly (Machine)": ["reverse pec dec", "rear delt machine"],
    "Lateral Raise (Dumbbell)": ["side raise", "lat raise"],
    "Triceps Pushdown (Cable - Straight Bar)": ["tricep pushdown", "cable pushdown"],
    "Hip Thrust (Barbell)": ["glute bridge"],
    "Face Pull (Cable)": ["cable face pull"],
    "Hanging Leg Raise": ["leg raise"],
    "Bicep Curl (Dumbbell)": ["dumbbell curl"],
    "Bicep Curl (Barbell)": ["barbell curl"],
}


# Which free-exercise-db row describes the same movement as a Strong row.
#
# Written out one by one rather than matched at build time, for the reason BACKLOG.md gives:
# the closest string to "Back Extension" is "Leg Extensions", and hanging the wrong muscles
# off an exercise is worse than hanging none. Every pair below was read and accepted; the 18
# Strong rows that are not here are mostly cardio and sport ("Yoga", "Hiking") plus a few
# movements free-exercise-db does not carry, and they simply ship without the five fields.
#
# One source row may describe several Strong rows: "Leverage Shrug" is both the machine and
# the Smith-machine shrug, which differ in the rack, not in the muscles.
MUSCLE_SOURCE = {
    'Ab Wheel': 'Ab Roller',
    'Arnold Press (Dumbbell)': 'Arnold Dumbbell Press',
    'Around the World': 'Around The Worlds',
    'Back Extension': 'Hyperextensions (Back Extensions)',
    'Back Extension (Machine)': 'Hyperextensions (Back Extensions)',
    'Ball Slams': 'One-Arm Medicine Ball Slam',
    'Battle Ropes': 'Battling Ropes',
    'Bench Dip': 'Bench Dips',
    'Bench Press (Barbell)': 'Barbell Bench Press - Medium Grip',
    'Bench Press (Cable)': 'Barbell Bench Press - Medium Grip',
    'Bench Press (Dumbbell)': 'Dumbbell Bench Press',
    'Bench Press (Smith Machine)': 'Machine Bench Press',
    'Bench Press - Close Grip (Barbell)': 'Close-Grip Barbell Bench Press',
    'Bench Press - Wide Grip (Barbell)': 'Wide-Grip Barbell Bench Press',
    'Bent Over One Arm Row (Dumbbell)': 'One-Arm Dumbbell Row',
    'Bent Over Row (Band)': 'Bent Over Barbell Row',
    'Bent Over Row (Barbell)': 'Bent Over Barbell Row',
    'Bent Over Row (Dumbbell)': 'Bent Over Two-Dumbbell Row',
    'Bent Over Row - Underhand (Barbell)': 'Bent Over Barbell Row',
    'Bicep Curl (Barbell)': 'Barbell Curl',
    'Bicep Curl (Cable)': 'Standing Biceps Cable Curl',
    'Bicep Curl (Dumbbell)': 'Dumbbell Bicep Curl',
    'Bicep Curl (Machine)': 'Machine Bicep Curl',
    'Bicycle Crunch': 'Air Bike',
    'Box Jump': 'Front Box Jump',
    'Box Squat (Barbell)': 'Box Squat',
    'Bulgarian Split Squat': 'Split Squats',
    'Cable Crossover': 'Cable Crossover',
    'Cable Crunch': 'Cable Crunch',
    'Cable Kickback': 'One-Legged Cable Kickback',
    'Cable Pull Through': 'Pull Through',
    'Cable Twist': 'Cable Russian Twists',
    'Calf Press on Leg Press': 'Calf Press On The Leg Press Machine',
    'Calf Press on Seated Leg Press': 'Calf Press On The Leg Press Machine',
    'Chest Dip': 'Dips - Chest Version',
    'Chest Dip (Assisted)': 'Dips - Chest Version',
    'Chest Fly': 'Butterfly',
    'Chest Fly (Band)': 'Butterfly',
    'Chest Fly (Dumbbell)': 'Dumbbell Flyes',
    'Chest Press (Band)': 'Leverage Chest Press',
    'Chest Press (Machine)': 'Leverage Chest Press',
    'Chin Up': 'Chin-Up',
    'Chin Up (Assisted)': 'Chin-Up',
    'Clean (Barbell)': 'Clean',
    'Clean and Jerk (Barbell)': 'Clean and Jerk',
    'Concentration Curl (Dumbbell)': 'Concentration Curls',
    'Cross Body Crunch': 'Cross-Body Crunch',
    'Crunch': 'Crunches',
    'Crunch (Machine)': 'Ab Crunch Machine',
    'Crunch (Stability Ball)': 'Exercise Ball Crunch',
    'Cycling': 'Bicycling',
    'Cycling (Indoor)': 'Bicycling, Stationary',
    'Deadlift (Band)': 'Barbell Deadlift',
    'Deadlift (Barbell)': 'Barbell Deadlift',
    'Deadlift (Dumbbell)': 'Barbell Deadlift',
    'Deadlift (Smith Machine)': 'Leverage Deadlift',
    'Decline Bench Press (Barbell)': 'Decline Barbell Bench Press',
    'Decline Bench Press (Dumbbell)': 'Decline Dumbbell Bench Press',
    'Decline Bench Press (Smith Machine)': 'Decline Barbell Bench Press',
    'Decline Crunch': 'Decline Crunch',
    'Deficit Deadlift (Barbell)': 'Deficit Deadlift',
    'Elliptical Machine': 'Elliptical Trainer',
    'Face Pull (Cable)': 'Face Pull',
    'Flat Knee Raise': 'Flat Bench Lying Leg Raise',
    'Flat Leg Raise': 'Flat Bench Lying Leg Raise',
    'Floor Press (Barbell)': 'Floor Press',
    'Front Raise (Band)': 'Front Plate Raise',
    'Front Raise (Barbell)': 'Standing Front Barbell Raise Over Head',
    'Front Raise (Cable)': 'Front Cable Raise',
    'Front Raise (Dumbbell)': 'Front Dumbbell Raise',
    'Front Raise (Plate)': 'Front Plate Raise',
    'Front Squat (Barbell)': 'Front Barbell Squat',
    'Glute Ham Raise': 'Glute Ham Raise',
    'Glute Kickback (Machine)': 'Glute Kickback',
    'Goblet Squat (Kettlebell)': 'Goblet Squat',
    'Good Morning (Barbell)': 'Good Morning',
    'Hack Squat': 'Hack Squat',
    'Hack Squat (Barbell)': 'Barbell Hack Squat',
    'Hammer Curl (Band)': 'Hammer Curls',
    'Hammer Curl (Cable)': 'Cable Hammer Curls - Rope Attachment',
    'Hammer Curl (Dumbbell)': 'Hammer Curls',
    'Handstand Push Up': 'Handstand Push-Ups',
    'Hang Clean (Barbell)': 'Hang Clean',
    'Hang Snatch (Barbell)': 'Hang Snatch',
    'Hanging Knee Raise': 'Hanging Leg Raise',
    'Hanging Leg Raise': 'Hanging Leg Raise',
    'Hip Abductor (Machine)': 'Thigh Abductor',
    'Hip Adductor (Machine)': 'Thigh Adductor',
    'Hip Thrust (Barbell)': 'Barbell Hip Thrust',
    'Hip Thrust (Bodyweight)': 'Barbell Hip Thrust',
    'Incline Bench Press (Barbell)': 'Barbell Incline Bench Press - Medium Grip',
    'Incline Bench Press (Cable)': 'Barbell Incline Bench Press - Medium Grip',
    'Incline Bench Press (Dumbbell)': 'Hammer Grip Incline DB Bench Press',
    'Incline Bench Press (Smith Machine)': 'Smith Machine Incline Bench Press',
    'Incline Chest Fly (Dumbbell)': 'Incline Dumbbell Flyes',
    'Incline Chest Press (Machine)': 'Leverage Incline Chest Press',
    'Incline Curl (Dumbbell)': 'Incline Dumbbell Curl',
    'Incline Row (Dumbbell)': 'Dumbbell Incline Row',
    'Inverted Row (Bodyweight)': 'Inverted Row',
    'Iso-Lateral Chest Press (Machine)': 'Leverage Chest Press',
    'Iso-Lateral Row (Machine)': 'Leverage High Row',
    'Jackknife Sit Up': 'Jackknife Sit-Up',
    'Jump Rope': 'Rope Jumping',
    'Jump Shrug (Barbell)': 'Barbell Shrug',
    'Jump Squat': 'Freehand Jump Squat',
    'Kettlebell Swing': 'One-Arm Kettlebell Swings',
    'Kettlebell Turkish Get Up': 'Kettlebell Turkish Get-Up (Lunge style)',
    'Kipping Pull Up': 'Pullups',
    "Knee Raise (Captain's Chair)": 'Knee/Hip Raise On Parallel Bars',
    'Kneeling Pulldown (Band)': 'Wide-Grip Lat Pulldown',
    'Lat Pulldown (Cable)': 'Wide-Grip Lat Pulldown',
    'Lat Pulldown (Machine)': 'Wide-Grip Lat Pulldown',
    'Lat Pulldown (Single Arm)': 'One Arm Lat Pulldown',
    'Lat Pulldown - Underhand (Band)': 'Underhand Cable Pulldowns',
    'Lat Pulldown - Underhand (Cable)': 'Underhand Cable Pulldowns',
    'Lat Pulldown - Wide Grip (Cable)': 'Wide-Grip Lat Pulldown',
    'Lateral Box Jump': 'Lateral Box Jump',
    'Lateral Raise (Band)': 'Lateral Raise - With Bands',
    'Lateral Raise (Cable)': 'Cable Seated Lateral Raise',
    'Lateral Raise (Dumbbell)': 'Side Lateral Raise',
    'Lateral Raise (Machine)': 'Side Lateral Raise',
    'Leg Extension (Machine)': 'Leg Extensions',
    'Leg Press': 'Leg Press',
    'Lunge (Barbell)': 'Barbell Lunge',
    'Lunge (Bodyweight)': 'Bodyweight Walking Lunge',
    'Lunge (Dumbbell)': 'Dumbbell Lunges',
    'Lying Leg Curl (Machine)': 'Lying Leg Curls',
    'Mountain Climber': 'Mountain Climbers',
    'Muscle Up': 'Muscle Up',
    'Oblique Crunch': 'Decline Oblique Crunch',
    'Overhead Press (Barbell)': 'Standing Military Press',
    'Overhead Press (Cable)': 'Standing Military Press',
    'Overhead Press (Dumbbell)': 'Dumbbell Shoulder Press',
    'Overhead Press (Smith Machine)': 'Smith Machine Overhead Shoulder Press',
    'Overhead Squat (Barbell)': 'Overhead Squat',
    'Pec Deck (Machine)': 'Butterfly',
    'Pendlay Row (Barbell)': 'Bent Over Barbell Row',
    'Pistol Squat': 'Kettlebell Pistol Squat',
    'Plank': 'Plank',
    'Power Clean': 'Power Clean',
    'Power Snatch (Barbell)': 'Power Snatch',
    'Preacher Curl (Barbell)': 'Preacher Curl',
    'Preacher Curl (Dumbbell)': 'Preacher Hammer Dumbbell Curl',
    'Preacher Curl (Machine)': 'Machine Preacher Curls',
    'Pull Up': 'Pullups',
    'Pull Up (Assisted)': 'Pullups',
    'Pull Up (Band)': 'Band Assisted Pull-Up',
    'Pullover (Dumbbell)': 'Bent-Arm Dumbbell Pullover',
    'Pullover (Machine)': 'Bent-Arm Dumbbell Pullover',
    'Push Press': 'Push Press',
    'Push Up': 'Pushups',
    'Push Up (Band)': 'Pushups',
    'Push Up (Knees)': 'Pushups',
    'Rack Pull (Barbell)': 'Rack Pulls',
    'Reverse Crunch': 'Reverse Crunch',
    'Reverse Curl (Band)': 'Reverse Plate Curls',
    'Reverse Curl (Barbell)': 'Reverse Barbell Curl',
    'Reverse Curl (Dumbbell)': 'Standing Dumbbell Reverse Curl',
    'Reverse Fly (Cable)': 'Cable Rear Delt Fly',
    'Reverse Fly (Dumbbell)': 'Seated Bent-Over Rear Delt Raise',
    'Reverse Fly (Machine)': 'Reverse Machine Flyes',
    'Reverse Grip Concentration Curl (Dumbbell)': 'Concentration Curls',
    'Romanian Deadlift (Barbell)': 'Romanian Deadlift',
    'Romanian Deadlift (Dumbbell)': 'Romanian Deadlift',
    'Rowing (Machine)': 'Rowing, Stationary',
    'Running': 'Running, Treadmill',
    'Running (Treadmill)': 'Running, Treadmill',
    'Russian Twist': 'Russian Twist',
    'Seated Calf Raise (Machine)': 'Seated Calf Raise',
    'Seated Calf Raise (Plate Loaded)': 'Seated Calf Raise',
    'Seated Leg Curl (Machine)': 'Seated Leg Curl',
    'Seated Leg Press (Machine)': 'Leg Press',
    'Seated Overhead Press (Barbell)': 'Seated Barbell Military Press',
    'Seated Overhead Press (Dumbbell)': 'Dumbbell Shoulder Press',
    'Seated Palms Up Wrist Curl (Dumbbell)': 'Seated Dumbbell Palms-Up Wrist Curl',
    'Seated Row (Cable)': 'Seated Cable Rows',
    'Seated Row (Machine)': 'Seated Cable Rows',
    'Seated Wide-Grip Row (Cable)': 'Seated Cable Rows',
    'Shoulder Press (Machine)': 'Leverage Shoulder Press',
    'Shoulder Press (Plate Loaded)': 'Leverage Shoulder Press',
    'Shrug (Barbell)': 'Barbell Shrug',
    'Shrug (Dumbbell)': 'Dumbbell Shrug',
    'Shrug (Machine)': 'Leverage Shrug',
    'Shrug (Smith Machine)': 'Leverage Shrug',
    'Side Bend (Band)': 'Weighted Ball Side Bend',
    'Side Bend (Cable)': 'Dumbbell Side Bend',
    'Side Bend (Dumbbell)': 'Dumbbell Side Bend',
    'Side Plank': 'Side Bridge',
    'Single Leg Bridge': 'Single Leg Glute Bridge',
    'Sit Up': 'Sit-Up',
    'Skating': 'Skating',
    'Skullcrusher (Barbell)': 'EZ-Bar Skullcrusher',
    'Skullcrusher (Dumbbell)': 'Lying Triceps Press',
    'Snatch (Barbell)': 'Snatch',
    'Snatch Pull (Barbell)': 'Snatch Pull',
    'Split Jerk (Barbell)': 'Split Jerk',
    'Squat (Band)': 'Squats - With Bands',
    'Squat (Barbell)': 'Barbell Squat',
    'Squat (Bodyweight)': 'Bodyweight Squat',
    'Squat (Dumbbell)': 'Dumbbell Squat',
    'Squat (Machine)': 'Smith Machine Squat',
    'Squat (Smith Machine)': 'Smith Machine Squat',
    'Standing Calf Raise (Barbell)': 'Standing Barbell Calf Raise',
    'Standing Calf Raise (Bodyweight)': 'Standing Calf Raises',
    'Standing Calf Raise (Dumbbell)': 'Standing Dumbbell Calf Raise',
    'Standing Calf Raise (Machine)': 'Standing Calf Raises',
    'Standing Calf Raise (Smith Machine)': 'Standing Calf Raises',
    'Step-up': 'Step-up with Knee Raise',
    'Stiff Leg Deadlift (Barbell)': 'Stiff-Legged Barbell Deadlift',
    'Stiff Leg Deadlift (Dumbbell)': 'Stiff-Legged Dumbbell Deadlift',
    'Straight Leg Deadlift (Band)': 'Stiff-Legged Barbell Deadlift',
    'Strict Military Press (Barbell)': 'Standing Military Press',
    'Sumo Deadlift (Barbell)': 'Sumo Deadlift',
    'Superman': 'Superman',
    'T Bar Row': 'Lying T-Bar Row',
    'Thruster (Barbell)': 'Kettlebell Thruster',
    'Thruster (Kettlebell)': 'Kettlebell Thruster',
    'Toes To Bar': 'Hanging Leg Raise',
    'Torso Rotation (Machine)': 'Torso Rotation',
    'Trap Bar Deadlift': 'Trap Bar Deadlift',
    'Triceps Dip': 'Dips - Triceps Version',
    'Triceps Dip (Assisted)': 'Dips - Triceps Version',
    'Triceps Extension': 'Machine Triceps Extension',
    'Triceps Extension (Barbell)': 'Incline Barbell Triceps Extension',
    'Triceps Extension (Cable)': 'Cable Incline Triceps Extension',
    'Triceps Extension (Dumbbell)': 'Decline Dumbbell Triceps Extension',
    'Triceps Extension (Machine)': 'Machine Triceps Extension',
    'Triceps Pushdown (Cable - Straight Bar)': 'Triceps Pushdown',
    'Upright Row (Barbell)': 'Upright Barbell Row',
    'Upright Row (Cable)': 'Upright Cable Row',
    'Upright Row (Dumbbell)': 'Standing Dumbbell Upright Row',
    'Walking': 'Walking, Treadmill',
    'Wide Pull Up': 'Wide-Grip Rear Pull-Up',
    'Wrist Roller': 'Wrist Roller',
    'Zercher Squat (Barbell)': 'Zercher Squats',
}


# Which ExerciseDB (AscendAPI, free tier) exercise animates each Strong row.
#
# Only the id is bundled. The free tier forbids storing anything it returns, and its media
# URLs rotate every Monday, so the app asks for a fresh URL at run time; the ids themselves
# are documented as permanent. Hand-picked for the same reason as MUSCLE_SOURCE: a gif of the
# wrong variant is worse than the equipment mark, so the 69 rows ExerciseDB has no honest
# match for -- sport, most band work, the Olympic lifts -- are left out. Each comment is
# ExerciseDB's own name, so a pairing can be checked without calling the API.
EXERCISEDB_ID = {
    'Ab Wheel': 'NAgVB3t',  # wheel rollerout
    'Arnold Press (Dumbbell)': 'Xy4jlWA',  # dumbbell arnold press
    'Back Extension': 'zhMwOwE',  # hyperextension
    'Back Extension (Machine)': 'rUXfn3R',  # lever back extension
    'Ball Slams': 'oHg8eop',  # medicine ball overhead slam
    'Battle Ropes': 'RJa4tCo',  # battling ropes
    'Bench Dip': 'RrLske5',  # bench dip (knees bent)
    'Bench Press (Barbell)': 'EIeI8Vf',  # barbell bench press
    'Bench Press (Cable)': '7xI5MXA',  # cable bench press
    'Bench Press (Dumbbell)': 'SpYC0Kp',  # dumbbell bench press
    'Bench Press (Smith Machine)': 'trqKQv2',  # smith bench press
    'Bench Press - Close Grip (Barbell)': 'J6Dx1Mu',  # barbell close-grip bench press
    'Bench Press - Wide Grip (Barbell)': 'JsKq9so',  # barbell wide bench press
    'Bent Over One Arm Row (Dumbbell)': 'C0MA9bC',  # dumbbell one arm bent-over row
    'Bent Over Row (Barbell)': 'eZyBC3j',  # barbell bent over row
    'Bent Over Row (Dumbbell)': 'BJ0Hz5L',  # dumbbell bent over row
    'Bent Over Row - Underhand (Barbell)': 'SzX3uzM',  # barbell reverse grip bent over row
    'Bicep Curl (Barbell)': '25GPyDY',  # barbell curl
    'Bicep Curl (Cable)': 'G08RZcQ',  # cable curl
    'Bicep Curl (Dumbbell)': 'NbVPDMW',  # dumbbell biceps curl
    'Bicep Curl (Machine)': 'q6y3OhV',  # lever bicep curl
    'Bicycle Crunch': '1ZFqTDN',  # air bike
    'Burpee': 'dK9394r',  # burpee
    'Cable Crossover': '0CXGHya',  # cable cross-over variation
    'Cable Crunch': 'WW95auq',  # cable kneeling crunch
    'Cable Kickback': 'HEJ6DIX',  # cable kickback (triceps, as Strong files it under arms)
    'Cable Pull Through': 'OM46QHm',  # cable pull through (with rope)
    'Cable Twist': 'aVs3BR3',  # cable twist
    'Calf Press on Leg Press': 'ykHcWme',  # sled calf press on leg press
    'Calf Press on Seated Leg Press': 'Ie9UGty',  # lever seated calf press
    'Chest Dip': '9WTm7dq',  # chest dip
    'Chest Dip (Assisted)': 'PAgTVaK',  # assisted chest dip (kneeling)
    'Chest Fly': 'v3xmPAR',  # lever seated fly
    'Chest Fly (Dumbbell)': 'yz9nUhF',  # dumbbell fly
    'Chest Press (Machine)': 'T0yTjgW',  # lever chest press (selectorised)
    'Chin Up': 'T2mxWqc',  # chin-up
    'Chin Up (Assisted)': 'MaMuGH6',  # lever assisted chin-up
    'Concentration Curl (Dumbbell)': 'gvsWLQw',  # dumbbell concentration curl
    'Cross Body Crunch': 'rbu5UUb',  # cross body crunch
    'Crunch': 'TFqbd8t',  # crunch floor
    'Crunch (Machine)': 'Wgaz7pm',  # lever seated crunch
    'Crunch (Stability Ball)': 'MCUhf1F',  # crunch (on stability ball)
    'Cycling (Indoor)': 'H1PESYI',  # stationary bike run
    'Deadlift (Barbell)': 'ila4NZS',  # barbell deadlift
    'Deadlift (Dumbbell)': 'nUwVh7b',  # dumbbell deadlift
    'Deadlift (Smith Machine)': 'UfePqpx',  # smith deadlift
    'Decline Bench Press (Barbell)': 'GrO65fd',  # barbell decline bench press
    'Decline Bench Press (Dumbbell)': 'DwhEmmE',  # dumbbell decline bench press
    'Decline Bench Press (Smith Machine)': 'ETZfAbZ',  # smith decline bench press
    'Decline Crunch': '9Ap7miY',  # decline crunch
    'Elliptical Machine': 'rjtuP6X',  # walk elliptical cross trainer
    'Face Pull (Cable)': 'ZfyAGhK',  # cable standing rear delt row (with rope)
    'Flat Leg Raise': 'WhuFnR7',  # lying leg raise flat bench
    'Front Raise (Band)': 'TFA88iB',  # band front raise
    'Front Raise (Barbell)': 'b2Uoz54',  # barbell front raise
    'Front Raise (Cable)': 'u2X71Np',  # cable front raise
    'Front Raise (Dumbbell)': '3eGE2JC',  # dumbbell front raise
    'Front Raise (Plate)': 'e4aFmFY',  # weighted front raise
    'Front Squat (Barbell)': 'zG0zs85',  # barbell front squat
    'Glute Ham Raise': 'Vvwjz6N',  # glute-ham raise
    'Goblet Squat (Kettlebell)': 'ZA8b5hc',  # kettlebell goblet squat
    'Good Morning (Barbell)': 'XlZ4lAC',  # barbell good morning
    'Hack Squat': '5VCj6iH',  # barbell hack squat (Strong files it under barbell)
    'Hack Squat (Barbell)': '5VCj6iH',  # barbell hack squat
    'Hammer Curl (Cable)': 'HPlPoQA',  # cable hammer curl (with rope)
    'Hammer Curl (Dumbbell)': 'slDvUAU',  # dumbbell hammer curl
    'Handstand Push Up': 'rQxwMxO',  # handstand push-up
    'Hanging Leg Raise': 'I3tsCnC',  # hanging leg raise
    'Hip Abductor (Machine)': 'CHpahtl',  # lever seated hip abduction
    'Hip Adductor (Machine)': 'oHsrypV',  # lever seated hip adduction
    'Incline Bench Press (Barbell)': '3TZduzM',  # barbell incline bench press
    'Incline Bench Press (Cable)': 'Vh0GsK4',  # cable incline bench press
    'Incline Bench Press (Dumbbell)': 'ns0SIbU',  # dumbbell incline bench press
    'Incline Bench Press (Smith Machine)': '5v7KYld',  # smith incline bench press
    'Incline Chest Fly (Dumbbell)': 'ESOd5Pl',  # dumbbell incline fly
    'Incline Chest Press (Machine)': 'jHAnWmT',  # lever incline chest press
    'Incline Curl (Dumbbell)': 'ae9UoXQ',  # dumbbell incline curl
    'Incline Row (Dumbbell)': '7vG5o25',  # dumbbell incline row
    'Inverted Row (Bodyweight)': 'bZGHsAZ',  # inverted row
    'Iso-Lateral Chest Press (Machine)': 'DOoWcnA',  # lever chest press (plate-loaded)
    'Jackknife Sit Up': 'mbkgB44',  # jackknife sit-up
    'Jump Rope': 'e1e76I2',  # jump rope
    'Jump Squat': 'LIlE5Tn',  # jump squat
    'Kettlebell Swing': 'UHJlbu3',  # kettlebell swing
    'Kettlebell Turkish Get Up': 'Ha7SZ3y',  # kettlebell turkish get up (squat style)
    'Lat Pulldown (Cable)': 'RVwzP10',  # cable pulldown
    'Lat Pulldown (Machine)': '7F1DVzn',  # lever front pulldown
    'Lat Pulldown - Underhand (Band)': 'k6tUeqS',  # band underhand pulldown
    'Lat Pulldown - Underhand (Cable)': 'xBYcQHj',  # cable underhand pulldown
    'Lateral Raise (Cable)': 'goJ6ezq',  # cable lateral raise
    'Lateral Raise (Dumbbell)': 'DsgkuIt',  # dumbbell lateral raise
    'Lateral Raise (Machine)': 'dRTfGZT',  # lever lateral raise
    'Leg Extension (Machine)': 'my33uHU',  # lever leg extension
    'Leg Press': '2Qh2J1e',  # sled 45° leg press (side pov)
    'Lunge (Barbell)': 't8iSghb',  # barbell lunge
    'Lunge (Dumbbell)': 'RRWFUcw',  # dumbbell lunge
    'Lying Leg Curl (Machine)': '17lJ1kr',  # lever lying leg curl
    'Mountain Climber': 'RJgzwny',  # mountain climber
    'Muscle Up': 'yJUHKTn',  # muscle up
    'Oblique Crunch': 'QUDd8WS',  # oblique crunches floor
    'Overhead Press (Barbell)': 'Kyd9Rz5',  # barbell standing wide military press
    'Overhead Press (Cable)': 'PzQanLE',  # cable shoulder press
    'Overhead Press (Dumbbell)': 'A6wtbuL',  # dumbbell standing overhead press
    'Overhead Press (Smith Machine)': '903mzG8',  # smith shoulder press
    'Overhead Squat (Barbell)': 'gfk9kD4',  # barbell overhead squat
    'Pec Deck (Machine)': 'v3xmPAR',  # lever seated fly
    'Pendlay Row (Barbell)': 'r0z6xzQ',  # barbell pendlay row
    'Pistol Squat': 'nqs5HGV',  # single leg squat (pistol) male
    'Power Clean': 'SiWCcTN',  # power clean
    'Preacher Curl (Barbell)': 'qOgPVf6',  # barbell preacher curl
    'Preacher Curl (Dumbbell)': 'jivWf8n',  # dumbbell preacher curl
    'Preacher Curl (Machine)': 'b6hQYMb',  # lever preacher curl
    'Pull Up': 'lBDjFxJ',  # pull-up
    'Pull Up (Assisted)': 'kiJ4Z2K',  # assisted pull-up
    'Pull Up (Band)': 'r1XNRYB',  # band assisted pull-up
    'Pullover (Dumbbell)': '9XjtHvS',  # dumbbell pullover
    'Pullover (Machine)': '4U7iLb5',  # lever pullover
    'Push Up': 'I4hDWkc',  # push-up
    'Push Up (Knees)': 'ZOuKWir',  # kneeling push-up (male)
    'Rack Pull (Barbell)': 'za9Ni4z',  # barbell rack pull
    'Reverse Crunch': 'nCU1Ekp',  # reverse crunch
    'Reverse Curl (Barbell)': 'xNrS20v',  # barbell reverse curl
    'Reverse Curl (Dumbbell)': '0IgNjSM',  # dumbbell standing reverse curl
    'Reverse Fly (Cable)': 'P5p0j8B',  # cable standing cross-over high reverse fly
    'Reverse Fly (Dumbbell)': 'EAs3xL9',  # dumbbell reverse fly
    'Reverse Fly (Machine)': 'myfUsKf',  # lever seated reverse fly
    'Reverse Grip Concentration Curl (Dumbbell)': 'lyKCLmK',  # dumbbell seated revers grip concentration curl
    'Romanian Deadlift (Barbell)': 'wQ2c4XD',  # barbell romanian deadlift
    'Romanian Deadlift (Dumbbell)': 'rR0LJzx',  # dumbbell romanian deadlift
    'Running': 'oLrKqDH',  # run
    'Russian Twist': 'XVDdcoj',  # russian twist
    'Seated Calf Raise (Machine)': 'bOOdeyc',  # lever seated calf raise
    'Seated Calf Raise (Plate Loaded)': 'bOOdeyc',  # lever seated calf raise
    'Seated Leg Curl (Machine)': 'Zg3XY7P',  # lever seated leg curl
    'Seated Overhead Press (Barbell)': 'kTbSH9h',  # barbell seated overhead press
    'Seated Overhead Press (Dumbbell)': 'znQUdHY',  # dumbbell seated shoulder press
    'Seated Palms Up Wrist Curl (Dumbbell)': '2dImyQ8',  # dumbbell seated palms up wrist curl
    'Seated Row (Cable)': 'fUBheHs',  # cable seated row
    'Seated Row (Machine)': '7I6LNUG',  # lever seated row
    'Seated Wide-Grip Row (Cable)': 'qcY50ZD',  # cable seated wide-grip row
    'Shoulder Press (Machine)': '67n3r98',  # lever shoulder press
    'Shoulder Press (Plate Loaded)': '67n3r98',  # lever shoulder press
    'Shrug (Barbell)': 'dG7tG5y',  # barbell shrug
    'Shrug (Dumbbell)': 'NJzBsGJ',  # dumbbell shrug
    'Shrug (Machine)': 'ZZKbeMw',  # lever shrug
    'Shrug (Smith Machine)': 'OUQ0ZyW',  # smith shrug
    'Side Bend (Cable)': 'wPypxFY',  # cable side bend
    'Side Bend (Dumbbell)': 'IpONWYv',  # dumbbell side bend
    'Single Leg Bridge': 'rmEukuS',  # single leg bridge with outstretched leg
    'Sit Up': '6ZCiYWQ',  # sit-up with arms on chest
    'Skullcrusher (Barbell)': 'h8LFzo9',  # barbell lying triceps extension skull crusher
    'Skullcrusher (Dumbbell)': 'mpKZGWz',  # dumbbell lying triceps extension
    'Snatch Pull (Barbell)': 'dG5Smob',  # snatch pull
    'Squat (Band)': 'TUZLh71',  # band squat
    'Squat (Barbell)': 'qXTaZnJ',  # barbell full squat
    'Squat (Dumbbell)': 'HsvHqgf',  # dumbbell squat
    'Squat (Smith Machine)': 'jFtipLl',  # smith squat
    'Squat Row (Band)': 'w1NOByi',  # band squat row
    'Standing Calf Raise (Barbell)': '8ozhUIZ',  # barbell standing calf raise
    'Standing Calf Raise (Bodyweight)': 'bJYHBIN',  # bodyweight standing calf raise
    'Standing Calf Raise (Dumbbell)': 'dPmaUaU',  # dumbbell standing calf raise
    'Standing Calf Raise (Machine)': 'ykUOVze',  # lever standing calf raise
    'Standing Calf Raise (Smith Machine)': '6MaEjVA',  # smith standing leg calf raise
    'Stiff Leg Deadlift (Barbell)': 'hrVQWvE',  # barbell straight leg deadlift
    'Stiff Leg Deadlift (Dumbbell)': '5eLRITT',  # dumbbell stiff leg deadlift
    'Straight Leg Deadlift (Band)': 'KUaoUV8',  # band straight leg deadlift
    'Strict Military Press (Barbell)': 'Kyd9Rz5',  # barbell standing wide military press
    'Sumo Deadlift (Barbell)': 'KgI0tqW',  # barbell sumo deadlift
    'T Bar Row': 'aaXr7ld',  # lever t bar row
    'Thruster (Barbell)': 'f7Y9eDZ',  # barbell thruster
    'Thruster (Kettlebell)': 'yWxMvB5',  # kettlebell thruster
    'Trap Bar Deadlift': 'jQGwmxN',  # trap bar deadlift
    'Triceps Dip': 'X6C6i5Y',  # triceps dip
    'Triceps Dip (Assisted)': 'J60bN17',  # assisted triceps dip (kneeling)
    'Triceps Extension (Cable)': '2IxROQ1',  # cable overhead triceps extension (rope attachment)
    'Triceps Extension (Dumbbell)': 'kont8Ut',  # dumbbell seated triceps extension
    'Triceps Extension (Machine)': 'Ser9eQp',  # lever triceps extension
    'Triceps Pushdown (Cable - Straight Bar)': '3ZflifB',  # cable pushdown
    'Upright Row (Barbell)': 'UDlhcO8',  # barbell upright row
    'Upright Row (Cable)': 'cALKspW',  # cable upright row
    'Upright Row (Dumbbell)': 'ainizkb',  # dumbbell upright row
    'Wide Pull Up': 'Qqi7bko',  # wide grip pull-up
    'Wrist Roller': 'bd5b860',  # wrist rollerer
    'Zercher Squat (Barbell)': 'LSTChY9',  # barbell zercher squat
}


def bracket(name: str) -> str | None:
    """The equipment note Strong puts in brackets, normalised.

    "Triceps Pushdown (Cable - Straight Bar)" is a cable movement; the part after the dash
    only says which handle.
    """
    match = re.search(r"\(([^)]+)\)\s*$", name)
    if not match:
        return None
    return match.group(1).split(" - ")[0].strip().lower()


def equipment_of(name: str, category: str | None) -> str:
    from_bracket = BY_BRACKET.get(bracket(name) or "")
    if from_bracket:
        return from_bracket

    mapped = BY_CATEGORY.get((category or "").lower())
    if mapped in (None, "OTHER", "BODYWEIGHT"):
        # A duration or cardio row can still be a machine; the name is the only thing that
        # says so, since Strong files all of them under the same category.
        if any(word in name.lower() for word in MACHINE_WORDS):
            return "MACHINE"
    return mapped or "OTHER"


def tokens(name: str) -> frozenset[str]:
    return frozenset(re.findall(r"[a-z0-9]+", name.lower()))


def met_and_rest(muscle_group: str, equipment: str, is_cardio: bool) -> tuple[float, int]:
    """Compendium-style MET plus a starting rest, for movements the hand-built list does not
    carry. The app overrides the rest per exercise once a session has been logged."""
    if is_cardio:
        return 7.0, 60
    if equipment in ("BARBELL", "DUMBBELL") and muscle_group in ("LEGS", "BACK", "FULL_BODY"):
        return 6.0, 180
    if equipment in ("BARBELL", "DUMBBELL"):
        return 5.0, 120
    if equipment == "BODYWEIGHT":
        return 4.0, 90
    return 3.5, 90


def hand_built_index(rows: list[dict]) -> dict[frozenset[str], dict]:
    """Hand-built rows keyed by their words, so "Barbell Back Squat" can be found from
    Strong's "Squat (Barbell)"."""
    index: dict[frozenset[str], dict] = {}
    for row in rows:
        index[tokens(row["name"])] = row
    return index


def source_detail(source_rows: list[dict]) -> dict[str, dict]:
    """The six free-exercise-db fields, keyed by that source's own name."""
    return {
        row["name"]: {
            "primary_muscles": row.get("primaryMuscles") or [],
            "secondary_muscles": row.get("secondaryMuscles") or [],
            "force": row.get("force"),
            "mechanic": row.get("mechanic"),
            "level": row.get("level"),
            "instructions": row.get("instructions") or [],
        }
        for row in source_rows
    }


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(
            "usage: build_exercises.py <free-exercise-db dist/exercises.json>\n"
            "  curl -sL -o /tmp/fedb.json https://raw.githubusercontent.com/"
            "yuhonas/free-exercise-db/main/dist/exercises.json",
        )

    catalogue = json.loads(CATALOGUE.read_text())["exercises"]
    hand_built = json.loads(HAND_BUILT.read_text())["exercises"]
    index = hand_built_index(hand_built)
    detail = source_detail(json.loads(Path(sys.argv[1]).read_text()))

    # A table entry naming a row the source no longer carries would silently ship an exercise
    # without muscles, which is exactly the state this build exists to fix.
    unknown = sorted({v for v in MUSCLE_SOURCE.values() if v not in detail})
    if unknown:
        raise SystemExit(f"MUSCLE_SOURCE points at rows free-exercise-db does not have: {unknown}")

    matched = 0
    rows = []
    for entry in catalogue:
        name = entry["name"]
        body_part = (entry["body_part"] or "").lower()
        muscle_group = MUSCLE_GROUP.get(body_part, "FULL_BODY")
        is_cardio = body_part == "cardio"
        equipment = equipment_of(name, entry.get("category"))

        # The hand-built numbers win where the same movement is recognisable. An exact word
        # match first, then a containment match, and only when it is unambiguous -- two
        # candidates means the name is too vague to be sure which MET applies.
        key = tokens(name)
        hand = index.get(key)
        if hand is None:
            near = [row for k, row in index.items() if k <= key or key <= k]
            hand = near[0] if len(near) == 1 else None
        if hand is not None:
            matched += 1

        met = hand["met_value"] if hand else None
        rest = hand["default_rest_sec"] if hand else None
        if met is None or rest is None:
            met, rest = met_and_rest(muscle_group, equipment, is_cardio)

        row = {
            "name": name,
            "muscle_group": muscle_group,
            "equipment": equipment,
            "type": "CARDIO" if is_cardio else "STRENGTH",
            "met_value": met,
            "default_rest_sec": rest,
            # Filled from free-exercise-db below where the table pairs this row. The 18 rows
            # it does not name -- cardio and sport, mostly -- ship without steps rather than
            # with someone else's.
            "instructions": [],
        }
        # Muscles, force, mechanic, level and the how-to. muscle_group is left alone: Strong's
        # body part decides the heading, this is only the detail under it.
        source = MUSCLE_SOURCE.get(name)
        if source:
            row.update(detail[source])

        if name in ALIASES:
            row["aliases"] = ALIASES[name]
        if name in EXERCISEDB_ID:
            row["art"] = EXERCISEDB_ID[name]
        rows.append(row)

    rows.sort(key=lambda r: r["name"])
    OUT.write_text(
        json.dumps({"version": LIBRARY_VERSION, "exercises": rows}, indent=1, ensure_ascii=False)
        + "\n",
    )

    with_steps = sum(1 for r in rows if r["instructions"])
    with_muscles = sum(1 for r in rows if r.get("primary_muscles"))
    with_force = sum(1 for r in rows if r.get("force"))
    with_art = sum(1 for r in rows if r.get("art"))
    print(
        f"{len(rows)} exercises -> {OUT}\n"
        f"  {matched} took MET and rest from the hand-built list\n"
        f"  {with_steps} with instructions\n"
        f"  {with_muscles} with muscles, {with_force} with a push/pull force\n"
        f"  {with_art} with an ExerciseDB animation",
    )


if __name__ == "__main__":
    main()
