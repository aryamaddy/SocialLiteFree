/**
 * Common Controls (Injected on all platforms)
 *
 * Universal controls:
 * - Grayscale mode (reduces dopamine addiction)
 * - Hide generic "Open in App" or app store install banners
 *
 * Config flags:
 *   grayscale
 */
(function() {
  'use strict';

  var NS = '__SocialLiteFree_Common__';
  if (window[NS]) {
    try { window[NS].dispose(); } catch (e) { /* ignore */ }
  }

  var config = /*CONFIG*/{};
  var styleEl = null;

  function inject() {
    var css = [];

    if (config.grayscale) {
      css.push('html { filter: grayscale(1) !important; }');
    }

    // Generic "Open App" prompts
    css.push(
      '.smartbanner, .app-banner, [class*="open-app-banner" i], [class*="app-promo" i], [class*="install-banner" i] { display: none !important; }'
    );

    styleEl = document.createElement('style');
    styleEl.id = NS + '_css';
    styleEl.textContent = css.join('\n');
    (document.head || document.documentElement).appendChild(styleEl);
  }

  inject();

  window[NS] = {
    dispose: function() {
      if (styleEl) styleEl.remove();
      delete window[NS];
    }
  };
})();
