(() => {
  const panel = document.getElementById('chat-history-panel');
  if (!panel || typeof panel.showModal !== 'function') return;

  const content = document.getElementById('history-panel-content');
  const recent = document.getElementById('recent-chats');
  const historyLink = document.querySelector('.mobile-history');
  let loading;
  let recentVersion = 0;

  async function fetchHistory(url, signal) {
    const response = await fetch(url, {signal, credentials: 'same-origin'});
    if (!response.ok || response.redirected) throw new Error('Unable to load chat history.');
    const page = new DOMParser().parseFromString(await response.text(), 'text/html');
    const history = page.getElementById('history-content');
    if (!history) throw new Error('Unable to load chat history.');
    return history;
  }

  async function openHistory(url) {
    loading?.abort();
    const request = new AbortController();
    loading = request;
    const loadingText = document.createElement('p');
    loadingText.setAttribute('role', 'status');
    loadingText.textContent = 'Loading chat history…';
    content.replaceChildren(loadingText);
    if (!panel.open) panel.showModal();
    const timeout = setTimeout(() => request.abort(), 8000);

    try {
      const history = await fetchHistory(url, request.signal);
      if (loading !== request || !panel.open) return;
      content.replaceChildren(...history.childNodes);
      content.scrollTop = 0;
      content.focus();
    } catch {
      if (loading !== request || !panel.open) return;
      const fallback = document.createElement('a');
      fallback.href = url;
      fallback.textContent = 'Open the full history page';
      loadingText.textContent = 'Chat history could not be loaded here.';
      content.append(fallback);
    } finally {
      clearTimeout(timeout);
    }
  }

  document.addEventListener('click', event => {
    const link = event.target.closest('a[href]');
    if (!link || event.defaultPrevented || event.button !== 0
        || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const inHistory = link.matches('[data-history-link]')
      || (content.contains(link) && link.matches('.history-back, .pagination a'));
    if (!inHistory) return;
    event.preventDefault();
    openHistory(link.href);
  });

  panel.addEventListener('close', () => {
    loading?.abort();
    loading = null;
  });

  document.addEventListener('chat-thread-saved', async () => {
    const version = ++recentVersion;
    try {
      const history = await fetchHistory(historyLink.href, AbortSignal.timeout(8000));
      const list = history.querySelector('.saved-chat-list');
      if (!list || version !== recentVersion) return;
      list.replaceChildren(...Array.from(list.children).slice(0, 5));
      recent.replaceChildren(list);
    } catch { /* The history panel remains available independently of the sidebar. */ }
  });
})();
