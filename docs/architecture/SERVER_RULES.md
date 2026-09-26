# SERVER RULES V2.1

> **Status:** Sole authoritative architecture, coding, readability, ownership, concurrency, persistence, and safety contract for `server/**`
>
> **Applies to:** planning, implementation, refactor, review, testing, protocol, persistence, gameplay, concurrency, naming, package/file structure, and Java style.
>
> **Reference source:** sibling legacy project `../rongthanchibi` / legacy `rongthan` source is the main readability and gameplay-flow reference.
>
> **Primary goal:** preserve correctness while keeping gameplay code simple enough that a beginner Java developer can follow the normal flow without first learning architecture jargon.

---

# 1. Non-negotiable rules

These rules override style preferences and must be checked on every gameplay task.

```text
1. Feature.java owns the main feature behavior.
2. Manager does not become the default place for gameplay logic.
3. Service means message / packet / send / broadcast unless explicitly approved otherwise.
4. Zone owns execution timing and mutation ordering.
5. Entity owns the behavior that naturally describes that entity.
6. Repository owns persistence.
7. Handler owns protocol parsing/dispatch, not gameplay.
8. Packet writer owns binary serialization.
9. One mutable gameplay concept must have one authoritative owner.
10. Do not add abstraction for hypothetical future needs.
11. Prefer simple imperative Java over clever Java when both are correct.
12. Avoid unnecessary file jumps.
13. Inspect the equivalent legacy feature before changing an existing gameplay feature.
14. Do not block Zone execution on JDBC, socket I/O, HTTP, filesystem I/O, or external waits.
15. Do not nest cross-Zone owner waits.
16. Do not change protocol bytes, DB schema, persistence ordering, or gameplay behavior unless explicitly approved.
17. Do not silently turn temporary coordination code into permanent feature ownership.
18. If ownership is unclear, stop planning implementation and resolve ownership first.
```

---

# 2. Authority order

When sources disagree, use this order:

```text
1. Locked correctness invariants
   - protocol compatibility
   - database schema/data safety
   - Zone single-writer ownership
   - persistence ordering
   - Session/account ordering
   - approved concurrency invariants

2. docs/architecture/SERVER_RULES.md

3. Newly approved task/fix plan created after this rule

4. server/README.md for operational commands

5. Older plans/specifications

6. Legacy source
```

Important:

```text
Current code structure is NOT automatically authoritative.
```

Existing production structure may be temporary or wrong.

A coding model must not justify bad ownership by saying:

```text
"the current code already does it this way"
```

Only locked correctness behavior has priority over this rule.

---

# 3. Mandatory pre-coding gate

Before planning or changing `server/**`, the coding model MUST:

```text
1. Read this file.
2. Inspect the current implementation.
3. Identify the feature center.
4. Identify the Manager responsibility.
5. Identify the Service responsibility.
6. Identify protocol impact.
7. Identify persistence impact.
8. Identify concurrency/execution owner.
9. Identify authoritative mutable state.
10. Inspect the equivalent legacy implementation when available.
11. State behavior that must remain unchanged.
12. State focused tests required.
```

If this file cannot be read:

```text
STOP before changing server code.
```

If ownership is still unclear after inspection:

```text
STOP before implementation.
Resolve ownership first.
```

---

# 4. Default feature shape

The default gameplay feature shape is:

```text
Feature.java
    runtime state
    main gameplay behavior
    update/run logic
    normal feature actions

FeatureTemplate.java
    static definition/configuration

FeatureManager.java
    init/load
    catalog
    create/find
    global collection when genuinely needed
    open/close lifecycle of many instances

FeatureService.java
    packet/message/send/broadcast only when needed

Repository
    persistence only
```

Not every feature needs every file.

Do not create files just to make folders symmetrical.

Good:

```text
Monster.java
MonsterTemplate.java
MonsterManager.java
```

Avoid automatically adding:

```text
Factory
Registry
Coordinator
Processor
Context
Result
Runtime
Facade
Mapper
Snapshot
Scheduler
Operation
```

unless each type solves a demonstrated problem.

---

# 5. Feature center rule

Every gameplay feature must have one obvious first file to open.

Examples:

```text
Player behavior       → Player.java
Monster behavior      → Monster.java
Npc behavior          → Npc.java
Item behavior         → Item.java
Shop behavior         → Shop.java
Map structure         → Map.java
Zone runtime/world    → Zone.java
```

