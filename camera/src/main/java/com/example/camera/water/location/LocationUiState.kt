package com.example.camera.water.location

import androidx.annotation.Keep

/**
 * 定位组件返回给 ViewModel 的结果。
 */
sealed interface LocationUiState {
    @Keep
    data class Success(val locationText: String) : LocationUiState

    data object PermissionDenied : LocationUiState
    data object ProviderDisabled : LocationUiState
    data object Unavailable : LocationUiState

    @Keep
    data class Failure(val message: String) : LocationUiState
}
