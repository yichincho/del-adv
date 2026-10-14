const API = 'https://api.typesafe.ai/v1/systemone';
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type !== 'CLASSIFY') return;
  (async () => {
    const cfg = await chrome.storage.local.get({enabled:true,apiKey:'',threshold:0.92});
    if (!cfg.enabled || !cfg.apiKey) return {ok:false,error:'請先啟用功能並設定 TypeSafe API Key'};
    const candidates = (message.items || []).slice(0, 8).filter(x=>typeof x.text==='string').map(x=>({id:x.id,text:x.text.slice(0,500),label:x.label || ''}));
    if (!candidates.length) return {ok:true,results:[]};
    const questions = Object.fromEntries(candidates.map((c,i)=>['ad'+i,{
      type:'noul',
      instructions:'Is the item identified by id a paid advertisement, sponsored promotion, or commercially promotional placement rather than ordinary editorial or user content? Be conservative: a normal product review or relevant organic result is not necessarily an ad.',
      criteria:{true:'Explicitly promotional or sponsored content',false:'Normal non-sponsored content or insufficient evidence'}
    }]));
    const body={model:'jev-latest',state:{source:'visible web page snippets',items:candidates},questions};
    let response;
    for(let attempt=0;attempt<3;attempt++){
      response=await fetch(API,{method:'POST',headers:{Authorization:'Bearer '+cfg.apiKey,'Content-Type':'application/json'},body:JSON.stringify(body)});
      if (![429,529].includes(response.status)) break;
      await pause(Math.min(2000,400*(2**attempt)));
    }
    if(!response.ok) throw new Error('TypeSafe API HTTP '+response.status);
    const data=await response.json();
    return {ok:true,results:candidates.map((item,i)=>({id:item.id,probability:data.answers?.['ad'+i]?.noul ?? 0})),threshold:Number(cfg.threshold)};
  })().then(sendResponse).catch(e=>sendResponse({ok:false,error:String(e.message||e)}));
  return true;
});
