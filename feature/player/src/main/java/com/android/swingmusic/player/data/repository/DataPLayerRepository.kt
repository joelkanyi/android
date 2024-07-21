package com.android.swingmusic.player.data.repository

import com.android.swingmusic.auth.data.baseurlholder.BaseUrlHolder
import com.android.swingmusic.auth.data.tokenholder.AuthTokenHolder
import com.android.swingmusic.auth.domain.repository.AuthRepository
import com.android.swingmusic.core.domain.model.Track
import com.android.swingmusic.database.data.dao.LastPlayedTrackDao
import com.android.swingmusic.database.data.dao.QueueDao
import com.android.swingmusic.database.data.mapper.toEntity
import com.android.swingmusic.database.data.mapper.toModel
import com.android.swingmusic.database.domain.model.LastPlayedTrack
import com.android.swingmusic.network.data.api.service.NetworkApiService
import com.android.swingmusic.network.data.mapper.toDto
import com.android.swingmusic.network.domain.model.LogTrackRequest
import com.android.swingmusic.player.domain.repository.PLayerRepository
import retrofit2.HttpException
import timber.log.Timber
import java.sql.Timestamp
import java.time.Instant
import javax.inject.Inject

class DataPLayerRepository @Inject constructor(
    private val queueDao: QueueDao,
    private val lastPlayedTrackDao: LastPlayedTrackDao,
    private val networkApiService: NetworkApiService,
    private val authRepository: AuthRepository
) : PLayerRepository {

    override suspend fun insertQueue(track: List<Track>) {
        val trackEntities = track.map { it.toEntity() }
        queueDao.insertQueueInTransaction(trackEntities)
    }

    override suspend fun getSavedQueue(): List<Track> {
        return queueDao.getSavedQueue().map { it.toModel() }
    }

    override suspend fun clearQueue() {
        queueDao.clearQueue()
    }

    override suspend fun updateLastPlayedTrack(
        trackHash: String,
        indexInQueue: Int,
        lastPlayPositionMs: Long
    ) {
        lastPlayedTrackDao.insertLastPlayedTrack(
            LastPlayedTrack(
                trackHash = trackHash,
                indexInQueue = indexInQueue,
                lastPlayPositionMs = lastPlayPositionMs
            ).toEntity()
        )
    }

    override suspend fun getLastPlayedTrack(): LastPlayedTrack? {
        return lastPlayedTrackDao.getLastPlayedTrack()?.toModel()
    }

    override suspend fun logLastPlayedTrackToServer(
        track: Track,
        playDuration: Int,
        source: String
    ) {
        try {
            val accessToken = AuthTokenHolder.accessToken ?: authRepository.getAccessToken()
            val baseUrl = BaseUrlHolder.baseUrl ?: authRepository.getBaseUrl()

            val timeStamp = Timestamp.from(Instant.now()).toInstant().epochSecond
            val logRequest = LogTrackRequest(
                trackHash = track.trackHash,
                duration = playDuration,
                timestamp = timeStamp,
                source = source
            ).toDto()

            networkApiService.logLastPlayedTrackToServer(
                logTrackRequest = logRequest,
                baseUrl = "${baseUrl}logger/track/log",
                bearerToken = "Bearer $accessToken"
            )

        } catch (e: HttpException) {
            Timber.e("NETWORK ERROR LOGGING TRACK TO SERVER")
        } catch (e: Exception) {
            Timber.e("ERROR LOGGING TRACK TO SERVER: CAUSED BY -> ${e.message}")
        }
    }
}