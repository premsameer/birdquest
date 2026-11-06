/* Optional online provider. Activate only after deploying and validating the endpoint. */
class PerchClient {
  constructor(baseUrl){const url=new URL(baseUrl);if(url.protocol!=='https:')throw new Error('Recognition requires HTTPS.');this.baseUrl=url.href.replace(/\/$/,'');}
  async identify(blob,onProgress=()=>{}){
    if(!blob.size||blob.size>5*1024*1024)throw new Error('Record a short clip under 5 MB.');
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),90000);
    try{
      onProgress('Uploading your recording for Perch analysis…');
      const form=new FormData();form.append('file',blob,blob.type.includes('ogg')?'bird-call.ogg':'bird-call.webm');
      const response=await fetch(this.baseUrl+'/v1/identify',{method:'POST',body:form,signal:controller.signal,credentials:'omit'});
      if(!response.ok)throw new Error(response.status===429?'Recognition is busy. Please try again later.':'Online recognition is unavailable. Please retry.');
      const data=await response.json();
      if(data.non_animal_top_class||data.low_confidence)return [];
      if(!Array.isArray(data.predictions))throw new Error('Invalid recognition response.');
      return data.predictions.filter(p=>p.taxonomic_group==='bird'&&!p.low_confidence&&Number.isFinite(p.confidence)&&p.confidence>=0&&p.confidence<=1&&typeof p.scientific_name==='string').slice(0,5).map(p=>({scientificName:p.scientific_name,commonName:p.common_name||p.scientific_name,confidence:p.confidence,provider:'Perch 2.0',requestId:data.request_id}));
    }catch(error){if(error.name==='AbortError')throw new Error('Online recognition timed out. Please retry.');throw error;}finally{clearTimeout(timer);}
  }
}
