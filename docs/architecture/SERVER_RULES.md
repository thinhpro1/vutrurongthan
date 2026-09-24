# SERVER RULES

> **Status:** Sole authoritative architecture, coding, readability, and safety contract for `server/**`
>
> **Applies to:** server implementation, refactor, review, testing, shared Unity/server contracts, persistence, protocol, concurrency, gameplay structure, naming, and readability.
>
> **Reference source:** sibling legacy project `../rongthanchibi` may be used as a readability/gameplay-flow reference only.

---

# 1. Core goal

The server must be:

```text
easy to find
easy to read
easy to modify
hard to accidentally break
```

This is a realtime game server.

Gameplay code should read like game logic, not like framework machinery.

Prefer:

```text
direct game vocabulary
feature-first packages
obvious feature centers
cohesive gameplay files
few meaningful abstractions
few unnecessary file jumps
top-to-bottom execution flow
```

Do not optimize for:

```text
maximum number of interfaces
maximum immutability ceremony
maximum Design Patterns
perfectly symmetrical folders
one top-level file per tiny type
small line counts
architecture diagrams that are cleaner than the code
```

Correctness boundaries still win over style.

---

# 2. Authority and priority

When sources disagree, use this order:

```text
1. Current production runtime / database / protocol / concurrency invariants
2. This file: docs/architecture/SERVER_RULES.md
3. Newly approved feature/refactor design created after this rule
4. server/README.md for operational instructions
5. Older plans/specifications
6. ../rongthanchibi legacy source
```

The approved target architecture does not authorize an early or repo-wide
cutover. Until a dedicated migration phase replaces an existing contract, the
current production contract remains authoritative.

---

# 3. Mandatory coding-model gate

Before planning, reviewing, testing, or changing `server/**`, the coding model MUST:

```text
1. Read this file.
2. Inspect the current implementation.
3. Identify the feature owner and feature center.
4. Identify protocol impact.
5. Identify persistence impact.
6. Identify concurrency impact.
7. Identify behavior that must remain unchanged.
8. Identify focused tests required.
```

For gameplay feature work, also follow the legacy-reference gate below.

If this file is missing or unreadable:

```text
STOP before changing server code.
```

Do not claim runtime success from static review alone.

---

# 4. Current technology contract

Current server stack:

```text
Language             Java 21
Build                 Maven
JSON                  Gson
Connection pool       HikariCP
Database access       Plain JDBC
Database              MySQL-compatible
Tests                 JUnit Jupiter
Networking            ServerSocket / Socket
TLS                   Java SSL/TLS
Protocol              Legacy binary protocol compatible with Unity client
```

Do not introduce major frameworks without an approved design solving a demonstrated problem:

```text
Spring / Spring Boot
JPA / Hibernate
Netty
Redis
Kafka
Microservices
DI framework
Event-bus framework
ORM framework
Flyway / Liquibase
```

Plain/direct Java is preferred.

---

# 5. Feature-first organization and gameplay readability

A developer should be able to guess where code lives:

```text
Player        → player/
Monster       → monster/
Map / Zone    → map/
Combat        → combat/
Login         → account/
Protocol      → network/
DB            → persistence/
Static data   → resource/
```

The same principle applies to every other gameplay domain, including for example:

```text
Npc
Boss
Item / ItemMap
Skill
Effect
Quest
Shop
Dungeon
Giftcode
Upgrade
Event
Clan
Pet
Waypoint / Point
```

Every meaningful gameplay feature must have an obvious feature center.

Prefer:

```text
direct game vocabulary
short context-aware class/method names
legacy vocabulary when the meaning is still correct
cohesive gameplay entities
obvious run/update/lifecycle flow
few meaningful abstractions
few unnecessary file jumps
large cohesive files when appropriate
```

A normal gameplay flow should be understandable mostly from the class and method
names without requiring the reader to understand the architecture first.

Do not split by line count alone.

Do not create:

```text
Factory
Manager
Service
Registry
Scheduler
Snapshot
Result
Dto
Mapper
```

just to make the architecture look complete.

Every top-level type must justify the navigation cost it introduces.

---

# 6. Legacy source naming/readability-reference gate

The sibling legacy project:

```text
../rongthanchibi
```

