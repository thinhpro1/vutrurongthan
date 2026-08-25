# NETWORK REVIEW ACTION PLAN — AFTER COMMIT `0633e423`

## Mục tiêu

Chuyển kết quả review hiện tại thành checklist sửa code để model/code agent có thể thực hiện trực tiếp.

Trạng thái:

```text
G1 Legacy Network Compatibility
→ code gần hoàn thành
→ verification chưa hoàn thành
```

Ưu tiên hiện tại là khóa G1, không phát triển thêm Auth/TLS/gameplay.

---

# P1 — BẮT BUỘC SỬA TRƯỚC UNITY G1

## 1. Hoàn thiện JUnit coverage N1–N8

### N1 — Primitive endian

Thêm JUnit verify:

```text
writeInt(0x01020304)
→ 01 02 03 04
```

Nên đặt tại:

```text
server/src/test/java/com/project/game/network/MessageReaderWriterTest.java
```

### N2 — UTF

Thêm JUnit verify:

```text
writeUtf("abc")
→ 00 03 61 62 63
```

và Unicode:

```text
"Rồng Thần"
```

Round-trip encode/decode phải đúng UTF-8.

### N3 — Handshake exact bytes

Assert chính xác:

```text
80 00 04 03 61 03 01
```

### N4 — Continuous cipher

Giữ test hiện tại với 3 packet liên tiếp, cùng writer cursor và cùng reader cursor.

### N5 — Known UPDATE_DATA frame

Bổ sung regression test:

```text
command = UPDATE_DATA (-125)
payload = [-1]
key = "abc"

expected wire:
E2 62 62 9E
```

Test phải assert chính xác 4 byte trên.

### N6 — Special 3-byte VERSION_SOURCE

Giữ independent Unity-style decoder hiện tại.

Không dùng lại `LegacyPacketCodec.readServerResponse()` để verify encoder của chính codec.

### N7 — Truncated packet

Đưa test từ `ProtocolSelfTest` sang JUnit:

```text
declared length > actual bytes
→ EOFException / IOException
→ no infinite loop
```

### N8 — Outbound ordering

Nâng test sang post-handshake encrypted path:

```text
SessionState.HANDSHAKE_DONE
→ send A
→ send B
→ send C
```

Client decoder dùng cùng một `LegacyCipher` và phải nhận:

```text
A
B
C
```

đúng thứ tự, không interleave.

---

# P1 — JAVA INTEGRATION PHẢI ASSERT `UPDATE_DATA -1`

Hiện integration test gửi `UPDATE_DATA -1` nhưng chưa chứng minh server thật sự route packet đó.

Target:

```text
server received UPDATE_DATA
type == -1
```

Ưu tiên cách test không làm bẩn production code. Có thể dùng:

```text
inject observer
inject callback
test event sink
spy handler
```

Ví dụ conceptual:

```java
interface NetworkEventObserver {
    void onUpdateData(Session session, int type);
}
```

Production dùng no-op implementation, test inject observer và assert `type == -1`.

Done khi:

```text
connect
→ CONNECT_SERVER
→ handshake
→ VERSION_SOURCE
→ UPDATE_DATA -1
→ server xác nhận route type=-1
→ disconnect
→ onlineCount == 0
→ reconnect thành công
```

---

# P1 — CLOSE SESSION KHI HANDLER NÉM `RuntimeException`

`Session.readLoop()` cần xử lý runtime failure ở boundary.

Target:

```java
private void readLoop() {
    try {
        while (state() != SessionState.CLOSED) {
            Message message = codec.read(
                    input,
                    cipher,
                    state() != SessionState.CONNECTED
            );

            LOGGER.fine(() ->
                    "RX id=" + id
                    + " cmd=" + message.command()
                    + " len=" + message.payload().length
            );

            handler.onMessage(message);
        }
    } catch (IOException exception) {
        close("read failure or peer disconnect");
    } catch (RuntimeException exception) {
        LOGGER.log(
                Level.SEVERE,
                "Unhandled session failure id=" + id,
                exception
        );
        close("unexpected handler failure");
    }
}
```

Không catch `Throwable`.

Test bắt buộc:

```text
handler/service throws RuntimeException
→ Session CLOSED
→ transport closed
→ SessionManager onlineCount giảm
→ account mapping cleanup nếu có
```

---

# P1 — KHÔNG MỞ RỘNG `PLAYER_INFO` TRƯỚC G3

Hiện payload prototype:

```text
UTF playerName
byte gender
```

Chưa có bằng chứng đây là full payload Unity legacy cần.

Không thêm field theo suy đoán.

Giữ thứ tự:

```text
G1 → network compatibility
G2 → Register/Login/Create Player
G3 → audit PLAYER_INFO + MAP_INFO
```

Khi sang G3 phải đọc trực tiếp:

```text
Unity Controller.cs
legacy server serializer
MessageName.PLAYER_INFO
```

rồi mới chốt payload.

---

# P2 — HARDENING

## Per-IP regression test

Test:

```text
maxPerIp = 2

A from 127.0.0.1 → accept
B from 127.0.0.1 → accept
C from 127.0.0.1 → reject

close A
D from 127.0.0.1 → accept
```

