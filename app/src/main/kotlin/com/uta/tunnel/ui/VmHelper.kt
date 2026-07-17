package com.uta.tunnel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uta.tunnel.AppContainer
import com.uta.tunnel.TunnelApp

/** [AppContainer] を注入して ViewModel を生成する簡易ヘルパー。 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as TunnelApp).container
    return viewModel(
        factory = viewModelFactory { initializer { create(container) } },
    )
}
