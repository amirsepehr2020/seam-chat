const $ = (s) => document.querySelector(s);
const input = $('#messageInput');
const area = $('#messageArea');
const toast = $('#toast');

function showToast(text){ toast.textContent=text; toast.classList.add('show'); clearTimeout(window.__toast); window.__toast=setTimeout(()=>toast.classList.remove('show'),1800); }
function scrollBottom(){ area.scrollTo({top:area.scrollHeight,behavior:'smooth'}); }

$('#composer').addEventListener('submit',(e)=>{
  e.preventDefault();
  const text=input.value.trim();
  if(!text)return;
  const article=document.createElement('article');
  article.className='message sent reveal';
  article.innerHTML=`<div><p></p><time>18:43 ✓✓</time></div>`;
  article.querySelector('p').textContent=text;
  $('#typing').before(article);
  input.value='';
  scrollBottom();
  setTimeout(()=>{ showToast('Message sent ✦'); },250);
});

$('#searchInput').addEventListener('input',(e)=>{
  const q=e.target.value.toLowerCase();
  document.querySelectorAll('.chat-row').forEach(row=>row.style.display=row.dataset.name.toLowerCase().includes(q)?'grid':'none');
});

document.querySelectorAll('.chat-row').forEach(row=>row.addEventListener('click',()=>{
  document.querySelectorAll('.chat-row').forEach(r=>r.classList.remove('selected'));
  row.classList.add('selected');
  const name=row.dataset.name;
  $('#activeName').textContent=name;
  $('#detailsName').textContent=name;
  showToast(`Opened ${name}`);
}));

document.querySelectorAll('.filter').forEach(btn=>btn.addEventListener('click',()=>{
  document.querySelectorAll('.filter').forEach(b=>b.classList.remove('active'));
  btn.classList.add('active');
}));

$('#notifyToggle').addEventListener('click',(e)=>{
  e.currentTarget.classList.toggle('on');
  showToast(e.currentTarget.classList.contains('on')?'Notifications on':'Notifications off');
});
$('#newChat').addEventListener('click',()=>{ input.focus(); showToast('Start a new conversation'); });

document.addEventListener('keydown',(e)=>{
  if((e.metaKey||e.ctrlKey)&&e.key.toLowerCase()==='k'){e.preventDefault();$('#searchInput').focus();}
});

setTimeout(scrollBottom,100);
