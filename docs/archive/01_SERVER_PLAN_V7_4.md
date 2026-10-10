# GAME SERVER PLAN — V7.4

> **Baseline dài hạn của project.**
>
> V7 giữ gần như toàn bộ V6. Chỉ sửa những điểm thật sự cần thiết sau khi rà lại:
>
> 1. Làm rõ `MapInstanceManager` và `MapService`.
> 2. Sửa flow ItemDrop để không bắt GameLoop chờ DB.
> 3. Giữ `ConnectionTicketService`, nhưng ticket store mặc định dùng RAM và có interface để đổi backend.
> 4. Reconnect Dungeon chia rõ V1 và V2.
> 5. `BossEvent` vẫn giữ trong kiến trúc, nhưng không còn là Core Definition of Done.
>
> Các phần dài hạn như Java 21, Virtual Threads, WorldCommandQueue, PacketWriter theo domain, TLS, build gate, installation identity, transaction/idempotency **vẫn giữ**.

---

# 1. Scope

## Core bắt buộc
- Register / Login / Create Player.
- Player load/save.
- Session online/offline.
- Map / Zone / Movement.
- Monster / Combat / Skill.
- Item / Inventory / ItemDrop.
- NPC template/runtime + NPC interaction/menu.
- Shop.
- Upgrade/Enhancement system.
- ~10 Quest.
- 5 Boss.
- Daily Login 7 ngày.
- GiftCode.
- Clan.
- Clan Dungeon.
- Autosave / Graceful shutdown.
- Benchmark >=100 simulated clients.
- TLS + admission security cho online/demo.

## Extension sau Core
- Personal Dungeon.
- Party Dungeon.
- Scheduled Boss Event.
- Arena.
- Survival/Event map.
- Dungeon reconnect/rejoin nâng cao.

---

# 2. Stack

- Java 21.
- Maven.
- Spring Boot 3.x.
- Spring Data JPA.
- HikariCP.
- MySQL 8.
- BCrypt.
- ServerSocket / SSLSocket.
- Java Virtual Threads.
- TLS 1.3.
- Một GameLoop chính ~100ms.
- JUnit.

Không dùng V1:
- Microservices.
- Redis bắt buộc.
- Kafka.
- Kubernetes.
- Netty.
- Multi-node.

Redis có thể là backend tương lai nếu ticket/session cần multi-node, nhưng không phải dependency hiện tại.

---

# 3. Map core

```text
MapTemplate
   ↓
MapInstance
   ↓
Zone
```

`MapTemplate`:
- dữ liệu map tĩnh/cache;
- tile/background;
- waypoint;
- NPC template;
- monster spawn template;
- collision;
- zone count.

`MapInstance`:
- một bản runtime từ MapTemplate;
- có thể là map world bình thường hoặc map riêng;
- không chứa trực tiếp Dungeon/Arena/BossEvent state.

```java
public class MapInstance {

    private long instanceId;

    private MapTemplate template;

    private List<Zone> zones;

    private long createdAt;

    private boolean closed;
}
```

---

# 4. MapInstanceManager và MapService

Hai class này **đều giữ**, nhưng trách nhiệm phải rõ.

## MapInstanceManager

Quản lý **runtime object / registry**:

```java
@Component
public class MapInstanceManager {

    public MapTemplate getTemplate(int mapId) { ... }

    public MapInstance createInstance(int mapId) { ... }

    public MapInstance findInstance(long instanceId) { ... }

    public MapInstance getWorldMap(int mapId) { ... }

    public void removeInstance(long instanceId) { ... }
}
```

Không xử lý:
- player permission;
- teleport;
- change zone;
- gameplay rule.

## MapService

Xử lý **use case player ↔ map**:

```java
@Service
public class MapService {

    public boolean canEnter(
        Player player,
        MapInstance map
    ) { ... }

    public void enterMap(
        Player player,
        MapInstance map,
        int zoneId
    ) { ... }

    public void leaveMap(Player player) { ... }

    public void changeMap(
        Player player,
        int mapId
    ) { ... }

    public void changeZone(
        Player player,
        int zoneId
    ) { ... }

    public void teleport(...) { ... }
}
```

Flow:

```text
Dungeon/Gameplay
→ MapInstanceManager.createInstance()
→ MapService.enterMap()
```

---

# 5. Isolation

Player runtime giữ:

```java
private Zone currentZone;
```

Zone giữ:

```java
private MapInstance mapInstance;
```

Broadcast gameplay ưu tiên:

```java
player.getCurrentZone()
      .broadcast(message);
```

