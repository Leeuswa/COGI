const { chromium } = require('playwright');
const fs=require('fs');
(async()=>{
 const f=process.argv[2], out=process.argv[3];
 const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'}).catch(()=>chromium.launch());
 const p=await b.newPage({viewport:{width:900,height:1200}});
 p.on('console',m=>console.log('console',m.text())); p.on('pageerror',e=>console.log('err',e.message));
 await p.setContent('<html><body><div id=v></div></body></html>');
 await p.addScriptTag({path:__dirname+'/bundle.js'});
 const data=fs.readFileSync(f).toString('base64');
 await p.evaluate(d=>{const bin=atob(d);const a=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)a[i]=bin.charCodeAt(i);new window.HWPViewer(document.getElementById('v'),a,{type:'array'});},data);
 await p.waitForTimeout(1500);
 const ps=await p.$$('#v *'); let k=0; for(const e of ps){const bb=await e.boundingBox(); if(bb&&bb.height>1000&&bb.height<1200&&bb.width>700&&bb.width<850){await e.screenshot({path:out.replace('.png','_'+(++k)+'.png')});}} console.log('pages',k);
 await p.screenshot({path:out,fullPage:true});
 await b.close();
})();
