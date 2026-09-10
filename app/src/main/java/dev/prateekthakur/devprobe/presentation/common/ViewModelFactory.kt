package dev.prateekthakur.devprobe.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.Composable

/** Small helper to build a single-ViewModel factory from AppContainer without a DI framework. */
inline fun <reified VM : ViewModel> simpleViewModelFactory(crossinline create: () -> VM) =
    viewModelFactory { initializer { create() } }

@Composable
inline fun <reified VM : ViewModel> rememberContainerViewModel(crossinline create: () -> VM): VM {
    return viewModel(factory = simpleViewModelFactory(create))
}