Hai `MapInstance` cùng `MapTemplate` phải có:
- Zone collection riêng.
- Player collection riêng.
- Monster/Boss riêng.
- ItemDrop riêng.

`instanceId` dùng cho:
- lookup;
- logging;
- debug;
- metrics;
- admin;
- reconnect tương lai.

---

# 6. Fixed MAP_IDS

Content cố định được phép hard-code rõ ràng:

```java
public static final List<Integer> MAP_IDS = List.of(
    MapName.CLAN_GATE,
    MapName.CLAN_INSIDE,
    MapName.CLAN_BOSS
);
```

Không cần tạo generic DB/config cho chuỗi map chỉ vì “sau này có thể cần”.

Chỉ chuyển sang data-driven nếu:
- có nhiều dungeon;
- admin/content designer phải thay đổi không build lại;
- difficulty sinh chuỗi map khác nhau.

---

# 7. ClanDungeon trực tiếp

Không dùng generic `ActivityInstance`.

```java
public class ClanDungeon {

    public static final List<Integer> MAP_IDS = List.of(
        MapName.CLAN_GATE,
        MapName.CLAN_INSIDE,
        MapName.CLAN_BOSS
    );

    private long id;

    private Clan clan;

    private List<MapInstance> maps;

    private DungeonState state;

    private int currentWave;

    private long startTime;

    private long endTime;

    public void start() {}

    public void update(long now) {}

    public boolean canEnter(Player player) {
        return false;
    }

    public void onMonsterKilled(
        Player killer,
        Monster monster
    ) {}

    public void finish() {}

    public void close() {}
}
```

Nếu dungeon chỉ cần một map thì dùng:

```java
private MapInstance map;
```

Không xây multi-map engine riêng.

---

# 8. Future Dungeon abstraction

Nếu sau này có:

```text
ClanDungeon
PersonalDungeon
PartyDungeon
```

và code lặp rõ ràng, lúc đó mới extract:

```java
abstract class Dungeon
```

hoặc:

```java
interface Dungeon
```

Không extract trước khi có nhu cầu thật.

---

# 9. BossEvent

Kiến trúc vẫn giữ:

```java
public class BossEvent {

    public static final int MAP_ID =
        MapName.BOSS_ISLAND;

    private MapInstance map;

    private Boss boss;

    private long openTime;

    private long closeTime;

    private boolean running;

    public void start() {}

    public void update(long now) {}

    public void close() {}
}
```

Scheduler gọi start/close.

`BossEvent` là **Extension**, không phải Core DoD.

5 Boss core vẫn có thể chạy ở:
- map thường;
- dungeon;
- spawn thủ công/config.

---

# 10. Arena / Survival

Giữ hướng thiết kế tương lai:

```text
gameplay/arena/
gameplay/event/
```

Nhưng không tạo hàng loạt class rỗng khi chưa implement.

Nếu cần Arena:

```java
public class Arena {

    private MapInstance map;

    private Player player1;

    private Player player2;

    private ArenaState state;

    public void start() {}

    public void update(long now) {}

    public void finish(Player winner) {}
}
```

---

# 11. Package tree target

```text
com.project.game/
│
├── GameApplication.java
│
├── config/
│
├── server/
│   ├── GameServer.java
│   ├── GameLoop.java
│   └── ServerLifecycle.java
│
├── security/
│   ├── AdmissionService.java
│   ├── BuildVersionService.java
│   ├── InstallationService.java
│   ├── ConnectionTicketService.java
│   ├── ConnectionTicketStore.java
│   └── InMemoryConnectionTicketStore.java
│
├── network/
│   ├── Session.java
│   ├── SessionManager.java
│   ├── SessionState.java
│   ├── MessageHandler.java
│   ├── message/
│   ├── codec/
│   ├── packet/
│   └── transport/
│
├── map/
│   ├── MapName.java
│   ├── MapTemplate.java
│   ├── MapInstance.java
│   ├── MapInstanceManager.java
│   ├── MapService.java
│   ├── Zone.java
│   └── Position.java
│
├── npc/
│   ├── NpcTemplate.java
│   ├── Npc.java
│   └── NpcService.java
│
├── upgrade/
│   ├── Upgrade.java
│   ├── UpgradeType.java
│   ├── UpgradeManager.java
│   ├── PendingUpgrade.java
│   ├── UpgradeItem.java
│   ├── UpgradeStar.java
│   ├── DrillSocket.java
│   └── UpgradeQuality.java
│
├── gameplay/
│   ├── dungeon/
│   │   ├── ClanDungeon.java
│   │   ├── DungeonState.java
│   │   └── DungeonService.java
│   ├── boss/
│   │   └── BossEvent.java
│   ├── arena/
│   └── event/
│
├── model/
│   ├── player/
│   ├── monster/
│   ├── boss/
│   ├── item/
│   ├── skill/
│   ├── quest/
│   └── clan/
│
├── service/
│   ├── AuthService.java
│   ├── PlayerService.java
│   ├── CombatService.java
│   ├── InventoryService.java
│   ├── SkillService.java
│   ├── ShopService.java
│   ├── QuestService.java
│   ├── DailyLoginService.java
│   ├── GiftCodeService.java
│   └── ClanService.java
│
├── command/
│   ├── WorldCommand.java
│   ├── WorldCommandQueue.java
│   ├── MoveCommand.java
│   ├── AttackCommand.java
│   ├── SkillCommand.java
│   ├── ChangeMapCommand.java
│   └── PickItemCommand.java
│
├── persistence/
│   ├── entity/
│   ├── json/
│   └── mapper/
│
└── repository/
```

