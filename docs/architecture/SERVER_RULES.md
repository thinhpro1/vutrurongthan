# SERVER RULES V3

> **Phạm vi:** toàn bộ `server/**`. Không áp dụng cho client Unity.
> **Ngày:** 2026-10-10. Thay thế V2.2 (đã chuyển vào `docs/archive/`).
> **Nguồn tham khảo cách viết:** src cũ `rongthan` (`../rongthanchibi`).
> **Mục tiêu:** đọc code gameplay giống đọc src cũ (handler → hành động của nhân vật → báo cho khu vực),
> nhưng an toàn khi nhiều người chơi cùng lúc nhờ **1 Zone = 1 virtual thread**.

Quyết định và lý do nằm ở `docs/architecture/DECISIONS.md`. File này chỉ nói **phải viết thế nào**.

---

## 0. Đọc file này thế nào

- Đây là **hình dạng đích**. Code hiện tại chưa theo hết; mục 16 liệt kê những chỗ chưa chuyển.
- Mỗi lần chạm vào một tính năng, chuyển **trọn tính năng đó** sang hình dạng đích. Không sửa lan sang tính năng khác.
- Các bất biến ở mục 4 (thread) và mục 10 (lưu dữ liệu) luôn đúng, kể cả với code chưa chuyển.
- Không đổi protocol (bytes gửi/nhận với Unity) hay schema DB nếu task không yêu cầu rõ.

---

## 0.1 Người đọc chính là người mới học Java

Chủ dự án không chuyên code. Mọi code gameplay phải đọc được như đọc kịch bản game:

- **Mở đúng một file là sửa được.** Tên file = tên thứ trong game.
- **Tên hàm là hành động trong game**: `attack`, `injure`, `die`, `respawn`, `pickItem`. Đọc tên là đoán được nó làm gì.
- **Mỗi hàm làm một việc, ngắn** (thường dưới ~30 dòng). Dài hơn thì tách thành hàm con có tên game.
- **Công thức để lộ ra một chỗ.** Ví dụ sát thương: `damage()` trong `Player.java`; hồi sinh quái: `respawnDelayMillis()` trong `Monster.java`. Không giấu công thức trong Service/Handler.
- **Không bắt người đọc hiểu thread.** Phần thread/hàng đợi nằm trong `ZoneWriter`; file gameplay không có `synchronized`, `lock`, `Future`, lambda lồng.
- **Comment tiếng Việt ngắn** ở đầu mỗi hàm gameplay không hiển nhiên: nó làm gì trong game.
- Code đúng nhưng người mới đọc không hiểu thì **chưa đạt**.

### Muốn sửa gì thì mở file nào

| Muốn sửa | Mở file | Hàm |
|---|---|---|
| Sát thương, chỉ số, HP/MP của người chơi | `player/Player.java` | `damage()`, `injure()`, `revive()` |
| Người chơi di chuyển, chọn mục tiêu, đánh | `player/Player.java` | `move()`, `useSkill()`, `attack()` |
| Chuyển map, về nhà, dịch chuyển | `player/Player.java` | `requestChangeMap()`, `returnTownFromDead()`, `teleport()` |
| Quái: đi lại, đuổi, đánh, chọn mục tiêu | `monster/Monster.java` | `update()`, `updateMove()`, `updateAttack()`, `findTarget()` |
| Quái: bị đánh, chết, thưởng, hồi sinh | `monster/Monster.java` | `injure()`, `die()`, `respawn()` |
| Dữ liệu quái (máu, dame, tốc độ) | DB / `monster/MonsterTemplate.java` | — |
| Trong một khu vực có gì, chạy theo nhịp nào | `map/Zone.java` | `update()`, `enter()`, `finishLoadMap()`, `leave()` |
| Map có những khu vực nào, waypoint, chọn khu vực | `map/Map.java` | `findZone()`, `findOrRandomZone()`, `findWaypoint()` |
| Đi map, vào game, thoát game | `map/MapManager.java` | `travel()`, `enterGame()`, `leave()` |
| Nhịp update, hàng đợi lệnh (chỉ hạ tầng) | `map/ZoneWriter.java` | — |
| Gói tin gửi cho người chơi khi có sự kiện | `service/AreaService.java` | `monsterAttack()`, `playerMove()`… |
| Bytes của gói tin | `network/packet/*PacketWriter.java` | — |
| Đọc gói tin client gửi lên | `network/handler/*Handler.java` | `handleXxx()` |
| Lưu/đọc DB | `persistence/**/Jdbc*Repository.java` | — |

