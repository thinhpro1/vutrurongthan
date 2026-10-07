# Monster Readability Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this bounded cleanup in place. Preserve the earlier uncommitted ZoneWriter and Player-combat work.

**Goal:** Make the existing Monster flow easier to read without changing gameplay.

**Architecture:** Monster remains the gameplay center; Zone owns execution, membership and ordered output. MonsterManager retains catalog, creation and lifecycle triggering. No new production file, layer or runtime boundary is needed.

**Tech Stack:** Java 21, Maven, JUnit 5.

**Spec:** The bounded design below, authorized by the user's request to audit and start fixing Monster.

## Design and audit

Read first: `server/src/main/java/com/project/game/monster/Monster.java`.
The normal flow remains `MonsterManager.update → Zone.updateMonsters → Monster.update`.
Each Monster respawns and returns if dead; otherwise it moves, then attempts retaliation.

Legacy inspected under `../rongthanchibi/rongthanchibi/`:

- `entity/monster/Monster.java`: `update`, `updateAttack`, `findTarget`, `injure`, `respawn`, `addEnemy`, `delayAttack`.
- `entity/monster/MonsterManager.java`: template initialization/catalog.
- `map/Zone.java`: `run → update → monster.update`.
- `service/AreaService.java`: Monster output methods.

Adopt direct names, one cohesive entity and a visible update flow. Reject singleton/DB coupling, entity locks, per-Zone platform threads and packet encoding inside gameplay. Legacy Monster has no corresponding patrol/chase implementation to copy.

Current enemies store accumulated damage values that no consumer reads. Only unique insertion-ordered Player IDs are meaningful. Use `LinkedHashSet<Integer>` and remove the unused accumulation helper. Make constructor-only `maxHp` final. Remove unused `xFirst()` and `enemyCount()` APIs; keep their underlying state/count semantics. Make the internal attack-delay helper private and use the equivalent legacy name `delayAttack()`.

Keep `updateRespawn(now)` because it checks a deadline, whereas legacy `respawn()` restores unconditionally. Keep existing update/movement/attack names. Place movement helpers next to movement, and attack/range helpers next to attack; put read APIs after behavior. Merge movement candidate scans, retaining an immediate stop for any in-range Player before leash filtering and without mutating until the scan completes.

## Constraints and review focus

- Preserve strict cooldown/respawn deadlines, failed-attempt cooldown consumption, and movement before attack.
- Preserve enemy insertion order, repeated-hit deduplication, distinct-enemy cooldown and cleanup.
- Preserve strict attack range, spawn leash, nearest-distance/lower-ID tie and no automatic aggro.
- Preserve one random draw over eligible candidates in supplied order; keep `findTarget`'s two passes.
- Preserve overflow validation before lethal mutation and existing movement/distance arithmetic.
- Preserve valid packet records, snapshots, protocol, persistence and Zone writer ownership. No scheduler or Unity changes.

## Task 1: Characterize, simplify and verify Monster

**Modify:** `server/src/main/java/com/project/game/monster/Monster.java`.
**Test:** `server/src/test/java/com/project/game/monster/MonsterTest.java`.

**Interfaces:** Public `update(List<Player>, long, RandomGenerator)`, `injure(int, long, long, int)`, enemy IDs/removal, packet records and snapshot stay compatible. Only unused read APIs are removed; the internal delay helper becomes private.

- [x] Add a repeated-hit test: IDs `8, 7, 8` remain `[8, 7]`; select the first with one draw of bound `2`; equality at 1200 ms is not due. Removing `8` changes the cooldown to 1600 ms; equality is not due and the next millisecond attacks `7` with one draw of bound `1`.
- [x] Add movement priority coverage: an earlier chase candidate cannot cause movement when a later in-range Player exists, even outside the spawn leash; cover reversed order too.
- [x] Strengthen rejected lethal-deadline overflow coverage with unchanged snapshot and enemy IDs.
- [x] Run focused tests before production edits. These are characterization tests and should pass; do not invent a failing behavior for a behavior-preserving refactor.
- [x] Apply only the in-place cleanup described above.
- [x] Run the same focused tests, then fresh independent review, affected-rule review and `git diff --check`.
- [x] Run the full server Maven gate using JDK 21. This checkout has no Maven wrapper; use installed Maven.
- [x] Record actual results and unexecuted MySQL, Unity, deployed TLS/runtime and load gates. No commit/push is part of this task.

