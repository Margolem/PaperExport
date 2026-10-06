import type {Bone, Manifest, Model, PaperPackage} from './types.js';
import {PackageError} from './validate.js';

const encode=(v:unknown)=>new TextEncoder().encode(JSON.stringify(v,null,2)+'\n');
export const itemModelId=(manifest:Manifest,bone:Bone)=>`${manifest.id.split(':')[0]}:paperexport/${manifest.id.split(':')[1]}/${bone.id}`;
export const soundEventId=(manifest:Manifest,name:string)=>`${manifest.id.split(':')[0]}:paperexport.${manifest.id.split(':')[1]}.${name}`;
/** Compile one model per bone, with all of that bone's cubes in one model. */
export function compileResourcePack(manifest:Manifest,model:Model,textures:Map<string,Uint8Array>):Map<string,Uint8Array> {
  const [ns,id]=manifest.id.split(':'); const files=new Map<string,Uint8Array>();
  files.set('resourcepack/pack.mcmeta',encode({pack:{min_format:[75,0],max_format:[75,0],description:`PaperExport ${manifest.id}`}}));
  for(const [name,ref] of Object.entries(manifest.textures)) {
    const data=textures.get(ref.path);if(!data)throw new PackageError(`Missing texture ${name}`);
    files.set(`resourcepack/assets/${ns}/textures/item/paperexport/${id}/${name}.png`,data);
  }
  if(manifest.sounds && Object.keys(manifest.sounds).length){
    const definitions:Record<string,unknown>={};
    for(const [name,ref] of Object.entries(manifest.sounds)){
      const data=textures.get(ref.path);if(!data)throw new PackageError(`Missing sound ${name}`);
      files.set(`resourcepack/assets/${ns}/sounds/paperexport/${id}/${name}.ogg`,data);
      definitions[`paperexport.${id}.${name}`]={sounds:[{name:`${ns}:paperexport/${id}/${name}`,stream:false}]};
    }
    files.set(`resourcepack/assets/${ns}/sounds.json`,encode(definitions));
  }
  for(const bone of model.bones) {
    if(bone.cubes.length===0)continue;
    const tex:Record<string,string>={};for(const name of Object.keys(manifest.textures))tex[name]=`${ns}:item/paperexport/${id}/${name}`;
    const elements=bone.cubes.map(c=>{
      const from=c.from.map((v,i)=>v-bone.pivot[i]+8),to=c.to.map((v,i)=>v-bone.pivot[i]+8);
      if([...from,...to].some(v=>v< -16||v>32))throw new PackageError(`Cube '${c.name}' in bone '${bone.id}' exceeds Minecraft model bounds after pivot conversion`);
      const faces:Record<string,unknown>={};for(const [side,face] of Object.entries(c.faces))if(face)faces[side]={uv:face.uv,texture:`#${face.texture}`,...(face.rotation?{rotation:face.rotation}:{})};
      return {from,to,faces};
    });
    const base=`resourcepack/assets/${ns}`;
    files.set(`${base}/models/entity/${id}/${bone.id}.json`,encode({textures:tex,elements,display:{fixed:{translation:[0,0,0],rotation:[0,0,0],scale:[1,1,1]}}}));
    files.set(`${base}/items/paperexport/${id}/${bone.id}.json`,encode({model:{type:'minecraft:model',model:`${ns}:entity/${id}/${bone.id}`}}));
  }
  return files;
}
export function packAssets(pkg:PaperPackage):Map<string,Uint8Array> {
  const out=new Map<string,Uint8Array>();for(const [path,data] of pkg.files)if(path.startsWith('resourcepack/'))out.set(path,data);return out;
}
