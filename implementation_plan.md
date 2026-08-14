# Backend Engineer Test: Concert Ticket Booking Platform

## Background
Xây dựng hệ thống backend cho Nền tảng Đặt vé Sự kiện Âm nhạc hỗ trợ Flash Sale. Hệ thống cần xử lý lượng truy cập lớn (50k người dùng, 300-500 requests/phút), ngăn chặn overselling, duplicate bookings và voucher abuse. Bài test đánh giá tư duy kỹ sư (engineering thinking) và các quyết định đánh đổi (trade-offs).

---

## Kiến trúc Hệ thống (System Architecture)
Hệ thống được thiết kế theo mô hình **Modular Monolith (Monolith phân mô-đun theo Domain Context / Package-by-Feature)** kết hợp với kiến trúc phân tầng nội bộ từng module, bao gồm các miền nghiệp vụ được đóng gói độc lập:

1. **Common Module (`common`)**: 
   - Chứa các Cross-Cutting Concerns dùng chung: `ApiResponse<T>`, `GlobalExceptionHandler`, `IdempotencyFilter`, `RateLimitFilter`, `UserIdHeaderFilter`, và cấu hình Redis/Redisson.
2. **Concert Module (`module.concert`)**: 
   - Quản lý sự kiện âm nhạc, danh sách biểu diễn và hạng vé (`TicketCategory`). Đóng gói Controller, Service, JPA Repository và Redis Read-Cache cho thông tin kho vé.
3. **Booking Module (`module.booking`)**: 
   - Trọng tâm xử lý Flash Sale: tạo đơn giữ vé 15 phút, quản lý State Machine (`PENDING`, `AWAITING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`), thực thi **Atomic CAS Update** trực tiếp tại DB để chống overselling, và `BookingExpiryScheduler` trả vé tự động.
4. **Voucher Module (`module.voucher`)**: 
   - Quản lý chiến dịch khuyến mãi, kiểm tra tính hợp lệ và xử lý chống gian lận lạm dụng voucher bằng khóa phân tán **Redisson Fine-Grained Lock** (`lock:voucher:{userId}:{voucherId}`).
5. **Payment Module (`module.payment`)**: 
   - Tiếp nhận yêu cầu thanh toán, tương tác với `MockPaymentGateway` (giả lập các kịch bản SUCCESS, FAILED, TIMEOUT) và cập nhật trạng thái đơn hàng.
6. **Admin Module (`module.admin`)**: 
   - Dành cho Operator quản trị: khởi tạo concert, công khai sự kiện, hủy đơn thủ công và theo dõi báo cáo.

---

## User Review Required
- **Tech Stack Proposal**: **Java (JDK 21) + Spring Boot 3 + PostgreSQL + Redis**. 
  - *Lý do*: Java là chuẩn mực (enterprise-standard) cho các hệ thống tải cao, Transaction và Thread-safety được quản lý chặt chẽ. Hệ sinh thái Spring Boot hỗ trợ tận răng (JPA, Redis, Cache).
  - *Câu hỏi*: Bạn có yêu cầu sử dụng Maven hay Gradle cho build tool không? (Mặc định tôi sẽ sử dụng **Maven**).
- **Phạm vi tính năng (Scope & Assumptions)**:
  1. Tính năng Đăng nhập sẽ được mock (chỉ truyền `X-User-Id` qua Header) để dồn toàn lực vào Booking Flow & chống Overselling.
  2. Payment Gateway sẽ được mock (API Payment sẽ trả về SUCCESS/FAILED ngẫu nhiên, hoặc thành công nếu payload đúng).
  3. Operation/Dashboard API tập trung vào hiển thị danh sách Booking và chức năng Seed data khởi tạo, không dựng toàn bộ CRUD phức tạp.

## Kế hoạch thực hiện (Implementation Phases)

### Phase 1: System Design & Documentation (Tài liệu)
- **Thiết kế CSDL (ERD)**: `Users`, `Concerts`, `TicketCategories`, `Vouchers`, `Bookings`, `BookingItems`.
- **Thiết kế giải pháp Flash Sale**:
  - **Overselling**: Sử dụng Redis với thư viện **Redisson** để tạo Distributed Lock khi giữ vé (reserve), hoặc dùng Lua Script (chống race-condition).
  - **Duplicate Bookings**: Bắt buộc client gửi `Idempotency-Key` header ở API Reservation, server lưu key vào Redis (với TTL ngắn) để chặn request kép.
  - **Voucher Abuse**: Lock theo `UserId + VoucherId` trên Redis khi đang xử lý giảm giá để ngăn 1 user dùng 1 voucher n lần đồng thời.
- **Output**: Thư mục `docs/` chứa tài liệu thiết kế hệ thống, ERD (có thể bằng mã Mermaid) và Assumptions.

### Phase 2: Project Setup & Foundation
- Sử dụng Spring Initializr khởi tạo project Spring Boot.
- Thiết lập dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Spring Data Redis, Lombok, Validation.
- Tạo `docker-compose.yml` định nghĩa PostgreSQL và Redis container.
- Xây dựng base packages: `controller`, `service`, `repository`, `model/entity`, `model/dto`, `exception`.
- Tạo `GlobalExceptionHandler` và cấu trúc Response `ApiResponse<T>`.

### Phase 3: Core API Implementation (Customer Flow)
- API `GET /api/v1/concerts`: Danh sách sự kiện.
- API `POST /api/v1/bookings/reserve`: API Flash Sale (Trọng tâm nhất). 
  - Validate Idempotency-Key.
  - Khóa (Lock) số lượng vé của loại vé yêu cầu thông qua Redis.
  - Kiểm tra tồn kho, trừ tồn kho logic (trong Cache hoặc DB) và tính tổng tiền (có voucher).
  - Tạo Booking trạng thái `PENDING` và nhả khóa.
- API `POST /api/v1/bookings/{id}/pay`: Cập nhật trạng thái Booking thành `CONFIRMED` hoặc `CANCELLED`.

### Phase 4: Internal Operation API & Data Seeding
- API `GET /api/v1/admin/bookings`: Endpoint cho Operation Dashboard.
- Cài đặt **Database Seeder** (`CommandLineRunner` của Spring Boot) tự động nạp dữ liệu vào DB (Concerts, TicketCategories, Vouchers mẫu) mỗi khi khởi chạy hệ thống (với schema trắng), giúp người chấm dễ dàng test ngay.

### Phase 5: Documentation & Testing Output
- Tích hợp **Springdoc OpenAPI (Swagger)** để tự động sinh Document API tại `/swagger-ui.html`.
- Viết Unit Tests (JUnit 5 + Mockito) cho `BookingService` để chứng minh logic (chặn vượt quá số lượng vé, tính toán voucher).
- Hoàn thiện `README.md` (Hướng dẫn start Docker, chạy Maven, Coding Guidelines).

## Verification Plan
### Automated Tests
- Chạy command `./mvnw test` để đảm bảo Unit Tests verify luồng business core đúng.
### Manual Verification
- Khởi động DB + Redis qua `docker-compose up -d`.
- Khởi động app bằng IDE hoặc `./mvnw spring-boot:run`.
- Truy cập vào Swagger UI (localhost:8080/swagger-ui.html), sử dụng Data được seed sẵn để tạo 1 flow đặt vé hoàn chỉnh (Reservation -> Payment).
