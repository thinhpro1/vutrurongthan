# Zone / Player / Monster gameplay ownership

**Trạng thái:** Đã được người dùng duyệt và triển khai code ngày 2026-10-04; kết quả verification ghi trong plan tương ứng.

**Mục tiêu:** Đọc combat từ `Player.java`; đọc world/membership từ `Zone.java`; giữ đúng execution, protocol và persistence hiện tại.

## 1. Căn cứ và giới hạn

- `docs/architecture/SERVER_RULES.md` là authority; runtime baseline là hướng migration đã được duyệt.
- Hai quyết định đã chốt: điều phối public travel ở `MapManager`; mỗi `Zone` có đúng một `ZoneWriter` package-private.
- Extraction `ZoneWriter` đang có trong working tree là điểm xuất phát. Không ghi đè hoặc làm lại phần đó.
- Tài liệu `RONGTHANCHIBI_REFACTOR_BASELINE.md` do người dùng cung cấp là bối cảnh. Không lấy mô tả game loop trong đó làm lệnh đổi scheduler hiện tại.
- Phase này sửa ownership/readability của Player đánh Monster. Không mở một đợt viết lại Map, Monster AI hoặc Player persistence.

Các ràng buộc áp dụng nguyên văn cho plan:

- Java 21; Maven; JUnit 5.11.0; không thêm dependency.
- Giữ public travel trong MapManager và đúng một ZoneWriter package-private cho mỗi Zone.
- Giữ protocol bytes, DB schema, persistence ordering và gameplay constants hiện tại.
- Zone.members dùng exact Session identity làm membership authority; Session.zone chỉ là routing backlink.
- Mọi mutation của Player/Monster đang thuộc Zone chạy trong writer của Zone đó.
- Không blocking I/O trong Zone writer; không nested cross-Zone wait.
- Không đổi queue capacity, overload policy, synchronization hoặc cơ chế ACTIVE/FROZEN/STOPPED.
- Không thêm CombatService, CombatManager, PlayerContext, Entity base hoặc result/snapshot mới.
- Không sửa Unity/client.

## 2. Implementation đã kiểm tra

| Hiện tại | Vấn đề / trách nhiệm |
|---|---|
| `Player.java` | Đã có move/injure/revive/addPotential; còn thiếu hành vi đánh Monster. |
| `Monster.java` | `injure` đã sở hữu HP/death/enemies/respawn deadline; `update` đã sở hữu AI. |
| `Zone.canTargetMonsterOnWriter` | Trộn exact membership với luật Player/Monster còn sống. |
| `Zone.attackMonsterOnWriter` | Trộn ownership/lookup/output với damage và cộng tiềm năng cho Player. |
| `Combat.java` | Chỉ giữ Clock và chuyển lời gọi sang Zone; không có state hay luật combat. |
| `CombatHandler.java` | Giữ prepare/impact pending state, parse packet; gọi Combat. |
| `Zone.leave` | Chụp PlayerSaveData ổn định trước persistence ngoài writer; boundary hợp lệ. |
| `Zone.damageMonster` / `hasLiveMonster` | Chỉ có caller trong tests; đường mutate thấp cấp không cần cho production. |

## 3. Legacy reference đã kiểm tra

Root: `D:/doan11/rongthanchibi/rongthanchibi`.

- `entity/player/Player.java`: `useSkill`, `attackMonster`; Player quyết định hành vi tấn công.
- `entity/monster/Monster.java`: `injure`, `update`; Monster giữ HP/death và lifecycle.
- `map/Zone.java`: `run`, `update`, `enter`, `leave`, `findMonsterById`; Zone giữ world lookup và luồng thế giới.
- `service/AreaService.java`: `monsterInjure` và gửi/broadcast kết quả.

Adopt: luồng lookup → Player.attackMonster → Monster.injure; tên game ngắn; entity giữ hành vi; Service giữ gửi packet.

Reject: singleton, lock-per-entity, packet/Session coupling trong entity, thread-per-Zone cũ và các công thức skill/range/critical chưa có trong gameplay hiện tại. Manager catalog/lifecycle hiện tại không chuyển thành nơi tính combat.

## 4. Ownership và interfaces đích

```text
CombatHandler: parse prepare/impact, giữ pending, chọn candidate Zone
→ Zone.canTargetMonster / Zone.attackMonster
→ ZoneWriter: serialize
→ Zone: exact membership + live Monster lookup
→ Player.canTarget / Player.attackMonster
→ Monster.injure
→ Zone + AreaService: gửi kết quả theo writer order
```

Entity APIs mới trong `Player.java`:

```java
public boolean canTarget(Monster monster);
public Monster.Damage attackMonster(Monster monster, long nowMillis, int playerCount);
```

- `canTarget`: Player còn sống, Monster khác null và còn sống. Không xét Session, membership hoặc damage > 0.
- `attackMonster`: kiểm tra canTarget và damage > 0; gọi `Monster.injure(id, currentStats.damage, nowMillis, playerCount)`; cộng reward dương cho chính Player khi result là lethal; trả lại `Monster.Damage` hiện có hoặc null nếu không đánh được.
- `Monster.Damage` giữ nguyên: đây là dữ liệu sự kiện đang dùng để gửi packet. Không tạo AttackResult mới.
- Các entity method không tự enqueue và không biết Session/Zone/Repository/AreaService. Caller phải đang sở hữu execution của cả hai entity; fixture chưa tham gia runtime được test trực tiếp.

