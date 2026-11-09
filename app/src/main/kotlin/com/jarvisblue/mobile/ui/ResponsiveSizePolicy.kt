package com.jarvisblue.mobile.ui

/** App window width, not physical display pixels. Handles split-screen and folding. */
internal object ResponsiveSizePolicy {
    enum class Width { COMPACT, MEDIUM, EXPANDED }

    fun classify(availableWidthDp: Float): Width = when {
        availableWidthDp >= 840f -> Width.EXPANDED
        availableWidthDp >= 600f -> Width.MEDIUM
        else -> Width.COMPACT
    }

    /** Rail is appropriate when sufficient horizontal room remains for content. */
    fun showNavigationRail(availableWidthDp: Float): Boolean = classify(availableWidthDp) != Width.COMPACT

    /** Avoid two tiny, inaccessible home cards on very narrow windows. */
    fun stackHomeShortcuts(availableWidthDp: Float): Boolean = availableWidthDp < 365f
}
