package com.ch.fds.core.port

import com.ch.fds.core.model.TransactionFeatures

interface ModelInferencePort {
    
    val modelName: String
    val modelVersion: String
    val isModelLoaded: Boolean
    
    suspend fun predictScore(
        features: TransactionFeatures
    ): Float
    
    suspend fun reloadModel(
        modelPath: String
    )
}

