import {spawn} from 'node:child_process';
import {copyFile,mkdir,rm} from 'node:fs/promises';
import {join} from 'node:path';
const win=process.platform==='win32';
function run(command,args,cwd=process.cwd()){return new Promise((resolve,reject)=>{const p=spawn(command,args,{cwd,stdio:'inherit',shell:win});p.on('exit',code=>code===0?resolve():reject(new Error(`${command} ${args.join(' ')} exited ${code}`)));p.on('error',reject);});}
await run('npm',['ci']);
await run('npm',['test']);
await run('npm',['run','build']);
await run(win?'gradlew.bat':'./gradlew',['test','jar','apiJar','--no-daemon'],join(process.cwd(),'paper-runtime'));
await mkdir('dist',{recursive:true});
await rm('dist/test_dummy.paperexport',{force:true});
await copyFile('paper-runtime/build/libs/PaperExport-Paper-1.0.2.jar','dist/PaperExport-Paper-1.0.2.jar');
await copyFile('paper-runtime/build/libs/PaperExport-API-1.0.2.jar','dist/PaperExport-API-1.0.2.jar');
for(const old of ['PaperExport-Paper-1.0.0.jar','PaperExport-API-1.0.0.jar','PaperExport-Paper-1.0.1.jar','PaperExport-API-1.0.1.jar'])await rm(join('dist',old),{force:true});
if(win){
  await run('dotnet',['run','--project','viewer-native-tests/PaperExport.Viewer.Tests.csproj','-c','Release']);
  await run('dotnet',['publish','viewer/PaperExport.Viewer.csproj','-c','Release','-o','viewer/release-build']);
  await copyFile('viewer/release-build/PaperExport Viewer.exe','dist/PaperExport-Viewer-Windows.exe');
  for(const old of ['PaperExport Viewer 1.0.0.exe','PaperExport Viewer Setup 1.0.0.exe'])await rm(join('dist',old),{force:true});
}
console.log('\nArtifacts: dist/paperexport.js + dist/icon.png, dist/PaperExport-Paper-1.0.2.jar, dist/PaperExport-API-1.0.2.jar');
if(win)console.log('Native C# viewer: dist/PaperExport-Viewer-Windows.exe');
