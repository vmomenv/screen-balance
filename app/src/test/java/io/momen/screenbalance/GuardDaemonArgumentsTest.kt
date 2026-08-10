package io.momen.screenbalance

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardDaemonArgumentsTest {
    @Test
    fun configurationRoundTripsThroughProcessArguments() {
        val configuration = GuardDaemonConfiguration(
            guardEnabled = true,
            guardDurationMillis = 120_000L,
            temporaryPassUntilMillis = 1_000L,
            cooldownUntilMillis = 2_000L,
            protectedPackages = linkedSetOf(
                "com.tencent.mm",
                "com.ss.android.ugc.aweme",
            ),
        )

        assertEquals(
            configuration,
            GuardDaemonArguments.decode(GuardDaemonArguments.encode(configuration)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPackageNameIsRejected() {
        GuardDaemonArguments.encode(
            GuardDaemonConfiguration(
                guardEnabled = true,
                guardDurationMillis = 1_000L,
                temporaryPassUntilMillis = 0L,
                cooldownUntilMillis = 0L,
                protectedPackages = setOf("com.example;am force-stop android"),
            ),
        )
    }
}
