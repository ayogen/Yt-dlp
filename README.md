# Video Downloader (Android)

A native Android video and audio downloader powered by Kotlin, Jetpack Compose, Room Database, and yt-dlp.

## Features

- **Media Extraction & Inspection**: Analyze single videos, playlists, audio streams, and carousels from supported providers.
- **Download Queue Management**: Download with background service notification support, live progress, speed tracking, pause/resume, and cancellation.
- **Quality & Format Customization**: Configurable profiles for video resolution, audio bitrates (MP3, M4A, FLAC, OPUS), subtitle embedding, and thumbnail art.
- **Local Persistence**: Powered by Room Database to preserve download records, history logs, and custom profiles.
- **Modern Jetpack Compose UI**: Material Design 3 design system with dynamic theming, edge-to-edge support, and responsive layouts.

## Tech Stack

- Kotlin & Jetpack Compose (Material 3)
- Room Database (with KSP)
- Coroutines & Flow
- OkHttp & Moshi
- yt-dlp & FFmpeg Android integration