Thêm tính năng mới thì thêm một dòng vào bảng này.

---

## 1. Mười hai nguyên tắc

1. **Nhân vật là trung tâm.** Hành động của người chơi nằm trong `Player` (hoặc phần con của Player). Mở `Player.java` là biết người chơi làm được gì.
2. **Zone quyết định KHI NÀO, entity quyết định LÀM GÌ.** Zone chỉ chạy tuần tự; không chứa luật chơi của Player/Monster.
3. **Handler chỉ đọc packet rồi chuyển việc.** Không có quyết định gameplay trong Handler.
4. **Entity báo kết quả bằng từ ngữ game qua `zone.service()`.** Service đóng gói và gửi; PacketWriter lo bytes.
5. **Mỗi trạng thái có một chủ.** HP, vị trí, membership, cooldown… chỉ một nơi được sửa.
6. **Không chờ chéo giữa các Zone.** Gửi việc sang Zone khác bằng `post` (không chờ), không bao giờ `call` lồng.
7. **Không I/O chặn trên writer.** JDBC, socket, file, HTTP chạy ngoài Zone.
8. **Không tin client.** Server kiểm tra vị trí, tầm đánh, cooldown, phần thưởng.
9. **Manager quản lý tập hợp, không chứa gameplay.** `init`, `load`, `find`, `create`, `open/close`.
10. **Tách theo mảng gameplay, không tách theo tầng kỹ thuật.** Không `PlayerService`, `MapService`, `XxxProcessor`, `XxxCoordinator`.
11. **Java phổ thông, đọc từ trên xuống.** Kiểm tra trước, thay đổi sau, báo cuối. Ít lambda, ít record, không Stream/Optional trong gameplay.
12. **Xem tính năng tương ứng ở src cũ trước khi viết.** Giữ tên và luồng dễ hiểu; bỏ phần không an toàn (mục 13).

---

## 2. Ai làm gì

| Thành phần | Làm | Không làm |
|---|---|---|
| `MessageHandler` + các `XxxHandler` | đọc/kiểm tra định dạng packet, tìm `Player`, `zone.post(...)` | quyết định gameplay, sửa state |
| `Zone` | giữ `players`, `bosses`, `monsters` (sau: `npcs`, `itemMaps`); `update()`; `post()`; `enter/leave`; `service` | luật chơi của entity |
| `ZoneWriter` | hàng đợi, virtual thread, nhịp update, ngủ/thức | bất kỳ state gameplay nào |
| `Player` | hành động của người chơi; giữ tham chiếu `session` để gửi | parse packet, tạo bytes, JDBC, lock |
| Phần con của Player (`Inventory`, `PlayerTask`, `Trade`…) | một mảng gameplay riêng của Player | tự đi tìm Zone/Manager khác |
| `Character` | phần chung của Player và Boss: vị trí, HP/MP, chỉ số, skill, effect, `injure`, `die`, `useSkill` | phần riêng người chơi (session, túi đồ, nhiệm vụ, lưu) |
| `Boss` | AI: chọn skill, chọn mục tiêu, phase, phần thưởng | — |
| `Monster` | `update`, `updateMove`, `updateAttack`, `findTarget`, `injure`, `die`, `respawn` | gửi bytes, biết Session |
| `Map` | template, các Zone, `findZone`, `findOrRandomZone`, `findWaypoint`, `isWall`, `groundY` | tạo Zone public mới khi đang chơi |
| `MapManager` | danh sách Map public, `findMap`, bật/tắt vòng update, `travel(...)` | hành động khác của Player |
| `Dungeon` | lượt chơi riêng: các Map riêng, thời gian, người tham gia, `update`, `close` | — |
| `XxxManager` | `init/load`, catalog template, `find/create`, mở/đóng nhiều instance | gameplay của một instance |
| `AreaService` (`zone.service()`) | tên hàm theo sự kiện game, gửi cho người trong Zone | quyết định gameplay |
| `XxxPacketWriter` | bytes của packet | gameplay |
| `XxxRepository` | đọc/ghi DB | gameplay |
| `Session` | kết nối, hàng đợi gửi, trạng thái đăng nhập, lưu checkpoint ngoài Zone | là đối tượng gameplay |

---

## 3. Luồng mẫu: Player đánh Monster

Mọi tính năng mới viết theo đúng hình dạng này. Code thật: `CombatHandler`, `Player`, `Monster`, `AreaService`.

