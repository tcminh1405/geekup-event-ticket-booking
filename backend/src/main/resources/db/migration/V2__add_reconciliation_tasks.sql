-- =============================================================================
-- V2__add_reconciliation_tasks.sql
-- Reconciliation tasks for Redis inventory re-sync on Redis failure
-- =============================================================================

CREATE TABLE reconciliation_tasks (
    id                   BIGSERIAL  PRIMARY KEY,
    ticket_category_id   BIGINT     NOT NULL,
    expected_quantity    BIGINT     NOT NULL,
    created_at           TIMESTAMP  NOT NULL DEFAULT NOW()
);
