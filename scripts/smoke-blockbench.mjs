import {spawn} from 'node:child_process';
import {mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {readFile} from 'node:fs/promises';
import {readArchive} from '../shared/dist/index.js';
const binary=resolve(process.env.LOCALAPPDATA??'', 'Programs/Blockbench/Blockbench.exe');
const profile=resolve('.cache/blockbench-smoke');await mkdir(profile,{recursive:true});
const child=spawn(binary,[`--user-data-dir=${profile}`,'--remote-debugging-port=9231','--no-first-run'],{detached:true,stdio:'ignore',windowsHide:true});child.unref();
const wait=ms=>new Promise(r=>setTimeout(r,ms));let target;
try{
  for(let attempt=0;attempt<60;attempt++){try{const response=await fetch('http://127.0.0.1:9231/json/list');const list=await response.json();target=list.find(x=>x.type==='page'&&x.webSocketDebuggerUrl);if(target)break;}catch{}await wait(500);}
  if(!target)throw new Error('Blockbench debug page did not start');
  const ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
  let seq=0;const pending=new Map();ws.onmessage=event=>{const data=JSON.parse(event.data);if(data.id&&pending.has(data.id)){const {resolve,reject}=pending.get(data.id);pending.delete(data.id);data.error?reject(new Error(data.error.message)):resolve(data.result);}};
  const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
  for(let attempt=0;attempt<60;attempt++){const result=await send('Runtime.evaluate',{expression:'typeof Blockbench !== "undefined" && typeof Plugins !== "undefined" && !!Plugins.registered',returnByValue:true});if(result.result.value)break;await wait(500);}
  const path=resolve('dist/paperexport.js').replaceAll('\\','/');
  const script=`(async()=>{const p=new Plugin();await p.loadFromFile({path:${JSON.stringify(path)},name:'paperexport.js',content:''},false);return {format:!!Formats.paperexport,codec:!!Codecs.paperexport,panel:!!Panels.paper_entity,plugin:!!Plugins.registered.paperexport};})()`;
  const result=await send('Runtime.evaluate',{expression:script,awaitPromise:true,returnByValue:true});
  if(result.exceptionDetails)throw new Error(result.exceptionDetails.text);
  console.log(JSON.stringify(result.result.value));
  if(!result.result.value?.format||!result.result.value?.codec||!result.result.value?.plugin)process.exitCode=1;
  const fixture=await readArchive(await readFile('tests/fixtures/minimal.paperexport'));
  const png=Buffer.from(fixture.files.get('textures/dummy.png')).toString('base64');
  const setup=`(()=>{window.__pe_export=null;window.__pe_error=null;Blockbench.export=options=>{window.__pe_export=options.content;};Blockbench.showMessageBox=options=>{window.__pe_error=options.message;};Screencam.cleanCanvas=(options,callback)=>callback('data:image/png;base64,${png}');Formats.paperexport.new();const texture=new Texture({name:'dummy.png'}).fromDataURL('data:image/png;base64,${png}').add();const group=new Group({name:'Body',origin:[0,0,0],rotation:[0,0,0]}).addTo().init();const faces={};for(const side of ['north','south','east','west','up','down'])faces[side]={texture:texture.uuid,uv:[0,0,16,16]};new Cube({name:'Body cube',from:[-2,0,-2],to:[2,8,2],faces}).addTo(group).init();const anim=new Animation({name:'idle',length:1,loop:'loop'}).add();anim.getBoneAnimator(group).addKeyframe({channel:'rotation',time:0,data_points:[{x:0,y:0,z:0}]});return {format:Format.id,groups:Group.all.length,cubes:Cube.all.length};})()`;
  const setupResult=await send('Runtime.evaluate',{expression:setup,returnByValue:true});if(setupResult.exceptionDetails)throw new Error(setupResult.exceptionDetails.text);
  await wait(1000);
  const dimensions=await send('Runtime.evaluate',{expression:'({width:Texture.all[0].width,height:Texture.all[0].height,canvas:Texture.all[0].canvas?.width})',returnByValue:true});console.log(JSON.stringify(dimensions.result.value));
  await send('Runtime.evaluate',{expression:'Codecs.paperexport.export()'});
  let encoded=null;
  for(let attempt=0;attempt<50;attempt++){const poll=await send('Runtime.evaluate',{expression:`({error:window.__pe_error,zip:window.__pe_export?btoa(String.fromCharCode(...window.__pe_export)):null})`,returnByValue:true});if(poll.result.value.error)throw new Error(poll.result.value.error);if(poll.result.value.zip){encoded=poll.result.value.zip;break;}await wait(200);}
  if(!encoded)throw new Error('Blockbench export produced no archive');
  const exported=await readArchive(Buffer.from(encoded,'base64'));
  console.log(JSON.stringify({exported:exported.manifest.id,bones:exported.model.bones.length,animations:Object.keys(exported.animations)}));
  ws.close();
}finally{spawn('taskkill',['/PID',String(child.pid),'/T','/F'],{stdio:'ignore',windowsHide:true});}
