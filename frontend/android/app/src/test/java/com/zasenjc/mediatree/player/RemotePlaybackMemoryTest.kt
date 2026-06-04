package com.zasenjc.mediatree.player

import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.remotePlaybackMemoryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RemotePlaybackMemoryTest {
    @Test
    fun remotePlaybackMemoryKeySeparatesProvidersAndProfilesForTheSameMovieId() {
        val mediaTree = remotePlaybackMemoryKey(ProviderType.MediaTree, "mediatree-default", 42)
        val jellyfin = remotePlaybackMemoryKey(ProviderType.Jellyfin, "jellyfin-home", 42)
        val emby = remotePlaybackMemoryKey(ProviderType.Emby, "emby-home", 42)
        val secondJellyfin = remotePlaybackMemoryKey(ProviderType.Jellyfin, "jellyfin-lan", 42)

        assertEquals("remote:MediaTree:mediatree-default" to "42", mediaTree)
        assertEquals("remote:Jellyfin:jellyfin-home" to "42", jellyfin)
        assertEquals("remote:Emby:emby-home" to "42", emby)
        assertNotEquals(mediaTree.first, jellyfin.first)
        assertNotEquals(jellyfin.first, emby.first)
        assertNotEquals(jellyfin.first, secondJellyfin.first)
    }
}
