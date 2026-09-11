import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * Patches vendored SuperSplat index.js postMessage bridge (Pk):
 * - supersplat:is-scene-dirty
 * - supersplat:import-local
 * - supersplat:export-ply (silent scene.write into transferable ArrayBuffer)
 */

const here = path.dirname(fileURLToPath(import.meta.url))
const defaultFiles = [
  path.resolve(here, '../../../vendors/supersplat/index.js'),
  path.resolve(here, '../public/supersplat/index.js'),
]

const files = process.argv.slice(2).length > 0 ? process.argv.slice(2) : defaultFiles

const dirtyOnly =
  'Pk=t=>{window.addEventListener("message",e=>{const n=e.source;if(n&&(t=>t&&"object"==typeof t&&t.type===Ak)(e.data)){const s={type:Ak,result:t.invoke("scene.dirty")};n.postMessage(s,e.origin)}})}'

const importOnly =
  'Pk=t=>{window.addEventListener("message",async e=>{const n=e.source;if(!n||!e.data||"object"!=typeof e.data)return;if(e.data.type===Ak){n.postMessage({type:Ak,result:t.invoke("scene.dirty")},e.origin);return}if("supersplat:import-local"===e.data.type&&e.data.fileName&&e.data.buffer){try{await t.invoke("import",[{filename:e.data.fileName,contents:new File([e.data.buffer],e.data.fileName)}]);n.postMessage({type:"supersplat:import-local-done"},e.origin)}catch(s){n.postMessage({type:"supersplat:import-local-error",message:String(s&&s.message||s)},e.origin)}}})}'

const importOnlyBroken =
  'Pk=t=>{window.addEventListener("message",async e=>{const n=e.source;if(!n||!e.data||"object"!=typeof e.data)return;if(e.data.type===Ak){n.postMessage({type:Ak,result:t.invoke("scene.dirty")},e.origin);return}if("supersplat:import-local"===e.data.type&&e.data.fileName&&e.data.buffer){try{await t.invoke("import",[{filename:e.data.fileName,contents:new File([e.data.buffer],e.data.fileName)}]);n.postMessage({type:"supersplat:import-local-done"},e.origin)}catch(s){n.postMessage({type:"supersplat:import-local-error",message:String(s&&s.message||s)},e.origin)}}}})'

const withExport =
  'Pk=t=>{window.addEventListener("message",async e=>{const n=e.source;if(!n||!e.data||"object"!=typeof e.data)return;if(e.data.type===Ak){n.postMessage({type:Ak,result:t.invoke("scene.dirty")},e.origin);return}if("supersplat:import-local"===e.data.type&&e.data.fileName&&e.data.buffer){try{await t.invoke("import",[{filename:e.data.fileName,contents:new File([e.data.buffer],e.data.fileName)}]);n.postMessage({type:"supersplat:import-local-done"},e.origin)}catch(s){n.postMessage({type:"supersplat:import-local-error",message:String(s&&s.message||s)},e.origin)}}if("supersplat:export-ply"===e.data.type){try{const s=e.data.fileName||"export.ply",i=!!e.data.compressed,a=i?"compressedPly":"ply",r={filename:s,splatIdx:"all",serializeSettings:{maxSHBands:t.invoke("view.bands")},compressedPly:i},o=[],l={seek:async()=>{},write:async e=>{o.push(e)},truncate:async()=>{},close:async()=>{},abort:async()=>{}};await t.invoke("scene.write",a,r,l);const c=await(new Blob(o)).arrayBuffer();n.postMessage({type:"supersplat:export-ply-result",fileName:s,buffer:c},e.origin,[c])}catch(s){n.postMessage({type:"supersplat:export-ply-error",message:String(s&&s.message||s)},e.origin)}}})}'

const withExportProgress =
  'Pk=t=>{window.addEventListener("message",async e=>{const n=e.source;if(!n||!e.data||"object"!=typeof e.data)return;if(e.data.type===Ak){n.postMessage({type:Ak,result:t.invoke("scene.dirty")},e.origin);return}if("supersplat:import-local"===e.data.type&&e.data.fileName&&e.data.buffer){try{await t.invoke("import",[{filename:e.data.fileName,contents:new File([e.data.buffer],e.data.fileName)}]);n.postMessage({type:"supersplat:import-local-done"},e.origin)}catch(s){n.postMessage({type:"supersplat:import-local-error",message:String(s&&s.message||s)},e.origin)}}if("supersplat:export-ply"===e.data.type){try{const s=e.data.fileName||"export.ply",i=!!e.data.compressed,a=i?"compressedPly":"ply",r={filename:s,splatIdx:"all",serializeSettings:{maxSHBands:t.invoke("view.bands")},compressedPly:i},o=[],p=0,l={seek:async()=>{},write:async u=>{o.push(u);p+=u.byteLength||u.size||0;n.postMessage({type:"supersplat:export-ply-progress",loaded:p},e.origin)},truncate:async()=>{},close:async()=>{},abort:async()=>{}};n.postMessage({type:"supersplat:export-ply-progress",loaded:0},e.origin);const h=setInterval(()=>n.postMessage({type:"supersplat:export-ply-progress",loaded:p},e.origin),5e3);try{await t.invoke("scene.write",a,r,l)}finally{clearInterval(h)}const c=await(new Blob(o)).arrayBuffer();n.postMessage({type:"supersplat:export-ply-result",fileName:s,buffer:c},e.origin,[c])}catch(s){n.postMessage({type:"supersplat:export-ply-error",message:String(s&&s.message||s)},e.origin)}}})}'

for (const file of files) {
  if (!fs.existsSync(file)) {
    console.log('skip missing', file)
    continue
  }
  let s = fs.readFileSync(file, 'utf8')
  if (s.includes(withExportProgress)) {
    console.log('already has export-ply-progress', file)
    continue
  }
  if (s.includes(withExport)) {
    s = s.replace(withExport, withExportProgress)
    fs.writeFileSync(file, s)
    console.log('patched export-ply → export-ply-progress', file)
    continue
  }
  if (s.includes(importOnlyBroken)) {
    s = s.replace(importOnlyBroken, withExportProgress)
    fs.writeFileSync(file, s)
    console.log('patched broken-import → export-ply-progress', file)
    continue
  }
  if (s.includes(importOnly)) {
    s = s.replace(importOnly, withExportProgress)
    fs.writeFileSync(file, s)
    console.log('patched import-local → export-ply-progress', file)
    continue
  }
  if (s.includes(dirtyOnly)) {
    s = s.replace(dirtyOnly, withExportProgress)
    fs.writeFileSync(file, s)
    console.log('patched dirty-only → export-ply-progress', file)
    continue
  }
  console.error('could not patch', file)
  process.exit(1)
}
