# Rongthan network server (new project)

Đây là project network mới độc lập theo `04_NETWORK_IMPLEMENTATION_PLAN_V2_4.md`.
Nó không import và không ghi đè `../server/src` legacy.

Đã triển khai các gate N0–N8:

- message primitives big-endian và UTF-8 tương thích Unity cũ;
- continuous legacy XOR cipher;
- framing 2-byte và special 3-byte cho `VERSION_SOURCE`, `REQUEST_ICON`, `UPDATE_DATA`;
- `ClientTransport`/`LegacyTcpTransport`;
- session state, session manager, bounded outbound queue;
- TCP accept loop và bootstrap `CONNECT_SERVER → key → VERSION_SOURCE`;
- state validation cơ bản và protocol tests.

## Chạy test

```powershell
mvn test
java -cp target/classes com.project.game.network.ProtocolSelfTest
```

## Chạy server

```powershell
.\mvnw.cmd -q package
java -cp target/classes com.project.game.GameApplication
```

Mặc định server nghe `127.0.0.1:1707`. Auth, TLS và domain packet writers vẫn để ở các gate sau theo plan.
