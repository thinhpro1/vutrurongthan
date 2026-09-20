# SERVER ARCHITECTURE & CODING RULES

> **Status:** Authoritative server architecture contract  
> **Scope:** `server/**`  
> **Baseline:** `97202d13e8dee3f723767962746571b12d0d928c` — final production cleanup commit
>
> Mục tiêu của tài liệu này là giữ server **dễ tìm code, dễ đọc, dễ sửa và khó phá nhầm** khi project lớn dần.
> Readability quan trọng hơn việc ép code tuân thủ 100% một Design Pattern.
>
> **Bắt buộc với coding model:** trước khi sửa server, phải đọc file này. Nếu implementation định làm khác tài liệu này, phải nêu rõ điểm xung đột và được chấp thuận trước.

---

# 1. Thứ tự ưu tiên tài liệu

Khi các tài liệu server mâu thuẫn nhau:

```text
1. Production code + database/protocol contract hiện tại
2. SERVER_ARCHITECTURE_RULES_UPDATED.md (this document)
3. Feature design/spec mới được duyệt sau file này
4. server/README.md
5. Các plan/spec cũ
```

Các file cũ như:

```text
01_SERVER_PLAN_V7_4.md
03_IMPLEMENTATION_TEST_SCHEDULE_V5_4.md
04_NETWORK_IMPLEMENTATION_PLAN_V2_4.md
docs/superpowers/**
```

vẫn dùng làm tài liệu tham khảo lịch sử, nhưng không phải mọi nội dung còn đúng.

Đặc biệt, các kiến trúc cũ nhắc tới:

```text
Spring Boot
Spring Data JPA
Hibernate/JPA
mandatory GameLoop / WorldCommandQueue
```

**không phải kiến trúc hiện hành** nếu chưa có design mới phê duyệt việc đưa chúng trở lại.

---

# 2. Stack hiện tại

```text
Language             Java 21
Build                 Maven
JSON                  Gson 2.11.x
Connection pool       HikariCP 7.x
Database access       Plain JDBC
Database              MySQL-compatible schema
Driver                mysql-connector-j 9.x
Tests                 JUnit Jupiter 5.11.x
Networking            ServerSocket / Socket
TLS                   SSLSocket, TLS 1.3 optional
Concurrency           Java 21 virtual threads cho Session reader/writer
Protocol              Legacy binary protocol tương thích Unity client
```

Server hiện tại là **plain Java**, không dùng framework application lớn.

Không được tự ý thêm nếu chưa có design riêng:

```text
Spring / Spring Boot
JPA / Hibernate
Netty
Redis
Kafka
Microservices
DI framework
Event-bus framework
ORM framework
Flyway / Liquibase
```

---

# 3. Triết lý kiến trúc

Ưu tiên:

```text
feature-first
package nông
tên trực tiếp
flow dễ lần
abstraction vừa đủ
```

Không tối ưu cho:

```text
nhiều interface
nhiều layer
diagram đẹp
folder đối xứng
Design Pattern tối đa
```

Người mới vào project phải có thể đoán:

```text
Player        → player/
Monster       → monster/
Map           → map/
Login         → account/
Protocol      → network/
DB            → persistence/
Static data   → resource/
```

Nếu phải hiểu `domain/application/adapter/gateway/usecase` trước mới tìm được file thì kiến trúc đã đi lệch mục tiêu.

---

# 4. Package tree rule — representative, not file inventory

Cây dưới đây mô tả **shape/ownership mong muốn**, không phải checklist bắt buộc phải có đúng từng file.

