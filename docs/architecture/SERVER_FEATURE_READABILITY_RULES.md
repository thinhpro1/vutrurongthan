# SERVER FEATURE READABILITY & GAMEPLAY CODE STYLE RULES

> **Status:** Authoritative companion rule for `server/**`
>
> **Applies to:** gameplay feature structure, naming, file layout, method ordering,
> entity/runtime ownership, Manager/Service decisions, and readability.
>
> **Works together with:** `docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md`
>
> **Audited baseline:** `10197ca43cc5bb294d00720b49aaf78fa56bb2b2`
>
> This file does **not** weaken the existing protocol, persistence, concurrency,
> migration, testing, or Unity compatibility rules.

---

# 1. Why this rule exists

The server must be safe and testable, but it must also read like game code.

A developer opening a gameplay package should quickly understand:

```text
What is the main object?
What data defines it?
Who manages its collection/lifecycle?
What does it do each update?
Where does its state change?
```

The project must not become a collection of technically precise but
fragmented classes where one gameplay flow requires opening many files.

Reference source trees may inspire naming and readability, but they do not
override current protocol/database/concurrency contracts.

---

# 2. Priority when rules interact

Use this order:

```text
1. Current runtime / Unity protocol / database / concurrency invariants
2. SERVER_ARCHITECTURE_RULES_UPDATED.md for safety boundaries
3. This file for feature structure, naming, gameplay readability and code layout
4. Newly approved implementation plan
5. Older plans/reference projects
```

For the topics this file explicitly owns, it supersedes older examples that
encourage Service/Factory/Registry fragmentation merely because those names are
technically precise.

It does not permit gameplay code to absorb JDBC, packet encoding, socket code,
or other technical boundaries.

---

# 3. Core principle — every gameplay feature needs a center

Every meaningful gameplay feature must have an obvious **feature center**.

Examples:

```text
monster -> Monster
player  -> Player
npc     -> Npc
item    -> Item
skill   -> Skill
team    -> Team
map     -> Map / Zone, depending on the behavior
```

A developer asking:

> "Where do I start reading this feature?"

must have a clear answer.

The main feature object should contain the behavior that naturally belongs to
that object.

Do not scatter one entity's basic lifecycle across many peer classes merely to
reduce class size.

---

# 4. Preferred feature shape — a convention, not a quota

For entity/static-data features, the preferred mental model is:

```text
Feature.java
FeatureManager.java
FeatureTemplate.java
supporting types...
```

Meaning:

```text
Feature
    runtime object + feature-owned behavior

FeatureManager
    collection/catalog/runtime authority
    init/create/find/update/lifecycle orchestration when that responsibility exists

FeatureTemplate
    immutable/static definition shared by runtime objects
```

Examples:

```text
monster/
├── Monster.java
├── MonsterManager.java
├── MonsterTemplate.java
├── MonsterSpawn.java
└── MonsterDart.java
```

```text
item/
├── Item.java
├── ItemManager.java
├── ItemTemplate.java
├── ItemOption.java
└── ItemOptionTemplate.java
```

But this is **not** a requirement to create three files for every feature.

If a role does not exist, do not invent it.

Examples:

```text
team/
├── Team.java
├── TeamManager.java
├── TeamMember.java
└── TeamStatus.java
```

No `TeamTemplate` unless the game actually has a static team definition.

```text
upgrade/
├── Upgrade.java
├── UpgradeManager.java
├── UpgradeItem.java
├── UpgradeStone.java
└── ...
```

An operation-family feature does not need a fake `UpgradeTemplate`.

---

# 5. Feature center before technical helper

A gameplay package may contain technical/supporting classes, but they must not
hide the primary mental model.

Bad navigation:

```text
MonsterFactory
MonsterService
MonsterLifecycleScheduler
ZoneRegistry
MonsterSnapshot
Monster
```

with no clear answer to "where is monster behavior?"

Acceptable:

```text
Monster              -> entity behavior
MonsterManager       -> feature-level authority
MonsterTemplate      -> definition

MonsterSpawn         -> supporting static placement
MonsterDart          -> supporting visual/combat definition
MonsterSnapshot      -> cross-boundary read contract only if independently useful
```

Supporting classes are allowed when they solve a real problem.

The rule is not "few files at all costs."

The rule is:

> **A supporting class must make the feature easier to understand, not merely
> make one file shorter.**

---

# 6. File length is NOT a split criterion

There is no preferred maximum line count for gameplay entities.

These are all potentially acceptable if cohesive:

```text
300 lines
800 lines
1,500 lines
2,000+ lines
```

A game entity often accumulates real behavior as the project grows.

Do NOT split because:

```text
the file passed 300/500/1000 lines
a linter/design article says the class is too long
the method list looks large
a smaller-file metric would look nicer
```

