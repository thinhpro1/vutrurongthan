# SERVER RUNTIME ARCHITECTURE BASELINE

> **Status:** Explanatory architecture baseline for the approved runtime refactor direction.
>
> **Authority:** `docs/architecture/SERVER_RULES.md` remains the sole authoritative server rule. If this document conflicts with `SERVER_RULES.md`, the rule file wins.
>
> **Date:** 2026-09-23

## Core direction

```text
CLIENT
  ↓ blocking TCP
Session reader/writer virtual threads
  ↓ bounded Zone input
Zone
  = 1 ACTIVE Zone
  = 1 virtual thread
  = 1 writer
  ↓
mutable realtime Player / Monster / Boss / Npc / Item / Effect state
  ├─→ bounded network output → writer VT → Socket
  └─→ persistence handoff → blocking JDBC outside Zone → DB
```

Central rule:

> **Zone is the realtime mutation authority for Zone-owned runtime state.**

## Main decisions

```text
YES blocking network I/O first
YES Session reader/writer virtual threads
YES 1 ACTIVE Zone = 1 virtual thread baseline
YES Zone single-writer target
YES mutable realtime entities as long-term direction
YES idle Zone may freeze while preserving runtime state in RAM
YES normal DB persistence via checkpoint/final save
YES Effect belongs to runtime target entity
YES Monster AI should live primarily with Monster
NO snapshot in hot AI/combat paths
NO DB/network blocking I/O in Zone execution
NO network/persistence thread directly mutating Zone entities
NO worker pool/NIO/reactive DB before benchmark evidence
```

## Freeze semantics

`FROZEN` means execution is suspended, not runtime destruction.

Example:

```text
Monster #1 HP = 120 / 200
Zone freezes
Zone wakes later
Monster #1 HP remains 120 / 200
```

Time-based mechanics that must advance while frozen should use absolute
deadlines where suitable:

```text
respawnAt
effectEndAt
cooldownUntil
itemExpireAt
```

Regen, DoT while nobody is present, and world/event boss lifecycle require
explicit per-mechanic semantics.

## Persistence direction

While online:

```text
Zone/RAM = current realtime state
DB       = durable checkpoint
```

Normal gameplay:

```text
many runtime changes
→ dirty/revision
→ coalesce
→ save latest state periodically
```

Disconnect correctness is primarily about ordering:

```text
process prior gameplay
→ detach from realtime mutation
→ final save latest state
→ checkpoint attempt completes according to account contract
→ release same-account reservation
```

A separate Snapshot type is not mandatory when final detached state is already
stable. Stable SaveData/Snapshot is justified when an online Player continues
mutating while persistence runs.

## Migration order

```text
R0  lock rules/runtime contract
R1  pin current behavior with tests
R2  introduce Zone execution/lifecycle core
R3  move membership/movement mutation into Zone ownership
R4  move Monster lifecycle into Zone execution
R5  redesign Player as mutable runtime
R6  implement dirty checkpoint/final-save persistence model
R7  refactor Monster v2/readability/hot path
R8  extract real Entity/CombatEntity + implement Effect runtime
R9  finalize freeze/time semantics
R10 load test and optimize from evidence
```

Each phase must also clean the touched feature slice to current
`SERVER_RULES.md`. Do not postpone code-quality cleanup into a vague future
pass.

## Still open

```text
exact Zone input queue implementation
exact tick period
park vs stop/restart VT for freeze
DoT/regen semantics while frozen
exact mutable Player shape
exact Entity/CombatEntity shared state
checkpoint interval
JDBC batch strategy
exact Monster update phase API
```