**Lưu ý:** đây là target tree. Không cần tạo file/package rỗng trước khi feature đó bắt đầu.

---

# 12. World concurrency

Network Virtual Thread không sửa world runtime trực tiếp.

```text
Session
→ MessageHandler
→ WorldCommandQueue
→ GameLoop
→ Player/Zone/Monster/Map
```

Giữ các command class/record.

Ví dụ:

```java
public record MoveCommand(
    long playerId,
    short x,
    short y
) implements WorldCommand {}
```

Lợi ích:
- logging.
- debugging.
- metrics theo command.
- test.
- dễ đổi sang nhiều worker sau này nếu thật sự cần.

---

# 13. GameLoop

Mỗi ~100ms:

```text
process WorldCommandQueue
→ update active special gameplay
→ update Zones
→ update Monster/Boss/Drop
```

Không:
- query JPA;
- chờ DB;
- chờ socket write;
- gọi API blocking.

---

# 14. Packet serialization

Giữ target:

```text
network/packet/
├── PlayerPacketWriter
├── MapPacketWriter
├── MonsterPacketWriter
├── CombatPacketWriter
├── InventoryPacketWriter
├── ShopPacketWriter
├── QuestPacketWriter
└── ClanPacketWriter
```

Nhưng **tạo theo feature**, không tạo 8 file rỗng ngày đầu.

Ví dụ:
- network phase: codec/message.
- player phase: PlayerPacketWriter.
- map phase: MapPacketWriter.
- combat phase: CombatPacketWriter.

Service quyết định **gửi gì**.

PacketWriter quyết định **serialize payload thế nào**.

PacketCodec quyết định:
- framing;
- length;
- legacy XOR.

Golden packet test bắt buộc cho:
- PLAYER_INFO.
- MAP_INFO.
- special framing protocol.

---

# 15. NPC

NPC là feature Core, không chỉ là object trang trí trên map.

## 15.1 NPC data/runtime

```text
NpcTemplate
→ dữ liệu tĩnh/cache

Npc
→ instance đặt trong Zone
```

`NpcTemplate` tối thiểu:
- id.
- name.
- avatar/model/icon.
- default chat/config cần thiết.

`Npc` runtime tối thiểu:
- template.
- x/y.
- zone/map reference nếu cần.

NPC spawn lấy từ `MapTemplate`, giống tinh thần source cũ.

## 15.2 NPC protocol compatibility

Client hiện tại đã có flow sẵn:

```text
UPDATE_DATA subtype 4
→ NPC template list

MAP_INFO
→ npc count
→ templateId + x + y
```

Các command cần giữ:

```text
OPEN_NPC      = -93
CONFIRM_MENU  = -92
NPC_CHAT      = -91
ADD_NPC       = -48
REMOVE_NPC    = -47
```

`OPEN_NPC -93`:
- client → server: NPC template id.
- server → client: npc id + chat + menu list.

`CONFIRM_MENU -92`:
- client → server: NPC id/template id + selected menu index.

`NPC_CHAT -91`:
- server → client: NPC id + chat text.

## 15.3 NpcService

```java
@Service
public class NpcService {

    public void openMenu(
        Player player,
        int npcId
    ) { ... }

    public void confirmMenu(
        Player player,
        int npcId,
        int selectedIndex
    ) { ... }

    public void sendChat(
        Player player,
        Npc npc,
        String text
    ) { ... }
}
```

NPC menu đóng vai trò entry point tới:
- ShopService.
- QuestService.
- UpgradeManager → `Upgrade.showTab()`.
- Clan/Dungeon service nếu gameplay cần.

