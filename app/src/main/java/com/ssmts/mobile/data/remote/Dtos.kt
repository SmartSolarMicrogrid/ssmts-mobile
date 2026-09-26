package com.ssmts.mobile.data.remote

/**
 * Data contracts mirroring SmartSolarMicrogrid.API DTOs.
 * ASP.NET Core serializes with camelCase; property names below match 1:1.
 * All dates are ISO-8601 strings, parsed on demand via TimeUtil.
 */

// ── Auth ───────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val expiresAt: String,
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val redirectPath: String?
)

data class CurrentUserDto(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val nic: String?,
    val phone: String?,
    val address: String?,
    val status: String?,
    val isActive: Boolean,
    val registeredAt: String?
)

// ── Prosumer ───────────────────────────────────────────────────────────

data class ProsumerRegisterRequest(
    val nic: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val address: String,
    val password: String
)

data class ProsumerDto(
    val id: String,
    val nic: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val address: String?,
    val status: String,
    val registeredAt: String?,
    val activatedAt: String?
)

data class ProsumerUpdateRequest(
    val fullName: String,
    val phone: String,
    val address: String
)

// ── Nodes & slots ──────────────────────────────────────────────────────

data class NodeDto(
    val id: String,
    val nodeCode: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val buyPricePerKwh: Double,
    val sellPricePerKwh: Double,
    val openTime: String?,
    val closeTime: String?,
    val slotDurationMinutes: Int,
    val status: String,
    val maxKwhPerReservation: Double,
    val capacityBays: Int
)

data class SlotDto(
    val id: String,
    val nodeId: String,
    val localDate: String,
    val startUtc: String,
    val endUtc: String,
    val capacity: Int,
    val bookedCount: Int,
    val availableCount: Int,
    val status: String,
    val version: Int
)

// ── Reservations ───────────────────────────────────────────────────────

data class CreateReservationRequest(
    val nodeId: String,
    val slotId: String,
    val tradeType: String,       // "Export" or "Import"
    val requestedKwh: Double,
    val prosumerNic: String? = null
)

data class ModifyReservationRequest(
    val newSlotId: String? = null,
    val newRequestedKwh: Double? = null,
    val newTradeType: String? = null
)

data class RejectReservationRequest(
    val reason: String
)

data class TransferSummaryDto(
    val meterStartKwh: Double,
    val meterEndKwh: Double,
    val actualKwh: Double,
    val value: Double,
    val finalizedBy: String?,
    val finalizedAtUtc: String?
)

data class ReservationDto(
    val id: String,
    val reservationNo: String,
    val prosumerNic: String,
    val nodeId: String,
    val slotId: String,
    val nodeName: String,
    val slotStartUtc: String,
    val slotEndUtc: String,
    val tradeType: String,
    val requestedKwh: Double,
    val unitPrice: Double,
    val estimatedValue: Double,
    val status: String,
    val isActive: Boolean,
    val version: Int,
    val changeDeadlineUtc: String?,
    val canModify: Boolean,
    val canCancel: Boolean,
    val qrVersion: Int?,
    val qrIssuedAtUtc: String?,
    val transaction: TransferSummaryDto?
)

// ── QR & transfers ─────────────────────────────────────────────────────

data class QrResponse(
    val reservationId: String,
    val reservationNo: String,
    val version: Int,
    val payload: String,
    val backupCode: String,
    val validFromUtc: String,
    val validToUtc: String
)

data class VerifyTransferRequest(
    val payload: String? = null,
    val reservationId: String? = null,
    val backupCode: String? = null
)

data class FinalizeTransferRequest(
    val meterStartKwh: Double,
    val meterEndKwh: Double
)

// ── Dashboards ─────────────────────────────────────────────────────────

data class ProsumerDashboardDto(
    val nic: String,
    val fullName: String,
    val activeBookingsCount: Int,
    val completedTransfersCount: Int,
    val totalEnergyExportedKwh: Double,
    val totalEnergyImportedKwh: Double,
    val netEarnings: Double,
    val upcomingBookings: List<ReservationDto>,
    val recentTransfers: List<ReservationDto>
)

data class OperatorDashboardDto(
    val assignedNodeIds: List<String>,
    val activeSlotsToday: Int,
    val pendingApprovalsCount: Int,
    val inProgressTransfersCount: Int,
    val completedTodayCount: Int,
    val todayEnergyTransferredKwh: Double
)
