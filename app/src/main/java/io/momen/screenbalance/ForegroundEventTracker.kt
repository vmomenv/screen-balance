package io.momen.screenbalance

class ForegroundEventTracker {
    var currentPackage: String? = null
        private set

    fun record(
        packageName: String,
        isWindowStateChange: Boolean,
        isProtected: Boolean,
    ) {
        if (isProtected || isWindowStateChange) {
            currentPackage = packageName
        }
    }

    fun clear() {
        currentPackage = null
    }
}
