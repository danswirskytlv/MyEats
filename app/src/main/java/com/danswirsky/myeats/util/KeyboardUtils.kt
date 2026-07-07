package com.danswirsky.myeats.util

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

/** Closes the soft keyboard and clears focus from the current field. */
fun Fragment.hideKeyboard() {
    val imm = requireContext()
        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    view?.let { root ->
        imm.hideSoftInputFromWindow(root.windowToken, 0)
        root.findFocus()?.clearFocus()
    }
}

/**
 * Makes tapping anywhere on [root] (outside an input field) close the
 * keyboard — the standard "tap outside to dismiss" behavior.
 */
@SuppressLint("ClickableViewAccessibility")
fun Fragment.dismissKeyboardOnTapOutside(root: View) {
    root.setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN) hideKeyboard()
        false // don't consume — scrolling still works
    }
}
