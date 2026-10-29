package com.redsurf.tv.sports

import java.text.Normalizer

/** Quality tier a provider tags onto a channel name. Only the first recognised token counts. */
enum class Quality { SD, HD, FHD, UHD }

/** Markers that make a channel a poor match target, so matching drops or demotes it. */
enum class ChannelFlag { BACKUP, TEST, TWENTY_FOUR_SEVEN }

/**
 * A provider channel name split into what matters for matching: the [key] (the network or
 * venue name only), the country prefix (`US` in `US| ESPN`), the quality tier, and the flags.
 */
data class ChannelName(
    val key: String,
    val country: String?,
    val quality: Quality?,
    val flags: Set<ChannelFlag>,
)

/**
 * Normalises raw IPTV channel names for matching against ESPN's network names, per
 * `docs/plans/SPORTS_HUB.md` §3. Pure and deterministic: the same raw name always yields the same key.
 *
 * Example: `US| NBC SPORTS PHILADELPHIA ᴴᴰ` → key `nbc sports philadelphia`, country `US`, quality HD.
 */
object ChannelNameNormalizer {

    // `US| ESPN`, or the dash form a real provider uses (`US - FOX 29 ...`, `LAT - TELEMUNDO`). The dash
    // form takes 2 letters or `lat` only: a 3-letter word before " - " is as likely a network (`ABC - ...`).
    private val countryPrefix = Regex("""^\s*(?:([a-z]{2,3})\s*\||([a-z]{2}|lat)\s+-\s)""")
    private val qualityTokens = mapOf(
        "sd" to Quality.SD,
        "hd" to Quality.HD,
        "fhd" to Quality.FHD,
        "uhd" to Quality.UHD,
        "4k" to Quality.UHD,
        "720p" to Quality.HD,
        "1080p" to Quality.FHD,
        "2160p" to Quality.UHD,
    )
    private val ignoredTokens = setOf("hevc", "h265", "h264", "50fps", "60fps")
    private val flagTokens = mapOf(
        "backup" to ChannelFlag.BACKUP,
        "bkp" to ChannelFlag.BACKUP,
        "test" to ChannelFlag.TEST,
        "24/7" to ChannelFlag.TWENTY_FOUR_SEVEN,
        "247" to ChannelFlag.TWENTY_FOUR_SEVEN,
    )

    fun normalize(raw: String): ChannelName {
        // NFKC folds superscript and fullwidth letters (ᴴᴰ -> HD, ＨＤ -> HD) before any token check.
        var text = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase()

        val prefix = countryPrefix.find(text)
        val country = prefix?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.uppercase()
        if (prefix != null) text = text.substring(prefix.range.last + 1)

        text = text.replace('[', ' ').replace(']', ' ').replace('(', ' ').replace(')', ' ')
        text = text.replace("&", " and ")
        text = text.replace("24/7", " 247 ")
        text = text.replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

        var quality: Quality? = null
        val flags = mutableSetOf<ChannelFlag>()
        val kept = mutableListOf<String>()
        for (token in text.split(' ').filter { it.isNotEmpty() }) {
            val q = qualityTokens[token]
            val f = flagTokens[token]
            when {
                q != null -> if (quality == null) quality = q
                f != null -> flags += f
                token in ignoredTokens -> Unit
                else -> kept += token
            }
        }

        return ChannelName(
            key = kept.joinToString(" "),
            country = country,
            quality = quality,
            flags = flags,
        )
    }
}

/**
 * Seed alias table of national networks, keyed by canonical name. Each alias is the normalised
 * form of a name ESPN or a provider uses. Extend this list as S0's on-device measurement shows
 * gaps; do not guess new aliases without a real channel name to check them against.
 */
object NationalNetworks {

    private val aliases: Map<String, List<String>> = mapOf(
        "espn" to listOf("espn"),
        "espn2" to listOf("espn2", "espn 2"),
        // "espn+" normalises to the bare key "espn" (the plus is punctuation), so only "espn plus" is listed.
        "espn+" to listOf("espn plus"),
        "abc" to listOf("abc"),
        "fox" to listOf("fox"),
        "cbs" to listOf("cbs"),
        "nbc" to listOf("nbc"),
        "tnt" to listOf("tnt"),
        "tbs" to listOf("tbs"),
        "peacock" to listOf("peacock"),
        "prime video" to listOf("prime video", "amazon prime video"),
        "nfl network" to listOf("nfl network", "nfl net"),
        "nfl redzone" to listOf("nfl redzone", "redzone"),
        "nhl network" to listOf("nhl network"),
        "mlb network" to listOf("mlb network"),
        "ufc" to listOf("ufc"),
    )

