# Monster Combat V1 Design

**Date:** 2026-08-27  
**Baseline:** `e9bcc52ba2dd3bd1ab4eb3d7758a7918baacd038`  
**Old reference:** `thinhpro1/rongthan@a8bfd96d0dac4e606054d78dce3f5da58f7937b2`  
**Status:** Approved design, including EffectImage 17 prerequisite; implementation plan requires final written-spec review.

## 1. Goal

Add the first authoritative player-versus-monster combat slice without importing the old server's full skill/combat subsystem.

The slice must make the existing Map1 runtime monsters genuinely damageable and killable while preserving the already-frozen Zone-owned runtime architecture.

The target flow is:

```text
Unity -72 prepare skill/target
        ↓
MessageHandler records one pending monster attack
        ↓
Unity performs its existing local skill/projectile animation
        ↓
Unity -108 confirms impact
        ↓
MessageHandler consumes the pending attack
        ↓
MapService validates same live Zone membership
        ↓
Zone serializes mutation
        ↓
RuntimeMonster HP/status changes
        ↓
-106 MONSTER_INJURE or -100 MONSTER_START_DIE
        ↓
all players in the Zone receive the same authoritative result
```

This slice deliberately does **not** add monster AI, respawn, full skill validation, MP/cooldown, PvP, drops, EXP, server-side patrol, or range validation.

---

## 2. Frozen Baseline

The implementation must start from:

```text
e9bcc52ba2dd3bd1ab4eb3d7758a7918baacd038
Require explicit zone monster seeds
```

The following behavior remains frozen unless this spec explicitly says otherwise:

```text
Map0 monster count = 0
Map1 each Zone starts with exactly six Hổ nanh kiếm
runtime IDs = 0..5
canonical Map1 anchors unchanged
MonsterBootstrap version = 1
dart 0 unchanged
monster template 1 unchanged

ResourceService = immutable bootstrap data
MonsterRuntimeFactory = fresh runtime seed per Zone
MapService = Zone registry
Zone = owner of RuntimeMonster state
MAP_INFO = Zone MonsterSnapshot

MAP_INFO may lazily create destination Zone
MAP_INFO does not join player
FINISH_LOAD_MAP remains the join point
empty Zones remain retained

ADD_PLAYER unchanged
REMOVE_PLAYER unchanged
PLAYER_MOVE unchanged
no mover movement ACK
Map0 <-> Map1 change-map lifecycle unchanged
Unity movement/local monster patrol unchanged
codec/cipher unchanged
```

The current initial player profile damage is `10` and every canonical Map1 Hổ starts with `300` HP.

---

## 3. Canonical Protocol Audit

### 3.1 Client `-72` prepare packet

The current Unity client sends `PLAYER_START_USE_ULTIMATE = -72` from `Service.UseSkill`.

Wire form:

```text
sbyte skillId
```

or, when the current skill carries a focus target:

```text
sbyte skillId
byte targetType
int targetId
```

Current client target types:

```text
0 = Player
1 = Monster / non-Player target
```

For this slice only `targetType = 1` is actionable.

A one-byte `-72` packet is a valid no-monster-target prepare and clears any previous pending monster attack.

### 3.2 Client `-108` impact packet

The current Unity client sends `USE_SKILL = -108` from `Service.Attack` when the local skill/projectile reaches its impact point.

Wire forms:

```text
byte -1
```

for no target, or:

```text
byte 0
int playerId
```

for Player target, or:

```text
byte 1
int monsterId
```

for monster target.

This slice only executes `type = 1`.

### 3.3 Old server two-phase behavior

The old server also treats skill execution as two phases:

```text
-72 prepares current skill + focus
-108 confirms impact against the prepared focus
```

Old `-108` validates that target ID and target type match the focus retained from the prepare phase.

Therefore V1 must not treat arbitrary `-108` monster packets as independent damage commands.

