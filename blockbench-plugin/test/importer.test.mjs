import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {build} from 'esbuild';
import {compileResourcePack,readArchive,writeArchive} from '../../shared/dist/index.js';

test('Blockbench import restores project UV size and disabled faces',async()=>{
  const pkg=await readArchive(await readFile('tests/fixtures/minimal.paperexport'));
  pkg.model.texture_size=[32,16];
  const head=pkg.model.bones.find(bone=>bone.id==='head');
  const cube=head.cubes[0];
  cube.faces.north.uv=[8,0,16,16];
  delete cube.faces.east;
  for(const [path,data] of compileResourcePack(pkg.manifest,pkg.model,pkg.files))pkg.files.set(path,data);
  const bytes=await writeArchive(pkg);

  const built=await build({entryPoints:['blockbench-plugin/src/importer.ts'],bundle:true,platform:'node',format:'esm',write:false});
  const url=`data:text/javascript;base64,${Buffer.from(built.outputFiles[0].contents).toString('base64')}`;
  const {openPackage}=await import(url);
  const cubes=[];
  globalThis.Project={uuid:'uv-import-test',texture_width:16,texture_height:16};
  globalThis.Texture=class {
    constructor(options){this.name=options.name;this.uuid=`texture-${options.name}`;}
    fromDataURL(){return this;}
    add(){return this;}
  };
  globalThis.Group=class {
    constructor(options){Object.assign(this,options);this.uuid=`group-${options.name}`;}
    addTo(){return this;}
    init(){return this;}
  };
  globalThis.Cube=class {
    constructor(options){Object.assign(this,options);cubes.push(this);}
    addTo(){return this;}
    init(){return this;}
  };
  globalThis.Animation=class {
    constructor(options){Object.assign(this,options);}
    add(){return this;}
    getBoneAnimator(){return {addKeyframe(){}};}
  };
  globalThis.Canvas={updateAllBones(){}};
  globalThis.Blockbench={showQuickMessage(){}};
  const format={new(){Project.texture_width=16;Project.texture_height=16;}};
  await openPackage(bytes,format);
  assert.deepEqual([Project.texture_width,Project.texture_height],[32,16]);
  const imported=cubes.find(value=>value.name===cube.name);
  assert.deepEqual(imported.faces.north.uv,[16,0,32,16]);
  assert.equal(imported.faces.east.enabled,false);
  assert.equal(imported.faces.east.texture,null);
});

test('Blockbench export uses project UV size even when the PNG is smaller',async()=>{
  const fixture=await readArchive(await readFile('tests/fixtures/minimal.paperexport'));
  const png=fixture.files.get('textures/skin.png');
  const built=await build({entryPoints:['blockbench-plugin/src/convert.ts'],bundle:true,platform:'node',format:'esm',write:false});
  const url=`data:text/javascript;base64,${Buffer.from(built.outputFiles[0].contents).toString('base64')}`;
  const {captureProject}=await import(url);
  const group={uuid:'group-uv-export',name:'Head',parent:null,origin:[0,8,0],rotation:[0,0,0]};
  globalThis.Format={id:'paperexport'};
  globalThis.Project={uuid:'uv-export-test',texture_width:32,texture_height:16};
  globalThis.Texture={all:[{uuid:'texture-uv-export',name:'skin.png',width:16,height:16,
    canvas:{toDataURL:()=>`data:image/png;base64,${Buffer.from(png).toString('base64')}`}}]};
  globalThis.Group={all:[group]};
  globalThis.Cube={all:[{name:'Head cube',from:[0,0,0],to:[8,8,8],parent:group,rotation:[0,0,0],
    faces:{north:{enabled:true,texture:'texture-uv-export',uv:[16,0,32,16],rotation:0}}}]};
  globalThis.Animation={all:[]};
  const exported=captureProject();
  assert.deepEqual(exported.model.texture_size,[32,16]);
  assert.deepEqual(exported.model.bones[0].cubes[0].faces.north.uv,[8,0,16,16]);
  const source=Cube.all[0];source.rotation=[15,35,20];source.origin=[4,4,4];source.inflate=.25;
  delete source.faces.north.enabled;
  const rotated=captureProject();
  const child=rotated.model.bones.find(b=>b.parent===rotated.model.bones[0].id);
  assert.deepEqual(child.rotation,[15,35,20]);assert.deepEqual(child.pivot,[4,4,4]);
  assert.deepEqual(child.cubes[0].from,[-.25,-.25,-.25]);assert.deepEqual(child.cubes[0].to,[8.25,8.25,8.25]);
  assert.deepEqual(child.cubes[0].faces.north.uv,[8,0,16,16]);
  source.inflate=0;source.to=[8,8,0];
  const plane=captureProject().model.bones.find(b=>b.parent).cubes[0];
  assert.equal(plane.to[2]-plane.from[2],.01);
});

test('package round trip keeps custom events, priority, bone scale and named hitboxes',async()=>{
  const pkg=await readArchive(await readFile('tests/fixtures/minimal.paperexport'));
  pkg.model.bones[0].scale=[1.2,.8,1.1];
  pkg.animations.attack.priority=77;
  pkg.animations.idle.priority=-1;
  pkg.animations.attack.events=[{time:0,type:'custom_event',data:{key:'sample:strike',damage:12}},{time:.25,type:'sound',data:{key:'minecraft:entity.zombie.ambient'}}];
  const built=await build({stdin:{contents:"export {openPackage} from './blockbench-plugin/src/importer.ts'; export {captureProject} from './blockbench-plugin/src/convert.ts';",resolveDir:process.cwd()},bundle:true,platform:'node',format:'esm',write:false});
  const {openPackage,captureProject}=await import(`data:text/javascript;base64,${Buffer.from(built.outputFiles[0].contents).toString('base64')}`);
  globalThis.Format={id:'paperexport'};globalThis.Project={uuid:'roundtrip'};
  globalThis.Texture=class {static all=[];constructor(o){Object.assign(this,o);this.uuid=o.name;this.width=16;this.height=16;Texture.all.push(this);}fromDataURL(url){this.canvas={toDataURL:()=>url};return this;}add(){return this;}};
  globalThis.Group=class {static all=[];constructor(o){Object.assign(this,o);this.uuid=o.name;Group.all.push(this);}addTo(parent){this.parent=parent;return this;}init(){return this;}};
  globalThis.Cube=class {static all=[];constructor(o){Object.assign(this,{rotation:[0,0,0]},o);Cube.all.push(this);}addTo(parent){this.parent=parent;return this;}init(){return this;}};
  globalThis.Animation=class {static all=[];constructor(o){Object.assign(this,o);this.animators={};Animation.all.push(this);}add(){return this;}getBoneAnimator(g){const a=this.animators[g.uuid]??={position:[],rotations:[],scale:[]};a.addKeyframe=k=>a[k.channel==='rotation'?'rotations':k.channel].push(k);return a;}};
  globalThis.Canvas={updateAllBones(){}};globalThis.Blockbench={showQuickMessage(){}};
  await openPackage(await writeArchive(pkg),{new(){}});
  const result=captureProject();
  assert.deepEqual(result.model.bones[0].scale,pkg.model.bones[0].scale);
  assert.deepEqual(result.model.hitboxes.map(h=>h.id),pkg.model.hitboxes.map(h=>h.id));
  assert.equal(result.animations.attack.priority,77);
  assert.equal(result.animations.idle.priority,-1);
  for(const [name,a] of Object.entries(pkg.animations))assert.deepEqual(result.animations[name].events,a.events);
});
