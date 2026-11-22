/**
 * TikTok Content Controls (www.tiktok.com)
 *
 * Content filtering for TikTok mobile web:
 * - Block explore / discover navigation
 * - Block live streams
 * - Block shop / marketplace
 * - Block upload / create
 * - Block search
 * - Block comments
 * - Block suggested accounts
 *
 * Config flags:
 *   blockExplore, blockLive, blockShop, blockUpload, blockSearch, blockComments, blockSuggested
 */
(function() {
  'use strict';

  var NS = '__SocialLiteFree_TikTok__';
  if (window[NS]) {
    try { window[NS].dispose(); } catch (e) { /* ignore */ }
  }

  var config = /*CONFIG*/{};

  var styleEl = null;
  var scanTimer = null;
  var observer = null;

  function injectStyles() {
    var css = [];

    if (config.blockExplore) {
      css.push('a[href*="/explore"], a[href*="/discover"], [data-e2e="nav-explore"], [data-e2e="nav-discover"] { display: none !important; pointer-events: none !important; }');
    }

    if (config.blockLive) {
      css.push('a[href*="/live"], [data-e2e="nav-live"] { display: none !important; pointer-events: none !important; }');
    }

    if (config.blockShop) {
      css.push('a[href*="/shop"], a[href*="/browse"], [data-e2e="nav-shop"] { display: none !important; pointer-events: none !important; }');
    }

    if (config.blockUpload) {
      css.push('a[href*="/upload"], a[href*="/creator"], [data-e2e="nav-upload"], [data-e2e="nav-create"], [data-e2e="upload-icon"], [class*="CreateButton" i], [class*="BottomNavCreate" i] { display: none !important; pointer-events: none !important; }');
    }

    if (config.blockSearch) {
      css.push('[data-e2e="nav-search"], a[href*="/search"], [data-e2e="search-box"], [class*="SearchContainer" i], [class*="SearchBar" i], form[action*="/search"] { display: none !important; pointer-events: none !important; }');
    }

    if (config.blockComments) {
      css.push('[data-e2e="comment-list"], [class*="CommentListContainer" i], [class*="CommentList" i], [data-e2e="browse-comment"], [data-e2e="comment-icon"], [class*="comment-list" i] { display: none !important; pointer-events: none !important; }');
    }

    if (config.blockSuggested) {
      css.push('[data-e2e="suggested-accounts"], [class*="SuggestedAccount" i], [data-e2e="recommend-list"] { display: none !important; pointer-events: none !important; }');
    }

    styleEl = document.createElement('style');
    styleEl.id = NS + '_css';
    styleEl.textContent = css.join('\n');
    (document.head || document.documentElement).appendChild(styleEl);
  }

  function checkBlockedRoutes() {
    var path = location.pathname.toLowerCase();

    if (config.blockExplore && (path.indexOf('/explore') >= 0 || path.indexOf('/discover') >= 0)) {
      location.replace('/following');
      return;
    }

    if (config.blockLive && (path.indexOf('/live') >= 0 || /^\/@[^/]+\/live(?:\/|$)/.test(path))) {
      location.replace('/following');
      return;
    }

    if (config.blockShop && path.indexOf('/shop') >= 0) {
      location.replace('/following');
      return;
    }

    if (config.blockUpload && (path.indexOf('/upload') >= 0 || path.indexOf('/creator') >= 0)) {
      location.replace('/following');
      return;
    }

    if (config.blockSearch && path.indexOf('/search') >= 0) {
      location.replace('/following');
      return;
    }
  }

  function init() {
    injectStyles();
    checkBlockedRoutes();

    scanTimer = setInterval(checkBlockedRoutes, 500);

    observer = new MutationObserver(checkBlockedRoutes);
    observer.observe(document.documentElement, { childList: true, subtree: true });

    document.addEventListener('click', function(e) {
      var t = e.target;
      if (!t || !t.closest) return;

      var link = t.closest('a[href]');
      if (!link) return;

      var href = link.getAttribute('href') || '';
      if ((config.blockExplore && (href.indexOf('/explore') >= 0 || href.indexOf('/discover') >= 0)) ||
          (config.blockLive && href.indexOf('/live') >= 0) ||
          (config.blockShop && href.indexOf('/shop') >= 0) ||
          (config.blockUpload && href.indexOf('/upload') >= 0) ||
          (config.blockSearch && href.indexOf('/search') >= 0)) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }
    }, true);
  }

  init();

  window[NS] = {
    dispose: function() {
      if (scanTimer) clearInterval(scanTimer);
      if (observer) observer.disconnect();
      if (styleEl) styleEl.remove();
      delete window[NS];
    }
  };
})();
