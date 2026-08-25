# NETWORK STABILIZATION PLAN — AFTER COMMIT `a752992`

## Mục tiêu

Không viết thêm gameplay, Admission hay mở rộng TLS ở thời điểm này.

Đi theo thứ tự:

```text
Current network
↓
Fix lifecycle/state
↓
Complete protocol tests
↓
Pass Java Integration
↓
Pass REAL Unity Gate G1
↓
Freeze Network Core
↓
G2 Auth/Create Player
↓
G3 PlayerInfo/Map
↓
TLS quay lại sau
```

Plan gốc quy định legacy compatibility phải ổn trước; Unity Gate 1 chưa pass thì chưa nên thêm TLS/gameplay.

---

# PHASE 1 — Sửa Network Core còn lỗi

**Priority: cao nhất**

## 1.1 Fix `Session.start()` failure cleanup

Hiện flow:

```text
SessionManager.tryAdd()
↓
session.start()
↓
IOException
↓
catch ở NetworkServer
```

Session có thể đã nằm trong manager nhưng không được remove.

Target:

```java
if (!sessions.tryAdd(session, maxSessionsPerIp)) {
    transport.close();
    continue;
}

try {
    session.start();
} catch (IOException e) {
    session.close();
    throw e;
}
```

### Done khi

```text
start fail
→ socket close
→ session remove
→ per-IP counter giảm
→ onlineCount không bị ghost session
```

---

## 1.2 Fix command validation ở `IN_GAME`

Hiện tại:

```java
case IN_GAME -> true;
```

Nghĩa là login lại, register lại, handshake lại hoặc command rác đều lọt qua.

Không nên dùng:

```java
case IN_GAME -> true;
```

Tạm thời chỉ whitelist command đã thực sự implement.

Ví dụ G2 chưa có gameplay:

```java
case IN_GAME -> switch (command) {
    // thêm command khi feature tương ứng được implement
    default -> false;
};
```

Sau này G4/G6/G7 mới dần thêm:

```text
MOVE
CHANGE_MAP
SKILL
ATTACK
SHOP
NPC
QUEST
CLAN
...
```

---

## 1.3 Fix duplicate-account race

Hiện flow:

```text
tryBindAccount(session, account)
↓
session.bindAccount(account)
```

Có một khoảng race ở giữa hai bước này.

Target nên là một operation thống nhất:

```java
boolean bindAccount(Session session, String accountName)
```

Bên trong manager xử lý:

```text
check session chưa CLOSED
→ putIfAbsent account
→ bind account vào session
→ rollback nếu session đóng giữa chừng
```

Không để `Session` và `SessionManager` mỗi bên giữ một nửa transaction.

### Done khi test được

```text
session A login user1
→ session A disconnect
→ session B login user1
→ SUCCESS
```

và không có account ghost.

---

# PHASE 2 — Hoàn thiện Protocol Test đúng N1–N8

Hiện `ProtocolSelfTest` vẫn chưa thực sự chứng minh N4/N8.

## 2.1 N4 — Continuous cipher thật

Không test một packet.

Phải test kiểu:

```text
Cipher write cursor
↓
packet A
↓
packet B
↓
packet C
```

Sau đó phía reader dùng **một LegacyCipher duy nhất**:

```text
decode A
decode B
decode C
```

và verify cả 3.

Không được tạo cipher mới sau mỗi packet.

---

## 2.2 N6 — Client-compatible special frame

Hiện test:

```text
server encoder
→ server decoder
```

chưa đủ mạnh.

Nên viết một decoder nhỏ mô phỏng đúng C#:

```text
read command
↓
ReadKey(length byte 1) + 128
ReadKey(length byte 2) + 128
ReadKey(length byte 3) + 128
↓
reconstruct length
```

đúng logic client `Session.cs`.

Không reuse `LegacyPacketCodec.readServerResponse()` cho test này.

---

