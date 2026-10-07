# Zone Gameplay Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Chuyển luật Player đánh Monster khỏi Zone, rút ngắn luồng combat, giữ toàn bộ correctness contracts hiện tại.

**Architecture:** Public travel giữ ở MapManager; execution mechanics giữ trong đúng một ZoneWriter của mỗi Zone. Zone kiểm tra ownership/tìm entity/gửi kết quả; Player giữ attack/reward; Monster giữ HP/death/AI. Bỏ Combat router, inject Clock trực tiếp tại protocol composition.

**Tech Stack:** Java 21, Maven, JUnit 5.11.0, custom binary Unity/server protocol.

**Spec:** [2026-10-03-zone-gameplay-ownership-design.md](../specs/2026-10-03-zone-gameplay-ownership-design.md). Đọc cả spec, SERVER_RULES.md và runtime baseline trước execution.

**Trạng thái:** Đã triển khai code theo phạm vi được duyệt ngày 2026-10-04; giữ nguyên extraction ZoneWriter. Code còn trong working tree, chưa commit/push.

## Global Constraints

- Java 21; Maven; JUnit 5.11.0; không thêm dependency.
- Giữ public travel trong MapManager và đúng một ZoneWriter package-private cho mỗi Zone.
- Giữ protocol bytes, DB schema, persistence ordering và gameplay constants hiện tại.
- Zone.members dùng exact Session identity làm membership authority; Session.zone chỉ là routing backlink.
- Mọi mutation của Player/Monster đang thuộc Zone chạy trong writer của Zone đó.
- Không blocking I/O trong Zone writer; không nested cross-Zone wait.
- Không đổi queue capacity, overload policy, synchronization hoặc cơ chế ACTIVE/FROZEN/STOPPED.
- Không thêm CombatService, CombatManager, PlayerContext, Entity base hoặc result/snapshot mới.
- Không sửa Unity/client.

## Review Focus

1. Prepare với damage 0: vẫn target được; impact không mutate/send — Task 1 + 2.
2. Stale Session backlink / same Player ID khác Session / detach trước queued hit: writer revalidate exact membership — Task 2.
3. Hai lethal contenders: đúng finisher theo writer order, không duplicate reward, respawn được thưởng lần mới — Task 2.
4. Input queue-full hoặc outbound rejection: không mutate muộn, close/save ngoài writer và lưu reward mới nhất — Task 2.
5. Writer chậm sau impact: deadline dùng Clock đã sample trước enqueue; không đổi pending/replay protocol — Task 3.

---

## File map

Đường dẫn dưới đây tương đối với `D:/doan11/vutrurongthan`.

| File | Vai trò sau phase |
|---|---|
| `server/src/main/java/com/project/game/player/Player.java` | Thêm canTarget/attackMonster, giữ reward behavior. |
| `server/src/main/java/com/project/game/map/Zone.java` | Combat entrypoint + ownership/lookup/output, bỏ low-level test hooks. |
| `server/src/main/java/com/project/game/combat/Combat.java` | Xóa lớp chỉ routing + Clock. |
| `server/src/main/java/com/project/game/network/handler/CombatHandler.java` | Parse/pending và gọi Zone; nhận Clock. |
| `server/src/main/java/com/project/game/network/SessionServices.java` | Inject Clock thay Combat. |
| `server/src/main/java/com/project/game/network/handler/MessageHandler.java` | Wiring CombatHandler. |
| `server/src/main/java/com/project/game/bootstrap/ServerBootstrap.java` | Compose Clock.systemUTC. |
| `docs/architecture/SERVER_RUNTIME_ARCHITECTURE_BASELINE.md` | Ghi luồng entity combat cuối cùng, không thay rule authority. |

Monster.java / MapManager.java / ZoneWriter.java / AreaService.java / Repository / packet writers không cần production edit theo plan. Các loại Monster.Damage/Attack/Move/Respawn hiện có giữ nguyên.

## Commands và điểm xuất phát

Tại repository root, kiểm tra `git status --short` và giữ lại diff hiện có. Không reset/revert extraction ZoneWriter. Chỉ checkpoint/commit theo workflow người dùng đang dùng, không tự commit cả dirty tree.

Trong mỗi terminal test, dùng working directory `server` và Java 21:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Repo hiện không có `mvnw.cmd`; dùng `mvn` đã cài. Gate cuối tương đương full `mvnw.cmd test` là `mvn test`.

## Task 1: Player sở hữu target/attack/reward

**Files**

- Modify: `server/src/main/java/com/project/game/player/Player.java`.
- Test: `server/src/test/java/com/project/game/player/PlayerTest.java`.