### 3.4 Monster result packets

The old server and current Unity agree on:

#### `MONSTER_INJURE = -106`

```text
int monsterId
long damage
long hpAfter
bool critical
```

#### `MONSTER_START_DIE = -100`

```text
int monsterId
long damage
bool critical
```

V1 always writes:

```text
critical = false
```

No new opcode is introduced.

---

## 4. Required Effect Resource Migration

### 4.1 Why EffectImage 17 is required

Current Unity `Monster.UpdateDead()` transitions a killed monster through its existing death animation and, after roughly 500 ms, creates:

```text
EffectLoop(17, ...)
```

`EffectLoop` indexes `EffectManager.effectImages[17]` directly.

The current server bootstrap contains only EffectImage IDs 6 and 7. Leaving the resource unchanged would allow a legitimate `-100` death packet to reach a client that does not have effect 17, producing `KeyNotFoundException` during the built-in death animation.

Therefore canonical EffectImage 17 is a prerequisite of enabling lethal monster combat.

### 4.2 Canonical EffectImage 17

Use the SQL-authoritative values:

```text
id = 17
dx = 0
dy = -10
delay = 50
icons = [1911, 1912, 1913, 1914]
```

Do not invent replacement icons or alter Unity.

### 4.3 Effect resource version bump

Change effect resource version:

```text
0 -> 1
```

The approved V1 effect image set becomes exactly:

```text
6  unchanged
7  unchanged
17 added with canonical values
```

Version bump is mandatory so clients that already cached version 0 re-request type-3 effect resources instead of retaining the old 6/7-only cache.

### 4.4 Server resource invariants

`ResourceService` effect loading changes from:

```text
required IDs = [6, 7]
version = 0
exact image count = 2
```

into:

```text
required IDs = [6, 7, 17]
version = 1
exact image count = 3
```

`MessageHandler` effect manifest/type-3 response changes its effect version to `1`.

Effect 6 and Effect 7 must remain byte-for-byte semantically unchanged.

Monster resource version remains `1` and `MonsterBootstrap.json` remains unchanged.

### 4.5 PNG assets

This repository intentionally does not track the large PNG asset set.

No PNG files are added to git in this slice.

The existing runtime/icon-resource setup must provide icon IDs `1911..1914` in the developer's local resource source. Missing local PNGs are an environment/resource issue, not a reason to fabricate assets.

---

## 5. Runtime Monster Mutation Model

Current `RuntimeMonster` already owns mutable fields:

```text
x
y
maxHp
hp
status
```

V1 adds behavior, not a second state store.

### 5.1 Damage result value

Add an immutable value object in the monster package:

```java
public record MonsterDamageResult(
        int monsterId,
        long damage,
        long hpAfter,
        boolean killed
) {}
```

It is the result of one accepted runtime mutation and contains only values needed by the caller to choose/encode the resulting packet.

### 5.2 RuntimeMonster API

Add:

```java
public boolean isAlive();

public Optional<MonsterDamageResult> applyDamage(long damage);
```

Semantics:

```text
if damage <= 0
    return empty

if status != LIVE or hp <= 0
    return empty

hpAfter = max(0, hp - damage)
hp = hpAfter

if hpAfter == 0
    status = DIE (wire value 1)
    return killed result
else
    status remains LIVE (wire value 0)
    return injured result
```

HP must never become negative.

The result `damage` field is the accepted logical hit amount supplied to `applyDamage`; V1 damage is deterministic `PlayerProfile.damage()`.

Do not add public HP/status setters.

Do not add respawn methods in this slice.

---

## 6. Zone Combat Boundary

`Zone` remains the owner of live runtime monsters.

It must not expose `RuntimeMonster` or its internal `LinkedHashMap`.

Add package/public behavior-oriented APIs sufficient for MapService:

```java
public synchronized boolean hasLiveMonster(int monsterId);

public synchronized Optional<MonsterDamageResult> damageMonster(
        int monsterId,
        long damage);
```

