# SERVER RULES

> **Status:** Sole authoritative architecture, coding, readability, and safety contract for `server/**`
>
> **Applies to:** server implementation, refactor, review, testing, shared Unity/server contracts, persistence, protocol, concurrency, gameplay structure, naming, and readability.
>
> **Reference source:** sibling legacy project `../rongthanchibi` may be used as a readability/gameplay-flow reference only.
>
> This file replaces:
>
> - `docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md`
> - `docs/architecture/SERVER_FEATURE_READABILITY_RULES.md`
>
> After adoption, the two old rule files must no longer be treated as active contracts.

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

Older plans and legacy code are historical/reference material.

They do not override this rule.

If a requested change conflicts with a current production invariant, report the conflict before changing production code.

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

For gameplay feature work, also follow the legacy-reference gate in section 6.

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

# 5. Feature-first organization

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

Do not require understanding generic technical layers such as:

```text
domain/
application/
adapter/
gateway/
usecase/
```

just to locate gameplay code.

Do not create empty future packages.

Create a feature package only when the feature exists.

---

# 6. Legacy source readability-reference gate

The sibling legacy project:

```text
../rongthanchibi
```

is an approved reference for gameplay readability.

Before planning or changing an existing gameplay feature, the coding model MUST inspect the equivalent legacy implementation when one exists.

Examples:

```text
Monster
→ legacy Monster
→ legacy Entity/base class
→ relevant legacy Zone/update flow

Player
→ legacy Player
→ legacy Entity/base classes

Boss
→ legacy Boss / Monster / Player relationship as applicable

Npc
→ legacy Npc

Item
→ legacy Item / manager / ownership flow

Skill
→ legacy Skill / combat flow
```

The purpose is to study:

```text
feature center
inheritance/base-state choices
field vs behavior balance
method vocabulary
update/lifecycle flow
Manager usage
package/file organization
how much gameplay remains locally readable
```

Before coding, the model MUST report briefly:

```text
Legacy reference inspected:
- exact relevant files/classes

Adopt:
- readability strengths worth carrying forward

Reject:
- legacy design choices that conflict with current architecture
```

The legacy source is NOT authoritative for:

```text
Spring/JPA architecture
global mutable singletons
direct DB access from gameplay
packet construction inside gameplay entities
giant switch/controller ownership
thread-per-zone/thread-per-session design
unsafe concurrency
obsolete gameplay data
obsolete protocol behavior
old persistence behavior
```

Do not mechanically copy legacy code.

Take the readability strengths, not the obsolete coupling.

---

# 7. Gameplay feature center

Every meaningful gameplay feature must have an obvious place to start reading.

Examples:

```text
monster → Monster
player  → Player / current approved Player runtime model
npc     → Npc
boss    → Boss or Monster-derived owner when appropriate
item    → Item
skill   → Skill
map     → Map / Zone depending on behavior
```

A developer asking:

> Where do I start reading this feature?

must have a clear answer.

The main feature object should contain behavior that naturally belongs to that object.

Do not scatter one entity's normal lifecycle across many peer classes merely to reduce file size.

---

# 8. Preferred gameplay shape

For an entity/static-definition feature, a useful default mental model is:

```text
Feature
FeatureManager      // only if real collection/lifecycle authority exists
FeatureTemplate     // only if static definition exists
supporting types    // only when they have real identity/use
```

Example:

```text
monster/
├── Monster.java
├── MonsterManager.java       // only if it truly manages the feature
├── MonsterTemplate.java
├── MonsterSpawn.java         // if independently useful
└── MonsterDart.java          // if independently useful
```

This is not a quota.

Do NOT create:

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

just to make the feature look architecturally complete.

Every top-level type must justify the navigation cost it introduces.

---

# 9. Gameplay flow before architectural purity

When choosing between:

```text
theoretically cleaner decomposition across many small classes
```

and:

```text
a larger cohesive gameplay file readable straight through
```

prefer the cohesive gameplay file unless the split creates a real ownership, subsystem, persistence, protocol, or infrastructure boundary.

A refactor is suspect when it:

```text
reduces lines per file
but
increases the number of files required to understand one normal gameplay flow
```

Navigation cost is an architecture concern.

---

# 10. Large files are allowed

There is no line-count threshold requiring gameplay code to split.

Potentially acceptable:

```text
300 lines
800 lines
1,500 lines
2,000+ lines
```

A large class is not automatically a God class.

