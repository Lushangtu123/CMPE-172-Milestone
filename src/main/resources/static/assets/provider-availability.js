(() => {
  const form = document.getElementById('availability-form');
  if (!form) return;

  const {serviceId, startTime, endTime} = form.elements;
  function updateEndTime() {
    const duration = Number(serviceId.selectedOptions[0]?.dataset.duration);
    const start = Date.parse(startTime.value + 'Z');
    endTime.value = duration > 0 && Number.isFinite(start)
      ? new Date(start + duration * 60_000).toISOString().slice(0, 16)
      : '';
  }

  serviceId.addEventListener('change', updateEndTime);
  startTime.addEventListener('change', updateEndTime);
  form.querySelector('[aria-invalid="true"]')?.focus();
})();
