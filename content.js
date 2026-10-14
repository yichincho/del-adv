(() => {
  if (window.top !== window) return;
  const seen = new WeakSet();
  const hidden = new Set();
  let counter = 0, scheduled = false, running = false;
  const selectors = 'article,aside,[role="complementary"],[data-testid*="post"],[class*="sponsor"],[class*="advert"],[id*="advert"]';
  const obvious = /(^|[\\s:：])(?:廣告|贊助|sponsored|advertisement|promoted)(?=$|[\\s:：])/i;
  function hide(node, reason) {
    if (!node || node === document.body || node === document.documentElement) return;
    node.dataset.delAdvHidden = reason;
    node.style.setProperty('display','none','important');
    hidden.add(node);
  }
  async function scan() {
    if (running) return;
    running = true;
    try {
      const settings = await chrome.storage.local.get({enabled:true,apiKey:'',threshold:0.92,mode:'safe'});
      if(!settings.enabled)return;
      const pending=[];
      document.querySelectorAll(selectors).forEach(el=>{
        if(seen.has(el) || el.dataset.delAdvHidden) return;
        seen.add(el);
        const text=(el.innerText||'').trim().slice(0,500);
        if (!text || text.length<8 || text.length>500) return;
        const label=[el.getAttribute('aria-label'),el.getAttribute('data-testid'),el.className].filter(x=>typeof x==='string').join(' ').slice(0,150);
        const header=text.slice(0,120);
        if (obvious.test(header) || /sponsor|advert|promoted/i.test(label)) {
          hide(el,'rule');
        } else if(settings.apiKey && pending.length<8 && text.length>25) {
          const id='candidate'+(++counter);
          pending.push({id,text,label,node:el});
        }
      });
      if(pending.length){
        const result=await chrome.runtime.sendMessage({type:'CLASSIFY',items:pending.map(({id,text,label})=>({id,text,label}))});
        if (result?.ok) for(const item of result.results) {
          if(item.probability>=result.threshold) {
            const match=pending.find(c=>c.id===item.id);
            if(match?.node.isConnected)hide(match.node,'ai');
          }
        }
      }
    } finally {running=false;}
  }
  function requestScan(){if(scheduled)return;scheduled=true;setTimeout(()=>{scheduled=false;scan();},800);}
  const observer=new MutationObserver(requestScan);
  observer.observe(document.documentElement,{childList:true,subtree:true});
  chrome.storage.onChanged.addListener((changes,area)=>{
    if(area==='local' && changes.enabled?.newValue===false){
      for(const el of hidden){el.style.removeProperty('display');delete el.dataset.delAdvHidden;}
      hidden.clear();
    }
    requestScan();
  });
  requestScan();
})();