/* 注入脚本：嗅探 m3u8，交给原生播放器 */
(function () {
  if (window.__NV__) return;
  window.__NV__ = 1;

  var RE = /\.(m3u8|mp4|flv|ts)(\?|#|$)/i;
  var found = [];
  var seen = {};

  function post(name, payload) {
    try { if (window.NiVod && window.NiVod[name]) window.NiVod[name](payload == null ? '' : String(payload)); } catch (e) {}
  }
  function isMedia(u) { return typeof u === 'string' && RE.test(u.split('#')[0]); }
  function remember(u) {
    if (!u || seen[u] || !/^https?:/i.test(u)) return;
    seen[u] = 1;
    found.push(u);
    // 优先保留 m3u8
    if (/\.m3u8/i.test(u)) post('found', u);
  }

  // 嗅探 fetch
  var _f = window.fetch;
  if (_f) window.fetch = function (i) {
    try { var u = typeof i === 'string' ? i : (i && i.url); if (isMedia(u)) remember(u); } catch (e) {}
    return _f.apply(this, arguments);
  };
  // 嗅探 XHR
  var _o = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (m, u) {
    try { if (isMedia(u)) remember(u); } catch (e) {}
    return _o.apply(this, arguments);
  };
  // 嗅探 src 赋值
  try {
    var d = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
    if (d && d.set) {
      var os = d.set;
      Object.defineProperty(HTMLMediaElement.prototype, 'src', {
        configurable: true, enumerable: d.enumerable, get: d.get,
        set: function (v) { try { if (isMedia(v)) remember(v); } catch (e) {} return os.call(this, v); }
      });
    }
  } catch (e) {}

  function pick() {
    for (var i = found.length - 1; i >= 0; i--) if (/\.m3u8/i.test(found[i])) return found[i];
    return found.length ? found[found.length - 1] : '';
  }

  function send() {
    var u = pick();
    if (!u) { post('toast', '还没嗅到视频地址，先点一下网页里的播放'); return; }
    post('play', JSON.stringify({ url: u, title: (document.title || '').slice(0, 100) }));
  }

  function addBtn() {
    if (document.getElementById('__nv_btn')) return;
    var b = document.createElement('div');
    b.id = '__nv_btn';
    b.textContent = '▶ 原生播放';
    b.style.cssText = 'position:fixed;right:14px;bottom:104px;z-index:2147483647;' +
      'padding:11px 16px;border-radius:24px;background:rgba(18,18,22,.88);color:#3ddc97;' +
      'font:600 14px/1 system-ui,sans-serif;border:1px solid #3ddc97;box-shadow:0 4px 18px rgba(0,0,0,.55)';
    b.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); send(); }, true);
    (document.body || document.documentElement).appendChild(b);
  }

  window.__nvList = function () { return JSON.stringify(found); };
  window.__nvPick = pick;
  window.__nvSend = send;

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', addBtn);
  else addBtn();
  setTimeout(addBtn, 1500);
  setTimeout(addBtn, 4000);
})();
