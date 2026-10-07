package app.goimium.browser

/**
 * Goimium Lite - the single source of truth for the browser's web-facing identity.
 *
 * Everything a website can observe is defined here, once, so that the UA string, the client
 * hint headers and the injected JavaScript cannot drift apart. Drift is the usual cause of
 * detection: a UA that says Windows next to a client hint that says Android is a louder
 * signal than an honest Android UA would have been.
 *
 * See docs/IDENTITY.md for the full channel-by-channel rationale.
 */
object DesktopIdentity {

    /**
     * Keep this aligned with a real, current Chrome stable major version. An outdated or
     * invented version is itself a fingerprint, and some sites gate features on it.
     */
    const val CHROME_MAJOR = "141"
    const val CHROME_FULL = "$CHROME_MAJOR.0.0.0"

    /**
     * The UA string. Note what is absent: no "Android", no "Linux", no "Mobile" token, and no
     * "wv" token (which WebView normally appends and which identifies an embedded WebView
     * rather than a browser).
     *
     * The minor/build/patch segments are 0.0.0 deliberately - Chrome's own User-Agent
     * reduction freezes them, so real values would be the anomaly.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$CHROME_FULL Safari/537.36"

    /**
     * User-Agent Client Hint headers.
     *
     * These matter more than the UA string on the modern web. Chrome sends the low-entropy
     * three on every request; servers ask for the rest via Accept-CH.
     *
     * Values chosen to be mutually consistent and unremarkable:
     *  - platform must be exactly "Windows" (the UA-CH token set is fixed; "Win32" is wrong here)
     *  - platform version uses the UA-CH Windows mapping: 15.0.0 == Windows 11, 13.0.0 == Win10 21H1+
     *  - arch x86 + bitness 64 must agree with "Win64; x64" in the UA string
     *  - model must be empty; a device model would be fatal
     *  - mobile must be ?0
     */
    val CLIENT_HINTS: Map<String, String> = linkedMapOf(
        "Sec-CH-UA" to
            ""Chromium";v="$CHROME_MAJOR", " +
            ""Google Chrome";v="$CHROME_MAJOR", " +
            ""Not?A_Brand";v="24"",
        "Sec-CH-UA-Mobile" to "?0",
        "Sec-CH-UA-Platform" to ""Windows"",
        "Sec-CH-UA-Platform-Version" to ""15.0.0"",
        "Sec-CH-UA-Arch" to ""x86"",
        "Sec-CH-UA-Bitness" to ""64"",
        "Sec-CH-UA-Model" to """",
        "Sec-CH-UA-Wow64" to "?0",
        "Sec-CH-UA-Form-Factors" to ""Desktop""
    )

    /**
     * The layout width Goimium reports and lays out at.
     *
     * 1280 is chosen rather than 1920: it is a real, common desktop breakpoint, every site has
     * a desktop layout for it, and text stays legible on a phone screen. Raising this to 1920
     * makes sites render more "PC-like" but smaller. Adjustable in Settings.
     */
    const val DESKTOP_WIDTH_DP = 1280

    const val HOME_PAGE = "https://www.google.com/"

    /**
     * Honest accounting of what this tier cannot do, surfaced in the app's About screen rather
     * than hidden. Goimium Full (the Chromium fork) closes all three.
     */
    val LITE_LIMITATIONS = listOf(
        "Client hints are sent on page navigations, but WebView cannot attach headers to " +
            "subresource requests, so an in-page fetch() may omit them.",
        "navigator properties are installed as overrides. A script that inspects property " +
            "descriptors can tell they were overridden, even though the values are correct.",
        "Touch event constructors and Windows font metrics cannot be fully emulated from " +
            "JavaScript; only the compiled fork removes them at the source.",
        "Pointer Lock is bridged through Android View pointer capture. The engine-level Chromium " +
            "Pointer Lock implementation remains available only in Goimium Full."
    )
}
