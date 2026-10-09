# RouteHotspots — Hammerhead Karoo-extensie

**Doel:** zodra er een route op je Karoo actief is, injecteert deze extensie automatisch
de **hotspots langs die route** als POI-markers op de kaart. Geen route vooraf bouwen —
een "hotspot-overlay" op je eigen route.

> Status: **F1 werkt, live bevestigd op een echte Karoo (model k24) op 4 okt 2026.** Route
> "Utrecht terug" geactiveerd → 2456 polyline-coords gedecodeerd → 218 hotspots opgehaald →
> als koffie-/etc.-iconen op het kaartscherm getekend. Zie "Live-test — bevindingen" onder
> voor wat daarbij ontdekt is en nog open staat.

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

## Live-test — bevindingen (4 okt 2026, Karoo k24)
Twee echte bugs gevonden en gefixt tijdens het live-testen (beide al in deze repo):
- **`INTERNET`-permissie ontbreekt in het manifest** — zonder die regel blokkeert Android
  elke netwerkcall met een `SecurityException`/`EPERM`. Toegevoegd in `AndroidManifest.xml`.
- **Template-schermpjes (`MainActivity`/`MainScreen`/`Theme`) stonden nog in het oude
  package `io.hammerhead.karooexttemplate`**, terwijl de `R`-klasse onder de echte namespace
  (`com.routehotspots.karoo`) hoort — `compileDebugKotlin` faalde op `Unresolved reference R`.
  Verplaatst naar het juiste package.