Prefer:

> **one large cohesive entity**

over:

> **many small classes that force the developer to jump through files to
> understand one lifecycle.**

Developer navigation cost is part of architecture quality.

A refactor that reduces lines per file but increases the number of files
required to understand one gameplay flow is a readability regression unless it
creates a real subsystem boundary.

---

# 7. When behavior SHOULD be split

Split behavior when the extracted part has its own meaningful ownership.

Strong reasons:

```text
1. It is a separate gameplay subsystem with its own vocabulary/lifecycle.
2. It is reused by multiple unrelated owners.
3. It has independent state/authority.
4. It is a technical boundary that must stay separate:
   persistence, network, packet serialization, resource parsing.
5. Keeping it in the entity makes the entity own something that is not actually
   about that entity.
6. A coherent block can be understood/tested as an independent feature.
```

Examples that often deserve separation:

```text
Shop transaction
Clan subsystem
Trade session
Quest engine
JDBC repository
PacketWriter
Protocol codec
Resource loader
```

Weak reasons that are NOT enough:

```text
"class is long"
"method is long"
"Factory pattern exists"
"Service pattern exists"
"we want every file under 200 lines"
```

---

# 8. Runtime entity behavior belongs near the entity

If a behavior describes what one object does or what happens to it, prefer the
runtime object.

Examples:

```text
Monster.update()
Monster.updateAttack()
Monster.findTarget()
Monster.injure() / Monster.takeDamage()
Monster.die()
Monster.respawn()
Monster.addEnemy()
Monster.isDead()

Player.move()
Player.addPotential()
Player.consumeMp()
Player.revive()

Item.use()
Item.getOption()
Item.isExpired()
Item.upgrade()
```

This does NOT mean the entity may own everything.

Keep these outside:

```text
JDBC / SQL
socket handling
packet binary serialization
global scheduling infrastructure
cross-feature transactions that have their own owner
```

A domain entity may return a result describing what happened; a PacketWriter may
serialize that result.

---

# 9. One primary gameplay orchestrator per feature

Avoid multiple peer orchestrators for the same feature unless their boundaries
are clearly different.

Be suspicious of packages containing combinations like:

```text
FeatureService
FeatureManager
FeatureFactory
FeatureCoordinator
FeatureRegistry
FeatureScheduler
```

all participating in the same normal flow.

Ask:

```text
Which one is the primary entry point?
Could one owner naturally absorb the other responsibility?
Is the extra class only present because of a Design Pattern?
```

A feature may still legitimately have supporting technical classes, but the
developer should not have to guess among four peers to understand it.

---

# 10. Manager is valid game vocabulary

`Manager` is not an anti-pattern by default.

Use `FeatureManager` when the class truly owns or coordinates one or more of:

```text
feature collection
catalog
init
create
find
registration
runtime lifecycle
feature-wide update
```

Good conceptual examples:

```text
MonsterManager
PlayerManager
NpcManager
ItemManager
SkillManager
TeamManager
MapManager
```

A Manager must still have a boundary.

A Manager must NOT become:

```text
global service locator
database gateway
packet encoder
home for unrelated subsystems
"everything about the game"
```

Do not reject `Manager` merely because `Service`, `Factory`, or `Registry` sounds
more architecturally precise.

Choose the name that a game developer can find fastest.

---

# 11. Service is for a real use-case/cross-owner action

Use `Service` when the class represents a meaningful operation that crosses
object/feature boundaries or a technical application boundary.

Examples that can be valid:

```text
AuthService
CombatService
ShopService
TradeService
```

Do NOT default every feature to:

```text
NpcService
ItemService
SkillService
MonsterService
```

when the behavior is more naturally understood as:

```text
Npc
NpcManager

Item
ItemManager

Skill
SkillManager

Monster
MonsterManager
```

A Service should earn its name by orchestrating a real use case, not by being
"the place where logic goes."

---

# 12. Factory / Registry / Scheduler are not default feature peers

Use these names only when the distinction is useful to the reader.

```text
Factory
    creation policy is substantial enough to deserve independent ownership

Registry
    lookup/registration is a clear standalone authority

Scheduler
    scheduling is genuinely reusable/independent infrastructure
```

Do not create them only because a method could technically be extracted.

If a scheduler only exists for one feature and is not independently meaningful,
prefer hiding it under that feature owner when lifecycle/order permits.

If creation is trivial, prefer a constructor/static creation method or Manager
instead of a top-level Factory.

---

# 13. Technical layers remain technical

The following are intentionally allowed to use technical names:

```text
Repository
JdbcRepository
Record
Handler
PacketWriter
Codec
Transport
Loader
Migrator
Seeder
Config
```