Verify counter không leak.

## Queue overflow regression test

Với queue nhỏ:

```text
queue size = 1
writer bị block
send A
send B
```

Khi overflow:

```text
send() returns false
session CLOSED
transport closed
manager cleanup
```

Producer không được block.

---

# PHẦN ĐÃ PASS — KHÔNG REFACTOR LẠI

Không sửa lại nếu không có bug mới:

```text
Java 21
Virtual threads
ClientTransport abstraction
LegacyTcpTransport per-IP address
bounded ArrayBlockingQueue
sendQueue.offer()
handshake raw write ordering
continuous XOR cursor architecture
special server framing rule
Session.start() cleanup
duplicate-account bind cleanup
strict loginVersion
trailing login payload reject
NetworkConfig
SESSION_OPEN / RX / TX / HANDSHAKE_OK / STATE / SESSION_CLOSE logging
JUnit 5 + Surefire setup
independent Unity-style N6 decoder
```

---

# STATE POLICY HIỆN TẠI

Giữ strict validation:

```text
CONNECTED
→ CONNECT_SERVER

HANDSHAKE_DONE
→ UPDATE_DATA
→ REGISTER
→ LOGIN

AUTHENTICATED
→ CREATE_PLAYER

IN_GAME
→ chưa mở gameplay command cho đến feature gate tương ứng
```

Không quay lại:

```java
case IN_GAME -> true;
```

---

# VERIFY SAU KHI SỬA

## Maven

Chạy:

```bash
cd server
mvn test
```

Yêu cầu:

```text
N1 PASS
N2 PASS
N3 PASS
N4 PASS
N5 PASS
N6 PASS
N7 PASS
N8 PASS
Session lifecycle PASS
SessionManager PASS
NetworkConfig PASS
Java integration PASS
```

## Java G1 Integration

Flow:

```text
connect
→ CONNECT_SERVER
→ key "abc"
→ enable XOR
→ VERSION_SOURCE "0.9.5"
→ UPDATE_DATA -1
→ server xác nhận route type=-1
→ disconnect
→ onlineCount = 0
→ reconnect
→ repeat PASS
```

---

# UNITY G1 — CHỈ CHẠY SAU KHI TEST TRÊN PASS

Không refactor network client Unity trước.

Flow:

```text
Unity client
↓
127.0.0.1:1707
↓
CONNECT_SERVER
↓
raw session key
↓
Unity reconstruct "abc"
↓
XOR enabled
↓
VERSION_SOURCE
↓
LoginScreen
↓
Service.UpdateData(-1)
↓
server nhận UPDATE_DATA type=-1
```

Checklist:

```text
[ ] Unity connect thành công
[ ] SESSION_OPEN
[ ] CONNECT_SERVER được nhận
[ ] HANDSHAKE_OK
[ ] Unity reconstruct đúng "abc"
[ ] VERSION_SOURCE decode đúng
[ ] LoginScreen mở
[ ] UPDATE_DATA type=-1 tới server
[ ] Unity exit → SESSION_CLOSE
[ ] onlineCount về 0
[ ] reconnect thành công
[ ] không ghost session
[ ] không ghost per-IP count
```

Chỉ khi tất cả pass mới ghi:

```text
G1 = PASS
```

---

# KHÔNG LÀM TRONG LẦN SỬA NÀY

Không thêm:

```text
Repository/DB
BCrypt migration
PLAYER_INFO full payload
MAP_INFO
Movement
Combat
WorldCommandQueue
Admission
ConnectionTicket
Unity SslStream
TLS feature mới
Shop
Quest
Clan
```

TLS hiện có giữ nguyên/freeze:

```properties
game.network.transport=LEGACY_TCP
```

---

# THỨ TỰ SỬA ĐỀ XUẤT

```text
1. JUnit N1/N2/N3/N5/N7
2. Upgrade N8 encrypted path
3. Integration observer cho UPDATE_DATA -1
4. RuntimeException session boundary
5. Per-IP regression test
6. Queue overflow regression test
7. mvn test
8. Java integration G1
9. Unity G1
```

---

# DEFINITION OF DONE

```text
G1 LEGACY NETWORK COMPATIBILITY
===============================

Java 21                  PASS
Virtual threads          PASS
Transport abstraction   PASS
Per-IP                   PASS
Lifecycle cleanup        PASS
State validation         PASS
Config version           PASS
Strict login payload     PASS
Logging                  PASS

N1 Primitive endian      PASS
N2 UTF                    PASS
N3 Handshake              PASS
N4 Continuous cipher      PASS
N5 Known frame            PASS
N6 Special framing        PASS
N7 Truncated packet       PASS
N8 Outbound ordering      PASS

Java integration         PASS
UPDATE_DATA routed       PASS
Clean disconnect         PASS
Reconnect                PASS

Unity real client         TODO until manually verified
```

Sau khi Unity checklist xanh:

```text
G1 = PASS
```

Sau đó mới chuyển sang:

```text
G2 Auth / Register / Login / Create Player
```