A reviewer should be able to understand the normal flow with few file jumps.

If understanding one gameplay action requires:

```text
Manager
→ Service
→ Coordinator
→ Result
→ Context
→ Feature
```

the design is probably too fragmented.

Line count alone is NOT a reason to split a cohesive feature.

A large cohesive file is acceptable if it is easier to read than many tiny files.

---

# 6. Manager rule

A `Manager` is NOT the default home for gameplay behavior.

A Manager may own:

```text
init
load
catalog
global collection
create
find
register/remove
open/close
lifecycle of many instances
```

Good examples:

```text
MonsterManager.init()
MonsterManager.findTemplate()

NpcManager.init()

ItemManager.createItem()
ItemManager.findTemplate()

MapManager.init()
MapManager.findMap()
MapManager.close()
```

Strong review warnings:

```text
MapManager.movePlayer()
MonsterManager.attack()
MonsterManager.moveMonster()
NpcManager.chat()
ItemManager.upgradeItem()
PlayerManager.injure()
```

If logic naturally describes what one runtime object does, prefer the runtime object.

```text
Player.move()
Monster.attack()
Npc.chat()
Item.upgrade()
```

---

# 7. Service rule

In this project:

```text
Service = message / packet / send / broadcast
```

Examples:

```text
AreaService.addPlayer(...)
AreaService.removePlayer(...)
AreaService.move(...)
```

Do NOT use `Service` as a generic business layer.

Avoid:

```text
PlayerService.move()
MapService.changeMap()
MonsterService.attack()
UpgradeService.processUpgrade()
```

Cross-owner coordination does NOT automatically justify creating a `Service`.

If a cross-owner operation is truly needed:

```text
design its owner explicitly in the approved task plan
```

Do not invent a `Service` merely because no obvious owner exists yet.

---

# 8. Repository, Handler, Packet boundaries

Repository owns persistence.

```text
load
insert
update
delete
query
```

Gameplay objects must not know JDBC/SQL details.

Preferred:

```text
Player
→ PlayerSaveData
→ PlayerRepository
→ JDBC
```

Handler owns protocol boundary:

```text
read packet fields
validate packet shape
call gameplay entry point
send response
```

Handler must not contain gameplay decisions.

Packet writers own binary encoding.

Gameplay decides:

```text
what happened
```

Packet writer decides:

```text
how it is serialized
```

---

# 9. Data / Template / Runtime distinction

Keep separate:

```text
Persistence row
→ durable DB representation

Template
→ canonical static definition

Runtime object
→ live mutable gameplay state
```

Example:

```text
DB/resource data
→ MonsterTemplate
→ Monster
```

Do not make gameplay understand SQL rows.

Do not duplicate Template data into runtime state without a reason.

---

# 10. Newbie-readable Java style

Default style:

```text
simple
imperative
explicit
top-to-bottom
easy to debug
```

Prefer:

```java
Player player = session.player();

if (player == null) {
    return false;
}

if (player.isDead()) {
    return false;
}

player.move(x, y);
return true;
```

Avoid dense validation + mutation:

```java
if (player == null || player.isDead() || !player.move(x, y)) {
    return false;
}
```

Do not hide important side effects inside compound boolean expressions.

Preferred rule:

```text
validation first
mutation second
output/broadcast last
```

---

# 11. Java syntax restraint

## Prefer early return

```java
if (player == null) {
    return;
}

if (player.isDead()) {
    return;
}
```

Avoid deep nesting when early return is clearer.

## Prefer explicit types

Preferred:

```java
List<Session> rejected = area.removePlayer(...);
Player player = session.player();
Zone zone = map.findZone(zoneId);
```

Avoid `var` in normal gameplay code.

## Prefer normal loops

Preferred:

```java
for (Player player : players) {
    if (player.isDead()) {
        continue;
    }

    ...
}
```

Use Stream only when it is clearly shorter and equally readable.

## Limit Optional

Do not use `Optional` as normal gameplay control flow.

Preferred:

```java
Monster monster = zone.findMonster(id);

if (monster == null) {
    return;
}
```

## Limit lambdas

Lambda is acceptable at a real execution boundary:

```java
zone.call(() -> {
    player.move(x, y);
    return null;
});
```

