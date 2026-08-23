package com.example.ui.model

import com.example.core.model.CanonicalMediaResult
import com.example.data.model.MediaMetadata

object MediaUiMapper {

    fun mapToUiModel(canonical: CanonicalMediaResult): AnalysisUiModel {
        return AnalysisUiModel.fromCanonicalMediaResult(canonical)
    }

    fun mapToUiModel(metadata: MediaMetadata): AnalysisUiModel {
        return AnalysisUiModel.fromMediaMetadata(metadata)
    }
}