V1 có ít NPC nên một `switch` rõ ràng theo NPC/menu là chấp nhận được.

Không tạo generic NPC scripting engine từ đầu.

Nếu sau này NPC rất nhiều và `NpcService` bắt đầu phình, mới tách handler theo NPC/function.

---

# 16. Upgrade / Enhancement

Upgrade là Core domain. Bản này chốt theo nguyên tắc:

> **Một mechanic nâng cấp = một class server.**
>
> Không tách `UpgradePreview`, `UpgradeOption`, `UpgradeResult` thành file riêng khi chưa có nhu cầu thật.

5 slot nâng cấp dự kiến:

```text
1. UPGRADE_ITEM      → nâng +1, +2, +3...
2. UPGRADE_STAR      → nâng sao
3. DRILL_SOCKET      → đục lỗ
4. UPGRADE_QUALITY   → nâng phẩm cấp
5. Mechanic thứ 5   → chưa chốt; chưa thêm UpgradeType/class
```

Không tự định nghĩa mechanic thứ 5 trước khi gameplay/content được chốt.

## 16.1 Cấu trúc

```text
upgrade/
├── Upgrade.java
├── UpgradeType.java
├── UpgradeManager.java
├── PendingUpgrade.java
│
├── UpgradeItem.java
├── UpgradeStar.java
├── DrillSocket.java
└── UpgradeQuality.java
```

Mỗi file gameplay chứa gần như toàn bộ luật của mechanic đó:
- validate item;
- preview text/menu;
- cost/rate;
- material;
- confirm;
- transaction;
- result rule.

Không tạo thêm `Handler`, `Strategy`, `Factory`, `Preview`, `Option` chỉ để chia file.

## 16.2 UpgradeType

```java
public enum UpgradeType {
    UPGRADE_ITEM,
    UPGRADE_STAR,
    DRILL_SOCKET,
    UPGRADE_QUALITY
}
```

Nếu mechanic thứ 5 chưa chốt khi bắt đầu code thì có thể **chưa thêm enum value đó**; thêm khi thật sự implement.

## 16.3 Upgrade interface

Interface ngắn nhưng có nhiệm vụ thật: cho `UpgradeManager` quản lý tất cả mechanic bằng cùng một kiểu.

```java
public interface Upgrade {

    UpgradeType getType();

    void showTab(Player player);

    void preview(
        Player player,
        List<Integer> itemIndexes
    );

    void confirm(
        Player player,
        PendingUpgrade pending,
        int selectedIndex
    );
}
```

Không cần abstract base class từ đầu. Chỉ đổi thành abstract class nếu sau này có helper code lặp rõ ràng.

## 16.4 UpgradeManager

Dùng Spring inject danh sách implementation để thêm mechanic mới không phải sửa constructor dài:

```java
@Component
public class UpgradeManager {

    private final Map<UpgradeType, Upgrade>
        upgrades = new EnumMap<>(UpgradeType.class);

    public UpgradeManager(
            List<Upgrade> upgradeList) {

        for (Upgrade upgrade : upgradeList) {
            upgrades.put(
                upgrade.getType(),
                upgrade
            );
        }
    }

    public Upgrade get(UpgradeType type) {
        return upgrades.get(type);
    }
}
```

`UpgradeManager` chỉ là registry. Không chứa gameplay logic.

## 16.5 Player runtime state

Protocol cũ `UPGRADE -37` không gửi `UpgradeType`, nên Player giữ state runtime:

```java
public class Player {

    private UpgradeType currentUpgradeType;

    private PendingUpgrade pendingUpgrade;

    private final ReentrantLock upgradeLock =
        new ReentrantLock();
}
```

Các field này:
- runtime only;
- không persist DB;
- clear khi logout;
- clear khi đổi mechanic;
- clear khi pending hết hạn;
- clear sau confirm.

## 16.6 PendingUpgrade tối giản

Không dùng `Object[]` như source cũ và cũng không nhét dữ liệu riêng của từng mechanic vào đây.

```java
public record PendingUpgrade(
    UpgradeType type,
    List<Integer> itemIndexes,
    long createdAt
) {

    public PendingUpgrade(
            UpgradeType type,
            List<Integer> itemIndexes) {

        this(
            type,
            List.copyOf(itemIndexes),
            System.currentTimeMillis()
        );
    }

    public boolean isExpired() {
        return System.currentTimeMillis()
                - createdAt > 30_000;
    }
}
```

`PendingUpgrade` chỉ trả lời:

```text
player đang confirm mechanic nào?
đã chọn các slot item nào?
request được tạo lúc nào?
```

