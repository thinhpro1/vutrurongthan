# AGENTS.md

## Mandatory project instructions

This repository contains a Unity client and a Java game server.

---

## Mandatory architecture gate

For any task that touches:

```text
server/**
server architecture
shared protocol/contracts
Unity/server compatibility
```

before planning, reviewing, testing, or changing code:

1. **MUST read and follow**
   `docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md`.

2. **MUST also read and follow**
   `docs/architecture/SERVER_FEATURE_READABILITY_RULES.md`.

3. Treat the two files as complementary authoritative contracts:

```text
SERVER_ARCHITECTURE_RULES_UPDATED.md
→ protocol / persistence / concurrency / dependency / testing safety

SERVER_FEATURE_READABILITY_RULES.md
→ feature center / Manager-Service choice / entity ownership /
  naming / gameplay flow / file layout / readability
```

4. If examples in the older architecture rule conflict with the feature-readability
   rule on gameplay naming, file shape, Manager/Service preference, method ordering,
   or split-vs-cohesion decisions, **the feature-readability rule controls those
   readability topics**, while the existing runtime/protocol/persistence/concurrency
   invariants remain mandatory.

5. **MUST inspect the current implementation before proposing or applying changes.**

6. If either mandatory rule file is missing or unreadable, **MUST stop before
   changing server code**.

7. If the requested implementation conflicts with current production runtime,
   protocol, persistence, or concurrency invariants, **MUST report the conflict
   before changing production code**.

---

## Architecture priority

When documents or examples conflict, use this order:

```text
1. Current production/runtime/database/protocol/concurrency contract
2. docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md
3. docs/architecture/SERVER_FEATURE_READABILITY_RULES.md for its owned topics
4. Newly approved feature/refactor design/spec
5. server/README.md
6. Older plans/specifications/reference source trees
```

Historical/reference source may guide:

```text
feature naming
feature center
Manager convention
method vocabulary
code reading flow
```

but does not override current safety/runtime contracts.

---

## Server work

Before modifying anything under `server/**`:

1. **MUST preserve existing behavior outside the requested scope.**

2. **MUST preserve Unity protocol compatibility** unless the task explicitly
   approves a protocol/client change.

3. **MUST preserve existing persistence boundaries.** Persistence implementation
   belongs under `persistence/**` unless an approved architecture change says otherwise.

4. **MUST NOT introduce new frameworks, architectural layers, abstractions, or
   Design Patterns unless they solve a demonstrated current problem.**

5. **MUST NOT revive obsolete runtime architecture from older plans/reference
   source** when it conflicts with current production contracts.

6. **MUST NOT opportunistically refactor unrelated code.**

7. **MUST add or update focused tests** for changed behavior/contracts/concurrency/
   protocol/persistence/regression risk.

8. **MUST run relevant focused tests and the full Maven server test gate** after
   implementation.

9. **MUST explicitly report** any Unity/manual/real-DB/TLS/runtime gate that was
   not actually executed.

10. **MUST NOT claim runtime success from static review alone.**

---

## Gameplay feature rules

Before adding or refactoring gameplay code, identify:

```text
feature center
main runtime object
static Template/Data if any
Manager/collection/lifecycle owner if any
cross-feature Service/use-case if any
technical boundaries that stay separate
```

Default mental model for an entity/static-data feature is:

```text
Feature
FeatureManager      // only when management authority exists
FeatureTemplate     // only when static definition exists
supporting types
```

This is a convention, not a quota.

Do not create missing roles only for symmetry.

### Large files

There is **no line-count threshold** requiring a gameplay class to split.

A 1,000+ line cohesive `Player`, `Monster`, `Item`, `Skill`, or `Zone` may be
better than many small files if it keeps one gameplay flow locally readable.

Split only when the extracted part has a real owner/subsystem/technical boundary.

### Navigation cost

A refactor is suspect if it:

```text
reduces lines per file
but
increases the number of files needed to understand one normal gameplay flow
```

### Manager vs Service