Zone APIs giữ nguyên:

```java
public boolean canTargetMonster(Session session, int monsterId);
public boolean attackMonster(Session session, int monsterId, long nowMillis);
```

Hai private action đặt ngay dưới entrypoint tương ứng:

```java
private synchronized boolean canTarget(Session session, int monsterId);
private synchronized boolean attack(
        Session session, int monsterId, long nowMillis, List<Session> rejected);
```

Zone chỉ kiểm tra CLOSED/null Player/backlink/exact membership, tìm Monster trong collection, gọi Player và gửi kết quả. Không trực tiếp đọc damage để áp dụng đòn đánh, không trực tiếp gọi `Monster.injure`, không cộng reward lần hai.

Reward state được cập nhật trong `Player.attackMonster` trước output; packet vẫn theo thứ tự injury/death broadcast → potential gửi riêng finisher. `AreaService` chỉ enqueue không blocking; close rejected Sessions vẫn sau khi writer trả về.

Protocol composition:

- Xóa `Combat.java`; constructor `CombatHandler(Session session, Clock clock)`.
- Thay component `Combat combat` của `SessionServices` bằng `Clock clock`; inject `Clock.systemUTC()` từ `ServerBootstrap`; `MessageHandler` truyền `services.clock()`.
- Handler đọc candidate `session.zone()`, kiểm tra no-zone/CLOSED rồi gọi Zone. Không đọc HP/damage hoặc quyết định reward.
- Lấy `clock.millis()` ngay trước gọi `zone.attackMonster`, trên request caller trước enqueue, như hiện tại. Server Clock quyết định thời gian deadline; writer order quyết định thứ tự hit/finisher.
- Pending vẫn bị consume trước xử lý impact; replay/mismatch/map change/malformed payload giữ nguyên.

Đổi tên có chủ đích: `canTargetMonsterOnWriter` → `canTarget`, `attackMonsterOnWriter` → `attack`. Writer boundary đã thể hiện ở entrypoint và ZoneWriter; helper chỉ cần tên hành động. Tên legacy `Player.attackMonster` và `Monster.injure` được giữ.

## 5. Những phần Player/Monster hợp lệ vẫn ở Zone

| Phần | Quyết định và lý do |
|---|---|
| enter / move / leave | Giữ entrypoint + ownership/output; Player đã giữ thay đổi state. |
| PlayerSaveData.capture trong leave | Giữ stable save boundary; PlayerManager/Repository lưu ngoài writer. |
| updateMonsters | Giữ traversal, world input, dispatch output; Monster.update giữ AI/lifecycle. |
| hostileLivingPlayers | Giữ exact live membership filter; Monster giữ chọn target/range/chase. |
| Dọn enemy ở các Monster khi Player chết | Giữ world cleanup giữa nhiều runtime objects trong cùng writer. |
| monsterSnapshots | Giữ immutable MAP_INFO/external read boundary. |
| reserve / admission / collections | Giữ world ownership. |
| damageMonster / hasLiveMonster | Xóa; tests dùng semantic attack hoặc fixture entity trong writer. |

Không đưa thêm Player/Monster method vào ZoneWriter. Không xuất public live Monster lookup chỉ để tests lấy entity.

## 6. Hành vi phải giữ

- Prepare không gây damage; Player damage = 0 vẫn có thể target Monster sống, impact không damage/reward/packet.
- Unknown/dead Monster, dead Player, CLOSED/non-member Session hoặc stale backlink bị từ chối mà không mutate.
- Hai Session cùng Player ID không thay thế được identity của member đang có.
- Canonical Monster #101 HP 300; damage mặc định 10; potential ban đầu 1; reward lethal 10; chỉ finisher nhận tổng 11; saturation Long.MAX_VALUE.
- Thứ tự A rồi B với HP 100, damage 60/70: B finisher. B rồi A: A finisher. Later hit không death/reward lần hai.
- Respawn delay `max(10_000 - 1_000 * memberCount, 5_000)` lấy member count tại lethal hit; respawn chỉ khi `now > deadline`.
- Same-Zone broadcast bytes giữ nguyên; Zone khác không nhận; potential chỉ gửi finisher sau death broadcast.
- Queue-full/STOPPED combat bị từ chối; accepted work không chạy trùng; rejected sends không close trong writer.
- Disconnect save gồm reward/HP/location mới nhất sau các action đã được writer xử lý; save ngoài writer rồi mới release account reservation.
- Writer hiện tại drain queue rồi FROZEN, không phải periodic game loop; MonsterManager giữ lifecycle trigger 100 ms.

## 7. Tiêu chí kết thúc để tránh vòng refactor

1. Mở Player.java thấy luật Player đánh Monster; mở Monster.java thấy HP/death/AI; mở Zone.java thấy world action và call entity.
2. Gameplay chain chỉ cần Player + Monster; request chain thêm CombatHandler + Zone. Writer chỉ cần đọc khi sửa concurrency; Service/packet writer khi sửa output.
3. Không còn Combat router, low-level Zone damage hook hoặc reward/damage algorithm trong Zone.
4. Các entrypoint combat mỏng, PlayerSaveData và world cleanup còn trong Zone là kết quả đúng ownership, không phải debt cần một lần tách nữa.
5. Focused regressions và full Maven gate pass; mọi gate không chạy được ghi rõ.
6. Không dùng số dòng hoặc việc Zone còn import Player/Monster để quyết định tách tiếp. Mở rộng scope cần vấn đề cụ thể và task riêng.
