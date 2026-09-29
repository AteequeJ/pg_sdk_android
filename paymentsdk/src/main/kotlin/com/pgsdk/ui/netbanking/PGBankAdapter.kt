package com.pgsdk.ui.netbanking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pgsdk.databinding.PgItemBankBinding
import com.pgsdk.network.dto.BankDto

internal class PGBankAdapter(
    private val onBankSelected: (BankDto) -> Unit
) : RecyclerView.Adapter<PGBankAdapter.ViewHolder>() {

    private var banks: List<BankDto> = emptyList()

    fun submit(banks: List<BankDto>) {
        this.banks = banks
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = banks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = PgItemBankBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(banks[position], onBankSelected)
    }

    internal class ViewHolder(private val binding: PgItemBankBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(bank: BankDto, onBankSelected: (BankDto) -> Unit) {
            binding.pgBankName.text = bank.name
            binding.root.setOnClickListener { onBankSelected(bank) }
        }
    }
}
