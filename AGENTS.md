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

---

## Legacy gameplay reference gate

The sibling legacy project:

```text
../rongthanchibi
```

is an approved **readability/gameplay-flow reference**, not an authoritative
runtime architecture.

For work on an existing gameplay feature such as:

```text
Monster
Player
Npc
Boss
Item
Skill
Map/Zone
```

the coding model **MUST inspect the corresponding legacy implementation when
available before planning the change**.

Before coding, report briefly:

```text
Legacy reference inspected:
- ...

Adopt:
- ...

Reject:
- ...
```

Use the legacy source to study:

```text
feature center
base Entity/state choices
method vocabulary
update/lifecycle flow
field-vs-behavior balance
Manager usage
package/file organization
```

Do NOT copy obsolete legacy choices such as:

```text
Spring/JPA coupling
global mutable singletons
direct DB access from gameplay
packet encoding inside gameplay entities
unsafe concurrency
obsolete data/protocol behavior
thread-per-zone/thread-per-session architecture
```

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

## Gameplay readability gate

Before gameplay implementation/refactor, identify:

```text
feature center
main runtime object
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
cohesive gameplay entities
obvious update/lifecycle flow
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
abstractions created only to reduce line count
```

A refactor is suspect when it makes the normal gameplay flow require more files
without creating a real ownership/subsystem/technical boundary.

---

## Final review gate

Before finishing server work:

1. Re-read affected sections of `SERVER_RULES.md`.
2. Inspect the final diff.
3. Verify no protocol/persistence/concurrency drift.
4. Verify no unjustified fragmentation or new architecture ceremony.
5. Verify focused regression coverage.
6. Run the full server gate.
7. Report exactly which runtime/manual gates were not executed.

For gameplay work, reviewer must be able to answer:

```text
Which file do I open first?
What is the main runtime object?
What does update/lifecycle do?
Where does state change?
Who owns collection/lifecycle?
Where is persistence?
Where is packet encoding?
How many files are needed to understand the normal flow?
```

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
