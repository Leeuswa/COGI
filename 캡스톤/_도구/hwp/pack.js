const fs=require('fs');
// cfb 라이브러리가 쓸데없는 Sh33tJ5 스트림을 끼워넣어서 처음 한 번 패치함
const cp=require.resolve('cfb'); let cs=fs.readFileSync(cp,'utf8');
if(!cs.includes('if(true) return;')){cs=cs.replace('if(CFB.find(cfb, "/" + nm)) return;','if(true) return;');fs.writeFileSync(cp,cs);}
const CFB=require('cfb');
const [src,out,sec,di,bins]=process.argv.slice(2);
const c=CFB.read(fs.readFileSync(src),{type:'buffer'});
CFB.utils.cfb_add(c,'/BodyText/Section0',fs.readFileSync(sec));
CFB.utils.cfb_add(c,'/DocInfo',fs.readFileSync(di));
if(bins) for(const [id,p] of JSON.parse(fs.readFileSync(bins))){
  const name='BIN'+id.toString(16).toUpperCase().padStart(4,'0')+'.png';
  CFB.utils.cfb_add(c,'/BinData/'+name,fs.readFileSync(p));
}
fs.writeFileSync(out,CFB.write(c,{type:'buffer',fileType:'cfb'}));
