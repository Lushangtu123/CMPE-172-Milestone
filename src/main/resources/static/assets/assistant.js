(() => {
  const form = document.getElementById('chat-form');
  const error = document.getElementById('agent-error');
  const log = document.getElementById('conversation');
  const status = document.getElementById('agent-status');
  const send = document.getElementById('send');
  const welcome = document.getElementById('assistant-welcome');
  const proposal = document.getElementById('proposal');
  let pending = null;
  let busy = false;
  function lock(value, label) {
    busy = value;
    for (const button of document.querySelectorAll('main button')) button.disabled = value;
    form.setAttribute('aria-busy', String(value));
    send.textContent = value ? label : 'Send message';
    status.textContent = value ? (label === 'Thinking…' ? 'Waiting for the AI service. Checking live availability or policies may take a few seconds.' : label) : '';
  }
  async function request(path, fields = null) {
    const controller = new AbortController();
    const deadline = setTimeout(() => controller.abort(), path === 'chat' ? 45000 : 10000);
    const options = {credentials: 'same-origin', signal: controller.signal};
    if (fields !== null) {
      options.method = 'POST';
      options.headers = {'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': form.querySelector('[name="_csrf"]').value};
      options.body = new URLSearchParams(fields);
    }
    try {
      const response = await fetch('/api/customer/ai/' + path, options);
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || 'Unable to complete this request.');
      return data;
    } catch (e) {
      if (e.name === 'AbortError') {
        const timeout = new Error(path === 'chat'
          ? 'The AI is taking too long. Please try again in a moment.'
          : 'The request timed out. Check My appointments before retrying a confirmed action.');
        timeout.timedOut = true;
        throw timeout;
      }
      throw e;
    } finally {clearTimeout(deadline);}
  }
  function render(data) {
    log.replaceChildren();
    if (welcome) welcome.hidden = (data.messages || []).length > 0;
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
  async function action(callback, label = 'Working…') {
    if (busy) return;
    lock(true, label); error.textContent = '';
    try { await callback(); } catch (e) {error.textContent = e.message;} finally {lock(false);}
  }
  form.addEventListener('submit', event => {event.preventDefault();action(async () => {
    const message = document.getElementById('message');
    try {
      const data = await request('chat', {message: message.value}); render(data); message.value = '';
    } catch (e) {
      // A timed-out chat may still hold the server session lock. Do not wait on history.
      if (e.timedOut) {pending = null; proposal.hidden = true;}
      else {render(await request('history'));}
      throw e;
    }
  }, 'Thinking…');});
  document.getElementById('confirm').addEventListener('click', () => action(async () => {
    if (!pending) return;
    try {await request('confirm', {token: pending.token});}
    finally {render(await request('history'));}
  }));
  for (const name of ['discard','reset']) document.getElementById(name).addEventListener('click', () => action(async () => render(await request(name, {}))));
  for (const prompt of document.querySelectorAll('[data-prompt]')) {
    prompt.addEventListener('click', () => {
      if (busy) return;
      const message = document.getElementById('message');
      message.value = prompt.getAttribute('data-prompt');
      message.focus();
    });
  }
  action(async () => render(await request('history')));
})();
