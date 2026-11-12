(() => {
  const form = document.getElementById('reminder-form');
  if (!form) return;
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button');
    const result = document.getElementById('reminder-result');
    button.disabled = true; result.textContent = 'Generating reminders…';
    try {
      const response = await fetch(form.action, {method: 'POST', credentials: 'same-origin', headers: {'X-CSRF-TOKEN': form.querySelector('[name="_csrf"]').value}});
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || 'Unable to run reminders.');
      result.textContent = `Checked ${data.scanned} appointment(s), queued ${data.enqueued} reminder(s), generation failures ${data.failed}.`;
    } catch (e) {result.textContent = e.message;} finally {button.disabled = false;}
  });
})();
