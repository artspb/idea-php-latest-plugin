package me.artspb.idea.php.latest.plugin

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.RequestBuilder
import org.jetbrains.io.mandatory.Mandatory
import org.jetbrains.io.mandatory.NullCheckingFactory
import org.jetbrains.io.mandatory.RestModel
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

val LATEST_RELEASE_URL = "https://api.github.com/repos/artspb/php-latest-mac/releases/latest"

fun requestRelease(): GitHubRelease = getRequestBuilder(LATEST_RELEASE_URL).connect { request ->
    val response = parseResponse(request.inputStream)
    return@connect fromJson(response, GitHubRelease::class.java)
}

private fun parseResponse(json: InputStream): JsonElement {
    InputStreamReader(json, CharsetToolkit.UTF8_CHARSET).use { reader ->
        return JsonParser().parse(reader)
    }
}

private fun <T> fromJson(json: JsonElement, clazz: Class<T>) = initGson().fromJson(json, clazz) ?: throw IllegalStateException("GitHub response parsing failed")

private fun initGson() = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapterFactory(NullCheckingFactory.INSTANCE)
        .create()

fun downloadInterpreter(url: String, file: File, indicator: ProgressIndicator) = getRequestBuilder(url).saveToFile(file, indicator)

private fun getRequestBuilder(url: String): RequestBuilder = HttpRequests.request(url).forceHttps(true)

@RestModel
class GitHubRelease(@Mandatory val tagName: String, @Mandatory val assets: List<Asset>)

@RestModel
class Asset(@Mandatory val browserDownloadUrl: String)