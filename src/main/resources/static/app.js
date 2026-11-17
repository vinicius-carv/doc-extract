const $ = (id) => document.getElementById(id);

function el(tag, text, cls) {
  const e = document.createElement(tag);
  if (text !== undefined) e.textContent = text; // textContent escapes HTML
  if (cls) e.className = cls;
  return e;
}

function say(text, isError) {
  $('msg').textContent = text;
  $('msg').className = isError ? 'error' : '';
}

async function load(url, options) {
  say('Loading…');
  let res;
  try {
    res = await fetch(url, options);
  } catch (e) {
    say('Network error', true);
    return null;
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    say(body.detail || 'Request failed (' + res.status + ')', true);
    return null;
  }
  say('');
  return body;
}

function showPage(doc, ev) {
  const pre = $('page');
  pre.replaceChildren();
  const page = doc.pages.find((p) => p.number === ev.page);
  if (!page || ev.start < 0) {
    $('where').textContent = 'No matching text found on any page.';
    return;
  }
  $('where').textContent = 'Page ' + page.number;
  pre.append(
    page.text.slice(0, ev.start),
    el('mark', page.text.slice(ev.start, ev.end)),
    page.text.slice(ev.end));
  pre.querySelector('mark').scrollIntoView({ block: 'center' });
}

function render(doc) {
  $('result').hidden = false;
  $('res-h').textContent = doc.title;
  $('res-h').focus();
  $('page').replaceChildren();
  $('where').textContent = 'Select a quote to see it in context.';
  const rows = $('rows');
  rows.replaceChildren();
  for (const f of doc.fields) {
    const tr = el('tr');
    tr.append(el('td', f.category.replaceAll('_', ' ').toLowerCase()), el('td', f.value ?? '—'));
    const quote = el('td');
    const status = el('td');
    if (f.evidence.length === 0) {
      quote.textContent = '—';
      status.textContent = 'No evidence';
    }
    for (const ev of f.evidence) {
      const b = el('button', '“' + ev.claimed.quote + '”');
      b.type = 'button';
      b.addEventListener('click', () => showPage(doc, ev));
      quote.append(b, document.createElement('br'));
      status.append(el('span', ev.status.replace('_', ' '), 'badge ' + ev.status), document.createElement('br'));
    }
    tr.append(quote, status);
    rows.append(tr);
  }
}

async function init() {
  const docs = (await load('/api/documents')) || [];
  if (docs.length === 0) say('No featured results yet. Run the eval to generate them.');
  for (const d of docs) {
    const li = el('li');
    const b = el('button', d.title + ' (F1 ' + d.f1.toFixed(2) + ')');
    b.type = 'button';
    b.addEventListener('click', async () => {
      const doc = await load('/api/documents/' + encodeURIComponent(d.id));
      if (doc) render(doc);
    });
    li.append(b);
    $('docs').append(li);
  }
}

$('upload').addEventListener('submit', async (e) => {
  e.preventDefault();
  const doc = await load('/api/extract', { method: 'POST', body: new FormData(e.target) });
  if (doc) render(doc);
});

init();