Bevestigd, geen bug maar goed om te weten:
- **`routePolyline`-precisie 1e5 is correct** — de polyline van een echte route ("Utrecht
  terug") decodeerde naar 2456 zinnige coördinaten in Nederland; geen verschoven hotspots.
- **De backend-aanroep duurde ~55 seconden** (5 categorieën × een aparte Overpass-call per
  categorie in `api_poi_query`, elk tot 45s timeout). Dat is griezelig dicht bij onze eigen
  client-`readTimeout` van 60s in `RouteHotspotsApi.kt` — bij een trage Overpass-respons
  kan dit alsnog timen out. Kandidaten voor een vervolgstap: timeout verhogen, categorieën
  parallelliseren server-side, of client-side minder categorieën tegelijk opvragen.
- **218 hotspots op één route is visueel veel** — bij lage zoom clusteren de POI-iconen tot
  onleesbare "bubbels" op de kaart. Bevestigd op de Hammerhead-webdashboard-kaart van
  dezelfde route (`dashboard.hammerhead.io`) dat de hotspots wél écht over de volle 74,6km
  verspreid liggen — geen plaatsingsbug, puur te dicht op elkaar.
- **Een crashende, andere Karoo-extensie herlaadde het kaartscherm** tijdens het testen,
  wat onze lopende aanvraag afbrak (de coroutine-scope wordt gecancelled bij `startMap`-
  unbind). Geen bug in RouteHotspots, maar een reminder dat de kaartlaag op een drukke Karoo
  vaker kan herladen dan je zou denken.

Derde fix, ná het eerste succesvolle resultaat:
- **`spread_m` ontbrak in de request-body.** De backend heeft spreiding (`_spread_pois`,
  minimale afstand tussen POI's van dezelfde categorie langs de route) al ingebouwd, maar
  past 'm alléén toe als de client 'm expliciet meestuurt — default is 0 (uit). Dat
  verklaarde de 218. Toegevoegd aan `RouteHotspotsApi.hotspotsAlongRoute()` met een default
  van 1000m. **Nog niet live herbevestigd** (zie onder) — wel unit-getest en code-identiek
  aan het al-bewezen request-pad (één extra JSON-veld).
- **Bug: een mislukte poging blokkeerde retries.** `lastRouteKey` werd gezet vóórdat de
  netwerkcall werd geprobeerd, dus een wifi-hapering "verbruikte" de poging voorgoed voor
  die route-instantie — pas een echte state-wisseling (route uit/aan) gaf een nieuwe kans.
  Gefixt: `lastRouteKey` wordt nu pas gezet ná een geslaagde call. **Bewezen werkend** tijdens
  het live-testen: de Karoo emit kennelijk periodiek een vers `NavigatingRoute`-event (ook
  zonder dat de route zelf wijzigt), waardoor de extensie na deze fix vanzelf om de ~5
  seconden opnieuw probeert tot het lukt — relevant voor een echte rit met wisselende dekking.

## Live-test — bevindingen (9 okt 2026, Karoo k24, F2 + meldingen)
- **Route opende niet (klapte terug naar de routelijst).** Niet onze bug: Hammerheads
  eigen `io.hammerhead.routesapp` crashte vijf keer op
  `SQLiteDatabaseCorruptException` in `/sdcard/offline/personal_heatmap.mbtiles`
  (persoonlijke-heatmap-kaartlaag). Integriteitscheck op een kopie bevestigde het.
  Opgelost door het bestand te hernoemen naar `*.corrupt-20261009`; de Karoo bouwt
  de heatmap zelf opnieuw. Herkennen: `adb logcat -d '*:E' | grep -A3 FATAL`.
- **De Karoo zet wifi zélf uit zodra een rit/route actief wordt.** Logcat:
  `HxStateManagerService: handleUpdateWifi - disabling for ACTIVE state` /
  `re-enabling for INACTIVE state`. Dus "live ophalen tijdens de rit" werkt alleen
  als de rijder wifi handmatig aanzet via het control center (dat blijft dan aan).
  Daarom toont de extensie nu een `InRideAlert` "Zet wifi aan voor hotspots" (één
  keer per route, `ACCESS_NETWORK_STATE`-check vóór de call) en blijft hij stil
  retryen tot er netwerk is.
- **"0 hotspots ontvangen" was een backend-/Overpass-probleem.** overpass-api.de gaf
  HTTP 504 op de 75 km-route; de backend zette dat in `errors_by_category` en
  antwoordde tóch 200 met nul POI's. De extensie leest dat veld nu: falen álle
  gevraagde categorieën, dan geldt het als mislukt (melding "Hotspots nu niet
  beschikbaar", geen retry-blokkade). Backend-fix (bbox in de Overpass-query +
  failover over mirrors) staat in integration_dashboard op
  `feat/routehotspots-overpass-bbox-failover`.
- **`spread_m=1000` live bevestigd:** 26 hotspots (lunch+koffie) i.p.v. 218.
- **Verlaat antwoord na herbinden van de kaartlaag:** de blokkerende HTTP-call is niet
  te onderbreken; een antwoord van een al-losgekoppelde `startMap`-scope toonde een
  verkeerde melding. Nu: na de call `currentCoroutineContext().isActive` checken.
- **USB-debugging stond uit** (Karoo meldde zich alleen als USB-schijf, `ioreg` toonde
  enkel een mass-storage-interface). Instellingen → Ontwikkelaarsopties → USB-foutopsporing.

## Nog open
- **Wifi-tijdens-de-rit is bevestigd de verkeerde aanname** (zie 9 okt). De melding is
  de pleister; de echte oplossing is "vooraf ophalen zodra er wifi is, lokaal cachen":
  hotspots ophalen zodra een route wordt gekozen (vóór de rit start, als wifi nog aan
  staat) of bij het synchroniseren thuis, en per route-id cachen.
- **POI-limiet van de kaartlaag** — onbekend of 218 symbolen tegelijk ook daadwerkelijk een
  praktisch/prestatie-probleem is, los van de visuele clutter.
- **`NavigatingRoute` vs. `NavigatingToDestination`** — de extensie injecteert alleen op een
  vooraf geladen route, niet op ad-hoc "navigeer naar X". Nog niet apart getest.
- **Backend-aanroep duurt ~55 seconden** (5 categorieën × aparte Overpass-call, elk tot 45s)
  — griezelig dicht bij onze 60s client-timeout. Kandidaten: timeout verhogen, categorieën
  parallelliseren server-side, of client-side minder categorieën tegelijk opvragen.

## Backend-referentie
`integration_dashboard` → `app/routes/routehotspots.py` (`api_poi_query`, `POI_CATEGORIES`
voor de categorie-keys + emoji's die je als iconen kunt hergebruiken).
