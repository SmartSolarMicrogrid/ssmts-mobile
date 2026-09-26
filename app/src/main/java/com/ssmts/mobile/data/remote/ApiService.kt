package com.ssmts.mobile.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit definition of the SSMTS backend REST API (/api/v1). */
interface ApiService {

    // ── Auth ───────────────────────────────────────────────────────────

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: ProsumerRegisterRequest): ProsumerDto

    @GET("auth/me")
    suspend fun me(): CurrentUserDto

    // ── Prosumer self-service ──────────────────────────────────────────

    @GET("prosumers/me")
    suspend fun myProfile(): ProsumerDto

    @PUT("prosumers/me")
    suspend fun updateMyProfile(@Body body: ProsumerUpdateRequest): ProsumerDto

    @PUT("prosumers/me/request-deactivation")
    suspend fun requestDeactivation(): Response<Unit>

    // ── Nodes & slots ──────────────────────────────────────────────────

    @GET("nodes")
    suspend fun listNodes(): List<NodeDto>

    @GET("nodes/nearby")
    suspend fun nearbyNodes(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 50.0,
        @Query("max") max: Int = 20
    ): List<NodeDto>

    @GET("nodes/{id}")
    suspend fun getNode(@Path("id") id: String): NodeDto

    @GET("nodes/{id}/slots")
    suspend fun slotsForDay(
        @Path("id") nodeId: String,
        @Query("date") date: String   // YYYY-MM-DD
    ): List<SlotDto>

    // ── Reservations ───────────────────────────────────────────────────

    @POST("reservations")
    suspend fun createReservation(@Body body: CreateReservationRequest): ReservationDto

    @GET("reservations/mine")
    suspend fun myReservations(): List<ReservationDto>

    @GET("reservations")
    suspend fun searchReservations(
        @Query("status") status: String? = null,
        @Query("nodeId") nodeId: String? = null,
        @Query("limit") limit: Int = 50
    ): List<ReservationDto>

    @GET("reservations/{id}")
    suspend fun getReservation(@Path("id") id: String): ReservationDto

    @PUT("reservations/{id}")
    suspend fun modifyReservation(
        @Path("id") id: String,
        @Body body: ModifyReservationRequest
    ): ReservationDto

    // OkHttp requires a body on PATCH — send an empty JSON object.
    @PATCH("reservations/{id}/cancel")
    suspend fun cancelReservation(
        @Path("id") id: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<Unit>

    @PATCH("reservations/{id}/approve")
    suspend fun approveReservation(
        @Path("id") id: String,
        @Body body: Map<String, String> = emptyMap()
    ): ReservationDto

    @PATCH("reservations/{id}/reject")
    suspend fun rejectReservation(
        @Path("id") id: String,
        @Body body: RejectReservationRequest
    ): ReservationDto

    // ── QR & transfers ─────────────────────────────────────────────────

    @GET("reservations/{id}/qr")
    suspend fun getQr(@Path("id") reservationId: String): QrResponse

    @POST("transfers/verify")
    suspend fun verifyTransfer(@Body body: VerifyTransferRequest): ReservationDto

    @POST("transfers/{reservationId}/finalize")
    suspend fun finalizeTransfer(
        @Path("reservationId") reservationId: String,
        @Body body: FinalizeTransferRequest
    ): ReservationDto

    // ── Dashboards ─────────────────────────────────────────────────────

    @GET("dashboard/prosumer")
    suspend fun prosumerDashboard(): ProsumerDashboardDto

    @GET("dashboard/operator")
    suspend fun operatorDashboard(): OperatorDashboardDto
}
