/*
 * Goimium Lite - desktop identity bootstrap
 *
 * This file is injected by WebViewCompat.addDocumentStartJavaScript(), which runs it before
 * ANY page script, including inline scripts in <head>. That timing is the whole point: most
 * WebView spoofs inject from onPageStarted or onPageFinished, by which time the page has
 * already read the real navigator, and the spoof is useless.
 */
(function () {
  'use strict';

  if (window.__goimium) return;
  Object.defineProperty(window, '__goimium', { value: true, enumerable: false });

  var CHROME_VERSION = '141.0.0.0';
  var UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
           '(KHTML, like Gecko) Chrome/' + CHROME_VERSION + ' Safari/537.36';

  var nativeToString = Function.prototype.toString;
  var faked = new WeakMap();

  Function.prototype.toString = function () {
    if (faked.has(this)) return 'function ' + faked.get(this) + '() { [native code] }';
    return nativeToString.call(this);
  };
  faked.set(Function.prototype.toString, 'toString');

  function define(target, name, getter) {
    var fn = function () { return getter(); };
    faked.set(fn, 'get ' + name);
    try {
      Object.defineProperty(target, name, {
        get: fn, set: undefined, enumerable: true, configurable: true
      });
    } catch (e) {}
  }

  var NavProto = Object.getPrototypeOf(navigator) || Navigator.prototype;

  define(NavProto, 'userAgent',  function () { return UA; });
  define(NavProto, 'appVersion', function () { return UA.substring(8); });
  define(NavProto, 'platform',   function () { return 'Win32'; });
  define(NavProto, 'oscpu',      function () { return 'Windows NT 10.0; Win64; x64'; });
  define(NavProto, 'vendor',     function () { return 'Google Inc.'; });
  define(NavProto, 'vendorSub',  function () { return ''; });
  define(NavProto, 'product',    function () { return 'Gecko'; });
  define(NavProto, 'productSub', function () { return '20030107'; });
  define(NavProto, 'appCodeName',function () { return 'Mozilla'; });
  define(NavProto, 'appName',    function () { return 'Netscape'; });

  define(NavProto, 'maxTouchPoints', function () { return 0; });
  define(NavProto, 'hardwareConcurrency', function () { return 16; });
  define(NavProto, 'deviceMemory',        function () { return 8; });

  if (navigator.connection) {
    var ConnProto = Object.getPrototypeOf(navigator.connection);
    define(ConnProto, 'effectiveType', function () { return '4g'; });
    define(ConnProto, 'rtt',           function () { return 50; });
    define(ConnProto, 'downlink',      function () { return 10; });
    try { delete ConnProto.type; } catch (e) {}
  }

  var BRANDS = [
    { brand: 'Chromium', version: '141' },
    { brand: 'Google Chrome', version: '141' },
    { brand: 'Not?A_Brand', version: '24' }
  ];
  var FULL_BRANDS = [
    { brand: 'Chromium',      version: CHROME_VERSION },
    { brand: 'Google Chrome', version: CHROME_VERSION },
    { brand: 'Not?A_Brand',   version: '24.0.0.0' }
  ];
  var HIGH_ENTROPY = {
    architecture: 'x86',
    bitness: '64',
    brands: BRANDS,
    fullVersionList: FULL_BRANDS,
    mobile: false,
    model: '',
    platform: 'Windows',
    platformVersion: '15.0.0',
    uaFullVersion: CHROME_VERSION,
    wow64: false,
    formFactors: ['Desktop']
  };

  var uaData = {
    get brands() { return BRANDS.slice(); },
    get mobile() { return false; },
    get platform() { return 'Windows'; },
    getHighEntropyValues: function (hints) {
      var out = { brands: BRANDS.slice(), mobile: false, platform: 'Windows' };
      (hints || []).forEach(function (h) {
        if (h in HIGH_ENTROPY) out[h] = HIGH_ENTROPY[h];
      });
      return Promise.resolve(out);
    },
    toJSON: function () {
      return { brands: BRANDS.slice(), mobile: false, platform: 'Windows' };
    }
  };
  faked.set(uaData.getHighEntropyValues, 'getHighEntropyValues');
  faked.set(uaData.toJSON, 'toJSON');
  define(NavProto, 'userAgentData', function () { return uaData; });

  ['TouchEvent', 'Touch', 'TouchList'].forEach(function (name) {
    try {
      Object.defineProperty(window, name, {
        get: function () { return undefined; },
        configurable: true
      });
    } catch (e) {}
  });
  try {
    Object.defineProperty(window, 'ontouchstart', {
      get: function () { return undefined; },
      configurable: true
    });
  } catch (e) {}

  var W = 1920, H = 1080;
  var ScrProto = Object.getPrototypeOf(screen) || Screen.prototype;
  define(ScrProto, 'width',       function () { return W; });
  define(ScrProto, 'height',      function () { return H; });
  define(ScrProto, 'availWidth',  function () { return W; });
  define(ScrProto, 'availHeight', function () { return H - 40; });
  define(ScrProto, 'colorDepth',  function () { return 24; });
  define(ScrProto, 'pixelDepth',  function () { return 24; });
  define(window,   'devicePixelRatio', function () { return 1; });

  try { delete NavProto.vibrate; } catch (e) {}
  ['DeviceOrientationEvent', 'DeviceMotionEvent'].forEach(function (name) {
    try {
      Object.defineProperty(window, name, {
        get: function () { return undefined; },
        configurable: true
      });
    } catch (e) {}
  });

  var GL_VENDOR = 'Google Inc. (NVIDIA)';
  var GL_RENDERER =
    'ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)';

  function patchGl(proto) {
    if (!proto) return;
    var orig = proto.getParameter;
    var wrapped = function (p) {
      if (p === 37445 || p === 0x1F00) return GL_VENDOR;
      if (p === 37446 || p === 0x1F01) return GL_RENDERER;
      return orig.call(this, p);
    };
    faked.set(wrapped, 'getParameter');
    proto.getParameter = wrapped;
  }
  patchGl(window.WebGLRenderingContext && WebGLRenderingContext.prototype);
  patchGl(window.WebGL2RenderingContext && WebGL2RenderingContext.prototype);
})();