`hasLiveMonster` returns false when:

```text
monster ID does not exist
or monster HP <= 0
or monster status is DIE
```

`damageMonster` returns empty for missing/dead monsters or non-positive damage.

Because MapService will execute a larger atomic combat operation under `synchronized(zone)`, Java monitor reentrancy permits these synchronized Zone methods to be called safely from that block.

No raw runtime reference leaves Zone.

---

## 7. Pending Attack State

### 7.1 Location

Pending attack intent belongs to `MessageHandler`, not `Session`, `Zone`, or `RuntimeMonster`.

Each Session owns one MessageHandler, and inbound messages for one Session are processed serially by that Session's reader thread.

No additional lock is required around this one-session pending value.

### 7.2 Value

Add a private record in `MessageHandler`:

```java
private record PendingMonsterAttack(
        int skillId,
        int mapId,
        int zoneId,
        int monsterId
) {}
```

and:

```java
private PendingMonsterAttack pendingMonsterAttack;
```

Only one pending monster attack exists per Session.

### 7.3 Prepare semantics

Every well-formed `-72` first clears/replaces prior pending intent.

Processing:

```text
parse signed skillId

if no bytes remain:
    pending = null
    return

otherwise payload must contain exactly:
    targetType byte
    targetId int

if targetType == 0:
    valid unsupported player target
    pending = null
    return

if targetType != 1:
    malformed packet

if targetType == 1:
    require current bound PlayerProfile
    require player is currently a member of the exact Zone
    require target monster exists and is LIVE in that Zone

    if any gameplay validation fails:
        pending = null
        return

    pending = (skillId, current mapId, current zoneId, monsterId)
```

V1 stores `skillId` for protocol fidelity/future extension but deliberately does not load/validate the full old skill subsystem.

### 7.4 No prepare-time damage

`-72` never mutates monster HP.

It only establishes a single-use attack intent.

---

## 8. Impact Confirmation

### 8.1 Pending consumption

A well-formed `-108` consumes the current pending monster attack exactly once.

Conceptually:

```java
PendingMonsterAttack pending = pendingMonsterAttack;
pendingMonsterAttack = null;
```

Consumption occurs before gameplay match validation so a mismatch cannot be retried repeatedly against the same pending intent.

Malformed packets follow existing handler malformed-packet behavior; the Session is closed, so pending lifetime is irrelevant after the parse failure.

### 8.2 Impact validation

For a monster impact (`type = 1`), damage occurs only when all are true:

```text
pending exists
impact monsterId == pending.monsterId
current player mapId == pending.mapId
current player zoneId == pending.zoneId
session is still a member of that exact existing Zone
target monster still exists
target monster is still LIVE
PlayerProfile.damage() > 0
```

If any check fails, impact is a no-op.

### 8.3 Unsupported impact types

Well-formed:

```text
type = -1
```

or:

```text
type = 0 + playerId
```

are valid but unsupported in V1 and perform no damage.

Unknown target types are malformed.

Trailing bytes are malformed.

### 8.4 Replay resistance

Required behavior:

```text
-72 monster 0
-108 monster 0  -> one hit
-108 monster 0  -> no-op
-108 monster 0  -> no-op
```

Also:

```text
-72 monster 0
-108 monster 1  -> no-op and pending consumed
-108 monster 0  -> still no-op
```

---

## 9. Map Change Invalidation

Pending combat intent must not survive map travel.

At the start of `handleRequestChangeMap`:

```text
pendingMonsterAttack = null
```

This invalidates intent even if a stale client impact arrives after travel begins.

Impact validation also rechecks current map/zone against the pending values.

Example:

```text
Map1 -72 monster 0
REQUEST_CHANGE_MAP
Map0
stale -108 monster 0
```

Result:

```text
no damage
```

