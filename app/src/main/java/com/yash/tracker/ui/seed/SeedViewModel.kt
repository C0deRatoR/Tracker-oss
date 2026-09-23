package com.yash.tracker.ui.seed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.seed.SeedImporter
import com.yash.tracker.data.local.seed.SeedProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeedViewModel @Inject constructor(
    private val importer: SeedImporter,
) : ViewModel() {

    private val _state = MutableStateFlow<SeedProgress>(SeedProgress.Idle)
    val state: StateFlow<SeedProgress> = _state.asStateFlow()

    init {
        start()
    }

    fun start() {
        viewModelScope.launch {
            _state.value = importer.import { _state.value = it }
        }
    }
}
