package com.phonecontroller.models

import kotlinx.serialization.Serializable

@Serializable
data class CommandResponse(
    val id: String,
    val ok: Boolean,
    val message: String? = null,
    val error: String? = null,
    val nodes: List<UiNode>? = null
)