A God class is defined by ownership sprawl.

A 1,500-line `Monster` may be acceptable when the file mostly describes Monster behavior.

A 300-line class may be bad when it owns:

```text
database
network
shop
combat
events
scheduling
unrelated global state
```

Review responsibility, not line count.

---

# 11. Runtime Entity/base-class rule

A runtime base class such as:

```text
Entity
CombatEntity
```

is allowed when multiple runtime entities genuinely share state or behavior.

Good candidates may include, when proven common:

```text
runtime id
position
HP / max HP
common alive/dead state
common combat state
common zone/world association
common timers
```

Do NOT introduce inheritance merely to make a subclass shorter.

Before creating or expanding a base class, verify the state/behavior is genuinely shared by multiple real runtime owners such as:

```text
Player
Monster
Boss
Pet/Disciple
```

Feature-specific state stays with the feature.

For Monster, examples that normally remain Monster-owned:

```text
MonsterTemplate
spawn position
enemy/threat tracking
respawn timing
movement direction
Monster AI
Monster-specific attack timing
```

Do not make gameplay runtime classes extend persistence/JDBC row types.

Persistence data and runtime objects remain separate concepts.

---

# 12. Data / Template / runtime distinction

Keep these concepts distinct when applicable:

```text
Persistence row
→ raw durable DB representation

Template
→ canonical static game definition

Runtime object
→ live mutable/authoritative gameplay state
```

Example:

```text
DB row / JSON
→ repository / loader
→ MonsterTemplate
→ Monster
```

Do not copy immutable Template fields into runtime fields without a runtime reason.

Prefer an explicit relationship:

```java
private final MonsterTemplate template;
```

when static values remain canonical in the template.

Do not make gameplay code understand SQL row details.

---

# 13. Entity files should primarily show gameplay

When opening a gameplay entity, the developer should mainly see:

```text
update
move
attack
findTarget
injure
die
respawn
equip
useItem
join
leave
```

not mostly:

```text
DTO conversion
Optional plumbing
snapshot conversion
builder ceremony
framework abstractions
wrapper/result forwarding
```

Field declarations are fine when they represent real runtime state.

The goal is not minimum fields.

The goal is that behavior dominates the mental model.

---

# 14. Gameplay lifecycle readability

For entities with a lifecycle, prefer an obvious entry point when appropriate:

```java
update(...)
```

and readable substeps:

```java
updateMove(...)
updateAttack(...)
updateEffect(...)
updateRespawn(...)
```

A normal flow should be understandable by reading method names.

Prefer:

```text
update
→ updateMove
→ findTarget
→ updateAttack
→ respawn
```

over chains such as:

```text
tickLifecycle
→ attackDueMonsters
→ beginAttackAttemptIfDue
→ createAttackResult
→ processAttackTransition
```

Multiple layers are valid when every layer adds real meaning.

They are not valid when each layer merely renames the same action.

---

# 15. Method naming

Use direct game vocabulary.

Good:

```text
update
move
moveTo
patrol
findTarget
attack
injure
takeDamage
die
respawn
addEnemy
removeEnemy
join
leave
equip
unequip
useItem
```

Do not shorten a method until its semantics become misleading.

Example:

A method named:

```text
beginAttack()
```

must actually represent beginning an attack.

If it only checks cooldown and marks a timestamp, choose a name that reflects that behavior or restructure the flow so the method truly owns the attack step.

The problem is not long names.

The problem is names that hide what the code really does.

---

# 16. Optional usage rule

`Optional` is allowed, but it is NOT the default gameplay control-flow mechanism.

Use `Optional` when it materially clarifies an API boundary.

Avoid repeatedly using:

```text
Optional<Result>
isEmpty()
orElseThrow()
flatMap(Optional::stream)
ifPresent(...)
```

for ordinary realtime game-loop branches where "nothing happened this tick" is normal.

Do not introduce `Optional` merely to avoid every internal `null`.

Within a tightly controlled internal gameplay boundary, a direct return plus an explicit no-result convention may be clearer.

Choose the form that makes the gameplay flow easiest to read and hardest to misuse.

Never return ambiguous `null` across broad/public boundaries where the contract is unclear.

---

# 17. Record usage rule

`record` is allowed, but it is not a default for every small value.

Good record candidates:

```text
persistence rows
immutable configuration
static definitions where value semantics fit
protocol/boundary data
small immutable values with independent meaning
```

Do NOT create records automatically for:

```text
every local gameplay result
every state transition
every snapshot
every intermediate action
```

If a small type only belongs to one owner, prefer nesting it:

```text
Monster.Damage
Monster.Move
Monster.Respawn
MonsterDart.Phase
Boss.Phase
```

If even the nested type adds more ceremony than value, keep the operation direct.

Do not turn normal game flow into:

```text
entity
→ Optional<ResultRecord>
→ SnapshotRecord
→ EventRecord
→ PacketWriter
```

without a real boundary requiring those types.

---

# 18. Snapshot / Result / Event types

A top-level `Snapshot`, `Result`, or `Event` must have independent value.

Use one when it:

```text
crosses a real boundary
is consumed by multiple owners
needs stable immutable representation
has independent domain meaning
```

Do not create one merely to:

```text
avoid returning null
avoid exposing one getter
make a method look pure
reduce lines in the owning class
```

Local result/state types should normally be nested under their owner.

Do not create generic containers such as:

```text
MonsterModels.java
MonsterDtos.java
MonsterResults.java
GameTypes.java
CommonModels.java
```

to hide fragmentation.

---

# 19. Manager vs Service

Use `Manager` when the class genuinely owns or coordinates meaningful feature authority such as:

```text
collection
catalog
registration
create/init
find
feature lifecycle/update
```

Use `Service` for a real cross-owner/use-case operation.

Examples:

```text
CombatService
ShopService
AuthService
```

Do not rename a Service to Manager without changing or confirming its ownership.

A class named `MonsterManager` should make it obvious what Monster authority it actually owns.

Do not create peer:

```text
Factory + Service + Manager + Registry + Scheduler
```

unless each has independently justified responsibility.

---

# 20. Factory rule

A Factory is justified when object creation has meaningful composition/policy.

Do not keep/create a Factory only because construction was extracted from another file.

If feature management naturally owns initialization/creation and combining them improves readability, the Manager may own creation.

Do not merge blindly if doing so would create cyclic dependencies or violate resource/persistence boundaries.

---

# 21. Scheduler and lifecycle ownership

Technical scheduling must not become the conceptual owner of gameplay.

Classes such as:

```text
NetworkServer
Transport
Codec
Repository
PacketWriter
```

must not own Monster/Player/Boss gameplay logic.

A technical scheduler may trigger a gameplay update.

The gameplay feature must still have an obvious lifecycle owner.

Do not add new feature-specific lifecycle logic to `NetworkServer`.

`NetworkServer` should trend toward:

```text
listen
accept
create Session
start/stop networking
```

Application/gameplay wiring belongs in bootstrap/runtime ownership.

---

# 22. Zone ownership and synchronization

Concurrency correctness wins over file-shape preferences.

`Zone` may remain a synchronization/authority boundary.

Do not move code merely to make a prettier class tree if that weakens:

```text
Zone synchronization
combat ordering
membership atomicity
runtime authority
```

However, synchronization ownership does not mean all feature logic must live in `Zone`.

Prefer:

```text
Zone
→ owns membership/world authority/synchronization

Entity
→ owns its own state transitions and gameplay behavior

Manager / cross-owner service
→ resolves surrounding entities when truly needed
```

Do not let `Zone` become the hidden implementation file for Monster AI simply because Monsters live inside a Zone.

---

# 23. Handler boundary

Handlers are protocol adapters.

Handlers may:

```text
parse packet
validate packet shape/trailing bytes
validate protocol/session state
call gameplay
send response
perform small request orchestration
```

Handlers should not own reusable gameplay rules.

If an action could also be triggered by:

```text
NPC
GM command
bot
server event
test
```

its reusable rule should normally live outside the Handler.

Do not create one Handler per command.

Group handlers by real feature.

---

# 24. Packet boundary

Gameplay decides:

> what happened?

Packet writers decide:

> how is that serialized to the Unity protocol?

Packet encoding belongs under:

```text
network/packet/
```

Do not put binary writer chains inside gameplay entities/managers.

Do not move packet construction into `Monster`, `Player`, `Npc`, or `Boss` merely because the legacy project did so.

Keep golden/focused tests for important packets.

---

# 25. Persistence boundary

Gameplay must not directly depend on JDBC implementation details such as:

```text
Connection
PreparedStatement
ResultSet
HikariDataSource
Jdbc*Repository
```

SQL stays under persistence ownership.

Runtime model must not implement:

```text
save()
load()
deleteFromDatabase()
```

Current mental model:

```text
Session / Zone / gameplay runtime
→ realtime authority

MySQL
→ durable state/checkpoint
```

Do not write DB on movement/combat hot paths without an approved design.

Do not make runtime entities inherit persistence `*Data` classes.

---

# 26. Migration and catalog safety

Schema evolution uses the current in-house versioned SQL migration contract.

Do not edit an already shipped migration to represent a future schema change.

Use a new:

```text
VNNN__name.sql
```

and update:

```text
schema reference snapshot
JDBC mappings
focused tests
real MySQL gate
```

Migration history/checksum, advisory locking, startup ordering, and failure behavior are correctness contracts.

Catalog seed behavior is also a correctness contract.

Do not refactor migration/seed semantics as part of gameplay readability cleanup.

---

# 27. PlayerProfile current frozen contract

Current `PlayerProfile` immutability participates in concurrency/checkpoint safety.

Do not replace it with a mutable giant Player merely as part of a readability cleanup.

Changing the Player runtime model requires a dedicated design covering:

```text
locking
runtime authority
immutable checkpoint snapshots
disconnect ordering
same-account relogin races
save consistency
```

The fact that legacy Player is easier to scan in some areas is not sufficient by itself to change this concurrency contract.

Review Player separately before introducing shared Entity inheritance.

---

# 28. Session / account / disconnect invariants

Concurrency is correctness, not style.

Sensitive areas include:

```text
Session.close
same-account admission
account reservation
Zone mutation
combat/lifecycle ordering
disconnect final checkpoint
```

Preserve the current disconnect ordering unless an approved change explicitly redesigns it:

```text
mark CLOSED
→ remove realtime membership
→ capture checkpoint snapshot
→ final checkpoint attempt
→ release same-account reservation
→ remove session
```

Do not:

```text
release reservation before checkpoint attempt
make final save fire-and-forget
hold inappropriate Session locks across JDBC
rewrite synchronization just to reduce code
```

---

# 29. Resource rules

Static definitions and runtime objects are separate.

`GameResources` is a loaded catalog/read API.

Loaders own parse + validation.

Do not recreate one giant `ResourceService`.

Add feature loaders only when their data exists.

Do not create generic validation frameworks merely because multiple loaders validate data.

---

# 30. Bootstrap rules

Bootstrap owns application composition:

```text
config
database manager
migrations
repositories
resources
gameplay services/managers
packet writers
NetworkServer
```

Do not grow `NetworkServer` into application wiring.

A dependency bundle such as `SessionServices` may exist at a coarse boundary.

Do not turn it into a global service locator passed deep through gameplay.

---

# 31. Package and subfolder rules

Create subfolders only for real subfeatures with their own vocabulary/behavior.

Reasonable examples:

```text
monster/big/
monster/event/
monster/pet/
player/action/
map/expansion/
```

Do not create inside every feature:

```text
model/
service/
manager/
factory/
dto/
result/
```

merely to categorize technical shapes.

Feature navigation matters more than taxonomy.

---

# 32. Small supporting types

A small top-level type is fine when it has independent identity and is useful to discover directly.

Prefer a nested type when it:

```text
only belongs to one owner
describes one owner's local result/state/phase
does not need independent discovery
has no separate lifecycle/persistence/wire contract
```

Do not optimize for:

```text
minimum file count
or
one-type-per-file purity
```

Optimize for navigation.

---

# 33. Tests must not distort production design

Do not keep/add production:

```text
getters
factories
services
public methods
result types
```

only because tests find them convenient.

Use test support where appropriate.

Production architecture should reflect the domain/runtime design.

Tests should validate that design, not force fragmentation.

Do not create a test framework/DSL without a demonstrated need.

---

# 34. Behavior-preservation rule

A readability/architecture refactor must not silently change gameplay.

Unless explicitly approved, preserve:

```text
Unity packet bytes/order/width
Session state/order
Zone synchronization
monster movement timing
monster attack timing
monster respawn timing
combat formulas
player persistence/checkpoint behavior
database schema/data
TLS/network behavior
```

Prefer:

```text
move
rename
nest
reorder
ownership cleanup
```

before changing algorithms.

Behavior changes require separate explicit scope and regression tests.

---

# 35. Refactor discipline

Each refactor must identify:

```text
exact baseline
scope
feature center
current owners
target owners
behavior to preserve
protocol impact
persistence impact
concurrency impact
focused tests
full test gate
manual/runtime gates when relevant
```

