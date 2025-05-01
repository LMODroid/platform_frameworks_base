/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.media.remedia.data.repository

import android.media.session.MediaController
import android.media.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MediaRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val session = MediaSession(context, "MediaRepositoryTestSession")

    private val underTest: MediaRepositoryImpl = kosmos.mediaRepository

    @Before
    fun setUp() {
        underTest.activateIn(testScope)
    }

    @Test
    fun addCurrentUserMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val currentUserEntries by collectLastValue(underTest.currentUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia =
                MediaData()
                    .copy(token = session.sessionToken, active = true, instanceId = instanceId)

            underTest.addCurrentUserMediaEntry(userMedia)

            assertThat(currentUserEntries?.get(instanceId)).isEqualTo(userMedia)

            underTest.addCurrentUserMediaEntry(userMedia.copy(active = false))

            assertThat(currentUserEntries?.get(instanceId)).isNotEqualTo(userMedia)
            assertThat(currentUserEntries?.get(instanceId)?.active).isFalse()
        }

    @Test
    fun addCurrentUserMediaEntry_thenRemove_returnsBoolean() =
        testScope.runTest {
            val currentUserEntries by collectLastValue(underTest.currentUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia = MediaData().copy(token = session.sessionToken, instanceId = instanceId)

            underTest.addCurrentUserMediaEntry(userMedia)

            assertThat(currentUserEntries?.get(instanceId)).isEqualTo(userMedia)
            assertThat(underTest.removeCurrentUserMediaEntry(instanceId, userMedia)).isTrue()
        }

    @Test
    fun addCurrentUserMediaEntry_thenRemove_returnsValue() =
        testScope.runTest {
            val currentUserEntries by collectLastValue(underTest.currentUserEntries)

            val instanceId = InstanceId.fakeInstanceId(123)
            val userMedia = MediaData().copy(token = session.sessionToken, instanceId = instanceId)

            underTest.addCurrentUserMediaEntry(userMedia)

            assertThat(currentUserEntries?.get(instanceId)).isEqualTo(userMedia)

            assertThat(underTest.removeCurrentUserMediaEntry(instanceId)).isEqualTo(userMedia)
        }

    @Test
    fun addMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val allMediaEntries by collectLastValue(underTest.allMediaEntries)

            val userMedia = MediaData().copy(active = true)

            underTest.addMediaEntry(KEY, userMedia)

            assertThat(allMediaEntries?.get(KEY)).isEqualTo(userMedia)

            underTest.addMediaEntry(KEY, userMedia.copy(active = false))

            assertThat(allMediaEntries?.get(KEY)).isNotEqualTo(userMedia)
            assertThat(allMediaEntries?.get(KEY)?.active).isFalse()
        }

    @Test
    fun addMediaEntry_thenRemove_returnsValue() =
        testScope.runTest {
            val allMediaEntries by collectLastValue(underTest.allMediaEntries)

            val userMedia = MediaData()

            underTest.addMediaEntry(KEY, userMedia)

            assertThat(allMediaEntries?.get(KEY)).isEqualTo(userMedia)

            assertThat(underTest.removeMediaEntry(KEY)).isEqualTo(userMedia)
        }

    @Test
    fun addMediaControlPlayingThenRemote() =
        testScope.runTest {
            val playingInstanceId = InstanceId.fakeInstanceId(123)
            val remoteInstanceId = InstanceId.fakeInstanceId(321)
            val playingData = createMediaData("app1", true, LOCAL, false, playingInstanceId)
            val remoteData = createMediaData("app2", true, REMOTE, false, remoteInstanceId)

            underTest.addCurrentUserMediaEntry(playingData)
            underTest.addCurrentUserMediaEntry(remoteData)
            runCurrent()

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData.toDataModel(underTest.currentMedia[0].controller),
                    remoteData.toDataModel(underTest.currentMedia[1].controller),
                )
                .inOrder()
        }

    @Test
    fun switchMediaControlsPlaying() =
        testScope.runTest {
            val playingInstanceId1 = InstanceId.fakeInstanceId(123)
            val playingInstanceId2 = InstanceId.fakeInstanceId(321)
            var playingData1 = createMediaData("app1", true, LOCAL, false, playingInstanceId1)
            var playingData2 = createMediaData("app2", false, LOCAL, false, playingInstanceId2)

            underTest.addCurrentUserMediaEntry(playingData1)
            underTest.addCurrentUserMediaEntry(playingData2)
            runCurrent()

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData1.toDataModel(underTest.currentMedia[0].controller),
                    playingData2.toDataModel(underTest.currentMedia[1].controller),
                )
                .inOrder()

            playingData1 = createMediaData("app1", false, LOCAL, false, playingInstanceId1)
            playingData2 = createMediaData("app2", true, LOCAL, false, playingInstanceId2)

            underTest.addCurrentUserMediaEntry(playingData1)
            underTest.addCurrentUserMediaEntry(playingData2)
            runCurrent()

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData1.toDataModel(underTest.currentMedia[0].controller),
                    playingData2.toDataModel(underTest.currentMedia[1].controller),
                )
                .inOrder()

            underTest.reorderMedia()
            runCurrent()

            assertThat(underTest.currentMedia.size).isEqualTo(2)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingData2.toDataModel(underTest.currentMedia[0].controller),
                    playingData1.toDataModel(underTest.currentMedia[1].controller),
                )
                .inOrder()
        }

    @Test
    fun fullOrderTest() =
        testScope.runTest {
            val instanceId1 = InstanceId.fakeInstanceId(123)
            val instanceId2 = InstanceId.fakeInstanceId(456)
            val instanceId3 = InstanceId.fakeInstanceId(321)
            val instanceId4 = InstanceId.fakeInstanceId(654)
            val instanceId5 = InstanceId.fakeInstanceId(124)
            val playingAndLocalData = createMediaData("app1", true, LOCAL, false, instanceId1)
            val playingAndRemoteData = createMediaData("app2", true, REMOTE, false, instanceId2)
            val stoppedAndLocalData = createMediaData("app3", false, LOCAL, false, instanceId3)
            val stoppedAndRemoteData = createMediaData("app4", false, REMOTE, false, instanceId4)
            val canResumeData = createMediaData("app5", false, LOCAL, true, instanceId5)

            underTest.addCurrentUserMediaEntry(stoppedAndLocalData)

            underTest.addCurrentUserMediaEntry(stoppedAndRemoteData)

            underTest.addCurrentUserMediaEntry(canResumeData)

            underTest.addCurrentUserMediaEntry(playingAndLocalData)

            underTest.addCurrentUserMediaEntry(playingAndRemoteData)

            underTest.reorderMedia()
            runCurrent()

            assertThat(underTest.currentMedia.size).isEqualTo(5)
            assertThat(underTest.currentMedia)
                .containsExactly(
                    playingAndLocalData.toDataModel(underTest.currentMedia[0].controller),
                    playingAndRemoteData.toDataModel(underTest.currentMedia[1].controller),
                    stoppedAndRemoteData.toDataModel(underTest.currentMedia[2].controller),
                    stoppedAndLocalData.toDataModel(underTest.currentMedia[3].controller),
                    canResumeData.toDataModel(underTest.currentMedia[4].controller),
                )
                .inOrder()
        }

    private fun createMediaData(
        app: String,
        playing: Boolean,
        playbackLocation: Int,
        isResume: Boolean,
        instanceId: InstanceId,
    ): MediaData {
        return MediaData(
            token = session.sessionToken,
            playbackLocation = playbackLocation,
            resumption = isResume,
            notificationKey = "key: $app",
            isPlaying = playing,
            instanceId = instanceId,
        )
    }

    private fun MediaData.toDataModel(mediaController: MediaController): MediaDataModel {
        return MediaDataModel(
            instanceId = instanceId,
            appUid = appUid,
            packageName = packageName,
            appName = app.toString(),
            appIcon = null,
            background = null,
            title = song.toString(),
            subtitle = artist.toString(),
            notificationActions = actions,
            playbackStateActions = semanticActions,
            outputDevice = device,
            clickIntent = clickIntent,
            controller = mediaController,
            canBeDismissed = isClearable,
            isActive = active,
            isResume = resumption,
            resumeAction = resumeAction,
            isExplicit = isExplicit,
        )
    }

    companion object {
        private const val LOCAL = MediaData.PLAYBACK_LOCAL
        private const val REMOTE = MediaData.PLAYBACK_CAST_LOCAL
        private const val KEY = "KEY"
    }
}
