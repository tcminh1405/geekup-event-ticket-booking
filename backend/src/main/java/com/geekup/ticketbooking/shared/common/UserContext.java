package com.geekup.ticketbooking.shared.common;

/**
 * Thread-local holder for the authenticated user ID extracted from the
 * {@code X-User-Id} request header.
 *
 * <p>Set early in the filter chain by {@code UserIdHeaderFilter} and cleared
 * in a finally block after the request completes to prevent thread-pool leaks.</p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
        // utility class — not instantiable
    }

    /**
     * Store the current user's ID for this thread.
     *
     * @param userId the authenticated user ID
     */
    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * Retrieve the current user's ID for this thread.
     *
     * @return the user ID, or {@code null} if not set
     */
    public static Long get() {
        return USER_ID.get();
    }

    /**
     * Remove the user ID from the current thread's local storage.
     * Must be called in a finally block to prevent memory leaks in thread pools.
     */
    public static void clear() {
        USER_ID.remove();
    }
}
