package com.android.swingmusic.artist.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.swingmusic.artist.domain.repository.ArtistRepository
import com.android.swingmusic.artist.presentation.event.ArtistInfoUiEvent
import com.android.swingmusic.artist.presentation.state.ArtistInfoState
import com.android.swingmusic.core.data.util.Resource
import com.android.swingmusic.core.domain.model.AlbumsAndAppearances
import com.android.swingmusic.core.domain.model.ArtistExpanded
import com.android.swingmusic.core.domain.model.ArtistInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArtistInfoViewModel @Inject constructor(
    private val artistRepository: ArtistRepository
) : ViewModel() {
    private val _artistInfoState: MutableStateFlow<ArtistInfoState> =
        MutableStateFlow(ArtistInfoState())
    val artistInfoState: StateFlow<ArtistInfoState> = _artistInfoState

    private fun getArtistInfo(artistHash: String) {
        viewModelScope.launch {
            artistRepository.getArtistInfo(artistHash).collectLatest { res ->
                _artistInfoState.value = _artistInfoState.value.copy(
                    infoResource = res
                )

                if (res is Resource.Success) {
                    _artistInfoState.value = _artistInfoState.value.copy(
                        requiresReload = false
                    )
                }
            }
        }
    }

    private fun getSimilarArtists(artistHash: String) {
        viewModelScope.launch {
            artistRepository.getSimilarArtists(artistHash).collectLatest { res ->
                _artistInfoState.value = _artistInfoState.value.copy(
                    similarArtistsResource = res
                )
            }
        }
    }

    private fun updateCurrentArtistHash(hash: String) {
        val currentState = _artistInfoState.value
        val currentInfo = currentState.infoResource.data

        currentInfo?.let { info ->
            _artistInfoState.value = currentState.copy(
                infoResource = Resource.Success(
                    data = info.copy(
                        artist = info.artist.copy(artistHash = hash),
                        albumsAndAppearances = info.albumsAndAppearances,
                        tracks = info.tracks
                    )
                )
            )
        }
    }

    private fun toggleArtistFavorite(artistHash: String, isFavorite: Boolean) {
        viewModelScope.launch {
            val emptyAlbumsAndAppearances = AlbumsAndAppearances(
                albums = emptyList(),
                appearances = emptyList(),
                artistName = "",
                compilations = emptyList(),
                singlesAndEps = emptyList()
            )
            val emptyArtistExpanded = ArtistExpanded(
                albumCount = 0,
                artistHash = "",
                color = "",
                duration = 0,
                genres = emptyList(),
                image = "",
                isFavorite = false,
                name = "",
                trackCount = 0
            )

            // Optimistically update the UI
            _artistInfoState.value = _artistInfoState.value.copy(
                infoResource = Resource.Success(
                    ArtistInfo(
                        albumsAndAppearances = _artistInfoState.value.infoResource.data?.albumsAndAppearances
                            ?: emptyAlbumsAndAppearances,
                        artist = _artistInfoState.value.infoResource.data?.artist?.copy(
                            isFavorite = !isFavorite
                        ) ?: emptyArtistExpanded,
                        tracks = _artistInfoState.value.infoResource.data?.tracks ?: emptyList()
                    )
                )
            )

            val request = if (isFavorite) {
                artistRepository.removeArtistFromFavorite(artistHash)
            } else {
                artistRepository.addArtistToFavorite(artistHash)
            }

            Timber.e("TOGGLE ARTIST FAVORITE TO :${!isFavorite}")

            request.collectLatest {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        _artistInfoState.value = _artistInfoState.value.copy(
                            infoResource = Resource.Success(
                                ArtistInfo(
                                    albumsAndAppearances = _artistInfoState.value.infoResource.data?.albumsAndAppearances
                                        ?: emptyAlbumsAndAppearances,
                                    artist = _artistInfoState.value.infoResource.data?.artist?.copy(
                                        isFavorite = it.data ?: false
                                    ) ?: emptyArtistExpanded,
                                    tracks = _artistInfoState.value.infoResource.data?.tracks
                                        ?: emptyList()
                                )
                            )
                        )
                    }

                    is Resource.Error -> {
                        _artistInfoState.value = _artistInfoState.value.copy(
                            infoResource = Resource.Success(
                                ArtistInfo(
                                    albumsAndAppearances = _artistInfoState.value.infoResource.data?.albumsAndAppearances
                                        ?: emptyAlbumsAndAppearances,
                                    artist = _artistInfoState.value.infoResource.data?.artist?.copy(
                                        isFavorite = isFavorite
                                    ) ?: emptyArtistExpanded,
                                    tracks = _artistInfoState.value.infoResource.data?.tracks
                                        ?: emptyList()
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    fun onArtistInfoUiEvent(event: ArtistInfoUiEvent) {
        when (event) {
            is ArtistInfoUiEvent.OnLoadArtistInfo -> {

                updateCurrentArtistHash(event.artistHash)

                getArtistInfo(event.artistHash)
                getSimilarArtists(event.artistHash)
            }

            is ArtistInfoUiEvent.OnToggleArtistFavorite -> {
                toggleArtistFavorite(event.artistHash, event.isFavorite)
            }

            is ArtistInfoUiEvent.OnRefresh -> {
                getArtistInfo(event.artistHash)
                getSimilarArtists(event.artistHash)
            }

            is ArtistInfoUiEvent.OnUpdateArtistHash -> {
                updateCurrentArtistHash(event.artistHash)

                getArtistInfo(event.artistHash)
                getSimilarArtists(event.artistHash)
            }

            else -> {

            }
        }
    }
}