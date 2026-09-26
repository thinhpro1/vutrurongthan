# SERVER RUNTIME ARCHITECTURE BASELINE

> **Status:** Explanatory architecture baseline for the approved runtime refactor direction.
>
> **Authority:** `docs/architecture/SERVER_RULES.md` remains the sole authoritative server rule. If this document conflicts with `SERVER_RULES.md`, the rule file wins.
>
> **Date:** 2026-09-25

## Core direction

```text
CLIENT
  ↓ blocking TCP
Session reader/writer virtual threads
  ↓ bounded Zone input
Zone
  = 1 ACTIVE Zone
  = 1 virtual thread
  = 1 writer
  ↓
mutable realtime Player / Monster / Boss / Npc / Item / Effect state
  ├─→ bounded network output → writer VT → Socket
  └─→ persistence handoff → blocking JDBC outside Zone → DB
```

Central rule:

> **Zone is the realtime mutation authority for Zone-owned runtime state.**

## Player contract

`Player.java` is the Player feature center.

Zone decides **when** a joined Player mutation runs. Player decides **what**
the Player state transition does.

Player location state has this contract:

```text
mapId/x/y are logical location state and participate in durable persistence.
zoneId is the runtime Zone/handoff destination and is not stored in PlayerSaveData.
Player location fields do not by themselves prove realtime Zone membership.
```

Joined realtime membership authority is `Zone.members`, using exact `Session`
identity; `Zone.hasPlayer(session)` is the membership truth. `Session.zone` is
only a routing/back-reference and candidate owner, not proof of membership.
`Zone.reservedPlayers` is pending destination admission and is separate from
active membership. A joined Session must have matching Session backlink, exact
Zone membership, and Player map/zone location. During a committed cross-Map
handoff, the Session is intentionally detached from the source while the
destination reservation and Player destination location await `FINISH_LOAD_MAP`.

## Public Map / Zone lifecycle

`MapManager` owns the public Map registry and `Map` owns the Zones inside each
public Map. A public Map eagerly creates exactly `minZone` Zones at
construction time. `Map.findZone(zoneId)` is lookup-only; a normal public
request never creates a missing Zone, even when `zoneId` is below the template
`maxZone` bound. `maxZone` remains catalog/template metadata and does not
authorize runtime Zone expansion.

Monster snapshots are an external `MAP_INFO` boundary only: `MapHandler`
resolves `MapManager.getMap(mapId)`, then `Map.findZone(zoneId)`, then reads
`Zone.monsterSnapshots()`. The read never creates a Zone and rejects an absent
Zone. Monster AI, combat, and Zone mutation use live runtime state directly;
they do not route through a snapshot or `MonsterManager`.

Monster lifecycle follows the public-world traversal:

```text
MonsterLifecycleScheduler
→ MonsterManager.update
→ MapManager.maps
→ Map.zones
→ Zone.updateMonsters(now, random)
→ Zone writer: all movement, all due respawns, all attacks
→ AreaService packets
```

`Monster` owns its mutable combat, movement, cooldown, enemy, and respawn
decisions. `Zone` owns the collection, exact current-member candidates,
cross-Monster death cleanup, lifecycle phase ordering, and the sole writer.
`AreaService` serializes neither gameplay decisions nor ownership; it only
sends same-Zone packets through packet writers. Rejected sends close only after
the Zone writer returns.

## Public world and future Dungeon runs

```text
PUBLIC WORLD

MapManager
→ public runtime Map
→ minZone public Zones
```

```text
FUTURE DUNGEON RUN

Dungeon run
→ multiple private runtime Maps
→ each Map owns its runtime Zone(s)
```

Zone is not the whole dungeon; a dungeon may span multiple Maps. Different
dungeon runs share MapTemplate/static resources, but never share runtime
Map/Zone/Monster state. The legacy Barrack / Manor / Expansion structure is a
conceptual readability reference only, not a target runtime architecture.

## Main decisions

```text
YES blocking network I/O first
YES Session reader/writer virtual threads
YES 1 ACTIVE Zone = 1 virtual thread baseline
YES Zone single-writer target
YES mutable realtime entities as the current Player/Map runtime contract
YES idle Zone may freeze while preserving runtime state in RAM
YES normal DB persistence via checkpoint/final save
YES Effect belongs to runtime target entity
YES Monster AI should live primarily with Monster
NO snapshot in hot AI/combat paths
NO DB/network blocking I/O in Zone execution
NO network/persistence thread directly mutating Zone entities
NO worker pool/NIO/reactive DB before benchmark evidence
```

