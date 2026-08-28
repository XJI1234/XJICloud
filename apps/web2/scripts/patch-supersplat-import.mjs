import fs from 'node:fs'

const files = [
  new URL('../../../vendors/supersplat/index.js', import.meta.url),
  new URL('../public/supersplat/index.js', import.meta.url),
]

const broken =
  'Pk=t=>{window.addEventListener("message",async e=>{const n=e.source;if(!n||!e.data||"object"!=typeof e.data)return;if(e.data.type===Ak){n.postMessage({type:Ak,result:t.invoke("scene.dirty")},e.origin);return}if("supersplat:import-local"===e.data.type&&e.data.fileName&&e.data.buffer){try{await t.invoke("import",[{filename:e.data.fileName,contents:new File([e.data.buffer],e.data.fileName)}]);n.postMessage({type:"supersplat:import-local-done"},e.origin)}catch(s){n.postMessage({type:"supersplat:import-local-error",message:String(s&&s.message||s)},e.origin)}}}})'

const original =
  'Pk=t=>{window.addEventListener("message",e=>{const n=e.source;if(n&&(t=>t&&"object"==typeof t&&t.type===Ak)(e.data)){const s={type:Ak,result:t.invoke("scene.dirty")};n.postMessage(s,e.origin)}})}'

const fixed =
  'Pk=t=>{window.addEventListener("message",async e=>{const n=e.source;if(!n||!e.data||"object"!=typeof e.data)return;if(e.data.type===Ak){n.postMessage({type:Ak,result:t.invoke("scene.dirty")},e.origin);return}if("supersplat:import-local"===e.data.type&&e.data.fileName&&e.data.buffer){try{await t.invoke("import",[{filename:e.data.fileName,contents:new File([e.data.buffer],e.data.fileName)}]);n.postMessage({type:"supersplat:import-local-done"},e.origin)}catch(s){n.postMessage({type:"supersplat:import-local-error",message:String(s&&s.message||s)},e.origin)}}})}'

for (const file of files) {
  let s = fs.readFileSync(file, 'utf8')
  if (s.includes(fixed) && !s.includes(broken)) {
    console.log('already valid', file.pathname)
    continue
  }
  if (s.includes(broken)) {
    s = s.replace(broken, fixed)
    fs.writeFileSync(file, s)
    console.log('fixed braces', file.pathname)
    continue
  }
  if (s.includes(original)) {
    s = s.replace(original, fixed)
    fs.writeFileSync(file, s)
    console.log('patched from original', file.pathname)
    continue
  }
  console.error('could not patch', file.pathname)
  process.exit(1)
}
