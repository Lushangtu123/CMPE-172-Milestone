(() => {
  const form = document.getElementById('chat-form');
  const error = document.getElementById('agent-error');
  const log = document.getElementById('conversation');
  const scrollArea = document.getElementById('chat-content');
  const status = document.getElementById('agent-status');
  const send = document.getElementById('send');
  const message = document.getElementById('message');
  const welcome = document.getElementById('assistant-welcome');
  const proposal = document.getElementById('proposal');
  const confirm = document.getElementById('confirm');
  const summary = document.getElementById('proposal-summary');
  const draftKey = form.dataset.draftKey;
  function saveDraft() {
    try {
      if (message.value) sessionStorage.setItem(draftKey, message.value);
      else sessionStorage.removeItem(draftKey);
    } catch { /* The current draft remains available when storage is blocked. */ }
  }
  try { message.value = sessionStorage.getItem(draftKey) ?? message.value; } catch {}
  message.addEventListener('input', saveDraft);
  let pending = null;
  let busy = false;
  function lock(value, label) {
    busy = value;
    for (const button of document.querySelectorAll('main button')) button.disabled = value;
    form.setAttribute('aria-busy', String(value));
    send.textContent = value ? label : 'Send message';
    status.textContent = value ? (label === 'Thinking…' ? 'Waiting for the AI assistant…' : label) : '';
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
  function appendTurn(turn) {
    const article = document.createElement('article');
    article.className = 'turn ' + (turn.role === 'user' ? 'customer-turn' : 'assistant-turn');
    const label = document.createElement('strong');
    label.textContent = turn.role === 'user' ? 'You' : 'TutorConnect';
    const text = document.createElement('p');
    text.textContent = turn.text;
    article.append(label, text);
    log.append(article);
    return article;
  }
  function updateConversation() {
    welcome.hidden = log.childElementCount > 0;
    scrollArea.scrollTop = scrollArea.scrollHeight;
  }
  function render(data) {
    log.replaceChildren();
    for (const turn of data.messages || []) appendTurn(turn);
    pending = data.pending || null;
    proposal.hidden = !pending;
    if (pending) {
      const cancelling = pending.action === 'CANCEL';
      const details = pending.details;
      document.getElementById('proposal-title').textContent = cancelling
        ? 'Review cancellation' : 'Review booking';
      confirm.textContent = cancelling ? 'Confirm cancellation' : 'Confirm booking';
      document.getElementById('proposal-details').hidden = !details;
      summary.hidden = Boolean(details);
      summary.textContent = pending.summary;
      if (details) {
        document.getElementById('proposal-service').textContent = details.service;
        document.getElementById('proposal-tutor').textContent = details.tutor;
        document.getElementById('proposal-time').textContent = details.time;
      }
    }
    updateConversation();
  }
  async function action(callback, label = 'Working…') {
    if (busy) return;
    lock(true, label); error.textContent = '';
    try { await callback(); } catch (e) {error.textContent = e.message;} finally {lock(false);}
  }
  form.addEventListener('submit', event => {
    event.preventDefault();
    const draft = message.value;
    const text = draft.trim();
    if (!text) return;
    action(async () => {
      saveDraft();
      const firstExchange = log.childElementCount === 0;
      const preview = appendTurn({role: 'user', text});
      updateConversation();
      proposal.hidden = true;
      message.readOnly = true;
      message.value = '';
      try {
        render(await request('chat', {message: text}));
        saveDraft();
        if (firstExchange) document.dispatchEvent(new Event('chat-thread-saved'));
      } catch (e) {
        preview.remove();
        updateConversation();
        message.value = draft;
        pending = null;
        // A timed-out chat may still hold the server session lock.
        if (!e.timedOut) {
          try {render(await request('history'));} catch { /* Preserve the original error. */ }
        }
        throw e;
      } finally {
        message.readOnly = false;
      }
    }, 'Thinking…');
  });
  confirm.addEventListener('click', () => action(async () => {
    if (!pending) return;
    const token = pending.token;
    pending = null;
    proposal.hidden = true;
    render(await request('confirm', {token}));
  }));
  for (const name of ['discard','reset']) document.getElementById(name).addEventListener('click', () => action(async () => render(await request(name, {}))));
  for (const prompt of document.querySelectorAll('[data-prompt]')) {
    prompt.addEventListener('click', () => {
      if (busy) return;
      message.value = prompt.getAttribute('data-prompt');
      saveDraft();
      message.focus();
    });
  }
  action(async () => render(await request('history')));
})();
