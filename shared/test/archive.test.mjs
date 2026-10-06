import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import JSZip from 'jszip';
import {readArchive,preflightArchive,PackageError,sample,hierarchy,compileResourcePack,simplifyKeys,validateManifest} from '../dist/index.js';

const fixture=()=>readFile('../tests/fixtures/minimal.paperexport');
test('fixture roundtrip reads bones, hitboxes, textures, and animations',async()=>{const p=await readArchive(await fixture());assert.equal(p.model.bones.length,6);assert.equal(p.model.hitboxes.length,2);assert.equal(Object.keys(p.animations).length,5);assert.equal(p.files.get('textures/skin.png')[0],137);});
test('portable and older compatibility markers are accepted',async()=>{const p=await readArchive(await fixture());assert.equal(p.manifest.paper_version,'1.21-26.3');validateManifest({...p.manifest,minecraft_version:'1.21.11',paper_version:'1.21.11'});});
test('bad ZIP is rejected',()=>assert.throws(()=>preflightArchive(new Uint8Array([1,2,3])),PackageError));
test('ZIP traversal is rejected before decompression',async()=>{const z=new JSZip();z.file('manifest.json','{}');z.file('safe.txt','x');const b=await z.generateAsync({type:'uint8array'});const needle=new TextEncoder().encode('safe.txt');for(let i=0;i<b.length-needle.length;i++)if(needle.every((v,j)=>b[i+j]===v)&&b[i-46]===0x50){b.set(new TextEncoder().encode('../x.txt'),i);break;}assert.throws(()=>preflightArchive(b),PackageError);});
test('missing texture is reported',async()=>{const original=await readArchive(await fixture());const z=new JSZip();for(const [p,b] of original.files)if(p!=='textures/skin.png')z.file(p,b);const bytes=await z.generateAsync({type:'uint8array'});await assert.rejects(()=>readArchive(bytes),/Missing texture/);});
test('newer format version is refused',async()=>{const original=await readArchive(await fixture());const z=new JSZip();for(const [p,b] of original.files)z.file(p,p==='manifest.json'?JSON.stringify({...original.manifest,format_version:2}):b);const bytes=await z.generateAsync({type:'uint8array'});await assert.rejects(()=>readArchive(bytes),/unsupported format_version/);});
test('sampling and hierarchy',async()=>{assert.deepEqual(sample([{time:0,value:[0,0,0]},{time:1,value:[10,0,0]}],.5,[0,0,0]),[5,0,0]);const p=await readArchive(await fixture());const order=hierarchy(p.model).map(b=>b.id);assert.ok(order.indexOf('body')<order.indexOf('head'));});
test('compiled pack contains item model definitions',async()=>{const p=await readArchive(await fixture());const pack=compileResourcePack(p.manifest,p.model,p.files);assert.ok(pack.has('resourcepack/assets/fixture/items/paperexport/rig/head.json'));});
test('custom boss sound is bundled and registered',async()=>{const p=await readArchive(await fixture());assert.equal(p.entity.boss.enabled,true);assert.equal(p.entity.sound_cues.spawn,'tone');assert.equal(p.files.get('sounds/tone.ogg').subarray(0,4).toString(),'79,103,103,83');const sounds=JSON.parse(new TextDecoder().decode(p.files.get('resourcepack/assets/fixture/sounds.json')));assert.equal(sounds['paperexport.rig.tone'].sounds[0].name,'fixture:paperexport/rig/tone');});
test('invalid hitbox bone is rejected',async()=>{const p=await readArchive(await fixture());p.model.hitboxes[0].bone='missing';await assert.rejects(()=>import('../dist/index.js').then(m=>m.writeArchive(p)),/invalid pe_hitbox/);});
test('lossless linear key simplification',()=>{const keys=[{time:0,value:[0,0,0]},{time:.5,value:[5,0,0]},{time:1,value:[10,0,0]}];assert.deepEqual(simplifyKeys(keys),[keys[0],keys[2]]);});
