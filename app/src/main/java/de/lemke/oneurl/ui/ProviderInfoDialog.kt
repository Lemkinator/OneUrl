package de.lemke.oneurl.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.DialogFragment
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.DialogProviderInfoBinding
import de.lemke.oneurl.domain.OpenLinkUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import javax.inject.Inject

@AndroidEntryPoint
class ProviderInfoDialog(private val provider: ShortURLProvider) : DialogFragment() {
    private lateinit var binding: DialogProviderInfoBinding

    @Inject
    lateinit var openLink: OpenLinkUseCase

    private fun androidx.appcompat.widget.AppCompatButton.setIcon(icon: Int) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(AppCompatResources.getDrawable(requireContext(), icon), null, null, null)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogProviderInfoBinding.inflate(layoutInflater)
        if (provider.name != provider.group) {
            binding.providerDialogInfoGroup.visibility = View.VISIBLE
            binding.providerDialogInfoGroupText.visibility = View.VISIBLE
            binding.providerDialogInfoGroupText.text = provider.group
        }
        provider.getInfoContents(requireContext()).forEachIndexed { index, info ->
            when (index) {
                0 -> binding.providerDialogInfo1 to binding.providerDialogInfoText1
                1 -> binding.providerDialogInfo2 to binding.providerDialogInfoText2
                2 -> binding.providerDialogInfo3 to binding.providerDialogInfoText3
                3 -> binding.providerDialogInfo4 to binding.providerDialogInfoText4
                else -> null
            }?.let { (button, textView) ->
                button.apply {
                    text = info.title
                    setIcon(info.icon)
                    visibility = View.VISIBLE
                }
                textView.apply {
                    text = info.linkOrDescription
                    visibility = View.VISIBLE
                }
            }
        }
        provider.getInfoButtons(requireContext()).forEachIndexed { index, info ->
            when (index) {
                0 -> binding.providerDialogInfoButton1
                1 -> binding.providerDialogInfoButton2
                2 -> binding.providerDialogInfoButton3
                else -> null
            }?.apply {
                text = info.title
                setIcon(info.icon)
                setOnClickListener { openLink(info.linkOrDescription) }
                visibility = View.VISIBLE
            }
        }
        return AlertDialog.Builder(requireContext())
            .setTitle(provider.name)
            .setView(binding.root)
            .setNeutralButton(R.string.ok, null).create()
    }
}