```text
com.project.game/
├── GameApplication.java
├── account/
│   └── AuthService.java
├── bootstrap/
│   └── ServerBootstrap.java
├── combat/
│   └── CombatService.java
├── player/
│   ├── Appearance.java
│   ├── BaseStats.java
│   ├── CurrentStats.java
│   ├── PlayerProfile.java
│   ├── PlayerProfileFactory.java
│   └── PlayerService.java
├── map/
│   ├── MapTemplate.java
│   ├── Waypoint.java
│   ├── Zone.java
│   ├── ZoneRegistry.java
│   └── MapService.java
├── monster/
│   ├── Monster.java
│   ├── MonsterAttack.java
│   ├── MonsterCombatTemplate.java
│   ├── MonsterDart.java
│   ├── MonsterFactory.java
│   ├── MonsterLifecycleScheduler.java
│   ├── MonsterService.java
│   ├── MonsterSnapshot.java
│   ├── MonsterSpawn.java
│   └── MonsterTemplate.java
├── resource/
│   ├── EffectImage.java
│   ├── FrameTemplate.java
│   ├── GameResources.java
│   ├── IconCatalog.java
│   ├── IconFingerprint.java
│   ├── LevelTemplate.java
│   ├── SkillTemplate.java
│   └── loader/
│       ├── EffectLoader.java
│       ├── FrameLoader.java
│       ├── GameResourcesLoader.java
│       ├── JsonResourceReader.java
│       ├── LevelLoader.java
│       ├── MapLoader.java
│       ├── MonsterCombatLoader.java
│       ├── MonsterLoader.java
│       └── SkillLoader.java
├── network/
│   ├── ClientConfig.java
│   ├── NetworkServer.java
│   ├── Session.java
│   ├── SessionManager.java
│   ├── SessionServices.java
│   ├── SessionState.java
│   ├── codec/
│   │   ├── LegacyCipher.java
│   │   └── LegacyPacketCodec.java
│   ├── handler/
│   ├── message/
│   ├── packet/
│   │   ├── MapPacketWriter.java
│   │   ├── MonsterPacketWriter.java
│   │   ├── PlayerPacketValidator.java
│   │   ├── PlayerPacketWriter.java
│   │   └── ResourcePacketWriter.java
│   └── transport/
│       ├── ClientTransport.java
│       ├── LegacyTcpTransport.java
│       ├── TlsContextFactory.java
│       └── TlsTcpTransport.java
└── persistence/
    ├── DatabaseConfig.java
    ├── DatabaseManager.java
    ├── account/
    └── player/
```

Các tên ở cây trên mô tả ownership hiện tại ở mức đại diện; đây không phải checklist file inventory. Package `service/` không còn trong production.

Không tạo package chỉ để làm cây đối xứng.

Không tạo trước package rỗng cho feature chưa bắt đầu:

```text
item/
inventory/
npc/
boss/
shop/
quest/
disciple/
effect/
clan/
...
```

Khi feature bắt đầu, tạo package nhỏ nhất đủ dùng. Khi lớn lên mới chia tiếp.

Ví dụ ban đầu:

```text
npc/
├── Npc.java
├── NpcTemplate.java       // chỉ khi thật sự có static definition riêng
└── NpcService.java        // chỉ khi thật sự có orchestration riêng
```

Không suy ra rằng vì `monster/` có `Factory`, `Template`, `Scheduler` thì `npc/`, `boss/`, `item/` cũng phải có các file tương ứng.

---

# 5. Quy tắc ownership

## account/

Chứa logic đăng ký/đăng nhập/account.

Không chứa JDBC implementation.

```text
AuthHandler
→ AuthService
→ AccountRepository
→ JdbcAccountRepository
```

## player/

Chứa state và logic trực tiếp của Player:

```text
identity
HP/MP
stats
power
potential
EXP
currency
appearance
create/load/checkpoint orchestration
```

Player được phép có behavior thật; không cần biến thành getter/setter bag.

Logic chỉ cần state của chính player có thể nằm trong Player:

```text
isDead()
addHp()
consumeMp()
addPotential()
addExp()
revive()
hasEnoughCoin()
```

Logic phối hợp nhiều subsystem thường nằm ở Service của hành động:

```text
buy item       → ShopService
equip item     → Inventory/EquipmentService
change map     → MapService
attack monster → CombatService
save player    → PlayerService / Repository
```

## map/

Chứa:

```text
map definition
waypoint
Zone
membership
movement
change map / zone
```

MapService không được tiếp tục trở thành global gameplay service.

## monster/

Chứa:

```text
monster runtime
template
movement
AI/lifecycle
respawn
monster-specific rules
```

## combat/

Chứa:

```text
attack flow
damage formula
critical
armor
dodge
skill damage interaction
combat reward orchestration
```

Không đặt damage formula trong Handler.

## resource/

Chứa static data + loader.

```text
GameResources = dữ liệu đã load
loader/*      = parse + validate
```

Không tiếp tục nhét mọi resource mới vào một God `ResourceService`.

## network/

Chứa:

```text
socket/TLS
Session
protocol
codec
message
packet serialization
command dispatch
protocol state validation
```

Network được gọi gameplay service.

