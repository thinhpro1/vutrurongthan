# NETWORK IMPLEMENTATION PLAN — V2.4

> **Mục tiêu:** implement network đầu tiên, bám đúng Unity client C# và protocol source cũ, nhưng không làm mất kiến trúc dài hạn của Server V7.
>
> **Nguyên tắc V2:** compatibility trước, nhưng target architecture vẫn giữ:
>
> ```text
> transport
> → Session
> → codec/message
> → MessageHandler
> → service / WorldCommand
> ```
>
> Không tạo mọi file ngay ngày đầu. Implement theo từng gate.

---

# 1. Kết quả Network Core

Mốc đầu:

```text
Unity client cũ
→ TCP 127.0.0.1:1707
→ CONNECT_SERVER -128
→ nhận session key
→ bật XOR
→ nhận VERSION_SOURCE -127
→ gửi UPDATE_DATA -125
→ vào LoginScreen
```

Mốc tiếp:

```text
Register/Login/Create Player
```

Sau khi legacy ổn mới:

```text
LegacyTcpTransport
→ TlsTcpTransport
→ Admission + Ticket
```

---

# 2. Target package

```text
network/
├── Session.java
├── SessionManager.java
├── SessionState.java
├── MessageHandler.java
│
├── message/
│   ├── Message.java
│   ├── MessageName.java
│   ├── MessageReader.java
│   └── MessageWriter.java
│
├── codec/
│   ├── LegacyCipher.java
│   └── LegacyPacketCodec.java
│
├── transport/
│   ├── ClientTransport.java
│   ├── LegacyTcpTransport.java
│   └── TlsTcpTransport.java
│
└── packet/
    └── created later by feature
```

Trong phase legacy:
- tạo `ClientTransport`.
- tạo `LegacyTcpTransport`.
- chưa implement `TlsTcpTransport` cho tới Gate TLS.
- chưa tạo PacketWriter rỗng.

---

# 3. ClientTransport

Abstraction nhỏ, đáng giữ vì TLS chắc chắn sẽ có.

```java
public interface ClientTransport {

    InputStream input();

    OutputStream output();

    String remoteAddress();

    void close() throws IOException;
}
```

`LegacyTcpTransport` wrap `Socket`.

Sau này `TlsTcpTransport` wrap `SSLSocket`/TLS stream.

Session không cần biết transport là plain TCP hay TLS.

---

# 4. Primitive protocol

Client dùng:

```text
byte/sbyte  1 byte
short       2 byte big-endian
int         4 byte big-endian
long        8 byte big-endian
boolean     1 byte
UTF         2-byte byte-length + UTF-8 bytes
```

Không dùng Java `DataOutputStream.writeUTF()` cho game UTF.

Tạo `readUtf/writeUtf` dùng:

```java
StandardCharsets.UTF_8
```

---

# 5. Legacy first packet

Trước key:

```text
CONNECT_SERVER = -128
length = 0
```

Raw:

```text
80 00 00
```

Frame:

```text
[command:1][length high][length low][payload]
```

---

# 6. Handshake key

Giữ protocol cũ.

Legacy key compatibility:

```text
"abc"
```

Server payload:

```text
03 61 03 01
```

Full raw frame:

```text
80 00 04 03 61 03 01
```

Handshake key frame chưa XOR.

Client reconstruct:
- first byte length;
- cumulative XOR để ra `"abc"`.

---

# 7. XOR

Session có:
- read index.
- write index.

Không reset sau mỗi packet.

```java
public final class LegacyCipher {

    private final byte[] key;

    private int readIndex;

    private int writeIndex;

    public byte decode(byte value) { ... }

    public byte encode(byte value) { ... }
}
```

---

# 8. Normal encrypted frame

Sau handshake:

```text
[encrypted command]
[encrypted length high]
[encrypted length low]
[encrypted payload...]
```

Client → server normal length là 2 byte.

Test known frame:

```text
E2 62 62 9E
```

phải decode thành:

```text
command=-125
payload=[-1]
```

với key `"abc"` và cursor đúng trạng thái.

---

# 9. Special server response length

