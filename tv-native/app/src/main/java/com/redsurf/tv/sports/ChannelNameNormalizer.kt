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

    private val countryPrefix = Regex("""^\s*([a-z]{2,3})\s*\|""")
    private val qualityTokens = mapOf(
        "sd" to Quality.SD,
        "hd" to Quality.HD,
        "fhd" to Quality.FHD,
        "uhd" to Quality.UHD,
        "4k" to Quality.UHD,
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

        val country = countryPrefix.find(text)?.groupValues?.get(1)?.uppercase()
        if (country != null) text = text.substring(countryPrefix.find(text)!!.range.last + 1)

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
 * Home-market RSNs and affiliates (`SPORTS_HUB.md` §3a). Seeded with only the Philadelphia
 * spellings the brief itself records; check each against the user's real channel names before
 * adding more (same rule as [NationalNetworks]).
 */
object MarketNetworks {

    private val markets: Map<String, Map<MarketChannel, List<String>>> = mapOf(
        "philadelphia" to mapOf(
            MarketChannel("nbc sports philadelphia", MarketKind.RSN) to
                listOf("nbc sports philadelphia", "nbc sports phil", "nbcsp"),
            MarketChannel("wtxf", MarketKind.AFFILIATE, "fox") to listOf("fox 29", "wtxf"),
            MarketChannel("kyw", MarketKind.AFFILIATE, "cbs") to listOf("cbs 3", "kyw"),
            MarketChannel("wcau", MarketKind.AFFILIATE, "nbc") to listOf("nbc 10", "wcau"),
            MarketChannel("wpvi", MarketKind.AFFILIATE, "abc") to listOf("6abc", "wpvi"),
        ),
    )

    private val byAlias: Map<String, Map<String, MarketChannel>> = markets.mapValues { (_, channels) ->
        buildMap {
            for ((channel, names) in channels) for (name in names) put(ChannelNameNormalizer.normalize(name).key, channel)
        }
    }

    /** The [market] channel a provider-or-ESPN name refers to, or null. */
    fun channelFor(name: String, market: String): MarketChannel? =
        byAlias[market]?.get(ChannelNameNormalizer.normalize(name).key)

    /** The [market]'s affiliate for a national broadcast network ("fox" -> wtxf in Philadelphia), or null. */
    fun affiliateFor(network: String, market: String): MarketChannel? =
        markets[market]?.keys?.firstOrNull { it.kind == MarketKind.AFFILIATE && it.network == network }
}
