package de.lemke.oneurl.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.oneurl.R
import de.lemke.oneurl.databinding.DialogAddUrlBinding
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.ShortUrlProvider
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddUrlDialog : DialogFragment() {
    private lateinit var binding: DialogAddUrlBinding

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogAddUrlBinding.inflate(layoutInflater)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, ShortUrlProvider.values().map { it.toString() })
        adapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        binding.providerSpinner.adapter = adapter
        lifecycleScope.launch {
            val userSettings = getUserSettings()

        }

        val builder = AlertDialog.Builder(requireContext())
            //.setTitle(R.string.add_url)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.sesl_cancel, null)
        return builder.create()
    }
}

fun EditText.toggle() {
    requestFocus()
    (context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, 0)
}

fun EditText.requestFocusWithKeyboard() {
    post {
        dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0f, 0f, 0))
        setSelection(length())
    }
}