Gameplay không được phụ thuộc wire format.

## persistence/

Chứa toàn bộ concern DB:

```text
DatabaseConfig
DatabaseManager
Repository
JdbcRepository
Record
SQL mapping
persistence exception
```

Persistence cố ý tách top-level khỏi gameplay.

---

# 6. Dependency direction

Flow ưu tiên:

```text
Network Handler
      ↓
Gameplay Service
      ↓
Model / runtime state
      ↓
Repository interface khi cần lưu
      ↓
JDBC Repository
```

Ví dụ:

```text
PlayerHandler
→ PlayerService
→ PlayerRepository
→ JdbcPlayerRepository
```

```text
CombatHandler
→ CombatService
→ Player + Zone + Monster
```

Không cần package dependency hoàn hảo kiểu Clean Architecture, nhưng dependency phải dễ hiểu và không vòng vèo vô lý.

---

# 7. Handler rules

Handler là **protocol adapter**, không phải chủ business rule.

Handler được phép:

```text
parse packet
check trailing bytes
check SessionState
check client/protocol version
gọi Service
orchestrate request nhỏ
chọn response packet
session.send(...)
```

Handler không nên giữ business rule có thể reuse ngoài network.

Câu hỏi kiểm tra:

> Nếu mai hành động này được gọi từ GM command, NPC, bot hoặc test thì rule này còn đúng không?

Nếu **có** → thường không nên chỉ nằm trong Handler.

Ví dụ:

```text
quantity encoded as short
→ Handler

player phải đủ coin
→ ShopService

inventory phải còn chỗ
→ Inventory logic

critical multiplier
→ CombatService
```

---

# 8. MessageHandler rule

Giữ tên `MessageHandler`, nhưng lâu dài nó chỉ nên:

```text
command allowlist
protocol violation
dispatch
```

Target:

```text
MessageHandler
├── ConnectionHandler
├── ResourceHandler
├── AuthHandler
├── PlayerHandler
├── MapHandler
└── CombatHandler
```

Không tạo 1 Handler cho mỗi command.

Group theo feature rõ nghĩa.

Không tiếp tục thêm mọi feature mới trực tiếp vào một `MessageHandler.java` khổng lồ.

---

# 9. Packet serialization rule

Gameplay quyết định:

> Chuyện gì đã xảy ra?

PacketWriter quyết định:

> Serialize kết quả đó sang legacy Unity protocol thế nào?

Packet encoding thuộc:

```text
network/packet/
```

Ví dụ:

```text
PlayerPacketWriter
MapPacketWriter
MonsterPacketWriter
ResourcePacketWriter
```

Không đặt monster encoding vào `PlayerPacketWriter`.

Không để `.writeInt().writeLong().writeShort()` dài hàng chục dòng trong Service gameplay.

Protocol/framing/XOR thuộc:

```text
network/message/
network/codec/
```

Golden packet tests cần giữ cho contract quan trọng:

```text
PLAYER_INFO
MAP_INFO
special framing
monster damage/death
future inventory/equipment packets
```

---

# 10. Persistence rules

Gameplay package không được trực tiếp dùng:

```text
Connection
PreparedStatement
ResultSet
HikariDataSource
JdbcPlayerRepository
JdbcAccountRepository
```

Gameplay có thể dùng interface:

```text
PlayerRepository
AccountRepository
```

Model không tự:

```text
save()
load()
deleteFromDatabase()
```

Không viết SQL trong:

```text
Player
MapService
CombatService
Handler
PacketWriter
```

Nguyên tắc hiện tại:

```text
Session / Zone / gameplay runtime = realtime authority
MySQL                             = durable checkpoint
```

Không write DB trong movement/combat hot path nếu chưa có design riêng.

---

# 11. PlayerProfile và concurrency

Hiện tại `PlayerProfile` là immutable record.

Update runtime bằng cách tạo value mới:

```text
withHp()
withMp()
withPosition()
withPotential()
withLocation()
revivedAt()
```

rồi:

```text
session.bindPlayer(updated)
```

Điều này đang giúp:

```text
volatile Session.player
immutable checkpoint snapshot
disconnect save ordering
race safety
```

Không được đổi sang mutable giant `Player` chỉ vì muốn tên/code nhìn đẹp hơn trong cùng một refactor thư mục.

Nếu sau này đổi sang mutable Player phải có design riêng cho:

```text
locking
snapshot
checkpoint consistency
disconnect race
same-account relogin
```

---

# 12. Session / Zone concurrency invariants

Concurrency là correctness, không phải style.

Các vùng nhạy cảm:

```text
Session.close
account admission
same-account reservation
Zone mutation
monster combat ordering
disconnect final checkpoint
```

Invariant disconnect hiện tại:

```text
mark CLOSED
→ remove realtime membership
→ capture immutable PlayerProfile snapshot
→ final checkpoint attempt
→ release same-account reservation
→ remove session
```

Không release account trước checkpoint attempt.

Không đổi final save thành fire-and-forget async.

Không giữ Session monitor trong lúc JDBC.

Không refactor synchronization chỉ để code trông đẹp hơn.

---

# 13. Map / Monster / Combat boundary

Giữ boundary responsibility sau refactor:



```text
MapService
→ join / leave / movement / change map / change zone

MonsterService
→ monster lifecycle / movement / respawn / AI tick

CombatService
→ attack / damage / combat formula / combat reward
```

Không tiếp tục thêm:

```text
skill
buff/debuff
drop
EXP
equipment combat modifier
```

trực tiếp vào MapService.

`Zone` hiện có thể biết `Session`; đây là lựa chọn pragmatic được chấp nhận.

Không tạo EventBus/ZoneMember abstraction chỉ để xóa dependency đó nếu chưa có nhu cầu thật.

---

# 14. Resource rule

Không tạo lại một God `ResourceService`. Static data và loader phải có ownership rõ.

Target pattern:

```text
GameResources
→ catalog/read API

MapLoader
MonsterLoader
SkillLoader
FrameLoader
LevelLoader
EffectLoader
→ parse + validation
```

Khi có Item:

```text
ItemLoader
```

thay vì thêm thêm hàng trăm dòng vào một loader tổng.

Static template và runtime object là hai khái niệm khác nhau:

```text
MonsterTemplate != Monster
```

---

# 15. Bootstrap rule

`NetworkServer` lâu dài chỉ nên lo:

```text
listen
accept
create Session
start/stop networking
```

Wiring application chuyển vào:

```text
bootstrap/ServerBootstrap.java
```

Bootstrap sẽ tạo:

```text
config
DatabaseManager
repositories
resources
services
packet writers
NetworkServer
```

Không tiếp tục để `NetworkServer.fromSystemProperties()` phình theo mỗi feature.

`SessionServices` (hoặc bundle session tương đương trong production hiện tại) chỉ là coarse wiring object cho một accepted session; không biến nó thành global service locator chứa 10–20 service truyền đi khắp nơi.

Handler nên nhận dependency nó thực sự cần. Nếu một bundle chỉ được dùng để giảm constructor noise ở boundary, không truyền bundle đó sâu vào gameplay.

---

# 16. Naming rules — direct game vocabulary first

Ưu tiên tên mà developer game có thể hiểu ngay khi nhìn file tree:

```text
Player
Monster
Npc
Boss
Item
Skill
Effect
Zone
MapService
CombatService
MonsterTemplate
ItemTemplate
PlayerRepository
```

Tên phải mô tả **concept hiện tại**, không mô tả lịch sử refactor hoặc jargon kiến trúc nếu không cần.

Tránh nếu không có ambiguity thật:

```text
PlayerDomainModel
PlayerAggregateRoot
PlayerUseCaseExecutor
PlayerPersistenceGateway
WorldInteractionFacade
ClientCompatibilityConfiguration
```

## 16.1 Dùng context của package, không lặp lại context vào tên

Trong `monster/`, `MonsterFactory` nêu factory của feature.

Trong `network/packet/`, `PlayerPacketValidator` nêu trực tiếp trách nhiệm.

Trong `resource/`, `IconCatalog` mô tả collection dùng cho lookup.

## 16.2 Ý nghĩa suffix/prefix

Dùng các từ sau chỉ khi chúng thêm thông tin thật:

```text
Template  = static definition dùng để tạo/runtime-reference entity
Spawn     = static placement/seed của runtime entity
Factory   = có creation policy/composition đáng kể
Service   = orchestration/use-case/feature behavior qua nhiều object
Manager   = thật sự quản lý lifecycle/registry/collection authority
Registry  = lookup/ownership registry rõ ràng
Catalog   = read-oriented collection/static resource lookup
Snapshot  = immutable read view cần truyền qua boundary
Config    = configuration values
Validator = validation là responsibility chính
```