is the approved reference for **gameplay naming, vocabulary, readability, and
normal gameplay flow**.

It is not the authority for the new runtime architecture.

## Scope

This gate applies to **all existing gameplay/server feature domains**, not only
Player and Monster.

Examples include, but are not limited to:

```text
Player
Monster
Boss
Npc
Item
ItemMap
Skill
Effect
Map / Zone
Waypoint / Point
Quest
Shop
Dungeon
Giftcode
Upgrade
Event
Clan
Pet
resource/catalog gameplay features
other existing game-domain features
```

Before planning or changing an existing feature, inspect the equivalent legacy
implementation when one exists.

If `../rongthanchibi` is available, skipping this inspection is a rule
violation.

If the sibling project is unavailable or no equivalent feature can be found:

```text
report the missing/unavailable reference explicitly
report the files/names/terms searched when applicable
do not invent legacy names
do not claim the legacy gate passed
```

Do not inspect the legacy source only to understand behavior. Study how it makes
the feature easy to read.

Study:

```text
feature center
class names
method names
gameplay vocabulary
normal call flow
run/update/lifecycle flow
field vs behavior balance
inheritance/base-state choices
Manager usage
package/file organization
how many files are needed to understand the normal flow
```

## Mandatory pre-coding report

Before coding, report:

```text
Legacy reference inspected:
- exact relevant files/classes

Legacy vocabulary/flow inspected:
- exact useful names and normal flow
- for example: run / update / addNpc / addPoint / attack / move / ...

Adopt:
- simple naming/readability strengths worth carrying forward

Reject:
- legacy choices that conflict with current architecture
```

A generic statement such as:

```text
"legacy source inspected"
```

without exact files/classes and useful vocabulary/flow does not satisfy this
gate.

## Naming rule

When the legacy source already has a short, clear gameplay name and the new code
means the same thing, prefer the same simple vocabulary.

Examples of the preferred style:

```text
run
update
init
load
save
add
remove
find
get
move
moveTo
attack
updateAttack
findTarget
injure
die
respawn
addNpc
removeNpc
addPoint
addItem
removeItem
addEffect
removeEffect
join
leave
```

These examples express a naming style. Do not mechanically reuse a name if the
new semantics are genuinely different.

Do not replace a clear game action with a longer technical phrase merely to
sound more architectural.

Prefer:

```text
addNpc
addPoint
update
attack
move
```

over:

```text
registerNpcRuntimeEntity
resolveWaypointRegistration
processRuntimeExecutionCycle
executeAttackTransition
processMovementOperation
```

when both versions mean the same thing.

The class already provides context. Avoid repeating that context in every
method name.

For example, inside a `Zone`, `Monster`, `Npc`, `Quest`, or `Shop`, prefer the
shortest name that remains clear in that class.

If new code changes a clear legacy name for the same feature concept, the
coding model MUST explain before coding:

```text
Legacy name:
New name:
Why the legacy name is no longer accurate:
```

No explanation is required when there is no useful legacy equivalent or the
semantics are genuinely new, but the new name must still follow the same direct
game-vocabulary style.

## Technical-name restraint

Words such as:

```text
Runtime
Execution
Context
Coordinator
Processor
Transition
Resolution
Operation
Orchestrator
Facade
Command
Result
Snapshot
Handler
```

are not banned.

They are acceptable at a real technical boundary, such as protocol/network
handling, persistence, or a genuine execution abstraction.

In gameplay code, do not add these words merely to make a name sound formal.

If a simple game verb expresses the same meaning, use the game verb.

## Flow rule

Prefer code whose normal flow reads top-to-bottom like gameplay:

```text
run
→ update
→ move
→ attack
```

or:

```text
update
→ findTarget
→ updateMove
→ updateAttack
```

rather than a chain of architecture terminology that must be decoded before the
gameplay can be understood.

For a new feature with no useful legacy equivalent, follow the same style:

```text
short names
direct game vocabulary
obvious feature center
few file jumps
top-to-bottom flow
```

## What must NOT be copied

The legacy source is NOT authoritative for:

```text
Spring/JPA architecture
global mutable singletons
direct DB access from gameplay
packet construction inside gameplay entities
giant switch/controller ownership
platform-thread-per-Zone as a scalability default
network/session threads mutating gameplay entities directly
cross-thread runtime mutation hidden behind locks
always-running idle Zone loops
unsafe concurrency
obsolete gameplay data
obsolete protocol behavior
old persistence behavior
```

The approved target may deliberately use virtual threads.

Do not confuse:

```text
legacy thread-per-object implementation
```

with:

```text
1 ACTIVE Zone = 1 virtual thread = 1 writer
Session reader/writer virtual threads = blocking network infrastructure
```

The important distinction is ownership.

The target architecture may differ from legacy while the **gameplay naming and
reading style should remain comparably simple whenever semantics allow it**.

---

# 7. Runtime Entity/base-class rule

A runtime base class such as:

```text
Entity
CombatEntity
```

is allowed only when multiple real runtime entities genuinely share the state
or behavior.

Potential shared state:

```text
runtime id
position
HP / max HP
alive/dead state
common combat state
common zone/world association
common effects/timers
```

Do not introduce inheritance merely to shorten subclasses.

Feature-specific state stays with the feature.

Do not make gameplay runtime classes extend persistence/JDBC row types.

---

# 8. Data / Template / runtime distinction

Keep distinct:

```text
Persistence row
→ raw durable DB representation

Template
→ canonical static game definition

Runtime object
→ live mutable gameplay state
```

Example:

```text
DB row / JSON
→ repository / loader
→ MonsterTemplate
→ Monster
```

Do not make gameplay code understand SQL row details.

---

# 9. Optional / record / Snapshot / Result rule

`Optional`, `record`, Snapshot, Result and Event types are allowed when they
clarify a real boundary or have independent semantic value.

They are NOT defaults for ordinary realtime control flow.

Avoid turning hot gameplay into:

```text
entity
→ Optional<ResultRecord>
→ SnapshotRecord
→ EventRecord
→ PacketWriter
```

without a real boundary requiring those types.

Local result/state types should normally be nested under their owner if they are
needed at all.

---

# 10. Manager / Service / Factory rule

Use `Manager` only when it genuinely owns meaningful authority such as:

```text
collection
catalog
registration
create/init
find
feature lifecycle/update
```

Use `Service` for a real cross-owner/use-case operation.

A Factory is justified only when object creation has meaningful composition or
policy.

Do not keep/create Factory+Manager+Registry+Scheduler symmetry without real
responsibilities.

---

# 11. Packet boundary

Gameplay decides:

> what happened?

Packet writers decide:

> how is that serialized to the Unity protocol?

Packet encoding belongs under:

```text
network/packet/
```

Do not put binary encoding into Player/Monster/Boss/Npc merely because a legacy
source did so.

---

# 12. Approved Zone runtime ownership target

Concurrency correctness wins over class-tree aesthetics.

## Current production contract

Until a dedicated migration phase replaces it, current synchronization remains
authoritative. Existing `synchronized(zone)` behavior must not be removed
opportunistically.

## Approved target

```text
1 ACTIVE Zone
= 1 virtual thread
= 1 writer
```

Zone owns mutation authority for live state belonging to that Zone, including
when present:

```text
Player
Monster
Boss
Npc
ItemMap
active gameplay effects
other live Zone world state
```

Only Zone execution may mutate Zone-owned runtime entities.

External execution contexts such as:

```text
Session/network virtual threads
persistence/JDBC execution
web/admin/payment input
other Zones
```

must communicate through an explicit Zone input or handoff boundary.

Do not solve normal gameplay concurrency by allowing arbitrary threads to mutate
the same entity and then wrapping every entity in locks.

Zone ownership does not mean all gameplay logic belongs in `Zone`.

Prefer:

```text
Zone
→ membership, world authority, execution, mutation ordering

Entity
→ state transitions and gameplay behavior that naturally belong to it

Manager / cross-owner Service
→ surrounding lookup/use-case orchestration only when truly needed
```

Do not let `Zone` become the hidden implementation file for Monster AI.

Cross-Zone operations must avoid circular waits between Zone owners.

---

# 13. Blocking I/O boundary

Blocking APIs are intentionally allowed where they keep implementation simple
and remain outside realtime Zone execution.

Allowed examples:

```text
Session reader VT → Socket.read
Session writer VT → Socket.write
login/load before Player enters Zone → JDBC
persistence worker/VT → JDBC
```

Forbidden inside Zone gameplay execution:

```text
socket read/write
JDBC
HTTP
filesystem I/O
waiting on an external Future
indefinitely blocking queue submission
other external blocking operations
```

A slow client or slow DB must not stall movement, combat, AI, Effect/DoT, or
other Zone gameplay.

Queues/admission between boundaries must be bounded or explicitly backpressured.

Virtual threads reduce blocking-thread cost; they do not remove DB connection,
memory, bandwidth, or backpressure limits.

---

# 14. Zone lifecycle and freeze semantics

Target lifecycle:

```text
ACTIVE
FROZEN
STOPPED
```

`ACTIVE`:

```text
Zone runtime exists
Zone owner virtual thread executes realtime gameplay
```

`FROZEN`:

```text
Zone runtime still exists in memory
runtime entity state is not destroyed
realtime ticking is suspended/parked
```

Example:

```text
Monster HP = 120 / 200
Zone becomes FROZEN
Zone later wakes
Monster HP remains 120 / 200
```

unless a specific mechanic explicitly defines elapsed-time reconciliation.

`STOPPED` is for actual shutdown/unload, not normal empty-Zone idling.

Time-based mechanics that must advance while frozen should prefer absolute
deadlines where suitable:

```text
respawnAt
effectEndAt
cooldownUntil
itemExpireAt
```

On wake, reconcile against current time.

Mechanics such as:

```text
regen
DoT while nobody is present
world/event boss lifecycle
```

must define explicit frozen-Zone semantics.

A Zone with an event/boss/realtime responsibility that must continue advancing
is not eligible to freeze merely because player count reached zero.

---

# 15. Hot realtime path discipline

Hot-path candidates include:

```text
Zone tick
Monster AI
target search
movement
damage/combat
Effect/DoT update
crowded broadcast preparation
```

Prefer direct mutable runtime state and straightforward loops.

Do not allocate or transform through:

```text
Snapshot
Optional
Stream
temporary List
DTO
Result wrapper
Mapper
```

inside hot paths merely for symmetry or style.

These types remain valid when they provide a real boundary or independent
semantic value.

Example:

```text
bad default:
monster.snapshot() → distance/AI check

preferred:
Monster runtime → direct distance/AI check
```

First remove unnecessary abstraction/allocation; then benchmark.

---

# 16. Persistence boundary and durability model

Gameplay must not directly depend on JDBC implementation details.

SQL stays under persistence ownership.

Runtime model must not implement:

```text
save()
load()
deleteFromDatabase()
```

Current/target mental model:

```text
Zone / online gameplay runtime
→ current realtime authority

MySQL
→ durable checkpoint
```

Normal realtime gameplay should mutate RAM and coalesce persistence work rather
than write DB for every move/hit/effect/EXP mutation.

Preferred direction:

```text
runtime mutation
→ mark dirty / advance revision
→ capture stable save state when required
→ bounded persistence submission
→ blocking JDBC outside Zone
→ DB
```

A Snapshot/SaveData type is justified only when a real boundary needs a stable
copy. It is not mandatory for every mutable entity.

Persistence execution must never race by reading a mutable online Player while
Zone execution is changing that same object.

Durability-critical external operations such as:

```text
payment/web rewards
premium economy transactions
market/cross-server operations
```

may use their own durable transaction path and then hand the committed result
into the Zone.

---

# 17. PlayerProfile current frozen contract

Current `PlayerProfile` immutability participates in concurrency/checkpoint
safety.

Do not replace it with a mutable Player as part of unrelated cleanup.

The approved long-term direction may use a mutable Player runtime, but only
after Zone single-writer ownership is established in a dedicated migration
phase.

Changing Player runtime requires a dedicated design covering:

```text
runtime authority
Zone mutation ownership
checkpoint/save-data handoff while Player remains online
disconnect ordering
same-account relogin races
save consistency
persistence mapping
packet behavior
```

A separate immutable Player snapshot is not automatically required for final
disconnect if Player has already been detached and can no longer mutate.

Until the dedicated phase lands, current `PlayerProfile` behavior remains
authoritative.

