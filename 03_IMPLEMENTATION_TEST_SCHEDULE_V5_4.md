# IMPLEMENTATION & CONTINUOUS TEST SCHEDULE — V5.4

> Baseline: 1 người, 2–4 giờ/ngày, 5–6 ngày/tuần.
>
> Timeline tham chiếu: **13 tuần** để thêm NPC + Upgrade mà không dồn quá nhiều feature vào cùng một tuần.
>
> V5 giữ kiến trúc dài hạn của Server V7; không cắt WorldCommand, PacketWriter, security/install identity. Chỉ điều chỉnh implementation để tránh blocking và tách Core/Extension rõ hơn.
>
> **Network plan chi tiết riêng:** `04_NETWORK_IMPLEMENTATION_PLAN_V2.md`.

---

# 1. Quality Gates

```text
G0  Project + DB
G1  Legacy Network Compatibility
G2  Auth/Create Player
G3  PlayerInfo + MapInfo
G4  Multiplayer + GameLoop
G5  TLS + Admission
G6  Monster/Combat
G7  Item/Shop/NPC
G8  Upgrade + Quest
G9  5 Boss + Daily/GiftCode
G10 Clan
G11 Clan Dungeon
G12 Cleanup + Benchmark + Security
```

BossEvent/Arena/Survival không còn là Core gate.

---

# TUẦN 1 — Project skeleton + protocol audit

- Java 21.
- Spring Boot/Maven.
- MySQL/JPA/Hikari.
- base config.
- đọc lại client Session/Message.
- đọc network source cũ.
- xác nhận protocol table.
- client original build.

Test:
- server boot.
- DB connection.
- original Unity client compile.

---

# TUẦN 2 — Legacy Network Compatibility

Làm theo `04_NETWORK_IMPLEMENTATION_PLAN_V2.md`.

Core:
- Message.
- MessageReader/Writer.
- SessionCipher.
- LegacyPacketCodec.
- Session.
- SessionManager.
- MessageHandler.
- GameServer.
- legacy transport.
- bounded outbound queue.
- protocol tests.
- Unity handshake test.

Gate:

```text
Unity old client
→ TCP connect
→ key
→ VERSION_SOURCE
→ UPDATE_DATA bootstrap
→ LoginScreen
```

G1.

---

# TUẦN 3 — Register/Login/Create Player

- UserEntity/Repository.
- BCrypt.
- AuthService.
- Register.
- Login.
- duplicate login.
- PlayerEntity.
- Player runtime.
- PlayerMapper.
- Create Player.
- Session state: AUTHENTICATED/IN_GAME.

Tests:
- wrong password.
- duplicate username.
- valid login.
- duplicate session.
- create/relogin.
- password never logged.

G2.

---

# TUẦN 4 — PlayerInfo + Map runtime

- PlayerPacketWriter.
- PLAYER_INFO compatibility.
- MapTemplate cache.
- MapInstance.
- MapInstanceManager.
- MapService.
- Zone.
- MapPacketWriter.
- MAP_INFO.
- NPC template cache (`UPDATE_DATA` subtype 4).
- NPC spawn data inside MAP_INFO.
- FINISH_LOAD_MAP.

Responsibility test:
- MapInstanceManager only registry/runtime.
- MapService handles enter/leave/change.

Isolation test:
- create 2 MapInstance same template.
- no shared Zone/entity collection.

G3.

---

# TUẦN 5 — Movement + WorldCommandQueue + GameLoop

- WorldCommand interface.
- MoveCommand.
- ChangeMapCommand.
- ChangeZoneCommand.
- WorldCommandQueue.
- GameLoop ~100ms.
- movement.
- map/zone change.
- enter/leave broadcast.
- set-position correction.

Tests:
- network thread không mutate world.
- command order.
- 2–5 clients.
- no cross-instance broadcast.
- disconnect cleanup.

G4.

---

# TUẦN 6 — TLS + Admission

## Transport
- ClientTransport abstraction.
- LegacyTcpTransport đã chạy.
- TlsTcpTransport server.
- C# SslStream.
- certificate validation.

## Admission
- BuildVersionService.
- InstallationService.
- ConnectionTicketService.
- ConnectionTicketStore.
- InMemoryConnectionTicketStore.
- Auth/Admission API.
- client switch `LEGACY_DEV` → `SECURE_ONLINE`.
- HTTPS Login/Register becomes production auth path.
- disable legacy TCP username/password LOGIN/REGISTER in `SECURE_ONLINE`.
- ticket consume one-time.
- session-state validation.
- rate limit basics.

Tests:
- valid TLS.
- bad cert.
- wrong hostname.
- invalid build.
- valid/expired/replay ticket.
- revoked installation.
- legacy XOR vẫn chạy bên trong TLS.

G5.

---

