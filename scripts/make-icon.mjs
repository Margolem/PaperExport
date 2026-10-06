import {PNG} from 'pngjs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=fileURLToPath(new URL('../',import.meta.url));
const source=PNG.sync.read(await readFile(resolve(root,'assets/paperexport.png')));
if(source.width!==source.height)throw new Error('PaperExport logo must be square');

function scaled(size){
  const png=new PNG({width:size,height:size});
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){
    const from=(Math.floor(y*source.height/size)*source.width+Math.floor(x*source.width/size))*4;
    source.data.copy(png.data,(y*size+x)*4,from,from+4);
  }
  return PNG.sync.write(png);
}

const sizes=[16,32,48,64,256];
const images=sizes.map(scaled);
const header=Buffer.alloc(6+16*sizes.length);
header.writeUInt16LE(1,2);
header.writeUInt16LE(sizes.length,4);
let offset=header.length;
for(let i=0;i<sizes.length;i++){
  const at=6+i*16;
  header[at]=sizes[i]===256?0:sizes[i];
  header[at+1]=header[at];
  header.writeUInt16LE(1,at+4);
  header.writeUInt16LE(32,at+6);
  header.writeUInt32LE(images[i].length,at+8);
  header.writeUInt32LE(offset,at+12);
  offset+=images[i].length;
}
for(const folder of ['viewer/assets','paper-runtime/src/main/resources','blockbench-plugin/dist','dist'])
  await mkdir(resolve(root,folder),{recursive:true});
await writeFile(resolve(root,'viewer/assets/paperexport.ico'),Buffer.concat([header,...images]));
await writeFile(resolve(root,'viewer/assets/paperexport.png'),images.at(-1));
await writeFile(resolve(root,'paper-runtime/src/main/resources/pack.png'),images[3]);
await writeFile(resolve(root,'blockbench-plugin/dist/icon.png'),images[3]);
await writeFile(resolve(root,'dist/icon.png'),images[3]);
console.log('Prepared PaperExport logo for viewer, Blockbench, and resource packs');
