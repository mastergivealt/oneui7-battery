# One UI 7 Battery Pill  (Vector / LSPosed module for SM-A136B, One UI 6.1 Core)

Hooks `com.android.systemui.battery.BatteryMeterView` (class + field names confirmed from your
firmware A136BXXSDDYK2) and replaces the icon with a One UI 7 style pill:
percentage INSIDE the pill, fill by level, green + bolt while charging, yellow in power saving,
red at <=15%. If anything fails, SystemUI keeps the stock icon (all hooks are try/catch-wrapped).

## Build (no Android Studio needed)
1. Create a free GitHub account + a new empty repository.
2. Upload ALL files of this folder to it (keep the folder structure, incl. `.github/workflows/build.yml`).
3. Open the repo -> "Actions" tab -> "Build APK" -> wait ~3-5 min.
4. Open the finished run -> Artifacts -> download "OneUI7Pill-apk" -> unzip -> `app-debug.apk`.
(If the build fails, send me the red error text from the log.)

## Install
1. Install `app-debug.apk`.
2. Open the Vector manager -> Modules -> enable "One UI 7 Battery Pill" -> scope must show **System UI**.
3. Restart SystemUI (or reboot).
4. Vector manager -> Logs: look for `[OneUI7Pill] hooked N methods on BatteryMeterView`.

## If something looks wrong / to undo
Vector manager -> disable the module -> restart SystemUI. Nothing on /system is modified.

## Tweaks
`PillDrawable.java` top: WIDTH_DP / HEIGHT_DP / colours / LOW_LEVEL.

## v0.2 - animations
- Plug in: green fill wipes in from the left, bolt pops in with an overshoot.
- While charging: a soft light sweep glides over the fill every ~4 s.
- Level changes glide smoothly; colour changes (charging / power saving / low) cross-fade.
- Redraws only while an animation is running (no constant redraw).
Timings are constants at the top of PillDrawable.java (INTRO_MS, SHIMMER_PERIOD_MS, ...).
Update: upload the changed files to the same repo, build again, install over the old APK, restart SystemUI.

## v0.3 - settings app (live, turn anything off any time)
Open the "One UI 7 Pill" app (or the module's settings button in Vector). First tap
"Grant permission" (Modify system settings) - this is how the app talks to SystemUI.
Options: enable/disable pill (off = stock icon), animations, bolt, number inside, size 70-140%,
low-battery threshold, charging color. Changes apply live; if one does not, restart SystemUI.
Root alternative: `settings put system oneui7pill_enabled 0` (keys are in PillConfig.java).
AOD: not possible on the Galaxy A13 5G (LCD panel, no AOD in Samsung's firmware).

## v0.4 - notification + Quick Settings tweaks (experimental)
New section in the app: "Notifications & Quick Settings". Master switch is OFF by default.
It rescales SystemUI sizes by resource name (names taken from the A136B / One UI 6.1 probe-4 report):
notification card corners, notification group spacing, notification side margin,
Quick Settings corners, brightness slider corners, Quick Settings side padding. 100% = stock.
- Only dimens that SystemUI reads from code are caught; values read while inflating XML are not.
- SystemUI caches most values: restart SystemUI (or reboot) after a change.
- To go back to stock: switch it off (or Reset to defaults) and restart SystemUI.
- Not included: shade transparency/blur (the probe did not show whether those values are int, float
  or color, so scaling them blindly could break the shade), One UI 7 layouts, Now Bar.
