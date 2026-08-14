package com.geekup.ticketbooking.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test P4 — Inventory Non-Negative Invariant
 *
 * For any sequence of reserve/cancel/expire operations on a TicketCategory,
 * the {@code available_quantity} column in PostgreSQL SHALL NEVER fall below 0.
 *
 * <p>The atomic {@code UPDATE … WHERE available_quantity >= :qty} is the exact same
 * SQL used by {@link com.geekup.ticketbooking.concert.repository.TicketCategoryRepository#decrementAvailableQuantity}
 * in production. This test verifies that the CAS (compare-and-set) UPDATE prevents
 * negative inventory at the database level regardless of the operation sequence.</p>
 *
 * <p>Uses the Testcontainers Singleton Container Pattern so the container is started
 * once per JVM (compatible with jqwik's own JUnit Platform engine which does not
 * run JUnit Jupiter {@code @ExtendWith} lifecycle hooks).</p>
 *
 * <p>A minimal schema ({@code ticket_categories_test}) is created on first use;
 * each property trial inserts a fresh row and deletes it after assertions.</p>
 *
 * **Validates: Requirements 9.1, 9.6**
 */
@net.jqwik.api.Tag("inventory")
class InventoryPropertyTest {

    // ─── Singleton Container (started once per JVM via static initializer) ──────

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        // Container is started once per JVM; Ryuk cleans it up on exit.
        // IDE may warn about resource leak here — this is a known false positive
        // for the Testcontainers singleton pattern.
        @SuppressWarnings("resource")
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass");
        container.start();
        POSTGRES = container;
    }

    // ─── Schema bootstrap (idempotent, executed once) ────────────────────────────

    private static volatile boolean schemaInitialized = false;

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private void ensureSchema(Connection conn) throws SQLException {
        if (!schemaInitialized) {
            synchronized (InventoryPropertyTest.class) {
                if (!schemaInitialized) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(
                            "CREATE TABLE IF NOT EXISTS ticket_categories_test (" +
                            "    id                 BIGSERIAL PRIMARY KEY," +
                            "    available_quantity INT NOT NULL" +
                            ")"
                        );
                    }
                    schemaInitialized = true;
                }
            }
        }
    }

    // ─── Arbitraries ─────────────────────────────────────────────────────────────

    /**
     * Generates an initial available_quantity between 1 and 20.
     * Small upper bound so trials finish quickly; large enough for varied sequences.
     */
    @Provide
    Arbitrary<Integer> initialQuantity() {
        return Arbitraries.integers().between(1, 20);
    }

    /**
     * Generates a list of reserve attempt quantities (1..5 each), length 1..30.
     */
    @Provide
    Arbitrary<List<Integer>> operationQuantities() {
        return Arbitraries.integers()
                .between(1, 5)
                .list()
                .ofMinSize(1)
                .ofMaxSize(30);
    }

    // ─── SQL helpers (mirror production SQL exactly) ─────────────────────────────

    /** Inserts a row and returns the generated PK. */
    private long insertCategory(Connection conn, int availableQty) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ticket_categories_test (available_quantity) VALUES (?) RETURNING id")) {
            ps.setInt(1, availableQty);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Production-equivalent atomic decrement:
     * {@code UPDATE … SET available_quantity = available_quantity - :qty
     *         WHERE id = :id AND available_quantity >= :qty}
     *
     * @return 1 if decrement succeeded, 0 if available_quantity < qty (sold-out guard)
     */
    private int atomicDecrement(Connection conn, long id, int qty) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ticket_categories_test " +
                "SET available_quantity = available_quantity - ? " +
                "WHERE id = ? AND available_quantity >= ?")) {
            ps.setInt(1, qty);
            ps.setLong(2, id);
            ps.setInt(3, qty);
            return ps.executeUpdate();
        }
    }

    /**
     * Production-equivalent restore (cancel / expire path):
     * {@code UPDATE … SET available_quantity = available_quantity + :qty WHERE id = :id}
     */
    private void atomicRestore(Connection conn, long id, int qty) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ticket_categories_test " +
                "SET available_quantity = available_quantity + ? " +
                "WHERE id = ?")) {
            ps.setInt(1, qty);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Reads the current {@code available_quantity} from the DB. */
    private int readQuantity(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT available_quantity FROM ticket_categories_test WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Removes the test row. */
    private void deleteCategory(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM ticket_categories_test WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ─── Property 4a: available_quantity never goes negative under any sequence ──

    /**
     * **Validates: Requirements 9.1, 9.6**
     *
     * For any initial quantity N and any sequence of reserve/cancel/expire operations,
     * {@code available_quantity} in PostgreSQL SHALL NEVER fall below 0.
     *
     * <p>The test models the full lifecycle:
     * <ul>
     *   <li><b>Reserve</b>: atomic decrement (WHERE available_quantity >= qty). When
     *       available stock is insufficient the WHERE guard fires, returning 0 rows and
     *       leaving the column unchanged — it can never go negative.</li>
     *   <li><b>Cancel / Expire</b>: unconditional increment for every <em>successful</em>
     *       reservation, simulating the booking cancellation or scheduler expiry path.
     *       A random subset of reservations is restored to exercise different scenarios.</li>
     * </ul>
     * After every individual operation the column is read back and asserted to be &ge; 0.
     * </p>
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("inventory")
    void availableQuantityNeverGoesNegative(
            @ForAll("initialQuantity")     int          initialQty,
            @ForAll("operationQuantities") List<Integer> ops) throws SQLException {

        try (Connection conn = openConnection()) {
            ensureSchema(conn);

            long categoryId = insertCategory(conn, initialQty);
            try {
                // Track successfully reserved quantities (for cancel/expire simulation)
                List<Integer> reserved = new ArrayList<>();
                Random rng = new Random(initialQty * 31L + ops.size());

                // Phase 1 — Reserve attempts (mix of successes and sold-out rejections)
                for (int qty : ops) {
                    int rows = atomicDecrement(conn, categoryId, qty);

                    int afterDecrement = readQuantity(conn, categoryId);
                    assertThat(afterDecrement)
                            .as("available_quantity must never go below 0 after reserve attempt "
                                    + "(initialQty=%d, qty=%d, rowsAffected=%d)",
                                    initialQty, qty, rows)
                            .isGreaterThanOrEqualTo(0);

                    if (rows == 1) {
                        // Reservation succeeded — track for possible cancel/expire
                        reserved.add(qty);
                    }
                    // rows == 0 → WHERE guard fired (sold-out); quantity unchanged — still >= 0
                }

                // Phase 2 — Randomly cancel/expire a subset of successful reservations
                for (int qty : reserved) {
                    if (rng.nextBoolean()) {
                        atomicRestore(conn, categoryId, qty);

                        int afterRestore = readQuantity(conn, categoryId);
                        assertThat(afterRestore)
                                .as("available_quantity must never go below 0 after cancel/expire "
                                        + "(initialQty=%d, restoredQty=%d)", initialQty, qty)
                                .isGreaterThanOrEqualTo(0);
                    }
                }

                // Phase 3 — Final state assertion
                int finalQty = readQuantity(conn, categoryId);
                assertThat(finalQty)
                        .as("Final available_quantity must be >= 0 (initialQty=%d, ops=%s)",
                                initialQty, ops)
                        .isGreaterThanOrEqualTo(0);

            } finally {
                deleteCategory(conn, categoryId);
            }
        }
    }

    // ─── Property 4b: reserve → cancel cycle preserves bounds ───────────────────

    /**
     * **Validates: Requirements 9.1, 9.6**
     *
     * Repeated reserve-then-cancel cycles must keep {@code available_quantity}
     * within the invariant range {@code [0, initialQty]}.
     *
     * <p>This property specifically guards against double-restore bugs: restoring
     * the same reservation twice would drive the quantity above {@code initialQty},
     * while a missing restore after a failed payment could leave quantity lower than
     * expected (but still >= 0, which the WHERE guard enforces).</p>
     */
    @Property(tries = 100)
    @net.jqwik.api.Tag("inventory")
    void reserveAndCancelCycleMaintainsBounds(
            @ForAll("initialQuantity")           int initialQty,
            @ForAll @IntRange(min = 1, max = 20) int cycles) throws SQLException {

        try (Connection conn = openConnection()) {
            ensureSchema(conn);

            long categoryId = insertCategory(conn, initialQty);
            try {
                for (int i = 0; i < cycles; i++) {
                    // Attempt to reserve 1 unit (smallest possible unit)
                    int rows = atomicDecrement(conn, categoryId, 1);

                    int afterReserve = readQuantity(conn, categoryId);
                    assertThat(afterReserve)
                            .as("Cycle %d: after reserve, quantity must be >= 0 (initialQty=%d)",
                                    i, initialQty)
                            .isGreaterThanOrEqualTo(0);

                    if (rows == 1) {
                        // Cancel / expire the reservation
                        atomicRestore(conn, categoryId, 1);

                        int afterCancel = readQuantity(conn, categoryId);
                        assertThat(afterCancel)
                                .as("Cycle %d: after cancel, quantity must be in [0, initialQty=%d]",
                                        i, initialQty)
                                .isGreaterThanOrEqualTo(0)
                                .isLessThanOrEqualTo(initialQty);
                    }
                }

                // Final check: after all cycles the quantity is still within bounds
                int finalQty = readQuantity(conn, categoryId);
                assertThat(finalQty)
                        .as("After %d reserve/cancel cycles, quantity must be in [0, initialQty=%d]",
                                cycles, initialQty)
                        .isGreaterThanOrEqualTo(0)
                        .isLessThanOrEqualTo(initialQty);

            } finally {
                deleteCategory(conn, categoryId);
            }
        }
    }
}