**Interfaces**

- Consumes: `Monster.isAlive()`, `Monster.injure(int, long, long, int) -> Monster.Damage`, `Player.addPotential(long) -> long`.
- Produces: `Player.canTarget(Monster) -> boolean`; `Player.attackMonster(Monster, long nowMillis, int playerCount) -> Monster.Damage` (nullable).

- [x] **1. Thêm entity tests trước API.** Dùng MonsterManager.createForMap với canonical resource/repository fixture sẵn có; Monster constructor package-private không được mở public để test. Các entity chưa attach Zone nên gọi trực tiếp. Private fixture Player damage 0 dùng constructor và CurrentStats hiện có, không thêm production factory.

  Test names/assertions (mỗi case dùng fixture mới):

  ```java
  // canTargetRejectsNullDeadMonsterAndDeadPlayer
  assertFalse(player.canTarget(null));
  assertTrue(player.canTarget(liveMonster));
  // Sau lethal injure của fixture Monster: false; sau Player HP = 0: false.

  // zeroDamageCanTargetButCannotAttack
  assertTrue(zeroDamagePlayer.canTarget(monster));
  assertNull(zeroDamagePlayer.attackMonster(monster, 1_000_000L, 1));
  assertEquals(300L, monster.hp());
  assertEquals(1L, zeroDamagePlayer.potential());

  // attackUsesPlayerDamageAndRewardsOnlyOnceOnDeath
  assertEquals(new Monster.Damage(101, 10L, 290L, false, 0L),
          player.attackMonster(monster, 1_000_000L, 1));
  assertEquals(1L, player.potential());
  // 29 hit tiếp theo: hit cuối killed=true; potential=11.
  assertTrue(lethal.killed());
  assertEquals(11L, player.potential());
  assertNull(player.attackMonster(monster, 1_000_001L, 1));
  assertEquals(11L, player.potential());

  // lethalRewardSaturatesPotential: potential trước kill = Long.MAX_VALUE - 5.
  // Sau kill reward 10:
  assertEquals(Long.MAX_VALUE, player.potential());
  ```

- [x] **2. Chạy `mvn '-Dtest=PlayerTest' test`.** Expect compile failure ở đúng hai API chưa tồn tại; không nhận lỗi fixture/dependency là RED hợp lệ.
- [x] **3. Implement đúng hai signature trong Player.java.** Check canTarget rồi damage > 0; injure; nếu killed và reward > 0 thì addPotential; trả result nguyên bản. Không packet, Session, lookup, enqueue hoặc I/O.
- [x] **4. Chạy `mvn '-Dtest=PlayerTest,MonsterTest' test`.** Expect BUILD SUCCESS, không failure/error; kiểm tra cả reward saturation và invalid/dead target.
- [x] **5. Review diff Task 1.** API thể hiện game action, giữ Monster-local HP/death; không thay stats/formula. Checkpoint chỉ file task khi workflow hiện tại cần commit.

## Task 2: Zone còn ownership/world/output, tests qua boundary đúng

**Files**

- Modify: `server/src/main/java/com/project/game/map/Zone.java`.
- Test/modify: `server/src/test/java/com/project/game/map/ZoneMonsterCombatTest.java`.
- Test/modify: `server/src/test/java/com/project/game/map/ZoneTest.java`.
- Test/modify: `server/src/test/java/com/project/game/map/MapManagerCatalogTest.java`.
- Test/modify: `server/src/test/java/com/project/game/network/SessionTest.java`.
- Verify unchanged coverage: `server/src/test/java/com/project/game/combat/CombatTest.java` (rename ở Task 3).

**Interfaces**

- Consumes: hai Player APIs của Task 1; ZoneWriter tryCall/call hiện tại; AreaService.monsterDamage và potential.
- Produces, unchanged public: `Zone.canTargetMonster(Session, int) -> boolean`, `Zone.attackMonster(Session, int, long) -> boolean`.
- Produces, private: `synchronized boolean canTarget(Session, int)`; `synchronized boolean attack(Session, int, long, List<Session>)`.
- Removes, package-private: `Zone.damageMonster(int, int, long, long)`, `Zone.hasLiveMonster(int)`.

