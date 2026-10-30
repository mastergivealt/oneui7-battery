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

## v0.4.2 - settings storage changed
Settings.System rejected writes with IllegalArgumentException on this firmware. Settings now live
inside the module app (ConfigProvider, authority dev.claude.oneui7pill.cfg); SystemUI reads them via
that provider, only the app can write. No "Modify system settings" permission needed any more.
Install the new APK over the old one and restart SystemUI. Old oneui7pill_* system settings are ignored.

## v0.5.0 - look matched to One UI 7
- Number is now ONE color (inverse of the icon color), bold, centered over the whole pill - like One UI 7.
  (Old split-color number is still available: Look -> Number style.)
- Power saving shows a leaf in front of the number, normal colors (old yellow fill still available).
- Charging: green fill, white bolt in front of the number. "Neutral + bolt" gives the light/white-fill variant.
- Pill gets wider (26dp -> 32dp) while a bolt/leaf is shown, like One UI 7. "100" shrinks to fit.
- Low battery: red fill, track and number unchanged.
- Track is lighter on a dark status bar and softer on a light one.
- Fixed: stale "Grant permission" banner removed from the app, possible view leak in the holder.
Update: upload the changed files to the same repo, build, install over the old APK, restart SystemUI.

## v0.6.0 - status bar (experimental)
Three new sliders in "Notifications & Quick Settings" (master switch must be ON): status bar side
margins, icon spacing, clock size. One UI 7 has clearly larger left/right margins than One UI 6.1.
They rescale SystemUI dimens by NAME; the names are AOSP guesses (not verified on A136B).
"Status bar discovery log": logs every status-bar-looking dimen SystemUI reads, with its value, to the
Vector log as `[OneUI7Pill] dimen <name> = <value>`. Use it once, then the real names go into UiHook.

## v0.7.0 - One UI 7 status bar (battery cutout + wider margins)
- Number style "Cutout (One UI 7 stable)" is the new default: the number and the bolt/leaf are punched
  THROUGH the pill, so the status bar / wallpaper shows through them (white pill + see-through number),
  exactly like the One UI 7 reference renders. Old styles stay in Look -> Number style.
- New "Status bar -> Extra side margin" (0-28 dp, applies live): adds padding on both sides of the
  status bar contents view. One UI 7 is about +10 dp wider than 6.1. Works on the view itself, so it
  does not depend on dimen names (the older percent sliders still do).
- "Apply One UI 7 look (preset)" button: cutout number, leaf for power saving, neutral fill + bolt
  while charging, +10 dp margin.
- Vector log lines to check: `status bar contents found: ...` (works) or `status bar contents NOT
  found ... chain: ...` (send me that line).
Not included: the new bold wifi/signal icon artwork (those are separate system icon drawables).
