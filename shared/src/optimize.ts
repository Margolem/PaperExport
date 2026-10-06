import type {EntityAnimation,Keyframe} from './types.js';
function equal(a:number[],b:number[]):boolean{return a.every((v,i)=>Math.abs(v-b[i])<1e-8);}
export function simplifyKeys(keys:Keyframe[]):Keyframe[]{
  if(keys.length<3)return [...keys];const result=[keys[0]];
  for(let i=1;i<keys.length-1;i++){
    const a=result.at(-1)!,b=keys[i],c=keys[i+1];
    if(a.interpolation==='step'||b.interpolation==='step'){if(a.interpolation==='step'&&b.interpolation==='step'&&equal(a.value,b.value))continue;result.push(b);continue;}
    const f=(b.time-a.time)/(c.time-a.time);
    if(b.value.every((v,j)=>Math.abs(v-(a.value[j]+(c.value[j]-a.value[j])*f))<1e-7))continue;
    result.push(b);
  }
  result.push(keys.at(-1)!);return result;
}
export function optimizeAnimations(animations:Record<string,EntityAnimation>):number{
  let removed=0;for(const animation of Object.values(animations))for(const track of animation.tracks)for(const channel of ['position','rotation','scale'] as const){const keys=track[channel];if(keys){const simplified=simplifyKeys(keys);removed+=keys.length-simplified.length;track[channel]=simplified;}}
  return removed;
}
