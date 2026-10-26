package io.github.touya0.nimbe.data.jellyfin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinPlaybackTest {

    private val server = "http://192.168.1.182:8097"

    @Test
    fun `lecture directe du fichier, avec sous-titre externe`() {
        val response = JSONObject(
            """
            {"PlaySessionId":"ps1","MediaSources":[{"Id":"ms1","SupportsDirectPlay":true,"SupportsDirectStream":true,
              "MediaStreams":[{"Type":"Subtitle","DeliveryMethod":"External","Codec":"srt","Language":"fre","DisplayTitle":"Français",
                "DeliveryUrl":"/Videos/m/ms1/Subtitles/2/0/Stream.srt?api_key=SECRET"}]}]}
            """,
        )
        val source = JellyfinPlayback.source(response, server, "m")
        assertEquals(PlayMethod.DirectPlay, source.method)
        assertEquals("$server/Videos/m/stream?static=true&mediaSourceId=ms1&playSessionId=ps1", source.url)
        assertFalse(source.isHls)
        assertEquals("$server/Videos/m/ms1/Subtitles/2/0/Stream.srt", source.subtitles.single().url)
        assertEquals("application/x-subrip", source.subtitles.single().mimeType)
    }

    @Test
    fun `transcodage HLS, sans le jeton dans l'adresse`() {
        val response = JSONObject(
            """
            {"PlaySessionId":"ps2","MediaSources":[{"Id":"ms2","SupportsDirectPlay":false,"SupportsDirectStream":true,
              "TranscodingSubProtocol":"hls",
              "TranscodingUrl":"/videos/m/master.m3u8?DeviceId=abc&MediaSourceId=ms2&VideoCodec=h264&ApiKey=SECRET&PlaySessionId=ps2"}]}
            """,
        )
        val source = JellyfinPlayback.source(response, server, "m")
        assertEquals(PlayMethod.Transcode, source.method)
        assertTrue(source.isHls)
        assertEquals("$server/videos/m/master.m3u8?DeviceId=abc&MediaSourceId=ms2&VideoCodec=h264&PlaySessionId=ps2", source.url)
        assertFalse(source.url.contains("SECRET"))
    }

    @Test
    fun `retrait du jeton dans toutes ses écritures`() {
        assertEquals("/a?b=1", JellyfinPlayback.withoutToken("/a?api_key=x&b=1"))
        assertEquals("/a", JellyfinPlayback.withoutToken("/a?ApiKey=x"))
        assertEquals("/a", JellyfinPlayback.withoutToken("/a"))
    }

    @Test(expected = JellyfinException::class)
    fun `aucune source lisible`() {
        JellyfinPlayback.source(JSONObject("""{"MediaSources":[{"Id":"x","SupportsDirectPlay":false,"SupportsDirectStream":false}]}"""), server, "m")
    }

    @Test
    fun `profil d'appareil, codecs annoncés, repli HLS H264`() {
        val profile = JellyfinPlayback.deviceProfile(DeviceCapabilities(setOf("hevc", "h264"), setOf("aac", "ac3")))
        val direct = profile.getJSONArray("DirectPlayProfiles").getJSONObject(0)
        assertEquals("h264,hevc", direct.getString("VideoCodec"))
        assertEquals("aac,ac3", direct.getString("AudioCodec"))
        val transcoding = profile.getJSONArray("TranscodingProfiles").getJSONObject(0)
        assertEquals("hls", transcoding.getString("Protocol"))
        assertEquals("h264", transcoding.getString("VideoCodec"))
    }
}
