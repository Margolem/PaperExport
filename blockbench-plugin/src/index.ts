import {writeArchive,PackageError} from '@paperexport/shared';
import {captureProject} from './convert.js';
import {openPackage} from './importer.js';
import {state} from './state.js';
import logo from '../../assets/paperexport.png';

let format:any,codec:any,panel:any;const actions:any[]=[];
const thumbnails=new Map<string,Uint8Array>();
function thumbnail():Promise<Uint8Array>{return new Promise((resolve,reject)=>{try{Screencam.cleanCanvas({width:256,height:256},(url:string)=>{try{const encoded=url.split(',')[1];if(!encoded)throw new Error('No screenshot data');resolve(Uint8Array.from(atob(encoded),c=>c.charCodeAt(0)));}catch(e){reject(e);}});}catch(e){reject(e);}});}
const message=(e:unknown)=>Blockbench.showMessageBox({title:'PaperExport',message:e instanceof Error?e.message:String(e)});
function validation():void {try {const p=captureProject();const displays=p.model.bones.filter(b=>b.cubes.length).length;const warnings=[!p.animations.idle?'No idle animation':null,!p.animations.walk?'No walk animation':null,!p.animations.death?'No death animation':null].filter(Boolean);const cost=displays>32?'HIGH':displays>12?'MEDIUM':'LOW';
    Blockbench.showMessageBox({title:'PaperExport Validation',message:`✓ ${p.manifest.id}\n✓ ${p.model.bones.length} bones, ${displays} display nodes\n✓ ${Object.keys(p.manifest.textures).length} textures\n✓ ${Object.keys(p.animations).length} animations\nRuntime cost: ${cost}${warnings.length?`\nWarnings: ${warnings.join(', ')}`:''}`});
  }catch(e){message(e);}}