Server → client, 3 command dùng framing 3 byte riêng:

```text
VERSION_SOURCE = -127
REQUEST_ICON   = -22
UPDATE_DATA    = -125
```

Đây là command-based compatibility rule, không phải:

```text
payload lớn → tự dùng 3 byte
```

Giữ đúng thuật toán source/client cũ trước khi refactor.

---

# 10. Message model

```java
public final class Message {

    private final int command;

    private final byte[] payload;

    public Message(int command) {
        this(command, new byte[0]);
    }

    public Message(
        int command,
        byte[] payload
    ) {
        this.command = command;
        this.payload = payload;
    }

    public int command() {
        return command;
    }

    public byte[] payload() {
        return payload;
    }

    public MessageReader reader() {
        return new MessageReader(payload);
    }
}
```

Không để Message giữ socket/DB/session.

---

# 11. LegacyPacketCodec

Responsibility:

```text
transport bytes
↔
Message
```

Không xử lý:
- auth.
- player.
- map.
- shop.

API có thể là:

```java
public final class LegacyPacketCodec {

    public Message read(
        InputStream input,
        LegacyCipher cipher,
        boolean keyReady
    ) throws IOException { ... }

    public void write(
        OutputStream output,
        LegacyCipher cipher,
        boolean keyReady,
        Message message
    ) throws IOException { ... }

    public void writeHandshakeKey(
        OutputStream output,
        byte[] key
    ) throws IOException { ... }
}
```

---

# 12. Session state

Dùng enum nhỏ ngay từ đầu:

```java
public enum SessionState {
    CONNECTED,
    HANDSHAKE_DONE,
    AUTHENTICATED,
    IN_GAME,
    CLOSED
}
```

Không xây state-machine framework.

Transition:

```text
TCP accepted
→ CONNECTED

key sent
→ HANDSHAKE_DONE

login success
→ AUTHENTICATED

player loaded
→ IN_GAME

disconnect
→ CLOSED
```

---

# 13. Session threading

Java 21:

```text
1 virtual reader
+
1 virtual writer
```

per Session.

```text
Session
├── readLoop VirtualThread
└── writeLoop VirtualThread
```

Writer có bounded queue:

```java
BlockingQueue<Message> sendQueue =
    new ArrayBlockingQueue<>(256);
```

## Chính sách khi sendQueue đầy — CHỐT

Producer **không được block**:

```java
public void send(Message message) {

    if (state == SessionState.CLOSED) {
        return;
    }

    if (!sendQueue.offer(message)) {
        closeSlowClient();
    }
}
```

Không dùng:

```java
sendQueue.put(message);
```

trong GameLoop/service vì `put()` có thể block.

V1 policy:

```text
queue còn chỗ
→ enqueue

queue đầy
→ đánh dấu/log slow client
→ disconnect session
```

Không drop packet tùy tiện vì có thể phá ordering/state client.

Future optimization nếu benchmark thật sự cần:
- coalesce MOVE/position packet;
- replace stale low-priority state packet;
- priority queue theo packet class.

Nhưng không làm trước khi có số liệu.

Lý do:
- nhiều subsystem có thể gửi packet.
- tránh byte interleave.
- GameLoop không block vì slow socket.
- outbound ordering rõ.
- bounded memory per session.

---

# 14. Handshake write rule

Handshake key là ngoại lệ.

Flow:

```text
receive CONNECT_SERVER
→ write raw handshake key synchronously
→ flush
→ state HANDSHAKE_DONE
→ queue VERSION_SOURCE
```

Không:
- enqueue key.
- đổi state trước.
- để writer encrypt nhầm key.

---

# 15. Session responsibility

Session giữ:
- id.
- ClientTransport.
- input/output.
- LegacyCipher.
- state.
- send queue.
- reader/writer loops.
- remote address.
- User reference.
- Player reference.
- close.

Session không chứa:
- SQL Login.
- MapInfo serialization.
- Shop.
- Quest.
- Clan logic.

---

# 16. SessionManager

Dùng concurrent collections.

Responsibilities:
- runtime session ID.
- add/remove.
- find session.
- count online.
- per-IP count.
- closeAll.
- duplicate-account session check sau auth.