Dữ liệu riêng như:
- cost;
- success rate;
- số sao;
- số lỗ;
- phẩm cấp;
- batch;

không lưu chung ở đây. `confirm()` của từng mechanic tính/validate lại.

Nếu một mechanic tương lai thật sự cần giữ kết quả random giữa preview và confirm, lúc đó mới refactor `PendingUpgrade` thành interface/subtype. Không làm trước.

## 16.7 showTab()

NPC/menu gọi:

```text
UpgradeManager.get(type)
→ upgrade.showTab(player)
```

`showTab()`:

```text
set player.currentUpgradeType
→ gửi -27
→ client mở đúng panel riêng của mechanic
```

Không mutate item/economy.

## 16.8 Client panel riêng cho từng mechanic

Server có thể gửi một `uiType`/`formatType` trong packet `-27`.

Target mapping:

```text
0 → UPGRADE_ITEM
1 → UPGRADE_STAR
2 → DRILL_SOCKET
3 → UPGRADE_QUALITY
4 → reserved; chỉ dùng sau khi mechanic thứ 5 được chốt
```

Unity client dùng giá trị này để mở panel riêng.

Ví dụ:

```text
UpgradeItemPanel
→ 3 ô:
   [Trang bị] [Đá nâng cấp] [Đá bảo vệ]

DrillSocketPanel
→ 2 ô:
   [Trang bị] [Vật phẩm đục lỗ]

UpgradeQualityPanel
→ 5 ô:
   [Trang bị] [NL1] [NL2] [NL3] [Bảo vệ]

UpgradeStarPanel
→ layout riêng theo rule nâng sao
```

Exact layout mechanic thứ 5 chốt sau.

Giữ command `-27`; không tạo một packet mở panel riêng cho từng mechanic.

## 16.9 UPGRADE -37 = preview

Client gửi:
- count;
- các `indexUI` item được chọn.

Server:

```text
MessageHandler
→ player.currentUpgradeType
→ UpgradeManager.get(type)
→ upgrade.preview(player, indexes)
```

`preview()` của từng mechanic tự:
- lấy item thật từ inventory;
- phân loại item;
- validate;
- tính cost/rate/material;
- lưu `PendingUpgrade`;
- tạo text/menu confirm;
- gửi menu confirm cho client.

Không cần object `UpgradePreview` hoặc `UpgradeOption`.

Client không gửi:
- UpgradeType;
- cost;
- rate;
- success;
- resulting level/state.

## 16.10 CONFIRM_MENU -92 = execute

```text
CONFIRM_MENU -92
→ menu/NPC handler
→ lấy player.pendingUpgrade
→ check timeout
→ check selectedIndex
→ UpgradeManager.get(pending.type())
→ upgrade.confirm(...)
```

`confirm()` phải validate lại trạng thái hiện tại vì inventory/currency/material có thể đã thay đổi sau preview.

## 16.11 UpgradeItem

UI dự kiến 3 ô:

```text
[Trang bị]
[Đá nâng cấp]
[Đá bảo vệ]
```

Server vẫn không tin client rằng slot 1/2/3 đúng loại. `UpgradeItem` tự phân loại item từ inventory/template.

```text
preview()
→ find equipment
→ find upgrade stone
→ optional protection stone
→ validate level
→ calculate material/cost/rate
→ show confirm choices
→ save PendingUpgrade
```

```text
confirm()
→ lock player upgrade
→ lấy lại item từ indexes
→ classify + validate lại
→ transaction
→ consume material/currency
→ server RNG
→ success: +1
→ fail: giữ/tụt theo protection rule
→ commit
→ sync runtime
```

## 16.12 UpgradeStar

Một class riêng:

```text
UpgradeStar.java
```

Tự quản:
- item nào được nâng sao;
- stone/material nâng sao;
- max star;
- cost;
- rate;
- fail rule;
- stat tăng theo star.

Không nhét logic sao vào `UpgradeItem`.

Panel Unity riêng, số ô chốt theo gameplay thật.

## 16.13 DrillSocket

UI dự kiến 2 ô:

```text
[Trang bị]
[Vật phẩm đục lỗ]
```

`DrillSocket` tự quản:
- loại trang bị được đục;
- số lỗ hiện tại;
- max socket;
- material;
- cost/rate nếu có;
- success/fail rule.

Nếu đục lỗ luôn thành công thì không ép dùng RNG.

## 16.14 UpgradeQuality

UI dự kiến 5 ô:

```text
[Trang bị]
[Nguyên liệu 1]
[Nguyên liệu 2]
[Nguyên liệu 3]
[Bảo vệ / nguyên liệu phụ]
```