    private val canonicalByAlias: Map<String, String> = buildMap {
        for ((canonical, names) in aliases) for (name in names) put(ChannelNameNormalizer.normalize(name).key, canonical)
    }

    /**
     * The canonical national network a provider-or-ESPN name refers to, or null if it is not a
     * known national network. Both sides go through the same normaliser, so `NFL Net` matches
     * a provider channel named `US| NFL NET ᴴᴰ`.
     */
    fun canonicalFor(name: String): String? = canonicalByAlias[ChannelNameNormalizer.normalize(name).key]
}

/** Whether a market channel is a regional sports network or a local broadcast affiliate. */
enum class MarketKind { RSN, AFFILIATE }

/** A home-market channel: its canonical key, what kind it is, and the national network it carries (affiliates only). */
data class MarketChannel(val canonical: String, val kind: MarketKind, val network: String? = null)

/**
 * Home-market RSNs and affiliates (`SPORTS_HUB.md` §3a). Philadelphia's aliases were checked
 * against the test provider's real live names, 2026-10-29 (e.g. `US - FOX 29 PHILADELPHIA PA (WTXF) HD`,
 * `US - NBC SPORTS PHILADELPHIA PLUS HD`); check any new market the same way before adding it.
 *
 * Matching is by contiguous tokens, not the whole key, because provider names carry the city, state
 * and call sign around the network. A bare "fox 29" / "cbs 3" / "nbc 10" is never an alias: other
 * markets reuse those numbers (`US - FOX 29 BUFFALO NY (WUTV)`). The call sign or number + city is.
 */
object MarketNetworks {

    private val markets: Map<String, Map<MarketChannel, List<String>>> = mapOf(
        "philadelphia" to mapOf(
            MarketChannel("nbc sports philadelphia", MarketKind.RSN) to
                listOf("nbc sports philadelphia", "nbc sports phil", "nbcsp"),
            MarketChannel("nbc sports philadelphia plus", MarketKind.RSN) to
                listOf("nbc sports philadelphia plus", "nbcsp plus"),
            MarketChannel("wtxf", MarketKind.AFFILIATE, "fox") to listOf("fox 29 philadelphia", "wtxf"),
            MarketChannel("kyw", MarketKind.AFFILIATE, "cbs") to listOf("cbs 3 philadelphia", "kyw"),
            MarketChannel("wcau", MarketKind.AFFILIATE, "nbc") to listOf("nbc 10 philadelphia", "wcau"),
            MarketChannel("wpvi", MarketKind.AFFILIATE, "abc") to
                listOf("6abc", "abc 6 philadelphia", "abc 06 philadelphia", "wpvi"),
        ),
    )

    // Per market: (alias tokens, channel), longest alias first so `... PHILADELPHIA PLUS` beats the
    // plain RSN it contains.
    private val aliasTokens: Map<String, List<Pair<List<String>, MarketChannel>>> = markets.mapValues { (_, channels) ->
        channels.flatMap { (channel, names) ->
            names.map { ChannelNameNormalizer.normalize(it).key.split(' ') to channel }
        }.sortedByDescending { it.first.size }
    }

    /** The [market] channel a provider-or-ESPN name refers to, or null. */
    fun channelFor(name: String, market: String): MarketChannel? {
        val tokens = ChannelNameNormalizer.normalize(name).key.split(' ')
        return aliasTokens[market]?.firstOrNull { (alias, _) -> containsRun(tokens, alias) }?.second
    }

    private fun containsRun(tokens: List<String>, run: List<String>): Boolean =
        run.size <= tokens.size && (0..tokens.size - run.size).any { i -> tokens.subList(i, i + run.size) == run }

    /** The [market]'s affiliate for a national broadcast network ("fox" -> wtxf in Philadelphia), or null. */
    fun affiliateFor(network: String, market: String): MarketChannel? =
        markets[market]?.keys?.firstOrNull { it.kind == MarketKind.AFFILIATE && it.network == network }
}
