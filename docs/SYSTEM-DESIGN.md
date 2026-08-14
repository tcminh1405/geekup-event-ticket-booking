# System Design Document: Concert Ticket Booking Platform

## 1. Overview & Architecture

Nền tảng Đặt vé Sự kiện Âm nhạc (Concert Ticket Booking Platform) là một dịch vụ backend được thiết kế theo kiến trúc **Modular Monolith (Monolith phân mô-đun theo Domain Context / Package-by-Feature)** trên nền **Java 21** và **Spring Boot 3**. Hệ thống kết hợp sức mạnh của **PostgreSQL 15** (Source of Truth duy nhất cho dữ liệu quan hệ) và **Redis 7** (cho caching, idempotency deduplication và distributed locking qua Redisson).

### Tại sao chọn Kiến trúc Modular Monolith?
1. **High Cohesion & Low Coupling**: Mỗi mô-đun (Concert, Booking, Voucher, Payment, Admin) tự đóng gói toàn bộ Domain Model, Service, Repository và Controller của riêng mình. 
2. **Sẵn sàng cho Microservices (Microservices-Ready)**: Các mô-đun giao tiếp với nhau qua các Interface/DTO rõ ràng. Trong tương lai, nếu lưu lượng tăng vọt, từng mô-đun (ví dụ: `booking-module`) có thể tách ra thành Microservice độc lập mà không cần viết lại ứng dụng.
3. **Quản lý ranh giới nghiệp vụ (Clear Bounded Contexts)**: Giúp mã nguồn dễ bảo trì, dễ viết Unit/Integration test độc lập cho từng module.

---

### 1.1 Modular Package Structure

```
com.geekup.ticketbooking/
├── TicketBookingApplication.java
├── concert/                 # Concert catalog domain
│   ├── controller/          # ConcertController
│   ├── dto/                 # ConcertDetailResponse, ConcertSummaryResponse, ...
│   ├── entity/              # Concert, TicketCategory
│   ├── repository/          # ConcertRepository, TicketCategoryRepository
│   └── service/             # ConcertService
├── booking/                 # Reservation, payment endpoint and booking lifecycle
│   ├── controller/          # BookingController, PaymentController
│   ├── dto/                 # ReserveBookingRequest, PaymentRequest, responses
│   ├── entity/              # Booking, BookingItem
│   ├── repository/          # BookingRepository, BookingItemRepository
│   ├── scheduler/           # BookingExpiryScheduler
│   ├── service/             # BookingService, BookingExpiryService
│   └── state/               # BookingState
├── voucher/                 # Voucher validation and campaign usage
│   ├── dto/                 # VoucherValidationResult
│   ├── entity/              # Voucher, VoucherCampaign
│   ├── repository/          # VoucherRepository, VoucherCampaignRepository
│   └── service/             # VoucherService
├── admin/                   # Internal operator use cases
│   ├── controller/          # AdminBooking/Concert/VoucherController
│   ├── dto/                 # Admin request/response DTOs
│   └── service/             # AdminService
└── shared/                  # Shared technical concerns; not a business module
    ├── cache/               # InventoryCache, VoucherLockService, reconciliation task
    ├── common/              # ApiResponse, UserContext
    ├── config/              # RedisConfig, FilterConfig
    ├── exception/           # ApplicationException, GlobalExceptionHandler
    ├── filter/              # UserId, rate-limit, idempotency and admin-role filters
    ├── idempotency/         # IdempotencyService
    └── infrastructure/
        ├── payment/         # MockPaymentGateway
        └── seeder/          # DataSeeder
```

`payment` is not a standalone package/module in the current implementation: its HTTP endpoint is `booking.controller.PaymentController`, while the mock gateway is shared infrastructure at `shared.infrastructure.payment`. Likewise, voucher administration is exposed from `admin`, not a customer-facing `VoucherController`.

---

### 1.2 System Architecture Diagram (Modular Monolith)