`UpgradeQuality` tự quản:
- quality hiện tại;
- quality tiếp theo;
- recipe material;
- currency;
- success/fail;
- stat/template option thay đổi.

Không đưa các field riêng này vào `PendingUpgrade`.

## 16.15 Mechanic thứ 5

Plan giữ một slot kiến trúc nhưng **không đoán gameplay**.

Khi chốt:
- thêm `UpgradeType`;
- thêm đúng 1 class implementation;
- thêm đúng 1 panel client nếu layout khác;
- `UpgradeManager`, protocol `-37/-92` và `PendingUpgrade` không đổi.

## 16.16 Per-player serialization

Giữ ý tưởng tốt từ source cũ, nhưng lock riêng cho Upgrade:

```java
player.getUpgradeLock().lock();

try {
    // validate + transaction
} finally {
    player.getUpgradeLock().unlock();
}
```

Không để hai confirm upgrade của cùng player chạy song song.

Không dùng global lock.

## 16.17 Transaction boundary

`preview()`:
- không consume;
- không sửa item;
- không random kết quả cuối;
- không DB transaction mutation dài.

`confirm()`:
- check PendingUpgrade;
- per-player upgrade lock;
- validate lại;
- DB transaction;
- consume material/currency;
- server RNG/result;
- commit;
- sync runtime;
- clear pending;
- send result.

Không chạy Upgrade transaction trong GameLoop.

## 16.18 Side effects

Upgrade class tập trung vào mechanic/economy của chính nó.

Không copy source cũ theo kiểu một class tự ôm:
- quest;
- daily;
- achievement;
- global chat.

Nếu cần:
- emit `ItemUpgradedEvent`;
- Quest/Achievement listener xử lý bên ngoài.

Chỉ thêm event khi feature thật sự dùng.

## 16.19 Protocol compatibility

Giữ:

```text
server → client
SHOW/CONFIG UPGRADE PANEL = -27

client → server
UPGRADE = -37

client → server
CONFIRM_MENU = -92
```

`-27` tiếp tục dùng cùng command, nhưng client mới có thể hiểu `uiType/formatType` để chọn panel riêng.

Exact payload `-27/-37/-92` phải có integration test với Unity client trước khi cleanup protocol.

## 16.20 Item persistence

Các progression chính nên là field rõ ràng nếu được dùng thường xuyên:

```text
upgrade_level
star_level
socket_count
quality
```

Không gom bốn mechanic chính vào một JSON/map generic chỉ để “linh hoạt”.

Schema chi tiết chốt khi thiết kế `PlayerItemEntity`.



# 17. Persistence strategy

## Realtime RAM
- x/y.
- hp/mp.
- target.
- cooldown.
- aggro.
- monster runtime.

## Dirty autosave
- level/exp.
- position/map.
- inventory thay đổi thông thường.
- progression thường.

## Transaction ngay
- Shop.
- GiftCode.
- Daily.
- Quest reward quan trọng.
- Dungeon reward.
- currency/item có tính kinh tế quan trọng.

Critical flow:

```text
validate use case
→ DB transaction
→ commit success
→ update runtime
→ send client
```

Không đặt transaction DB trong GameLoop tick.

---

# 18. ItemDrop — flow đã sửa

## Flow mặc định

Vì GameLoop là single writer:

```text
PICK_ITEM command
→ GameLoop validate
→ find ItemDrop
→ validate owner/range/bag
→ remove ItemDrop khỏi Zone
→ add item vào Inventory runtime
→ mark player dirty
→ autosave sau
```

Hai player cùng pick:

```text
A xử lý trước
→ drop bị remove

B xử lý sau
→ không tìm thấy drop
→ reject
```

Không cần DB transaction blocking trong tick cho mọi item.

## Durable pickup — chỉ khi thật sự cần

Nếu sau này có item cực quan trọng cần durability tức thì:

```text
AVAILABLE
→ RESERVED
→ async persistence
→ success: REMOVED
→ fail: AVAILABLE
```

`RESERVED` là cơ chế mở rộng, không phải mặc định.

---

# 19. Security

Giữ đầy đủ:

1. TLS 1.3.
2. Account auth.
3. Build/version gate.
4. One-time ConnectionTicket.
5. Installation identity/credential.
6. Session-state validation.
7. Rate limiting.
8. Server-authoritative gameplay.
9. Transaction/idempotency.
10. Security logging/revoke.

Dev:

```text
TCP + legacy XOR
```

Online/demo:

```text
TLS
→ legacy/custom framing
→ XOR compatibility
```

XOR không được coi là security.

---

# 20. ConnectionTicket storage

