package com.skyrist.markme.network

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {
    // model e.g. "gemini-1.5-flash"
    @POST("v1beta/models/{model}:generateContent")
    fun generateContent(
        @Path("model") model: String,
        @Body body: Map<String, Any>
    ): Call<JsonObject>
}
