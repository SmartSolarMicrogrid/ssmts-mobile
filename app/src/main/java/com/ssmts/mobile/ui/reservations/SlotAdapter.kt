package com.ssmts.mobile.ui.reservations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ssmts.mobile.R
import com.ssmts.mobile.data.remote.SlotDto
import com.ssmts.mobile.databinding.ItemSlotBinding
import com.ssmts.mobile.util.TimeUtil

/** Selectable list of booking slots for a chosen node + date. */
class SlotAdapter(
    private val onSelect: (SlotDto) -> Unit
) : ListAdapter<SlotDto, SlotAdapter.Holder>(Diff) {

    var selectedId: String? = null
        private set

    object Diff : DiffUtil.ItemCallback<SlotDto>() {
        override fun areItemsTheSame(a: SlotDto, b: SlotDto) = a.id == b.id
        override fun areContentsTheSame(a: SlotDto, b: SlotDto) = a == b
    }

    fun select(slot: SlotDto) {
        val old = selectedId
        selectedId = slot.id
        currentList.forEachIndexed { index, s ->
            if (s.id == old || s.id == slot.id) notifyItemChanged(index)
        }
        onSelect(slot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: ItemSlotBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(slot: SlotDto) {
            val ctx = b.root.context
            val bookable = slot.status.equals("Available", true) && slot.availableCount > 0

            b.txtTime.text = "${TimeUtil.time(slot.startUtc)} – ${TimeUtil.time(slot.endUtc)}"
            b.txtBays.text = if (bookable) "${slot.availableCount} bay(s) free" else "Full / blocked"

            val selected = slot.id == selectedId
            b.card.strokeColor = ContextCompat.getColor(
                ctx, if (selected) R.color.solar_amber else R.color.outline)
            b.card.strokeWidth = if (selected) 4 else 1
            b.card.setCardBackgroundColor(ContextCompat.getColor(
                ctx, if (selected) R.color.solar_amber_light else R.color.surface))

            b.root.alpha = if (bookable) 1f else 0.45f
            b.root.setOnClickListener { if (bookable) select(slot) }
        }
    }
}
