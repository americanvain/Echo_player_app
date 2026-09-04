package com.echoplayer.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoplayer.app.EchoApp

/** 所有 ViewModel 都只依赖 EchoApp 容器，用一个通用工厂省掉 DI 框架。 */
@Composable
inline fun <reified VM : ViewModel> echoViewModel(key: String? = null, crossinline create: (EchoApp) -> VM): VM {
    val app = LocalContext.current.applicationContext as EchoApp
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create(app) as T
        }
    })
}
