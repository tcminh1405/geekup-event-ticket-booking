# System Assumptions & Scope Boundaries

Tài liệu này định nghĩa rõ ràng các giả định (Assumptions), phạm vi hệ thống (Scope Boundaries) và các tham số giới hạn vận hành của Nền tảng Đặt vé Sự kiện Âm nhạc.

---

## 1. Scope Boundaries (Phạm vi dự án)

### 1.1 In-Scope (Nằm trong phạm vi thực thi)
- API duyệt danh sách sự kiện âm nhạc và chi tiết hạng vé (Customer).
- API đặt giữ vé Flash Sale với cơ chế chống Overselling, chống trùng đơn và chống lạm dụng mã giảm giá.
- API thanh toán đơn hàng (thông qua Payment Gateway được giả lập).
- API quản lý đơn hàng và theo dõi trạng thái vòng đời Booking.
- API Quản trị cho Operator: Quản lý sự kiện, phát hành hạng vé, tạo chiến dịch Voucher, theo dõi tồn kho thực tế.
- Tự động nạp dữ liệu mẫu (Database Seeding) khi khởi chạy ứng dụng.
- Đầy đủ tài liệu Swagger / OpenAPI UI tại `/swagger-ui.html`.

### 1.2 Out-of-Scope (Nằm ngoài phạm vi bài test)
Để tập trung tối đa nguồn lực xử lý luồng core **Flash Sale Booking & Concurrency Control**, các tính năng sau được giả định ngoài phạm vi:
- **Authentication & Authorization thực tế**: Không cài đặt Spring Security OAuth2 / JWT. Việc xác thực người dùng được mô phỏng qua HTTP Header `X-User-Id`.
- **Payment Gateway thực tế**: Không kết nối thực tế với VNPay / Momo / Stripe. Sử dụng `MockPaymentGateway` trả về trạng thái ngẫu nhiên hoặc theo cấu hình.
- **Thông báo thời gian thực (Push Notifications)**: Không gửi Email xác nhận vé, tin nhắn SMS OTP hay WebSocket notification.
- **Tạo mã Barcode / QR Code**: Không dựng sinh mã QR cho vé.
- **Đa tiền tệ (Multi-currency)**: Hệ thống mặc định tính toán trên đơn vị tiền tệ **VND**.

---

## 2. Key System Assumptions (Các giả định cốt lõi)

### 2.1 Identity & User Context (Xác thực người dùng)
- Client bắt buộc gửi Header `X-User-Id: <Long>` đại diện cho ID của người dùng thực hiện request.
- Các endpoint `/api/v1/admin/**` là thao tác nội bộ và yêu cầu thêm `X-Role: ADMIN` hoặc `X-Role: OPERATOR`. Đây là role gate mô phỏng; production phải thay bằng authentication/authorization thực tế.
- Nếu không truyền `X-User-Id`, hệ thống tự động gán user mặc định (người dùng ẩn danh) hoặc trả về lỗi theo từng endpoint quy định.

### 2.2 Payment Gateway Mocking (Thanh toán giả lập)
- Endpoint `POST /api/v1/bookings/{id}/pay` nhận yêu cầu thanh toán.
- `MockPaymentGateway` sẽ phản hồi kết quả dựa trên biến cấu hình `app.payment.mock-status`:
  - `SUCCESS`: Trả về thành công -> Đơn chuyển trạng thái `CONFIRMED`.
  - `FAILED`: Trả về thất bại -> Đơn chuyển trạng thái `CANCELLED`, hoàn trả vé về kho.
  - `RANDOM`: Trả về ngẫu nhiên (80% SUCCESS, 20% FAILED).
  - `TIMEOUT`: Giả lập phản hồi quá 10 giây -> Trả về HTTP 504 `PAYMENT_GATEWAY_TIMEOUT`, đơn giữ nguyên `PENDING`.

### 2.3 Operational Limits & Default Parameters (Các tham số hệ thống)

| Tham số | Giá trị | Mô tả |
|---|---|---|
| **Max Tickets per Category** | `10` vé / đơn | Một yêu cầu đặt vé chỉ được mua tối đa 10 vé mỗi loại |
| **Payment Deadline Window** | `15` phút | Thời gian giữ vé cho đơn PENDING tính từ lúc tạo đơn |
| **Idempotency Key TTL** | `24` giờ | Thời gian lưu trữ kết quả Idempotency trong Redis |
| **Rate Limit Threshold** | `200` req/min | Giới hạn số lượng request per `X-User-Id` |
| **Lock Wait Time** | `3` giây | Thời gian chờ tối đa khi xin Redisson Lock |
| **Lock Lease Time** | `10` giây | Thời gian tự động giải phóng Redisson Lock |
| **Expiry Scheduler Interval** | `30` giây | Tần suất chạy job quét dọn các đơn hàng hết hạn |
| **Pagination Default Page Size** | `20` items | Số lượng phần tử mặc định mỗi trang API |
| **Pagination Max Page Size** | `100` items | Số lượng phần tử tối đa mỗi trang API |

---

## 3. Data Seeding Assumptions

Khi ứng dụng khởi chạy (`CommandLineRunner` DataSeeder execution):
1. **Normal Mode**:
   - Tự động kiểm tra nếu cơ sở dữ liệu trống, sẽ tạo sẵn ít nhất 2 Concert đã công khai (`published = true`).
   - Mỗi Concert có 2 hạng vé: VIP (Giá > 500,000 VND, Số lượng ≥ 50) và Standard (Giá < 500,000 VND, Số lượng ≥ 100).
   - Tạo ít nhất 1 chiến dịch Voucher đang trong thời gian hiệu lực kèm theo 5 mã voucher khả dụng.
2. **Flash Sale Testing Mode** (`FLASH_SALE_MODE=true`):
   - Nạp thêm 1 Concert đặc biệt có loại vé số lượng đúng `100` vé để phục vụ bài test kiểm thử truy cập đồng thời (Concurrency Test).
