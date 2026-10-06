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
});