```java
// CombatHandler: đọc packet, chuyển việc. Lambda duy nhất của luồng nằm ở đây.
void handleAttack(Message message) throws IOException {
    ... // đọc targetType, targetId, kiểm tra không thừa byte
    Player player = session.player();
    Zone zone = player.zone();
    if (zone == null) {
        return; // đang đi giữa hai Zone
    }
    int monsterId = targetType == TARGET_MONSTER ? targetId : -1;
    long now = clock.millis();
    zone.post(player, () -> player.attack(monsterId, now));
}

// Player.java: chạy trên writer của Zone, không cần lock
public boolean useSkill(int skillId, int monsterId) {   // packet -72: chọn mục tiêu
    focus = null;
    if (monsterId < 0) {
        return false;
    }
    Monster monster = zone.findMonster(monsterId);
    if (!canTarget(monster)) {
        return false;
    }
    focus = monster;
    return true;
}

public boolean attack(int monsterId, long nowMillis) {  // packet -108: ra đòn
    Monster target = focus;
    focus = null;
    if (target == null) {
        return false;
    }
    if (target.id() != monsterId) {
        return false;
    }
    return attackMonster(target, nowMillis);
}

public boolean attackMonster(Monster monster, long nowMillis) {
    if (!canTarget(monster)) {
        return false;
    }
    monster.injure(this, damage(), nowMillis);
    return true;
}

// Monster.java
public void injure(Player attacker, long damage, long nowMillis) {
    if (!isAlive()) {
        return; // writer tuần tự: chỉ một đòn đưa HP về 0
    }
    long hpAfter = Math.max(0L, hp - damage);
    if (hpAfter == 0L) {
        die(attacker, damage, nowMillis);
        return;
    }
    hp = hpAfter;
    enemies.add(attacker.id());
    zone.service().monsterInjure(this, damage);
}

private void die(Player killer, long damage, long nowMillis) {
    status = STATUS_DIE;
    respawnAtMillis = nowMillis + respawnDelayMillis(zone.playerCount());
    zone.service().monsterStartDie(this, damage);
    killer.addPotential(template.potentialReward());
    zone.service().playerPotential(killer);
}

// AreaService.java: gửi cho mọi người trong Zone của Monster; ai đầy hàng đợi thì zone.kick
public void monsterInjure(Monster monster, long damage) {
    sendToAll(monster.zone(), monsterPackets.injure(monster, damage), null);
}
```

Đọc luồng: **4 file** (Handler → Player → Monster → AreaService), không record trung gian, không lock.
Thời gian (`now`) do Handler lấy một lần và truyền vào, để test điều khiển được bằng `Clock`.

---

## 4. Thread và Zone (bất biến)

1. **1 Zone = 1 virtual thread = 1 writer.** Mọi thay đổi state của Zone và entity trong Zone chạy trên writer đó.
2. **Một cửa vào:** `zone.post(player, action)`.
   - Không chờ; hàng đợi có giới hạn; đầy thì bỏ lệnh (client gửi lại được).
   - Trước khi chạy `action`, Zone kiểm tra `player` còn là thành viên **của Zone này**; không còn thì bỏ. Vì vậy Handler đọc `player.zone()` cũ cũng an toàn.
   - Kiểm tra membership chỉ ở cửa này, không lặp trong từng hành động.
3. **Chờ kết quả (`call`) chỉ dùng ở biên hạ tầng** (đăng nhập, thoát game, test). Không bao giờ gọi từ trong một writer.
4. **Vòng update:** `Zone.update(now)` mỗi 100 ms khi Zone còn Player đã tải xong map.
   - Nhịp kế tiếp tính từ lúc nhịp trước kết thúc: input luôn có lượt, nhịp trễ không dồn.
   - Zone trống thì ngủ (thread kết thúc); input mới đánh thức.
   - Mọi bộ hẹn giờ dùng **mốc thời gian tuyệt đối** (`respawnAt`, `effectEndAt`, `cooldownUntil`), để ngủ rồi thức vẫn đúng.
   - Mỗi Zone có `RandomGenerator` riêng.
