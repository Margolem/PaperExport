import {modelToBlockbenchUv,readArchive,type PaperPackage} from '@paperexport/shared';
import {setState} from './state.js';

function dataUrl(bytes:Uint8Array):string {let s='';for(const b of bytes)s+=String.fromCharCode(b);return `data:image/png;base64,${btoa(s)}`;}
export async function openPackage(data:Uint8Array,format:any):Promise<void> {
  const pkg:PaperPackage=await readArchive(data);format.new();
  Project.texture_width=pkg.model.texture_size[0];
  Project.texture_height=pkg.model.texture_size[1];
  const [namespace,entityId]=pkg.manifest.id.split(':');
  const sounds:Record<string,Uint8Array>={};for(const [name,ref] of Object.entries(pkg.manifest.sounds??{}))sounds[name]=pkg.files.get(ref.path)!;
  setState({namespace,entityId,name:pkg.manifest.name,author:pkg.manifest.author,description:pkg.manifest.description,entity:{...pkg.entity,sound_cues:pkg.entity.sound_cues??{},boss:pkg.entity.boss??{enabled:false,title:'',bar_color:'PURPLE',bar_style:'SOLID',range:48,phases:[]}},sounds});
  const textures=new Map<string,any>();
  for(const [name,ref] of Object.entries(pkg.manifest.textures)) {
    const t=new Texture({name:`${name}.png`}).fromDataURL(dataUrl(pkg.files.get(ref.path)!)).add();
    textures.set(name,t);
  }
  const groups=new Map<string,any>();
  for(const bone of pkg.model.bones){const g=new Group({name:bone.name,origin:bone.pivot,rotation:bone.rotation});g.paperexportScale=[...bone.scale];groups.set(bone.id,g);}
  for(const bone of pkg.model.bones){const g=groups.get(bone.id);g.addTo(bone.parent?groups.get(bone.parent):undefined).init();}
  for(const bone of pkg.model.bones)for(const cube of bone.cubes){
    const faces:any={};for(const side of ['north','south','east','west','up','down']){
      const face=cube.faces[side as keyof typeof cube.faces];
      faces[side]=face?{texture:textures.get(face.texture).uuid,uv:modelToBlockbenchUv(face.uv,pkg.model.texture_size),rotation:face.rotation??0,enabled:true}:{texture:null,uv:[0,0,0,0],enabled:false};
    }
    new Cube({name:cube.name,from:cube.from,to:cube.to,faces,autouv:0,box_uv:false}).addTo(groups.get(bone.id)).init();
  }
  for(const hitbox of pkg.model.hitboxes??[]){const cube=new Cube({name:'pe_hitbox',from:hitbox.from,to:hitbox.to,faces:{}}).addTo(groups.get(hitbox.bone)).init();cube.paperexportHitboxId=hitbox.id;}
  for(const a of Object.values(pkg.animations)){
    const anim=new Animation({name:a.name,length:a.length,loop:a.loop}).add();
    anim.paperexportEvents=structuredClone(a.events??[]);anim.paperexportPriority=a.priority;
    for(const track of a.tracks){const animator=anim.getBoneAnimator(groups.get(track.bone));if(!animator)continue;
      for(const channel of ['position','rotation','scale'] as const)for(const key of track[channel]??[]){const [x,y,z]=key.value;animator.addKeyframe({channel,time:key.time,interpolation:key.interpolation??'linear',data_points:[{x,y,z}]});}
    }
  }
  Canvas.updateAllBones();Blockbench.showQuickMessage(`Loaded ${pkg.manifest.id}`);
}
