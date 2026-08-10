package io.momen.screenbalance

class ForegroundEventTracker {
    var currentPackage: String? = null
        private set

    fun record(
        packageName: String,
        isWindowStateChange: Boolean,
        pageState: GuardedPageState,
    ) {
        when (pageState) {
            GuardedPageState.GUARDED -> currentPackage = packageName
            GuardedPageState.UNGUARDED -> {
                if (isWindowStateChange) currentPackage = null
            }
            GuardedPageState.UNKNOWN -> Unit
        }
    }

    fun clear() {
        currentPackage = null
    }
}
