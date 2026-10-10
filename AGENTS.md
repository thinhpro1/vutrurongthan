# AGENTS.md

Repo gồm Unity client và Java game server.

## Server (`server/**`)

Trước khi lập kế hoạch, review hay sửa server:

1. **Đọc `docs/architecture/SERVER_RULES.md`.** Đây là rule duy nhất cho kiến trúc và cách viết code server. Đọc không được thì dừng.
2. Lý do của từng quyết định nằm ở `docs/architecture/DECISIONS.md`. Muốn đổi một quyết định thì đề xuất với người dùng trước, không tự đổi.
3. Xem code hiện tại và tính năng tương ứng ở src cũ `../rongthanchibi` (nếu có). Giữ tên và luồng dễ đọc; không chép phần không an toàn (rule mục 13).
4. Tài liệu trong `docs/archive/` và các plan cũ chỉ để tham khảo lịch sử. Mâu thuẫn với rule thì rule thắng.

Khi làm:

- Chỉ sửa trong phạm vi task. Chạm vào tính năng nào thì chuyển trọn tính năng đó sang hình dạng đích (rule mục 0, 16).
- Không đổi protocol với Unity hoặc schema DB nếu task không yêu cầu rõ.
- Thêm/sửa test cho hành vi thay đổi; lỗi concurrency phải có test tái hiện.
- Chạy `mvn test` trong `server/` (xanh mới push).
- Báo ngắn: đổi gì, test gì, và **những gì chưa chạy** (DB thật, Unity, TLS, tải). Không tuyên bố chạy thật thành công khi chỉ có test tự động.

## Client (Unity)

- Không sửa client chỉ để làm đẹp kiến trúc server.
- Sửa client cần task riêng hoặc thay đổi hợp đồng chung đã được duyệt.
- Thay đổi packet, serialization, đăng nhập/tài nguyên dùng chung giữa client và server thì phải theo rule server.
