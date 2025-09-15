package com.skyrist.markme.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SmsApi {
    @POST("send-sms")
    fun sendSms(@Body req: SmsRequest): Call<SmsResponse>
}