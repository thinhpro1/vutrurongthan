# Rà soát refactor Map / Zone / Player / Monster — 2026-10-04

Kết quả review ban đầu: phần tổ chức trách nhiệm và giảm file vụn đã đạt trong phạm vi các đợt vừa làm. Chưa đạt toàn bộ mục tiêu ban đầu về chuyển map, vòng đời Zone và persistence. Hai lỗi concurrency và thiếu normalize được ghi bên dưới đã được sửa trong đợt triển khai handoff/disconnect ngày 2026-10-04.

## Cập nhật sau triển khai handoff/disconnect

Đã xử lý ba finding của review ban đầu và các race bổ sung phát hiện khi kiểm tra:

- `MapManager` ghi nhận từng handoff theo exact Session trước reserve; `Zone` vẫn là authority của reservation/membership. Chỉ một handoff được thực hiện tại một thời điểm. Rollback hủy reservation trước khi bỏ bookkeeping.
- Admission mới xóa handoff tương ứng ngay trên destination writer, sau bind và trước delivery. Lời gọi `finishLoad` cũ trả về muộn hoặc đọc route cũ không thể xóa handoff mới hay để lại guard đã được consume. Nhánh joined/already-present không xóa guard đang chạy.
- Disconnect drain nguồn, pending/logical/actual destination; remove exact membership và reservation, clear backlink trước broadcast, sau đó mới capture. Lỗi enqueue observer không bỏ qua cleanup đích hoặc final capture. Cleanup không xóa Session mới của cùng Player id.
- Player chết được `revive` về home bằng current max HP/MP trước final capture. Closed Session không join/reserve home. Zone đã dừng và không detach được thì không lấy một bản capture không an toàn.
- Checkpoint chuyển map gọi `Session.savePlayer`. Lượt lưu thường và final save dùng cùng lock riêng; CLOSED được kiểm tra bên trong lock. Final save chờ lượt ghi đã bắt đầu, kể cả khi final capture thất bại, rồi mới nhả account. Login bootstrap hoàn tất cũng không nhả account khi final checkpoint chưa xong.

Đọc luồng travel/disconnect từ `MapManager.java`, rồi `Zone.enter/leave` để xem admission/detach. Player vẫn quyết định `changeMap/revive`; Zone quyết định thời điểm mutation. `Session.java` và `SessionManager.java` chứa ordering checkpoint/account, `PlayerManager`/Repository giữ persistence, `AreaService`/PacketWriter giữ send/encoding. `Handoff` là identity nhỏ nằm trong MapManager cho ranh giới concurrency, không thêm file hay layer. Bỏ dependency PlayerManager không còn dùng trong MapHandler; giữ các tên `enter`, `leave`, `savePlayer`, `revive` và public điều phối tại MapManager.

Legacy đối chiếu cho đợt này: `map/Zone.java`, `map/MapManager.java`, `entity/player/Player.java` (`joinMap`, `teleport`, `logout/saveData`) và `entity/player/PlayerManager.java` (`saveData`). Giữ tên game và lifecycle trực tiếp; không chép save-before-detach, DB trong entity, singleton mutable hoặc lock cho nhiều thread tự mutation. Không đổi protocol, SQL/schema hay Unity.

Thêm **16 regression test** vào MapManagerTest/SessionTest. Các race chính được kiểm chứng RED → GREEN, gồm các stale-route variant; test queued checkpoint còn được thử với mutation cố ý dời CLOSED check ra ngoài lock và đã bắt lỗi. Final focused gate: **164 pass, 0 failure/error/skip**. Full Maven 3.9.9 / JDK 21 `mvn test`: **539 total, 535 pass, 4 skip, 0 failure/error, BUILD SUCCESS**; không có mvnw.cmd trong repo nên dùng Maven đã cài. TLS network/transport tự động pass; `git diff --check` pass. Hai review độc lập không còn finding trong phạm vi này.

Log cuối: [.superpowers full-final.log](D:/doan11/vutrurongthan/.superpowers/sdd/2026-10-04-handoff-disconnect/full-final.log). Chưa chạy 4 MySQL opt-in test, race với DB thật, Unity manual, TLS triển khai thực tế, gameplay production/server đang chạy hoặc load/profile nhiều Zone. Không suy ra runtime production thành công từ test tự động. Policy log-and-release sau checkpoint failure được giữ nguyên. Chọn public Zone, teleport/goBack còn thiếu, game loop/freeze và periodic dirty checkpoint vẫn là các phase tiếp theo; chưa thay đổi trong đợt này. Chưa commit/push.