# TUẦN 7 — Monster/Combat/Skill

- Monster runtime.
- cached MonsterTemplate.
- spawn.
- AI.
- AttackCommand.
- SkillCommand.
- CombatService.
- range/cooldown/mana validation.
- death/respawn.
- Monster/Combat PacketWriter khi feature cần.

Tests:
- invalid target.
- out of range.
- cooldown cheat.
- fake damage ignored.
- monster lifecycle.

G6.

---

# TUẦN 8 — Item/Inventory/Shop + NPC Interaction

## Item/Inventory
- Player inventory runtime.
- persistence.
- ItemDrop.
- PickItemCommand.
- dirty autosave.

## Shop
- ShopService.
- server price authority.
- transaction.

## NPC
- NpcTemplate/Npc runtime.
- NpcService.
- OPEN_NPC `-93`.
- CONFIRM_MENU `-92`.
- NPC_CHAT `-91`.
- ADD_NPC `-48`.
- REMOVE_NPC `-47`.
- NPC menu routes tới Shop/Quest/Upgrade.

Tests:
- NPC template update.
- NPC appears in MAP_INFO.
- open menu.
- select menu.
- NPC chat.
- shop through NPC.
- double pickup.
- fake shop price.
- GameLoop not blocked by DB.

G7.

---

# TUẦN 9 — Upgrade + Quest

## Upgrade common core
- Upgrade interface.
- UpgradeType.
- UpgradeManager (`List<Upgrade>` registry).
- minimal PendingUpgrade:
  - type;
  - itemIndexes;
  - createdAt.
- Player `currentUpgradeType`.
- Player `upgradeLock`.

Không tạo:
- UpgradePreview.
- UpgradeOption.
- UpgradeResult framework.
- handler/factory/strategy layer.

## Upgrade mechanics

### 1. UpgradeItem
- server `UpgradeItem.java`.
- Unity `UpgradeItemPanel.cs`.
- layout dự kiến 3 ô:
  - equipment;
  - upgrade stone;
  - protection stone.
- level/cost/rate/fail rule.

### 2. UpgradeStar
- server `UpgradeStar.java`.
- Unity `UpgradeStarPanel.cs`.
- star cap/material/cost/rate.
- layout chốt theo material thật.

### 3. DrillSocket
- server `DrillSocket.java`.
- Unity `DrillSocketPanel.cs`.
- layout dự kiến 2 ô:
  - equipment;
  - drill material.
- max socket/material/cost/rule.

### 4. UpgradeQuality
- server `UpgradeQuality.java`.
- Unity `UpgradeQualityPanel.cs`.
- layout dự kiến 5 ô:
  - equipment;
  - material 1;
  - material 2;
  - material 3;
  - protect/extra.
- quality transition/recipe/cost/rule.

### 5. Upgrade mechanic thứ 5
- chốt gameplay trước khi implement.
- sau khi chốt:
  - thêm 1 UpgradeType;
  - thêm 1 server class;
  - thêm 1 client panel nếu layout khác.
- không tạo placeholder logic giả.

## Upgrade lifecycle
- NPC opens mechanic.
- `showTab()` sets `currentUpgradeType`.
- server `-27` sends `uiType`.
- Unity opens corresponding panel.
- panel sends selected `indexUI` via `UPGRADE -37`.
- mechanic `preview()` validates/calculates.
- save minimal PendingUpgrade.
- server creates confirm menu.
- client `CONFIRM_MENU -92`.
- mechanic `confirm()` validates again.
- per-player upgradeLock.
- DB transaction.
- consume material/currency.
- server RNG/result where applicable.
- commit then runtime sync.
- clear PendingUpgrade.

## Tests
- `-27` opens correct panel for each implemented mechanic.
- panel slot count/layout correct.
- `-37` contains item indexes only.
- currentUpgradeType routes to correct class.
- invalid item types rejected server-side.
- preview consumes nothing.
- stale PendingUpgrade rejected.
- inventory changes before confirm are detected.
- selected confirm index validated.
- two confirms on same player serialized.
- transaction rollback preserves item/currency.
- result generated server-side.
- relogin retains:
  - upgrade level;
  - star;
  - socket count;
  - quality.

## Quest
- Quest DB/model.
- PlayerQuest.
- small EventBus.
- ~10 quests.
- NPC TALK.
- KILL/COLLECT/LEVEL/DUNGEON.
- ItemUpgradedEvent only if a real quest/achievement needs it.

G8.

---


# TUẦN 10 — 5 Boss + Daily + GiftCode

## Boss
- Earth.
- Fire.
- Ice.
- Demon.
- Dragon.
- server authoritative mechanics.

## Daily/GiftCode
- reward tables/service.
- duplicate/idempotency protection.

Tests:
- each boss mechanic.
- boss quest.
- Daily duplicate.
- GiftCode duplicate/concurrent claim.

