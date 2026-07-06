package com.classkey.modernattendance

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.classkey.modernattendance.data.Repo
import com.classkey.modernattendance.data.User

enum class Stage { SPLASH, LOGIN, SIGNUP, PROFILE_SETUP, BIOMETRIC_SETUP, HOME }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)

    var user by mutableStateOf<User?>(repo.sessionUser())
    var stage by mutableStateOf(Stage.SPLASH)
    var message by mutableStateOf<String?>(null)

    /** Incremented after any data mutation so screens re-query the local database. */
    var reload by mutableIntStateOf(0)
        private set

    fun bump() {
        reload++
    }

    fun say(text: String) {
        message = text
    }
}
