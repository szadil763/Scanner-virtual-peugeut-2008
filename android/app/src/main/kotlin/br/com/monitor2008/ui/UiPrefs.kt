package br.com.monitor2008.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Preferências que mudam a tela inteira na hora (tema "sol forte"). */
object UiPrefs {
    var highContrast by mutableStateOf(false)
}
