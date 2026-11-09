package com.jarvisblue.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveSizePolicyTest {
    @Test fun breakpointEdges() {
        assertEquals(ResponsiveSizePolicy.Width.COMPACT, ResponsiveSizePolicy.classify(599.9f))
        assertEquals(ResponsiveSizePolicy.Width.MEDIUM, ResponsiveSizePolicy.classify(600f))
        assertEquals(ResponsiveSizePolicy.Width.MEDIUM, ResponsiveSizePolicy.classify(839.9f))
        assertEquals(ResponsiveSizePolicy.Width.EXPANDED, ResponsiveSizePolicy.classify(840f))
    }

    @Test fun compactNavAndCards() {
        assertFalse(ResponsiveSizePolicy.showNavigationRail(350f))
        assertTrue(ResponsiveSizePolicy.stackHomeShortcuts(350f))
        assertFalse(ResponsiveSizePolicy.stackHomeShortcuts(390f))
        assertTrue(ResponsiveSizePolicy.showNavigationRail(700f))
    }
}
