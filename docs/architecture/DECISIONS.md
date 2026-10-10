# Quyết định kiến trúc server

Mỗi mục: quyết định, lý do, ngày. Quy tắc viết code nằm ở `SERVER_RULES.md`; file này giải thích **vì sao**.
Quyết định mới thêm vào cuối; quyết định bị thay thì ghi "Thay bởi #n" chứ không xóa.

---

## Đã có trước V3 (vẫn giữ)

**#1. 1 Zone = 1 virtual thread = 1 writer.** Thay `Zone extends Thread` cộng lock chồng của src cũ. Mọi thay đổi trong Zone tuần tự, không cần lock trong gameplay. *(trước 2026-10)*

**#2. Session dùng virtual thread đọc/ghi socket chặn (blocking I/O).** Dễ hiểu; chỉ đổi sang non-blocking khi có số liệu. *(trước 2026-10)*

**#3. Lưu DB ngoài Zone, qua `PlayerSaveData`.** Writer không chờ JDBC; thứ tự lưu cuối khi thoát game cố định. *(2026-10-04)*

**#4. Vòng update chạy trên chính writer của Zone, ngủ khi Zone trống.** Bỏ platform thread `monster-lifecycle` đi qua mọi Zone. Nhịp kế tiếp tính từ lúc nhịp trước kết thúc; mỗi Zone một `RandomGenerator`. *(2026-10-08)*

---

## V3 — 2026-10-10

**#5. Player là trung tâm hành động; Zone chỉ quyết định thời điểm.**
Src mới đã đẩy hành động sang `Zone` (`move`, `attackMonster`) và `MapManager` (`changeMap`), làm Player chỉ còn dữ liệu và Zone thành god class mới. Quay về luồng của src cũ: Handler → `player.xxx()`, chạy trên writer qua `zone.post`.

**#6. Zone giữ `Player`, không giữ `Session`.** Thay mục 18 của V2.2.
Giữ Session buộc mọi hành động kiểm tra lại trạng thái kết nối, backlink, membership, và làm luồng thoát game phức tạp. Player giữ tham chiếu `session` để gửi packet riêng.

**#7. Một cửa vào `zone.post(player, action)`; kiểm tra membership một lần ở cửa.**
Thay cho mỗi hành động một method trên Zone với khối kiểm tra lặp lại. Lambda chỉ xuất hiện ở đây.

**#8. Entity gọi `zone.service().xxx(...)` bằng từ ngữ game.**
Bỏ các record `Monster.Damage/Attack/Move/Respawn`. Packet vẫn đóng gói trong PacketWriter, nên entity vẫn không biết bytes.

**#9. Tách Player theo mảng gameplay, không theo tầng kỹ thuật.**
Player vạn dòng của src cũ là god class thật. Cách tách đúng là `Inventory`, `PlayerTask`, `Trade`… làm field của Player (src cũ đã bắt đầu với `TradeAction`, `ClanAction`). Đưa hành động sang Zone/Manager/Service chỉ chuyển god class sang chỗ khác.

**#10. `Character` mỏng làm cha chung của Player và Boss (sau này cả đệ tử).**
Boss có skill, effect, và được client hiển thị bằng gói player. `extends Monster` không đủ; `extends Player` (như src cũ) kéo theo session, túi đồ, lưu DB. Boss lấy dữ liệu từ `BossTemplate`; chỉ boss có cơ chế riêng mới có class con.

**#11. `Effects` là class dùng chung, gắn làm field của `Character` và `Monster`.**
Dùng chung logic choáng/trói/DoT mà không bắt Monster kế thừa `Character`.

**#12. `Zone.enter` gửi `MAP_INFO` từ dữ liệu đang sống; bỏ `Monster.Snapshot`.**
Giống `Zone.enter → setMapInfo` của src cũ; bytes không đổi nên Unity không phải sửa.

**#13. Chuyển map = rời Zone nguồn rồi `post(enter)` sang Zone đích; bỏ cơ chế giữ chỗ.**
Thay reserve/revalidate/commit + `Trip`. Sức chứa là mềm (xem #16). Player đánh dấu `loading` tới `FINISH_LOAD_MAP` để Monster bỏ qua. Mọi kiểu đi lại (waypoint, về nhà, goBack, teleport) dùng chung `MapManager.travel`.

**#14. Dungeon theo kiểu `Expansion` của src cũ, có writer riêng.**
Dungeon tự giữ các Map riêng, thời hạn, người tham gia. Trạng thái chung của nhiều Zone có writer riêng và chỉ nhận việc qua `post`, giữ đúng #1.

**#15. Một file rule ngắn + file quyết định này; các plan cũ vào `docs/archive/`.**
Bộ tài liệu cũ (~9.700 dòng) dài gấp ~4 lần code gameplay, và có chỗ mâu thuẫn nhau (`01_SERVER_PLAN` mô tả `@Service MapService`, trong khi rule V2.2 cấm). AI làm theo nghi thức báo cáo thay vì viết code dễ đọc.
Đã chuyển vào archive: `SERVER_RULES_V2.2.md`, `SERVER_RUNTIME_ARCHITECTURE_BASELINE.md`, `2026-10-04-server-refactor-review.md`, `01_SERVER_PLAN_V7_4.md`.
Chưa xử lý: `03_IMPLEMENTATION_TEST_SCHEDULE_V5_4.md`, `04_NETWORK_IMPLEMENTATION_PLAN_V2_4.md`, `review/NETWORK_REVIEW_ACTION_PLAN_AFTER_0633E423.md`. Nếu mâu thuẫn với rule V3 thì rule thắng.

**#16. Sức chứa Zone là mềm, chọn Zone như `findOrRandomZone(-1)` của src cũ.**
Zone đầu tiên còn chỗ; tất cả đầy thì Zone ít người nhất. Không từ chối người chơi vì đầy: cơ chế giữ chỗ cũ có thể làm người chơi kẹt giữa hai map (đã thoát Zone nguồn nhưng không vào được đích).

**#17. Checkpoint khi đổi map chạy ngoài writer, đúng thứ tự (`Session.saveLater`).**
Mỗi Session có một hàng lưu riêng (một virtual thread). Writer chỉ chụp `PlayerSaveData` rồi đi tiếp; lần lưu cuối của `close()` chờ lần lưu đang chạy, lần lưu đến muộn sau khi đóng bị bỏ.
