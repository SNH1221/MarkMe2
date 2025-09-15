package com.skyrist.markme.network

import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val API_KEY = try { com.skyrist.markme.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

    private val authInterceptor = Interceptor { chain ->
        val rb = chain.request().newBuilder()
            .addHeader("Content-Type", "application/json")
        if (API_KEY.isNotBlank()) {
            // add key header
            rb.addHeader("X-Goog-Api-Key", API_KEY)
            // alternative: you could pass ?key=... in URL; header works fine
        }
        chain.proceed(rb.build())
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()

    val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttp)
            .build()
            .create(GeminiApi::class.java)
    }
}
