package com.zasenjc.mediatree.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

fun viewModelFactory(block: () -> ViewModel): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = block() as T
    }
}
