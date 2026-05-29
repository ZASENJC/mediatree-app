package com.zasenjc.mediatree.data

import android.content.Context

class AppContainer(context: Context) {
    val sessionStore = SessionStore(context)
    val clientStorageStore = AndroidClientStorageStore(context)
    val clientStorageRepository = ClientStorageRepository(clientStorageStore)
    val webDavClient = WebDavClient()
    private val mediaTreeApi = MediaTreeApi(sessionStore)
    val mediaProvider: MediaProvider = MediaTreeProvider(mediaTreeApi)
}