Do not turn normal gameplay into functional chains for style.

---

# 12. Record / Result / Snapshot rule

These types are allowed when they represent a real boundary.

Valid reasons include:

```text
persistence boundary
packet boundary
cross-thread boundary
cross-Zone boundary
stable handoff
```

Examples:

```text
PlayerSaveData
MapTemplate
stable cross-Zone intent/handoff data
```

Do not create wrappers only because a method returns multiple values.

Avoid unnecessary:

```text
MoveResult
AttackResult
DamageResult
RuntimeContext
OperationResult
```

For every new Result/record/Snapshot, the coding model must answer:

```text
What real boundary requires this type?
Why is direct control flow less clear or less safe?
```

If there is no concrete answer, do not create the type.

---

# 13. Naming rule

Use direct gameplay vocabulary.

Prefer:

```text
init
load
save
create
find
add
remove
enter
leave
move
moveTo
attack
updateAttack
findTarget
injure
die
respawn
revive
upgrade
chat
open
close
```

Avoid unnecessary technical naming:

```text
executePlayerMovement
processRuntimeTransition
resolveGameplayOperation
performAttackResolution
coordinateMapMovement
```

The class already provides context.

Inside `Player`:

```text
move()
```

not:

```text
movePlayer()
```

Inside `Monster`:

```text
attack()
```

not:

```text
executeMonsterAttack()
```

---

# 14. find / get / create naming contract

Use names consistently.

```text
findX()
= search only
= does not create
= may return null

getX()
= return an expected existing object
= does not silently create
= may throw if missing when that contract is clear

createX()
= create a new object

getOrCreateX()
= may return existing or create new
```

Do NOT hide creation behind an ordinary `getX()` name.

Example:

```text
Map.findZone(zoneId)
Map.getZone(zoneId)          // only if Zone must already exist
Map.getOrCreateZone(zoneId)  // if creation is allowed
```

Names must expose side effects.

---

# 15. Legacy-reference gate

The legacy source is the approved reference for:

```text
feature center
class responsibilities
Manager usage
Service usage
gameplay vocabulary
normal call flow
method naming
file count
top-to-bottom readability
```

Before changing an existing feature, report:

```text
Legacy feature center:
- exact file/class

Legacy Manager responsibilities:
- exact responsibilities

Legacy runtime-object responsibilities:
- exact responsibilities

Legacy Service responsibilities:
- exact responsibilities

Legacy normal flow:
- concise call flow

Adopt:
- readability/naming/ownership strengths

Reject:
- unsafe or obsolete choices
```

Example:

```text
Legacy feature center:
Monster.java

Legacy MonsterManager:
init/load templates only

Legacy Monster:
update
findTarget
attack
injure
die
respawn

Legacy Zone:
calls monster.update()

Legacy Service:
broadcast/send monster packets
```

The statement:

```text
"legacy inspected"
```

is not enough.

---

# 16. What must NOT be copied from legacy

Do NOT copy:

```text
global singleton architecture
direct JDBC inside gameplay
packet construction inside runtime entities
network Message parsing inside entities
platform-thread-per-Zone as a default
arbitrary multi-thread mutation
large lock-per-entity concurrency model
unsafe global mutable state
obsolete protocol/data behavior
```

Use legacy for readability and responsibility shape, not obsolete technical architecture.

---

# 17. Mutable state authority rule

Every mutable gameplay concept must have one authoritative owner.

Examples of concepts:

```text
current Zone membership
Player position
Player HP
Monster HP
Monster target
Zone capacity
effect lifetime
```

Duplicate representations are allowed only when their role is explicit:

```text
authority
cache
durable checkpoint
network binding
derived value
```

Two mutable fields must not silently both act as authority.

When reviewing a duplicate state, explicitly answer:

```text
Which field/object is authoritative?
Which copy is derived or cached?
When is it synchronized?
Who may mutate it?
```

If these answers are unclear, ownership is not finished.

---

# 18. Player / Session / Zone boundary

`Player` is the gameplay runtime object.

`Session` is network/session state.

`Zone` is realtime execution/world ownership.

Player owns behavior such as:

```text
move
injure
revive
addPotential
change its own location fields
future inventory/skill/task behavior
```

Session may:

```text
hold connection state
bind Player
send messages
track connection lifecycle
hold necessary online binding metadata
```

