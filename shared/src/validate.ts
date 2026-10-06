import type {AnimationTrack, Bone, EntityAnimation, EntityDefinition, Manifest, Model, PaperPackage, Vec3} from './types.js';

export class PackageError extends Error { constructor(message: string) { super(message); this.name = 'PackageError'; } }
export const ID = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
export const SEGMENT = /^[a-z0-9_.-]+$/;
export const MAX = {archive: 32 * 1024 * 1024, expanded: 96 * 1024 * 1024, file: 16 * 1024 * 1024, files: 512, json: 2 * 1024 * 1024, texture: 2048};
function fail(message: string): never { throw new PackageError(message); }
const obj = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const finite = (n: unknown, min: number, max: number): n is number => typeof n === 'number' && Number.isFinite(n) && n >= min && n <= max;
const vec3 = (v: unknown, min: number, max: number): v is Vec3 => Array.isArray(v) && v.length === 3 && v.every(n => finite(n, min, max));
export function safePath(path: string): boolean {
  return path.length <= 240 && path.length > 0 && !path.startsWith('/') && !path.includes('\\') &&
    !path.includes('\0') && !path.includes(':') && path.split('/').every(s => s !== '' && s !== '.' && s !== '..' && /^[A-Za-z0-9_.-]+$/.test(s));
}
export function validateManifest(m: unknown): asserts m is Manifest {
  if (!obj(m) || m.format !== 'paperexport') fail('manifest.json: format must be paperexport');
  if (m.format_version !== 1) fail(`manifest.json: unsupported format_version ${String(m.format_version)}`);
  if (typeof m.id !== 'string' || !ID.test(m.id) || m.id.includes('//') || m.id.includes('..')) fail('manifest.json: invalid namespaced id');
  if (m.minecraft_version !== '1.21.11' || m.paper_version !== '1.21.11') fail('manifest.json: requires Minecraft/Paper 1.21.11');
  for (const k of ['name','author','description','exporter_version'] as const) if (typeof m[k] !== 'string' || m[k].length > 256) fail(`manifest.json: invalid ${k}`);
  if (m.model !== 'model/model.json' || m.entity !== 'entity/entity.json' || m.resource_pack !== 'resourcepack/') fail('manifest.json: invalid fixed entry path');
  if (!obj(m.animations) || !obj(m.textures)) fail('manifest.json: animations and textures must be maps');
  for (const [name,path] of Object.entries(m.animations)) if (!SEGMENT.test(name) || typeof path !== 'string' || path !== `animations/${name}.json`) fail(`manifest.json: invalid animation ${name}`);
  for (const [name,ref] of Object.entries(m.textures)) {
    if (!SEGMENT.test(name) || !obj(ref) || ref.path !== `textures/${name}.png` || !finite(ref.width,1,MAX.texture) || !finite(ref.height,1,MAX.texture)) fail(`manifest.json: invalid texture ${name}`);
  }
  if (m.sounds !== undefined) {
    if (!obj(m.sounds) || Object.keys(m.sounds).length > 32) fail('manifest.json: invalid sounds');
    for (const [name,ref] of Object.entries(m.sounds))
      if (!SEGMENT.test(name) || !obj(ref) || ref.path !== `sounds/${name}.ogg`) fail(`manifest.json: invalid sound ${name}`);
  }
}
export function validateModel(m: unknown, textures: Set<string>): asserts m is Model {
  if (!obj(m) || !Array.isArray(m.texture_size) || m.texture_size.length !== 2 || !m.texture_size.every(n => finite(n,1,MAX.texture)) || !Array.isArray(m.bones) || m.bones.length === 0 || m.bones.length > 128) fail('model/model.json: invalid model');
  const ids = new Set<string>();
  for (const b of m.bones as Bone[]) {
    if (!obj(b) || typeof b.id !== 'string' || !SEGMENT.test(b.id) || ids.has(b.id)) fail('model/model.json: invalid or duplicate bone id');
    ids.add(b.id);
    if (typeof b.name !== 'string' || b.name.length > 128 || !vec3(b.pivot,-1024,1024) || !vec3(b.rotation,-36000,36000) || !vec3(b.scale,0.001,100) || !Array.isArray(b.cubes) || b.cubes.length > 256) fail(`model/model.json: invalid bone ${b.id}`);
    for (const c of b.cubes) {
      if (!obj(c) || typeof c.name !== 'string' || !vec3(c.from,-1024,1024) || !vec3(c.to,-1024,1024) || !c.from.every((v,i) => v < c.to[i]) || !obj(c.faces)) fail(`model/model.json: invalid cube in ${b.id}`);
      for (const [side,face] of Object.entries(c.faces)) {
        if (!['north','south','east','west','up','down'].includes(side) || !obj(face) || typeof face.texture !== 'string' || !textures.has(face.texture) || !Array.isArray(face.uv) || face.uv.length !== 4 || !face.uv.every(n => finite(n,-2048,2048)) || (face.rotation !== undefined && ![0,90,180,270].includes(face.rotation as number))) fail(`model/model.json: invalid ${side} face in ${b.id}`);
      }
    }
  }
  for (const b of m.bones as Bone[]) if (b.parent !== null && (typeof b.parent !== 'string' || !ids.has(b.parent) || b.parent === b.id)) fail(`model/model.json: invalid parent for ${b.id}`);
  for (const b of m.bones as Bone[]) { let p = b.parent; const seen = new Set([b.id]); while (p !== null) { if (seen.has(p)) fail(`model/model.json: bone cycle at ${b.id}`); seen.add(p); p = (m.bones as Bone[]).find(x => x.id === p)!.parent; } }
  if (m.hitboxes !== undefined) {
    if (!Array.isArray(m.hitboxes) || m.hitboxes.length > 64) fail('model/model.json: invalid hitboxes');
    const hitboxIds=new Set<string>();
    for (const h of m.hitboxes) {
      if (!obj(h) || typeof h.id !== 'string' || !SEGMENT.test(h.id) || hitboxIds.has(h.id) || typeof h.bone !== 'string' || !ids.has(h.bone) || !vec3(h.from,-1024,1024) || !vec3(h.to,-1024,1024)) fail('model/model.json: invalid pe_hitbox');
      const to=h.to;
      if (!h.from.every((v,i)=>v<to[i]) || to[1]-h.from[1]>512 || Math.max(to[0]-h.from[0],to[2]-h.from[2])>512) fail('model/model.json: invalid pe_hitbox');
      hitboxIds.add(h.id);
    }
  }
}
export function validateEntity(e: unknown, sounds?: Set<string>, animations?: Set<string>): asserts e is EntityDefinition {
  if (!obj(e) || typeof e.base_entity !== 'string' || !['ZOMBIE','SKELETON','HUSK','STRAY','PIG','COW','ARMOR_STAND','INTERACTION'].includes(e.base_entity) || !['passive','neutral','hostile','stationary','flying','swimming','boss','npc','custom'].includes(e.behavior as string) || !obj(e.stats) || !obj(e.hitbox)) fail('entity/entity.json: invalid entity');
  const limits: Record<string,[number,number]> = {max_health:[1,2048],movement_speed:[0,2],attack_damage:[0,2048],follow_range:[0,256],knockback_resistance:[0,1],armor:[0,30],armor_toughness:[0,20],scale:[0.01,16]};
  for (const [k,[lo,hi]] of Object.entries(limits)) if (!finite(e.stats[k],lo,hi)) fail(`entity/entity.json: invalid stats.${k}`);
  for (const k of ['gravity','invulnerable','silent','persistent']) if (typeof e.stats[k] !== 'boolean') fail(`entity/entity.json: invalid stats.${k}`);
  for (const [k,[lo,hi]] of Object.entries({width:[0.05,32],height:[0.05,32],vertical_offset:[-16,16],interaction_range:[0,64]})) if (!finite(e.hitbox[k],lo,hi)) fail(`entity/entity.json: invalid hitbox.${k}`);
  if (e.sound_cues !== undefined) {
    if (!obj(e.sound_cues)) fail('entity/entity.json: invalid sound_cues');
    for (const [cue,name] of Object.entries(e.sound_cues))
      if (!['spawn','ambient','attack','hurt','death'].includes(cue) || typeof name !== 'string' || !SEGMENT.test(name) || (sounds && !sounds.has(name)))
        fail(`entity/entity.json: invalid sound cue ${cue}`);
  }
  if (e.boss !== undefined) {
    const b=e.boss;
    if (!obj(b) || typeof b.enabled !== 'boolean' || typeof b.title !== 'string' || b.title.length>128 ||
      !['PINK','BLUE','RED','GREEN','YELLOW','PURPLE','WHITE'].includes(b.bar_color as string) ||
      !['SOLID','SEGMENTED_6','SEGMENTED_10','SEGMENTED_12','SEGMENTED_20'].includes(b.bar_style as string) ||
      !finite(b.range,4,128) || !Array.isArray(b.phases) || b.phases.length>8) fail('entity/entity.json: invalid boss settings');
    let previous=1;
    for (const p of b.phases) {
      if (!obj(p) || !finite(p.below_health,0.01,0.99) || p.below_health>=previous ||
        (p.animation!==undefined && (typeof p.animation!=='string' || !SEGMENT.test(p.animation) || (animations && !animations.has(p.animation)))) ||
        (p.sound!==undefined && (typeof p.sound!=='string' || !SEGMENT.test(p.sound) || (sounds && !sounds.has(p.sound)))))
        fail('entity/entity.json: invalid boss phase');
      previous=p.below_health;
    }
  }
}
function track(t: AnimationTrack, bones: Set<string>, length: number) {
  if (!obj(t) || typeof t.bone !== 'string' || !bones.has(t.bone)) fail(`animation: missing bone ${String(t?.bone)}`);
  for (const channel of ['position','rotation','scale'] as const) {
    const keys = t[channel]; if (keys === undefined) continue;
    if (!Array.isArray(keys) || keys.length > 4096) fail(`animation: invalid ${channel} track`);
    let prev = -1;
    for (const k of keys) {
      if (!obj(k) || !finite(k.time,0,length) || k.time <= prev || !vec3(k.value,channel === 'scale' ? 0.001 : -36000,36000) || (k.interpolation !== undefined && !['linear','step'].includes(k.interpolation))) fail(`animation: invalid ${channel} keyframe`);
      prev = k.time;
    }
  }
}
export function validateAnimation(a: unknown, name: string, bones: Set<string>): asserts a is EntityAnimation {
  if (!obj(a) || a.name !== name || !finite(a.length,0.001,3600) || !['once','hold','loop'].includes(a.loop as string) || !finite(a.priority,-1000,1000) || !Array.isArray(a.tracks) || a.tracks.length > 256 || !Array.isArray(a.events) || a.events.length > 1024) fail(`animations/${name}.json: invalid animation`);
  for (const t of a.tracks) track(t,bones,a.length as number);
  for (const e of a.events) if (!obj(e) || !finite(e.time,0,a.length as number) || !['sound','particle','damage','custom_event'].includes(e.type as string) || !obj(e.data) || Object.values(e.data).some(v => !['string','number','boolean'].includes(typeof v))) fail(`animations/${name}.json: invalid event`);
}
export function validatePackage(p: PaperPackage): void {
  validateManifest(p.manifest); validateEntity(p.entity,new Set(Object.keys(p.manifest.sounds??{})),new Set(Object.keys(p.manifest.animations))); validateModel(p.model,new Set(Object.keys(p.manifest.textures)));
  const bones = new Set(p.model.bones.map(b => b.id));
  for (const name of Object.keys(p.manifest.animations)) validateAnimation(p.animations[name],name,bones);
}