No special disconnect cleanup is required because MessageHandler lifetime is the Session lifetime.

---

## 10. MapService Combat API

Combat APIs must use existing Zones only.

Unlike MAP_INFO, combat must **not** call `getOrCreateZone` as a side effect.

A player may receive destination MAP_INFO before FINISH_LOAD_MAP; that must not allow combat before actual Zone membership.

### 10.1 Target validation

Add:

```java
public boolean canTargetMonster(
        Session session,
        int monsterId);
```

It must:

```text
reject null/closed Session
require bound PlayerProfile
lookup existing Zone by current mapId/zoneId
return false if Zone does not exist
require Zone contains this player ID/session membership
require monster exists and is LIVE
```

It must not create a Zone.

### 10.2 Attack execution

Add:

```java
public boolean attackMonster(
        Session session,
        int monsterId,
        long damage);
```

It must:

```text
reject null/closed Session
require bound PlayerProfile
lookup existing Zone only
require the Session is still a current member
require positive damage
```

Then execute one serialized combat event under the Zone monitor:

```text
synchronized(zone)
    revalidate membership
    mutate monster once
    if no mutation result -> false
    snapshot current Zone members
    build one result packet
    enqueue that same logical result to every active Zone member
    return true
```

Mutation and outbound enqueue order stay inside the same Zone monitor so concurrent attackers cannot publish HP transitions in a different order than the authoritative mutation order.

Example required ordering:

```text
A: 300 -> 290
B: 290 -> 280
```

must not be broadcast as:

```text
280 then 290
```

### 10.3 Concurrent lethal hit

If a monster has 10 HP and A/B impact concurrently:

```text
first Zone-serialized hit:
    10 -> 0
    one -100 broadcast

second hit:
    sees DIE
    no mutation
    no packet
```

No duplicate death packet and no negative HP.

---

## 11. MonsterPacketWriter

Create a dedicated:

```text
server/src/main/java/com/project/game/network/packet/MonsterPacketWriter.java
```

Do not add monster combat encoding to `PlayerPacketWriter`.

### 11.1 Injure packet

API concept:

```java
public Message injure(MonsterDamageResult result);
```

Encode:

```text
command = -106
int monsterId
long damage
long hpAfter
bool false
```

Only non-lethal results may use this method.

### 11.2 Death packet

API concept:

```java
public Message startDie(MonsterDamageResult result);
```

Encode:

```text
command = -100
int monsterId
long damage
bool false
```

Only killed results may use this method.

A lethal hit sends `-100` only. It does not send a preceding `-106` for the same hit.

No `REMOVE_MONSTER` is sent.

---

## 12. Why REMOVE_MONSTER Is Not Used

Current Unity `MONSTER_START_DIE` handling calls its existing monster death lifecycle.

Unity then:

```text
sets HP to zero
marks the monster dying
plays the built-in death animation
uses EffectImage 17
moves the dead sprite out of the scene
clears local focus
```

Therefore V1 leaves the runtime monster in the Zone with:

```text
hp = 0
status = DIE (1)
```

This is also required for later MAP_INFO persistence.

`REMOVE_MONSTER` remains out of scope.

---

## 13. Damage Calculation

V1 does not port the old combat formula.

Accepted damage is exactly:

```java
session.player().damage()
```

Current initial profile:

```text
damage = 10
```

Canonical Map1 Hổ:

```text
hp = 300
```

Therefore the deterministic default sequence is:

```text
hit 1  -> HP 290 -> -106
hit 2  -> HP 280 -> -106
...
hit 29 -> HP 10  -> -106
hit 30 -> HP 0   -> -100
```

`critical = false` for every V1 hit.

No random variance is introduced.

---

## 14. Multiplayer Semantics

All active members of the same Zone receive monster result packets, including the attacker.

Example with A and B in Map1/Zone0:

```text
A prepares + impacts M0
server M0: 300 -> 290

A receives -106(id=0, damage=10, hpAfter=290, crit=false)
B receives -106(id=0, damage=10, hpAfter=290, crit=false)
```

On lethal impact:

```text
A receives one -100
B receives one -100
```

A player in another Zone does not receive the packet.

A player who has destination MAP_INFO but has not sent FINISH_LOAD_MAP is not a combat member and cannot prepare/execute a valid attack.

---

## 15. Persistent Dead State

The previously frozen Zone lifetime behavior remains important.

If M0 dies in Map1/Zone0:

```text
hp = 0
status = 1
```

and all players leave, the Zone remains registered.

A later player entering the same Zone during the same server process receives MAP_INFO from the same runtime monster set:

```text
M0 hp = 0
M0 status = 1
```

No automatic respawn occurs in V1.

A server restart recreates runtime state from bootstrap and therefore restores initial HP/status; persistence across restart is out of scope.

---

## 16. No Range Validation Yet

Server-side range checking is deliberately excluded.

The frozen monster model currently has:

```text
server logical x/y = spawn anchor
Unity visual RUN patrol = xFirst ± rangeMove
```

Using the server anchor as an authoritative range position would reject some attacks against monsters the client visibly considers in range.

V1 is authoritative for:

```text
monster identity
same-Zone membership
prepare/impact pairing
single-use impact
HP
LIVE/DIE state
multiplayer ordering
```

V1 is not yet authoritative for:

```text
precise visual patrol coordinate
attack distance
```

Range validation must wait for a separately designed movement/position authority model.

---

## 17. No Remote Skill Animation Yet

The old server broadcasts `PLAYER_USE_SKILL_IN_AREA = -107` so other players see the attacker's skill animation.

V1 deliberately does not implement `-107`.

The local attacker already performs its own skill animation in Unity.

Other players in the Zone receive authoritative `-106/-100` HP/death results but may not yet see the attacker's corresponding remote skill animation.

This visual limitation is accepted for V1 and belongs to a later presentation/synchronization slice.

---

## 18. IN_GAME Protocol Allowlist and Dispatch

Current command constants already include:

```text
PLAYER_START_USE_ULTIMATE = -72
USE_SKILL = -108
MONSTER_INJURE = -106
MONSTER_START_DIE = -100
```

Add inbound permission while `SessionState.IN_GAME` for exactly:

```text
-72
-108
```

Add dispatch to dedicated handlers, conceptually:

```java
case MessageName.PLAYER_START_USE_ULTIMATE
        -> handlePrepareMonsterAttack(message);

case MessageName.USE_SKILL
        -> handleMonsterAttackImpact(message);
```

Outbound `-106/-100` do not require inbound allowlist entries.

Do not enable unrelated combat commands in this slice.

---

## 19. Payload Validation Rules

Current handler convention treats malformed payload structure/trailing bytes as `IOException`, then closes the Session.

Preserve that convention.

### 19.1 `-72`

Valid lengths/forms:

```text
1 byte: skillId only
6 bytes: skillId + targetType + targetId
```

For the six-byte form:

```text
targetType 0 = valid unsupported player target -> no pending monster attack
targetType 1 = monster target -> validate and maybe create pending
other targetType = malformed
```

Any other total length/trailing bytes are malformed.

### 19.2 `-108`

Valid forms:

```text
1 byte: type = -1
5 bytes: type 0 + playerId
5 bytes: type 1 + monsterId
```

Any other target type or trailing byte is malformed.

Well-formed unsupported target forms are no-ops, not protocol violations.

---

## 20. Dependency Wiring

Current `MapService` dependencies are:

```text
PlayerPacketWriter
MonsterRuntimeFactory
```

V1 changes them to:

```text
PlayerPacketWriter
MonsterPacketWriter
MonsterRuntimeFactory
```

Production and test composition must provide one `MonsterPacketWriter` explicitly.

Do not let MessageHandler construct its own packet writer.

