# Rongthan network server (new project)

Đây là project network mới độc lập theo `04_NETWORK_IMPLEMENTATION_PLAN_V2_4.md`.
Nó không import và không ghi đè `../server/src` legacy.

Đã triển khai các gate N0–N12 ở mức `LEGACY_DEV`:

- message primitives big-endian và UTF-8 tương thích Unity cũ;
- continuous legacy XOR cipher;
- framing 2-byte và special 3-byte cho `VERSION_SOURCE`, `REQUEST_ICON`, `UPDATE_DATA`;
- `ClientTransport`/`LegacyTcpTransport`;
- session state, session manager, bounded outbound queue;
- TCP accept loop và bootstrap `CONNECT_SERVER → key → VERSION_SOURCE`;
- state validation cơ bản và protocol tests.
- Java 21 virtual threads cho reader/writer mỗi session;
- Java integration client cho N9;
- auth-ready N11: PBKDF2, register/login, duplicate-account guard, create player;
- protocol-violation counter và hardening giới hạn packet/session.
- N13 TLS 1.3 transport tùy chọn, keystore lấy từ cấu hình ngoài repo và password từ environment.

`pom.xml` yêu cầu Java 21. IDE cần chọn platform `JDK_21` trỏ tới
`C:\Program Files\Java\jdk-21` trước khi reload/index project.

## Chạy test

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
mvn test
& "$env:JAVA_HOME\bin\java.exe" -cp "target/test-classes;target/classes" com.project.game.network.ProtocolSelfTest
& "$env:JAVA_HOME\bin\java.exe" -cp "target/test-classes;target/classes" com.project.game.network.transport.TlsTransportSelfTest
& "$env:JAVA_HOME\bin\java.exe" -cp "target/test-classes;target/classes" com.project.game.network.TlsNetworkSelfTest
& "$env:JAVA_HOME\bin\java.exe" -cp "target/test-classes;target/classes" com.project.game.network.ProtocolIntegrationClient 127.0.0.1 1707
```

Để bật phục vụ icon từ thư mục local (chỉ dành cho DEV), chạy server với:

```powershell
java '-Dgame.resource.icon-dir=../client/Assets/Resources/SmallImages' -cp target/classes com.project.game.GameApplication
```

## Chạy server

```powershell
.\mvnw.cmd -q package
java -cp target/classes com.project.game.GameApplication
```

Test cả auth/create-player:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -cp "target/test-classes;target/classes" com.project.game.network.ProtocolIntegrationClient 127.0.0.1 1707 codex01 secret1
```

Mặc định server nghe `127.0.0.1:1707`. Admission ticket, repository/DB thật và domain packet writers vẫn để ở các gate sau theo plan.

## Bật TLS 1.3

Không đưa certificate/private key vào repository. Tạo keystore PKCS12 bên ngoài project,
sau đó chạy server với các system property và environment variable sau:

```powershell
$env:GAME_TLS_KEYSTORE_PASSWORD = 'your-keystore-password'
& "$env:JAVA_HOME\bin\java.exe" `
  '-Dgame.network.transport=TLS' `
  '-Dgame.network.tls.keystore=C:\secrets\rongthan-server.p12' `
  '-cp' target/classes com.project.game.GameApplication
```

Các packet bên trong TLS vẫn dùng framing legacy và XOR hiện có. Client Unity sẽ được
chuyển sang `SslStream` ở gate client/security tương ứng; N13 hiện mới hoàn tất Java server transport.
