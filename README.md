# LinguaScreen

Lightweight Android screen translator for Chinese apps (Xianyu, Taobao, 1688, Pinduoduo, WeChat, ...).
Tap a floating button, and English text is drawn over the Chinese text on screen. Everything runs on-device.

> **Status:** the code is written but **has not yet been compiled or run on a device** (the authoring
> sandbox could not reach Google's Android SDK / Maven hosts). The first step is to build it
> (CI does this, or run `./gradlew assembleDebug` locally) and fix any compile errors that show up.
> Nothing in the "How it works" section should be treated as verified until tested on a phone.

## How it works

1. **Start Translation** (or the *Floating button* switch) starts `OverlayService`, a foreground service that
   shows a draggable 译 button over other apps. Needs the "Display over other apps" permission.
2. Tap the button → panel: **Translate Screen**, **Pause Translation**, **Translation Settings**, **Close Floating Button**.
3. **Translate Screen**: the button hides, `ScreenCapturer` grabs one frame via MediaProjection, ML Kit Chinese OCR finds
   text blocks + bounding boxes, ML Kit on-device translation translates each block (cached, numbers/prices
   protected), and `TranslationOverlay` draws the English over the original positions (overlaps are pushed apart).
   Tap anywhere to dismiss; the pill offers *Show original*, *List view* (fallback panel) and *Close*.
4. Translations are saved to a local Room database (original, translation, time, source app if Usage access is granted).

Screen capture happens **only** on tap. The MediaProjection session keeps one idle VirtualDisplay (Android 14+ allows
one per consent) with its output detached, so nothing is rendered or copied between taps. Bitmaps are recycled right after OCR and never written to disk.

## Architecture

```
app/src/main/java/com/linguascreen/
  domain/     models, OcrEngine + TranslationProvider interfaces, TranslationPipeline, error types
  data/       ocr (ML Kit), translate (ML Kit provider, cache manager, model state), db (Room), prefs (DataStore), repo
  capture/    ScreenCapturer (MediaProjection), CaptureConsentActivity (system consent dialog)
  service/    OverlayService (foreground), OverlayState
  overlay/    FloatingButton, TranslationOverlay (plain Views: lighter than Compose inside a Service)
  ui/         Compose screens (home, history, settings) + ViewModels (MVVM), theme
  di/         Hilt modules (swap the TranslationProvider binding to add another engine)
  util/       pure helpers (text, overlay layout), screen metrics, foreground-app detector
```

## Build

Requirements: JDK 17+, Android SDK (platform 35, build-tools). Android Studio Koala+ opens the project directly.

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # unsigned unless keystore.properties exists
```

CI (`.github/workflows/android.yml`) builds both APKs and uploads them as the `linguascreen-apks` artifact.

### Release signing

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias linguascreen
```

Create `keystore.properties` in the project root (git-ignored):

```
storeFile=release.jks
storePassword=...
keyAlias=linguascreen
keyPassword=...
```

`assembleRelease` then produces a signed, minified, resource-shrunk APK.

## Testing on a physical device

1. Enable Developer options → USB debugging, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
   (or copy the APK to the phone).
2. Open LinguaScreen, tap **Download model** (Wi-Fi) and wait for "Ready".
3. Tap **Start Translation** → allow *Display over other apps* → come back, tap **Start Translation** again
   → allow notifications and the screen-capture dialog.
4. Open Xianyu / Taobao / 1688, tap the 译 button → **Translate Screen**. Check: text placement, overlap handling,
   price/number preservation, *Show original*, *List view*, dismiss by tap, rotation, pause/resume.
5. Turn on airplane mode and translate again to confirm offline operation.
6. Measure: `adb shell dumpsys meminfo com.linguascreen.debug` (RAM), `ls -l` on the APK (size), and the time from tap to overlay.

## Android restrictions and design decisions

- **Consent is mandatory.** MediaProjection always shows the system dialog; it can't be skipped. If the user stops sharing
  (status-bar chip) or presses *Pause*, consent is asked again on the next translation. Android 15 may offer
  "single app" sharing; choose "entire screen".
- **Secure windows** (`FLAG_SECURE`, e.g. some payment screens) come out black; the app detects this and says so instead of guessing.
- **Screen changes while the overlay is up:** the overlay is a snapshot. Rotation dismisses it; scrolling the app underneath
  isn't possible while it is shown (tap to dismiss, scroll, translate again). Continuous monitoring is deliberately out of the MVP.
- **Source app name** needs the optional *Usage access* permission; without it the history column is blank.
- **Models:** Chinese and English (pivot) plus the target language, ~30 MB each, downloaded once via ML Kit.
- No placeholder translations: if the model is missing the app says so rather than faking output.

## Privacy

No analytics, no accounts, no servers. Screenshots are processed in memory and discarded. History stays in a local database
(`allowBackup=false`). Network access is used only by ML Kit to download language models.
