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

## Zone writer implementation

`Zone.java` remains the world/runtime feature center. It owns membership,
destination reservations, entity collections, gameplay execution, area delivery,
and stable save capture. Each Zone owns one package-private `ZoneWriter`, which
contains only the bounded input queue, virtual-thread execution, caller waits,
interruption handling, and execution lifecycle. Gameplay entry points and their
writer-side actions are placed together in `Zone.java`.

The current writer drains queued work and returns to `FROZEN` when the queue is
empty. It is not a periodic game loop; the existing `MonsterManager` lifecycle
trigger still submits Monster updates. This extraction preserves overload,
stop/cancellation, same-writer calls, and the guard against entering high-level
operations from any Zone writer. Public cross-Map coordination remains in
`MapManager` under the existing reservation/revalidation/commit contract.

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

Authenticated Player bootstrap is split at the protocol boundary:

```text
AuthHandler
→ account authentication/admission
→ Session AUTHENTICATED
→ PlayerHandler.openPlayerForAuthenticatedAccount
→ PlayerManager.load → PlayerRepository
   or START_CREATE_PLAYER_SCREEN
→ Session.bindPlayer
→ IN_GAME
→ PLAYER_INFO
→ MAP_INFO
→ FINISH_LOAD_MAP
→ Zone.enter
```

`AuthHandler` owns account authentication, successful-login metadata, account
admission, and the `HANDSHAKE_DONE → AUTHENTICATED` transition. `PlayerHandler`
owns the `PlayerManager.load` call, no-Player create-screen branch, existing
Player bind, `IN_GAME` transition, and `enterGame` packet bootstrap. A failed
Player load reports the controlled system-busy result and rolls the Session
back to `HANDSHAKE_DONE` before the admission reservation is released.

Player lifecycle conversion is centralized in `PlayerManager`:

```text
PlayerHandler → PlayerManager.create → Player.create → PlayerSaveData
→ PlayerRecord → PlayerRepository → Player

PlayerHandler → PlayerManager.load → PlayerRepository → PlayerRecord → Player
```

`PlayerManager` owns only Player create/load/save lifecycle conversion. It is
not an online registry and has no Session, Zone, packet, or gameplay behavior
responsibility. `SessionManager` remains online account/session authority and
`Zone.members` remains realtime membership authority.

## Player attacks against Monsters

```text
CombatHandler prepare / impact
→ candidate Session.zone
→ Zone.canTargetMonster / Zone.attackMonster
→ ZoneWriter
→ exact Session membership + live Monster lookup
→ Player.canTarget / Player.attackMonster
→ Monster.injure
→ AreaService damage/death broadcast, then finisher potential packet
```

`Player` owns target eligibility, its attack damage, and its lethal reward.
`Monster.injure` owns HP/death/enemy changes and the respawn deadline. Both
entities run under the same Zone writer; its execution order decides the
finisher. `Zone` supplies membership, lookup, current member count, and ordered
output. It does not calculate Player damage or add the reward a second time.

`CombatHandler` retains only packet parsing and pending prepare/impact state.
The former `Combat` routing class is removed. `SessionServices` supplies a
`Clock`; impact samples it on the request caller before submitting to Zone,
preserving the current deadline semantics when writer execution is delayed.

Stable `PlayerSaveData` capture in `Zone.leave`, live member filtering for
Monster updates, and cross-Monster enemy cleanup remain world/handoff
responsibilities of Zone. Persistence still runs outside its writer.

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

Monster lifecycle now follows the normalized N2 responsibility shape.

CURRENT IMPLEMENTATION (after N2 Monster normalization):

```text
MonsterManager scheduled lifecycle
→ MonsterManager.update(MapManager)
→ MapManager.maps
→ Map.zones
→ Zone.updateMonsters(now, random)
→ Zone writer
→ for each Monster: Monster.update(...)
→ AreaService packets
```

`MonsterManager` owns the scheduled lifecycle trigger and public-world
traversal only; the scheduler does not mutate Monster state directly. `Zone`
remains the sole writer and owns the current Monster collection, membership
candidates, cross-Monster death cleanup, and execution ordering. `Monster`
owns its mutable combat, movement, cooldown, enemy, respawn, and update
decisions. `AreaService` serializes neither gameplay decisions nor ownership;
it only sends same-Zone packets through packet writers. Rejected sends close
only after the Zone writer returns.

