package com.ssmts.mobile.ui.reservations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ssmts.mobile.R
import com.ssmts.mobile.data.remote.ReservationDto
import com.ssmts.mobile.databinding.ItemReservationBinding
import com.ssmts.mobile.util.TimeUtil

/** Card list of reservations, shared by the dashboard and booking screens. */
class ReservationAdapter(
    private val onClick: (ReservationDto) -> Unit
) : ListAdapter<ReservationDto, ReservationAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ReservationDto>() {
        override fun areItemsTheSame(a: ReservationDto, b: ReservationDto) = a.id == b.id
        override fun areContentsTheSame(a: ReservationDto, b: ReservationDto) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemReservationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position))

    inner class Holder(private val b: ItemReservationBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: ReservationDto) {
            val ctx = b.root.context
            b.txtNode.text = item.nodeName
            b.txtReservationNo.text = item.reservationNo
            b.txtSlot.text = TimeUtil.slotRange(item.slotStartUtc, item.slotEndUtc)
            b.txtTrade.text = if (item.tradeType.equals("Export", true))
                "Export · sell ${fmtKwh(item.requestedKwh)}"
            else
                "Import · buy ${fmtKwh(item.requestedKwh)}"
            b.txtValue.text = "Rs. %,.2f".format(item.estimatedValue)

            b.txtStatus.text = item.status
            val (fg, bg) = statusColors(item.status)
            b.txtStatus.setTextColor(ContextCompat.getColor(ctx, fg))
            b.txtStatus.background?.setTint(ContextCompat.getColor(ctx, bg))

            b.root.setOnClickListener { onClick(item) }
        }

        private fun fmtKwh(v: Double) = if (v % 1.0 == 0.0) "${v.toInt()} kWh" else "$v kWh"

        private fun statusColors(status: String): Pair<Int, Int> = when (status.lowercase()) {
            "pending" -> R.color.warning to R.color.warning_soft
            "approved" -> R.color.info to R.color.info_soft
            "inprogress" -> R.color.solar_amber_dark to R.color.solar_amber_light
            "completed" -> R.color.success to R.color.success_soft
            "cancelled", "rejected" -> R.color.danger to R.color.danger_soft
            else -> R.color.neutral to R.color.neutral_soft // Expired, NoShow, …
        }
    }
}