Zone owns:

```text
runtime execution
membership
admission/capacity
mutation ordering
live world collections
```

## Session / Player / Zone authority

Joined realtime membership authority is `Zone.members`, keyed by exact
`Session` identity. `Zone.hasPlayer(session)` is the membership truth.

`Session.zone` is a routing/back-reference and candidate owner. It is not proof
that the Session is currently a member of that Zone.

`Player.mapId`/`zoneId`/`x`/`y` are logical location and handoff state.
`mapId`/`x`/`y` participate in persistence; `zoneId` is runtime-only and is not
persisted. None of these fields proves realtime Zone membership.

`Zone.reservedPlayers` is destination admission state for a pending handoff and
is separate from active `Zone.members`.

For a joined Session, the invariant is: `Session.zone == Zone`, exact Session
membership in that Zone, and Player map/zone location matches that Zone. During
a committed cross-Map handoff, the Session is intentionally detached from the
source Zone while the destination reservation and Player destination location
await `FINISH_LOAD_MAP` admission.

Important:

```text
Zone decides WHEN Player mutation runs.
Player decides WHAT Player mutation does.
```

Do not make Session the general gameplay model.

Do not spread new `Session → player()` dependencies through gameplay without review.

Do not change the current `Session / Player / Zone` membership representation casually.

Specifically, a coding model MUST NOT independently:

```text
change Zone members from Session to Player
create ZoneMember / PlayerContext / OnlinePlayer wrappers
move Zone authority into Session
duplicate another currentZone authority
```

Such changes require a dedicated approved design task.

---

# 19. Map / MapManager contract

Ownership tree:

```text
MapManager
→ Map
→ Zone
```

`Map` owns:

```text
MapTemplate
Zones
findZone
zones
findWaypoint
map-specific rules
```

Public/world Map contract:

```text
MapManager owns the public Map registry.
Map owns the Zones inside each public Map.
Public Map construction creates exactly minZone Zones.
Normal gameplay uses Map.findZone(zoneId) for existing Zones.
An absent public Zone is rejected.
Normal Player/Monster/client paths must not create a public Zone.
maxZone does not authorize public lazy Zone creation.
```

`MapManager` primarily owns:

```text
Map init/create
Map registry
find Map
open/close Map lifecycle
global Map collection
```

A future Dungeon run may own private runtime Maps created from shared
MapTemplate/static data. Those private Maps are not automatically part of the
public `MapManager` registry. Dungeon runtime creation requires its own
approved feature design.

Do not duplicate Map-owned Zone APIs in MapManager without a demonstrated global need.

Default:

```java
Map map = mapManager.findMap(mapId);

if (map == null) {
    return;
}

Zone zone = map.findZone(zoneId);
```

Strong review warnings:

```text
MapManager.movePlayer()
MapManager.injurePlayer()
MapManager.attackMonster()
```

Cross-Map/cross-Zone transition is a special technical boundary.

Its final owner MUST be explicitly approved by the task plan.

A coding model must not independently decide:

```text
"put it in MapManager"
"put it in Player"
"create MapService"
"create TransitionCoordinator"
```

because the operation crosses owners.

---

# 20. Zone contract

Zone is the realtime execution owner.

Target:

```text
1 ACTIVE Zone
= 1 virtual thread
= 1 writer
```

Zone owns:

```text
runtime writer
membership
enter/leave
capacity/admission
entity collections
mutation ordering
Zone lifecycle
```

Zone should call entity behavior:

```java
player.update();
monster.update();
```

Zone should not absorb the behavior of the entity.

Bad long-term direction:

```text
Zone.findMonsterTarget()
Zone.calculateMonsterAttack()
Zone.moveMonster()
Zone.respawnMonster()
```

Preferred:

```text
Monster.findTarget()
Monster.attack()
Monster.move()
Monster.respawn()
```

Zone provides execution and world context.

---

# 21. Feature ownership examples

## Monster

Feature center:

```text
Monster.java
```

Monster should own:

```text
update
findTarget
move/patrol/chase
attack
injure
death
respawn
aggro/enemy behavior
```

MonsterManager should mainly own:

```text
template/catalog init
create/find when meaningful
global lifecycle only if genuinely needed
```

## Npc

Npc owns NPC behavior.