Do not let MessageHandler broadcast directly to arbitrary Zone members.

MapService remains the coordinator for Zone membership + serialized monster mutation + result broadcast.

---

## 21. Expected Production File Surface

### Create

```text
server/src/main/java/com/project/game/monster/MonsterDamageResult.java
server/src/main/java/com/project/game/network/packet/MonsterPacketWriter.java
```

### Modify

```text
server/resources/json/EffectBootstrap.json
server/src/main/java/com/project/game/service/ResourceService.java
server/src/main/java/com/project/game/monster/RuntimeMonster.java
server/src/main/java/com/project/game/map/Zone.java
server/src/main/java/com/project/game/map/MapService.java
server/src/main/java/com/project/game/service/ServerServices.java
server/src/main/java/com/project/game/network/NetworkServer.java
server/src/main/java/com/project/game/network/MessageHandler.java
```

Likely test modifications/additions:

```text
server/src/test/java/com/project/game/service/ResourceServiceTest.java
server/src/test/java/com/project/game/monster/MonsterRuntimeFactoryTest.java or dedicated RuntimeMonsterTest
server/src/test/java/com/project/game/map/ZoneTest.java
server/src/test/java/com/project/game/map/MapServiceTest.java
server/src/test/java/com/project/game/network/MessageHandlerTest.java
server/src/test/java/com/project/game/network/NetworkIntegrationTest.java
server/src/test/java/com/project/game/network/packet/MonsterPacketWriterTest.java
```

No Unity source changes are expected.

No MonsterBootstrap changes are expected.

No MapBootstrap changes are expected.

---

## 22. Test Strategy

### 22.1 Effect resource migration tests

Prove:

```text
EffectBootstrap version = 1
exact IDs = [6,7,17]
6 unchanged
7 unchanged
17 dx=0 dy=-10 delay=50 icons=[1911,1912,1913,1914]
manifest reports effect version 1
type-3 response reports version 1 and exact three images
```

Reject:

```text
version 0
missing 17
extra effect ID
wrong 17 fields/icons
reordered canonical IDs if loader retains ordered contract
```

### 22.2 RuntimeMonster tests

Prove:

```text
300 - 10 = 290
status remains LIVE
result killed=false

10 - 10 = 0
status becomes DIE
result killed=true

hit on dead monster -> empty
non-positive damage -> empty
HP never negative
snapshot reflects mutation
```

### 22.3 Zone tests

Prove:

```text
hasLiveMonster correct for existing/missing/dead ID
damageMonster mutates correct ID
missing ID no-op
dead ID no-op
other monsters unchanged
ordered MAP_INFO snapshots remain deterministic
```

### 22.4 MapService tests

Prove:

```text
combat APIs do not lazily create Zones
MAP_INFO-created but pre-FINISH player cannot attack
FINISH member can target live monster
non-member cannot attack
wrong Zone cannot attack
same-Zone result broadcast includes attacker and peers
other Zone receives nothing
concurrent lethal hits produce one death result only
```

### 22.5 MessageHandler tests

Prove:

```text
-72 monster0 + -108 monster0 -> one hit
-108 without -72 -> no hit
-108 replay -> no second hit
-72 monster0 + -108 monster1 -> no hit and pending consumed
-72 monster0 + map change + stale -108 -> no hit
-72 one-byte no-target clears pending
-72 player target valid no-op
-108 type0 valid no-op
-108 type-1 valid no-op
unknown types malformed
trailing bytes malformed
```

### 22.6 Packet writer golden tests

Pin exact bytes/parse values:

```text
-106: int id + long damage + long hpAfter + bool false
-100: int id + long damage + bool false
```

### 22.7 Real TCP integration

With two clients in Map1/Zone0:

```text
A -72 M0
A -108 M0
A receives -106 hp=290
B receives -106 hp=290
```

Repeat valid prepared impacts until lethal:

