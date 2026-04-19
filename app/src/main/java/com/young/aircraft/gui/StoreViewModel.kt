package com.young.aircraft.gui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class StoreViewModel : ViewModel() {

    private val _items = MutableStateFlow(getMockStoreItems())
    val items: StateFlow<List<StoreItem>> = _items.asStateFlow()

    private val _credits = MutableStateFlow(5000) // Mocked initial credits
    val credits: StateFlow<Int> = _credits.asStateFlow()

    fun buyItem(item: StoreItem) {
        if (!item.isOwned && _credits.value >= item.price) {
            _credits.update { it - item.price }
            _items.update { currentItems ->
                currentItems.map {
                    if (it.id == item.id) it.copy(isOwned = true) else it
                }
            }
        }
    }
}
