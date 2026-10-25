# Home preview and discovery

This iteration adapts ideas from Shiv Patel's [Jellyfin Android TV UI overhaul](https://github.com/ShivPatel123/jellyfin-androidtv),
inspected at commit `75bade62f5059555d3302467d7f5256bfd75eae0`.
Relevant reference files are `HomeFragmentNetflixStyle.kt`, `fragment_home_netflix_style.xml`,
and the `HomeFragmentRecommendedRow`, `HomeFragmentPopularRow`, and `HomeFragmentGenreRow` classes.
Both projects retain the upstream GPL-2.0 license.

## Adaptation

- A selected-item backdrop fades into a navy surface with title, genres, runtime,
  production year, age rating, and community rating from existing row metadata.
- The preview waits 180 ms for focus to settle, loads one image, and bounds the
  requested dimensions. Home no longer also loads a full-screen backdrop slideshow.
- Blue row accents, left-aligned home captions, and subtle focused-caption panels
  complement JellyTweak's existing sidebar and poster focus border.
- Home settings offer a compact preview and optional discovery rows. Discovery is
  off by default and does no extra fetching until enabled.
- Discovery adds up to three rows per eligible movie/TV library: highly rated
  unwatched picks, recent releases sorted by premiere date, and a daily genre
  spotlight selected from that library's real genre response. Each feed is capped
  at 24 items and excludes heavy playback metadata. Libraries excluded from Home's
  latest items are also excluded from discovery.
- Configured Home rows attach first. Discovery loads afterwards, with a bounded
  genre request and cancellation when disabled. Empty result rows use the existing
  adapter removal behaviour.

The fork's fixed genre list and placeholder personalization/trending claims are
not used. Its old top navigation and playback architecture are not transplanted;
JellyTweak retains the persistent sidebar, six-column library grid, regular video
player, stats/file-info overlays, and music media-key support.

## Verification and continuation

Run the Home, library-grid, sidebar-boundary, playback, file-info, and Media3 session
unit tests, then build `:app:assembleStats` with `-Pjellyfin.version=0.20.0-stats.10`.
Keep code optimization/resource shrinking enabled and the APK below 30,000,000
bytes, retaining the existing package ID and update signing certificate.

Physical-TV checks remain necessary: preview crop and text at TV display sizes,
rapid remote navigation, return from details/settings, enabling/disabling discovery,
empty/offline libraries, and rendering/network/memory performance. No runtime
performance improvement is claimed from static inspection or unit tests.