```text
29 injure events total before death
30th accepted hit emits one -100
server runtime M0 hp=0/status=1
additional impact emits no monster result
```

Then leave/re-enter same Zone without server restart:

```text
MAP_INFO M0 hp=0/status=1
```

No respawn packet expected.

---

## 23. Manual Unity Gate

### 23.1 Effect cache migration

Start from a client that previously cached effect resource version 0.

Login after server V1 starts.

Expected:

```text
manifest advertises effect version 1
client requests type-3 effect resource
client accepts IDs 6,7,17
existing jump/fall/run effects remain correct
```

No manual cache deletion should be required for the normal version-migration path.

### 23.2 Combat smoke

Map1:

```text
focus one Hổ nanh kiếm
use normal available skill
visible local skill/projectile behavior remains normal
monster HP decreases by 10 per accepted impact
no disconnect
```

### 23.3 Death smoke

On lethal hit:

```text
monster enters existing death animation
EffectImage 17 resolves successfully
no KeyNotFoundException
monster disappears according to existing Unity death lifecycle
focus clears normally
```

### 23.4 Multiplayer smoke

Two clients in same Map1 Zone:

```text
A attacks
both A and B see HP/death state updates
no duplicate death transition
player presence/movement remains correct
```

Remote attacker skill animation is not required in V1.

### 23.5 Persistence smoke

Kill a monster, leave Map1, re-enter during same server process.

Expected:

```text
killed monster remains hp=0/status=dead through MAP_INFO
no respawn
other monsters remain alive
```

---

## 24. Explicit Non-Goals

Do not implement in this slice:

```text
monster AI attack (-101)
monster respawn (-105)
respawn timer
ADD_MONSTER runtime spawning
REMOVE_MONSTER death removal
server-side monster patrol
monster movement packet
server-authoritative attack range
remote player skill animation (-107)
MP consumption
skill cooldown enforcement
skill level/upgrade validation
full skill damage formula
critical hits
armor/reduction
dodge
lifesteal/manasteal
reflect damage
PvP
player death combat flow
EXP/potential reward
quest kill progress
drops/item map spawn
enemy aggro tables
DB monster persistence
cross-restart monster state persistence
Unity source changes
new monster template/dart data
```

---

## 25. Acceptance Invariants

After implementation:

```text
Effect resource:
version = 1
IDs exactly 6,7,17
6/7 unchanged
17 canonical

Combat input:
-72 + -108 allowed only IN_GAME
one valid -72 prepares at most one pending monster attack
one well-formed -108 consumes pending exactly once
arbitrary/replayed -108 cannot repeatedly damage
map change invalidates pending
pre-FINISH session cannot combat

Damage:
source = PlayerProfile.damage()
initial default = 10
critical = false
HP never negative
LIVE -> DIE happens once

Packets:
non-lethal = -106
lethal = -100 only
no REMOVE_MONSTER
all current same-Zone members receive result
other Zones receive nothing

Runtime persistence:
dead monster remains hp=0/status=1 in Zone
same-process MAP_INFO observes dead state
no respawn

Frozen regressions:
Map0 zero monsters
Map1 six initial monsters
map travel unchanged
ADD/REMOVE_PLAYER unchanged
PLAYER_MOVE unchanged
local monster patrol unchanged
Unity source unchanged
MonsterBootstrap unchanged
```

---

## 26. Stop Point

Stop the implementation series when:

```text
EffectImage 17 safely migrates through resource version 1
one player can damage Hổ via canonical -72/-108 flow
same-Zone clients receive authoritative -106 updates
lethal hit produces exactly one -100
Unity death lifecycle runs without missing-effect exception
dead runtime state persists for Zone lifetime
all frozen map/player/movement behavior remains green
```

Do not continue directly into respawn, monster AI, full skill validation, rewards, or server-authoritative range.

Those require separate design/review slices after Combat V1 is frozen.
