package com.project.ti2358.ui.donate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.project.ti2358.R
import com.project.ti2358.databinding.FragmentDonateBinding
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class DonateFragment : Fragment(R.layout.fragment_donate) {
    private var fragmentDonateBinding: FragmentDonateBinding? = null

    override fun onDestroy() {
        fragmentDonateBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDonateBinding.bind(view)
        fragmentDonateBinding = binding

        with(binding) {
            donateOstapButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tinkoff.ru/sl/4gwbQl8M2Hf"))
                requireContext().startActivity(intent)
            }

            donateNurlanButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tinvest.daager.ru/daager"))
                requireContext().startActivity(intent)
            }

            tutorialButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/oostap/2358/wiki/Инструкция-к-2358"))
                requireContext().startActivity(intent)
            }
        }
    }
}