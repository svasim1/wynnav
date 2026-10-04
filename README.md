# Wynnav

A world map and waypoints for Wynncraft. Fabric, Minecraft 1.21.11, client-only.

## Data sources
- **Terrain tiles:** top-down PNGs listed in `https://cdn.wynntils.com/static/Reference/maps.json`
  (made by the Wynntils team), cached by md5 in `<game dir>/wynnav/cache/tiles`.
- **Markers:** official Wynncraft API `GET https://api.wynncraft.com/v3/map/locations/markers` (cached 1 h).
- **Territories:** `GET /v3/guild/list/territory` (refreshed every 5 min).
- **Gathering nodes:** `GET /v3/map/gathering-nodes` (cached 12 h).
- **Place names:** labels of the official web map, parsed from `https://map.wynncraft.com/js/labels.js` (cached 3 days).
- **Camps / raids / world events:** `GET /v3/map/camps`, `/raids`, `/world-events`.
- **Marker icons:** official web map, `https://map.wynncraft.com/img/<icon>` (cached 7 days).
- **Player position:** read from the client player.
- **Waypoints:** `config/wynnav/waypoints.json`.

## Controls (world map, default key `M`)
- Left-drag: pan. Scroll: zoom toward the cursor.
- Left-click a marker or waypoint: its actions (track, add as waypoint, edit, delete, copy coordinates).
- Right-click anywhere: actions at that spot (add waypoint, track, copy coordinates).
- Middle-click: track whatever is under the cursor (or the spot itself); middle-click it again to stop.
- Movement keys keep walking the player while the map is open, except while typing in the search box.
- **Center on me** button: recenter and follow the player until you drag again.
- **Waypoints** button: sidebar with all waypoints; click one to jump to it. To bring waypoints over
  from Wynntils, press Export in Wynntils' waypoint manager, then "Paste Wynntils export" here.
- **Layers** button: toggle place names, marker categories, guild territories, camps, world events and
  gathering nodes (by profession and level range).
- **Search** box: places, markers, camps, world events and waypoints; Enter jumps to the first result.
- **Settings** button (also in Mod Menu): world marker and minimap options.

## In the game
- **World marker:** tracking something shows a beam and a floating icon with name and distance
  (visible through walls, pinned to the screen edge when off-screen). Tracking stops on arrival.
- **Minimap:** square or round, north-up or rotating with your view; size, zoom, opacity and corner
  are adjustable.
- **Friends:** heads of your friends (and party / guild members, toggled under Layers) on the map
  and minimap. Uses the official `GET /v3/map/locations/player` with your personal API token
  (Settings → "Friends on the map"; create one at wynncraft.com/account/dashboard?section=tokens).
  The token is stored on its own in `config/wynnav/api-token.txt`. Updates every 15 s; players in
  render distance follow their live position. Friends on another world are drawn faded.
- **Death point:** a "Death point" waypoint is kept where you last died.

## Building
Loom 1.18 needs Java 25 to run Gradle (the mod itself targets Java 21).

```sh
export JAVA_HOME=~/.local/share/jdks/jdk-25.0.4.1+1
./gradlew build                 # jar in build/libs
./gradlew runClient             # dev client
./gradlew runClientGameTest     # scripted client test; screenshots in build/run/clientGameTest/screenshots
```
