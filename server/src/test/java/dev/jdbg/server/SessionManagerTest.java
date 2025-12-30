package dev.jdbg.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionManager.
 */
class SessionManagerTest {

    private SessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SessionManager();
    }

    @Test
    void testCreateSessionGeneratesId() {
        String sessionId = manager.createSession(null);
        assertNotNull(sessionId);
        assertFalse(sessionId.isEmpty());
    }

    @Test
    void testCreateSessionWithCustomId() {
        String customId = "my-session";
        String sessionId = manager.createSession(customId);
        assertEquals(customId, sessionId);
    }

    @Test
    void testGetSessionThrowsForUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.getSession("nonexistent-session-id");
        });
    }

    @Test
    void testListSessionsReturnsEmptyInitially() {
        assertTrue(manager.listSessions().isEmpty());
    }

    @Test
    void testGetActiveSessionIdReturnsNullInitially() {
        assertNull(manager.getActiveSessionId());
    }
}
