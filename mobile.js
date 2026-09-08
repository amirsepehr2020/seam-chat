(() => {
  const isMobile = () => window.matchMedia('(max-width: 760px)').matches;

  document.addEventListener('click', (event) => {
    if (!isMobile()) return;

    const row = event.target.closest('.chat-row[data-name], .chat-row[data-user]');
    if (row) {
      document.body.dataset.mobileView = 'chat';
      return;
    }

    const back = event.target.closest('#mobileBack, #chatSearch');
    if (back) {
      event.preventDefault();
      event.stopImmediatePropagation();
      document.querySelectorAll('.chat-row.selected').forEach((item) => item.classList.remove('selected'));
      document.body.dataset.mobileView = 'list';
      document.querySelector('#searchInput')?.blur();
      return;
    }
  }, true);

  window.addEventListener('resize', () => {
    if (!isMobile()) delete document.body.dataset.mobileView;
  });
})();