Focused command (from `server`, JDK 21):

```powershell
mvn '-Dtest=MonsterTest,MonsterManager*Test,ZoneMonsterCombatTest,MonsterPacketWriterTest' test
```

Full gate: `mvn test` from `server`, JDK 21.


## Completion evidence (2026-10-04)

Implemented and reviewed. Only Monster.java and MonsterTest.java changed in this slice; earlier ZoneWriter/Player/combat edits remain intact.

- Focused gate before and after cleanup: 63 tests passed, no failures/errors/skips.
- Full Maven gate with JDK 21: BUILD SUCCESS; 521 total, 517 passed, 4 opt-in MySQL tests skipped, no failures/errors.
- Automatic TLS network/transport integration tests both passed.
- Fresh independent scoped review: no actionable findings. Affected rules and final diff reviewed; git diff --check passed.
- Not executed: real MySQL integration, manual Unity gameplay, deployed TLS/server runtime, load testing.
- No commit or push performed.

## Task 2: Finish package consolidation (user correction)

Task 1 only cleaned up Monster.java. The user clarified that the package still has too many small files. Complete the originally intended cohesive feature shape: exactly Monster.java, MonsterTemplate.java and MonsterManager.java in the monster package.

- [x] Preserve immutable boundary semantics while moving MonsterSnapshot to Monster.Snapshot, MonsterSpawn to MonsterTemplate.Spawn, and MonsterDart/Phase to MonsterTemplate.Dart/Phase. Spawn is reusable static creation configuration; Snapshot is captured current runtime state. Do not combine those meanings.
- [x] Characterize snapshot/spawn stability after runtime injury and defensive phase icon-list copying before changing production code.
- [x] Migrate all production/test callers to the nested types, remove the three redundant source files, and leave packet field order, catalog validation, persistence and writer behavior unchanged.
- [x] Run the focused gate with clean compilation to exclude stale deleted class files; run the full Maven server gate.
- [x] Update the runtime baseline with the three-file source organization; inspect final scoped diff and affected rules; obtain fresh independent review.

Legacy additionally inspected: entity/monster/MonsterTemplate.java, model/MonsterDartTemplate.java, model/DartTemplateInfo.java, data/MapMonsterData.java, and service/Service.java under the sibling legacy source. Adopt direct static/runtime naming; reject mutable resource arrays and mutable Monster seeds, singleton/JPA coupling and old resource wire shapes.

Dart, Spawn and Snapshot names drop the redundant Monster prefix because the containing class supplies context. They retain their real resource/creation/read-boundary meanings. No new layer, live-runtime reference, schema, packet shape or scheduler is introduced.


### Task 2 verification

Focused gate (JDK 21, from server):

```powershell
mvn clean '-Dtest=Monster*Test,MonsterCatalogLoaderTest,ResourcePacketWriterTest,MapPacketWriterTest,MessageHandlerMapTest,MessageHandlerResourceTest,GameResourcesTest,ZoneMonsterCombatTest,MapManagerCatalogTest' test
```

Characterization before production edits and clean compilation after edits each passed 163 tests without skips or failures. The final full mvn test gate passed: 523 total, 519 passed, 4 opt-in MySQL tests skipped, no failures/errors. Automatic TLS network and transport tests both passed. Record component types/names/order and packet encoding statements are preserved. The normal git diff --check gate passes.

Real MySQL integration, manual Unity gameplay, deployed TLS/server runtime and load testing were not executed. Independent final review found no actionable production issues; the stale MonsterDart type name in resources/json/README.md was corrected to MonsterTemplate.Dart. No commit or push performed.