5. **Không chặn writer:** JDBC, socket, file, HTTP, chờ Zone khác đều chạy ngoài.
6. **Gửi packet không chặn:** `session.trySend`. Hàng đợi gửi đầy thì `zone.kick(player)`; việc đóng Session chạy **ngoài** writer, sau việc hiện tại (`ZoneWriter.later`).
7. **Sang Zone khác:** chỉ `otherZone.post(...)`, không chờ. Không giữ tham chiếu để sửa entity của Zone khác.
8. **Trạng thái dùng chung của nhiều Zone** (Dungeon, sự kiện) có writer riêng và chỉ nhận việc qua `post`.
9. **Thứ tự trong một Zone là thứ tự của writer.** Không dùng timestamp của client để phân xử.
10. **Lỗi trong một hành động hoặc một nhịp** chỉ ghi log; writer chạy tiếp.

---

## 5. Player

- `Player.java` = lõi nhân vật: danh tính, vị trí, HP/MP, `move`, `attackMonster`, `injure`, `die`, `revive`, `requestChangeMap`, `returnTownFromDead`, `goBack`, `teleport`.
- **Tách khi** có một nhóm field + method chỉ làm việc với nhau (túi đồ, nhiệm vụ, giao dịch, bang hội…):
  - tạo class theo tên game (`Inventory`, `PlayerTask`, `Trade`), Player giữ làm field;
  - Handler gọi `player.inventory.useItem(index)` qua `zone.post`;
  - xem lại khi `Player.java` vượt khoảng 1.000–1.500 dòng.
- **Không tách** bằng cách đưa hành động sang Zone, Manager, Service hay Handler.
- `Player.move(x, y)` kiểm tra tường (`map.isWall`) và quãng đường theo thời gian; sai thì kéo về vị trí cũ.
- Player giữ `session` để gửi riêng cho mình (`zone.service().xxx(player)` dùng `player.session()`). Gameplay không đọc trạng thái kết nối của Session.

## 6. Character, Boss, đệ tử

- `Character` (abstract, mỏng) là phần chung: vị trí, HP/MP, chỉ số, `skills`, `effects`, `injure`, `die`, `useSkill`, `zone`.
- `Player extends Character`; `Boss extends Character`; sau này đệ tử cũng `extends Character`.
- Boss lấy dữ liệu từ `BossTemplate` (chỉ số, skill, câu thoại, lịch xuất hiện). **Chỉ boss có cơ chế riêng** (phase, triệu hồi) mới có class con.
- Client hiển thị Boss bằng gói player; `AreaService` gửi Boss như player.
- Zone giữ `players` và `bosses` là hai danh sách riêng.
- `Effects` (choáng, trói, hóa đá, DoT) là một class dùng chung, gắn làm field của `Character` và `Monster`, cập nhật trong `update()`.

## 7. Monster

- `Monster.update(now)`: chết thì xét hồi sinh; sống thì `effects.update` → `updateMove` → `updateAttack` → `findTarget`.
- Monster tự báo qua `zone.service()` (`monsterMove`, `monsterAttack`, `monsterInjure`, `monsterDie`, `monsterRespawn`). Zone không so trạng thái trước/sau để đoán Monster vừa làm gì.
- Quái tinh anh/thủ lĩnh là **field** (`levelBoss`), không phải class con.
- Hằng số AI (tầm đánh, tầm đuổi, tốc độ) đi dần vào `MonsterTemplate`.
- `MonsterManager`: load template, `findTemplate`, `createForMap`. Không chạy vòng lặp, không gameplay.
- Monster bỏ qua Player đang tải map (`player.isLoading()`).

## 8. Map, Zone và đi lại

- `MapManager` tạo đúng `minZone` Zone cho mỗi Map public lúc khởi động. Đang chơi không tạo thêm Zone public.
- `Map.findOrRandomZone()` (giống `findOrRandomZone(-1)` src cũ): Zone đầu tiên còn chỗ, để người đi cùng nhau vào chung khu; tất cả đầy thì Zone ít người nhất. **Sức chứa là mềm**: không bao giờ từ chối người chơi vì đầy.
- **Mọi kiểu đi lại dùng một đường chung.** Player tự tính đích, rồi gọi `maps.travel(this, mapId, x, y)`:
  - `requestChangeMap()` → waypoint (trong dungeon thì hỏi `dungeon.findMap` trước);
  - `returnTownFromDead()` → hồi sinh rồi về nhà;
  - `goBack()` → vị trí đã lưu;
  - `teleport(...)` → đích chỉ định.
- **`travel` chạy trên writer của Zone nguồn:**
  1. kiểm tra lại điều kiện; chọn Zone đích;
  2. `player.startTravel(destination)` (để thoát game giữa đường vẫn tìm thấy Player);
  3. `source.leave(player)` (báo người còn lại);
  4. đặt vị trí mới, `session.saveLater(...)` (lưu ngoài writer, đúng thứ tự);
  5. `destination.postEnter(player)`, không chờ.