Review Player separately before introducing shared Entity inheritance.

---

# 18. Session / account / disconnect invariants

Sensitive areas:

```text
Session.close
same-account admission
account reservation
Zone mutation
combat/lifecycle ordering
disconnect final checkpoint
```

Preserve current disconnect ordering until an approved migration phase replaces
it.

Correctness requirement:

```text
final save observes the latest authoritative gameplay state
same-account admission is not released too early
```

Future mutable-Player direction may be:

```text
Zone processes prior queued gameplay
→ detach Player from realtime mutation
→ hand off final stable state
→ final checkpoint attempt
→ release same-account reservation
```

Do not:

```text
release reservation before checkpoint attempt
make final save fire-and-forget when account ordering requires completion
hold inappropriate Session/Zone locks across JDBC
let DB/persistence threads mutate Player runtime directly
```

---

# 19. Monster ownership direction

Zone owns Monster collection/execution/world context.

Monster should own behavior that naturally describes Monster:

```text
target/aggro
move
attack
injure
death
respawn state
Monster AI
```

Preserve existing lifecycle ordering during migration unless a separate
behavior change is explicitly approved.

---

# 20. Effect runtime direction

Effect is runtime gameplay state owned by the affected entity.

Possible owners:

```text
Player
Monster
Boss
```

Sources may include:

```text
Skill
item
Boss mechanic
other gameplay system
```

Examples:

```text
stun
bind
petrify
poison
burn
blind
buff
debuff
```

Server owns gameplay truth.

Client packets own visual/icon/time representation.

Do not create one scheduler/thread per Effect.

---

# 21. Network backpressure

Session outbound buffering must be bounded.

A slow client must not:

```text
grow memory without bound
block Zone
```

Possible future optimization for stale/non-critical state such as movement is
coalescing, but only after measurement.

---

# 22. Tick model

Avoid systematic drift from:

```text
update()
sleep(period)
```

Prefer monotonic deadlines:

```text
nextTick += period
update()
park until nextTick
```

with explicit late-tick/catch-up policy.

Exact tick periods remain a benchmark/design decision.

---

# 23. Touched-slice compliance

When an architecture/refactor phase takes ownership of a feature slice, the
production code in that slice must leave the phase compliant with this rule.

Do not knowingly preserve obsolete intermediate architecture merely because it
existed before the phase.

For each touched top-level production type, re-evaluate whether it still has a
real responsibility.

Remove, merge, nest, rename, or move ownership when an old:

```text
Manager
Service
Factory
Registry
Scheduler
Snapshot
Result
DTO
wrapper
```

no longer has independent value after migration.

This requirement is scoped to the touched feature slice. It does not authorize
unrelated repo-wide cleanup.

---

# 24. Tests must not distort production design

Do not keep/add production API, wrapper types, managers, factories, or public
methods only because tests find them convenient.

Tests should validate production architecture, not force fragmentation.

---

# 25. Behavior-preservation rule

Unless explicitly approved, preserve:

```text
Unity packet bytes/order/width
Session state/order
current Zone synchronization until its dedicated migration phase
monster movement timing
monster attack timing
monster respawn timing
combat formulas
player persistence/checkpoint behavior
database schema/data
TLS/network behavior
```

The approved target architecture does not authorize an early cutover.

Behavior changes require separate explicit scope and regression tests.

---

# 26. Runtime migration discipline

Required order:

```text
pin current behavior with focused regression tests
→ establish Zone execution/lifecycle infrastructure
→ migrate one mutation slice at a time into Zone ownership
→ remove old synchronization only after that slice is owned by Zone
→ clean the touched slice to current readability rules
→ run focused tests
→ run full Maven gate
```

Do not combine without explicit approval:

```text
Zone concurrency cutover
+ mutable Player redesign
+ Monster AI redesign
+ Effect implementation
+ persistence rewrite
```

into one large change.

Until a slice is explicitly migrated:

```text
current production synchronization
current PlayerProfile contract
current packet behavior
current persistence ordering
current gameplay timing/order
```

remain authoritative.

---

# 27. Required tests and gates

Normal full server gate:

```powershell
cd server
.\mvnw.cmd test
```

Additionally:

```text
persistence/schema change
→ focused persistence/migration tests
→ real MySQL gate

protocol change
→ focused/golden packet tests
→ compatibility checks

Session/Zone/concurrency change
→ focused ordering/race regression tests

TLS/network transport change
→ relevant runtime/TLS gates
```

Report truthfully what was and was not executed.

---

# 28. Coding-model mandatory questions

Before adding/refactoring gameplay/runtime code, answer:

```text
1. What is the feature center?
2. Which file should a developer open first?
3. Which exact legacy files/classes were inspected?
4. What exact legacy vocabulary and normal flow are useful here?
5. Which legacy names can be reused because the semantics are still the same?
6. If a clear legacy name is being changed, why is it no longer accurate?
7. What readability strengths should be adopted?
8. What obsolete legacy choices must be rejected?
9. What state truly belongs to the entity?
10. Is shared Entity/base state genuinely shared?
11. Who owns collection/lifecycle?
12. Is Manager really an authority?
13. Is Service really a cross-owner use case?
14. Am I keeping Factory/Registry/Scheduler only to shorten another file?
15. How many files are needed to understand normal gameplay?
16. Can a larger cohesive file remove unnecessary jumps?
17. Am I using Optional because it helps, or from habit?
18. Am I creating a record/result/snapshot because a real boundary needs it?
19. Does the class/method list read like simple game actions?
20. Are names short because class context already supplies the missing meaning?
21. Did I replace a simple game verb with technical architecture vocabulary?
22. Does each method name describe what it actually does?
23. Am I duplicating Template/static data into runtime state?
24. Are persistence/network/resource boundaries still intact?
25. Are protocol and concurrency invariants preserved?
26. Which execution context owns each runtime mutation?
27. Did Session/network/persistence gain direct mutation access to Zone state?
28. Did any blocking I/O enter Zone gameplay execution?
29. Is Snapshot/Optional/Result/temp allocation in a hot path justified?
30. Did the touched slice retain obsolete architecture only because it existed before?
```

If the change makes the same gameplay idea require:

```text
more terminology
longer technical names
more wrapper/result types
more file jumps
```

without a real correctness, ownership, subsystem, or technical boundary,
reconsider the design.

---

# 29. Coding-model implementation report

Before implementation:

```text
Current feature center:
Legacy files/classes inspected:
Legacy vocabulary/flow inspected:
Legacy names reused:
Legacy names changed + reason:
Adopt from legacy:
Reject from legacy:
Target owner/flow:
Files expected to change:
Behavior/contracts preserved:
Focused tests:
```

For a feature with no useful legacy equivalent, write:

```text
Legacy equivalent:
- none found / not applicable

Naming approach:
- follows the same short/direct game-vocabulary style
```

Do not omit the legacy vocabulary section merely because architecture differs.

After implementation:

```text
Final feature center:
Normal gameplay flow:
Key class/method names:
Names simplified during implementation/review:
Files removed/merged/nested/renamed:
Focused tests run:
Full Maven gate:
Manual/DB/Unity/TLS gates not run:
Known remaining debt:
```

---

# 30. Final acceptance test

A reviewer must be able to answer quickly:

```text
Where do I start reading this feature?
What is the main runtime object?
Which legacy files/classes and vocabulary were inspected?
Do the touched class/method names read like simple game actions?
Which touched names differ from the legacy equivalent, and why?
Can I understand the normal flow without decoding architecture terminology?
Who owns each runtime mutation?
What is static definition data?
What common state comes from a real base entity, if any?
Who owns collection/lifecycle?
What happens during run/update?
Where does target selection happen?
Where does state change?
Where does combat orchestration happen?
Where does persistence happen?
Where is packet encoding?
Did any blocking I/O enter Zone execution?
How many files did I need to open for the normal flow?
Which test proves the important behavior?
```

Desired result:

```text
easy to find
easy to read top-to-bottom
easy to follow as game logic
short/direct names where semantics allow
few unnecessary jumps
technical boundaries remain safe
```

A change can be functionally correct and still fail review if the touched
gameplay code becomes harder to name, read, or follow without a correctness
reason.

When architecture purity and gameplay readability conflict without a correctness
reason:

> **Prefer the implementation that reads more directly as game logic.**