## Review ban đầu (trước bản sửa trên)

Review trên working tree hiện tại, gồm cả thay đổi chưa commit và file mới, tại HEAD `e5431fdc636e17889a33381760ac3d0643645057`. Lượt này không sửa code sản xuất; chỉ tạo báo cáo và kiểm tra trong thư mục làm việc bị Git ignore.

`SERVER_RULES.md` là authority; `SERVER_RUNTIME_ARCHITECTURE_BASELINE.md` xác định hợp đồng hiện tại và hướng migration. Tệp `C:/Users/Admin/Downloads/RONGTHANCHIBI_REFACTOR_BASELINE.md` được dùng để đối chiếu mục tiêu ban đầu, không thay thế rules. Các mục `DONE` của N1/N2/N3 chỉ xác nhận lát refactor tương ứng, không chứng minh toàn bộ roadmap đã hoàn tất.

## Các finding tại thời điểm review ban đầu

### 1. P1 — Disconnect khi handoff commit có thể để lại reservation ở Zone đích

[MapManager.leave](D:/doan11/vutrurongthan/server/src/main/java/com/project/game/map/MapManager.java:103) đọc `Session.zone` một lần. Chỉ nhánh backlink đã null mới hủy reservation đích; nhánh còn backlink gọi `zone.leave(session)` rồi trả kết quả ngay.

Interleaving đã tái hiện:

1. Chuyển map đã reserve đích và vượt qua source revalidation.
2. Disconnect đặt Session thành CLOSED trong lúc source chưa clear backlink.
3. `leave` chọn source và xếp cleanup lên source writer.
4. Handoff commit, cập nhật Player sang đích và detach source.
5. Cleanup chạy trên source sau đó, thấy Session không còn là member, capture Player hiện tại và kết thúc. Reservation đích không được hủy.

Kết quả probe: Session CLOSED, source có 0 member, destination có 0 member nhưng còn 1 reservation. Suất này chiếm capacity và có thể chặn Session mới của cùng Player. Test `disconnectBeforeFinishLoadReleasesDestinationReservation` chỉ bao phủ disconnect sau khi handoff đã trả về, nên không bắt interleaving trên.

Đây là lỗ hổng đã có trong hợp đồng handoff hiện tại, không phải lỗi được đưa vào do gom các kiểu dữ liệu Monster. Cần cleanup xác nhận trạng thái sau khi source writer hoàn tất rồi giải phóng pending destination ngoài writer; giữ nguyên exact Session identity và không chờ writer đích từ writer nguồn.

### 2. P1 — Checkpoint chuyển map cũ có thể ghi đè final save mới

[MapHandler](D:/doan11/vutrurongthan/server/src/main/java/com/project/game/network/handler/MapHandler.java:62) và [Session.close](D:/doan11/vutrurongthan/server/src/main/java/com/project/game/network/Session.java:251) cùng gọi [PlayerManager.save](D:/doan11/vutrurongthan/server/src/main/java/com/project/game/player/PlayerManager.java:36), nhưng không có thứ tự lưu chung theo Player/account. Repository cập nhật theo id/account, không có điều kiện loại bản capture cũ.

Probe gọi chính handler trả về home trong cùng Zone, giữ checkpoint đầu đang chờ, chạy một mutation HP trên writer rồi disconnect. Final checkpoint lưu HP 190. Khi giải phóng checkpoint cũ, dữ liệu cuối trở lại HP 200. Account reservation cũng có thể đã được nhả trong khi bản lưu của Session cũ chưa hoàn tất.

Ranh giới JDBC ngoài Zone là đúng, nhưng bản capture ổn định không tự bảo đảm thứ tự ghi DB. Cần bảo đảm final checkpoint đứng sau các write trước đó và Session cũ không tiếp tục ghi sau khi account được mở cho Session mới. Đây là vấn đề correctness hiện tại, tách khỏi mục tiêu tối ưu batch/coalescing sau này.

