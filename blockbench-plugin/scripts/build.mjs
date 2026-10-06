import {build} from 'esbuild';
import {mkdir,copyFile} from 'node:fs/promises';
await mkdir('dist',{recursive:true});
await build({entryPoints:['src/index.ts'],outfile:'dist/paperexport.js',bundle:true,format:'iife',platform:'browser',target:'es2022',minify:false});
await mkdir('../dist',{recursive:true});
await copyFile('dist/paperexport.js','../dist/paperexport.js');
await copyFile('dist/paperexport.js','../dist/PaperExport-Blockbench.js');
console.log('Built dist/paperexport.js');
