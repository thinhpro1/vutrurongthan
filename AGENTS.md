# AGENTS.md

## Mandatory project instructions

This repository contains a Unity client and a Java game server.

---

## Mandatory rules for every repository task

Before planning, reviewing, testing, or changing anything in this repository,
**MUST read and follow**:

```text
docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md
```

This applies to every task in this project, not only tasks under `server/**`.
For work outside the server, follow the rules relevant to that scope and
preserve the existing Unity/server compatibility contract. If this file is
missing or unreadable, stop and ask before proceeding with project work.

---

## Server work

Before planning, reviewing, or modifying anything under `server/**`:

1. **MUST read**
   `docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md`

2. **MUST treat**
   `docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md`
   as the authoritative architecture and coding contract for the server.

3. **MUST inspect the current implementation before proposing or applying changes.**

4. **MUST preserve existing behavior outside the requested scope.**

5. **MUST preserve Unity protocol compatibility**
   unless the task explicitly approves a protocol/client change.

6. **MUST follow the intended dependency direction:**

   ```text
   Network Handler
         ↓
   Gameplay Service
         ↓
   Runtime Model
         ↓
   Repository interface
         ↓
   JDBC implementation
   ```

7. **MUST keep persistence under `persistence/**`.**

8. **MUST NOT introduce new frameworks, architectural layers, abstractions,
   or Design Patterns unless they solve a demonstrated current problem.**

9. **MUST NOT revive obsolete architecture from older planning documents**
   when it conflicts with `SERVER_ARCHITECTURE_RULES_UPDATED.md`.

10. **MUST NOT opportunistically refactor unrelated code.**

11. **MUST add or update focused tests**
    for any changed behavior or contract.

12. **MUST run the relevant focused tests and the full Maven server test gate**
    after implementation.

13. **MUST explicitly report any manual, Unity, or real-DB gate that was not actually executed.**

14. **MUST NOT claim runtime success from static review alone.**

---

## Known architecture cleanup targets

Do not add more unrelated responsibility to these classes unless the task is specifically refactoring them:

```text
ResourceService
MessageHandler
MapService
NetworkServer
```

Expected long-term direction:

```text
ResourceService
→ GameResources + focused resource loaders

MessageHandler
→ thin dispatcher + feature handlers

MapService
→ map-only responsibility

Combat logic
→ CombatService

Monster lifecycle
→ MonsterService

NetworkServer bootstrap/wiring
→ ServerBootstrap
```

---

## Architecture priority

When documents conflict, use this order:

```text
1. Current production/runtime/database/protocol contract
2. docs/architecture/SERVER_ARCHITECTURE_RULES_UPDATED.md
3. Newly approved feature design/spec
4. server/README.md
5. Older plans/specifications
```

Older documents are historical references only unless explicitly re-approved.

---

## Coding principles

Prefer:

```text
direct readable code
clear ownership
feature-first packages
simple game vocabulary
small number of meaningful abstractions
```

Avoid:

```text
unnecessary layers
pattern-driven abstractions
generic service locators
God classes
SQL inside gameplay code
packet encoding inside gameplay models/services
database writes on realtime hot paths
empty future packages
```

Do not create an abstraction only because a Design Pattern suggests it.

If choosing between a clever abstraction and a direct readable implementation:

> **Choose the direct readable implementation.**

---

## Scope discipline

Before editing, identify:

```text
- feature owner
- package owner
- protocol impact
- persistence impact
- concurrency impact
- tests required
- behavior that must remain unchanged
```

If a change touches any of these sensitive areas:

```text
Session.close
account admission
same-account reservation
Zone synchronization
monster combat ordering
disconnect final checkpoint
legacy packet format
database schema
```

it must include focused regression coverage.

---

## Server test gate

Normal gate:

```powershell
cd server
.\mvnw.cmd test
```

If the Maven wrapper is unavailable, use the equivalent Maven command.

When persistence/schema changes, also follow the real MySQL integration gate documented in:

```text
server/README.md
```

---

## Client work

Do not modify Unity/client code merely to simplify the server.

Client changes require an explicit task or an approved protocol/design change.

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
