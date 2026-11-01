package com.linguascreen.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguascreen.data.db.HistoryEntity
import com.linguascreen.data.repo.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repo: HistoryRepository) : ViewModel() {
    val items: StateFlow<List<HistoryEntity>> =
        repo.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
    fun clear() { viewModelScope.launch { repo.clear() } }
}
