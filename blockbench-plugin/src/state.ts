import type {EntityDefinition} from '@paperexport/shared';
export interface State {namespace:string; entityId:string; name:string; author:string; description:string; entity:EntityDefinition; sounds:Record<string,Uint8Array>;}
export function defaults():State {return {namespace:'demo',entityId:'creature',name:'Creature',author:'',description:'',sounds:{},entity:{base_entity:'ZOMBIE',behavior:'hostile',stats:{max_health:40,movement_speed:0.23,attack_damage:4,follow_range:32,knockback_resistance:0,armor:0,armor_toughness:0,scale:1,gravity:true,invulnerable:false,silent:false,persistent:true},hitbox:{width:0.6,height:1.8,vertical_offset:0,interaction_range:3},sound_cues:{},boss:{enabled:false,title:'',bar_color:'PURPLE',bar_style:'SOLID',range:48,phases:[]}}};}
const states=new Map<string,State>();
export function state():State {const key=String(Project?.uuid??'default');let s=states.get(key);if(!s){s=defaults();states.set(key,s);}return s;}
export function setState(s:State):void {states.set(String(Project?.uuid??'default'),s);}
