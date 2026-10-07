/* Goimium desktop input bridge.
 *
 * The system WebView already maps ordinary USB/Bluetooth mouse and keyboard input into Chromium.
 * This small layer only fills the Android/WebView gap around Pointer Lock: requestPointerLock(),
 * movementX/Y, hidden pointer state, and exitPointerLock(). Native capture is still gated by a
 * recent real mouse button gesture, so an arbitrary page cannot silently steal the pointer.
 */
(function () {
  'use strict';
  if (window.__goimiumInput) return;
  Object.defineProperty(window, '__goimiumInput', { value: true, enumerable: false });

  var locked = false;
  var lastElement = null;
  var lastX = 0;
  var lastY = 0;

  function target() {
    return lastElement || document.body || document.documentElement;
  }

  function fire(type, init) {
    var el = target();
    if (!el) return;
    try {
      if (type === 'mousemove' || type === 'mousedown' || type === 'mouseup') {
        el.dispatchEvent(new MouseEvent(type, Object.assign({
          bubbles: true,
          cancelable: true,
          view: window,
          detail: 0,
          screenX: lastX,
          screenY: lastY,
          clientX: lastX,
          clientY: lastY,
          ctrlKey: false,
          altKey: false,
          shiftKey: false,
          metaKey: false,
          button: 0,
          buttons: 0,
          relatedTarget: null
        }, init || {})));
      }
    } catch (_) {}
  }

  window.__goimiumPointerLockEvent = function (kind, dx, dy, button, buttons, x, y) {
    if (!locked) return;
    lastX = x;
    lastY = y;
    if (kind === 'move') {
      fire('mousemove', { movementX: dx, movementY: dy, buttons: buttons });
    } else if (kind === 'down') {
      fire('mousedown', { button: button, buttons: buttons });
    } else if (kind === 'up') {
      fire('mouseup', { button: button, buttons: buttons });
    }
  };

  window.__goimiumPointerLockWheel = function (dx, dy) {
    if (!locked) return;
    var el = target();
    if (!el) return;
    try {
      el.dispatchEvent(new WheelEvent('wheel', {
        bubbles: true,
        cancelable: true,
        view: window,
        deltaX: dx,
        deltaY: dy,
        deltaMode: WheelEvent.DOM_DELTA_PIXEL,
        clientX: lastX,
        clientY: lastY
      }));
    } catch (_) {}
  };

  document.addEventListener('mousedown', function (e) {
    if (!locked) {
      lastElement = e.target;
      lastX = e.clientX;
      lastY = e.clientY;
    }
  }, true);

  document.addEventListener('mousemove', function (e) {
    if (!locked) {
      lastElement = e.target;
      lastX = e.clientX;
      lastY = e.clientY;
    }
  }, true);

  var proto = typeof Element !== 'undefined' ? Element.prototype : null;
  if (proto) {
    var originalRequest = proto.requestPointerLock;
    proto.requestPointerLock = function () {
      var ok = false;
      try {
        ok = !!window.GoimiumInput && GoimiumInput.requestPointerCapture();
      } catch (_) {}
      if (!ok) return Promise.reject(new DOMException('Pointer lock was denied', 'NotAllowedError'));
      locked = true;
      lastElement = this;
      fire('mousemove', { movementX: 0, movementY: 0, buttons: 0 });
      return Promise.resolve();
    };

    // Preserve the original implementation when it is actually available and native. We call
    // our Android implementation first because older WebView releases otherwise report the API
    // as unsupported even though Android View pointer capture can provide equivalent semantics.
    try {
      Object.defineProperty(proto.requestPointerLock, 'name', { value: 'requestPointerLock' });
    } catch (_) {}
  }

  try {
    Object.defineProperty(Document.prototype, 'pointerLockElement', {
      configurable: true,
      get: function () { return locked ? lastElement : null; }
    });
  } catch (_) {
    try {
      Object.defineProperty(document, 'pointerLockElement', {
        configurable: true,
        get: function () { return locked ? lastElement : null; }
      });
    } catch (__) {}
  }

  document.exitPointerLock = function () {
    if (!locked) return;
    locked = false;
    try { GoimiumInput.releasePointerCapture(); } catch (_) {}
    var el = lastElement;
    lastElement = null;
    try {
      document.dispatchEvent(new Event('pointerlockchange'));
    } catch (_) {}
    if (el) {
      try { el.dispatchEvent(new Event('pointerlockchange')); } catch (_) {}
    }
  };

  window.addEventListener('blur', function () {
    if (locked) document.exitPointerLock();
  });

  document.addEventListener('visibilitychange', function () {
    if (document.hidden && locked) document.exitPointerLock();
  });
})();
