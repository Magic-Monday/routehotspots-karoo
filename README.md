# RouteHotspots for Hammerhead Karoo

Activate a route on your Karoo and the hotspots along it — cafés, lunch spots, viewpoints,
drinking water, bike shops and more — appear as icons on your map. No route to build first:
a hotspot overlay on whatever route you're already riding.

**Status: free beta.** Works on Karoo 3 (firmware 1.538.2049+); Karoo 2 via adb.
Download: [latest release](https://github.com/magic-monday/routehotspots-karoo/releases/latest)
· User guide and install steps: [routehotspots.com/karoo.html](https://routehotspots.com/karoo.html)
(Dutch: [/nl/karoo.html](https://routehotspots.com/nl/karoo.html))

## What it does

- **Hotspots on the map.** When a route becomes active, the extension reads its polyline,
  asks the RouteHotspots backend for the hotspots along it and draws them as POI symbols
  on the map page. A short in-ride alert tells you how many.
- **Your categories.** Coffee, lunch, viewpoint, monument, drinking water, bike shop,
  camping, charging point, toilet, supermarket — pick what you want in the extension
  settings (Settings → Extensions → RouteHotspots → OPEN).
- **Maximum detour.** 250 m, 500 m, 1 km or 3 km from the route. Anything further stays
  off the map.
- **Offline on the ride.** The Karoo switches wifi off as soon as a ride is active. Activate
  the route once at home with wifi; the hotspots are cached per route (30 days) and shown
  without network on the road. Without wifi and without cache you get an alert
  ("Turn on wifi for hotspots") and the extension retries by itself.

UI is English; Dutch when the Karoo is set to Dutch.

## Install

**Karoo 3:** open the [release page](https://github.com/magic-monday/routehotspots-karoo/releases/latest)
on your phone, long-press the `.apk` link, pick **Hammerhead** in the share menu, tap
**Install** on the Karoo. (Hammerhead's own guide:
[Companion App Sideloading](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading).)

**Karoo 2 / developers:** enable USB debugging, then

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## How it works

```
Karoo route (OnNavigationState.NavigatingRoute.routePolyline)
  → decode Google-encoded polyline → [[lon,lat], …]
  → POST https://app.routehotspots.com/api/poi/query
      { coords, categories, buffer_m (= max detour), expand:false, spread_m:1000 }
  → hotspots per category (OpenStreetMap via Overpass, along a buffered route line)
  → ShowSymbols(Symbol.POI …) on the extension map layer; HideSymbols on route change
```

The backend is public and needs no key. It lives in a separate (private) repository; the
same API powers the RouteHotspots web tool and the iOS app.

**No extension?** The web tool and the app can also export a GPX with the hotspots as
POIs in Hammerhead's format. Share that file with the Hammerhead app and the route syncs
to your Karoo with the hotspot icons built in — optionally re-routed via the hotspots.

## Build

Uses [karoo-ext](https://github.com/hammerheadnav/karoo-ext) 1.1.3 from GitHub Packages,
which always needs a token (`read:packages`), even for public packages. Put it in
`~/.gradle/gradle.properties` (never in git):

```properties
gpr.user=<github username>
gpr.key=<personal access token with read:packages>
```

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest   # JDK 17+, e.g. Android Studio's
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s RouteHotspots                            # live log while testing
```

Layout: `RouteHotspotsExtension.kt` (the `KarooExtension`: map layer, navigation state,
alerts, cache, retry), `RouteHotspotsApi.kt` (backend client, plain JVM, unit-testable),
`GooglePolyline.kt` (polyline decoder), `HotspotCache.kt`, `*Prefs.kt` (settings),
`screens/MainScreen.kt` (settings UI, Compose). Tests under `app/src/test`.

## Known limitations

- Pre-loaded routes only (`NavigatingRoute`). "Navigate to a destination" gets no hotspots yet.
- First fetch can take up to a minute when Overpass is busy; the backend fails over
  between mirrors.
- Debug build signed with a development key; not in the Hammerhead extension store yet.

## Field notes

Detailed findings from testing on a real Karoo (wifi behaviour, corrupt heatmap crash,
Overpass timeouts, Hammerhead's GPX POI rules) are in [docs/README-nl.md](docs/README-nl.md)
(Dutch).

## License and contact

Built by [Magic Monday](https://magicmonday.nl), Arnhem, the Netherlands. Feedback and
tester reports: via [routehotspots.com](https://routehotspots.com/contact.html) or an issue here.