- **`Zone.enter(player)` chạy trên writer của Zone đích:**
  1. Session đã đóng → không vào, kết thúc chuyến đi (lưu cuối do `close()` làm, mục 10);
  2. cùng nhân vật đang ở đây bằng kết nối khác → kick kết nối mới;
  3. thêm vào `players`, đánh dấu `loading`;
  4. gửi `MAP_INFO` dựng từ dữ liệu đang sống (giống `Zone.enter → setMapInfo` của src cũ).
- `FINISH_LOAD_MAP` → `zone.finishLoadMap(player)`: bỏ cờ `loading`, Player và người trong Zone thấy nhau (`ADD_PLAYER` hai chiều).
- Đang `loading`: lệnh qua `post` bị bỏ, quái không đánh, người khác chưa thấy.
- Trong lúc đi giữa hai Zone, Player **thuộc về chuyến đi**, không Zone nào sửa nó. Thoát game giữa chừng thì Zone đích xử lý ở bước enter (1).

## 9. Dungeon

- `Dungeon` theo kiểu `Expansion` của src cũ:
  - giữ các Map riêng (tạo từ `MapTemplate` dùng chung, không nằm trong danh sách Map public);
  - giữ thời hạn và người tham gia;
  - có `update()` và `close()`.
- `DungeonManager`: tạo, tìm, đóng các lượt dungeon.
- Mỗi loại dungeon có luật riêng thì `extends Dungeon`.
- **Mỗi Dungeon có writer riêng** cho trạng thái chung. Zone báo bằng `dungeon.post(() -> dungeon.finishMap(mapId))`.
- Player có field `dungeon`; hết giờ hoặc thua thì `dungeon.close()` gửi từng Player về nhà bằng `travel`.

## 10. Lưu dữ liệu (bất biến)

- Nguồn sự thật lúc đang chơi: entity trong Zone. DB là checkpoint.
- Luồng lưu: **trên writer** chụp `PlayerSaveData` → **ngoài writer** `Repository` ghi DB.
- Thoát game, theo đúng thứ tự:
  1. xử lý xong việc đang chờ của Zone;
  2. tách Player khỏi Zone;
  3. chết thì hồi sinh về nhà;
  4. chụp `PlayerSaveData`;
  5. lưu DB ngoài Zone;
  6. nhả tài khoản cho lần đăng nhập mới.
- Lần lưu thường và lần lưu cuối dùng chung một thứ tự; Session đã đóng thì lần lưu đến muộn bị bỏ.
- Gameplay không biết SQL. Entity không tự lưu.

---

## 11. Cách viết Java

```java
// Nên: kiểm tra trước, thay đổi sau, báo cuối; mỗi điều kiện một dòng
if (isDead()) {
    return;
}
if (!map.isWall(x, y)) {
    this.x = x;
    this.y = y;
}
zone.service().playerMove(this);

// Tránh: gộp kiểm tra và thay đổi trong một điều kiện
if (player == null || player.isDead() || !player.move(x, y)) { ... }
```

- Early return; kiểu rõ ràng (không `var`); vòng `for` thường.
- Không `Optional`, `Stream` trong gameplay. `Optional` chỉ ở biên Repository nếu thật cần.
- **Lambda chỉ ở biên thực thi** (`zone.post(...)`, `dungeon.post(...)`). Không lồng lambda.
- **Record chỉ ở biên**: `XxxTemplate`, `PlayerSaveData`, dữ liệu đọc từ DB/file, `Message`. Không tạo `XxxResult`, `XxxContext`, `XxxSnapshot` để chuyền kết quả trong gameplay.
- Không `synchronized`/lock trong gameplay. Lock chỉ ở hạ tầng (Session, writer).
- File lớn mà cùng một chủ đề thì được phép; không tách chỉ vì số dòng.
- Comment giải thích **vì sao**, viết tiếng Việt hoặc tiếng Anh đều được, ngắn.

## 12. Đặt tên

- Dùng từ của game và của src cũ: `update`, `move`, `attack`, `injure`, `die`, `respawn`, `revive`, `enter`, `leave`, `findTarget`, `requestChangeMap`, `returnTownFromDead`, `teleport`, `addEffect`, `addItem`.
- Class đã nói ngữ cảnh thì method không lặp lại: trong `Monster` là `attack()`, không phải `monsterAttack()`.
- Riêng `AreaService` đặt tên theo sự kiện kèm chủ thể (`monsterAttack`, `playerMove`), vì một service gửi cho nhiều loại entity.
- `findX` = tìm, có thể null, không tạo. `getX` = phải có, thiếu thì lỗi. `createX` = tạo mới.
- Không dùng tên kỹ thuật thay hành động game: `Processor`, `Coordinator`, `Orchestrator`, `Facade`, `Operation`, `Transition`, `Resolution`.

