(()=>{
 const BASE='https://birdquest-gateway-2cop3owhka-uc.a.run.app';
 let token=null,expires=0,signing=false;
 const status=message=>{const el=document.querySelector('#online-status');if(el)el.textContent=message};
 const valid=()=>token&&Date.now()<expires;
 window.OnlineAuth={valid,async signIn(){
  if(signing)return;signing=true;
  try{
   status('Opening secure sign-in…');const r=await fetch(BASE+'/session',{method:'POST',signal:AbortSignal.timeout(20000)});if(!r.ok)throw new Error('Sign-in service unavailable. Retry shortly.');const {session}=await r.json();
   const link=document.querySelector('#google-login');link.href=BASE+'/login#session='+encodeURIComponent(session);link.hidden=false;link.click();status('Complete Google sign-in in your browser, then return here.');
   const until=Date.now()+300000;
   while(Date.now()<until){await new Promise(resolve=>setTimeout(resolve,2500));if(document.hidden)continue;const p=await fetch(BASE+'/session/poll',{method:'POST',headers:{'X-Pairing-Key':session},signal:AbortSignal.timeout(20000)});if(!p.ok)throw new Error('Sign-in session expired. Please try again.');const data=await p.json();if(data.status==='complete'){token=data.token;expires=Date.now()+55*60000;link.hidden=true;status('Connected · Perch online recognition');return}}
   throw new Error('Sign-in timed out. Please try again.');
  }catch(e){status(e.message)}finally{signing=false}
 }};
 window.BirdRecognition={async identify(blob,onProgress=()=>{}){
  if(!valid())throw new Error('Sign in with Google above, then retry identification.');
  onProgress('Uploading recording to Perch…');const form=new FormData();form.append('file',blob,blob.type.includes('ogg')?'bird.ogg':blob.type.includes('mp4')?'bird.m4a':'bird.webm');
  const r=await fetch(BASE+'/v1/identify',{method:'POST',headers:{Authorization:'Bearer '+token},body:form,signal:AbortSignal.timeout(115000)});
  if(r.status===401){token=null;throw new Error('Sign-in expired. Sign in again above.')}if(!r.ok)throw new Error(r.status===403?'This Google account is not enabled for testing.':'Recognition unavailable ('+r.status+'). Try again.');const d=await r.json();if(d.non_animal_top_class||d.low_confidence)return [];
  if(!Array.isArray(d.predictions))throw new Error('Invalid recognition response.');
  return d.predictions.filter(p=>p.taxonomic_group==='bird'&&!p.low_confidence&&typeof p.scientific_name==='string'&&Number.isFinite(p.confidence)&&p.confidence>=0&&p.confidence<=1).slice(0,5).map(p=>({scientificName:p.scientific_name,commonName:p.common_name||p.scientific_name,confidence:p.confidence}));
 }};
})();
