package de.marmaro.krt.ffupdater.app.impl

import android.graphics.Color
import android.os.Build
import de.marmaro.krt.ffupdater.R
import de.marmaro.krt.ffupdater.app.AvailableVersionResult
import de.marmaro.krt.ffupdater.app.BaseAppDetail
import de.marmaro.krt.ffupdater.app.impl.fetch.ApiConsumer
import de.marmaro.krt.ffupdater.app.impl.fetch.github.GithubConsumer
import de.marmaro.krt.ffupdater.app.impl.fetch.github.GithubConsumer.Asset
import de.marmaro.krt.ffupdater.app.impl.fetch.github.GithubConsumer.Release
import de.marmaro.krt.ffupdater.device.ABI

/**
 * https://api.github.com/repos/brave/brave-browser/releases
 */
class Brave(private val apiConsumer: ApiConsumer) : BaseAppDetail() {
    override val packageName = "com.brave.browser"
    override val displayTitle = R.string.brave__title
    override val displayDescription = R.string.brave__description
    override val displayWarning = R.string.brave__warning
    override val displayDownloadSource = R.string.github
    override val displayIcon = R.mipmap.ic_logo_brave
    override val displayIconBackground = Color.parseColor("#FFFFFF")
    override val minApiLevel = Build.VERSION_CODES.N
    override val supportedAbis = listOf(ABI.ARM64_V8A, ABI.ARMEABI_V7A, ABI.X86_64, ABI.X86)

    @Suppress("SpellCheckingInspection")
    override val signatureHash = "9c2db70513515fdbfbbc585b3edf3d7123d4dc67c94ffd306361c1d79bbf18ac"

    override suspend fun updateCheckWithoutCaching(): AvailableVersionResult {
        val fileName = getStringForCurrentAbi("BraveMonoarm.apk", "BraveMonoarm64.apk",
                "BraveMonox86.apk", "BraveMonox64.apk")
        val githubConsumer = GithubConsumer(
                apiConsumer = apiConsumer,
                repoOwner = "brave",
                repoName = "brave-browser",
                resultsPerPage = 20,
                validReleaseTester = { release: Release ->
                    !release.isPreRelease &&
                            release.name.startsWith("Release v") &&
                            release.assets.any { it.name == fileName }
                },
                correctDownloadUrlTester = { asset: Asset -> asset.name == fileName })
        val result = githubConsumer.updateCheckReliableOnlyForNormalReleases()
        val version = result.tagName.replace("v", "")
        return AvailableVersionResult(
                downloadUrl = result.url,
                version = version,
                publishDate = result.releaseDate,
                fileSizeBytes = result.fileSizeBytes,
                fileHash = null)
    }
}