The N2 change is a responsibility/readability normalization. It preserves the
locked correctness baseline and does not change protocol or gameplay constants.

The Monster source package has three feature files: `Monster.java`,
`MonsterTemplate.java`, and `MonsterManager.java`. `Monster.Snapshot` keeps
the immutable current-state boundary for external `MAP_INFO` reads beside
the runtime object. `MonsterTemplate.Spawn` is reusable immutable initial
configuration; `MonsterTemplate.Dart` and its `Phase` are static animation
data. These small types are nested with their feature owner rather than
separate files. Spawn configuration and captured current state retain
distinct meanings; packet writers never receive live Monster references.

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
→ PlayerManager.save → PlayerRepository outside Zone
```

`Zone.enter` owns admission, membership binding, and the existing-player
presence delivery. `Zone.move` owns the ordered Player mutation and area
delivery. `Zone.leave` removes exact Session membership/reservation and clears
its backlink before area delivery. Disconnect coordination drains all candidate
source/destination owners before final capture; rejected observer cleanup
happens after the Zone writers return.

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
→ PlayerManager.save → PlayerRepository outside Zone
→ FINISH_LOAD_MAP
→ Zone.enter consumes reservation
```

`MapManager` keeps a private pending handoff per exact Session before reserving
the destination. It is routing bookkeeping only; `Zone.reservedPlayers` remains
admission authority. Each handoff has unique identity. New destination admission
clears the current matching handoff on its writer after binding and before area
delivery; already-present/joined paths do not clear it. This prevents delayed
`finishLoad` calls from removing a newer trip or retaining a consumed guard.
The admission callback changes only concurrent routing metadata and never waits
or performs I/O. Rollback cancels
the actual reservation before clearing that handoff; committed cross-Zone
handoffs retain it until destination admission or disconnect cleanup.

Disconnect marks the Session CLOSED, drains its initially observed source,
pending destination and logical destination, then rereads routing after each
owner returns. Each cleanup removes both exact Session membership and its
reservation. Writer-side reserve/enter reject CLOSED; an admission already
executing before close is drained and detached. Only after all candidates are
detached does the last owner capture final state. A CLOSED dead Player is revived
at home (map 0, zone 0, x 1250, y 648, current maximum HP/MP) before capture,
without entering or reserving home. A never-joined Player with no runtime Zone
is already detached and may be captured directly. Failed owner cleanup yields
no final capture rather than reading possibly live state outside its owner.

Normal transition checkpoints go through `Session.savePlayer`; final close uses
the same separate checkpoint lock. Ordinary saves recheck CLOSED inside that
lock. Final save waits for an already-started write, then attempts the latest
detached checkpoint; if capture failed, it still drains the earlier write. JDBC
stays in `PlayerManager`/Repository outside Zone execution. Neither the Session
admission monitor nor the checkpoint lock is held over Zone waits. Account
release waits for both login admission and the close checkpoint attempt to
complete, including close during Player bootstrap. Repository failure retains
the existing log-and-release policy after the attempt completes.

Future Dungeon runs do not route private Maps through `MapManager`; their
runtime owner resolves private destination Maps and uses the Zone admission
primitives directly. R6 final P1 readability sweep is locked. P1 Architecture
Normalization, P2 Monster correctness, and P3 Player Core correctness are
locked correctness baselines; readability normalization remains a separate
ordered track below.

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

Correctness baselines:

```text
R0  Rules / runtime contract                         LOCKED
R1  Player contract/readability                      LOCKED
R2  Map → Zone ownership                             LOCKED
R3A Public Map / Zone lifecycle                      LOCKED
R3B Zone-local Player move / enter / leave           LOCKED
R4  Public-world Cross-Map transition                LOCKED
R5  Session / Player / Zone authority audit          LOCKED
R6  Final P1 readability sweep                       LOCKED
P1  Architecture Normalization correctness baseline  LOCKED
P2  Monster correctness baseline                     LOCKED
P3  Player Core correctness baseline                 LOCKED
```

Readability normalization order:

```text
N1.1 Zone                                           DONE
N2   Monster                                        DONE
N1.2 Map / MapManager                               DONE
N3   Player                                         DONE after review
```

Correctness baselines being locked does not mean readability normalization is
complete.

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
exact Monster.update(...) world-context/API shape
```