`ConnectionTicketService` giữ business logic:

```text
issue
validate
expire
consume one-time
```

Storage tách nhẹ:

```java
public interface ConnectionTicketStore {

    void save(ConnectionTicket ticket);

    ConnectionTicket consume(String ticketId);

    void removeExpired();
}
```

## V1 backend

```java
@Component
public class InMemoryConnectionTicketStore
        implements ConnectionTicketStore {

    private final ConcurrentHashMap<
        String,
        ConnectionTicket
    > tickets = new ConcurrentHashMap<>();
}
```

One-time consume có thể dựa trên atomic `remove()`.

## Future backend

Nếu sau này:
- restart ticket phải sống;
- multi-node;
- cần audit chi tiết;

có thể thêm:
- DatabaseConnectionTicketStore.
- RedisConnectionTicketStore.

Không đổi `AdmissionService`.

---

# 21. Installation identity

Giữ DB:

```text
client_installations
```

Có thể lưu:
- installation_id.
- user_id.
- credential hash/public material.
- created_at.
- last_seen_at.
- revoked.
- revoked_at.

Không dùng invasive hardware fingerprint làm nền chính.

---

# 22. Session state

Giữ enum nhỏ:

```java
public enum SessionState {
    CONNECTED,
    HANDSHAKE_DONE,
    AUTHENTICATED,
    IN_GAME,
    CLOSED
}
```

Không cần state machine framework.

Command validation:

```text
CONNECT_SERVER
→ CONNECTED

REGISTER/LOGIN
→ HANDSHAKE_DONE

MOVE/ATTACK/SKILL
→ IN_GAME
```

---

# 23. Protocol compatibility

Giữ command hiện tại:
- Connect `-128`.
- Version Source `-127`.
- Request Icon `-22`.
- Update Data `-125`.
- Login `-124`.
- Create Player `-122`.
- Map Info `-121`.
- Register `-118`.
- Finish Load Map `-115`.
- Open NPC `-93`.
- Confirm NPC Menu `-92`.
- NPC Chat `-91`.
- Move `-114`.
- Change Map `-113`.
- Player Info `-109`.
- Attack `-108`.
- Change Area `-84`.
- Pick Item `-79`.
- Buy Item `-78`.
- Skill `-72`.
- Add NPC `-48`.
- Remove NPC `-47`.
- Clan `-44`.
- Upgrade `-37`.
- Quest `-23`.

Không đổi command chỉ vì muốn protocol “đẹp hơn”.

Special server → client framing phải được giữ đồng bộ với Network Plan:

```text
VERSION_SOURCE -127
REQUEST_ICON   -22
UPDATE_DATA    -125
```

Ba command này dùng legacy 3-byte length framing riêng.

---

# 24. Quest/EventBus

Giữ một EventBus nhỏ vì cost thấp và có giá trị mở rộng.

Events:
- MonsterKilledEvent.
- BossKilledEvent.
- ItemObtainedEvent.
- LevelUpEvent.
- DungeonCompletedEvent.

Không biến EventBus thành framework phức tạp.

Quest types:
- TALK_NPC.
- KILL_MONSTER.
- KILL_BOSS.
- COLLECT_ITEM.
- REACH_LEVEL.
- COMPLETE_DUNGEON.

---

# 25. Database

Core tables:
- users.
- players.
- items.
- player_items (`upgrade_level` + item state).
- skills.
- player_skills.
- quests.
- player_quests.
- shops.
- shop_items.
- daily_login_rewards.
- player_daily_logins.
- gift_codes.
- gift_code_rewards.
- player_gift_codes.
- clans.
- clan_members.
- dungeon_runs.
- dungeon_participants.
- client_installations.
- item_upgrade_logs (optional nhưng hữu ích cho audit/debug economy).

`connection_tickets` **không bắt buộc ở V1** vì ticket mặc định lưu RAM.

Có thể thêm table này sau nếu cần persistent ticket/audit.

Không tạo generic `activity_runs` nếu chưa có nhu cầu.

---

# 26. Dungeon persistence

Giữ:
- `dungeon_runs`.
- `dungeon_participants`.

Mục đích:
- run history.
- daily limit.
- reward audit.
- thống kê.
- nền cho reconnect V2.

Không dùng các bảng này để cố restore toàn bộ runtime dungeon sau server restart.

---

# 27. Reconnect policy

## V1 — Core

Normal map:

```text
disconnect
→ save normal map/position
→ reconnect
→ restore
```

Special map / Dungeon:

```text
disconnect
→ session/player offline
→ reconnect
→ đưa về safe map
```