```mermaid
flowchart TD
    subgraph Clients ["Clients Layer"]
        C["Customer App / Web"]
        O["Operator Dashboard"]
    end

    subgraph CrossCutting ["shared / Cross-Cutting Layer"]
        UH["UserIdHeaderFilter"]
        RL["RateLimitFilter (Redis Sliding Window)"]
        IF["IdempotencyFilter (Redis 24h TTL)"]
        AR["AdminRoleFilter"]
        GEH["GlobalExceptionHandler"]
    end

    subgraph ModularMonolith ["Spring Boot Modular Monolith"]
        subgraph ConcertModule ["Concert Module"]
            CC["ConcertController"]
            CS["ConcertService"]
            CR["ConcertRepository"]
            TCR["TicketCategoryRepository"]
        end

        subgraph BookingModule ["Booking Module (Core Flash-Sale)"]
            BC["BookingController"]
            BS["BookingService"]
            BR["BookingRepository"]
            EX["BookingExpiryScheduler"]
        end

        subgraph VoucherModule ["Voucher Module"]
            VS["VoucherService"]
            VL["VoucherLockService (Redisson)"]
            VR["VoucherRepository"]
        end

        subgraph PaymentInfrastructure ["Payment adapter (shared infrastructure)"]
            PC["PaymentController"]
            PG["MockPaymentGateway"]
        end

        subgraph AdminModule ["Admin Module"]
            AC["AdminBooking/Concert/VoucherController"]
            AS["AdminService"]
        end
    end

    subgraph DataStores ["Infrastructure & Data Stores"]
        DB[(PostgreSQL 15 - Primary Database)]
        RD[(Redis 7 - Caching & Locks)]
    end

    C -->|"HTTP REST"| CrossCutting
    O -->|"HTTP REST"| CrossCutting

    CrossCutting --> ConcertModule
    CrossCutting --> BookingModule
    CrossCutting --> VoucherModule
    CrossCutting --> PaymentInfrastructure
    CrossCutting --> AdminModule

    BookingModule -->|"Atomic CAS / Inventory Update"| ConcertModule
    BookingModule -->|"Validate & Apply Voucher"| VoucherModule
    PaymentInfrastructure -->|"Update Booking State"| BookingModule
    AdminModule --> ConcertModule & BookingModule & VoucherModule

    CR & TCR & BR & VR --> DB
    VL & IF & RL --> RD
    PC --> PG
```

---

## 2. API Response & Error Handling Standards

Tất cả các API endpoints thuộc các mô-đun đều trả về cấu trúc response thống nhất thông qua lớp bao đệm `ApiResponse<T>` trong gói `shared.common`.

### 2.1 Standard Success Envelope
```json
{
  "success": true,
  "data": {
    "bookingId": 42,
    "state": "PENDING",
    "totalAmount": 1500000.00,
    "paymentDeadline": "2026-08-14T17:30:00Z"
  },
  "timestamp": "2026-08-14T17:15:00Z"
}
```

### 2.2 Standard Error Envelope
```json
{
  "success": false,
  "error": {
    "code": "TICKET_SOLD_OUT",
    "message": "Loại vé yêu cầu đã hết số lượng khả dụng."
  },
  "timestamp": "2026-08-14T17:15:00Z"
}
```

