/**
 * Instagram Content Controls (www.instagram.com)
 *
 * Content filtering for Instagram mobile web:
 * - Hide ads and sponsored posts (timeline connection filtering via fetch/XHR & JSON.parse)
 * - Hide suggested content (explore stories, netego units, suggested users)
 * - Hide comments (form, SVG buttons, comment counts)
 * - Block post creation
 * - Reels allowance / budget timer
 *
 * Config flags:
 *   hideAds, hideSuggested, hideComments, blockPosting, reelsBudgetMinutes
 */
(function() {
  'use strict';

  var NS = '__SocialLiteFree_Instagram__';
  if (window[NS]) {
    try { window[NS].dispose(); } catch (e) { /* ignore */ }
  }

  var config = /*CONFIG*/{};

  var KEY = 'xdt_api__v1__feed__timeline__connection';
  var seen = [];
  var seenLength = 0;
  var styleEl = null;
  var budgetStyleEl = null;
  var observer = null;
  var scanTimer = null;
  var budgetTimer = null;
  var reelsTimeSpentSeconds = 0;

  function markFiltered(text) {
    if (text.length > 262144) return;
    seen.push(text);
    seenLength += text.length;
    while (seen.length > 6 || seenLength > 262144) seenLength -= seen.shift().length;
  }

  function reason(node) {
    if (!node || typeof node !== 'object') return '';
    if (node.ad || node.ad4ad_in_webfeed) return config.hideAds ? 'ad' : '';
    if (!config.hideSuggested) return '';
    if (node.explore_story || node.suggested_users || node.stories_netego ||
        node.bloks_netego || node.abra_icebreakers_in_feed_unit ||
        (!node.media && !node.end_of_feed_demarcator)) return 'suggested';
    return '';
  }

  function filterTree(root) {
    if (!config.hideAds && !config.hideSuggested) return false;
    var touched = false;
    function walk(o, depth) {
      if (!o || typeof o !== 'object' || depth > 20) return;
      if (Array.isArray(o)) {
        for (var i = 0; i < o.length; i++) walk(o[i], depth + 1);
        return;
      }
      var conn = o[KEY];
      if (conn && Array.isArray(conn.edges)) {
        var kept = [], ads = 0, suggested = 0;
        for (var j = 0; j < conn.edges.length; j++) {
          var edge = conn.edges[j], why = reason(edge && edge.node);
          if (why === 'ad') ads++;
          else if (why === 'suggested') suggested++;
          else kept.push(edge);
        }
        if (ads || suggested) {
          conn.edges = kept;
          touched = true;
        }
        if (!kept.length && suggested > 0 && conn.page_info && conn.page_info.has_next_page) {
          conn.page_info.has_next_page = false;
          conn.page_info.end_cursor = null;
          touched = true;
        }
      }
      Object.keys(o).forEach(function(k) {
        if (!(conn && k === KEY)) walk(o[k], depth + 1);
      });
    }
    walk(root, 0);
    return touched;
  }

  function filterText(text) {
    if ((!config.hideAds && !config.hideSuggested) || typeof text !== 'string' || text.indexOf(KEY) < 0 || seen.indexOf(text) >= 0) return text;
    try {
      var parsed = JSON.parse(text);
      if (!filterTree(parsed)) return text;
      var output = JSON.stringify(parsed);
      markFiltered(output);
      return output;
    } catch (_) { return text; }
  }

  // Intercept fetch
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function(input, init) {
      return origFetch.apply(this, arguments).then(function(res) {
        if (!config.hideAds && !config.hideSuggested) return res;
        try {
          var clone = res.clone();
          var contentType = clone.headers.get('content-type') || '';
          if (/json|text/i.test(contentType)) {
            return clone.text().then(function(txt) {
              var filtered = filterText(txt);
              if (filtered === txt) return res;
              var headers = new Headers(res.headers);
              headers.delete('content-length');
              headers.delete('content-encoding');
              return new Response(filtered, { status: res.status, statusText: res.statusText, headers: headers });
            }).catch(function() { return res; });
          }
        } catch (_) {}
        return res;
      });
    };
  }

  // Intercept XHR
  var origOpen = XMLHttpRequest.prototype.open;
  var origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url) {
    this.__reqUrl = String(url);
    return origOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function() {
    var xhr = this;
    xhr.addEventListener('readystatechange', function() {
      if (xhr.readyState === 4 && (config.hideAds || config.hideSuggested)) {
        try {
          if (xhr.responseText && xhr.responseText.indexOf(KEY) >= 0) {
            var filtered = filterText(xhr.responseText);
            if (filtered !== xhr.responseText) {
              Object.defineProperty(xhr, 'responseText', { configurable: true, get: function() { return filtered; } });
              Object.defineProperty(xhr, 'response', { configurable: true, get: function() { return filtered; } });
            }
          }
        } catch (_) {}
      }
    });
    return origSend.apply(this, arguments);
  };

  // Intercept JSON.parse
  var origParse = JSON.parse;
  JSON.parse = function(text) {
    var result = origParse.apply(this, arguments);
    if ((config.hideAds || config.hideSuggested) && typeof text === 'string' && text.indexOf(KEY) >= 0 && seen.indexOf(text) < 0) {
      try { filterTree(result); } catch (_) {}
    }
    return result;
  };

  // CSS injection
  function injectStyles() {
    var css = [];
    if (config.hideComments) {
      var commentStems = ['Comment', '댓글', 'コメント', 'coment', 'commenter', 'kommentar', 'comentar', 'commenta', 'تعليق'];
      commentStems.forEach(function(s) {
        css.push('textarea[aria-label*="' + s + '" i] { display: none !important; }');
        css.push('svg[aria-label*="' + s + '" i] { display: none !important; }');
        css.push('a:has(svg[aria-label*="' + s + '" i]) { display: none !important; }');
        css.push('button:has(svg[aria-label*="' + s + '" i]) { display: none !important; }');
        css.push('[role="button"]:has(svg[aria-label*="' + s + '" i]) { display: none !important; }');
      });
      css.push('form:has(textarea) { display: none !important; }');
    }

    if (config.blockPosting) {
      css.push('a[href^="/create"], [aria-label="New post"], [aria-label="Create"], a:has(svg[aria-label="New post"]), [role="button"]:has(svg[aria-label="New post"]) { display: none !important; pointer-events: none !important; }');
    }

    styleEl = document.createElement('style');
    styleEl.id = NS + '_css';
    styleEl.textContent = css.join('\n');
    (document.head || document.documentElement).appendChild(styleEl);

    budgetStyleEl = document.createElement('style');
    budgetStyleEl.id = NS + '_budget_css';
    (document.head || document.documentElement).appendChild(budgetStyleEl);
  }

  // Reels budget checking
  function checkReelsBudget() {
    if (!config.reelsBudgetMinutes || config.reelsBudgetMinutes <= 0) return;
    var maxSeconds = config.reelsBudgetMinutes * 60;
    var isReel = /^\/reels?(\/|$)/i.test(location.pathname);

    if (isReel && document.visibilityState === 'visible') {
      reelsTimeSpentSeconds += 1;
      if (reelsTimeSpentSeconds >= maxSeconds) {
        budgetStyleEl.textContent = 'a[href^="/reel/"], a[href^="/reels/"] { display: none !important; }';
        document.querySelectorAll('video, audio').forEach(function(v) { v.pause(); });
        try {
          if (window.SocialLiteFree && window.SocialLiteFree.reelsBudgetExceeded) {
            window.SocialLiteFree.reelsBudgetExceeded();
          }
        } catch (_) {}
        location.replace('/');
      }
    }
  }

  function scan() {
    // Prevent clicking create
    if (config.blockPosting && /^\/create(\/|$)/i.test(location.pathname)) {
      location.replace('/');
    }
  }

  function init() {
    injectStyles();
    scanTimer = setInterval(scan, 500);
    budgetTimer = setInterval(checkReelsBudget, 1000);

    observer = new MutationObserver(scan);
    observer.observe(document.documentElement, { childList: true, subtree: true });

    document.addEventListener('click', function(e) {
      if (config.blockPosting) {
        var t = e.target;
        if (t && t.closest && (t.closest('a[href^="/create"]') || t.closest('[aria-label="New post"]') || t.closest('[aria-label="Create"]'))) {
          e.preventDefault();
          e.stopImmediatePropagation();
        }
      }
    }, true);
  }

  init();

  window[NS] = {
    dispose: function() {
      if (scanTimer) clearInterval(scanTimer);
      if (budgetTimer) clearInterval(budgetTimer);
      if (observer) observer.disconnect();
      if (styleEl) styleEl.remove();
      if (budgetStyleEl) budgetStyleEl.remove();
      if (origFetch) window.fetch = origFetch;
      if (origParse) JSON.parse = origParse;
      delete window[NS];
    }
  };
})();