- [x] **1. Thêm/giữ characterization tests trước đổi delegation.** Các test giữ behavior có thể pass trên code cũ; không tạo thay đổi gameplay giả chỉ để test RED.

  - `zeroDamagePrepareAndImpactKeepExistingSemantics`: target true; attack false; HP 300/potential 1; outbound rỗng sau drain admission.
  - `combatRejectsStaleBacklinkAndDifferentSessionWithSamePlayerId`: cả canTarget/attack false; HP/reward/outbound không đổi, kể cả Session.zone trỏ candidate Zone.
  - `queuedAttackRevalidatesMembershipAfterDetach`: writer bị chặn bằng latch; enqueue detach trước hit; hit false, HP/potential không đổi. Không dùng Thread.sleep hoặc client timestamps.
  - `writerOrderDecidesFinisherInBothOrders`: setup fresh canonical Monster còn HP 100 trước attach; Player A damage 60/B damage 70. Dùng latch và xác nhận từng caller đã enqueue theo order; A→B có B potential 11/A 1, B→A có A 11/B 1. Mỗi Session nhận đúng một death broadcast; later hit false/không packet/không reward. Giữ test respawn thưởng được lần mới.
  - `combatRejectsFullOrStoppedWriterWithoutDelayedMutation`: queue capacity 1, blocker + một queued action; canTarget/attack false khi đầy; release không có damage/reward/packet muộn. Case STOPPED cũng false.
  - `combatRejectionCheckpointsRewardOutsideWriter` trong SessionTest: managed attacker, repository spy, outbound queue từ chối lethal output; sau attack, Session CLOSED/member đã remove, checkpoint potential 11/Monster HP 0. Capture attack caller và checkpoint Thread; assert cùng caller bên ngoài writer. Dùng blocking checkpoint để assert account còn reserved trước release và đã release sau checkpoint; reuse test queue/repository patterns hiện có.
  - Giữ death → potential packet order/bytes, observer không nhận potential và cross-Zone không nhận combat broadcast.

  Assertions tiêu biểu của các case trên:

  ```java
  // A -> B; case B -> A đảo Player nhận 11/1.
  assertEquals(1L, playerA.potential());
  assertEquals(11L, playerB.potential());
  assertEquals(0L, zone.monsterSnapshots().getFirst().hp());
  assertFalse(zone.attackMonster(sessionA, 101, 1_000_001L));
  // Stale backlink / cùng ID khác Session:
  assertFalse(zone.canTargetMonster(nonMember, 101));
  assertFalse(zone.attackMonster(nonMember, 101, 1_000_000L));
  // Lethal outbound rejection, checkpoint đã hoàn tất:
  assertSame(attackCaller, checkpointThread.get());
  assertEquals(11L, savedPlayer.potential());
  assertEquals(SessionState.CLOSED, attacker.state());
  assertFalse(zone.hasPlayer(attacker));
  ```

- [x] **2. Chạy characterization `mvn '-Dtest=ZoneMonsterCombatTest,CombatTest,SessionTest' test`.** Expect pass trước refactor; sửa lỗi test setup nếu fail, không đổi contract để làm test pass.
- [x] **3. Sửa hai action trong Zone.java, đặt cạnh entrypoint.** Rename private helpers như Interfaces; giữ synchronized và mọi writer guard/rejection policy. Writer-side membership validation trước live lookup/call Player. canTarget gọi Player.canTarget; attack gọi Player.attackMonster một lần với members.size tại execution. Broadcast result rồi gửi player.potential khi lethal reward dương; bỏ Zone.addPotential và trực tiếp Monster.injure. closeRejected giữ ngoài writer.
- [x] **4. Migrate tests rồi xóa hai low-level hooks.** ZoneMonsterCombatTest dùng semantic enter/attack cho damage/deadline; setup aggro chuyên biệt dùng retained fixture Monster qua ZoneTestHooks.call. ZoneTest freeze/wake dùng fixture monster.injure trong submitted action. MapManagerCatalogTest dùng Session enter/attack ở Zone đầu để chứng minh HP hai Zone độc lập. hasLiveMonster assertion thay bằng snapshot status/HP; không xuất thêm public live Monster API.
- [x] **5. Chạy `mvn '-Dtest=PlayerTest,MonsterTest,ZoneMonsterCombatTest,CombatTest,ZoneTest,ZoneWriterTest,MapManagerCatalogTest,SessionTest,MapManagerTest' test`.** Expect BUILD SUCCESS; finisher ở cả hai order và save rejection đều pass.
- [x] **6. Review touched Zone.** Không còn damage/reward algorithms; entrypoint không đưa live entity ra ngoài writer; PlayerSaveData.capture/Monster update/world cleanup và MapManager travel giữ nguyên. Checkpoint chỉ task nếu workflow cần.

## Task 3: Bỏ Combat router, giữ protocol và time semantics

**Files**

