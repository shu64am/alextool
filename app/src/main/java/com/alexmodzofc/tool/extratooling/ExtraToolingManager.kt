package com.alexmodzofc.tool.extratooling

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern

/**
 * "Extra Tooling" module ported from the user's Alextrick Toolkit:
 *  1. **Domain blocker** — blocks resource/main-frame requests to configured domains
 *     (link shorteners, ad domains) inside the WebView via
 *     [com.alexmodzofc.tool.browser.webview.ClintWebViewClient].
 *  2. **Link Toolkit** — extracts Original Link / Bypassed Link pairs from pasted
 *     Telegram/WhatsApp bot messages and produces share-safe
 *     "alextool://links?alextrick=..." style URLs (see [buildToolingUrl]).
 *
 * Domain blocker state is stored in SharedPreferences and read per-request by the
 * WebViewClient; user scripts live in their own [UserScriptStore] object.
 */
object ExtraToolingManager {

    private const val PREFS_NAME = "AlexToolExtraPrefs"
    private const val KEY_DOMAINS = "blockedDomains"
    private const val KEY_DISABLED_DOMAINS = "disabledDomains"

    /** Default blocklist ported 1:1 from the reference toolkit (link shorteners + paid-linkers). */
    val DEFAULT_BLOCKED_DOMAINS: Array<String> = arrayOf(
        "bit.ly", "bitly.com", "j.mp", "bitly.is", "tinyurl.com", "tiny.one", "tny.im", "tiny.cc",
        "goo.gl", "goo.su", "rebrand.ly", "rb.gy", "short.io", "short.link", "shrtlnk.co", "ow.ly",
        "t2m.io", "t2m.co", "cutt.ly", "cutt.us", "t.ly", "is.gd", "v.gd", "s.id", "po.st",
        "clck.ru", "qps.ru", "x.co", "su.pr", "wp.me", "dlvr.it", "buff.ly", "chzb.gr", "bit.do", "gtly.to",
        "shorte.st", "sh.st", "clkmein.com", "viid.me", "xiiu.me", "adf.ly", "j.gs", "q.gs", "ay.gy",
        "adfly.fr", "ad7.biz", "yyv.co", "u.bb", "linkshrink.net", "linkshrink.com", "ouo.io", "ouo.press",
        "bc.vc", "bcvc.live", "bcvc.xyz", "shortam.link", "clk.sh", "clik.pw", "za.gl", "za.uy", "zee.gl",
        "cpmlink.net", "cpmlink.com", "vivads.net", "vivads.com", "linkbucks.com", "adfoc.us",
        "shrinkearn.com", "shrinkearn.in", "clicksfly.com", "clicksfly.in", "shrinkme.io",
        "droplink.co", "tnlink.in", "linkvertise.com", "linkvertise.net", "linkvertise.in",
        "link-target.net", "payskip.me", "payskip.org", "exe.io", "uii.io", "blv.me", "ity.im", "zzb.bz",
        "linkzfly.com", "linkzfly.in", "linkzfly.net", "cut-win.com", "linkrex.net", "binbucks.com",
        "petty.link", "rom.io", "dz4link.com", "fc.lc", "adshort.co", "oke.io", "admy.link", "coshort.co",
        "cutpaid.com", "urle.co", "mitly.us", "zlshorte.net", "igram.im", "link4win.net", "short.pe",
        "urlcash.net", "al.ly", "link-tm.net", "tmearn.com", "linko.love", "zagl.in", "shorti.io",
        "exeo.app", "exey.io", "dutchycorp.space", "dutchycorp.ovh", "dutchycorp.com",
        "meganews.biz", "megafly.in", "megalink.pro", "megalink.in", "upshrink.com",
        "linkjust.com", "linkjust.in", "shrinkurl.org", "urlst.me", "linkmonetizer.com",
        "monetizer.link", "shorte.link", "shortearn.eu", "cashfly.io", "linkmize.com", "linkmize.in",
        "adpaylink.com", "adpay.link", "urlpay.in", "paylink.in", "paylink.co", "cashurl.in", "cashurl.io",
        "linkpay.in", "linkpay.co", "earnu.in", "earnu.co", "moneylink.in", "moneyurl.in",
        "shortlink.in", "shorturl.in", "urlshort.in", "urlshortener.in", "indianshortner.in",
        "indianshortner.com", "indiaearnx.com", "indiaearnx.in", "earnlink.io", "earnlink.in",
        "earnlink.co", "earnlinks.in", "earnlinks.co", "earnlinks.io", "earnlinks.xyz",
        "earnlinks.net", "earnlinks.com", "linksgo.co", "linksgo.in", "urlking.in", "urlking.com",
        "urlspay.in", "urlspay.com", "vplink.in", "vplink.com", "sub2go.co", "sub2go.in",
        "nowshort.in", "nowshort.com", "liteshort.in", "liteshort.com", "shortxlinks.com",
        "shortxlinks.in", "caslinks.com", "dupload.in", "dupload.com", "alpharede.com",
        "monteolympus.com", "mrnbypass.com", "nhapma.com", "redirly.com",
        "ezy-bypass.com", "ezybypass.com", "avbypasskoyeb.app", "adsfly.co", "adsfly.in", "adsfly.net",
        "arolink.com", "arolink.in", "arolinks.com", "arolinks.in", "gplinks.in", "gplinks.co",
        "gplinks.com", "mboost.me", "gyanlink.com", "shortclick.top", "shortclick.net", "clk.asia",
        "upns.in", "upns.io", "indexlinks.com", "linkspaisa.com", "rocklinks.net", "clicknearn.com",
        "3fx.link", "shrinke.me", "gtlinks.me", "linksxp.com", "linksly.co", "linksbaba.com", "earnl.xyz",
        "vearn.in", "shortlink.asia", "panyshort.link", "vipshortener.com", "viplink.in", "pdisq.com",
        "adrinolinks.in", "adrinolinks.com", "gainl.xyz", "shortyz.com", "clk.ink", "moneyshrink.online",
        "linksmoney.com", "payurl.info", "earn2short.in", "ez4short.com", "vipurl.in", "inshortner.com",
        "shortverse.com", "multipcclub.com", "adslinkfly.com", "linkpays.in", "shortix.co",
        "yeumoney.com", "link1s.net", "links.vn", "giare.link", "rut.li", "layxu.com", "8link.xyz",
        "megaurl.in", "kutt.it", "cuturl.io", "shorten.vn", "linkvip.vn", "1link.vn",
        "sfl.gs", "linkspree.co", "otoklix.link", "cuit.link", "pintasan.co", "cepat.co",
        "shortkey.co", "adsklix.com", "aduro.co", "encurtador.com.br", "abre.ai", "encurtaland.com",
        "encortador.com", "linksbr.net", "ganharlink.com", "linkganhador.com", "encurtalink.com.br",
        "shorteva.com", "banglalinks.co", "urlshortbd.com", "loankoro.com", "sagorlinks.com",
        "earnbd.link", "pklinks.co", "shortenurl.co.za", "naijalinks.co", "linksng.com", "earnza.co.za",
        "9jalink.com", "afrishort.com", "url.sa", "roabt.link", "ikhtsr.com", "rbtlink.com",
        "suo.im", "dwz.cn", "url.cn", "6.cn", "t.cn", "vk.cc", "goo-gl.ru", "shrtco.de", "linktw.in",
        "adcoin.link", "linkpoi.in", "adurly.link", "shrinkforearn.in", "earnify.co",
        "urlcloud.info", "monlink.co", "cash4links.co", "linkbnao.com", "getpaidlink.com",
        "urlpaisa.in", "clicklink.in", "shortpaid.in", "earningurl.com", "moneyshort.in",
        "clkearn.in", "linktoclick.in", "urlcut.in", "shrinklink.in", "clicknearn.in",
        "linksfy.in", "shorteningy.com", "vipshort.in", "adshort.in", "moneylinks.co.in",
        "clickurl.in", "earnify.in", "gainshort.in", "shortpe.com", "linkspaisa.in", "urlkorlo.com",
        "linksarkari.com", "clickskey.com", "shortkro.com", "linkgeni.com", "clickurls.in",
        "adsy.pw", "linksdunia.com", "clicksurl.in", "moneyurl.co.in", "instashort.in",
        "urlpaid.in", "quickshort.in", "earn2url.in", "1short.in", "smartshort.in", "clkfly.in",
        "linkzon.in", "urlgain.in", "gainurl.in", "clickmoney.in", "hindilinks.in",
        "adlink.guru", "linksbux.com", "shortnize.com", "urlsalon.com", "sub4unlock.com",
        "unlockme.co", "boostlink.co", "clickpays.com", "monetize.link", "adlinks.pw",
        "linkgain.co", "urlprofit.com", "shorteez.com", "clicknow.link", "earnify.link",
        "cashlink.co", "adshrink.it", "linkdrop.net", "getlink.pw", "vpnearn.co",
        "linkcents.com", "click4earn.com", "urlmoney.co", "profitlink.co", "adslink.pw",
        "linkzcash.com", "monetizelinks.com", "urlcash.co", "cpmshort.com", "cpmshort.in",
        "cpmshort.io", "cpmshort.co", "cpmshort.net", "cashlink.io", "cashlink.in",
        "cashlink.net", "cashlink.com", "amankan.link", "tautan.co", "linkgacor.com",
        "shortenurl.id", "kilatlink.com", "tautkan.com", "shorten.asia", "linkngan.com",
        "taigame.link", "ikhtsr.co", "rwabt.com", "tqsyr.com", "rbtly.com", "linkza.co.za",
        "shortza.com", "9jaearn.com", "shortbd.co", "bdlinks.net", "banglashort.com"
    )

