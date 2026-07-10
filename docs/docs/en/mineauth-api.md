# MineAuth HTTP API

When the MineAuth plugin is installed alongside AdvanceRailway (MineAuth is a soft dependency —
AdvanceRailway works fine without it), AdvanceRailway registers a set of permission-gated, read-only HTTP endpoints under:

```
/api/v1/plugins/advancerailway/
```

If MineAuth is not installed, these endpoints are not registered and AdvanceRailway otherwise behaves
normally.

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| GET | `/stations` | List all stations |
| GET | `/stations/{id}` | Get a single station by id |
| GET | `/railways` | List all railways |
| GET | `/railways/{id}` | Get a single railway by id |
| GET | `/groups` | List all groups |
| GET | `/groups/{id}` | Get a single group by id |
| GET | `/route?from={id}&to={id}` | Find the fastest route between two stations |

Paths above are relative to the `/api/v1/plugins/advancerailway/` base path, e.g. the full path for listing
stations is `/api/v1/plugins/advancerailway/stations`.

A by-id request for an id that does not exist returns an HTTP `404 Not Found` error.

## Authentication

All endpoints are declared `@Authenticated(callers = [CallerType.SERVICE])`, so they can only be
called with a **service-account token** — a trusted credential that a server administrator issues via
MineAuth. Player user tokens (issued through MineAuth's OAuth2 flow) are rejected. The endpoints expose
exact in-world coordinates, so issue service tokens only to trusted backend integrations.

Because service tokens are treated as trusted credentials, MineAuth does **not** evaluate any Bukkit
permission node for them; access is controlled purely by whether the caller holds a valid service token.
(This replaces the previous `advancerailway.mineauth.read` permission-node gate.)

The integration can also be disabled entirely via config (`mineAuthEnabled: false` in the plugin
config); when disabled, `MineAuthIntegration` does not register the handler even if MineAuth is present.

## Pagination

The list endpoints (`/stations`, `/railways`, `/groups`) accept `limit` and `offset` query parameters
to bound the response and avoid an unbounded full-folder read:

- `limit` — number of items to return. Default `100`, capped at `500`.
- `offset` — number of items to skip. Default `0`.

Example: `GET /api/v1/plugins/advancerailway/stations?limit=50&offset=100`

## Response shapes

All responses are JSON, encoded with `kotlinx.serialization`.

### Station

```json
{
  "id": "central",
  "name": "Central Station",
  "numbering": "M01",
  "world": "world",
  "point": { "x": 0.0, "y": 64.0, "z": 0.0 },
  "overrideSize": null,
  "color": "#FF0000"
}
```

- `numbering` and `overrideSize` may be `null`.
- `color` is a `#RRGGBB` hex string.

`GET /stations` returns a list wrapped in an object:

```json
{ "stations": [ { "...": "StationDto" } ] }
```

`GET /stations/{id}` returns a single station object (unwrapped).

### Railway

```json
{
  "id": "central-to-north",
  "group": "main-line",
  "world": "world",
  "lineType": "UP_LINE",
  "fromStation": "central",
  "toStation": "north",
  "timeRequired": 30,
  "startPoint": { "x": 0.0, "y": 64.0, "z": 0.0 },
  "endPoint": { "x": 100.0, "y": 64.0, "z": 0.0 },
  "directionPoint": { "x": 50.0, "y": 64.0, "z": 0.0 }
}
```

- `group` may be `null` if the railway does not belong to a group.
- `timeRequired` is the travel time in seconds.

`GET /railways` returns:

```json
{ "railways": [ { "...": "RailwayDto" } ] }
```

`GET /railways/{id}` returns a single railway object (unwrapped).

### Group

```json
{
  "id": "main-line",
  "name": "Main Line",
  "color": "#00A0FF"
}
```

`GET /groups` returns:

```json
{ "groups": [ { "...": "GroupDto" } ] }
```

`GET /groups/{id}` returns a single group object (unwrapped).

### Route

`GET /route?from={id}&to={id}` computes the fastest (least total travel time) route between two stations.
Both `from` and `to` are **required** query parameters; omitting either returns `400 Bad Request`.

The network is modelled as a weighted graph with two kinds of edges, and the fastest path is found with
A\* search:

- **Rail edges** — each railway is an undirected edge weighted by its `timeRequired` (seconds).
  Directional routing (`UP_LINE` / `DOWN_LINE`) is not yet distinguished.
- **Walking edges** — any two stations **in the same world** are also connected by walking, weighted by
  their straight-line horizontal distance ÷ the Minecraft walk speed (`4.317` blocks/s). This lets a route
  reach stations that no railway connects. Stations in **different worlds** are only reachable via rail.

```json
{
  "from": "central",
  "to": "north",
  "totalTime": 150,
  "stations": ["central", "west", "north"],
  "legs": [
    { "mode": "RAIL", "railway": "central-to-west", "from": "central", "to": "west", "timeRequired": 60, "group": "main-line" },
    { "mode": "WALK", "railway": null, "from": "west", "to": "north", "timeRequired": 90, "group": null }
  ]
}
```

- `mode` is `"RAIL"` (riding a railway) or `"WALK"` (walking between stations).
- `railway` and `group` are `null` on `WALK` legs (and `group` is `null` for a railway with no group).
- `totalTime` is the sum of all legs' `timeRequired`, in seconds.
- `stations` is the ordered list of station ids passed through, from `from` to `to`.

The command form `/ar railway route <to>` additionally supports routing from a player's current location,
but the HTTP endpoint only accepts station ids.

Error responses carry a machine-readable `code`:

| Status | `code` | When |
| --- | --- | --- |
| `404 Not Found` | `station_not_found` | `from` or `to` is not a valid/existing station |
| `400 Bad Request` | `same_station` | `from` and `to` are the same station |
| `404 Not Found` | `no_route` | the two stations are not connected by any path |
