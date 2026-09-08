(() => {
  const mobile = () => window.matchMedia('(max-width: 760px)').matches;
  const setView = (view) => { if (mobile()) document.body.dataset.mobileView = view; };
  const sync = () => { if (mobile()) document.body.dataset.mobileView ||= 'list'; else delete document.body.dataset.mobileView; };
  document.addEventListener('click', (event) => {
    if (!mobile()) return;
    if (event.target.closest('#mobileBack')) { event.preventDefault(); setView('list'); document.querySelector('#searchInput')?.blur(); return; }
    if (event.target.closest('.chat-row[data-name], .chat-row[data-user]')) { setView('chat'); return; }
    if (event.target.closest('#newChat, #contactsBtn')) return;
    if (event.target.closest('#chatsNav')) setView('list');
  }, true);
  window.addEventListener('resize', sync, {passive:true});
  window.addEventListener('orientationchange', sync, {passive:true});
  if (window.visualViewport) {
    const resize = () => document.documentElement.style.setProperty('--vvh', `${window.visualViewport.height}px`);
    window.visualViewport.addEventListener('resize', resize, {passive:true}); resize();
  }
  sync();
})();
