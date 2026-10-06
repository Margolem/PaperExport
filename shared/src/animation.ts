import type {Bone, EntityAnimation, Keyframe, Model, Vec3} from './types.js';
export function sample(keys:Keyframe[]|undefined,time:number,fallback:Vec3):Vec3 {
  if(!keys?.length)return [...fallback] as Vec3;if(time<=keys[0].time)return [...keys[0].value] as Vec3;
  for(let i=1;i<keys.length;i++)if(time<=keys[i].time){const a=keys[i-1],b=keys[i];const t=a.interpolation==='step'?0:(time-a.time)/(b.time-a.time);return a.value.map((v,j)=>v+(b.value[j]-v)*t) as Vec3;}
  return [...keys[keys.length-1].value] as Vec3;
}
export function sampleBone(animation:EntityAnimation|undefined,bone:Bone,time:number):{position:Vec3;rotation:Vec3;scale:Vec3} {
  const t=animation?.tracks.find(t=>t.bone===bone.id);const localTime=animation?.loop==='loop'?time%animation.length:Math.min(time,animation?.length??0);
  return {position:sample(t?.position,localTime,[0,0,0]),rotation:sample(t?.rotation,localTime,[0,0,0]),scale:sample(t?.scale,localTime,[1,1,1])};
}
export function hierarchy(model:Model):Bone[] {const result:Bone[]=[],visited=new Set<string>();function visit(b:Bone){if(visited.has(b.id))return;if(b.parent){const p=model.bones.find(x=>x.id===b.parent);if(p)visit(p);}visited.add(b.id);result.push(b);}for(const b of model.bones)visit(b);return result;}
