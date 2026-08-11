package io.momen.screenbalance

object ProtectedAppSelection {
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun customPackages(
        selectedPackages: Set<String>,
        recommendedPackages: Set<String>,
    ): Set<String> = selectedPackages
        .filterTo(linkedSetOf()) { packageName -> packageName !in recommendedPackages }

    fun replaceCustomPackages(
        currentPackages: Set<String>,
        recommendedPackages: Set<String>,
        customPackages: Set<String>,
    ): Set<String> {
        customPackages.forEach(::requireValidPackageName)
        return buildSet {
            recommendedPackages.filterTo(this) { packageName -> packageName in currentPackages }
            customPackages.filterTo(this) { packageName -> packageName !in recommendedPackages }
        }
    }

    fun requireValidPackageName(packageName: String): String {
        require(packageNamePattern.matches(packageName)) { "Invalid package name: $packageName" }
        return packageName
    }
}
