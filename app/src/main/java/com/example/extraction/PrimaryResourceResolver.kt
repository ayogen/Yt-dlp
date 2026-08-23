package com.example.extraction

import com.example.core.model.CanonicalMediaResult
import com.example.core.model.CanonicalMetadata
import com.example.core.model.MediaCandidate
import com.example.core.model.ResolutionDecision
import com.example.core.policy.MediaRole
import com.example.data.model.MediaType

object PrimaryResourceResolver {

    /**
     * Resolves the primary resource deterministically from a list of candidates,
     * irrespective of candidate collection order.
     */
    fun resolve(
        candidates: List<MediaCandidate>,
        sourceUrl: String,
        canonicalUrl: String,
        platform: String = "generic",
        intent: String = "video"
    ): ResolutionDecision {
        if (candidates.isEmpty()) {
            return ResolutionDecision(
                primaryCandidate = null,
                thumbnailCandidate = null,
                rejectedCandidates = emptyList(),
                reason = "No candidates discovered"
            )
        }

        // Find candidate thumbnails (either explicit THUMBNAIL / POSTER / PREVIEW, or an image candidate)
        val thumbnailCandidate = candidates.firstOrNull { it.role == MediaRole.THUMBNAIL || it.role == MediaRole.POSTER }
            ?: candidates.firstOrNull { it.mediaType == MediaType.IMAGE && !it.role.isPrimary }

        val rejected = mutableListOf<MediaCandidate>()

        // 1. Explicit yt-dlp primary video/audio candidate
        val ytdlpPrimary = candidates.firstOrNull {
            it.source == com.example.core.model.CandidateSource.YTDLP && it.role.isPrimary
        }
        if (ytdlpPrimary != null) {
            rejected.addAll(candidates.filter { it.id != ytdlpPrimary.id && it.id != thumbnailCandidate?.id })
            return ResolutionDecision(
                primaryCandidate = ytdlpPrimary,
                thumbnailCandidate = thumbnailCandidate,
                rejectedCandidates = rejected,
                reason = "Selected primary candidate from yt-dlp"
            )
        }

        // 2. Explicit platform strategy candidate
        val platformPrimary = candidates.firstOrNull {
            it.source == com.example.core.model.CandidateSource.PLATFORM && it.role.isPrimary
        }
        if (platformPrimary != null) {
            rejected.addAll(candidates.filter { it.id != platformPrimary.id && it.id != thumbnailCandidate?.id })
            return ResolutionDecision(
                primaryCandidate = platformPrimary,
                thumbnailCandidate = thumbnailCandidate,
                rejectedCandidates = rejected,
                reason = "Selected primary candidate from platform strategy"
            )
        }

        // 3. Verified direct media candidate
        val directMediaPrimary = candidates.firstOrNull {
            it.source == com.example.core.model.CandidateSource.DIRECT_HTTP && it.role == MediaRole.DIRECT_MEDIA
        }
        if (directMediaPrimary != null) {
            rejected.addAll(candidates.filter { it.id != directMediaPrimary.id && it.id != thumbnailCandidate?.id })
            return ResolutionDecision(
                primaryCandidate = directMediaPrimary,
                thumbnailCandidate = thumbnailCandidate,
                rejectedCandidates = rejected,
                reason = "Selected verified direct media stream"
            )
        }

        // 4. Embedded / OpenGraph video or audio candidate
        val embeddedMediaPrimary = candidates.firstOrNull {
            (it.source == com.example.core.model.CandidateSource.EMBEDDED || it.source == com.example.core.model.CandidateSource.OPENGRAPH) &&
            it.role.isPrimary && (it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.AUDIO)
        }
        if (embeddedMediaPrimary != null) {
            rejected.addAll(candidates.filter { it.id != embeddedMediaPrimary.id && it.id != thumbnailCandidate?.id })
            return ResolutionDecision(
                primaryCandidate = embeddedMediaPrimary,
                thumbnailCandidate = thumbnailCandidate,
                rejectedCandidates = rejected,
                reason = "Selected embedded media stream"
            )
        }

        // 5. Image candidate ONLY if intent is explicitly image or direct media
        val isImageIntent = intent.equals("image", ignoreCase = true) || intent.equals("photo", ignoreCase = true)
        if (isImageIntent) {
            val imagePrimary = candidates.firstOrNull { it.mediaType == MediaType.IMAGE && it.isDownloadable }
            if (imagePrimary != null) {
                rejected.addAll(candidates.filter { it.id != imagePrimary.id && it.id != thumbnailCandidate?.id })
                return ResolutionDecision(
                    primaryCandidate = imagePrimary,
                    thumbnailCandidate = thumbnailCandidate,
                    rejectedCandidates = rejected,
                    reason = "Selected direct image candidate for explicit image intent"
                )
            }
        }

        // 6. Otherwise UNRESOLVED: For video/platform intents, NEVER promote a thumbnail into a primary video/image.
        val nonThumbnailImages = candidates.filter { it.mediaType == MediaType.IMAGE }
        rejected.addAll(candidates)

        return ResolutionDecision(
            primaryCandidate = null,
            thumbnailCandidate = thumbnailCandidate,
            rejectedCandidates = rejected,
            reason = if (nonThumbnailImages.isNotEmpty()) {
                "Known video platform or page extraction did not produce a playable video stream; thumbnail will not be downloaded as video."
            } else {
                "No primary media candidate could be resolved for intent: $intent"
            }
        )
    }

    fun toCanonicalMediaResult(
        decision: ResolutionDecision,
        sourceUrl: String,
        canonicalUrl: String,
        platform: String = "generic",
        intent: String = "video",
        fallbackMetadata: CanonicalMetadata = CanonicalMetadata()
    ): CanonicalMediaResult {
        val primary = decision.primaryCandidate
        val thumbnail = decision.thumbnailCandidate

        val mergedMeta = if (primary != null) {
            CanonicalMetadata(
                title = primary.title.ifBlank { fallbackMetadata.title },
                uploader = primary.uploader.ifBlank { fallbackMetadata.uploader },
                channel = fallbackMetadata.channel.ifBlank { primary.uploader },
                durationSeconds = if (primary.durationSeconds > 0) primary.durationSeconds else fallbackMetadata.durationSeconds,
                viewCount = fallbackMetadata.viewCount,
                likeCount = fallbackMetadata.likeCount,
                uploadDate = fallbackMetadata.uploadDate,
                description = primary.description.ifBlank { fallbackMetadata.description },
                thumbnail = thumbnail?.url ?: primary.thumbnail.ifBlank { fallbackMetadata.thumbnail },
                isPlaylist = fallbackMetadata.isPlaylist,
                playlistCount = fallbackMetadata.playlistCount,
                playlistEntries = fallbackMetadata.playlistEntries,
                subtitles = fallbackMetadata.subtitles,
                extractorName = fallbackMetadata.extractorName,
                directDownloadUrl = if (primary.role == MediaRole.DIRECT_MEDIA) primary.url else fallbackMetadata.directDownloadUrl
            )
        } else {
            fallbackMetadata.copy(
                thumbnail = thumbnail?.url ?: fallbackMetadata.thumbnail
            )
        }

        val warnings = if (!decision.isResolved) {
            listOf(decision.reason)
        } else emptyList()

        return CanonicalMediaResult(
            sourceUrl = sourceUrl,
            canonicalUrl = canonicalUrl,
            platform = platform,
            intent = intent,
            primary = primary,
            thumbnail = thumbnail,
            metadata = mergedMeta,
            formats = primary?.formats ?: emptyList(),
            warnings = warnings
        )
    }
}
