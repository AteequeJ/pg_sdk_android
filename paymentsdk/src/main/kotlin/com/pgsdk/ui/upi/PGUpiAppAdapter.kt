package com.pgsdk.ui.upi

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pgsdk.R
import com.pgsdk.databinding.PgItemUpiAppBinding
import com.pgsdk.handler.PGUpiApp

/** Renders installed UPI apps plus a trailing "Other apps" tile that opens the system chooser. */
internal class PGUpiAppAdapter(
    private val onAppSelected: (PGUpiApp?) -> Unit
) : RecyclerView.Adapter<PGUpiAppAdapter.ViewHolder>() {

    private var apps: List<PGUpiApp> = emptyList()

    fun submit(apps: List<PGUpiApp>) {
        this.apps = apps
        notifyDataSetChanged()
    }

    /** The last item (index == apps.size) is the "choose from system chooser" tile. */
    override fun getItemCount(): Int = apps.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = PgItemUpiAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps.getOrNull(position), onAppSelected)
    }

    internal class ViewHolder(private val binding: PgItemUpiAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: PGUpiApp?, onAppSelected: (PGUpiApp?) -> Unit) {
            if (app != null) {
                binding.pgAppLabel.text = app.label
                binding.pgAppIcon.setImageDrawable(
                    app.icon ?: ContextCompat.getDrawable(binding.root.context, R.drawable.pg_ic_upi_placeholder)
                )
            } else {
                binding.pgAppLabel.text = binding.root.context.getString(R.string.pg_upi_other_apps)
                binding.pgAppIcon.setImageResource(R.drawable.pg_ic_upi_placeholder)
            }
            binding.root.setOnClickListener { onAppSelected(app) }
        }
    }
}