Không thêm suffix chỉ để class nghe “enterprise” hơn.

## 16.3 `Legacy`, `Runtime`, `Initial`, `Compatibility`, `Resource`

Các từ này là **qualifier**, không phải mặc định.

Chỉ giữ khi bỏ chúng đi tạo ambiguity thật.

`Legacy` hợp lý cho thứ tồn tại vì protocol/client compatibility:

```text
LegacyPacketCodec
LegacyCipher
LegacyTcpTransport
```

Không thêm các qualifier này chỉ vì dữ liệu bắt nguồn từ client hoặc server cũ, object đang sống trong memory, hay type được tạo lúc khởi tạo. Dùng tên trực tiếp khi không có ambiguity, ví dụ `MapTemplate`, `Waypoint`, `MonsterTemplate`, `Monster`, `MonsterFactory`, `PlayerProfileFactory`, và `PlayerPacketValidator`.

## 16.4 Tên phải scale sang mọi feature

Các convention trên áp dụng cho:

```text
player
monster
npc
boss
item
inventory
skill
effect
quest
clan
event
upgrade
...
```

Ví dụ:

```text
Npc / NpcTemplate / NpcService
Boss / BossTemplate / BossService
Item / ItemTemplate / ItemService
Skill / SkillTemplate / SkillService
```

Nhưng **không tạo đủ bộ chỉ vì convention tồn tại**. Chỉ tạo type khi feature có responsibility thật tương ứng.

Rename-only nên làm riêng, không trộn gameplay/concurrency/protocol behavior change.

---

# 17. File/type granularity and code readability rules

Không có giới hạn cứng số dòng/class và cũng **không có rule “mỗi record/class phải một top-level file”**.

Mục tiêu là tránh cả hai cực:

```text
God class                              ❌
mỗi result/value vài field một file    ❌
```

## 17.1 Khi nào type nên là top-level file

Ưu tiên top-level khi type có ít nhất một trong các đặc điểm:

```text
1. Có domain identity độc lập và tên đáng để tìm trực tiếp.
2. Được nhiều owner/subsystem dùng như contract riêng.
3. Có lifecycle/state/behavior đáng kể của riêng nó.
4. Là static definition/persistence/protocol contract độc lập.
5. Developer thường cần mở trực tiếp type đó để hiểu feature.
```

Ví dụ thường hợp lý:

```text
Monster
MonsterTemplate
MonsterSpawn
PlayerProfile
ItemTemplate
Zone
AccountRepository
```

Số dòng ít **không phải** lý do để bắt buộc nest type nếu type đó có identity thật.

## 17.2 Khi nào ưu tiên nested type

Ưu tiên nested `record/class/enum` khi type:

```text
1. Chỉ có ý nghĩa trong context của một owner.
2. Chỉ mô tả result/phase/state/detail của một owner.
3. Không cần được discover độc lập trong file tree.
4. Không có lifecycle/persistence/wire contract riêng.
5. Tách ra top-level chỉ làm folder tăng noise.
```

Ví dụ:

```java
Monster.Damage
Monster.Move
Monster.Respawn
Monster.Snapshot        // nếu snapshot chỉ thuộc Monster và không cần contract độc lập
MonsterDart.Phase
Npc.DialogOption
Boss.Phase
Skill.CastResult
```

Không áp dụng ví dụ máy móc. Nếu một type sau này được nhiều subsystem dùng như contract độc lập thì có thể promote thành top-level trong một refactor riêng.

## 17.3 Không tạo container giả để giấu fragmentation

Không tạo:

```text
MonsterModels.java
MonsterDtos.java
MonsterTypes.java
MonsterResults.java
CommonModels.java
GameData.java
```

chỉ để giảm số file.

Nếu các type không có một owner tự nhiên thì giữ top-level; nếu có owner tự nhiên thì nest vào owner.

## 17.4 Result/value naming

Không mặc định tạo một top-level file cho result nhỏ chỉ thuộc một owner.

Nếu result chỉ thuộc một owner, ưu tiên tên ngắn dưới owner:

```text
Monster.Damage
Monster.Move
Monster.Respawn
Npc.Interaction
Boss.PhaseChange
```

