package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardedPageClassifierTest {
    @Test
    fun selectedNonWechatAppIsGuarded() {
        assertEquals(
            GuardedPageState.GUARDED,
            GuardedPageClassifier.classify(
                packageName = "com.ss.android.ugc.aweme",
                className = "com.ss.android.ugc.aweme.main.MainActivity",
                isPackageSelected = true,
            ),
        )
    }

    @Test
    fun ordinaryWechatPagesAreNotGuarded() {
        listOf(
            "com.tencent.mm.ui.LauncherUI",
            "com.tencent.mm.plugin.scanner.ui.BaseScanUI",
            "com.tencent.mm.plugin.wallet.pay.ui.WalletPayUI",
        ).forEach { className ->
            assertEquals(
                GuardedPageState.UNGUARDED,
                GuardedPageClassifier.classify(
                    packageName = GuardedPageClassifier.WECHAT_PACKAGE,
                    className = className,
                    isPackageSelected = true,
                ),
            )
        }
    }

    @Test
    fun wechatChannelsPagesAreGuarded() {
        listOf(
            "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI",
            "com.tencent.mm.plugin.finder.ui.FinderHomeUI",
            "com.tencent.mm.plugin.finder.feed.ui.FinderAllTimelineUI",
            "com.tencent.mm.plugin.flutter.finder.live.ui.LiveFlutterActivity",
            "com.tencent.mm.plugin.flutter.ui.MegaVideoFlutterActivity",
        ).forEach { className ->
            assertEquals(
                GuardedPageState.GUARDED,
                GuardedPageClassifier.classify(
                    packageName = GuardedPageClassifier.WECHAT_PACKAGE,
                    className = className,
                    isPackageSelected = true,
                ),
            )
        }
    }

    @Test
    fun missingWechatClassIsUnknownRatherThanGuarded() {
        assertEquals(
            GuardedPageState.UNKNOWN,
            GuardedPageClassifier.classify(
                packageName = GuardedPageClassifier.WECHAT_PACKAGE,
                className = null,
                isPackageSelected = true,
            ),
        )
    }

    @Test
    fun unselectedAppIsNeverGuarded() {
        assertEquals(
            GuardedPageState.UNGUARDED,
            GuardedPageClassifier.classify(
                packageName = "com.ss.android.ugc.aweme",
                className = "com.ss.android.ugc.aweme.main.MainActivity",
                isPackageSelected = false,
            ),
        )
    }
}
