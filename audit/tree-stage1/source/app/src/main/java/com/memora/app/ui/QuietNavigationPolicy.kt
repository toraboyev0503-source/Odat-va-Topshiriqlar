package com.memora.app.ui

/**
 * Route and bottom-chrome rules shared by Memora's quiet navigation.
 * Free of Android/Compose dependencies so they can be verified with plain JVM tests.
 */
internal object QuietNavigationPolicy {
    const val DETAIL_DURATION_MS = 170
    const val DETAIL_TRAVEL_DP = 12

    fun isPrimaryDestination(route: String?, topilmalarMode: String? = null): Boolean = when (route) {
        "home", "notes_hub/{filter}", "calendar_root", "settings_hub" -> true
        else -> false
    }

    fun needsDetailMotion(
        fromRoute: String?, fromMode: String?,
        toRoute: String?, toMode: String?
    ): Boolean = !(
        isPrimaryDestination(fromRoute, fromMode) &&
            isPrimaryDestination(toRoute, toMode)
        )

    fun showBottomBar(
        rootSelected: Boolean,
        keyboardVisible: Boolean,
        detailStillVisible: Boolean
    ): Boolean = rootSelected && !keyboardVisible && !detailStillVisible
}
