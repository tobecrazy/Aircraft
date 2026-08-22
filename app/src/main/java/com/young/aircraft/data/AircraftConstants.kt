package com.young.aircraft.data

/**
 * Create by young on 05/01/2026
 **/
object AircraftConstants {

    object IntentExtras {
        const val START_LEVEL = "start_level"
        const val JET_PLANE_RES = "jet_plane_res"
        const val JET_PLANE_INDEX = "jet_plane_index"
        const val TOTAL_KILLS = "total_kills"
        const val PUZZLE_SCORE = "puzzle_score"
        const val PUZZLE_LEVEL = "puzzle_level"
        const val GAME_MODE = "game_mode"
    }

    object Urls {
        const val PEAPIX_BING_CN_FEED = "https://peapix.com/bing/feed?country=cn"
        const val PROFILE_IMAGE = "https://images.cnblogs.com/cnblogs_com/tobecrazy/432338/o_250810143405_Card.png"
        const val EXAMPLE_IMAGE_PNG = "https://images.cnblogs.com/cnblogs_com/tobecrazy/2504287/o_260505005950_ChatGPT%20Image%20May%204,%202026,%2009_28_05%20PM.png"
        const val EXAMPLE_IMAGE_GIF = "https://images.cnblogs.com/cnblogs_com/tobecrazy/2505855/o_260513081240_789.gif"
        const val EXAMPLE_IMAGE_SVG = "https://github.com/tobecrazy/Aircraft/blob/main/class_diagram.svg?raw=1"
        const val EXAMPLE_LINK = "https://www.cnblogs.com/tobecrazy"
        const val CONTACT_US_QR_CODE = "https://images.cnblogs.com/cnblogs_com/tobecrazy/432338/o_250810143315_qrcode_123.jpg"
        private val IMAGE_URL_PATTERN = Regex("\"imageUrl\"\\s*:\\s*\"([^\"]+)\"")
        private val THUMB_URL_PATTERN = Regex("\"thumbUrl\"\\s*:\\s*\"([^\"]+)\"")
        private val FULL_URL_PATTERN = Regex("\"fullUrl\"\\s*:\\s*\"([^\"]+)\"")
        private val FEED_ITEM_PATTERN = Regex("\\{[^{}]*(?:\"thumbUrl\"|\"imageUrl\"|\"fullUrl\")[^{}]*\\}")

        fun extractPuzzleImageUrlsFromPeapixFeed(feedJson: String): List<String> {
            if (feedJson.isBlank()) return emptyList()
            return IMAGE_URL_PATTERN.findAll(feedJson)
                .mapNotNull { matchResult -> matchResult.groupValues.getOrNull(1)?.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }

        fun extractLatestPuzzleImageUrlFromPeapixFeed(feedJson: String): String? {
            return extractPuzzleImageUrlsFromPeapixFeed(feedJson).firstOrNull()
        }

        /**
         * Returns candidate image URLs for the latest entry in priority order:
         * thumbUrl (~150 KB, fastest), imageUrl, fullUrl. Used as fallbacks when
         * one URL fails to download (e.g. timeout on a 1920px JPG).
         */
        fun extractLatestPuzzleImageCandidatesFromPeapixFeed(feedJson: String): List<String> {
            if (feedJson.isBlank()) return emptyList()
            val thumb = THUMB_URL_PATTERN.find(feedJson)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val image = IMAGE_URL_PATTERN.find(feedJson)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val full = FULL_URL_PATTERN.find(feedJson)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return listOf(thumb, image, full).filter { it.isNotEmpty() }.distinct()
        }

        fun extractPuzzleImageCandidateGroupsFromPeapixFeed(feedJson: String): List<List<String>> {
            if (feedJson.isBlank()) return emptyList()
            val groups = FEED_ITEM_PATTERN.findAll(feedJson)
                .map { matchResult ->
                    val itemJson = matchResult.value
                    val thumb = THUMB_URL_PATTERN.find(itemJson)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    val image = IMAGE_URL_PATTERN.find(itemJson)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    val full = FULL_URL_PATTERN.find(itemJson)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    listOf(thumb, image, full).filter { it.isNotEmpty() }.distinct()
                }
                .filter { it.isNotEmpty() }
                .toList()

            return groups.ifEmpty {
                extractPuzzleImageUrlsFromPeapixFeed(feedJson).map { listOf(it) }
            }
        }
    }

    object PrivacyPolicy {
        const val ASSET_ZH = "privacy_policy.html"
        const val ASSET_EN = "privacy_policy_en.html"
        const val ASSET_PREFIX = "file:///android_asset/"
        const val LANG_ZH = "zh"
    }

    object HudLabels {
        const val MISSION = "MISSION"
        const val TIME = "TIME"
        const val HULL = "HULL"
        const val BOSS = "BOSS"
    }

    object HudColors {
        const val PANEL_DARK = "#260D1A17"
        const val PANEL_TIMER = "#2612201D"
        const val PANEL_BOSS = "#26101814"
        const val STROKE_GREEN = "#4D00FF88"
        const val LABEL_GREEN = "#8C8FFFC0"
        const val VALUE_LIGHT = "#D8F7E9"
        const val EMPHASIS_GREEN = "#9BFFCB"
        const val PROGRESS_BAR_BG = "#26FFFFFF"
        const val HEALTH_LOW = "#1BD772"
        const val HEALTH_MID = "#38EC8B"
        const val HEALTH_HIGH = "#7DFFBB"
        const val TIME_CRITICAL = "#4BFF9E"
        const val TIME_WARNING = "#7DFFBB"
        const val TIME_NORMAL = "#D9FFEC"
        const val HULL_LOW = "#59FFAB"
        const val HULL_MID = "#8DFFC6"
        const val SCORE_VALUE = "#D8F7E9"
        const val BOSS_BAR = "#FF5252"
    }
}