Suffix `Result` chỉ giữ khi object đó là contract độc lập, được nhiều owner trao đổi và tên không rõ nếu bỏ `Result`.

## 17.5 Enum/constants nhỏ

Enum/constants chỉ có ý nghĩa cho một owner nên ưu tiên nest:

```text
Monster.Status
Monster.MoveType
Boss.Phase
Npc.Type
```

Giữ top-level khi nó là contract dùng rộng xuyên feature/protocol/persistence.

Không tạo hàng loạt file 5–10 dòng chỉ để mỗi enum đứng riêng.

## 17.6 Khi nào nên split class behavior

Nên tách khi:

```text
1. Tên class không còn mô tả phần lớn việc nó làm.
2. Class chứa nhiều flow feature độc lập.
3. Feature mới cứ nối thêm block không liên quan.
4. Test của class trở thành mega-file nhiều responsibility.
5. Logic bị duplicate vì class ôm quá nhiều.
6. Muốn sửa một feature phải hiểu nhiều subsystem không liên quan.
```

Không tách chỉ vì:

```text
class dài
method dài
record có vài field
folder đang có ít file
pattern sách vở yêu cầu
```

Method dài vẫn chấp nhận nếu là một flow coherent, đọc top-to-bottom dễ hơn 10 helper giả tạo.

Chỉ extract helper khi:

```text
tên helper có ý nghĩa
ẩn detail gây nhiễu
reuse
hoặc cô lập validation/serialization/calculation
```

Không tạo nhiều method 1 dòng chỉ để giảm line count.

---

# 18. Anti-overengineering rules

Không tạo abstraction chỉ vì pattern.

Không tự động thêm:

```text
Facade
Gateway
Interactor
Strategy
Factory
Manager
Coordinator
EventBus
Command framework
generic Object[] state
```

nếu code hiện tại chưa có vấn đề mà abstraction đó giải quyết.

Nguyên tắc:

> Chỉ extract khi có nhu cầu thật.

Ưu tiên implementation trực tiếp, rõ nghĩa.

---

# 19. Client compatibility rules

Unity client là compatibility boundary hiện có.

Không sửa client chỉ để server architecture đẹp hơn.

Nếu server domain dùng type hẹp hơn nhưng legacy wire hỗ trợ type rộng hơn thì có thể giữ wire cũ nếu an toàn.

Ví dụ đã chốt:

```text
player HP/MP/damage trong server = int
wire legacy vẫn có thể writeLong
Unity vẫn ReadLong
```

Protocol range validation đặt gần protocol/serialization.

Không silent truncate.

Không silent clamp để che mismatch.

---

# 20. Current frozen player persistence invariants

```text
one account = one player

zoneId
→ runtime-only

durable position
→ mapId + x + y

exp
→ cumulative total EXP

no max_exp DB column

player base/current HP/MP/damage
→ int

power/potential/exp/coin/coinLock
→ long

diamond/ruby
→ int

monster HP/damage
→ long
```

DB/schema change phải cập nhật:

```text
reference SQL
repository mapping
tests
manual ALTER/recreate note nếu DB local đã tồn tại
real MySQL integration gate
```

Ứng dụng hiện không tự chạy migration.

---

# 21. Test architecture

Test ưu tiên mirror **feature ownership**, không mirror production package một cách máy móc.

Top-level test areas thường là:

```text
account/
player/
map/
monster/
combat/
resource/
network/
persistence/
testsupport/
```

Loader test nên ở gần loader ownership:

```text
resource/loader/MapLoaderTest
resource/loader/MonsterLoaderTest
resource/loader/SkillLoaderTest
```

Handler/unit test có thể nằm trong `network/handler/` **nếu** test trực tiếp handler và không cần package-private Session/network contract.

Protocol/session-oriented test có thể giữ ở `network/` khi primary subject là dispatcher + Session + state/wire behavior:

```text
MessageHandlerAuthTest
MessageHandlerMapTest
MessageHandlerCombatTest
MessageHandlerResourceTest
```

Không move test chỉ để tree đối xứng nếu việc move buộc:

```text
mở thêm production API
thêm getter chỉ cho test
thêm reflection không cần thiết
đổi package-private contract đang hữu ích
```

Integration test chia theo scenario/ownership khi file thật sự chứa nhiều flow độc lập:

```text
GameplayIntegrationTest
PlayerBootstrapIntegrationTest
ResourceIntegrationTest
```

