package com.vincent.android.myled.utils

import android.util.Log
import androidx.fragment.app.FragmentActivity

fun FragmentActivity.logd(message:String){

    Log.d(LOG_TAG,message)
}


fun FragmentActivity.loge(message:String){

    Log.e(LOG_TAG,message)
}

fun loge(tag:String,text: String) {
    Log.e(tag, text)
}
fun logd(tag:String,text: String) {
    Log.d(tag, text)
}
fun logd(text: String) {
    Log.d(LOG_TAG, text)
}

