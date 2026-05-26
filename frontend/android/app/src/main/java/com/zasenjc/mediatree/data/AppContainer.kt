package com.zasenjc.mediatree.data

import android.content.Context

class AppContainer(context: Context) {
    val sessionStore = SessionStore(context)
    val api = MediaTreeApi(sessionStore)
}