Kiểm tra dùng repository giả có điều khiển thời điểm ghi; chưa chạy race này trên MySQL thật. SQL production ở [JdbcPlayerRepository](D:/doan11/vutrurongthan/server/src/main/java/com/project/game/persistence/player/JdbcPlayerRepository.java:39) cũng không ngăn bản cũ ghi đè bản mới.

### 3. P2 — Chết rồi disconnect chưa normalize về home trước save

[Zone.leavePlayer](D:/doan11/vutrurongthan/server/src/main/java/com/project/game/map/Zone.java:169) capture HP và vị trí hiện tại. `Session.close` lưu capture đó; [PlayerRecord.toPlayer](D:/doan11/vutrurongthan/server/src/main/java/com/project/game/persistence/player/PlayerRecord.java:80) dựng lại nguyên dữ liệu.

Probe xác nhận Player chết ở map 1 được capture HP 0/map 1 và dựng lại vẫn HP 0/map 1. `returnHomeFromDeath` chỉ giải quyết khi có request RETURN_TOWN_FROM_DIE. Mục tiêu death → disconnect → normalize home → save trong baseline ban đầu chưa được thực hiện. Đây là mục tiêu còn thiếu, không phải regression của đợt gom Monster.

## Đối chiếu cấu trúc và luồng đọc với legacy

Legacy được kiểm tra tại `D:/doan11/rongthanchibi/rongthanchibi`, gồm:

- `map/Map.java`, `map/MapManager.java`, `map/Zone.java`.
- `entity/player/Player.java`, `entity/player/PlayerManager.java`.
- `entity/monster/Monster.java`, `MonsterTemplate.java`, `MonsterManager.java`.
- `service/AreaService.java`, `service/Service.java`, `model/MonsterDartTemplate.java`, `model/DartTemplateInfo.java`.

Từ vựng và luồng hữu ích: `run → update → player.update / monster.update`, `findOrRandomZone`, `joinMap`, `requestChangeMap`, `returnTownFromDead`, `teleport`, `injure`, `findTarget`, `updateAttack`, `delayAttack`, `respawn`, và Service gửi thông báo khu vực.

| Phần | Hiện tại so với mục tiêu/legacy | Đánh giá |
|---|---|---|
| Map | Giữ template và collection Zone, tạo đúng minZone, find chỉ tìm | Đạt |
| MapManager | Public điều phối chuyển map đúng vị trí đã được user chốt; reserve → revalidate → commit có lý do về ownership | Đạt vị trí, còn lỗi handoff |
| Zone | API và action đặt gần nhau; membership, collection và mutation ordering có owner rõ | Đạt phần tổ chức |
| ZoneWriter | Đúng một lớp nội bộ chứa queue, virtual thread, waits và lifecycle | Đạt extraction; chưa phải game loop đích |
| Player | Own move/injure/revive/canTarget/attackMonster/reward, không chứa JDBC hoặc packet encoding | Đạt lát gameplay hiện có |
| Monster | Own update/respawn/move/attack/enemies/cooldown; package chỉ còn Monster, MonsterTemplate, MonsterManager | Đạt mục tiêu giảm vụn |
| Dữ liệu Monster | Snapshot nằm cạnh runtime; Spawn/Dart/Phase nằm cạnh static template | Đạt; các kiểu có nghĩa khác nhau nên không gộp thành một kiểu |
| Handler/Service/PacketWriter | Parse/dispatch, gửi khu vực và encode được phân vai rõ | Đạt ranh giới hiện có |
| Persistence | SaveData ổn định, Repository ngoài writer | Đạt ranh giới; chưa đạt thứ tự lưu và normalize đầy đủ |

Luồng gameplay Monster bình thường chủ yếu cần đọc `Monster.java` và điểm gọi trong `Zone.java`. Template/catalog và đường encode chỉ cần mở khi sửa dữ liệu hoặc protocol. Không cần hiểu ZoneWriter để theo dõi Monster quyết định update gì. Các record output đang mang dữ liệu thông báo; Snapshot không nằm trong AI hoặc bản sao mỗi tick.

`Zone.canTargetMonster` vẫn hợp lý: nó nhận Session, kiểm tra exact membership và lookup Monster trên writer, rồi gọi `Player.canTarget`. `PlayerSaveData` tại leave là ranh giới capture ổn định cho persistence. Có tên Player/Monster trong Zone không đồng nghĩa Zone đang sở hữu nội dung gameplay của entity.