NpcManager mainly owns template/catalog/init/create/find.

## Item

Item owns item behavior.

ItemManager mainly owns template/catalog/init/create/find.

Do not move normal feature actions into Manager for convenience.

---

# 22. Concurrency / blocking / lifecycle invariants

Cross-Zone operations must avoid circular waits.

Never:

```text
sourceZone.call(...)
    → destinationZone.call(...)
```

while holding source ownership.

Required shape:

```text
capture source intent
→ leave source execution
→ destination admission/reservation
→ source revalidation/commit
→ handoff
```

Do not block Zone gameplay execution on:

```text
JDBC
HTTP
filesystem I/O
socket read/write
external Future waits
unbounded blocking queue submission
```

Zone lifecycle target:

```text
ACTIVE
FROZEN
STOPPED
```

FROZEN preserves runtime state.

Time-based mechanics should prefer absolute deadlines where suitable:

```text
respawnAt
effectEndAt
cooldownUntil
itemExpireAt
```

---

# 23. Persistence / hot-path invariants

Runtime authority:

```text
Zone + live runtime objects
```

Durable checkpoint:

```text
MySQL
```

Preferred persistence flow:

```text
runtime mutation
→ stable SaveData capture
→ Repository outside Zone
→ DB
```

Final disconnect ordering:

```text
process prior Zone work
→ detach realtime mutation
→ capture stable save state
→ DB save outside Zone
→ release account reservation
```

Hot paths include:

```text
Zone tick
Monster AI
target search
movement
combat
Effect/DoT
crowded broadcast
```

Prefer:

```text
direct runtime state
simple loops
few allocations
few wrappers
```

Do not use Snapshot/Optional/Stream/DTO/Result in hot paths without a concrete reason.

---

# 24. Testing rule

Tests must validate production design, not distort it.

Do not add public production APIs only for tests.

Concurrency tests should be deterministic.

Avoid:

```text
Thread.sleep(...)
```

for synchronization.

Prefer:

```text
CountDownLatch
explicit barriers
deterministic state coordination
```

Tests should prefer observable behavior over internal counters.

Example:

```text
prove destination capacity can be reused
```

is stronger than only asserting:

```text
reservedCount == 0
```

Package-private access may be used when a focused test genuinely needs it, but production API must not be expanded casually.

---

# 25. Touched-slice and final review gate

When a task owns a feature slice, that slice must leave the task compliant with this rule.

For every touched top-level type, review:

```text
Is this still the correct feature center?
Is Manager doing gameplay?
Is Service doing business logic?
Is Zone doing entity behavior?
Is Handler doing gameplay?
Is a Result/record/wrapper truly required?
Is there duplicate mutable authority?
Did a convenience API hide ownership?
Did a method hide side effects?
Did we increase file jumps without a correctness reason?
```

Before implementation, report:

```text
Current feature center:

Legacy feature center:
Legacy files/classes inspected:

Legacy Manager responsibilities:
Legacy runtime-object responsibilities:
Legacy Service responsibilities:
Legacy normal flow:

Authoritative mutable state:

Adopt:
Reject:

Target feature center:
Target Manager responsibilities:
Target Service responsibilities:
Target execution owner:

Files expected to change:
Behavior/contracts preserved:
Focused tests:
```

After implementation, report:

```text
Final feature center:
Normal gameplay flow:
Manager responsibilities:
Service responsibilities:
Authoritative mutable state:
Files changed:
Files removed/merged/renamed:
Names simplified:
Focused tests run:
Full Maven gate:
Manual/DB/Unity/TLS gates not run:
Known remaining debt:
```

Final reviewer must be able to answer quickly:

```text
Where do I start reading this feature?
What object contains the main gameplay logic?
What does the Manager actually manage?
What does Service actually send?
Who owns execution?
Who owns mutable state?
Who owns persistence?
Where is packet encoding?
How many files must I open for the normal flow?
Can a beginner follow the method top-to-bottom?
```

Desired result:

```text
easy to find
easy to read
easy to debug
few file jumps
simple Java
direct game vocabulary
correct ownership
safe concurrency
safe persistence
```

When architecture purity and readability conflict without a correctness reason:

> **Prefer the implementation that reads more directly as game logic.**

When a clever Java construct and a simple Java construct are equally correct:

> **Prefer the simpler one.**