    /** Subdomains that must never be blocked even when their parent domain is in the list. */
    val EXEMPT_SUBDOMAINS: Set<String> = setOf("viku.urlking.in")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBlockedDomains(context: Context): MutableList<String> {
        val raw = prefs(context).getString(KEY_DOMAINS, null) ?: return DEFAULT_BLOCKED_DOMAINS.toMutableList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.optString(i))
            if (list.isEmpty()) DEFAULT_BLOCKED_DOMAINS.toMutableList() else list
        } catch (e: Exception) {
            DEFAULT_BLOCKED_DOMAINS.toMutableList()
        }
    }

    fun saveBlockedDomains(context: Context, domains: List<String>) {
        val arr = JSONArray()
        domains.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_DOMAINS, arr.toString()).apply()
    }

    fun getDisabledDomains(context: Context): MutableSet<String> {
        val raw = prefs(context).getString(KEY_DISABLED_DOMAINS, null) ?: return mutableSetOf()
        return raw.split(",").filter { it.isNotEmpty() }.toMutableSet()
    }

    fun saveDisabledDomains(context: Context, domains: Set<String>) {
        prefs(context).edit().putString(KEY_DISABLED_DOMAINS, domains.joinToString(",")).apply()
    }

    fun addDomain(context: Context, input: String): String? {
        val domain = parseDomain(input) ?: return "parse_fail"
        val domains = getBlockedDomains(context)
        if (domains.contains(domain)) return "already"
        domains.add(domain)
        saveBlockedDomains(context, domains)
        return domain
    }

    fun removeDomain(context: Context, domain: String) {
        val domains = getBlockedDomains(context)
        domains.remove(domain)
        saveBlockedDomains(context, domains)
        val disabled = getDisabledDomains(context)
        disabled.remove(domain)
        saveDisabledDomains(context, disabled)
    }

    fun parseDomain(input: String): String? {
        return try {
            var lower = input.lowercase().trim()
            lower = lower.removePrefix("http://").removePrefix("https://").trim()
            if (lower.contains("/")) lower = lower.substringBefore("/").trim()
            if (lower.startsWith("www.")) lower = lower.removePrefix("www.")
            if (lower.isEmpty() || lower.contains(" ") || lower.contains(".").not()) return null
            lower
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns true when [urlStr] should be blocked. Mirrors the reference implementation:
     * alextrick-redirect URLs are never blocked, www-prefix is stripped, and both exact and
     * suffix host matches are honoured (including `*.sub.domain` rules).
     */
    fun isDomainBlocked(context: Context, urlStr: String): Boolean {
        val domains = getBlockedDomains(context)
        if (domains.isEmpty()) return false
        return try {
            val uri = Uri.parse(urlStr)
            if (!uri.isHierarchical) return false
            if (!uri.getQueryParameter("alextrick").isNullOrEmpty()) return false
            var host = uri.host ?: return false
            if (host.startsWith("www.")) host = host.removePrefix("www.")
            if (EXEMPT_SUBDOMAINS.contains(host)) return false
            val disabled = getDisabledDomains(context)
            domains.any { rule ->
                if (disabled.contains(rule)) return@any false
                val clean = if (rule.startsWith("*.")) rule.removePrefix("*.") else rule
                host == clean || host.endsWith(".$clean")
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Builds a share-safe tooling URL carrying the bypassed target. */
    fun buildToolingUrl(bypassedUrl: String): String {
        return "https://alextool.links/?alextrick=" + Uri.encode(bypassedUrl)
    }

    fun decodeToolingTarget(urlStr: String): String? {
        return try {
            val uri = Uri.parse(urlStr)
            val alextrick = uri.getQueryParameter("alextrick") ?: return null
            var target = alextrick
            var loops = 5
            var prev: String
            do {
                prev = target
                target = try { Uri.decode(target) } catch (e: Exception) { break }
            } while (prev != target && --loops > 0)
            if (target.startsWith("http://") || target.startsWith("https://")) target else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts Original Link / Bypassed Link pairs from a pasted bot message, mirroring the
     * reference toolkit's regex logic: pairs are matched per separated section, falling back to
     * alternating URL grouping when no explicit labels exist.
     */
    fun extractAllLinkPairs(text: String): List<LinkPair> {
        val pairs = mutableListOf<LinkPair>()
        val sections = text.split(Regex("─+|===|___|---"))
        for (section in sections) {
            val mOrig = PAT_ORIGINAL.matcher(section)
            val mByp = PAT_BYPASS.matcher(section)
            if (mOrig.find() && mByp.find()) {
                pairs.add(LinkPair(cleanUrl(mOrig.group(1)), cleanUrl(mByp.group(1))))
            }
        }
        if (pairs.isEmpty()) {
            val urls = mutableListOf<String>()
            val m = PAT_URL.matcher(text)
            while (m.find()) urls.add(cleanUrl(m.group()))
            for (i in urls.indices step 2) {
                if (i + 1 < urls.size) pairs.add(LinkPair(urls[i], urls[i + 1]))
            }
        }
        return pairs
    }

    private fun cleanUrl(url: String): String =
        url.replace(Regex("[\\s\"'*.,;)\\]]+$"), "").trim()

    fun isValidUrl(url: String): Boolean {
        return try {
            val u = URL(url)
            u.protocol == "http" || u.protocol == "https"
        } catch (e: MalformedURLException) {
            false
        }
    }

    private val PAT_ORIGINAL = Pattern.compile(
        "Original\\s*Link\\s*:?\\s*(?:✅|🔗)?\\s*(https?://\\S+)", Pattern.CASE_INSENSITIVE
    )
    private val PAT_BYPASS = Pattern.compile(
        "Bypass(?:ed)?\\s*Link\\s*:?\\s*(?:✅|🔗)?\\s*(https?://\\S+)", Pattern.CASE_INSENSITIVE
    )
    private val PAT_URL = Pattern.compile("https?://\\S+")

    data class LinkPair(val original: String, val bypassed: String)

    private typealias JSONObject = org.json.JSONObject
}
