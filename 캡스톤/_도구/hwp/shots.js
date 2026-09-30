const { chromium } = require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'});const p=await b.newPage({viewport:{width:1000,height:1400}});
await p.setContent('<html><body><div id=v></div></body></html>');await p.addScriptTag({path:__dirname+'/bundle.js'});
const data=fs.readFileSync(process.argv[2]).toString('base64');
await p.evaluate(d=>{const bin=atob(d);const a=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)a[i]=bin.charCodeAt(i);new window.HWPViewer(document.getElementById('v'),a,{type:'array'});},data);
await p.waitForTimeout(1000);
const n=await p.evaluate(()=>{const s=[...document.querySelectorAll('#v div')].filter(e=>{const c=e.getBoundingClientRect();return Math.round(c.width)==945&&Math.round(c.height)==1255});s.forEach((e,i)=>e.setAttribute('data-pg',i+1));return s.length});
for(let i=1;i<=n;i++){const e=await p.$('[data-pg="'+i+'"]');await e.scrollIntoViewIfNeeded();await e.screenshot({path:process.argv[3]+i+'.png'});}
console.log(n);await b.close();})();