Do not rename these to `Manager` merely for consistency.

Their role is already obvious.

Gameplay readability rules must not collapse technical boundaries.

---

# 14. Runtime object should preserve domain relationships

Do not flatten related domain data into many copied primitive fields without a
reason.

If a runtime object is defined by a template, prefer making that relationship
visible:

```text
Monster -> MonsterTemplate
Item    -> ItemTemplate
Skill   -> SkillTemplate
Npc     -> NpcTemplate
```

instead of copying every template field into the runtime object.

Copy a template value into runtime state only when it becomes independently
mutable or has a distinct runtime meaning.

This makes it clearer whether a value is:

```text
definition data
or
runtime state
```

Avoid hidden duplication.

---

# 15. Data / Template / persistence distinction

When applicable, keep these concepts distinct:

```text
persistence row/record
    raw durable representation

Template
    canonical game definition

runtime object
    live mutable/authoritative state
```

Example mental flow:

```text
DB row / JSON
→ loader/repository
→ MonsterTemplate
→ Monster
```

Do not make gameplay code understand SQL row details.

Do not force persistence row types into the gameplay package merely for visual
symmetry.

---

# 16. Gameplay code must read top-to-bottom

A gameplay class should tell a story when scanned from top to bottom.

Preferred order:

```text
1. constants
2. dependencies / immutable references
3. runtime state
4. constructor
5. init/setup
6. main lifecycle entry points
7. primary gameplay actions in execution order
8. secondary actions
9. queries/getters
10. private helpers
11. nested enums/records/classes
```

Exact sections may vary, but related declarations must stay together.

Do NOT interleave:

```text
method
enum
method
record
method
field
helper
main behavior
```

merely because declarations were added over time.

Nested support types should normally be grouped at the bottom unless placing
one next to its use clearly improves readability.

---

# 17. Outline test — the method list should explain the feature

When reading only method names, the developer should understand the feature.

Good game vocabulary:

```text
init
update
updateMove
updateAttack
findTarget
attack
injure
takeDamage
die
respawn
addEnemy
join
leave
move
equip
unequip
useItem
```

Avoid needlessly implementation-oriented names when a direct game verb exists:

```text
beginAttackAttemptIfDue
tickLifecycle
performRuntimeMutation
processEntityTransition
```

Long names are allowed when they express a real distinction.

The problem is not character count.

The problem is making a simple game action sound like framework machinery.

---

# 18. Avoid chains of synonyms for one action

Do not make one simple flow read like:

```text
tickLifecycle
→ attackDueMonsters
→ beginAttackAttemptIfDue
```

when the conceptual flow is:

```text
update
→ updateAttack
```

Multiple layers are fine when each adds real meaning.

They are not fine when each layer merely renames the same operation.

---

# 19. Main update/lifecycle should be easy to locate

For entities with a lifecycle, prefer an obvious lifecycle entry point when the
architecture allows it:

```java
update(...)
```

and then readable substeps:

```java
updateMove(...)
updateAttack(...)
updateEffect(...)
updateRespawn(...)
```

Do not force an `update()` method where the object has no lifecycle.

Do not introduce context/framework objects merely to manufacture this shape.

The important requirement is that the lifecycle flow is discoverable and
ordered.

---

# 20. Environment-dependent behavior

Some entity behavior needs Zone/Player/Session/world context.

Do not solve this by dumping everything into the entity or everything into a
Service.

Use the simplest ownership that keeps the flow readable.

A useful split is:

```text
Entity
    own state transition/rules

Zone/Manager
    resolve surrounding objects and synchronized authority

PacketWriter
    encode resulting event
```

For example, `Monster` may own damage/death/respawn/movement state changes while
`Zone` resolves eligible players under its synchronization boundary.

Do not create a generic `Context`, `WorldFacade`, or event bus only to avoid a
direct readable call.

---

# 21. Technical infrastructure must not secretly own gameplay lifecycle

Technical classes such as:

```text
NetworkServer
Transport
Codec
Repository
PacketWriter
```

must not become the conceptual owner of gameplay loops.

If a technical class starts/stops a gameplay lifecycle for practical startup
reasons, the feature ownership must still be explicit and the arrangement
should be reviewed for eventual relocation to bootstrap/runtime ownership.

Do not add new feature-specific lifecycle logic to `NetworkServer`.

---

# 22. Handler and packet boundaries stay separate

Handlers remain protocol adapters.

Packet writers remain binary serialization owners.

Do not move packet construction into:

```text
Monster
Player
Item
Npc
Manager
```

just because the reference source did so.

The desired model is:

```text
gameplay decides what happened
→ packet layer serializes it
```

---

# 23. Large file vs God class

A large class is NOT automatically a God class.

A God class is defined by **ownership sprawl**, not line count.

