package io.momen.screenbalance

enum class GuardedPageState {
    GUARDED,
    UNGUARDED,
    UNKNOWN,
}

object GuardedPageClassifier {
    const val WECHAT_PACKAGE = "com.tencent.mm"

    private val weChatChannelsPrefixes = listOf(
        "com.tencent.mm.plugin.finder.",
        "com.tencent.mm.plugin.flutter.finder.",
        "com.tencent.mm.plugin.flutter.ui.MegaVideoFlutter",
        "com.tencent.mm.ui.chatting.gallery.FinderFeed",
    )

    fun classify(
        packageName: String,
        className: String?,
        isPackageSelected: Boolean,
    ): GuardedPageState {
        if (!isPackageSelected) return GuardedPageState.UNGUARDED
        if (packageName != WECHAT_PACKAGE) return GuardedPageState.GUARDED

        val normalizedClassName = className?.trim().orEmpty()
        if (normalizedClassName.isEmpty()) return GuardedPageState.UNKNOWN
        if (weChatChannelsPrefixes.any(normalizedClassName::startsWith)) {
            return GuardedPageState.GUARDED
        }
        return if (normalizedClassName.startsWith("com.tencent.mm.")) {
            GuardedPageState.UNGUARDED
        } else {
            GuardedPageState.UNKNOWN
        }
    }
}
