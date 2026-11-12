(() => {
  const form = document.getElementById('chat-form');
  const error = document.getElementById('agent-error');
  const log = document.getElementById('conversation');
  const proposal = document.getElementById('proposal');
  let pending = null;
  let busy = false;
  function lock(value) {
    busy = value;
    for (const button of document.querySelectorAll('main button')) button.disabled = value;
    form.setAttribute('aria-busy', String(value));
  }
  async function request(path, fields = null) {
    const options = {credentials: 'same-origin'};
    if (fields !== null) {
      options.method = 'POST';
      options.headers = {'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': form.querySelector('[name="_csrf"]').value};
      options.body = new URLSearchParams(fields);
    }
    const response = await fetch('/api/customer/ai/' + path, options);
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Unable to complete this request.');
    return data;
  }
  function render(data) {
    log.replaceChildren();
    for (const turn of data.messages || []) {
      const article = document.createElement('article'); article.className = 'turn ' + (turn.role === 'user' ? 'customer-turn' : 'assistant-turn');
      const label = document.createElement('strong'); label.textContent = turn.role === 'user' ? 'You' : 'TutorConnect';
      const text = document.createElement('p'); text.textContent = turn.text;
      article.append(label, text); log.append(article);
    }
    pending = data.pending || null;
    proposal.hidden = !pending;
    document.getElementById('proposal-summary').textContent = pending ? pending.summary : '';
    log.scrollTop = log.scrollHeight;
  }
  async function action(callback) {
    if (busy) return;
    lock(true); error.textContent = '';
    try { await callback(); } catch (e) {error.textContent = e.message;} finally {lock(false);}
  }
  form.addEventListener('submit', event => {event.preventDefault();action(async () => {
    const message = document.getElementById('message');
    try {
      const data = await request('chat', {message: message.value}); render(data); message.value = '';
    } catch (e) {render(await request('history')); throw e;}
  });});
  document.getElementById('confirm').addEventListener('click', () => action(async () => {
    if (!pending) return;
    try {await request('confirm', {token: pending.token});}
    finally {render(await request('history'));}
  }));
  for (const name of ['discard','reset']) document.getElementById(name).addEventListener('click', () => action(async () => render(await request(name, {}))));
  action(async () => render(await request('history')));
})();
