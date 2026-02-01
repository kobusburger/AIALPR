package com.example.aialpr.api

import com.example.aialpr.BuildConfig
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object PlateRecognizerService {

    private const val BASE_URL = "https://api.platerecognizer.com/v1/plate-reader/"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun recognizePlate(
        imageFile: File,
        onSuccess: (PlateRecognizerResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "upload",
                imageFile.name,
                imageFile.asRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Token ${BuildConfig.PLATE_RECOGNIZER_API_TOKEN}")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val err = gson.fromJson(body, PlateRecognizerResponse::class.java)
                        err.detail ?: "HTTP ${response.code}: $body"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $body"
                    }
                    onError(errorMsg)
                    return
                }
                try {
                    val result = gson.fromJson(body, PlateRecognizerResponse::class.java)
                    onSuccess(result)
                } catch (e: Exception) {
                    onError("Parse error: ${e.message}")
                }
            }
        })
    }
}