Tên integration test không cần lặp `Network` nếu package `network/` và nội dung đã nói rõ context.

Không cần 1 test class cho mỗi command.

Không split test chỉ vì line count. Split khi scenario groups có ownership khác nhau hoặc file trở nên khó tìm/khó sửa.

Test support dùng plain helper, direct constructor và vocabulary hiện có. Không tạo test framework/DSL chỉ để “đẹp”.

---

# 22. Required gates

Normal gate:

```powershell
cd server
.\mvnw.cmd test
```

Hoặc Maven tương đương nếu wrapper chưa tồn tại trong checkout.

GitHub CI hiện chạy Java 21:

```text
mvn test
```

Normal CI không yêu cầu MySQL.

Nếu persistence/schema thay đổi phải chạy real DB integration gate trong `server/README.md`.

Nếu protocol thay đổi phải có focused/golden packet tests.

Nếu Session/Zone/concurrency thay đổi phải có regression tests cho ordering/race tương ứng.

Không được tuyên bố runtime PASS nếu chỉ review source/test tĩnh.

---

# 23. Refactor discipline

Architecture cleanup phải làm theo change set reviewable, nhưng không duy trì một “sequence lịch sử” đã hoàn thành như contract tương lai.

Mỗi cleanup/refactor phải xác định:

```text
exact baseline commit
scope hẹp
behavior/invariant phải giữ
rename/move/nest nào là mechanical
focused tests
full mvn test
manual/DB/Unity gate nếu thật sự liên quan
```

Ưu tiên thứ tự trong một cleanup pass:

```text
1. Characterize behavior/invariants nếu coverage chưa đủ.
2. Move/rename/nest type theo ownership, không đổi logic.
3. Compile + focused tests.
4. Sửa consumer/import/test names cơ học.
5. Full Maven gate.
6. Update README/authoritative rules nếu tree/naming contract thay đổi.
7. Review stale references trước khi đóng phase.
```

Không trộn cùng lúc nếu không bắt buộc:

```text
rename lớn + gameplay feature mới
package move + concurrency rewrite
file-granularity cleanup + protocol behavior change
architecture cleanup + schema migration
```

Nếu refactor chỉ đổi tên/move/nest type, không opportunistic thay algorithm/gameplay.

Nếu cleanup phát hiện bug thật ngoài scope:

```text
report finding
pin regression test
fix trong change/commit riêng
```

Không dùng “final cleanup” làm lý do sửa mọi thứ nhìn thấy.

---

# 24. Behavior refactor MUST preserve

Nếu plan không nói ngược lại, architecture cleanup phải giữ:

```text
legacy Unity protocol
register/login/create player
player persistence
same-account admission protection
disconnect final checkpoint
map movement
map change
monster combat
monster lifecycle
packet field order
normal Maven CI
real MySQL compatibility
```

Refactor architecture không phải permission đổi gameplay.

---

# 25. Future feature placement and scaling rules

Khi feature thật sự bắt đầu mới tạo package:

```text
item/
inventory/
skill/
effect/
disciple/
npc/
boss/
shop/
quest/
clan/
event/
upgrade/
```

Không tạo package chỉ vì roadmap có tên feature đó.

## 25.1 Start small

Feature nhỏ bắt đầu bằng số type tối thiểu:

```text
npc/
├── Npc.java
└── NpcService.java        // chỉ khi orchestration thật sự cần
```

Nếu có static data độc lập:

```text
npc/
├── Npc.java
├── NpcTemplate.java
└── NpcService.java
```

Không tự động thêm:

```text
NpcFactory
NpcManager
NpcRepository
NpcState
NpcResult
NpcDto
NpcMapper
```

nếu chưa có problem thật yêu cầu chúng.

## 25.2 Scale by responsibility, not symmetry

Khi feature lớn, split theo responsibility có nghĩa:

```text
player/
├── Player.java / PlayerProfile.java
├── PlayerService.java
├── stats/          // chỉ khi stats trở thành subsystem thật
├── progression/    // chỉ khi progression đủ lớn
└── ...
```

Không bắt `boss/` phải giống `monster/`.

Không bắt `npc/` phải giống `player/`.

Không bắt mọi feature có `Template + Factory + Service + Manager + Repository`.

## 25.3 Prefer game vocabulary over technical categories

Nếu developer hỏi “boss logic ở đâu?”, câu trả lời nên gần với:

```text
boss/
```

hoặc nếu Boss chỉ là một loại Monster và chưa có subsystem riêng:

```text
monster/
```

Không tạo package `boss/` chỉ vì noun tồn tại. Package mới cần ownership/behavior đủ độc lập.

## 25.4 Data source không quyết định domain type fragmentation

Hai JSON/file/table khác nhau không bắt buộc phải tạo hai domain class khác nhau.

Ví dụ:

```text
MonsterBootstrap.json
MonsterCombatBootstrap.json
```

có thể vẫn compose thành một `MonsterTemplate` nếu domain coi đó là một definition duy nhất.

Ngược lại, một JSON duy nhất có thể load thành nhiều type nếu domain thật sự có nhiều concept độc lập.

**Nguồn dữ liệu != boundary domain bắt buộc.**

---

# 26. Anti-patterns cấm mặc định

Không được tạo nếu chưa có lý do cụ thể:

```text
God Service.java chứa nhiều subsystem
MessageHandler chứa toàn bộ game
ResourceService parse mọi resource mãi mãi
MapService trở thành global gameplay service
SQL trong gameplay class
packet encoding trong model
DB write trong movement/combat hot path
global static service locator
reflection gameplay dispatch
generic Object[] runtime state
silent numeric truncation
silent clamping protocol mismatch
empty future packages
event bus chỉ để tránh direct call dễ đọc
factory/strategy/facade chỉ để giảm line count
mỗi record/result/enum vài field một top-level file theo mặc định
Models/Dto/Types/Results container chỉ để giấu quá nhiều file
prefix Legacy/Runtime/Initial/Compatibility không có ambiguity thật
feature package đối xứng máy móc (mọi feature đều Factory+Manager+Service+Repository)
```

---

# 27. Coding-model checklist bắt buộc

Trước khi sửa server, model code phải tự trả lời:

```text
1. Feature nào sở hữu behavior này?
2. File/package đúng là đâu?
3. Đây là protocol, gameplay, resource hay persistence logic?
4. Tôi có đang thêm code vào known God-class target không?
5. Có thể giữ Unity protocol hiện tại không?
6. Có đụng Session/account/Zone ordering không?
7. Có đụng durable DB/schema không?
8. Focused test nào chứng minh invariant mới?
9. Existing behavior nào phải giữ nguyên?
10. Type mới có cần top-level file thật không, hay thuộc về một owner rõ ràng?
11. Tên có đang lặp package context hoặc lịch sử implementation không?
12. Abstraction mới giải quyết vấn đề thật hay chỉ vì Design Pattern?
```

Nếu câu 12 là:

```text
"chỉ vì pattern"
```

thì không tạo abstraction đó.

---

# 28. Coding-model implementation rules

```text
MUST đọc code hiện tại trước khi sửa.
MUST dùng exact baseline khi plan/review yêu cầu.
MUST giữ scope hẹp.
MUST bảo toàn behavior ngoài scope.
MUST thêm/update focused tests cho contract thay đổi.
MUST chạy focused gate + full Maven gate.
MUST nói rõ manual/DB gate nào chưa thực chạy.
MUST NOT sửa Unity nếu feature không yêu cầu.
MUST NOT opportunistic-refactor code ngoài scope.
MUST NOT tạo top-level type nhỏ theo thói quen nếu type chỉ thuộc một owner.
MUST ưu tiên game vocabulary trực tiếp và package context khi đặt tên.
MUST NOT ép feature mới copy nguyên tree của feature khác.
MUST NOT hồi sinh architecture obsolete từ plan cũ.
MUST NOT coi static review là runtime proof.
```

Khi phân vân giữa abstraction thông minh và implementation trực tiếp dễ đọc:

> **Chọn implementation trực tiếp dễ đọc.**

---

# 29. Definition of good code

Code tốt trong project này giúp developer trả lời nhanh:

```text
Feature này ở đâu?
Nhìn tên file có đoán được vai trò chính không?
Type này có owner rõ không?
Rule này thuộc ai?
Packet vào ở đâu?
Gameplay chạy ở đâu?
State đổi ở đâu?
DB lưu ở đâu?
Packet trả về encode ở đâu?
Test nào chứng minh behavior?
```

Mục tiêu cuối cùng:

```text
easy to find
easy to read
easy to modify
hard to accidentally break
```

Không cần perfect architecture.
