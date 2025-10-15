package com.vincent.library.base.util

import android.util.Log
import androidx.fragment.app.FragmentActivity

const val LOG_TAG = "vincent_base"
fun FragmentActivity.logd(tag: String? = LOG_TAG, message: String) {
    Log.d(tag, message)
}
fun FragmentActivity.loge( message: String) {
    Log.e(LOG_TAG, message)
}

fun FragmentActivity.loge(tag: String, message: String) {
    Log.e(tag, message)
}

fun loge(tag:  String? = LOG_TAG, message: String) {
    Log.e(tag, message)
}

fun logd(tag: String? = LOG_TAG, message: String) {
    Log.d(tag, message)
}

fun logd(message: String) {
    Log.d(LOG_TAG, message)
}

