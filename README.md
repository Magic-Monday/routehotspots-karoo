# RouteHotspots — Hammerhead Karoo-extensie (starter-kit)

**Doel:** zodra er een route op je Karoo actief is, injecteert deze extensie automatisch
de **hotspots langs die route** als POI-markers op de kaart. Geen route vooraf bouwen —
een "hotspot-overlay" op je eigen route.

> Status: **starter-kit**. De netwerk-/datalaag is compleet; de karoo-ext-specifieke
> wiring (route uitlezen + POI's tekenen) moet in Android Studio tegen de live SDK worden
> afgemaakt (zie "F1" onder). Deze repo is bewust niet hier gebouwd — een Karoo-extensie
> vereist Android Studio + de Karoo om te sideloaden/testen.

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
- `app/src/main/kotlin/com/routehotspots/karoo/RouteHotspotsApi.kt` — **compleet.**
  Client + modellen voor `/api/poi/query`. Zuiver Kotlin/JVM, los testbaar.
- `app/src/main/kotlin/com/routehotspots/karoo/RouteHotspotsExtension.kt` — **skelet.**
  `KarooExtension`-subclass met de flow (verbind → lees actieve route → query → teken
  POI's). De twee SDK-specifieke stukken staan als `TODO(F1)` gemarkeerd.

## Opzet van het Android-project
Snelste basis: start van de officiële template en drop deze twee bestanden erin:
1. Ga naar **https://github.com/hammerheadnav/karoo-ext-template** → **"Use this template"**.
2. Clone 'm, zet de package op `com.routehotspots.karoo`, en plaats de twee `.kt`-bestanden.

### Dependency (karoo-ext via GitHub Packages)
In `settings.gradle.kts` / `build.gradle.kts`:
```kotlin
repositories {
  maven {
    url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
    credentials {
      username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("USERNAME")
      password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("TOKEN")
    }
  }
}
// ...
dependencies {
  implementation("io.hammerhead:karoo-ext:1.+")            // pak de nieuwste versie
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```
Zet `gpr.user` (je GitHub-username) en `gpr.key` (een GitHub **Personal Access Token** met
`read:packages`) in `local.properties` — GitHub Packages vereist altijd auth, óók voor
publieke packages.

### Extensie registreren (`AndroidManifest.xml`)
```xml
<service android:name="com.routehotspots.karoo.RouteHotspotsExtension" android:exported="true">
  <intent-filter>
    <action android:name="io.hammerhead.karooext.KAROO_EXTENSION" />
  </intent-filter>
  <meta-data android:name="io.hammerhead.karooext.EXTENSION_INFO"
             android:resource="@xml/extension_info" />
</service>
```
En `res/xml/extension_info.xml` met `id="routehotspots"` + displayName/icon (zie README van
karoo-ext voor het schema).

## F1 — het stuk dat in Android Studio af moet
In `RouteHotspotsExtension.kt`, tegen de SDK-docs
(https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/):
1. **Actieve route uitlezen** — consume het navigatie-event (vermoedelijk `OnNavigationState`)
   en haal de polyline eruit → map naar `[[lon,lat],...]` (let op volgorde: lon vóór lat).
2. **POI's tekenen** — dispatch het map-effect voor iconen-op-coördinaten (polylines/icons
   in een eigen kaartlaag) per hotspot; + een wis-effect bij route-wissel.
De netwerk-stap (`RouteHotspotsApi.hotspotsAlongRoute`) is al klaar.

## Bouwen & sideloaden
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Karoo in developer/sideload-modus
```
Activeer daarna een route op de Karoo → de hotspots horen te verschijnen.

## Backend-referentie
`integration_dashboard` → `app/routes/routehotspots.py` (`api_poi_query`, `POI_CATEGORIES`
voor de categorie-keys + emoji's die je als iconen kunt hergebruiken).
