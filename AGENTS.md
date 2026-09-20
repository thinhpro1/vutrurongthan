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

2. **MUST treat**
   `docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md`
   as the authoritative architecture, naming, ownership, type-granularity,
   dependency, persistence, concurrency, protocol, and testing contract
   for the server.

3. **MUST inspect the current implementation before proposing or applying changes.**

4. If the rule file is missing or unreadable, **MUST stop before changing server code**.

5. If the requested implementation conflicts with the rule file,
   **MUST report the conflict before changing production code**.

For client-only work that does not affect the server, shared protocol,
or Unity/server compatibility, the server architecture rule does not need
to be loaded.

---

## Server work

Before modifying anything under `server/**`:

1. **MUST preserve existing behavior outside the requested scope.**

2. **MUST preserve Unity protocol compatibility**
   unless the task explicitly approves a protocol/client change.

3. **MUST preserve existing persistence boundaries.**
   Persistence implementation belongs under `persistence/**`
   unless an approved architecture change explicitly says otherwise.

4. **MUST NOT introduce new frameworks, architectural layers,
   abstractions, or Design Patterns unless they solve a demonstrated
   current problem and comply with the architecture rule.**

5. **MUST NOT revive obsolete architecture from older plans/specifications**
   when it conflicts with the current production contract or
   `SERVER_ARCHITECTURE_RULES_UPDATED.md`.

6. **MUST NOT opportunistically refactor unrelated code.**

7. **MUST add or update focused tests**
   for changed behavior, contracts, concurrency, protocol handling,
   persistence behavior, or regression risk.

8. **MUST run relevant focused tests and the full Maven server test gate**
   after implementation.

9. **MUST explicitly report**
   any Unity, manual, real-DB, TLS, or other runtime gate that was not
   actually executed.

10. **MUST NOT claim runtime success from static review alone.**

---

## Architecture priority

When documents or examples conflict, use this order:

```text
1. Current production/runtime/database/protocol contract
2. docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md
3. Newly approved feature design/spec
4. server/README.md
5. Older plans/specifications/examples
```

Older documents and reference source trees are historical/reference material
only unless explicitly re-approved.

Reference projects may guide naming, readability, or game vocabulary,
but they do not override the current server architecture or runtime contracts.

---

## Coding principles

Prefer:

```text
direct readable code
clear ownership
feature-first packages
simple game vocabulary
small number of meaningful abstractions
cohesive files and types
```

Avoid:

```text
unnecessary layers
pattern-driven abstractions
generic service locators
God classes
one-file-per-trivial-record fragmentation
generic Models/DTOs/Types/Results containers
SQL inside gameplay code
packet encoding inside gameplay models/services
database writes on realtime hot paths
empty future packages
```

Do not create an abstraction, top-level type, package, or wrapper merely
because a Design Pattern or naming convention suggests it.

If choosing between a clever abstraction and a direct readable implementation:

> **Choose the direct readable implementation.**

Detailed naming, ownership, top-level-vs-nested type, package, and model
granularity rules belong to
`SERVER_ARCHITECTURE_RULES_UPDATED.md`
and must not be redefined independently here.

---

## Scope discipline

Before editing server code, identify:

```text
feature owner
package owner
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
account admission or same-account reservation
Zone synchronization
combat/lifecycle ordering
player persistence checkpoints
legacy packet bytes/order/width
database schema or transaction behavior
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

If the Maven wrapper is unavailable, use the equivalent Maven command.

When persistence/schema behavior changes, also follow the real MySQL
integration gate documented in:

```text
server/README.md
```

Protocol, concurrency, TLS, and other sensitive changes must also run
the focused gates required by
`SERVER_ARCHITECTURE_RULES_UPDATED.md`.

---

## Final server review gate

Before finishing any server implementation or refactor:

1. **MUST re-check the affected sections**
   of `SERVER_ARCHITECTURE_RULES_UPDATED.md`.

2. **MUST inspect the final diff**
   and verify that the change did not introduce:
   - naming violations;
   - unjustified top-level tiny types;
   - God classes;
   - package/layer drift;
   - protocol drift;
   - persistence leakage;
   - unrelated refactors.

3. **MUST verify focused regression coverage**
   for every changed contract.

4. **MUST report which automated/runtime gates actually passed**
   and which were not executed.

5. **MUST NOT mark the work complete**
   if the implementation knowingly violates the architecture rule.

---

## Client work

Do not modify Unity/client code merely to simplify the server.

Client changes require an explicit task or an approved protocol/design change.

If client work changes shared packets, serialization, login flow,
resource contracts, or other Unity/server compatibility behavior,
the mandatory architecture gate applies.

---

## Final rule

Good code in this repository should make it easy to answer:

```text
Where is this feature?
Who owns this rule?
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
easy to read
easy to modify
hard to accidentally break
```
