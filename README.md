# RouteHotspots — Hammerhead Karoo-extensie

**Doel:** zodra er een route op je Karoo actief is, injecteert deze extensie automatisch
de **hotspots langs die route** als POI-markers op de kaart. Geen route vooraf bouwen —
een "hotspot-overlay" op je eigen route.

> Status: **F1 geïmplementeerd, nog niet gebouwd/getest.** Volledig Android-gradle-project
> (uit `hammerheadnav/karoo-ext-template`) + de karoo-ext-specifieke wiring (route uitlezen
> + POI's tekenen). Nog te doen: in Android Studio openen, syncen, op de Karoo sideloaden
> en met een echte route testen — zie "Bouwen & sideloaden" onder.

## Architectuur
De extensie praat **niet** met de iOS-app, maar met dezelfde backend:
```
Karoo-extensie ──POST /api/poi/query──► app.routehotspots.com
  (actieve route-polyline + categorieën)        │
                                                 ▼
                                   hotspots langs de route (Overpass-buffer)
```
De backend-logica bestaat al (`app/routes/routehotspots.py::api_poi_query`, publiek, geen
auth). Request: `{ coords:[[lon,lat],...], categories:[...], buffer_m:3000 }`.

## Inhoud
- `app/src/main/kotlin/com/routehotspots/karoo/RouteHotspotsApi.kt` — client + modellen
  voor `/api/poi/query`. Zuiver Kotlin/JVM, los testbaar.
- `app/src/main/kotlin/com/routehotspots/karoo/GooglePolyline.kt` — decoder voor Google's
  Encoded Polyline Format (precisie 1e5), dat `NavigationState.routePolyline` gebruikt.
  Eigen implementatie i.p.v. de Mapbox-geojson-dependency (scheelt een los maven-repo).
- `app/src/main/kotlin/com/routehotspots/karoo/KarooSystemExtensions.kt` — `consumerFlow()`,
  1-op-1 overgenomen uit de officiële sample-extensie (zit niet in de gepubliceerde jar).
- `app/src/main/kotlin/com/routehotspots/karoo/RouteHotspotsExtension.kt` — de
  `KarooExtension`: `startMap()` → consumeert `OnNavigationState`, decodeert de actieve
  route, haalt hotspots op, tekent `ShowSymbols`/wist met `HideSymbols` bij route-wissel.
- `app/src/test/kotlin/.../GooglePolylineTest.kt` + `RouteHotspotsExtensionTest.kt` —
  unit tests (polyline-decoder tegen Google's eigen referentievoorbeeld; categorie→icoon-
  mapping). Puur JVM, geen Android-instrumentatie nodig: `./gradlew testDebugUnitTest`.

Geverifieerd tegen de live SDK-docs (`hammerheadnav.github.io/karoo-ext`) en de officiële
sample-extensie (`hammerheadnav/karoo-ext` → `app/.../SampleExtension.kt`):
`OnNavigationState.NavigationState.NavigatingRoute.routePolyline` (String, Google-encoded),
`startMap(emitter: Emitter<MapEffect>)` (vereist `mapLayer="true"`), `ShowSymbols`/
`HideSymbols`, `Symbol.POI(id, lat, lng, type, name)` met `Symbol.POI.Types.*` voor het
icoon.

## Dependency (karoo-ext via GitHub Packages)
`settings.gradle.kts` wijst al naar `https://maven.pkg.github.com/hammerheadnav/karoo-ext`
met credentials uit `gpr.user`/`gpr.key`. GitHub Packages vereist **altijd** auth, óók voor
publieke packages.

**Zet je token in `~/.gradle/gradle.properties`** (gebruikersbreed, dus nooit in git, en
werkt voor elk project op deze Mac dat dezelfde properties-namen gebruikt) — **niet** in
`local.properties` (dat leest Gradle niet automatisch als property; `local.properties` is
puur voor `sdk.dir`):
```properties
# ~/.gradle/gradle.properties
gpr.user=<je-github-username>
gpr.key=<personal access token met scope read:packages>
```
Token aanmaken: GitHub → Settings → Developer settings → Personal access tokens
(classic volstaat) → scope **`read:packages`**.

## Extensie-registratie
- `app/src/main/AndroidManifest.xml` — service `com.routehotspots.karoo.RouteHotspotsExtension`,
  intent-filter `io.hammerhead.karooext.KAROO_EXTENSION`.
- `app/src/main/res/xml/extension_info.xml` — `id="routehotspots"`, `mapLayer="true"`.

## Bouwen & sideloaden
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Karoo in developer/sideload-modus, USB
```
Activeer daarna een route op de Karoo → de hotspots horen te verschijnen als POI-iconen.

## Nog niet gedaan / te verifiëren in Android Studio
- **Build zelf nog niet gedraaid** — kon hier niet (geen token, geen Karoo aangesloten).
  `./gradlew testDebugUnitTest` zou wel moeten lukken zodra `gpr.user`/`gpr.key` staan,
  zonder dat de Karoo nodig is (puur JVM-tests).
- **`routePolyline`-precisie aangenomen als 1e5** (de Mapbox `PolylineUtils.encode(_, 5)`-
  aanroep in de officiële sample suggereert dit, maar is niet 1-op-1 voor `routePolyline`
  zelf bevestigd in de docs) — als de hotspots op het eerste gezicht systematisch verschoven
  staan, is dit de eerste plek om te checken.
- **`NavigatingRoute` vs. `NavigatingToDestination`** — de extensie injecteert alleen op een
  vooraf geladen route (`NavigatingRoute`), niet op ad-hoc "navigeer naar X". Klopt dat met
  wat je op de Karoo daadwerkelijk als "actieve route" ervaart?
- **POI-limiet van de kaartlaag** — onbekend hoeveel symbolen de Karoo-kaart in de praktijk
  aankan; bij een lange route met veel hotspots kan filteren/clusteren nodig worden (F3 in
  het plan).

## Backend-referentie
`integration_dashboard` → `app/routes/routehotspots.py` (`api_poi_query`, `POI_CATEGORIES`
voor de categorie-keys + emoji's die je als iconen kunt hergebruiken).