Use `Manager` naturally for feature collection/catalog/init/create/find/lifecycle
ownership.

Use `Service` for a real cross-owner/use-case orchestration.

Do not default every feature to `Service`.

Do not create peer `Factory + Service + Manager + Registry + Scheduler` merely
because each name is technically defensible.

### Entity behavior

Prefer entity-owned behavior near the entity:

```text
update
updateAttack
findTarget
injure/takeDamage
die
respawn
move
useItem
equip
```

Keep JDBC, packet encoding, socket/protocol handling, and unrelated cross-feature
transactions outside gameplay entities.

### File layout

Gameplay files should read top-to-bottom:

```text
constants
immutable refs/dependencies
runtime state
constructor/init
main lifecycle
primary actions
secondary actions
queries/getters
private helpers
nested types
```

Do not interleave enums/records/fields randomly between methods added over time.

---

## Coding principles

Prefer:

```text
direct readable game vocabulary
feature-first packages
obvious feature center
cohesive files/types
few meaningful abstractions
large cohesive gameplay files when appropriate
simple top-to-bottom flow
```

Avoid:

```text
unnecessary layers
pattern-driven abstractions
generic service locators
ownership-sprawling God classes
one-file-per-trivial-record fragmentation
technical names replacing simple game verbs
SQL inside gameplay code
packet encoding inside gameplay models/managers
DB writes on realtime hot paths
empty future packages
```

If choosing between a clever decomposition and a larger direct implementation
that is easier to follow:

> **Choose the implementation that is easier to read as game logic, while
> preserving the technical boundaries.**

---

## Scope discipline

Before editing server code, identify:

```text
feature owner
file/package owner
feature center/readability impact
naming/type-granularity impact
protocol impact
persistence impact
concurrency impact
tests required
behavior that must remain unchanged
```

If a change touches a sensitive runtime contract such as:

```text
Session close/disconnect ordering
account admission/same-account reservation
Zone synchronization
combat/lifecycle ordering
player persistence checkpoints
legacy packet bytes/order/width
database schema/transaction behavior
TLS/network transport behavior
```

it must include focused regression coverage appropriate to that contract.

---

## Server test gate

Normal full gate:

```powershell
cd server
.\mvnw.cmd test
```

If the Maven wrapper is unavailable, use equivalent system Maven.

When persistence/schema behavior changes, also follow the real MySQL integration
rules in `server/README.md`.

Protocol/concurrency/TLS/sensitive changes must run the focused gates required
by the authoritative architecture rules.

---

## Final server review gate

Before finishing any server implementation/refactor:

1. **MUST re-read the affected sections** of both authoritative rule files.

2. **MUST inspect the final diff** and verify no introduction of:

```text
naming/readability violations
unjustified fragmentation
ownership-sprawling God behavior
package/layer drift
protocol drift
persistence leakage
unrelated refactors
```

3. For gameplay features, reviewer must be able to answer:

```text
Which file do I open first?
What is the main runtime object?
Who manages the feature?
What does update/lifecycle do?
Where does state change?
Where is persistence?
Where is packet encoding?
```

4. **MUST verify focused regression coverage** for every changed contract.

5. **MUST report which automated/runtime gates actually passed** and which were
   not executed.

6. **MUST NOT mark work complete** while knowingly violating either rule file.

---

## Client work

Do not modify Unity/client code merely to simplify server architecture.

Client changes require an explicit task or approved shared-contract change.

If client work changes shared packets, serialization, login flow, resource
contracts, or Unity/server compatibility, the mandatory architecture gate applies.

---

## Final rule

Good code in this repository should make it easy to answer:

```text
Where is this feature?
Which file do I open first?
What does the main object do?
Who owns collection/lifecycle?
Where does the packet enter?
Where does gameplay happen?
Where is state changed?
Where is it persisted?
Where is it serialized back to the client?
Which test proves it?
```

Optimize for:

```text
easy to find
easy to read top-to-bottom
easy to modify
few unnecessary file jumps
hard to accidentally break
```