Không quản map/player world collections.

---

# 17. GameServer accept loop

Legacy phase:

```text
ServerSocket.accept()
→ socket options
→ LegacyTcpTransport
→ Session
→ SessionManager.add
→ Session.start
```

Socket options:
- TCP_NODELAY=true.
- KEEPALIVE=true.
- handshake read timeout khoảng 10s.

Sau handshake có thể bỏ read timeout.

---

# 18. MessageHandler

Ban đầu switch rõ ràng:

```java
switch (message.command()) {

    case CONNECT_SERVER:
        handleConnect(...);
        break;

    case UPDATE_DATA:
        handleUpdateData(...);
        break;

    case REGISTER:
        handleRegister(...);
        break;

    case LOGIN:
        handleLogin(...);
        break;

    default:
        ...
}
```

Khi file lớn thật sự mới tách handler theo domain.

Không cần handler registry/plugin architecture ngay.

---

# 19. Command state validation

Ví dụ:

```text
CONNECTED:
  CONNECT_SERVER

HANDSHAKE_DONE:
  UPDATE_DATA
  REGISTER
  LOGIN

AUTHENTICATED:
  CREATE_PLAYER
  PLAYER_INFO requests

IN_GAME:
  MOVE
  ATTACK
  SKILL
  MAP/SHOP/QUEST/CLAN...
```

Invalid state:
- dev: log + reject.
- hardened mode: count violation / disconnect khi cần.

---

# 20. VERSION_SOURCE

Config:

```properties
game.client.version=0.9.5
game.client.login-version=1
```

Payload:

```text
UTF "0.9.5"
```

Server response uses special 3-byte length framing because command `-127`.

---

# 21. UPDATE_DATA bootstrap

Client sau version sẽ gửi:

```text
command -125
type = -1
```

Network Gate chỉ cần:
- decode đúng;
- state đúng;
- route đúng;
- log type.

Không implement toàn bộ resource/template update ngay.

Resource sync làm sau khi auth/map cần nó.

---

# 22. Login/Register payload

Login:

```text
UTF clientVersion
UTF username
UTF password
sbyte loginVersion
```

Register:

```text
UTF username
UTF password
```

Create Player:

```text
UTF name
byte gender
```

Không log password.

---

# 23. Logging

Dev:

```text
SESSION_OPEN
RX cmd/len
HANDSHAKE_OK
STATE old→new
TX cmd/len
SESSION_CLOSE
```

Không log:
- password.
- ticket.
- installation credential.
- TLS private material.

Hex dump chỉ debug/test.

---

# 24. Validation

Packet:
- max normal size.
- special response max size.
- truncated EOF.
- negative/impossible length.
- malformed UTF.
- unknown command.

Config:

```properties
game.network.port=1707
game.network.max-session-per-ip=20
game.network.max-packet-size=65535
game.network.send-queue-size=256
game.network.handshake-timeout-ms=10000

game.security.mode=LEGACY_DEV
# later: SECURE_ONLINE
```

Benchmark local tăng max-session-per-ip.

---

# 25. Mandatory codec tests

## N1 Primitive endian
```text
writeInt(0x01020304)
→ 01 02 03 04
```

## N2 UTF
```text
writeUtf("abc")
→ 00 03 61 62 63
```

Test thêm Unicode tiếng Việt.

## N3 Handshake
```text
80 00 04 03 61 03 01
```

## N4 Continuous cipher
- nhiều packet liên tiếp.
- cursor không reset.

## N5 Known UPDATE_DATA frame
```text
E2 62 62 9E
```

decode đúng.

## N6 Special 3-byte VERSION_SOURCE
Replica client decoder đọc đúng.

## N7 Truncated packet
Declared length > actual bytes:
- throw.
- close session.
- no infinite loop.

## N8 Back-to-back outbound ordering
Client nhận đúng thứ tự.

---

# 26. Integration test trước Unity

Tạo Java test client:

```text
connect
→ CONNECT_SERVER
→ read/reconstruct key
→ read VERSION_SOURCE
→ send UPDATE_DATA -1
```