### 2.3 Validation Error Envelope (HTTP 400)
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Dữ liệu yêu cầu không hợp lệ.",
    "fields": [
      {
        "field": "quantity",
        "reason": "Số lượng vé mỗi loại phải từ 1 đến 10"
      }
    ]
  },
  "timestamp": "2026-08-14T17:15:00Z"
}
```

---

## 3. Flash Sale Concurrency Control Mechanics

### 3.1 Flow Diagram: Reservation Request Processing

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant Filter as IdempotencyFilter
    participant Redis as "Redis Cache"
    participant Service as BookingService
    participant Lock as VoucherLockService
    participant DB as "PostgreSQL DB"

    Customer->>Filter: POST /api/v1/bookings/reserve
    Filter->>Redis: Check Idempotency Key
    alt Key exists in cache
        Redis-->>Filter: Return Cached Response
        Filter-->>Customer: HTTP 200 Cached Response
    end

    Filter->>Service: Forward request to Service
    Service->>DB: BEGIN TRANSACTION

    Note over Service,DB: Atomic CAS Inventory Deduction
    Service->>DB: Execute Atomic CAS UPDATE query
    
    alt Ticket Sold Out (Rows Affected = 0)
        DB-->>Service: 0 rows affected
        Service->>DB: ROLLBACK
        Service-->>Customer: HTTP 409 TICKET_SOLD_OUT
    end

    Service->>DB: INSERT INTO bookings
    Service->>DB: INSERT INTO booking_items

    opt Apply Voucher
        Service->>Lock: Acquire RLock
        alt Lock Timeout after 3s
            Lock-->>Service: Timeout
            Service->>DB: ROLLBACK
            Service-->>Customer: HTTP 503 SERVICE_BUSY
        end
        Lock->>DB: Check voucher validity and usage limit
        Lock->>DB: Increment usage count and insert log
        Lock-->>Service: Release RLock
    end

    Service->>DB: COMMIT TRANSACTION
    Service->>Redis: Update inventory cache
    Service->>Redis: Store idempotency response (TTL 24h)
    Service-->>Customer: HTTP 201 Created
```

### 3.2 Key Concurrency Strategies

| Thách thức (Challenge) | Giải pháp kỹ thuật (Technical Solution) | Cơ chế hoạt động (Mechanism) |
|---|---|---|
| **Overselling** (Bán quá số vé) | **Atomic Row CAS Update** tại DB level | Sử dụng truy vấn SQL: `UPDATE ticket_categories SET available_quantity = available_quantity - :qty WHERE id = :id AND available_quantity >= :qty`. Database row lock tự động serialize các request đồng thời mà không cần distributed lock trên từng ticket category. |
| **Read Latency** (Đọc kho nhanh) | **Redis Inventory Read Cache** | Khi xem thông tin Concert, số lượng vé còn lại được đọc từ Redis key `inventory:{ticketCategoryId}` để giảm tải truy vấn `COUNT`/`SELECT` vào DB. Kho Redis được cập nhật sau khi DB commit transaction thành công. |
| **Duplicate Booking** (Đặt trùng) | **Idempotency Filter + Redis Cache** | Bắt buộc truyền header `Idempotency-Key` (max 128 chars). Filter kiểm tra key trong Redis. Nếu key đã có kết quả HTTP 2xx, lập tức trả về cached response mà không gọi xuống DB. Key có TTL 24 giờ. |
| **Voucher Abuse** (Lạm dụng mã) | **Redisson Lock per User+Voucher** | Tạo khóa phân tán Redisson với pattern `lock:voucher:{userId}:{voucherId}`. Đảm bảo 1 user không thể gửi 100 request đồng thời để dùng cùng 1 voucher vượt quá lượt phép. |
| **DDS Attack / Spam** (Quá tải) | **Redis Sliding Window Rate Limiter** | Giới hạn 200 requests/phút per `X-User-Id`. Khi vượt ngưỡng trả về HTTP 429 kèm header `Retry-After: 60`. |

---

## 4. Booking State Machine

Mỗi `Booking` trong mô-đun `booking` tuân theo một State Machine nghiêm ngặt để đảm bảo trạng thái vé và tồn kho luôn đồng bộ.

```mermaid
stateDiagram-v2
    [*] --> PENDING : Reserve Ticket (15m Hold)
    PENDING --> AWAITING_PAYMENT : Submit Payment
    PENDING --> EXPIRED : Timeout 15m
    PENDING --> CANCELLED : Operator Cancel
    AWAITING_PAYMENT --> CONFIRMED : Payment SUCCESS
    AWAITING_PAYMENT --> CANCELLED : Payment FAILED
    AWAITING_PAYMENT --> CANCELLED : Operator Cancel
    CONFIRMED --> CANCELLED : Operator Cancel (Refund Inventory)
    EXPIRED --> [*]
    CONFIRMED --> [*]
    CANCELLED --> [*]
```

