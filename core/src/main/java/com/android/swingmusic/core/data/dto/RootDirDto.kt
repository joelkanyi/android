package com.android.swingmusic.core.data.dto


import com.google.gson.annotations.SerializedName

data class RootDirDto(
    @SerializedName("name")
    val name: String?,
    @SerializedName("path")
    val path: String?
)