Dễ debug codec mà không phụ thuộc Unity.

---

# 27. Unity Network Gate 1

Không sửa network client trước.

Pass khi:

```text
[ ] connect 127.0.0.1:1707
[ ] key complete
[ ] VERSION_SOURCE decode
[ ] LoginScreen mở
[ ] UPDATE_DATA -1 tới server
[ ] session close sạch khi client thoát
```

Nếu chưa pass Gate 1:
- chưa làm gameplay.
- chưa thêm TLS.
- chưa đổi protocol.

---

# 28. Auth integration gate

Sau Gate 1:

```text
MessageHandler
→ AuthService
→ Repository
```

Pass:
- register.
- wrong password.
- valid login.
- duplicate login.
- SessionState AUTHENTICATED.
- Create Player decode.

---

# 29. TLS phase

Sau legacy + auth ổn.

Implement:

```text
TlsTcpTransport
```

Server:
- TLS 1.3.
- certificate/private key từ environment/secret.
- không commit private key.

Unity/C# client:
- `Session.cs` làm việc với `System.IO.Stream`.
- wrap `NetworkStream` bằng `System.Net.Security.SslStream`.
- validate hostname/certificate.

Java server:
- `TlsTcpTransport` wrap `SSLSocket` / Java TLS streams.
- `Session` chỉ nhìn `ClientTransport`, không phụ thuộc API .NET.

Game packet bên trong không đổi:

```text
TLS
→ legacy packet framing
→ XOR
→ Message
```

---

# 30. Admission phase

Sau TLS.

Giữ:
- AdmissionService.
- BuildVersionService.
- InstallationService.
- ConnectionTicketService.
- ConnectionTicketStore.
- InMemoryConnectionTicketStore.

## Hai mode xác thực — phải tách rõ

### LEGACY_DEV mode

Dùng khi bring-up/debug compatibility:

```text
TCP
→ legacy CONNECT_SERVER
→ LOGIN/REGISTER username/password qua game protocol
→ AuthService
```

Mục đích:
- tương thích client cũ;
- debug protocol;
- local development.

### SECURE_ONLINE mode

Final online/demo:

```text
Unity/C# client
→ HTTPS Register/Login
→ username/password chỉ đi qua HTTPS/TLS
→ validate build + installation
→ issue one-time ConnectionTicket
→ TLS Game TCP connect
→ send admission ticket
→ server consume ticket
→ bind authenticated account vào Session
→ load/create Player
```

Trong `SECURE_ONLINE`:

```text
legacy TCP LOGIN/REGISTER with username/password
→ disabled/rejected
```

Không duy trì hai đường password-auth song song trong production.

`AuthService` vẫn dùng chung phía server:
- HTTPS controller gọi AuthService;
- LEGACY_DEV MessageHandler cũng có thể gọi AuthService.

Khi chuyển client sang secure mode:
- LoginScreen gọi HTTPS trước;
- chỉ sau khi nhận ticket mới mở game TCP/TLS connection.

Có thể giữ command LOGIN/REGISTER trong protocol source để compatibility/dev,
nhưng production session-state policy không cho dùng chúng.

Ticket backend V1:
- ConcurrentHashMap.
- short TTL.
- atomic consume/remove.

Future:
- DB/Redis backend.

---

# 31. Network implementation order

```text
N0 MessageName + Message
N1 MessageReader/Writer
N2 LegacyCipher
N3 LegacyPacketCodec + tests
N4 ClientTransport + LegacyTcpTransport
N5 SessionManager
N6 Session + bounded writer queue
N7 GameServer accept loop
N8 MessageHandler bootstrap
N9 Java integration client
N10 Unity Gate 1
N11 Auth integration
N12 Session state validation/hardening
N13 TLS/TlsTcpTransport
N14 Admission + ticket/install/build
```

Không chuyển N13/N14 lên trước N10.

---

# 32. Network Core Definition of Done

## Legacy compatibility
- [ ] primitive IO.
- [ ] legacy XOR.
- [ ] special length frame.
- [ ] handshake.
- [ ] version.
- [ ] update-data bootstrap.
- [ ] Session reader/writer virtual threads.
- [ ] bounded outbound queue.
- [ ] clean close.
- [ ] SessionManager.
- [ ] Unity LoginScreen.

