# AGENTS.md

## Mandatory project instructions

This repository contains a Unity client and a Java game server.

---

## Server architecture gate

For any task touching:

```text
server/**
server architecture
shared Unity/server protocol
database/server persistence
server concurrency/runtime behavior
```

before planning, reviewing, testing, or changing code:

1. **MUST read and follow**
   `docs/architecture/SERVER_RULES.md`.

2. `SERVER_RULES.md` is the **sole authoritative server architecture/code-style
   contract**.

3. **MUST inspect the current implementation before proposing or applying
   changes.**

4. If `SERVER_RULES.md` is missing or unreadable, **STOP before changing server
   code**.

5. If the requested implementation conflicts with current production runtime,
   protocol, persistence, or concurrency invariants, report the conflict before
   changing production code.

For tasks touching server runtime ownership, Map/Zone/Player/Monster
migration, or the active refactor roadmap, the coding model MUST also read:

```text
docs/architecture/SERVER_RUNTIME_ARCHITECTURE_BASELINE.md
```

Authority is separated as follows:

```text
SERVER_RULES.md
= architecture/rule authority

SERVER_RUNTIME_ARCHITECTURE_BASELINE.md
= approved migration direction / active roadmap
```

If they conflict, `SERVER_RULES.md` wins and implementation must STOP/report
the conflict.

---

## Legacy gameplay reference gate

The sibling legacy project:

```text
../rongthanchibi
```

is the approved **readability, naming, and gameplay-flow reference**, not an
authoritative runtime architecture.

This gate applies to **all existing gameplay/server feature domains**, not only
Monster or Player. Examples include, but are not limited to:

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

Before planning or coding an existing feature, the coding model **MUST inspect
the corresponding legacy implementation when available**.

If `../rongthanchibi` is available, skipping this inspection is a rule
violation. If the sibling project is unavailable or no equivalent feature can
be found, report that explicitly before coding. Do not invent legacy names or
claim the gate passed without inspection.

The goal is not only to learn what the feature does. The coding model MUST
actively study how the legacy source keeps the feature easy to read:

```text
feature center
class names
method names
gameplay vocabulary
normal call/update flow
field-vs-behavior balance
base Entity/state choices
Manager usage
package/file organization
number of file jumps needed for the normal flow
```

Before coding, report briefly:

```text
Legacy reference inspected:
- exact files/classes

Legacy vocabulary/flow inspected:
- exact useful names and flow, for example run / update / addNpc / addPoint / ...

Adopt:
- simple naming/readability strengths worth carrying forward

Reject:
- obsolete choices that conflict with current architecture
```

### Legacy naming rule

When the legacy source already has a short, clear gameplay name and the new code
means the same thing, **prefer the same simple vocabulary**.

Examples of the preferred style:

```text
run
update
init
load
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
addEffect
removeEffect
join
leave
```

These examples are a naming style, not a requirement to mechanically reuse a
wrong name.

Do not replace a clear game action with a longer technical phrase merely to
sound architectural.

Prefer:

```text
addNpc
addPoint
update
attack
move
```

over names such as:

```text
registerNpcRuntimeEntity
resolveWaypointRegistration
processRuntimeExecutionCycle
executeAttackTransition
processMovementOperation
```

unless the longer name describes genuinely different semantics.

If the new code changes a clear legacy name for the same feature concept, the
coding model MUST explain before coding:

```text
Legacy name:
New name:
Why the legacy name is no longer accurate:
```

Class context should remove redundant words. If the class already says `Zone`,
`Monster`, `Npc`, `Quest`, or `Shop`, method names should normally describe the
game action instead of repeating architecture terminology.

Technical words such as:

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

are not banned, but in gameplay code they require a real technical meaning or
boundary. Do not use them as decorative naming.

For a new feature with no useful legacy equivalent, use the **same naming
style**: short, direct game vocabulary and a top-to-bottom flow that can be read
without architecture knowledge.

Do NOT copy obsolete legacy choices such as:

```text
Spring/JPA coupling
global mutable singletons
direct DB access from gameplay
packet encoding inside gameplay entities
unsafe concurrency
obsolete data/protocol behavior
platform-thread-per-Zone as a scalability default
network/session threads mutating gameplay entities directly
cross-thread runtime mutation hidden behind locks
always-running idle Zone loops
```

The approved target may deliberately use virtual threads at infrastructure and
runtime ownership boundaries. Do not confuse:

```text
legacy thread ownership
```

with:

```text
1 ACTIVE Zone = 1 virtual thread = 1 writer
Session reader/writer virtual threads for blocking socket I/O
```

The target model is defined by `SERVER_RULES.md`, not by mechanically copying
legacy threading code.

`docs/architecture/SERVER_RULES.md` and current runtime contracts always win.

---

## Server work

Before modifying `server/**`:

```text
MUST preserve behavior outside approved scope.
MUST preserve Unity protocol compatibility unless explicitly changing it.
MUST preserve persistence boundaries.
MUST preserve concurrency/runtime ordering contracts.
MUST NOT introduce frameworks/layers/patterns without a demonstrated problem.
MUST NOT opportunistically refactor unrelated code.
MUST add/update focused tests for changed contracts.
MUST run required focused tests.
MUST run the full Maven server gate.
MUST report manual/DB/Unity/TLS/runtime gates not actually executed.
MUST NOT claim runtime success from static review alone.
```

Normal full gate:

```powershell
cd server
.\mvnw.cmd test
```

---

## Runtime ownership gate

For Zone/runtime/concurrency work, the coding model MUST distinguish the
**current production contract** from the **approved migration target**.

Approved target:

```text
1 ACTIVE Zone
= 1 virtual thread
= 1 writer for Zone-owned runtime state
```

Zone-owned runtime state includes, when present:

```text
Player
Monster
Boss
Npc
ItemMap
active gameplay effects
other live Zone world state
```

External execution contexts such as:

```text
Session reader/writer virtual threads
persistence/JDBC execution
web/admin/payment input
other Zones
```

must not directly mutate Zone-owned runtime entities. They communicate through
explicit Zone input/handoff boundaries.

Blocking I/O is allowed at infrastructure boundaries, including blocking
Socket I/O and JDBC, but MUST NOT run inside Zone gameplay execution.

Until a dedicated migration phase replaces an existing synchronization or
Player runtime contract, the current production implementation remains
authoritative. Do not use the target architecture as permission for an
unscoped cutover.

Every touched feature slice must leave the phase compliant with
`SERVER_RULES.md`. Do not preserve obsolete wrappers, managers, schedulers,
snapshots, result types, names, or file fragmentation merely because they
existed before the phase. This rule does not authorize unrelated repo-wide
cleanup.

---

## Gameplay readability gate

Before gameplay implementation/refactor, identify:

```text
feature center
main runtime object
legacy files/classes for the same feature when available
legacy vocabulary and normal flow
static Template/Data if any
shared Entity/base state if genuinely justified
Manager/collection/lifecycle owner if any
cross-feature Service/use case if any
technical boundaries that stay separate
normal gameplay flow
```

Prefer:

```text
direct game vocabulary
short context-aware method names
names consistent with the legacy feature vocabulary when semantics match
cohesive gameplay entities
obvious run/update/lifecycle flow
few meaningful abstractions
few file jumps
large cohesive files when appropriate
```

Avoid:

```text
one-file-per-trivial-type
automatic Optional usage
automatic record/result/snapshot creation
Factory+Manager+Registry+Scheduler symmetry
technical names replacing game actions
long names that repeat context already provided by the class
abstractions created only to reduce line count
```

A normal gameplay flow should be understandable mostly from class/method names,
without needing to understand the architecture first.

A refactor is suspect when it makes the same gameplay flow require:

```text
more terminology
longer technical names
more wrapper/result types
more file jumps
```

without creating a real correctness, ownership, subsystem, or technical
boundary.

When the touched feature exists in `../rongthanchibi`, compare the final naming
and normal flow with the legacy version. If the new code expresses the same
gameplay idea with more terminology but no additional semantic value, simplify
it before finishing.

---

## Final review gate

Before finishing server work:

1. Re-read affected sections of `SERVER_RULES.md`.
2. Inspect the final diff.
3. Verify no protocol/persistence/concurrency drift.
4. Verify the execution owner of every mutated runtime object is explicit.
5. Verify no blocking I/O was introduced into Zone gameplay execution.
6. Verify no unjustified fragmentation or new architecture ceremony.
7. Verify the touched slice does not retain obsolete architecture without a
   documented migration reason.
8. Verify focused regression coverage.
9. Run the full server gate.
10. Report exactly which runtime/manual gates were not executed.

For gameplay work, reviewer must be able to answer:

```text
Which file do I open first?
What is the main runtime object?
Which legacy files/classes and vocabulary were inspected?
Do the method/class names read like simple game actions?
Which touched names differ from the legacy equivalent, and why?
What does run/update/lifecycle do?
Where does state change?
Who owns collection/lifecycle?
Where is persistence?
Where is packet encoding?
How many files are needed to understand the normal flow?
```

A change can be functionally correct and still fail this gate if the touched
gameplay code became harder to name, read, or follow without a correctness
reason.

---

## Client work

Do not modify Unity/client code merely to simplify server architecture.

Client changes require an explicit task or approved shared-contract change.

If client work changes shared packets, serialization, login/resource contracts,
or Unity/server compatibility, the server architecture gate applies.

---

## Final principle

Choose code that is:

```text
easy to find
easy to read
easy to modify
hard to accidentally break
```

For gameplay code, prefer the implementation that reads most directly as game
logic while preserving protocol, persistence, concurrency, and infrastructure
boundaries.
