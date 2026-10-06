import {createHash} from 'node:crypto';
import {createWriteStream} from 'node:fs';
import {copyFile, mkdir, readFile, rename, rm, writeFile} from 'node:fs/promises';
import {Readable} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import {resolve} from 'node:path';

const version=process.argv[2]??'1.21.11';
if(!/^(?:1\.21(?:\.\d+)?|26\.\d+(?:\.\d+)?)$/.test(version))throw new Error(`Invalid Paper version: ${version}`);
const allowPrerelease=process.argv.includes('--pre-release');
const root=resolve(version==='1.21.11'?'test-server':`test-servers/${version}`);
const agent='PaperExportLocalTest/1.0 (https://example.com/paperexport)';
const buildsUrl=`https://fill.papermc.io/v3/projects/paper/versions/${version}/builds`;
const response=await fetch(buildsUrl,{headers:{'User-Agent':agent}});
if(!response.ok)throw new Error(`PaperMC build lookup failed: HTTP ${response.status}`);
const builds=await response.json();
const available=builds.filter(item=>item.downloads?.['server:default']?.url);
const build=available.find(item=>item.channel==='STABLE')||(allowPrerelease?available[0]:undefined);
if(!build)throw new Error(`No stable Paper ${version} build found. Pass --pre-release to use a nonstable build.`);
const download=build.downloads['server:default'];
await mkdir(resolve(root,'plugins','PaperExport','entities'),{recursive:true});
const jar=resolve(root,'paper.jar');
let valid=false;
try{valid=createHash('sha256').update(await readFile(jar)).digest('hex')===download.checksums.sha256;}catch{}
if(!valid){
  const file=resolve(root,'paper.jar.download');
  const data=await fetch(download.url,{headers:{'User-Agent':agent}});
  if(!data.ok||!data.body)throw new Error(`PaperMC JAR download failed: HTTP ${data.status}`);
  try{
    await pipeline(Readable.fromWeb(data.body),createWriteStream(file));
    const actual=createHash('sha256').update(await readFile(file)).digest('hex');
    if(actual!==download.checksums.sha256)throw new Error('PaperMC JAR SHA-256 mismatch');
    await rename(file,jar);
  }catch(error){await rm(file,{force:true});throw error;}
}
await copyFile('paper-runtime/build/libs/PaperExport-Paper-1.0.1.jar',resolve(root,'plugins','PaperExport.jar'));
const properties=resolve(root,'server.properties');
try{await readFile(properties);}catch{
  await writeFile(properties,'server-ip=127.0.0.1\nserver-port=25566\nlevel-name=test-world\nmax-players=4\nspawn-protection=0\nonline-mode=true\nmotd=PaperExport local test\n');
}
if(version!=='1.21.11'){
  const start=resolve(root,'start.ps1');
  try{await readFile(start);}catch{await writeFile(start,"$ErrorActionPreference = 'Stop'\nSet-Location $PSScriptRoot\nif (-not (Test-Path -LiteralPath '.\\paper.jar')) { throw 'Run the setup script from the repository root.' }\n& java -Xms512M -Xmx2G -jar paper.jar --nogui\n");}
}
console.log(`Paper ${version} build ${build.id} (${build.channel}) staged in ${root}`);
console.log('Verified SHA-256:',download.checksums.sha256);
console.log('PaperExport plugin staged. Install sample entities separately if desired.');
console.log(`Run ${root}/start.ps1 after accepting the Minecraft EULA in ${root}/eula.txt.`);
