export type Vec3 = [number, number, number];
export type Face = 'north' | 'south' | 'east' | 'west' | 'up' | 'down';
export interface CubeFace { uv: [number, number, number, number]; texture: string; rotation?: 0 | 90 | 180 | 270; }
export interface Cube { name: string; from: Vec3; to: Vec3; faces: Partial<Record<Face, CubeFace>>; }
export interface Bone { id: string; name: string; parent: string | null; pivot: Vec3; rotation: Vec3; scale: Vec3; cubes: Cube[]; }
export interface HitboxRegion { id: string; bone: string; from: Vec3; to: Vec3; }
export interface Model { texture_size: [number, number]; bones: Bone[]; hitboxes?: HitboxRegion[]; }
export interface TextureRef { path: string; width: number; height: number; }
export interface SoundRef { path: string; }
export interface Manifest {
  format: 'paperexport'; format_version: 1; id: string; name: string; author: string;
  description: string; minecraft_version: '1.21.11' | '1.21-26.3'; paper_version: '1.21.11' | '1.21-26.3';
  exporter_version: string; model: string; entity: string;
  animations: Record<string, string>; textures: Record<string, TextureRef>; sounds?: Record<string, SoundRef>;
  resource_pack: 'resourcepack/';
}
export type Interpolation = 'linear' | 'step';
export interface Keyframe { time: number; value: Vec3; interpolation?: Interpolation; }
export interface AnimationTrack { bone: string; position?: Keyframe[]; rotation?: Keyframe[]; scale?: Keyframe[]; }
export interface AnimationEvent { time: number; type: 'sound' | 'particle' | 'damage' | 'custom_event'; data: Record<string, string | number | boolean>; }
export interface EntityAnimation { name: string; length: number; loop: 'once' | 'hold' | 'loop'; priority: number; tracks: AnimationTrack[]; events: AnimationEvent[]; }
export interface EntityStats { max_health: number; movement_speed: number; attack_damage: number; follow_range: number; knockback_resistance: number; armor: number; armor_toughness: number; scale: number; gravity: boolean; invulnerable: boolean; silent: boolean; persistent: boolean; }
export interface BossPhase { below_health: number; animation?: string; sound?: string; }
export interface BossSettings { enabled: boolean; title: string; bar_color: 'PINK'|'BLUE'|'RED'|'GREEN'|'YELLOW'|'PURPLE'|'WHITE'; bar_style: 'SOLID'|'SEGMENTED_6'|'SEGMENTED_10'|'SEGMENTED_12'|'SEGMENTED_20'; range: number; phases: BossPhase[]; }
export interface EntityDefinition { base_entity: string; behavior: 'passive' | 'neutral' | 'hostile' | 'stationary' | 'flying' | 'swimming' | 'boss' | 'npc' | 'custom'; stats: EntityStats; hitbox: {width: number; height: number; vertical_offset: number; interaction_range: number}; sound_cues?: Partial<Record<'spawn'|'ambient'|'attack'|'hurt'|'death',string>>; boss?: BossSettings; }
export interface PaperPackage { manifest: Manifest; model: Model; entity: EntityDefinition; animations: Record<string, EntityAnimation>; files: Map<string, Uint8Array>; }
