package com.project.ti2358.ui.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.model.dto.Account
import com.project.ti2358.databinding.FragmentAccountsBinding
import com.project.ti2358.databinding.FragmentAccountsItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class AccountsFragment : Fragment(R.layout.fragment_accounts) {
    val depositManager: DepositManager by inject()

    private var fragmentAccountsBinding: FragmentAccountsBinding? = null

    var adapterList: ItemFavoritesRecyclerViewAdapter = ItemFavoritesRecyclerViewAdapter(emptyList())
    lateinit var accounts: MutableList<Account>
    var job: Job? = null

    override fun onDestroy() {
        fragmentAccountsBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAccountsBinding.bind(view)
        fragmentAccountsBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            binding.updateButton.setOnClickListener {
                updateData()
            }

            updateData()
        }
    }

    private fun updateData() {
        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            depositManager.refreshAccounts()
            depositManager.refreshDeposit()
            depositManager.refreshKotleta()
            accounts = depositManager.accounts
            adapterList.setData(accounts)
            updateTitle()
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Счета: ${accounts.size}"
        }
    }

    inner class ItemFavoritesRecyclerViewAdapter(private var values: List<Account>) : RecyclerView.Adapter<ItemFavoritesRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Account>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentAccountsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentAccountsItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val account = values[index]
                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = depositManager.getActiveBrokerAccountId() == account.brokerAccountId

                    nameView.text = account.brokerAccountId
                    typeView.text = account.brokerAccountType

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        depositManager.setActiveBrokerAccountId(account.brokerAccountId)
                        updateData()
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}