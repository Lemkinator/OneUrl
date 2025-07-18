package de.lemke.oneurl.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.openURL
import de.lemke.oneurl.databinding.ViewProviderInfoBottomsheetBinding
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import dev.oneuiproject.oneui.app.SemBottomSheetDialogFragment

@AndroidEntryPoint
class ProviderInfoBottomSheet : SemBottomSheetDialogFragment() {
    private lateinit var binding: ViewProviderInfoBottomsheetBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            behavior.skipCollapsed = true
            setOnShowListener { behavior.state = STATE_EXPANDED }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ViewProviderInfoBottomsheetBinding.inflate(inflater, container, false).also { binding = it }.root

    private fun AppCompatButton.setIcon(icon: Int) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(requireContext(), icon), null, null, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val provider = ShortURLProviderCompanion.fromString(requireArguments().getString(KEY_PROVIDER)!!)
        binding.providerBottomSheetTitle.text = provider.name
        if (provider.name != provider.group) {
            binding.providerBottomSheetInfoGroup.isVisible = true
            binding.providerBottomSheetInfoGroupText.isVisible = true
            binding.providerBottomSheetInfoGroupText.text = provider.group
        }
        provider.getInfoContents(requireContext()).forEachIndexed { index, info ->
            when (index) {
                0 -> binding.providerBottomSheetInfo1 to binding.providerBottomSheetInfoText1
                1 -> binding.providerBottomSheetInfo2 to binding.providerBottomSheetInfoText2
                2 -> binding.providerBottomSheetInfo3 to binding.providerBottomSheetInfoText3
                3 -> binding.providerBottomSheetInfo4 to binding.providerBottomSheetInfoText4
                else -> null
            }?.let { (button, textView) ->
                button.apply {
                    text = info.title
                    setIcon(info.icon)
                    isVisible = true
                }
                textView.apply {
                    text = info.linkOrDescription
                    isVisible = true
                }
            }
        }
        provider.getInfoButtons(requireContext()).forEachIndexed { index, info ->
            when (index) {
                0 -> binding.providerBottomSheetInfoButton1
                1 -> binding.providerBottomSheetInfoButton2
                2 -> binding.providerBottomSheetInfoButton3
                else -> null
            }?.apply {
                text = info.title
                setIcon(info.icon)
                setOnClickListener { openURL(info.linkOrDescription) }
                isVisible = true
            }
        }
    }

    companion object {
        fun FragmentActivity.showProviderInfoBottomSheet(provider: ShortURLProvider) =
            showProviderInfoBottomSheet(supportFragmentManager, provider)

        fun showProviderInfoBottomSheet(fragmentManager: FragmentManager, provider: ShortURLProvider) =
            newInstance(provider).show(fragmentManager, ProviderInfoBottomSheet::class.java.simpleName)

        private fun newInstance(provider: ShortURLProvider): ProviderInfoBottomSheet = ProviderInfoBottomSheet().apply {
            arguments = Bundle().apply { putString(KEY_PROVIDER, provider.name) }
        }

        const val KEY_PROVIDER = "key_provider"
    }
}
