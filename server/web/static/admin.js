// Hayat admin: delete confirmation and live preview. No third-party code.
(function () {
  document.querySelectorAll('form[data-confirm]').forEach(function (f) {
    f.addEventListener('submit', function (e) { if (!confirm(f.dataset.confirm)) e.preventDefault(); });
  });
  var form = document.getElementById('ann-form');
  if (!form) return;
  function val(n) { var el = form.elements[n]; return el ? el.value.trim() : ''; }
  function update() {
    document.querySelectorAll('.pv-title').forEach(function (e) { e.textContent = val('title_en') || val('title_bn') || 'Title'; });
    document.querySelectorAll('.pv-body').forEach(function (e) { e.textContent = val('body_en') || val('body_bn') || 'Message'; });
    var cta = document.querySelector('.pv-cta'), label = val('cta_label_en') || val('cta_label_bn');
    cta.textContent = label; cta.hidden = !label;
    var img = document.querySelector('.pv-img'), src = val('image_url');
    if (/^https:\/\//.test(src)) { img.src = src; img.hidden = false; } else { img.hidden = true; img.removeAttribute('src'); }
    var kind = val('kind');
    document.querySelector('.pv-banner').hidden = kind === 'notification';
    document.querySelector('.pv-note').hidden = kind === 'banner';
  }
  form.addEventListener('input', update);
  update();
})();
