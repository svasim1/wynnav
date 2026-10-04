<p align="center"><img src="docs/banner.png" width="368" alt="Wynnav"></p>

A map mod for Wynncraft that is easy to use: a world map, a minimap and waypoints.

- Press **M** to open the map. Drag to move around, scroll to zoom.
- Click anything on the map to see what it is. Track it and a beam shows you the way.
- Right-click a spot to save a waypoint. Where you last died is saved for you.
- A minimap in the corner, round or square.
- Town names, quests, caves, merchants, guild territories, gathering spots and world events,
  each of which you can turn on or off.
- Search for places by name.
- Bring your waypoints over from Wynntils.

For Minecraft 1.21.11 with Fabric.

## What it downloads
Wynnav needs the internet to show the map. It only downloads; it doesn't send anything about you or
your game.

- Map images from [Wynntils](https://github.com/Wynntils/Wynntils) (`cdn.wynntils.com`). Thanks to
  the Wynntils team for rendering them.
- Markers, guild territories, gathering spots, camps and world events from the
  [Wynncraft API](https://docs.wynncraft.com) (`api.wynncraft.com`).
- Town and area names and marker icons from Wynncraft's [web map](https://map.wynncraft.com)
  (`map.wynncraft.com`).

Everything is saved in your game folder, so after the first start the map also works offline.

## License
LGPL-3.0, see [LICENSE](LICENSE). To build it yourself, run `./gradlew build` (needs Java 25).
