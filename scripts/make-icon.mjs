import {PNG} from 'pngjs';
import {mkdir,writeFile} from 'node:fs/promises';
const sizes=[16,32,48,64,256];
function polygon(x,y,points){let inside=false;for(let i=0,j=points.length-1;i<points.length;j=i++){const [xi,yi]=points[i],[xj,yj]=points[j];if(((yi>y)!==(yj>y))&&(x<(xj-xi)*(y-yi)/(yj-yi)+xi))inside=!inside;}return inside;}
function render(size){const png=new PNG({width:size,height:size});for(let y=0;y<size;y++)for(let x=0;x<size;x++){
  const px=(x+.5)*256/size,py=(y+.5)*256/size,i=(y*size+x)*4;let color=[0,0,0,0];
  if(polygon(px,py,[[128,18],[231,77],[128,136],[25,77]]))color=[103,221,173,255];
  if(polygon(px,py,[[25,77],[128,136],[128,240],[25,181]]))color=[31,135,124,255];
  if(polygon(px,py,[[231,77],[128,136],[128,240],[231,181]]))color=[20,86,92,255];
  if(px>76&&px<97&&py>91&&py<181)color=[237,255,237,255];
  if(px>95&&px<143&&py>91&&py<113)color=[237,255,237,255];
  if(px>95&&px<143&&py>130&&py<151)color=[237,255,237,255];
  if(px>140&&px<161&&py>109&&py<134)color=[237,255,237,255];
  png.data[i]=color[0];png.data[i+1]=color[1];png.data[i+2]=color[2];png.data[i+3]=color[3];
}return PNG.sync.write(png);}
const images=sizes.map(render);const header=Buffer.alloc(6+16*sizes.length);header.writeUInt16LE(1,2);header.writeUInt16LE(sizes.length,4);let offset=header.length;
for(let i=0;i<sizes.length;i++){const at=6+i*16;header[at]=sizes[i]===256?0:sizes[i];header[at+1]=header[at];header[at+2]=0;header[at+3]=0;header.writeUInt16LE(1,at+4);header.writeUInt16LE(32,at+6);header.writeUInt32LE(images[i].length,at+8);header.writeUInt32LE(offset,at+12);offset+=images[i].length;}
await mkdir('viewer/assets',{recursive:true});await writeFile('viewer/assets/paperexport.ico',Buffer.concat([header,...images]));await writeFile('viewer/assets/paperexport.png',images.at(-1));console.log('Generated viewer icon');
