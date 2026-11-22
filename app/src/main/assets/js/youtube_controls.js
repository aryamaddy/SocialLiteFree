/**
 * YouTube Content Controls (m.youtube.com)
 * 
 * Comprehensive content filtering for YouTube mobile web:
 * - Ad blocking (CSS, fetch/XHR interception, skip buttons, JSON.parse override)
 * - Shorts hiding (CSS + navigation redirect)
 * - Comment hiding
 * - Recommendation hiding
 * - Search-only mode (hide home feed)
 * - Feed autoplay blocking
 * - "Open App" prompt removal
 *
 * Config flags:
 *   blockAds, hideShorts, hideComments, hideRecommendations,
 *   searchOnly, blockFeedAutoplay
 */
(function() {
  'use strict';

  // ── Namespace guard: prevent double-injection ──
  var NS = '__SocialLiteFree_YouTube__';
  if (window[NS]) {
    try { window[NS].dispose(); } catch (e) { /* ignore */ }
  }

  // ── Config injected by Android via string replacement ──
  var config = /*CONFIG*/{};

  // ── State for cleanup ──
  var state = {
    styleEl: null,
    observer: null,
    scanTimer: null,
    origFetch: null,
    origXhrOpen: null,
    origJsonParse: null,
    skipAdTimer: null,
    autoplayTimer: null
  };

  // ═══════════════════════════════════════════════════════════
  //  1. CSS INJECTION
  // ═══════════════════════════════════════════════════════════
  function injectStyles() {
    var rules = [];

    // ── Ad blocking CSS ──
    if (config.blockAds) {
      rules.push(
        // Promoted / sponsored content containers
        'ytm-promoted-sparkles-web-renderer,' +
        'ytm-ad-slot-renderer,' +
        'ytm-companion-ad-renderer,' +
        'ytm-promoted-video-renderer,' +
        '.ytm-promoted-sparkles-web-renderer,' +
        '.ad-container,' +
        '.ad-showing .ytp-ad-module,' +
        '.ytp-ad-overlay-container,' +
        '.ytp-ad-message-container,' +
        '.ytm-rich-item-renderer[is-ad],' +
        'ytm-in-feed-ad-layout-renderer,' +
        'ytm-display-ad-renderer,' +
        '#player-ads,' +
        '#masthead-ad,' +
        '.ytm-banner-ad-renderer,' +
        '.ad-interrupting,' +
        '.video-ads,' +
        'div[id^="ytd-merch"],' +
        '.ytp-ad-skip-button-container { display: none !important; }'
      );
    }

    // ── Shorts hiding CSS ──
    if (config.hideShorts) {
      rules.push(
        // Shorts shelves on home/search
        'ytm-reel-shelf-renderer,' +
        'ytm-shorts-lockup-view-model,' +
        'ytm-rich-section-renderer[is-shorts],' +
        '[is-shorts],' +
        'ytm-reel-item-renderer,' +
        // Shorts tab in bottom nav
        'ytm-pivot-bar-item-renderer:has(> .pivot-shorts),' +
        'ytm-pivot-bar-item-renderer[tab-identifier="FEshorts"],' +
        // Shorts chip on search
        'a[href*="/shorts"],' +
        // Generic shorts shelf selectors
        '.reel-shelf-items,' +
        'ytm-shorts-lockup-view-model-v2 { display: none !important; }'
      );
    }

    // ── Comment hiding CSS ──
    if (config.hideComments) {
      rules.push(
        'ytm-comment-section-renderer,' +
        'ytm-comments-entry-point-header-renderer,' +
        'ytm-comment-section-header-renderer,' +
        '.comment-section,' +
        '#comment-teaser,' +
        'ytm-comments-simplebox-renderer,' +
        'ytm-comment-thread-renderer { display: none !important; }'
      );
    }

    // ── Recommendation hiding CSS ──
    if (config.hideRecommendations) {
      rules.push(
        'ytm-watch-next-content,' +
        'ytm-compact-video-renderer,' +
        'ytm-rich-item-renderer:not([is-search]),' +
        '.related-chips-slot-wrapper,' +
        'ytm-single-column-watch-next-results-renderer .item-section-renderer { display: none !important; }'
      );
    }

    // ── Search-only mode: hide home feed ──
    if (config.searchOnly) {
      rules.push(
        // Hide home feed items but NOT search results
        'ytm-browse[page-subtype="home"] ytm-rich-item-renderer,' +
        'ytm-browse[page-subtype="home"] ytm-rich-section-renderer,' +
        'ytm-browse[page-subtype="home"] .rich-grid-renderer,' +
        'ytm-section-list-renderer[page-subtype="home"],' +
        '#contents.ytm-section-list-renderer { display: none !important; }'
      );
    }

    // ── "Open App" prompt hiding ──
    rules.push(
      // Always hide open-in-app banners
      'ytm-feed-nudge-renderer,' +
      '.mobile-topbar-header-sign-in-button,' +
      '.ytm-autonav-bar,' +
      '.companion-ad-container,' +
      'ytm-mealbar-promo-renderer,' +
      '.upsell-dialog-lightbox,' +
      '.opened-dialog[data-dialog-type="upsell"],' +
      'ytm-banner-promo-renderer,' +
      '#consent-bump,' +
      'tp-yt-paper-dialog.ytm-mealbar-promo-renderer,' +
      '.ytm-promo-renderer { display: none !important; }'
    );

    if (rules.length > 0) {
      state.styleEl = document.createElement('style');
      state.styleEl.id = NS + '_styles';
      state.styleEl.textContent = rules.join('\n');
      (document.head || document.documentElement).appendChild(state.styleEl);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  2. FETCH / XHR INTERCEPTION (Ad stripping from API)
  // ═══════════════════════════════════════════════════════════

  /** Fields to strip from YouTube API JSON responses */
  var AD_FIELDS = [
    'adPlacements', 'adSlots', 'playerAds', 'adBreakParams',
    'advertisingId', 'instreamAdBreak', 'adBreakHeartbeatParams',
    'promotedSparklesWebRenderer', 'inFeedAdLayoutRenderer',
    'bannerPromoRenderer', 'mealbarPromoRenderer',
    'adLayoutMetadata', 'invideoOverlayAds', 'compactPromotedVideoRenderer',
    'promotedVideoRenderer', 'sparklesWebRenderer'
  ];

  /** Recursively strip ad-related keys from an object */
  function stripAds(obj) {
    if (!obj || typeof obj !== 'object') return obj;
    if (Array.isArray(obj)) {
      for (var i = 0; i < obj.length; i++) {
        stripAds(obj[i]);
      }
      return obj;
    }
    for (var i = 0; i < AD_FIELDS.length; i++) {
      if (obj.hasOwnProperty(AD_FIELDS[i])) {
        delete obj[AD_FIELDS[i]];
      }
    }
    var keys = Object.keys(obj);
    for (var k = 0; k < keys.length; k++) {
      stripAds(obj[keys[k]]);
    }
    return obj;
  }

  function interceptFetch() {
    if (!config.blockAds) return;

    state.origFetch = window.fetch;
    window.fetch = function() {
      return state.origFetch.apply(this, arguments).then(function(response) {
        try {
          var url = (arguments[0] && arguments[0].url) || (typeof arguments[0] === 'string' ? arguments[0] : '');
          // Only intercept YouTube API responses
          if (url.indexOf('/youtubei/') === -1 && url.indexOf('/api/') === -1) {
            return response;
          }
          var clone = response.clone();
          return clone.text().then(function(text) {
            try {
              var json = JSON.parse(text);
              stripAds(json);
              return new Response(JSON.stringify(json), {
                status: response.status,
                statusText: response.statusText,
                headers: response.headers
              });
            } catch (e) {
              return response;
            }
          });
        } catch (e) {
          return response;
        }
      });
    };
  }

  function interceptXhr() {
    if (!config.blockAds) return;

    state.origXhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
      this._slUrl = url || '';
      return state.origXhrOpen.apply(this, arguments);
    };

    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function() {
      var xhr = this;
      var origOnReady = xhr.onreadystatechange;
      xhr.onreadystatechange = function() {
        if (xhr.readyState === 4 && xhr._slUrl &&
            (xhr._slUrl.indexOf('/youtubei/') !== -1 || xhr._slUrl.indexOf('/api/') !== -1)) {
          try {
            var data = JSON.parse(xhr.responseText);
            stripAds(data);
            Object.defineProperty(xhr, 'responseText', { value: JSON.stringify(data) });
          } catch (e) { /* not JSON or parse error */ }
        }
        if (origOnReady) origOnReady.apply(this, arguments);
      };
      return origSend.apply(this, arguments);
    };
  }

  // ═══════════════════════════════════════════════════════════
  //  3. JSON.parse OVERRIDE (Strip ads from SSR initial data)
  // ═══════════════════════════════════════════════════════════
  function overrideJsonParse() {
    if (!config.blockAds) return;

    state.origJsonParse = JSON.parse;
    JSON.parse = function(text, reviver) {
      var result = state.origJsonParse.call(JSON, text, reviver);
      try {
        if (result && typeof result === 'object') {
          stripAds(result);
        }
      } catch (e) { /* ignore */ }
      return result;
    };
  }

  // ═══════════════════════════════════════════════════════════
  //  4. DOM SCANNING (skip buttons, shorts redirect, autoplay)
  // ═══════════════════════════════════════════════════════════
  function scanDOM() {
    try {
      // ── Auto-click skip ad buttons ──
      if (config.blockAds) {
        var skipBtns = document.querySelectorAll(
          '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, ' +
          'button.ytp-ad-skip-button-container, .ytp-skip-ad-button, ' +
          '[id^="skip-button"], .videoAdUiSkipButton'
        );
        for (var i = 0; i < skipBtns.length; i++) {
          skipBtns[i].click();
        }

        // Also try to dismiss any ad overlay
        var closeOverlays = document.querySelectorAll(
          '.ytp-ad-overlay-close-button, .ytp-ad-overlay-close-container button'
        );
        for (var j = 0; j < closeOverlays.length; j++) {
          closeOverlays[j].click();
        }
      }

      // ── Shorts redirect: /shorts/XXX → /watch?v=XXX ──
      if (config.hideShorts && window.location.pathname.indexOf('/shorts/') === 0) {
        var videoId = window.location.pathname.split('/shorts/')[1];
        if (videoId) {
          videoId = videoId.split('?')[0].split('/')[0];
          window.location.replace('/watch?v=' + videoId);
          return; // stop further scanning after redirect
        }
      }

      // ── Block feed autoplay (pause video on non-watch pages) ──
      if (config.blockFeedAutoplay && window.location.pathname.indexOf('/watch') === -1) {
        var videos = document.querySelectorAll('video');
        for (var v = 0; v < videos.length; v++) {
          if (!videos[v].paused) {
            videos[v].pause();
          }
        }
      }

      // ── Remove "Open App" prompt elements by text content ──
      var allBtns = document.querySelectorAll('button, a, [role="button"]');
      for (var b = 0; b < allBtns.length; b++) {
        var txt = (allBtns[b].textContent || '').trim().toLowerCase();
        if (txt === 'open app' || txt === 'get the app' || txt === 'use app' ||
            txt === 'open in app' || txt === 'get app') {
          var parent = allBtns[b].closest('[class*="promo"], [class*="banner"], [class*="upsell"], [class*="mealbar"]');
          if (parent) {
            parent.style.display = 'none';
          } else {
            allBtns[b].style.display = 'none';
          }
        }
      }
    } catch (e) {
      // Fail silently
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  5. MUTATION OBSERVER
  // ═══════════════════════════════════════════════════════════
  function startObserver() {
    state.observer = new MutationObserver(function(mutations) {
      // Debounce: only scan once per animation frame
      if (!state._scanScheduled) {
        state._scanScheduled = true;
        requestAnimationFrame(function() {
          state._scanScheduled = false;
          scanDOM();
        });
      }
    });

    state.observer.observe(document.documentElement, {
      childList: true,
      subtree: true
    });
  }

  // ═══════════════════════════════════════════════════════════
  //  6. DISPOSE PATTERN
  // ═══════════════════════════════════════════════════════════
  function dispose() {
    try {
      if (state.styleEl && state.styleEl.parentNode) {
        state.styleEl.parentNode.removeChild(state.styleEl);
      }
      if (state.observer) {
        state.observer.disconnect();
      }
      if (state.scanTimer) {
        clearInterval(state.scanTimer);
      }
      if (state.origFetch) {
        window.fetch = state.origFetch;
      }
      if (state.origXhrOpen) {
        XMLHttpRequest.prototype.open = state.origXhrOpen;
      }
      if (state.origJsonParse) {
        JSON.parse = state.origJsonParse;
      }
      delete window[NS];
    } catch (e) { /* ignore */ }
  }

  // ═══════════════════════════════════════════════════════════
  //  7. INITIALIZATION
  // ═══════════════════════════════════════════════════════════
  try {
    injectStyles();
    interceptFetch();
    interceptXhr();
    overrideJsonParse();
    scanDOM();
    startObserver();

    // Backup scan every 400ms
    state.scanTimer = setInterval(scanDOM, 400);

    // Expose dispose for re-injection
    window[NS] = { dispose: dispose };
  } catch (e) {
    // Fail gracefully
  }
})();
