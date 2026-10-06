import type {CubeFace} from './types.js';

export type UvSize = [number, number];

/** Blockbench's per-face UV grid uses the project UV size in this format. */
export function blockbenchToModelUv(uv: CubeFace['uv'], size: UvSize): CubeFace['uv'] {
  return uv.map((value, index) => value * 16 / size[index % 2]) as CubeFace['uv'];
}

export function modelToBlockbenchUv(uv: CubeFace['uv'], size: UvSize): CubeFace['uv'] {
  return uv.map((value, index) => value * size[index % 2] / 16) as CubeFace['uv'];
}
