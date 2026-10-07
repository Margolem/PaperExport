import {PNG} from 'pngjs';
import {mkdir,writeFile} from 'node:fs/promises';
import {compileResourcePack,writeArchive} from '../shared/dist/index.js';

const png=new PNG({width:16,height:16});
for(let i=0;i<png.data.length;i+=4){png.data[i]=60;png.data[i+1]=170;png.data[i+2]=210;png.data[i+3]=255;}
const texture=new Uint8Array(PNG.sync.write(png));
const tone=new Uint8Array(64);tone.set(new TextEncoder().encode('OggS'));
const faces=Object.fromEntries(['north','south','east','west','up','down'].map(side=>[side,{uv:[0,0,16,16],texture:'skin'}]));
const cube=(name,from,to)=>({name,from,to,faces});
const bones=[
  {id:'body',name:'Body',parent:null,pivot:[0,16,0],rotation:[0,0,0],scale:[1,1,1],cubes:[cube('Torso',[-4,12,-2],[4,24,2])]},
  {id:'head',name:'Head',parent:'body',pivot:[0,24,0],rotation:[0,0,0],scale:[1,1,1],cubes:[cube('Head',[-4,24,-4],[4,32,4])]},
  {id:'left_arm',name:'Left Arm',parent:'body',pivot:[-5,22,0],rotation:[0,0,0],scale:[1,1,1],cubes:[cube('Arm',[-7,12,-2],[-4,22,2])]},
  {id:'right_arm',name:'Right Arm',parent:'body',pivot:[5,22,0],rotation:[0,0,0],scale:[1,1,1],cubes:[cube('Arm',[4,12,-2],[7,22,2])]},
  {id:'left_leg',name:'Left Leg',parent:'body',pivot:[-2,12,0],rotation:[0,0,0],scale:[1,1,1],cubes:[cube('Leg',[-4,0,-2],[-1,12,2])]},
  {id:'right_leg',name:'Right Leg',parent:'body',pivot:[2,12,0],rotation:[0,0,0],scale:[1,1,1],cubes:[cube('Leg',[1,0,-2],[4,12,2])]}
];
const model={texture_size:[16,16],bones,hitboxes:[
  {id:'hitbox_1',bone:'body',from:[-4,12,-2],to:[4,24,2]},
  {id:'hitbox_2',bone:'head',from:[-4,24,-4],to:[4,32,4]}
]};
const key=(time,value)=>({time,value,interpolation:'linear'});
const animations={
  idle:{name:'idle',length:1,loop:'loop',priority:0,tracks:[],events:[]},
  walk:{name:'walk',length:1,loop:'loop',priority:30,tracks:[],events:[]},
  attack:{name:'attack',length:.5,loop:'once',priority:60,tracks:[{bone:'right_arm',rotation:[key(0,[0,0,0]),key(.25,[-75,0,0]),key(.5,[0,0,0])]}],events:[]},
  hurt:{name:'hurt',length:.5,loop:'once',priority:80,tracks:[],events:[]},
  death:{name:'death',length:1,loop:'hold',priority:100,tracks:[],events:[]}
};
const manifest={format:'paperexport',format_version:1,id:'fixture:rig',name:'Rig Fixture',author:'PaperExport',description:'Small archive for parser and rig tests',minecraft_version:'1.21-26.3',paper_version:'1.21-26.3',exporter_version:'1.1.0',model:'model/model.json',entity:'entity/entity.json',animations:Object.fromEntries(Object.keys(animations).map(n=>[n,`animations/${n}.json`])),textures:{skin:{path:'textures/skin.png',width:16,height:16}},sounds:{tone:{path:'sounds/tone.ogg'}},resource_pack:'resourcepack/'};
const entity={base_entity:'ZOMBIE',behavior:'boss',stats:{max_health:40,movement_speed:.23,attack_damage:4,follow_range:32,knockback_resistance:0,armor:0,armor_toughness:0,scale:1,gravity:true,invulnerable:false,silent:false,persistent:true},hitbox:{width:.6,height:1.8,vertical_offset:0,interaction_range:3},sound_cues:{spawn:'tone'},boss:{enabled:true,title:'Rig Fixture',bar_color:'PURPLE',bar_style:'SOLID',range:48,phases:[{below_health:.5,animation:'attack',sound:'tone'}]}};
const files=new Map([['textures/skin.png',texture],['sounds/tone.ogg',tone]]);
for(const [path,bytes] of compileResourcePack(manifest,model,files))files.set(path,bytes);
await mkdir('tests/fixtures',{recursive:true});
await writeFile('tests/fixtures/minimal.paperexport',await writeArchive({manifest,model,entity,animations,files}));