Không cố rejoin dungeon ở V1.

## V2 — Extension

Nếu sau này implement rejoin:

```text
dungeon run còn active
+ player là participant cũ
+ chưa claim reward
+ run không closed
→ cho rejoin
```

`instanceId`, `dungeon_runs`, `dungeon_participants` hỗ trợ roadmap này.

---

# 28. Bosses

5 Boss core:
1. Earth — baseline/high HP.
2. Fire — rage dưới 50%.
3. Ice — AoE.
4. Demon — summon theo HP threshold.
5. Dragon — 3 phase.

Boss dùng:
- Monster/Boss runtime chung.
- Zone chung.
- CombatService chung.

Không tạo Boss framework quá lớn.

---

# 29. Benchmark

Mốc:
- 100.
- 250.
- 500.
- 1000.
- 1500 nếu VPS/server vẫn ổn.

Core requirement vẫn chỉ cần >=100.

## Single GameLoop — early warning

GameLoop target khoảng:

```text
100 ms / tick
```

Không đợi benchmark 1000+ mới kiểm tra bottleneck.

Theo dõi từ sớm:

```text
tick p95 < 70 ms
→ còn headroom tốt

tick p95 70–90 ms
→ warning, bắt đầu profile

tick p95 >= 100 ms
→ loop không giữ được target tick
→ phải tìm bottleneck trước khi tăng client
```

Đây là ngưỡng kỹ thuật tham khảo, không phải acceptance rule cứng.

Trước khi nghĩ tới multi-worker/sharding, profile:
- command count/tick;
- monster AI;
- broadcast;
- packet creation;
- collection iteration;
- allocation/GC;
- code blocking vô tình chạy trong GameLoop.

Chỉ khi benchmark chứng minh single GameLoop là bottleneck mới cân nhắc:
- per-zone worker;
- partition world;
- multi-loop.

Không thiết kế sharding trước.

Metrics:
- concurrent sessions.
- command queue depth.
- command count/tick theo type.
- active MapInstance.
- active dungeon/event count.
- GameLoop tick p50/p95/p99.
- CPU.
- RAM/heap.
- GC.
- packets/sec.
- DB latency.
- TLS/admission latency.

Không cần tối ưu mọi metric trước khi baseline chạy ổn.

---

# 30. Core Definition of Done

- [ ] Legacy protocol tương thích client.
- [ ] Register/Login/Create Player.
- [ ] TLS/admission.
- [ ] Build/version validation.
- [ ] Installation identity/revoke cơ bản.
- [ ] One-time ConnectionTicket.
- [ ] MapTemplate/MapInstance/Zone.
- [ ] MapService/MapInstanceManager responsibility rõ.
- [ ] MapInstance isolation.
- [ ] WorldCommandQueue/GameLoop.
- [ ] PacketWriter theo domain được tạo theo feature.
- [ ] Persistence consistency.
- [ ] Player/Movement.
- [ ] Monster/Combat/Skill.
- [ ] Item/Inventory/Shop.
- [ ] NPC template/runtime/menu interaction.
- [ ] NPC → Shop/Quest/Upgrade integration.
- [ ] Core Upgrade lifecycle: showTab → preview → confirm.
- [ ] `currentUpgradeType` + minimal typed `PendingUpgrade`.
- [ ] `UpgradeItem`, `UpgradeStar`, `DrillSocket`, `UpgradeQuality`.
- [ ] Slot kiến trúc cho mechanic nâng cấp thứ 5, không tự bịa gameplay.
- [ ] Separate Unity panel per mechanic when layouts differ.
- [ ] Confirm re-validates state before transaction.
- [ ] Per-player upgrade serialization.
- [ ] Upgrade transaction/server-authoritative validation.
- [ ] No `Object[]`, `UpgradePreview`, `UpgradeOption` framework.
- [ ] Quest.
- [ ] 5 Boss.
- [ ] Daily/GiftCode.
- [ ] Clan.
- [ ] ClanDungeon trực tiếp.
- [ ] Fixed MAP_IDS nếu dungeon multi-map.
- [ ] Dungeon reward/history.
- [ ] Special-map reconnect V1 → safe map.
- [ ] Autosave/shutdown.
- [ ] >=100 simulated clients.

---

# 31. Extension Definition of Done

Không chặn đồ án nếu chưa xong:

- [ ] Scheduled BossEvent.
- [ ] Arena.
- [ ] Survival/Event.
- [ ] Personal Dungeon.
- [ ] Party Dungeon.
- [ ] Dungeon rejoin V2.
- [ ] Persistent/Redis ticket store.
- [ ] Multi-node.
