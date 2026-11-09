package com.eventms.mobile.data.remote

import com.eventms.mobile.data.remote.dto.MpesaInitiateRequest
import com.eventms.mobile.data.remote.dto.MpesaInitiateResponse
import com.eventms.mobile.data.remote.dto.PaymentInitiateRequest
import com.eventms.mobile.data.remote.dto.PaymentInitiateResponse
import com.eventms.mobile.data.remote.dto.PaymentStatusResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The payment gateway.
 *
 * Card and mobile-money capture needs merchant credentials, and anything shipped
 * inside an APK can be extracted — so the app never talks to the payment
 * provider directly. It asks this service to open a checkout, sends the customer
 * to the returned URL, and then asks it what happened. The provider is verified
 * server-side before a booking is ever marked paid; the app cannot mark its own
 * booking paid at all.
 */
interface PaymentsApi {

    /** Opens a checkout and returns the URL to send the customer to. */
    @POST("initiate")
    suspend fun initiate(@Body request: PaymentInitiateRequest): PaymentInitiateResponse

    /**
     * Sends an M-Pesa PIN prompt to the customer's phone. Nothing opens in a
     * browser — they confirm on the handset and the app polls [status].
     */
    @POST("mpesa/initiate")
    suspend fun initiateMpesa(@Body request: MpesaInitiateRequest): MpesaInitiateResponse

    /** Asks the gateway to re-check and sync the booking's payment state. */
    @POST("status")
    suspend fun status(@Body request: PaymentInitiateRequest): PaymentStatusResponse
}