### 4.1 Valid Transitions & Side Effects Table

| Trạng thái hiện tại | Trạng thái chuyển đến | Tác nhân / Sự kiện | Tác dụng phụ (Side Effects) |
|---|---|---|---|
| `PENDING` | `AWAITING_PAYMENT` | Khách hàng thực hiện thanh toán | Khởi tạo giao dịch với Payment Gateway |
| `AWAITING_PAYMENT` | `CONFIRMED` | Payment Gateway trả lời SUCCESS | Ghi nhận `payment_timestamp`, chốt đơn hàng |
| `AWAITING_PAYMENT` | `CANCELLED` | Payment Gateway trả lời FAILED | **Cộng hoàn trả kho DB** (`available_quantity + qty`), Cập nhật Redis cache, Giảm lượt dùng Voucher |
| `PENDING` | `EXPIRED` | `BookingExpiryScheduler` quét (quá 15m) | **Cộng hoàn trả kho DB**, Cập nhật Redis cache, Giảm lượt dùng Voucher |
| `PENDING` | `CANCELLED` | Operator hủy thủ công | **Cộng hoàn trả kho DB**, Cập nhật Redis cache, Giảm lượt dùng Voucher |
| `AWAITING_PAYMENT` | `CANCELLED` | Operator hủy thủ công | **Cộng hoàn trả kho DB**, Cập nhật Redis cache, Giảm lượt dùng Voucher |
| `CONFIRMED` | `CANCELLED` | Operator hủy thủ công | **Cộng hoàn trả kho DB**, Cập nhật Redis cache, Giảm lượt dùng Voucher |

---

## 5. Exception & HTTP Error Code Mapping

Các business exception trong hệ thống kế thừa từ `ApplicationException` và được xử lý tập trung qua `@RestControllerAdvice` (`GlobalExceptionHandler`) thuộc gói `shared.exception`. Các error code bên dưới là giá trị runtime được truyền vào nhóm exception chung, không phải các lớp exception riêng lẻ.

| Exception Class | HTTP Status | Error Code | Mô tả |
|---|---|---|---|
| `ConflictException` | 409 Conflict | `TICKET_SOLD_OUT`, `VOUCHER_ALREADY_USED`, `VOUCHER_EXHAUSTED`, `INVALID_BOOKING_STATE` | Xung đột tồn kho, voucher, hoặc trạng thái booking |
| Filter response | 400 Bad Request | `MISSING_IDEMPOTENCY_KEY` | Thiếu header `Idempotency-Key` ở API reserve |
| `ValidationException` | 422 Unprocessable Entity | `CONCERT_NOT_PUBLISHED`, `VOUCHER_CAMPAIGN_INACTIVE`, `VOUCHER_MINIMUM_NOT_MET`, `INVALID_STATE_TRANSITION` | Vi phạm rule nghiệp vụ |
| `ResourceNotFoundException` | 404 Not Found | `BOOKING_NOT_FOUND`, `CONCERT_NOT_FOUND`, `TICKET_CATEGORY_NOT_FOUND`, `VOUCHER_NOT_FOUND` | Không tìm thấy resource |
| `ServiceBusyException` | 503 Service Unavailable | `SERVICE_BUSY` | Không lấy được distributed lock (kèm `Retry-After: 2`) |
| `PaymentGatewayTimeoutException` | 504 Gateway Timeout | `PAYMENT_GATEWAY_TIMEOUT` | Payment gateway không phản hồi trong 10 giây |
| `PaymentFailedException` | 402 Payment Required | `PAYMENT_FAILED` | Giao dịch thanh toán không thành công |
| `ForbiddenException` | 403 Forbidden | `FORBIDDEN` | Không có quyền truy cập đơn hàng của user khác |
| `MethodArgumentNotValidException` | 400 Bad Request | `VALIDATION_ERROR` | Lỗi validation trường dữ liệu đầu vào |
| `Unhandled Exception` | 500 Internal Server Error | `INTERNAL_SERVER_ERROR` | Lỗi hệ thống không xác định (không lộ stacktrace) |
