# Rongthan Java Server

Server game Java 21 dùng Maven, tương thích với binary protocol hiện có của Unity client. Server dùng plain Java, JDBC và MySQL, không cần application framework.

## Luồng chạy

```text
GameApplication
→ ServerBootstrap
→ NetworkServer
→ Session
→ MessageHandler và các feature handler
→ account và gameplay services
→ runtime models
→ repositories/JDBC khi cần persistence
```

`ServerBootstrap` nạp cấu hình và static resources, mở database đã cấu hình, rồi nối các thành phần network và gameplay. `Session` quản lý connection state và luồng message. Handler chuyển xử lý tới account, map, combat, monster và resource. Account và player được lưu qua JDBC repositories; membership trong zone và trạng thái monster đang chạy là runtime state.

## Build và chạy

Cần JDK 21. Chạy từ thư mục này:

```powershell
mvn test
mvn -q package
java -cp target/classes com.project.game.GameApplication
```

Maven test suite thông thường bao gồm TLS tests và không cần MySQL. Server khi chạy cần database đã cấu hình cùng hai bảng `account` và `player`. Mặc định server nghe tại `127.0.0.1:1707` qua `LEGACY_TCP`.

## Kiểm tra protocol với server thật

Khi server đang chạy, `ProtocolIntegrationClient` có thể kiểm tra kết nối và luồng player. Chạy `mvn test-compile` hoặc `mvn test` để tạo test classes trước.

```powershell
& "$env:JAVA_HOME\bin\java.exe" -cp "target/test-classes;target/classes" com.project.game.network.ProtocolIntegrationClient 127.0.0.1 1707
& "$env:JAVA_HOME\bin\java.exe" -cp "target/test-classes;target/classes" com.project.game.network.ProtocolIntegrationClient 127.0.0.1 1707 codex01 secret1
```

## Static resources và icon

Mặc định `game.resource.json-dir` là `resources/json`. Xem [hướng dẫn JSON resources](resources/json/README.md) để biết các file Java runtime loader sử dụng. Thư mục icon mặc định là `resources/icon`.

`game.resource.icon-dir` chọn thư mục icon; `game.resource.image-version` chọn image capability server gửi trong resource manifest. Khi có icon directory, giá trị hợp lệ là `1..127`; `-1` biểu thị icon resources không khả dụng. Image version mặc định là `2`, hỗ trợ per-icon fingerprint manifest mà client yêu cầu bằng `UPDATE_DATA` type `12`. Lệnh tải icon vẫn là `REQUEST_ICON` (`-22`).

Ở version 2, fingerprint cho biết icon nào đã đổi nên thay PNG thông thường không cần tăng image version. Hãy khởi động lại server sau khi thay PNG vì catalog ghi nhận fingerprint lúc khởi động. Ví dụ:

```powershell
java '-Dgame.resource.icon-dir=../client/Assets/Resources/SmallImages' '-Dgame.resource.image-version=2' -cp target/classes com.project.game.GameApplication
```

## Lưu account và player

`REGISTER` và `LOGIN` dùng bảng MySQL `account`; player được lưu trong bảng `player`. Áp dụng `database/schema/account.sql`, sau đó `database/schema/player.sql` vào database đã cấu hình. Ứng dụng không tự chạy các schema tham khảo này. Server kiểm tra hai bảng khi khởi động. Inventory, skill progression và quest state hiện chưa thuộc phạm vi persistence. `zoneId` của player chỉ tồn tại trong runtime; vị trí bền vững lưu `mapId`, `x` và `y`. `exp` là tổng kinh nghiệm tích lũy, không có cột `max_exp`.

Cấu hình JDBC URL, username và tên biến môi trường mật khẩu qua `game.db.url`, `game.db.username` và `game.db.password-env`. Biến mật khẩu mặc định là `GAME_DB_PASSWORD`. Real MySQL tests là opt-in, cần database `rongthanchibi` cùng hai bảng trên. Với database local không có mật khẩu:

```powershell
mvn `
  '-Dgame.db.integration-test=true' `
  '-Dgame.db.url=jdbc:mysql://localhost:3306/rongthanchibi' `
  '-Dgame.db.username=root' `
  '-Dgame.db.allow-empty-password=true' `
  '-Dtest=JdbcAccountRepositoryIntegrationTest,AuthServiceDatabaseIntegrationTest,JdbcPlayerRepositoryIntegrationTest,PlayerServiceDatabaseIntegrationTest' `
  test
```

Với database có mật khẩu, giữ mật khẩu ngoài repository:

```powershell
$env:GAME_DB_PASSWORD = 'your-local-password'

mvn `
  '-Dgame.db.integration-test=true' `
  '-Dgame.db.url=jdbc:mysql://localhost:3306/rongthanchibi' `
  '-Dgame.db.username=root' `
  '-Dgame.db.allow-empty-password=false' `
  '-Dtest=JdbcAccountRepositoryIntegrationTest,AuthServiceDatabaseIntegrationTest,JdbcPlayerRepositoryIntegrationTest,PlayerServiceDatabaseIntegrationTest' `
  test
```

## TLS 1.3 tùy chọn

Để certificate và private key bên ngoài repository. Cấu hình PKCS12 keystore và truyền mật khẩu qua environment variable:

```powershell
$env:GAME_TLS_KEYSTORE_PASSWORD = 'your-keystore-password'
& "$env:JAVA_HOME\bin\java.exe" `
  '-Dgame.network.transport=TLS' `
  '-Dgame.network.tls.keystore=C:\secrets\rongthan-server.p12' `
  '-cp' target/classes com.project.game.GameApplication
```

Keystore type mặc định là `PKCS12`, TLS protocol mặc định là `TLSv1.3`; cấu hình lần lượt bằng `game.network.tls.keystore-type` và `game.network.tls.protocol`. Packet bên trong TLS vẫn dùng legacy framing và cipher. Client kết nối cần hỗ trợ TLS khi server chạy ở chế độ này.
