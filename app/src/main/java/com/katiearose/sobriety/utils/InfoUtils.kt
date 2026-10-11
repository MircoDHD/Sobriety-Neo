package com.katiearose.sobriety.utils

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.katiearose.sobriety.R

/*
 * The "i" (info) buttons of Sobriety Neo.
 *
 * Every screen has an "i" in its top bar (menu item R.id.info, see res/menu/menu_info.xml) and
 * some dialogs have an "i" button. Tapping it opens a short explanation of what the screen or
 * feature does. The texts are the strings named "info_*" in res/values/strings.xml.
 *
 * To add an explanation to a new screen:
 *  1. inflate R.menu.menu_info in onCreateOptionsMenu,
 *  2. call showInfo(R.string.your_title, R.string.info_your_text) for R.id.info in
 *     onOptionsItemSelected (or use handleInfoMenuItem below).
 */

/** Shows an explanation dialog with a title and a text. */
fun Context.showInfo(@StringRes title: Int, @StringRes text: Int) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setIcon(R.drawable.info_24px)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

/** Makes [this] view (usually an "i" ImageView) open the explanation when tapped. */
fun View.setInfoClick(@StringRes title: Int, @StringRes text: Int) {
    setOnClickListener { context.showInfo(title, text) }
}

/**
 * Helper for onOptionsItemSelected: shows the explanation if [itemId] is the info menu item.
 *
 * @return true if the item was handled
 */
fun Context.handleInfoMenuItem(itemId: Int, @StringRes title: Int, @StringRes text: Int): Boolean {
    if (itemId != R.id.info) return false
    showInfo(title, text)
    return true
}
