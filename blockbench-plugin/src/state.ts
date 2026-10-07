import type {EntityDefinition} from '@paperexport/shared';
export interface State {namespace:string; entityId:string; name:string; author:string; description:string; entity:EntityDefinition; sounds:Record<string,Uint8Array>;}
export function defaults():State {return {namespace:'demo',entityId:'creature',name:'Creature',author:'',description:'',sounds:{},entity:{base_entity:'ZOMBIE',behavior:'hostile',stats:{max_health:40,movement_speed:0.23,attack_damage:4,follow_range:32,knockback_resistance:0,armor:0,armor_toughness:0,scale:1,gravity:true,invulnerable:false,silent:false,persistent:true},hitbox:{width:0.6,height:1.8,vertical_offset:0,interaction_range:3},sound_cues:{},boss:{enabled:false,title:'',bar_color:'PURPLE',bar_style:'SOLID',range:48,phases:[]}}};}
const states=new Map<string,State>();
export function state():State {const key=String(Project?.uuid??'default');let s=states.get(key);if(!s){s=defaults();states.set(key,s);}return s;}
export function setState(s:State):void {states.set(String(Project?.uuid??'default'),s);Blockbench.dispatchEvent?.('paperexport_state',{});}
export function saveState():unknown {
  const s=state();
  return {...s,sounds:Object.fromEntries(Object.entries(s.sounds).map(([name,bytes])=>{
    let raw='';for(const byte of bytes)raw+=String.fromCharCode(byte);return [name,btoa(raw)];
  }))};
}
export function loadState(value:any):void {
  const base=defaults();
  if(!value||typeof value!=='object'){setState(base);return;}
  const sounds:Record<string,Uint8Array>={};
  for(const [name,encoded] of Object.entries(value.sounds??{})){
    if(typeof encoded!=='string'||encoded.length>12*1024*1024)throw new Error(`Invalid saved sound: ${name}`);
    sounds[name]=Uint8Array.from(atob(encoded),c=>c.charCodeAt(0));
  }
  setState({...base,...value,entity:{...base.entity,...value.entity,
    stats:{...base.entity.stats,...value.entity?.stats},hitbox:{...base.entity.hitbox,...value.entity?.hitbox},
    sound_cues:{...value.entity?.sound_cues},boss:{...base.entity.boss,...value.entity?.boss}},sounds});
}
