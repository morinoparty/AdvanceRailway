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

Paths above are relative to the `/api/v1/plugins/advancerailway/` base path, e.g. the full path for listing
stations is `/api/v1/plugins/advancerailway/stations`.

A by-id request for an id that does not exist returns an HTTP `404 Not Found` error.

## Authentication / permissions

All six endpoints are gated with the `@Permission("advancerailway.mineauth.read")` annotation, so a
caller must hold the `advancerailway.mineauth.read` permission node (enforced by MineAuth) to read
railway data. The endpoints expose exact in-world coordinates, so grant this node only to trusted
callers.

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