- Delete: `server/src/main/java/com/project/game/combat/Combat.java`.
- Modify: `server/src/main/java/com/project/game/network/handler/CombatHandler.java`.
- Modify: `server/src/main/java/com/project/game/network/SessionServices.java`.
- Modify: `server/src/main/java/com/project/game/network/handler/MessageHandler.java`.
- Modify: `server/src/main/java/com/project/game/bootstrap/ServerBootstrap.java`.
- Modify: `server/src/test/java/com/project/game/testsupport/GameplayServices.java`.
- Modify: `server/src/test/java/com/project/game/testsupport/TestServices.java`.
- Modify: `server/src/test/java/com/project/game/testsupport/GameplayTestSupport.java`.
- Rename: `server/src/test/java/com/project/game/combat/CombatTest.java` → `server/src/test/java/com/project/game/map/ZonePlayerCombatTest.java` (package/class đổi tương ứng, giữ coverage).
- Modify direct callers: `server/src/test/java/com/project/game/network/SessionTest.java`, `server/src/test/java/com/project/game/monster/MonsterManagerRetaliationTest.java`, `server/src/test/java/com/project/game/map/MapManagerTest.java`.
- Test/modify: `server/src/test/java/com/project/game/network/MessageHandlerCombatTest.java`.
- Modify: `docs/architecture/SERVER_RUNTIME_ARCHITECTURE_BASELINE.md`.

**Interfaces**

- Consumes: public Zone APIs giữ nguyên của Task 2.
- Produces: `CombatHandler(Session session, Clock clock)` (package-private).
- Produces: `SessionServices(AccountAuth auth, GameResources resources, MapManager maps, Clock clock, MonsterManager monsterManager, PlayerManager playerManager)`; component/accessor `Clock clock()` thay Combat.
- Test-only composition: `GameplayServices.clock() -> Clock`; giữ `canTargetMonster(Session,int)` và `attackMonster(Session,int)` tiện ích hiện có, route trực tiếp sang candidate Zone.

- [x] **1. Thêm `impactSamplesClockBeforeWaitingForZoneWriter` vào MessageHandlerCombatTest.** Private context fixture nhận Clock; chuẩn bị Monster HP 10 và pending hợp lệ; block writer bằng latch; impact caller dùng test Clock có latch khi millis được đọc. Phải thấy clock sample trong lúc writer còn block; advance Clock +60_000 rồi release. Lethal HP 0/potential 11; update ở sample+9_000 chưa respawn, +9_001 có respawn. Đây là characterization về deadline, không phải timestamp arbitration.

  ```java
  assertTrue(clockSampled.await(5, TimeUnit.SECONDS)); // trước release writer
  // Sau impact và sau khi caller hoàn tất:
  assertEquals(0L, zone.monsterSnapshots().getFirst().hp());
  assertEquals(11L, session.player().potential());
  zone.updateMonsters(sample + 9_000L, new Random(1L));
  assertEquals(0L, zone.monsterSnapshots().getFirst().hp());
  zone.updateMonsters(sample + 9_001L, new Random(1L));
  assertEquals(300L, zone.monsterSnapshots().getFirst().hp());
  ```
- [x] **2. Chạy `mvn '-Dtest=MessageHandlerCombatTest' test`.** Expect regression cũ và time characterization pass trước bỏ router.
- [x] **3. Đổi production wiring trong cùng một bước compile.** SessionServices giữ requireNonNull cho Clock; Bootstrap inject systemUTC; MessageHandler truyền services.clock. CombatHandler chỉ parse/pending/no-zone/CLOSED routing; sample thời gian trước Zone call. Xóa Combat.java sau khi thay caller; không đưa luật entity lên Handler.
- [x] **4. Migrate test composition/callers và rename suite.** GameplayServices giữ Clock thay Combat; `.combat().attackMonster` → `.attackMonster`, `.combat().canTargetMonster` → `.canTargetMonster`; TestServices inject gameplay.clock. `killMonster(Combat,Session)` → `killMonster(GameplayServices,Session)`. Case direct new Combat dùng test composition với Clock. Xóa import game.combat và kiểm tra không còn reference; không giảm packet/reward/concurrency coverage.
- [x] **5. Chạy `mvn '-Dtest=ZonePlayerCombatTest,MessageHandlerCombatTest,SessionTest,MapManagerTest,MonsterManagerRetaliationTest' test`.** Expect BUILD SUCCESS; replay/mismatch/no-target/malformed/map-change pending cases giữ nguyên.
- [x] **6. Cập nhật runtime baseline.** Thêm Player attack ownership và request chain cuối; xác nhận PlayerSaveData/world cleanup thuộc Zone, writer queue-drain và MonsterManager 100 ms vẫn giữ nguyên. Không sửa SERVER_RULES hoặc đánh dấu một migration khác hoàn tất.
- [x] **7. Review toàn bộ diff với reviewer độc lập.** Đọc lại SERVER_RULES sections 18–25. Xác nhận mutation owner của Player/Monster, không I/O/nested owner wait, packet bytes/ordering/persistence/clock không drift; compare final naming với legacy. Kiểm tra `rg -n 'game\.combat|\.combat\(|canTargetMonsterOnWriter|attackMonsterOnWriter|damageMonster|hasLiveMonster' server/src` không còn reference bị xóa.
- [x] **8. Chạy focused gate cuối:**

  ```powershell
  mvn '-Dtest=PlayerTest,MonsterTest,ZonePlayerCombatTest,ZoneMonsterCombatTest,ZoneTest,ZoneWriterTest,MapManagerCatalogTest,MapManagerTest,MessageHandlerCombatTest,SessionTest,MonsterManagerLifecycleTest,MonsterManagerMovementTest,MonsterManagerRetaliationTest,MonsterManagerRespawnTest' test
  ```

  Expect BUILD SUCCESS, 0 failures/errors. Không dùng kết quả từ lượt extraction trước làm evidence cho code mới.

