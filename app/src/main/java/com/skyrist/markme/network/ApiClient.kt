package com.skyrist.markme.network

import android.util.Log
import com.skyrist.markme.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val authInterceptor = Interceptor { chain ->
        val original: Request = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Content-Type", "application/json")

        val apiKey = BuildConfig.SMS_API_KEY
        if (apiKey.isNotBlank()) {
            requestBuilder.header("x-api-key", apiKey)
        }

        val newReq = requestBuilder.build()
        Log.d("ApiClient", "Outgoing request: ${newReq.method} ${newReq.url}")
        chain.proceed(newReq)
    }

    private val logging = HttpLoggingInterceptor { msg -> Log.d("OkHttp", msg) }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .build()

    val smsApi: SmsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SMS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttp)
            .build()
            .create(SmsApi::class.java)
    }
}