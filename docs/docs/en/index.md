# About AdvanceRailway

AdvanceRailway is a plugin for the [Minecraft](https://www.minecraft.net/) server
software [Paper](https://papermc.io/) that adds railway stations and railway lines to your server.

## What it does

- **Stations** — define named stations with a world, position, numbering, and optional color/size, used as
  the endpoints of railway lines.
- **Railways** — connect two stations with a line (type, required travel time, and the points used to draw
  it), optionally organized into **groups** (e.g. to color-code a line family).
- **squaremap integration** — when [squaremap](https://github.com/jpenilla/squaremap) is installed, stations
  and railway lines are drawn as markers on the live web map.
- **MineAuth HTTP API** — when the MineAuth plugin is installed, AdvanceRailway publishes read-only HTTP
  endpoints for stations, railways, and groups. See [MineAuth HTTP API](mineauth-api.md) for the full
  reference.

## Getting started

Use the in-game commands to create stations, railways, and groups. For details on the read-only HTTP API,
see [MineAuth HTTP API](mineauth-api.md).
