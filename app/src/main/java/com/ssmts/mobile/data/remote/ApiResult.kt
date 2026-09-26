package com.ssmts.mobile.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import java.io.IOException

/** Simple result wrapper so activities can branch on success/failure. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = 0) : ApiResult<Nothing>()
}

/** RFC 9457 ProblemDetails shape emitted by the backend on errors. */
private data class ProblemDetails(
    @SerializedName("title") val title: String?,
    @SerializedName("detail") val detail: String?,
    @SerializedName("status") val status: Int?
)

/**
 * Runs an API call and converts failures into a readable message —
 * ProblemDetails detail/title when available, otherwise a friendly fallback.
 */
suspend fun <T> safeApi(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: HttpException) {
    val message = try {
        val raw = e.response()?.errorBody()?.string()
        val problem = if (raw.isNullOrBlank()) null else Gson().fromJson(raw, ProblemDetails::class.java)
        problem?.detail ?: problem?.title ?: defaultHttpMessage(e.code())
    } catch (_: Exception) {
        defaultHttpMessage(e.code())
    }
    ApiResult.Error(message, e.code())
} catch (e: IOException) {
    ApiResult.Error("Cannot reach the server. Check your connection and that the API is running.")
} catch (e: Exception) {
    ApiResult.Error(e.message ?: "Something went wrong.")
}

private fun defaultHttpMessage(code: Int): String = when (code) {
    400 -> "Invalid request. Please check your input."
    401 -> "Session expired. Please sign in again."
    403 -> "You don't have permission to do that."
    404 -> "Not found."
    409 -> "Conflict — the item changed. Refresh and try again."
    422 -> "The request violates a business rule."
    429 -> "Too many attempts. Please wait a minute."
    else -> "Server error ($code). Try again."
}
