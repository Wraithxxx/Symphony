package io.github.zyrouge.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RadioQueueNavigationTest {
    @Test
    fun `previous from first wraps to final entry`() {
        assertEquals(3, RadioQueueNavigation.previousIndex(0, queueSize = 4))
    }

    @Test
    fun `next from final wraps to first entry`() {
        assertEquals(0, RadioQueueNavigation.nextIndex(3, queueSize = 4))
    }

    @Test
    fun `middle navigation remains sequential`() {
        assertEquals(1, RadioQueueNavigation.previousIndex(2, queueSize = 4))
        assertEquals(3, RadioQueueNavigation.nextIndex(2, queueSize = 4))
    }

    @Test
    fun `two-entry queue cycles in both directions`() {
        assertEquals(1, RadioQueueNavigation.previousIndex(0, queueSize = 2))
        assertEquals(0, RadioQueueNavigation.nextIndex(1, queueSize = 2))
    }

    @Test
    fun `single-entry queue does not rebuild the same player`() {
        assertNull(RadioQueueNavigation.previousIndex(0, queueSize = 1))
        assertNull(RadioQueueNavigation.nextIndex(0, queueSize = 1))
    }

    @Test
    fun `empty and invalid queue positions cannot navigate`() {
        assertNull(RadioQueueNavigation.previousIndex(-1, queueSize = 0))
        assertNull(RadioQueueNavigation.nextIndex(-1, queueSize = 0))
        assertNull(RadioQueueNavigation.previousIndex(4, queueSize = 4))
        assertNull(RadioQueueNavigation.nextIndex(4, queueSize = 4))
    }
}