G9.

---

# TUẦN 11 — Clan

- Clan model.
- ClanService.
- create/join/leave.
- leader/member role.
- Clan protocol.
- client Clan UI simplify.
- permission checks.

Tests:
- duplicate membership.
- leader rules.
- join/leave.
- reconnect.
- concurrent clan operations where relevant.

G10.

---

# TUẦN 12 — Clan Dungeon

- ClanDungeon.
- DungeonState.
- DungeonService.
- dungeon_runs.
- dungeon_participants.

Nếu nhiều map:

```java
public static final List<Integer> MAP_IDS = List.of(
    MapName.CLAN_GATE,
    MapName.CLAN_INSIDE,
    MapName.CLAN_BOSS
);
```

Runtime:
- create MapInstance.
- MapService enter/change.
- participant validation.
- timer.
- waves.
- boss.
- completion.
- reward transaction.
- daily/history.

Reconnect V1:

```text
disconnect in dungeon
→ reconnect
→ safe map
```

Tests:
- leader open.
- member enter.
- other clan reject.
- same template isolation.
- waves/boss/reward.
- daily limit.
- cleanup.
- common client renderer.

G11.

---

# TUẦN 13 — Cleanup + Load + Security + Report

## Client cleanup
- remove/disable unsupported old feature.
- hide unsupported upgrade modes.
- keep NPC/common renderer/network.

## Security final
- real TLS cert/domain.
- no accept-all.
- replay/build/install tests.
- packet/rate-limit tests.

## Load
- 100 required.
- 250.
- 500.
- 1000.
- 1500 if stable.

## Metrics
- sessions.
- CPU.
- RAM/heap.
- GC.
- GameLoop p50/p95/p99.
- command queue depth.
- command count/tick.
- packets/sec.
- DB active/query latency.
- upgrade transaction latency/error rate.
- TLS/admission latency.

Single GameLoop warning:
- p95 70–90ms → profile.
- p95 >=100ms → fix bottleneck before scaling further.

G12.

---

# 2. Extension backlog sau Core

Không chặn đồ án:

## E1 Scheduled BossEvent
- scheduled start/end.
- fixed map/map list.
- common renderer.

## E2 Dungeon Rejoin V2
- find active dungeon_run.
- verify participant.
- instance still alive.
- no reward claimed.
- restore runtime location.

## E3 Personal/Party Dungeon
- class riêng trước.
- extract Dungeon abstraction khi code lặp rõ.

## E4 Arena/Survival
- reuse MapInstance/Zone.
- game mode class riêng.

## E5 Ticket backend
- DatabaseConnectionTicketStore.
- RedisConnectionTicketStore nếu multi-node.

---

# 3. Continuous regression

Sau mỗi milestone:

```text
build server
→ build client
→ login
→ map
→ movement
→ disconnect
```

Sau combat:

```text
login
→ map
→ attack
→ kill monster
→ pickup
→ logout/relogin
```

Sau security:

```text
valid official flow
→ invalid build
→ expired ticket
→ replay ticket
→ revoked install
```

---

# 4. Core Definition of Done

- [ ] Legacy protocol compatible.
- [ ] Auth/Create Player.
- [ ] PlayerInfo/MapInfo.
- [ ] MapTemplate/MapInstance/Zone.
- [ ] MapService vs MapInstanceManager responsibility clear.
- [ ] WorldCommandQueue/GameLoop.
- [ ] TLS/admission.
- [ ] Installation identity.
- [ ] In-memory one-time ticket store.
- [ ] Monster/Combat/Skill.
- [ ] Item/Shop without DB blocking tick.
- [ ] NPC template/runtime/menu/chat.
- [ ] NPC integrated with Shop/Quest/Upgrade.
- [ ] Upgrade lifecycle showTab/preview/confirm.
- [ ] Minimal PendingUpgrade + per-player upgrade lock.
- [ ] UpgradeItem + UpgradeStar + DrillSocket + UpgradeQuality.
- [ ] Separate Unity panel per differing mechanic layout.
- [ ] Fifth mechanic added only after its gameplay is defined.
- [ ] Upgrade confirm transaction + server-authoritative result.
- [ ] Quest + 5 Boss.
- [ ] Daily/GiftCode.
- [ ] Clan.
- [ ] ClanDungeon.
- [ ] dungeon history/participants.
- [ ] Dungeon reconnect V1 safe map.
- [ ] autosave/shutdown.
- [ ] client cleanup.
- [ ] >=100 simulated clients.
- [ ] report metrics/security tests.

---

# 5. Không bắt buộc Core

- Scheduled BossEvent.
- Arena.
- Survival.
- Personal/Party Dungeon.
- Dungeon rejoin.
- Redis ticket backend.
- multi-node.