Giữ điểm mạnh của legacy: entity có behavior, tên game ngắn, update tập trung, Manager phục vụ catalog/lifecycle. Không sao chép singleton mutable, Entity tự truy cập DB/network, platform thread mỗi Zone, lock để cho nhiều thread tự mutation, hoặc mutable Monster dùng làm static seed. Public travel ở MapManager là khác biệt đã được user chốt; reservation và các capture handoff có ý nghĩa an toàn thực sự.

## Mục tiêu ban đầu còn thiếu

- **Đủ họ chuyển vị trí:** hiện có waypoint changeMap và returnHomeFromDeath cùng dùng changeZone; chưa có luồng goBack, teleport trong Zone và teleport sang map/context khác. Không được coi `Player.changeMap` chỉ đổi field là implementation của các use case này.
- **Phân phối public Zone:** Map đã tạo minZone, nhưng waypoint luôn chọn Zone 0 và load dựng Player với zoneId 0. Zone 0 đầy có thể từ chối dù Zone khác còn chỗ. Legacy có chọn Zone qua findOrRandomZone; baseline mới yêu cầu ưu tiên ít người, nên không nên copy ngưỡng/random cũ máy móc.
- **Zone tự game loop và idle freeze:** writer hiện drain queue rồi dừng. MonsterManager vẫn có timer 100 ms duyệt mọi public Zone và chờ từng Zone.updateMonsters, kể cả Zone không có Player. FROZEN lúc này nghĩa queue trống, chưa có nghĩa simulation được đóng băng theo trạng thái world. Đây là current implementation được runtime baseline ghi rõ, cần phase migration riêng.
- **Periodic dirty checkpoint:** hiện lưu lúc transition/disconnect, chưa có checkpoint định kỳ, revision, dirty/coalescing/batching. Có thể mất tiến độ kể từ checkpoint cuối nếu process dừng bất thường. Thứ tự lưu ở finding 2 cần giải quyết trước hoặc cùng thiết kế này.
- **Các feature tiếp theo:** Effect/DoT/regen, Npc/Boss/ItemMap và Dungeon chưa có đầy đủ gameplay như legacy. Không coi chúng là regression của lát refactor hiện tại. Shared Entity cũng chưa nên thêm chỉ để giống cây class legacy; runtime baseline vẫn để mở hợp đồng này.

## Kiểm chứng và thứ tự tiếp tục

Full gate mới: Maven 3.9.9, JDK 21, `mvn test` trong server: **523 test, 519 pass, 4 skip, 0 failure/error; BUILD SUCCESS**. Repository không có mvnw.cmd nên dùng Maven đã cài. Test TLS network/transport tự động pass. `git diff --check` pass.

Ba probe có điều khiển thứ tự chạy được lưu tại [.superpowers ReviewProbe.java](D:/doan11/vutrurongthan/.superpowers/sdd/2026-10-04-monster-readability/ReviewProbe.java), kết quả tại [review-probe.log](D:/doan11/vutrurongthan/.superpowers/sdd/2026-10-04-monster-readability/review-probe.log). Hook reflection chỉ dùng trong fixture để giữ đúng điểm xen kẽ hoặc thay repository, không sửa source production. Đây là bằng chứng finding, chưa phải regression coverage được đưa vào bộ JUnit.

Chưa chạy: 4 test MySQL opt-in, DB thật cho các race mới, Unity manual, TLS triển khai thực tế, gameplay trên server đang chạy, load/profile nhiều Zone. Test tự động hiện xanh không đủ để tuyên bố các trường hợp mới này đúng.

Thứ tự đề xuất để tránh refactor vòng lặp:

1. Sửa reservation cleanup và thứ tự transition/final save; đưa hai interleaving vào focused regression tests.
2. Normalize death-disconnect; khóa kỳ vọng save/load bằng test.
3. Hoàn thiện chọn public Zone và các use case travel còn thiếu trong vị trí MapManager đã chốt, dùng chung cơ chế handoff hiện có khi phù hợp.
4. Migration game loop/freeze và periodic checkpoint theo phase riêng, có kiểm tra runtime/DB tương ứng.
5. Tiếp tục feature gameplay sau khi nền runtime đủ rõ.

Chưa có bằng chứng cần một đợt tách/gom Monster nữa. Ba file feature hiện tại là điểm dừng hợp lý cho lát readability này.
