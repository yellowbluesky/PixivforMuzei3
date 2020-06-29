package com.antony.muzei.pixiv.util

import android.widget.TextView

fun TextView.text(trim: Boolean = false): CharSequence =
    this.text?.toString()
        ?.let {
            if (trim) it.trim() else it
        }
        ?: ""