- [x] **9. Chạy full server gate `mvn test`, rồi `git diff --check` ở repo root.** Expect BUILD SUCCESS, 0 failures/errors và diff check sạch; ghi đúng tổng/pass/skipped từ run mới. Nếu failing gate: sửa nguyên nhân trong scope rồi rerun gate liên quan.
- [x] **10. Báo cáo deliverable và gate thực chạy.** Nêu API/flow/file xóa, kết quả focused/full tests, skipped real-MySQL nếu chưa bật; ghi rõ Unity manual, real DB, production TLS/runtime và load test nào chưa chạy. Automated TLS chỉ báo pass nếu full run mới có test đó. Checkpoint chỉ diff task theo workflow, không push/merge tự động.

## Kết quả cần review

Player.java là điểm bắt đầu đọc luật đánh quái; Monster.java giữ HP/death/AI; Zone.java giữ world/ownership/output. Không còn Combat routing hop hoặc Zone low-level damage hook. Việc Zone còn Player/Monster entrypoints, stable PlayerSaveData capture và world cleanup được chấp nhận rõ trong spec, không mở một lượt tách tiếp chỉ vì import/số dòng.

Khuyến nghị execution trực tiếp trong phiên hiện tại, theo ba task tuần tự và review độc lập ở cuối: các task phụ thuộc cùng API/flow nên không nên sửa song song các file chung. Nếu chọn subagent-driven, triển khai từng task và review trước task kế tiếp; vẫn giữ đúng scope và interface của plan.

## Verification đã thực hiện — 2026-10-04

- Task 1: observed RED do thiếu hai entity API; GREEN PlayerTest + MonsterTest, 31 tests pass.
- Task 2: 41 characterization tests pass trước đổi delegation; sau thay đổi, focused gate 165 tests pass.
- Task 3 wiring/protocol: 97 tests pass, bao gồm pending/replay/malformed và Clock trước enqueue.
- Clean focused gate cuối: 196 tests pass, 0 failures/errors/skips; clean build xác nhận không dựa vào class cũ sau delete/rename.
- Full `mvn test`: BUILD SUCCESS; 519 total, 515 passed, 0 failures/errors, 4 skipped.
- TLS tự động: TlsNetworkIntegrationTest và TlsTcpTransportTest, mỗi suite 1 test pass.
- Review độc lập: không có actionable findings; Zone diff được so với baseline trước phase, giữ extraction ZoneWriter.
- `git diff --check`: pass. Không đổi client, protocol writer, DB schema, MapManager production hoặc ZoneWriter production trong phase này.
- Gate chưa chạy: MySQL thật, Unity manual, production TLS/server runtime, load test.

Các test MySQL opt-in bị skip:

1. AccountAuthDatabaseIntegrationTest.accountCredentialSurvivesAccountAuthRestart.
2. JdbcAccountRepositoryIntegrationTest.persistsAccountAndMapsSuccessfulLoginFields.
3. JdbcPlayerRepositoryIntegrationTest.createsGeneratedIdRejectsDuplicatesAndRoundTripsJson.
4. JdbcPlayerRepositoryIntegrationTest.sqlValidButDomainInvalidRowBecomesControlledLoadFailure.

Logs và ledger còn trong `.superpowers/sdd/2026-10-03-zone-gameplay-ownership/` (git-ignored). Chưa commit/push; giữ toàn bộ diff để người dùng review.