## Current P1 correctness baseline

The current runtime already preserves these correctness behaviors:

```text
mutable Player runtime
final disconnect stable save handoff
```

The approved ownership/readability migration for the joined Player flow is now
implemented in the R3B slice below. Current correctness behavior remains
unchanged.

Map admission keeps active members and pending destination reservations under the
destination Zone owner. A cross-Zone transition reserves the destination before
detaching the source and consumes the reservation at `FINISH_LOAD_MAP`.

## Joined Player flow after R3B

The ordinary same-Zone Player flow is owned by `Zone`; `MapManager` remains a
public Map router and the public-world route owner for cross-Map transitions:

```text
public finish load:
MapHandler → MapManager → Map.findZone → Zone.enter
```

```text
same-Zone movement:
MapHandler → Session.zone → Zone.move → Player.move → AreaService
```

```text
normal joined disconnect:
Session.close → MapManager bridge → Zone.leave → PlayerSaveData
→ Repository outside Zone
```

`Zone.enter` owns admission, membership binding, and the existing-player
presence delivery. `Zone.move` owns the ordered Player mutation and area
delivery. `Zone.leave` owns membership removal, detachment, and stable save
capture; rejected observer cleanup happens after the Zone writer returns.

`MapManager.finishLoad` preserves the two authority branches: a Session with a
Zone backlink must still be an exact member of that Zone with matching Player
map/zone location; a detached Session is routed from Player map/zone location
through `Zone.enter`, which rechecks the same location before membership is
inserted.

`MapManager` is the public-world cross-Map route owner. Public change-map and
death-return coordination runs as:

```text
MapHandler
→ MapManager public route
→ source Zone capture
→ destination Zone reservation
→ source Zone commit
→ PlayerSaveData
→ Repository outside Zone
→ FINISH_LOAD_MAP
→ Zone.enter consumes reservation
```

Future Dungeon runs do not route private Maps through `MapManager`; their
runtime owner resolves private destination Maps and uses the Zone admission
primitives directly. R6 final readability sweep is locked; P2 Monster Complete
is the current migration phase.

## Freeze semantics

`FROZEN` means execution is suspended, not runtime destruction.

Example:

```text
Monster #1 HP = 120 / 200
Zone freezes
Zone wakes later
Monster #1 HP remains 120 / 200
```

Time-based mechanics that must advance while frozen should use absolute
deadlines where suitable:

```text
respawnAt
effectEndAt
cooldownUntil
itemExpireAt
```

Regen, DoT while nobody is present, and world/event boss lifecycle require
explicit per-mechanic semantics.

## Persistence direction

While online:

```text
Zone/RAM = current realtime state
DB       = durable checkpoint
```

Normal gameplay:

```text
many runtime changes
→ dirty/revision
→ coalesce
→ save latest state periodically
```

Disconnect correctness is primarily about ordering:

```text
process prior gameplay
→ detach from realtime mutation
→ final save latest state
→ checkpoint attempt completes according to account contract
→ release same-account reservation
```

A separate Snapshot type is not mandatory when final detached state is already
stable. Stable SaveData/Snapshot is justified when an online Player continues
mutating while persistence runs.

## Migration order

Exact completion/lock state is determined by the commit-review workflow.

```text
R0  Rules / runtime contract                         LOCKED
R1  Player contract/readability                      LOCKED
R2  Map → Zone ownership                             LOCKED
R3A Public Map / Zone lifecycle                      LOCKED
R3B Zone-local Player move / enter / leave           LOCKED
R4  Public-world Cross-Map transition                LOCKED
R5  Session / Player / Zone authority audit          LOCKED
R6  Final P1 readability sweep                       LOCKED
P1  Architecture Normalization                       LOCKED
P2  Monster Complete                                 CURRENT
P3  Player Complete                                  NEXT AFTER P2
```

Each phase must also clean the touched feature slice to current
`SERVER_RULES.md`. Do not postpone code-quality cleanup into a vague future
pass.

## Still open

```text
exact Zone input queue implementation
exact tick period
park vs stop/restart VT for freeze
DoT/regen semantics while frozen
periodic dirty checkpoint
coalescing/batching
persistence infrastructure optimization
exact Entity/CombatEntity shared state
exact Monster update phase API
```
