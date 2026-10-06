import JSZip from 'jszip';
import {MAX, PackageError, safePath, validateAnimation, validateEntity, validateManifest, validateModel} from './validate.js';
import type {EntityAnimation, EntityDefinition, Manifest, Model, PaperPackage} from './types.js';

const utf8 = new TextDecoder('utf-8', {fatal: true});
const bytes = (s: string) => new TextEncoder().encode(s);
const json = (v: unknown) => bytes(JSON.stringify(v, null, 2) + '\n');
const u16 = (d: DataView, i: number) => d.getUint16(i,true);
const u32 = (d: DataView, i: number) => d.getUint32(i,true);

/** Checks the central directory before a ZIP library expands any file data. */
export function preflightArchive(input: Uint8Array): string[] {
  if (input.byteLength > MAX.archive || input.byteLength < 22) throw new PackageError('Archive is empty or exceeds 32 MiB');
  const view = new DataView(input.buffer,input.byteOffset,input.byteLength);
  let eocd = -1;
  for (let i=input.byteLength-22; i>=Math.max(0,input.byteLength-65557); i--) if (u32(view,i)===0x06054b50 && i+22+u16(view,i+20)===input.byteLength) {eocd=i;break;}
  if (eocd<0 || u16(view,eocd+4)!==0 || u16(view,eocd+6)!==0) throw new PackageError('Corrupt or multi-disk ZIP');
  const count=u16(view,eocd+10), size=u32(view,eocd+12), offset=u32(view,eocd+16);
  if (count>MAX.files || count===0 || count===65535 || size===0xffffffff || offset===0xffffffff || offset+size>eocd) throw new PackageError('ZIP directory exceeds limits or uses unsupported ZIP64');
  const names: string[]=[]; const seen=new Set<string>(); let pos=offset,total=0;
  for (let i=0;i<count;i++) {
    if (pos+46>offset+size || u32(view,pos)!==0x02014b50) throw new PackageError('Corrupt ZIP central directory');
    const flags=u16(view,pos+8), method=u16(view,pos+10), compressed=u32(view,pos+20), expanded=u32(view,pos+24);
    const n=u16(view,pos+28), extra=u16(view,pos+30), comment=u16(view,pos+32), local=u32(view,pos+42);
    if ((flags&1)!==0 || ![0,8].includes(method) || expanded===0xffffffff || compressed===0xffffffff || local>=offset || compressed>MAX.archive || expanded>MAX.file) throw new PackageError('ZIP entry uses unsupported compression, encryption, or exceeds limits');
    if (pos+46+n+extra+comment>offset+size) throw new PackageError('Truncated ZIP central directory');
    let name: string; try { name=utf8.decode(input.subarray(pos+46,pos+46+n)); } catch { throw new PackageError('Invalid UTF-8 ZIP path'); }
    if (name.endsWith('/')) { if(!safePath(name.slice(0,-1)))throw new PackageError(`Unsafe ZIP directory: ${name}`);pos+=46+n+extra+comment;continue; }
    if (!safePath(name) || seen.has(name.toLowerCase())) throw new PackageError(`Unsafe or duplicate ZIP path: ${name}`);
    if (name.match(/\.(?:jar|class|dll|exe|bat|cmd|ps1|sh|js|mjs|cjs|py|so|dylib)$/i)) throw new PackageError(`Executable content is forbidden: ${name}`);
    seen.add(name.toLowerCase()); names.push(name); total+=expanded;
    if (total>MAX.expanded) throw new PackageError('Expanded archive exceeds 96 MiB');
    pos+=46+n+extra+comment;
  }
  if (pos!==offset+size) throw new PackageError('ZIP directory size mismatch');
  return names;
}
export function pngDimensions(data: Uint8Array): [number,number] {
  if (data.length<24 || ![137,80,78,71,13,10,26,10].every((v,i)=>data[i]===v) || new DataView(data.buffer,data.byteOffset).getUint32(12)!==0x49484452) throw new PackageError('Invalid PNG texture');
  const view=new DataView(data.buffer,data.byteOffset); const w=view.getUint32(16),h=view.getUint32(20);
  if (w<1 || h<1 || w>MAX.texture || h>MAX.texture) throw new PackageError(`PNG dimensions ${w}x${h} exceed limits`);
  return [w,h];
}
function allowedEntry(path:string):boolean {
  return path==='manifest.json'||path==='model/model.json'||path==='entity/entity.json'||path==='resourcepack/pack.mcmeta'||path==='metadata/export.json'||
    /^animations\/[a-z0-9_.-]+\.json$/.test(path)||/^textures\/[a-z0-9_.-]+\.png$/.test(path)||/^sounds\/[a-z0-9_.-]+\.ogg$/.test(path)||/^preview\/[a-z0-9_.-]+\.png$/.test(path)||
    /^resourcepack\/assets\/[a-z0-9_./-]+\.(?:json|png|ogg|mcmeta)$/.test(path);
}
export async function readArchive(input: Uint8Array): Promise<PaperPackage> {
  const names=preflightArchive(input); const zip=await JSZip.loadAsync(input,{checkCRC32:true,createFolders:false});
  const files=new Map<string,Uint8Array>();
  for (const name of names) {
    if(!allowedEntry(name))throw new PackageError(`Unexpected archive entry: ${name}`);
    const entry=zip.file(name); if (!entry) throw new PackageError(`Missing ZIP entry: ${name}`);
    const data=await entry.async('uint8array'); if (data.length>MAX.file) throw new PackageError(`Entry too large: ${name}`); files.set(name,data);
  }
  const parse=(path:string):unknown=>{const data=files.get(path);if(!data)throw new PackageError(`Missing ${path}`);if(data.length>MAX.json)throw new PackageError(`JSON too large: ${path}`);try{return JSON.parse(utf8.decode(data));}catch{throw new PackageError(`Invalid JSON: ${path}`);}};
  const manifest=parse('manifest.json'); validateManifest(manifest);
  const model=parse(manifest.model); validateModel(model,new Set(Object.keys(manifest.textures)));
  const entity=parse(manifest.entity); validateEntity(entity,new Set(Object.keys(manifest.sounds??{})),new Set(Object.keys(manifest.animations)));
  const animations:Record<string,EntityAnimation>={};
  for(const [name,path] of Object.entries(manifest.animations)) {const a=parse(path);validateAnimation(a,name,new Set(model.bones.map(b=>b.id)));animations[name]=a;}
  for(const [name,ref] of Object.entries(manifest.textures)) {
    const data=files.get(ref.path);if(!data)throw new PackageError(`Missing texture: ${ref.path}`);
    const [w,h]=pngDimensions(data);if(w!==ref.width||h!==ref.height)throw new PackageError(`Texture dimensions differ from manifest: ${name}`);
  }
  for(const [name,ref] of Object.entries(manifest.sounds??{})){
    const data=files.get(ref.path);if(!data||data.length<32||data.length>8*1024*1024||String.fromCharCode(...data.subarray(0,4))!=='OggS')throw new PackageError(`Invalid or missing Ogg sound: ${name}`);
  }
  const [ns,id]=manifest.id.split(':');
  const base=`resourcepack/assets/${ns}/`;
  for(const path of names)if(path.startsWith('resourcepack/assets/') && path!==`${base}sounds.json` && ![`${base}items/paperexport/${id}/`,`${base}models/entity/${id}/`,`${base}textures/item/paperexport/${id}/`,`${base}sounds/paperexport/${id}/`].some(prefix=>path.startsWith(prefix)))throw new PackageError(`Unexpected resource-pack namespace or path: ${path}`);
  if(!files.has('resourcepack/pack.mcmeta'))throw new PackageError('Missing resourcepack/pack.mcmeta');
  for(const bone of model.bones)if(bone.cubes.length){
    for(const path of [`resourcepack/assets/${ns}/items/paperexport/${id}/${bone.id}.json`,`resourcepack/assets/${ns}/models/entity/${id}/${bone.id}.json`])if(!files.has(path))throw new PackageError(`Missing resource-pack model: ${path}`);
  }
  for(const name of Object.keys(manifest.textures))if(!files.has(`resourcepack/assets/${ns}/textures/item/paperexport/${id}/${name}.png`))throw new PackageError(`Missing resource-pack texture: ${name}`);
  for(const name of Object.keys(manifest.sounds??{}))if(!files.has(`resourcepack/assets/${ns}/sounds/paperexport/${id}/${name}.ogg`))throw new PackageError(`Missing resource-pack sound: ${name}`);
  if(Object.keys(manifest.sounds??{}).length&&!files.has(`${base}sounds.json`))throw new PackageError('Missing resource-pack sounds.json');
  for(const name of names) if(name.startsWith('resourcepack/') && !name.startsWith('resourcepack/assets/') && name!=='resourcepack/pack.mcmeta') throw new PackageError(`Unexpected resource-pack path: ${name}`);
  return {manifest,model,entity,animations,files};
}
export async function writeArchive(pkg: Omit<PaperPackage,'files'> & {files:Map<string,Uint8Array>}):Promise<Uint8Array> {
  validateManifest(pkg.manifest); validateModel(pkg.model,new Set(Object.keys(pkg.manifest.textures)));validateEntity(pkg.entity,new Set(Object.keys(pkg.manifest.sounds??{})),new Set(Object.keys(pkg.manifest.animations)));
  const zip=new JSZip();const all=new Map(pkg.files);
  all.set('manifest.json',json(pkg.manifest));all.set(pkg.manifest.model,json(pkg.model));all.set(pkg.manifest.entity,json(pkg.entity));
  for(const [name,path] of Object.entries(pkg.manifest.animations)) {const a=pkg.animations[name];validateAnimation(a,name,new Set(pkg.model.bones.map(b=>b.id)));all.set(path,json(a));}
  for(const [name,ref] of Object.entries(pkg.manifest.textures)) {const data=all.get(ref.path);if(!data)throw new PackageError(`Missing texture: ${name}`);const [w,h]=pngDimensions(data);if(w!==ref.width||h!==ref.height)throw new PackageError(`Texture dimensions differ: ${name}`);}
  for(const [name,ref] of Object.entries(pkg.manifest.sounds??{})){const data=all.get(ref.path);if(!data||data.length<32||data.length>8*1024*1024||String.fromCharCode(...data.subarray(0,4))!=='OggS')throw new PackageError(`Invalid or missing Ogg sound: ${name}`);}
  if(all.size>MAX.files)throw new PackageError('Too many files');let total=0;
  for(const [path,data] of all) {if(!safePath(path)||!allowedEntry(path)||data.length>MAX.file)throw new PackageError(`Unsafe path or oversized entry: ${path}`);total+=data.length;zip.file(path,data,{binary:true,compression:'DEFLATE'});}
  if(total>MAX.expanded)throw new PackageError('Expanded archive too large');
  const out=await zip.generateAsync({type:'uint8array',compression:'DEFLATE',compressionOptions:{level:6}});
  preflightArchive(out);return out;
}