## 13. Không chép từ src cũ

- Singleton toàn cục (`MapManager.getInstance()`), state toàn cục dùng chung.
- `Zone extends Thread` (platform thread), lock trên từng entity, `ReadWriteLock` chồng.
- Entity parse `Message` hoặc tạo bytes; entity gọi DB.
- `Player.java` vạn dòng: tách theo mục 5.
- `Boss extends Player`: dùng `Character` (mục 6).
- Hằng số phát tán, `Utils` khổng lồ.

## 14. Test

- Test qua hành vi quan sát được: packet gửi ra, state của entity, DB checkpoint.
- Test concurrency điều khiển thứ tự bằng latch/barrier, không dựa `Thread.sleep` để đồng bộ.
- Lỗi concurrency phải có test **tái hiện được** (fail trước khi sửa, pass sau khi sửa).
- Không mở rộng API production chỉ để test. Truy cập package-private cho test tập trung là được; tránh reflection.
- Bắt buộc có test cho: chuyển map, Zone đầy, chết rồi thoát, thoát giữa chuyến đi, nhiều người đánh một Monster (một người kết liễu), lưu/tải.
- Cổng chung: `mvn test` trong `server/` phải xanh trước khi push.

## 15. Quy trình một task

1. Đọc file này; mở tính năng tương ứng ở src cũ (ghi lại 1–2 dòng: file nào, tên/luồng nào giữ).
2. Viết theo luồng mẫu (mục 3).
3. Thêm/sửa test cho đúng hành vi đổi.
4. `mvn test` xanh.
5. Báo ngắn: đã đổi gì, test nào, **những gì chưa chạy** (DB thật, Unity, tải).

Code chạy đúng nhưng khó đọc hơn src cũ mà không có lý do correctness thì **chưa đạt**.

---

## 16. Phần code chưa theo rule (chuyển dần, theo thứ tự)

| Hiện tại | Đích | Mục |
|---|---|---|
| ~~`Zone` giữ `Session`; mỗi hành động là một method trên Zone~~ | **Đã chuyển (2026-10-10):** Zone giữ `Player`; Handler → `zone.post` → `Player.move/useSkill/attack` | 2, 3, 4 |
| ~~`Monster` trả `Damage/Attack/Move/Respawn`; Zone so trước/sau để gửi packet~~ | **Đã chuyển (2026-10-10):** Monster gọi `zone.service()` | 7 |
| ~~`MAP_INFO` gửi từ Handler, đọc Monster qua `Monster.Snapshot`~~ | **Đã chuyển (2026-10-10):** `Zone.enter` gửi `MAP_INFO` từ dữ liệu sống | 8 |
| ~~Chuyển map: giữ chỗ ở đích + `Trip` + vào Zone lúc `FINISH_LOAD_MAP`~~ | **Đã chuyển (2026-10-10):** `travel`: leave nguồn → `postEnter` đích | 8 |
| ~~`MapManager.changeMap/returnHomeFromDeath`~~ | **Đã chuyển (2026-10-10):** `Player.requestChangeMap/returnTownFromDead` → `MapManager.travel` | 5, 8 |
| `MonsterManager.start/stop` bật vòng update của Zone | `MapManager.start/stop` | 7, 8 |
| ~~`Zone` dùng `synchronized` cho truy vấn từ thread khác~~ | **Đã chuyển (2026-10-10):** phần thread nằm trong `ZoneWriter`; Zone không còn `synchronized` | 11 |
| `Player.move` nhận mọi tọa độ; đánh không kiểm tra tầm/cooldown | kiểm tra tường, quãng đường, tầm, cooldown | 5, 3 |
| Chưa có `Character`, `Boss`, `Effects`, `Dungeon`, `goBack`, `teleport` | theo mục 5–9 | — |

Thứ tự đề xuất: ~~luồng mẫu (move + attack monster, Zone giữ Player)~~ (xong) → ~~travel + `MAP_INFO` khi enter~~ (xong) → kiểm tra di chuyển/tầm/cooldown → `Character` + `Effects` → Boss → Dungeon.
