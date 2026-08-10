package io.momen.screenbalance

object ActivityManagerCommand {
    private const val ACTIVITY_MANAGER_PATH = "/system/bin/am"
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun forceStop(
        userId: Int,
        packageName: String,
    ): Array<String> {
        require(userId >= 0) { "Invalid Android user ID: $userId" }
        require(packageNamePattern.matches(packageName)) { "Invalid package name: $packageName" }
        return arrayOf(
            ACTIVITY_MANAGER_PATH,
            "force-stop",
            "--user",
            userId.toString(),
            packageName,
        )
    }
}
