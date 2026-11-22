/**
 * Twitter / X Content Controls (mobile.twitter.com / x.com)
 *
 * Content filtering for Twitter / X mobile web:
 * - Hide "For You" tab, force "Following"
 * - Hide Promoted tweets / Ads
 * - Hide "Open App" prompts and banners
 *
 * Config flags:
 *   hideForYou, hideAds
 */
(function() {
  'use strict';

  var NS = '__SocialLiteFree_Twitter__';
  if (window[NS]) {
    try { window[NS].dispose(); } catch (e) { /* ignore */ }
  }

  var config = /*CONFIG*/{};

  var styleEl = null;
  var scanTimer = null;
  var observer = null;

  var adLabels = [
    'Ad', 'Anzeige', 'Publicité', 'Anuncio', 'Annuncio', 'Anúncio', '広告', '광고', 'الإعلان',
    'Promoted', 'Gesponsert', 'Sponsorisé', 'Promocionado', 'Sponsorizzato', 'Promovido', 'プロモーション', '프로모션 중', 'مُرَوَّج'
  ];

  function injectStyles() {
    var css = [];

    // Hide app download banners
    css.push('[data-testid="BottomBar"], [data-testid="sheetDialog"], [aria-label*="Open app" i] { display: none !important; }');

    styleEl = document.createElement('style');
    styleEl.id = NS + '_css';
    styleEl.textContent = css.join('\n');
    (document.head || document.documentElement).appendChild(styleEl);
  }

  function hidePromotedTweets() {
    if (!config.hideAds) return;

    // Scan articles/cells for promoted badges
    var articles = document.querySelectorAll('article[data-testid="tweet"]');
    articles.forEach(function(article) {
      if (article.getAttribute('data-sl-filtered')) return;

      var text = (article.textContent || '').toLowerCase();
      var hasAdBadge = adLabels.some(function(badge) {
        return text.indexOf(badge.toLowerCase()) !== -1;
      });

      // Also check specific test IDs or spans
      var spans = article.querySelectorAll('span');
      for (var i = 0; i < spans.length; i++) {
        var spanText = (spans[i].textContent || '').trim().toLowerCase();
        if (adLabels.some(function(b) { return b.toLowerCase() === spanText; })) {
          hasAdBadge = true;
          break;
        }
      }

      if (hasAdBadge) {
        article.setAttribute('data-sl-filtered', 'true');
        article.style.setProperty('display', 'none', 'important');
      }
    });
  }

  function enforceFollowingTab() {
    if (!config.hideForYou) return;

    // Look for tabs: "For you" / "Following"
    var tabs = document.querySelectorAll('[role="tab"]');
    tabs.forEach(function(tab) {
      var tabText = (tab.textContent || '').trim().toLowerCase();
      if (tabText === 'for you' || tabText === 'foryou' || tabText === 'für dich' || tabText === 'pour vous' || tabText === 'para ti') {
        tab.style.setProperty('display', 'none', 'important');
      } else if (tabText === 'following' || tabText === 'folge ich' || tabText === 'abonnements' || tabText === 'siguiendo') {
        // If "For you" is currently selected, click "Following"
        if (tab.getAttribute('aria-selected') !== 'true') {
          // If no other valid tab is selected
          var selectedTab = document.querySelector('[role="tab"][aria-selected="true"]');
          if (selectedTab && selectedTab !== tab) {
            var selectedText = (selectedTab.textContent || '').trim().toLowerCase();
            if (selectedText === 'for you' || selectedText === 'foryou') {
              tab.click();
            }
          }
        }
      }
    });
  }

  function scan() {
    hidePromotedTweets();
    enforceFollowingTab();
  }

  function init() {
    injectStyles();
    scan();

    scanTimer = setInterval(scan, 600);

    observer = new MutationObserver(scan);
    observer.observe(document.documentElement, { childList: true, subtree: true });
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
