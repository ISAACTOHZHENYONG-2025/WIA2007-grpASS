# RainReady map check (throwaway test app)

This is for Emmanuel's week 1 card "OpenFreeMap check: UM map with zone shapes on the cheapest
phone". It is a separate, throwaway Gradle project inside `experiments/`, so it is **not part of
the RainReady app build**. The real map goes into Jingying's skeleton app later, and this folder
can be deleted after that.

What it does:

- Loads the OpenFreeMap "Liberty" style (`https://tiles.openfreemap.org/styles/liberty`) with
  MapLibre Native for Android 13.7.1. No API key and no sign-up.
- Centres on UM (3.1231 N, 101.6529 E) at zoom 15.
- Draws 4 rough test zones from `app/src/main/assets/zones.geojson`, placed over FSKTM, Science,
  Main Library and Perdanasiswa, and Engineering. The positions come from OpenStreetMap.
  Clear = green with a thin outline, Caution = amber with a dashed outline, Flooded = red with a
  thick outline.
- Shows timings on screen: style loaded, map loaded, fully drawn, and map frames per second.

## Run it on the cheapest phone

1. On the phone: Settings → About phone → tap "Build number" 7 times, then Developer options →
   turn on USB debugging. Plug it in and allow the laptop.
2. Open this folder in Android Studio (File → Open → `experiments/map-check`), not the repo root.
3. Pick the phone in the device menu and press Run.
4. **First launch after installing is the cold test**, with nothing cached. Wait for "Fully drawn".
5. Pan and zoom around campus for about 10 seconds. Read "peak" frames per second, and note any
   stutter or blank tiles you see.
6. Tap **Reload** for a warm run (tiles now cached). To repeat a cold run, open
   Settings → Apps → RainReady map check → Storage → Clear storage, then open it again.
7. Tap **Copy results** and paste the text into your Drive note.

Try it once on Wi-Fi and once on mobile data if you can. UM Wi-Fi and mobile data behave
differently.

## If the map is blank or the app crashes on the phone

MapLibre 13 draws with **Vulkan** by default (the emulator log shows a Vulkan device). Older and
cheaper phones often have weak Vulkan drivers. MapLibre also publishes an OpenGL build, so try that
before deciding OpenFreeMap does not work:

1. In `gradle/libs.versions.toml`, change `name = "android-sdk"` to `name = "android-sdk-opengl"`
   on the `maplibre-android` line.
2. Sync Gradle, run again, and write down both results (Vulkan and OpenGL) in the note.

## Changing the zones

Draw your own at geojson.io, export as GeoJSON, and replace `zones.geojson`. Give every shape
two properties: `"name"` and `"status"`, where status is `clear`, `caution` or `flooded`.

## Moving this into the skeleton app later

- Add `org.maplibre.gl:android-sdk` to the version catalogue and app dependencies.
- Call `MapLibre.getInstance(context)` before creating any `MapView`.
- Host the `MapView` in Compose with `AndroidView`, and forward the lifecycle as
  `MapCheckScreen()` does in `MainActivity.kt`.
- Keep MapLibre's attribution button visible: it shows the OpenStreetMap credit.