async function exportFile():Promise<void> {try{const p=captureProject();p.files.set('preview/thumbnail.png',thumbnails.get(String(Project?.uuid))??await thumbnail());const out=await writeArchive(p);Blockbench.export({type:'PaperExport package',extensions:['paperexport'],name:`${p.manifest.id.split(':')[1]}.paperexport`,content:out,savetype:'zip'});}catch(e){message(e);}}
async function setThumbnail():Promise<void>{try{thumbnails.set(String(Project?.uuid),await thumbnail());Blockbench.showQuickMessage('Current view saved as thumbnail');}catch(e){message(e);}}
function importFile():void {const input=document.createElement('input');input.type='file';input.accept='.paperexport';input.onchange=async()=>{try{const file=input.files?.[0];if(file)await openPackage(new Uint8Array(await file.arrayBuffer()),format);}catch(e){message(e);}};input.click();}
function importSound(component:any):void {const input=document.createElement('input');input.type='file';input.accept='.ogg,audio/ogg';input.onchange=async()=>{try{const file=input.files?.[0];if(!file)return;const name=file.name.replace(/[.]ogg$/i,'').toLowerCase().replace(/[^a-z0-9_.-]+/g,'_');const bytes=new Uint8Array(await file.arrayBuffer());if(!name||bytes.length<32||bytes.length>8*1024*1024||String.fromCharCode(...bytes.subarray(0,4))!=='OggS')throw new PackageError('Choose an Ogg Vorbis .ogg file under 8 MiB');component.s.sounds={...component.s.sounds,[name]:bytes};Blockbench.showQuickMessage('Added sound '+name);}catch(e){message(e);}};input.click();}
function addAction(id:string,name:string,icon:string,click:()=>void):void {const a=new Action(id,{name,icon,condition:()=>Format?.id==='paperexport',click});actions.push(a);MenuBar.addAction(a,'file.export');}
function addPanel():void {
  panel=new Panel('paper_entity',{name:'Paper Entity',icon:'settings',default_position:'right',condition:()=>Format?.id==='paperexport',component:{
    data(){return {s:state(),logo};},computed:{validId(){return /^[a-z0-9_.-]+$/.test(this.s.namespace)&&/^[a-z0-9_.-]+$/.test(this.s.entityId);},soundNames(){return Object.keys(this.s.sounds);},animationNames(){return (Animation.all as any[]).map(a=>String(a.name).toLowerCase().replace(/[^a-z0-9_.-]+/g,'_'));}},
    template:`<div class="paperexport_panel" style="padding:12px;display:grid;gap:8px">
      <div style="display:flex;align-items:center;gap:8px"><img :src="logo" width="32" height="32" alt="PaperExport"/><strong>Paper Entity</strong></div><label>Namespace <input v-model="s.namespace" /></label><label>Entity ID <input v-model="s.entityId" /></label>
      <small :style="{color:validId?'#66cda8':'#f08d82'}">{{validId?s.namespace+':'+s.entityId:'Use lowercase letters, digits, _, . or -'}}</small><label>Name <input v-model="s.name" /></label><label>Author <input v-model="s.author" /></label>
      <label>Description <input v-model="s.description" /></label><label>Base entity <select v-model="s.entity.base_entity"><option v-for="x in ['ZOMBIE','SKELETON','HUSK','STRAY','PIG','COW','ARMOR_STAND','INTERACTION']">{{x}}</option></select></label>
      <label>Behavior <select v-model="s.entity.behavior"><option v-for="x in ['passive','neutral','hostile','stationary','flying','swimming','boss','npc','custom']">{{x}}</option></select></label>
      <label>Health <input type="number" min="1" max="2048" v-model.number="s.entity.stats.max_health" /></label>
      <label>Speed <input type="number" min="0" max="2" step="0.01" v-model.number="s.entity.stats.movement_speed" /></label>
      <label>Attack <input type="number" min="0" max="2048" v-model.number="s.entity.stats.attack_damage" /></label>
      <label>Scale <input type="number" min="0.01" max="16" step="0.01" v-model.number="s.entity.stats.scale" /></label>
      <small>Hitbox preview; actual collision comes from the base entity.</small>
      <label>Hitbox width <input type="number" min="0.05" max="32" step="0.05" v-model.number="s.entity.hitbox.width" /></label>
      <label>Hitbox height <input type="number" min="0.05" max="32" step="0.05" v-model.number="s.entity.hitbox.height" /></label>
      <hr/><strong>Sounds</strong><button @click="importSound">Add Ogg sound...</button>
      <div v-for="name in soundNames" :key="name">{{name}} <button @click="removeSound(name)">Remove</button></div>
      <small>Use a sound name in a Blockbench sound keyframe to play it during an animation.</small>
      <label v-for="cue in ['spawn','ambient','attack','hurt','death']" :key="cue">{{cue}} sound
        <select v-model="s.entity.sound_cues[cue]"><option value="">None</option><option v-for="name in soundNames" :value="name">{{name}}</option></select>
      </label>
      <hr/><strong>Boss</strong><label><input type="checkbox" v-model="s.entity.boss.enabled" /> Show boss bar</label>
      <div v-if="s.entity.boss.enabled" style="display:grid;gap:7px">
        <label>Boss title <input v-model="s.entity.boss.title" placeholder="Uses entity name when empty" /></label>
        <label>Bar color <select v-model="s.entity.boss.bar_color"><option v-for="color in ['PINK','BLUE','RED','GREEN','YELLOW','PURPLE','WHITE']">{{color}}</option></select></label>
        <label>Bar style <select v-model="s.entity.boss.bar_style"><option v-for="style in ['SOLID','SEGMENTED_6','SEGMENTED_10','SEGMENTED_12','SEGMENTED_20']">{{style}}</option></select></label>
        <label>Visible range <input type="number" min="4" max="128" v-model.number="s.entity.boss.range" /></label>
        <strong>Health phases</strong><small>List thresholds from highest to lowest (for example 0.5 for 50% health).</small>
        <div v-for="(phase,index) in s.entity.boss.phases" :key="index" style="display:grid;gap:5px">
          <label>Below health <input type="number" min="0.01" max="0.99" step="0.01" v-model.number="phase.below_health" /></label>
          <label>Animation <select v-model="phase.animation"><option value="">None</option><option v-for="name in animationNames" :value="name">{{name}}</option></select></label>
          <label>Sound <select v-model="phase.sound"><option value="">None</option><option v-for="name in soundNames" :value="name">{{name}}</option></select></label>
          <button @click="removePhase(index)">Remove phase</button>
        </div><button @click="addPhase">Add health phase</button>
      </div>
      <button @click="validate">Validate</button><button @click="exportIt">Export .paperexport</button>
    </div>`,methods:{validate:validation,exportIt:exportFile,importSound(){importSound(this);},removeSound(name:string){const next={...this.s.sounds};delete next[name];this.s.sounds=next;},addPhase(){const phases=this.s.entity.boss.phases;this.s.entity.boss.phases=[...phases,{below_health:phases.length?Math.max(.01,Number((phases[phases.length-1].below_health-.2).toFixed(2))):.5,animation:'',sound:''}];},removePhase(index:number){this.s.entity.boss.phases=this.s.entity.boss.phases.filter((_:unknown,i:number)=>i!==index);}}
  }});
}
Plugin.register('paperexport',{title:'PaperExport',author:'PaperExport contributors',description:'Create vanilla-client custom entities for Paper 1.21.x–26.x',icon:'icon.png',version:'1.0.1',min_version:'4.8.0',variant:'both',onload(){
  codec=new Codec('paperexport',{name:'PaperExport package',extension:'paperexport',remember:false,compile:captureProject,parse:(data:any)=>{void openPackage(new Uint8Array(data),format);},export:()=>{void exportFile();}});
  format=new ModelFormat('paperexport',{name:'PaperMC Custom Entity',description:'Animated display-entity model for Paper 1.21.x–26.x',icon:'view_in_ar',category:'minecraft',target:['Minecraft: Java Edition'],show_on_start_screen:true,codec,bone_rig:true,rotate_cubes:false,meshes:false,animation_mode:true,animation_files:false,optional_box_uv:true,uv_rotation:true,single_texture:false,per_texture_uv_size:false,render_sides:'front',forward_direction:'-z',euler_order:'ZYX'});codec.format=format;
  addPanel();addAction('paperexport_export','Export Paper Entity (.paperexport)','file_download',()=>{void exportFile();});
  const imp=new Action('paperexport_import',{name:'Open Paper Entity (.paperexport)',icon:'folder_open',click:importFile});actions.push(imp);MenuBar.addAction(imp,'file.import');
  const val=new Action('paperexport_validate',{name:'Validate Paper Entity',icon:'fact_check',condition:()=>Format?.id==='paperexport',click:validation});actions.push(val);MenuBar.addAction(val,'tools');
  const shot=new Action('paperexport_thumbnail',{name:'Set Current View as Thumbnail',icon:'photo_camera',condition:()=>Format?.id==='paperexport',click:()=>{void setThumbnail();}});actions.push(shot);MenuBar.addAction(shot,'tools');
  const opt=new Action('paperexport_optimize',{name:'Optimize for Paper',icon:'tune',condition:()=>Format?.id==='paperexport',click:()=>{try{const p=captureProject();Blockbench.showMessageBox({title:'Optimize for Paper',message:`Geometry is grouped into ${p.model.bones.filter(b=>b.cubes.length).length} display nodes. Redundant animation keys are removed during export without changing the project.`});}catch(e){message(e);}}});actions.push(opt);MenuBar.addAction(opt,'tools');
},onunload(){for(const a of actions)a.delete();panel?.delete();format?.delete();codec?.delete();}});
