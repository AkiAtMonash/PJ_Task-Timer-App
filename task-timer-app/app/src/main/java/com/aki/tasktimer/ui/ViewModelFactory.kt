package com.aki.tasktimer.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.di.AppContainer

/**
 * ViewModel の中から AppContainer を取り出すための入口（docs/02_ARCHITECTURE.md 2 章：手動 DI）。
 *
 * Hilt を入れない代わりに、各 ViewModel の companion に
 *
 *     val Factory = viewModelFactory {
 *         initializer { HomeViewModel(container.sessionRepository) }
 *     }
 *
 * と書いて使う。Application は必ず先に onCreate が走っているので、
 * ここで container が未初期化になることはない。
 */
internal val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as TaskTimerApp).container
