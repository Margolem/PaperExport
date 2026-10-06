import {compileResourcePack,optimizeAnimations,PackageError,validateAnimation,validateEntity,validateManifest,validateModel,type Bone,type Cube as PECube,type EntityAnimation,type HitboxRegion,type Manifest,type Model,type PaperPackage,type TextureRef,type Vec3} from '@paperexport/shared';
import {state} from './state.js';

const slug=(s:string)=>s.toLowerCase().replace(/[^a-z0-9_.-]+/g,'_').replace(/^_+|_+$/g,'') || 'part';
const vector=(v:any):Vec3=>[Number(v[0]),Number(v[1]),Number(v[2])];
function dataUrlBytes(url:string):Uint8Array {const encoded=url.split(',')[1];if(!encoded)throw new PackageError('Texture has no PNG data');const raw=atob(encoded);return Uint8Array.from(raw,c=>c.charCodeAt(0));}
function textureData(t:any):Uint8Array {return dataUrlBytes(t.canvas.toDataURL('image/png'));}
function exportAnimations(groups:Map<string,string>,id:string,soundNames:Set<string>):Record<string,EntityAnimation> {
  const out:Record<string,EntityAnimation>={};
  for(const anim of Animation.all as any[]) {
    const name=slug(anim.name);if(out[name])throw new PackageError(`Duplicate animation name ${name}`);
    const tracks:any[]=[];
    for(const [uuid,animator] of Object.entries(anim.animators) as [string,any][]) {
      const bone=groups.get(uuid);if(!bone)continue;
      const track:any={bone};
      for(const channel of ['position','rotation','scale']) {
        const frames=(channel==='rotation'?animator.rotations:animator[channel]) as any[] ?? [];
        if(!frames.length)continue;
        track[channel]=frames.map(k=>{
          if(!['linear','step'].includes(k.interpolation??'linear'))throw new PackageError(`Animation '${name}' uses ${k.interpolation} on '${bone}'. Only linear and step are portable in v1.`);
          const d=k.data_points?.[0];const value=[Number(d?.x),Number(d?.y),Number(d?.z)];
          if(value.some(v=>!Number.isFinite(v)))throw new PackageError(`Animation '${name}' has an expression or invalid key on '${bone}'`);
          return {time:Number(k.time),value,interpolation:k.interpolation??'linear'};
        }).sort((a,b)=>a.time-b.time);
      }
      if(track.position||track.rotation||track.scale)tracks.push(track);
    }
    const events:any[]=[];
    for(const k of anim.effects?.keyframes??[]){if(k.channel==='sound'){let key=String(k.data_points?.[0]?.effect??'');if(soundNames.has(key)){const [ns,entity]=id.split(':');key=`${ns}:paperexport.${entity}.${key}`;}if(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(key))events.push({time:Number(k.time),type:'sound',data:{key}});}else if(k.channel==='particle'){const particle=String(k.data_points?.[0]?.effect??'');if(/^[A-Za-z0-9_]+$/.test(particle))events.push({time:Number(k.time),type:'particle',data:{particle}});}}
    out[name]={name,length:Number(anim.length),loop:anim.loop??'once',priority:name==='death'?100:name==='hurt'?80:name==='attack'?60:['walk','run'].includes(name)?30:0,tracks,events};
  }
  return out;
}
export function captureProject():PaperPackage {
  if(Format?.id!=='paperexport')throw new PackageError('Choose File → New → PaperMC Custom Entity first');
  const s=state(),id=`${s.namespace}:${s.entityId}`;
  const textures:Record<string,TextureRef>={},files=new Map<string,Uint8Array>(),textureIds=new Map<string,string>();
  for(const t of Texture.all as any[]) {
    const name=slug(String(t.name).replace(/\.png$/i,''));if(textures[name])throw new PackageError(`Duplicate texture name ${name}`);
    const path=`textures/${name}.png`;textures[name]={path,width:t.width,height:t.height};files.set(path,textureData(t));textureIds.set(t.uuid,name);
  }
  const bones:Bone[]=[],groups=new Map<string,string>(),used=new Set<string>();
  for(const g of Group.all as any[]) {let bone=slug(g.name);let i=2;while(used.has(bone))bone=`${slug(g.name)}_${i++}`;used.add(bone);groups.set(g.uuid,bone);}
  for(const g of Group.all as any[]) {
    const parent=g.parent && groups.get(g.parent.uuid) || null;
    bones.push({id:groups.get(g.uuid)!,name:g.name,parent,pivot:vector(g.origin),rotation:vector(g.rotation),scale:[1,1,1],cubes:[]});
  }
  if(!bones.length)throw new PackageError('Add at least one group/bone to the outliner');
  const hitboxes:HitboxRegion[]=[];
  for(const cube of Cube.all as any[]) {
    if(cube.export===false)continue;
    const boneId=cube.parent && groups.get(cube.parent.uuid);if(!boneId)throw new PackageError(`Cube '${cube.name}' must be inside a group`);
    if(cube.rotation.some((v:number)=>Math.abs(v)>1e-6))throw new PackageError(`Cube '${cube.name}' is rotated. Rotate its parent bone instead.`);
    if(cube.name==='pe_hitbox') {hitboxes.push({id:`hitbox_${hitboxes.length+1}`,bone:boneId,from:vector(cube.from),to:vector(cube.to)});continue;}
    const faces:PECube['faces']={};
    for(const side of ['north','south','east','west','up','down'] as const){const f=cube.faces[side];if(!f?.enabled||!f.texture)continue;const texture=textureIds.get(typeof f.texture==='string'?f.texture:f.texture.uuid);if(!texture)throw new PackageError(`Cube '${cube.name}' references a missing texture`);
      const ref=textures[texture];faces[side]={texture,uv:[f.uv[0]*16/ref.width,f.uv[1]*16/ref.height,f.uv[2]*16/ref.width,f.uv[3]*16/ref.height],rotation:f.rotation||0};}
    bones.find(b=>b.id===boneId)!.cubes.push({name:cube.name,from:vector(cube.from),to:vector(cube.to),faces});
  }
  const model:Model={texture_size:[Project.texture_width||16,Project.texture_height||16],bones,hitboxes};
  const sounds:NonNullable<Manifest['sounds']>={};
  for(const [name,data] of Object.entries(s.sounds)){
    if(!/^[a-z0-9_.-]+$/.test(name)||data.length<32||data.length>8*1024*1024||String.fromCharCode(...data.subarray(0,4))!=='OggS')throw new PackageError(`Invalid Ogg sound: ${name}`);
    const path=`sounds/${name}.ogg`;sounds[name]={path};files.set(path,data);
  }
  const animations=exportAnimations(groups,id,new Set(Object.keys(sounds)));
  optimizeAnimations(animations);
  const manifest:Manifest={format:'paperexport',format_version:1,id,name:s.name,author:s.author,description:s.description,minecraft_version:'1.21-26.3',paper_version:'1.21-26.3',exporter_version:'1.0.0',model:'model/model.json',entity:'entity/entity.json',animations:Object.fromEntries(Object.keys(animations).map(n=>[n,`animations/${n}.json`])),textures,sounds,resource_pack:'resourcepack/'};
  const entity:typeof s.entity=JSON.parse(JSON.stringify(s.entity));
  if(entity.sound_cues)entity.sound_cues=Object.fromEntries(Object.entries(entity.sound_cues).filter(([,name])=>!!name));
  if(entity.boss)entity.boss.phases=entity.boss.phases.map(p=>({...p,animation:p.animation||undefined,sound:p.sound||undefined}));
  validateManifest(manifest);validateEntity(entity,new Set(Object.keys(sounds)),new Set(Object.keys(animations)));validateModel(model,new Set(Object.keys(textures)));
  for(const [n,a] of Object.entries(animations))validateAnimation(a,n,new Set(bones.map(b=>b.id)));
  for(const [path,data] of compileResourcePack(manifest,model,files))files.set(path,data);
  return {manifest,model,entity,animations,files};
}
