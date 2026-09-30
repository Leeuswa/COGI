const { chromium } = require('playwright');const fs=require('fs');const path=require('path');
const NM=__dirname+'/node_modules';
(async()=>{
const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'});
const ctx=await b.newContext({viewport:{width:1280,height:800},deviceScaleFactor:1});
// 폰트 CDN 막혀 있어서 로컬 npm 폰트로 대체
await ctx.route(/jsdelivr\.net\/npm\/galmuri\/dist\/(.*)/,r=>{const f=r.request().url().split('/dist/')[1];const fp=NM+'/galmuri/dist/'+f;if(!fs.existsSync(fp))return r.fulfill({status:404});r.fulfill({body:fs.readFileSync(fp),contentType:f.endsWith('.css')?'text/css':'font/woff2'})});
await ctx.route(/fonts\.googleapis\.com/,r=>r.fulfill({contentType:'text/css',body:"@font-face{font-family:'Silkscreen';src:url(https://local.font/s4.woff2)}@font-face{font-family:'Silkscreen';font-weight:700;src:url(https://local.font/s7.woff2)}"}));
await ctx.route(/local\.font\/s(\d)/,r=>{const w=r.request().url().includes('s7')?700:400;r.fulfill({body:fs.readFileSync(NM+`/@fontsource/silkscreen/files/silkscreen-latin-${w}-normal.woff2`),contentType:'font/woff2'})});
const p=await ctx.newPage();
const shots=[['http://localhost:4173/','main'],['http://localhost:4173/login','login'],['http://localhost:4173/signup','signup']];
for(const f of ['회의록','요구사항정의서','기능정의서','개발일정','자료수집']) shots.push(['file://'+path.resolve(__dirname,'../ph/'+f+'.html'),f]);
for(const [u,n] of shots){await p.goto(u);await p.waitForTimeout(1200);
  const raw=await p.screenshot({clip:{x:0,y:0,width:1280,height:800}});
  const q=await ctx.newPage();
  await q.setContent(`<body style="margin:0;background:#fff"><img id=i src="data:image/png;base64,${raw.toString('base64')}" style="display:block;border:10px solid #1b2a4a"></body>`);
  await (await q.$('#i')).screenshot({path:path.resolve(__dirname,'../ph/'+n+'.png')});await q.close();}
await b.close();})();
