<p align="center"><img src="docs/banner.png" width="368" alt="Wynnav"></p>

A world map, minimap and waypoints for Wynncraft. Fabric, Minecraft 1.21.11, client-side.

## Features
- **World map** (`M`): drag to pan, scroll to zoom, **Center on me** to follow yourself.
- **Click anything** for its info and actions; right-click a spot to add a waypoint there;
  middle-click to track. You keep walking while the map is open.
- **Waypoints** with a beam and a floating label in the world. A death point is saved automatically.
- **Minimap**: square or round, north-up or rotating.
- **Layers**: town and area names, official markers, guild territories, gathering nodes, camps and
  world events.
- **Search** for places, markers and waypoints.
- **Friends** on the map and minimap (needs your Wynncraft API token, see Settings).
- **Wynntils import**: Export in Wynntils' waypoint manager, then *Paste Wynntils export* here.

Settings are on the map screen and in Mod Menu.

## Data
Terrain tiles come from [Wynntils](https://github.com/Wynntils/Wynntils)' published map; markers,
territories, gathering nodes, content and friend locations from the
[Wynncraft API](https://docs.wynncraft.com); place names and icons from the
[official web map](https://map.wynncraft.com). Everything is cached in `<game dir>/wynnav/cache`.
Your API token is stored separately in `config/wynnav/api-token.txt` and only sent to Wynncraft.

## Building
Gradle needs Java 25 (the mod targets Java 21).

```sh
./gradlew build               # jar in build/libs
./gradlew runClientGameTest   # scripted client test, screenshots in build/run/clientGameTest
python3 art/pixelart.py       # regenerate the pixel art (sprites are drawn as text grids there)
```
