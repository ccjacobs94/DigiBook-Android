package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

// ---------------------- API DTOs ----------------------

@JsonClass(generateAdapter = true)
data class AuthRequest(
    @Json(name = "token") val token: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "user") val user: String?
)

@JsonClass(generateAdapter = true)
data class BookDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "author") val author: String,
    @Json(name = "narrator") val narrator: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "duration") val duration: Long, // milliseconds
    @Json(name = "coverUrl") val coverUrl: String,
    @Json(name = "audioUrl") val audioUrl: String
)

@JsonClass(generateAdapter = true)
data class SyncProgressRequest(
    @Json(name = "bookId") val bookId: String,
    @Json(name = "currentPosition") val currentPosition: Long,
    @Json(name = "lastPlaybackTime") val lastPlaybackTime: Long,
    @Json(name = "isCompleted") val isCompleted: Boolean
)

@JsonClass(generateAdapter = true)
data class SyncProgressResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "currentPosition") val currentPosition: Long,
    @Json(name = "isCompleted") val isCompleted: Boolean
)

// ---------------------- Retrofit Interface ----------------------

interface DigiBookApiService {
    @POST("api/v1/auth/validate")
    suspend fun validateConnection(
        @Header("Authorization") token: String,
        @Body request: AuthRequest
    ): AuthResponse

    @GET("api/v1/books")
    suspend fun getAudiobooks(
        @Header("Authorization") token: String
    ): List<BookDto>

    @POST("api/v1/sync/progress")
    suspend fun syncProgress(
        @Header("Authorization") token: String,
        @Body request: SyncProgressRequest
    ): SyncProgressResponse
}

// ---------------------- Retrofit Client Creator ----------------------

object DigiBookClient {
    fun createService(serverUrl: String, cfTokenProvider: () -> String): DigiBookApiService {
        val cleanUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val token = cfTokenProvider()
                val requestBuilder = original.newBuilder()
                if (token.isNotEmpty()) {
                    requestBuilder.addHeader("Cf-Access-Jwt-Assertion", token)
                    requestBuilder.addHeader("Cookie", "CF_Authorization=$token")
                }
                chain.proceed(requestBuilder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(DigiBookApiService::class.java)
    }
}