Do not combine unrelated changes such as:

```text
large rename + new gameplay feature
package move + concurrency rewrite
readability cleanup + schema migration
type cleanup + protocol redesign
```

If a bug is discovered outside scope:

```text
report it
pin it with a regression test
fix it separately
```

---

# 36. Required tests and gates

Normal full server gate:

```powershell
cd server
.\mvnw.cmd test
```

Use equivalent Maven only when necessary.

Additionally:

```text
persistence/schema change
→ focused persistence/migration tests
→ real MySQL gate from server/README.md

protocol change
→ focused/golden packet tests
→ compatibility checks

Session/Zone/concurrency change
→ focused ordering/race regression tests

TLS/network transport change
→ relevant runtime/TLS gates
```

Report truthfully what was and was not executed.

Static review is not runtime proof.

---

# 37. Coding-model mandatory gameplay questions

Before adding/refactoring gameplay code, answer:

```text
1. What is the feature center?
2. Which file should a developer open first?
3. What does the legacy counterpart look like?
4. What readability strengths should be adopted?
5. What obsolete legacy choices must be rejected?
6. What state truly belongs to the entity?
7. Is shared Entity/base state genuinely shared by multiple runtime types?
8. Who owns collection/lifecycle?
9. Is Manager really an authority?
10. Is Service really a cross-owner use case?
11. Am I keeping Factory/Registry/Scheduler only to make another file shorter?
12. How many files are needed to understand the normal gameplay flow?
13. Can a larger cohesive file remove unnecessary jumps?
14. Am I using Optional because it helps, or from habit?
15. Am I creating a record/result/snapshot because a real boundary needs it?
16. Does the method list read like game actions?
17. Does the method name describe what it actually does?
18. Am I duplicating Template/static data into runtime state?
19. Are persistence/network/resource boundaries still intact?
20. Are protocol and concurrency invariants preserved?
```

If the change makes normal gameplay require more file jumps without creating a real boundary:

```text
reconsider the design.
```

---

# 38. Coding-model implementation report

Before implementation, for gameplay refactors, report briefly:

```text
Current feature center:
Legacy files inspected:
Adopt from legacy:
Reject from legacy:
Target owner/flow:
Files expected to change:
Behavior/contracts preserved:
Focused tests:
```

After implementation, report:

```text
Final feature center:
Normal gameplay flow:
Files removed/merged/nested/renamed:
Focused tests run:
Full Maven gate:
Manual/DB/Unity/TLS gates not run:
Known remaining debt:
```

This is intended to make architectural drift visible during review.

---

# 39. MUST / MUST NOT summary

MUST:

```text
read current implementation before changing it
read this rule before server work
inspect legacy equivalent for gameplay work when available
prefer direct game vocabulary
keep gameplay flow locally readable
keep technical boundaries explicit
allow large cohesive gameplay classes
preserve current runtime/protocol/persistence/concurrency contracts
justify every new top-level helper type
run focused tests and full required gates
report gates truthfully
```

MUST NOT:

```text
split because of line count alone
default every feature to Service
rename Service→Manager without verifying authority
create Factory+Manager+Registry+Scheduler by symmetry
use Optional/record automatically
create Snapshot/Result/Event without a real boundary
introduce Entity inheritance only to reduce field count
inherit gameplay models from persistence rows
move packet encoding into gameplay
move SQL/JDBC into gameplay
copy legacy singleton/JPA/network coupling
put feature lifecycle logic into NetworkServer
create generic Context/Facade/EventBus/Coordinator to hide readable direct calls
add public production API only for tests
opportunistically refactor unrelated code
claim runtime success from static inspection
```

---

# 40. Final acceptance test

A reviewer must be able to answer quickly:

```text
Where do I start reading this feature?
What is the main runtime object?
What is static definition data?
What common state comes from a real base entity, if any?
Who owns collection/lifecycle?
What happens during update?
Where does target selection happen?
Where does state change?
Where does combat orchestration happen?
Where does persistence happen?
Where is packet encoding?
How many files did I need to open for the normal gameplay flow?
Which test proves the important behavior?
```

Desired result:

```text
easy to find
easy to read top-to-bottom
easy to follow as game logic
few unnecessary jumps
technical boundaries remain safe
```

When architecture purity and gameplay readability conflict without a correctness reason:

> **Prefer the implementation that reads more directly as game logic.**
