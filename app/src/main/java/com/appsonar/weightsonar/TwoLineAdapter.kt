package com.appsonar.weightsonar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.appsonar.weightsonar.databinding.RowTwoLineBinding

/**
 * Ein Adapter für alle zweizeiligen Listen der App (Profile, Lebensmittel,
 * Aktivitäten, Katalog): [describe] liefert Titel und Untertitel je Eintrag.
 */
class TwoLineAdapter<T>(
    private val onClick: (T) -> Unit,
    private val onLongClick: (T) -> Unit,
    private val describe: (T) -> Pair<String, String>,
) : RecyclerView.Adapter<TwoLineAdapter<T>.Holder>() {

    private val items = mutableListOf<T>()

    fun submit(newItems: List<T>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowTwoLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(private val binding: RowTwoLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: T) {
            val (title, subtitle) = describe(item)
            binding.rowTitle.text = title
            binding.rowSubtitle.text = subtitle
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener { onLongClick(item); true }
        }
    }
}
