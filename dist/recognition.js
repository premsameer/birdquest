/* BirdNET Live browser inference adapter. Audio never leaves the device. */
(()=>{
 const ROOT='models';
 const TF='vendor/tf.min.js';
 let busy=false,worker,ready,resolveReady,rejectReady,pending,progress=()=>{},loadTimer;
 function fail(message){clearTimeout(loadTimer);rejectReady?.(new Error(message));pending?.reject(new Error(message));pending=null;worker?.terminate();worker=null;ready=null;}
 function initialize(){if(ready)return ready;ready=new Promise((resolve,reject)=>{resolveReady=resolve;rejectReady=reject;});try{worker=new Worker(`birdnet-worker.js?root=${encodeURIComponent(ROOT)}&tf=${encodeURIComponent(TF)}&lang=en_us`);loadTimer=setTimeout(()=>fail('BirdNET download timed out. Check your connection and retry.'),120000);worker.onerror=()=>fail('BirdNET could not start. Check your connection and WebGL browser support.');worker.onmessage=({data})=>{if(data.message==='error'){fail('BirdNET could not process this audio. Please retry.');return}if(data.message==='loaded'){clearTimeout(loadTimer);resolveReady();return}if(data.progress!=null)progress(`Loading BirdNET… ${data.progress}%`);if(data.message==='segments'&&pending){const strongest=new Map();for(const segment of data.segments)for(const p of segment.preds){if(!Number.isFinite(p.confidence)||p.confidence<.5||!p.scientificName?.includes(' '))continue;const prev=strongest.get(p.scientificName);if(!prev||p.confidence>prev.confidence)strongest.set(p.scientificName,p)}pending.matches=[...strongest.values()].sort((a,b)=>b.confidence-a.confidence).slice(0,5)}if(data.message==='pooled'&&pending){pending.resolve(pending.matches||[]);pending=null}}}catch{fail('BirdNET needs a browser with Web Worker and WebGL support.')}return ready||Promise.reject(new Error('Could not start BirdNET.'));}
 async function decode(blob){const Context=window.AudioContext||window.webkitAudioContext;if(!Context)throw new Error('Audio decoding is unavailable in this browser.');let context=new Context();try{const decoded=await context.decodeAudioData(await blob.arrayBuffer());if(decoded.duration<3)throw new Error('Record at least 3 seconds of birdsong.');if(decoded.duration>15)throw new Error('Please record a shorter call, up to 12 seconds.');const offline=new OfflineAudioContext(1,Math.ceil(decoded.duration*48000),48000),source=offline.createBufferSource();source.buffer=decoded;source.connect(offline.destination);source.start();const rendered=await offline.startRendering();return rendered.getChannelData(0).slice();}finally{await context.close();}}
 window.BirdRecognition={async identify(blob,onProgress=()=>{}){
  if(busy)throw new Error('An identification is already running.');
  busy=true;
  try{
   progress=onProgress;
   const pcm=await decode(blob);let energy=0;for(const v of pcm)energy+=v*v;
   if(Math.sqrt(energy/pcm.length)<.0001)return [];
   await initialize();progress('Listening for species in your recording…');
   return await new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>fail('Identification timed out. Try again on a WebGL-capable device.'),60000);
    pending={resolve:v=>{clearTimeout(timer);resolve(v)},reject:e=>{clearTimeout(timer);reject(e)}};
    try{worker.postMessage({message:'predict',pcmAudio:pcm,sensitivity:1,overlapSec:0},[pcm.buffer]);}
    catch{fail('Could not send audio to BirdNET. Please retry.');}
   });
  }finally{busy=false;}
 }};
})();