## Auth-ready
- [ ] Register/Login decode.
- [ ] AuthService integration.
- [ ] SessionState.
- [ ] duplicate login.

## Online security
- [ ] ClientTransport abstraction.
- [ ] TLS transport.
- [ ] certificate validation.
- [ ] build gate.
- [ ] installation identity.
- [ ] one-time ConnectionTicket.
- [ ] in-memory ticket store.
- [ ] rate limit basics.

---

# 33. Domain commands đã xác nhận nhưng implement sau Network Core

Network codec phải support các command này như command bình thường; business handler được thêm khi tới feature.

## NPC

```text
OPEN_NPC      -93
CONFIRM_MENU  -92
NPC_CHAT      -91
ADD_NPC       -48
REMOVE_NPC    -47
```

NPC template còn đi qua:

```text
UPDATE_DATA subtype 4
```

## Upgrade

Giữ 3 command chung:

```text
SHOW/CONFIG UPGRADE PANEL  -27   server → client
UPGRADE                    -37   client → server
CONFIRM_MENU               -92   client → server
```

Không tạo packet riêng cho từng mechanic.

## UI type trong `-27`

Client mới có panel riêng theo mechanic. Dùng `uiType/formatType` trong payload `-27` để chọn panel:

```text
0 → UPGRADE_ITEM
1 → UPGRADE_STAR
2 → DRILL_SOCKET
3 → UPGRADE_QUALITY
4 → reserved; chỉ bật sau khi mechanic thứ 5 được chốt
```

Exact field/payload phải giữ đồng bộ server + Unity và có integration test.

## Routing

```text
-27
→ client mở đúng Upgrade panel

-37
→ parse count + selected item indexes
→ lấy player.currentUpgradeType
→ UpgradeManager.get(type)
→ Upgrade.preview()

-92
→ parse selected menu index
→ lấy minimal player.pendingUpgrade
→ UpgradeManager.get(pending.type())
→ Upgrade.confirm()
```

`-37` không chứa authoritative `UpgradeType`, cost, rate hay result.

Network layer không:
- phân loại item;
- tính cost;
- RNG;
- mutate item;
- lưu `Object[]` context.

`CONFIRM_MENU -92` vẫn là command menu chung; domain/menu handler quyết định pending action hiện tại thuộc Upgrade hay chức năng khác.

Trước khi implement Upgrade:
- audit exact old `-27/-37/-92` payload;
- test `-27` uiType mở đúng panel mới;
- test `-37` chỉ gửi selected indexes;
- test `-92` chỉ gửi selected menu option;
- test back-to-back panel switch không route nhầm currentUpgradeType.



Không implement business logic NPC/Upgrade trong Network phase, nhưng phải **không coi các command này là unknown/invalid khi feature tương ứng được bật**.

Trước khi code Upgrade/NPC handler:
- audit exact payload `-27/-37/-92`.
- test `-37` chỉ chứa item indexes theo client hiện tại.
- test `-92` chỉ chọn menu option, không gửi lại cost/rate.
- thêm protocol integration tests.
- reuse client flow cũ nếu phù hợp.

---

# 34. Những thứ chưa làm trong Network phase

Không kéo vào trước khi cần:
- MapInfo đầy đủ.
- Movement/Combat.
- WorldCommandQueue.
- Shop/Quest.
- generic handler registry.
- protocol v2.
- Netty.
- Redis.
- multi-node.

Những phần này không bị “xóa khỏi kiến trúc”; chỉ chưa thuộc network milestone hiện tại.

---

# 35. Quy tắc chốt

> **Target architecture được giữ dài hạn, implementation được mở theo từng gate.**

Không tối giản kiến trúc tới mức sau này phải viết lại Session/transport.

Cũng không implement trước hàng loạt class chưa dùng.

Mốc đầu vẫn là:

```text
old Unity client
+
new Java server
=
legacy handshake đúng 100%
```

Sau đó mới lần lượt Auth → TLS → Admission.
