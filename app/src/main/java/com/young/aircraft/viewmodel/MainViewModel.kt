package com.young.aircraft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

/**
 * Create by Young
 **/
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    var isReadToPlaySound = MutableLiveData<Boolean>()

    init {
        //by default is false
        isReadToPlaySound.value = false
    }

    fun updateSoundServiceStatus(status: Boolean) {
        isReadToPlaySound.postValue(status)
    }
}