A 1,500-line `Item` can be acceptable if most methods describe the item itself.

A 300-line class can be a God class if it owns:

```text
DB
network
shop
combat
events
scheduling
unrelated global state
```

Review by responsibility, not line count.

---

# 24. Subfolders are for real subfeatures

Create subfolders when a group has its own vocabulary/behavior.

Examples:

```text
monster/big/
monster/event/
monster/pet/

player/action/

map/expansion/
```

Do not create:

```text
model/
service/
manager/
factory/
dto/
result/
```

inside every feature merely to categorize technical shapes.

Feature-first navigation is more important.

---

# 25. Small supporting types

A small top-level type is acceptable when it has independent identity and is
useful to discover directly.

A nested type is preferable when it only describes an owner's local result or
state.

Do not optimize for either:

```text
minimum file count
or
one-type-per-file purity
```

Optimize for navigation.

---

# 26. Tests must not dictate ugly production architecture

Do not keep extra production factories/services/getters only because tests find
them convenient.

Use test support when appropriate.

Do not introduce public API solely to make a unit test easier.

Tests should validate the chosen domain structure, not force fragmentation.

---

# 27. Refactor behavior-preservation rule

A readability refactor must not silently change gameplay.

Unless an approved plan explicitly says otherwise, preserve:

```text
Unity packet bytes/order/width
Session state/order
Zone synchronization
monster movement/attack/respawn timing
combat formulas
persistence/checkpoint behavior
database schema/data
network/TLS behavior
```

Rename/move/reorder first.

Behavior changes require a separate explicit scope and tests.

---

# 28. Reference-source rule

The historical `src thamkhao2` / legacy source is approved as a **readability
reference**, especially for:

```text
feature naming
feature center
Manager convention
Template/runtime relationship
method vocabulary
top-to-bottom gameplay flow
```

It is NOT authoritative for:

```text
global singletons
direct repository access from managers
packet encoding inside entities
giant switch controllers
JPA/Spring architecture
unsafe concurrency
obsolete gameplay data/protocol behavior
```

Take the readability strengths, not the obsolete coupling.

---

# 29. Coding-model mandatory questions

Before adding or refactoring gameplay code, the model MUST answer internally:

```text
1. What is the feature center?
2. Which file should a developer open first?
3. Is this behavior truly owned by the entity?
4. Is a Manager the natural feature authority?
5. Is a Service actually a cross-owner use case?
6. Am I creating Factory/Registry/Scheduler only to make another file smaller?
7. How many files must a developer open to understand the normal flow?
8. Can a large cohesive file be easier than this extraction?
9. Are declarations ordered so the file reads top-to-bottom?
10. Do method names use game vocabulary?
11. Am I duplicating Template data into runtime state unnecessarily?
12. Does this change preserve network/persistence/concurrency boundaries?
```

If a refactor makes question 7 worse without creating a real subsystem
boundary, reconsider it.

---

# 30. Coding-model MUST / MUST NOT

```text
MUST identify the feature center before coding.
MUST prefer game vocabulary over architecture jargon.
MUST keep a gameplay flow locally readable.
MUST keep declarations grouped and ordered.
MUST allow large cohesive gameplay files.
MUST use Manager when it naturally owns collection/catalog/lifecycle.
MUST keep persistence/network/packet/resource parsing boundaries intact.
MUST preserve behavior outside approved scope.
MUST run focused + full gates required by the main architecture rule.

MUST NOT split because of line count alone.
MUST NOT default every feature to Service.
MUST NOT create peer Factory+Service+Manager+Registry+Scheduler without
         independently justified responsibilities.
MUST NOT move entity-owned behavior out merely to reduce file size.
MUST NOT put gameplay lifecycle into technical infrastructure.
MUST NOT add generic Context/Facade/EventBus/Coordinator to hide direct calls.
MUST NOT copy obsolete singleton/DB/network coupling from reference source.
```

---

# 31. Review acceptance questions

A gameplay refactor is not complete until a reviewer can answer:

```text
Where do I start reading this feature?
What is the runtime object?
What is the static definition?
Who manages the feature collection/lifecycle?
What happens during update?
Where does state change?
Where does DB access happen?
Where is packet encoding?
How many files did I have to open to understand the normal gameplay flow?
```

The desired result is:

```text
easy to find
easy to read top-to-bottom
easy to follow as game logic
few unnecessary jumps
technical boundaries still safe
```

---

# 32. Final principle

When choosing between:

```text
a theoretically cleaner decomposition across many small classes
```

and:

```text
a larger cohesive gameplay file that a developer can read straight through
```

prefer the larger cohesive file **unless the split creates a real ownership or
subsystem boundary**.

The project is a game server.

Its code should look and read like game logic.
