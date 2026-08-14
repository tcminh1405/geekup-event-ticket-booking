# Coding Guidelines and Adding a New API

## Conventions

- Use Java 21, Spring Boot conventions, 4-space indentation, and one public top-level class per file.
- Organize business code by feature: `concert`, `booking`, `voucher`, or `admin`. Put only cross-cutting technical code in `shared`.
- Controllers own HTTP concerns only: request parsing, bean validation, response status, and `ApiResponse<T>` wrapping.
- Services own business rules and transaction boundaries. Repositories own persistence queries only.
- Use request/response DTOs; do not expose JPA entities directly from an endpoint.
- Use `BigDecimal` for money and UTC-compatible time types (`LocalDateTime` persisted with the JDBC UTC setting).
- Use `ApplicationException` subclasses and a machine-readable error code. Do not throw a raw exception to represent an expected business condition.
- Keep PostgreSQL as the source of truth. Redis may accelerate reads/coordination but must not be the only record of inventory or booking state.

## Steps for a new API

1. Identify the owning feature package and add request/response DTOs in its `dto` package. Apply Jakarta validation annotations to the request.
2. Add/reuse a repository method only when it represents a database operation. For a concurrent state/inventory change, prefer a conditional `UPDATE ... WHERE current_state = ...` or `WHERE available_quantity >= ...` query.
3. Implement the business use case in a service. Add `@Transactional` at the service method, not at the controller. Do not call an external system while holding a transaction unless that trade-off is deliberate and documented.
4. Add the controller endpoint with an OpenAPI `@Operation` and the appropriate headers. Customer actions use `X-User-Id`; operator actions additionally require `X-Role: ADMIN` or `OPERATOR`.
5. Return `ApiResponse.success(...)`. For expected failures, throw `ResourceNotFoundException`, `ConflictException`, `ValidationException`, `ForbiddenException`, or another existing application exception.
6. Add tests for the rule and update the Postman collection if the endpoint is user-testable.

## Test rules

- Unit/property tests go under `backend/src/test/java` and use the `*Test` naming convention.
- Tests that require PostgreSQL must use Testcontainers rather than a developer's local database.
- Every concurrent flow needs an invariant assertion, for example: `available_quantity` never becomes negative; one idempotency key creates no more than one booking; campaign usage does not exceed its limit.
- Before submission, run the commands in [LOCAL-SETUP.md](LOCAL-SETUP.md) and manually run the collection in `postman/`.