## 2.3 N8 — Back-to-back outbound ordering

Test:

```text
session.send(A)
session.send(B)
session.send(C)
```

Client phải nhận:

```text
A
B
C
```

không:

```text
A
C
B
```

và bytes không interleave.

---

## 2.4 Đưa tests vào test lifecycle thật

Hiện repo chỉ có:

```text
server/src/main/
```

Nên chuyển dần sang:

```text
server/
└── src/
    ├── main/
    │   └── java/
    └── test/
        └── java/
            └── com/project/game/network/
                ├── MessageReaderWriterTest.java
                ├── LegacyCipherTest.java
                ├── LegacyPacketCodecTest.java
                ├── SessionTest.java
                └── NetworkIntegrationTest.java
```

Mục tiêu:

```bash
mvn test
```

phải thực sự chạy toàn bộ network regression.

Không nên để README nói test pass nhưng Maven không chạy các executable self-test tự động.

---

# PHASE 3 — Clean config + logging

## 3.1 Bỏ hard-code version

Hiện config có:

```properties
game.client.version=0.9.5
game.client.login-version=1
```

nhưng `MessageHandler` vẫn có:

```java
private static final String CLIENT_VERSION = "0.9.5";
private static final int LOGIN_VERSION = 1;
```

Target:

```text
application.properties
↓
NetworkConfig
↓
MessageHandler
```

Không duplicate config.

---

## 3.2 Login payload strict

Plan quy định:

```text
UTF clientVersion
UTF username
UTF password
sbyte loginVersion
```

Nhưng hiện `loginVersion` optional.

Đổi từ:

```java
if (reader.remaining() > 0) {
    ...
}
```

thành:

```java
int loginVersion = reader.readByte();
```

và sau parse:

```java
if (reader.remaining() != 0) {
    reject malformed packet;
}
```

---

## 3.3 Logging đủ để debug Unity

Thêm tối thiểu:

```text
SESSION_OPEN id/ip
RX id/cmd/len
HANDSHAKE_OK
STATE old -> new
TX id/cmd/len
SESSION_CLOSE id/reason
```

Không log:

```text
password
TLS secret
ticket
credential
```

---

# PHASE 4 — Java Integration Gate

`ProtocolIntegrationClient` mới thêm là đúng hướng.

Trước Unity, bắt buộc pass:

```text
Java client
↓
TCP connect
↓
CONNECT_SERVER -128
↓
receive raw abc key
↓
enable XOR
↓
receive VERSION_SOURCE
↓
send UPDATE_DATA -1
↓
clean disconnect
```

Test thêm:

```text
wrong handshake order
malformed packet
packet quá max size
disconnect giữa handshake
queue overflow
reconnect
per-IP limit
```

## Gate J1

Chỉ PASS khi:

```text
[ ] handshake
[ ] XOR
[ ] version
[ ] update-data
[ ] ordering
[ ] malformed close
[ ] no session leak
[ ] no IP-count leak
```

---

# PHASE 5 — Unity Gate G1 thật

Đây mới là gate quan trọng.

Giữ nguyên client legacy `Session.cs`, không refactor network client lúc này.

Flow:

```text
Unity client
↓
127.0.0.1:1707
↓
CONNECT_SERVER
↓
receive key
↓
VERSION_SOURCE
↓
Controller xử lý version
↓
Service.UpdateData(-1)
↓
LoginScreen
```

## G1 PASS checklist

```text
[ ] Unity kết nối được
[ ] server thấy SESSION_OPEN
[ ] server nhận CONNECT_SERVER
[ ] Unity reconstruct được "abc"
[ ] XOR bật đúng
[ ] Unity nhận VERSION_SOURCE
[ ] LoginScreen xuất hiện
[ ] server nhận UPDATE_DATA type=-1
[ ] client thoát → session sạch
[ ] reconnect lại được
[ ] không ghost session
```

Nếu một ô chưa đạt:

```text
G1 = FAIL
```

Không chuyển phase.

---

# PHASE 6 — G2 Auth / Register / Create Player

Phần Auth hiện tại nên xem là **prototype**, chưa phải G2 final.

Theo schedule hiện tại, G2 target là:

```text
UserEntity
UserRepository
BCrypt
AuthService

PlayerEntity
PlayerRepository
PlayerMapper
Player runtime
```

Không nên giữ user thật trong memory map.

Flow:

```text
MessageHandler
↓
AuthService
↓
Repository
↓
DB
```

## G2 test

```text
[ ] register valid
[ ] duplicate username
[ ] wrong password
[ ] valid login
[ ] duplicate login
[ ] reconnect after logout
[ ] AUTHENTICATED state
[ ] create player
[ ] duplicate player name
[ ] relogin loads existing player
[ ] password không xuất hiện trong log
```

---

# PHASE 7 — Không làm `PLAYER_INFO` sớm

Hiện `sendPlayerInfo()` mới gửi:

```text
UTF playerName
byte gender
```

Không nên giả định client command `PLAYER_INFO` chỉ cần hai field đó.

Tạm thời G2:

```text
login success
↓
AUTHENTICATED
↓
no player
    → START_CREATE_PLAYER_SCREEN
```

Nếu player tồn tại thì chưa cần tự chế `PLAYER_INFO` cho đến G3.

G3 mới audit:

```text
Controller.cs
legacy server
PLAYER_INFO parser
```

rồi implement payload chính xác.

---

# PHASE 8 — Freeze TLS

Không xóa phần TLS đã code.

Hiện đã có:

```text
TlsTcpTransport
TlsContextFactory
TlsTransportSelfTest
TlsNetworkSelfTest
```

Nhưng tạm thời:

```properties
game.network.transport=LEGACY_TCP
```

và **không phát triển thêm TLS**.

Sau:

```text
G1 PASS
↓
G2 PASS
↓
G3/G4 core gameplay ổn
↓
quay lại TLS gate
```

Lúc quay lại TLS mới xử lý tiếp:

```text
TLS handshake không block accept loop
Unity SslStream
hostname validation
certificate validation
Admission
ConnectionTicket
SECURE_ONLINE
```

---

# Thứ tự commit khuyến nghị

| Commit | Nội dung | Không kèm |
|---|---|---|
| `network-fix-lifecycle` | start cleanup + duplicate account race | Auth feature mới |
| `network-state-validation` | strict command whitelist | gameplay |
| `network-tests-n1-n8` | N4/N6/N8 + Maven test | TLS |
| `network-config-logging` | config version + logs | DB |
| `network-g1-integration` | Java + Unity G1 fixes | Auth |
| `auth-g2` | Repository + BCrypt + create player | PlayerInfo |
| `player-g3` | PLAYER_INFO + MAP_INFO | combat |
| `tls-g5` | TLS/Admission hoàn chỉnh | feature khác |

Nếu một commit làm cùng lúc:

```text
Network
+ Auth
+ TLS
+ PlayerInfo
```

thì khi Unity lỗi sẽ rất khó xác định lỗi nằm ở framing, state, auth hay payload.

---

# Definition of Done gần nhất

Việc tiếp theo không phải viết thêm Auth/TLS.

Mục tiêu gần nhất duy nhất nên là:

```text
G1 LEGACY NETWORK COMPATIBILITY
===============================

Java 21                 PASS
Virtual threads         PASS
Transport abstraction  PASS
Per-IP                  PASS
Lifecycle cleanup       TODO
State validation        TODO
N4                      TODO
N8                      TODO
Maven test suite        TODO
Java integration        VERIFY
Unity real client       TODO
Clean disconnect        TODO
```

Khi toàn bộ phần này xanh, mới ghi:

```text
G1 = PASS
```

rồi chuyển sang **G2 Auth/Create Player**.
