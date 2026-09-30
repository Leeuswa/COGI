import copy
from hwplib import *
from forms_content import *
o=olefile.OleFileIO('전체양식_원본.hwp')
R=read_records(decomp(o.openstream('BodyText/Section0').read()))
LAST=0x80000000
def utf(s): return s.encode('utf-16le')

def cell_end(lh):
    # 칸 안 문단 헤더는 LIST_HEADER와 같은 레벨이라, 다음 칸(LIST_HEADER)이나 더 바깥 레코드가 나올 때까지
    L=R[lh][1]; j=lh+1
    while j<len(R) and not (R[j][1]<L or (R[j][0]==72 and R[j][1]==L)): j+=1
    return j
VP=[0]
def lsfor(ls,text):
    # 줄 위치(vpos)를 순서대로 매겨둠. 한글은 열 때 다시 계산하지만 뷰어 확인용
    b=bytearray(ls); struct.pack_into('<i',b,4,VP[0])
    lh=struct.unpack_from('<i',b,8)[0]; sp=struct.unpack_from('<i',b,20)[0]
    w=struct.unpack_from('<i',b,28)[0] or 40000
    lines=max(1,-(-len(text)*1000//max(w,5000)))
    VP[0]+= (lh+sp)*lines
    return bytes(b)
def mkpara(lvl,text,hdr_tpl,cs,ls,last):
    ls=lsfor(ls,text)
    h=bytearray(hdr_tpl); n=len(text)+1
    struct.pack_into('<II',h,0,n|(LAST if last else 0),0)
    struct.pack_into('<HHH',h,12,1,0,1)
    out=[[66,lvl,bytes(h)]]
    if text: out.append([67,lvl+1,utf(text)+b'\r\x00'])
    else: struct.pack_into('<I',h,0,1|(LAST if last else 0)); out[0][2]=bytes(h)
    out+= [[68,lvl+1,struct.pack('<II',0,cs)],[69,lvl+1,ls]]
    return out
def tpl(lh):
    h=R[lh+1]; assert h[0]==66
    j=lh+2; cs=ls=None
    while j<len(R) and R[j][1]>h[1]:
        if R[j][0]==68 and cs is None: cs=struct.unpack_from('<I',R[j][2],4)[0]
        if R[j][0]==69 and ls is None: ls=R[j][2][:36]
        j+=1
    return h[1],h[2],cs,ls

EDITS=[]   # (lh, kind, lines, opt)
def SET(lh,lines): EDITS.append((lh,'set',lines if isinstance(lines,list) else [lines],None))
def APP(lh,lines,ps=44,cs=4): EDITS.append((lh,'app',lines,(ps,cs)))
def TXT(i,s): EDITS.append((i,'txt',s,None))

# ── 양식1 과제계획 총괄표 (우리 팀 줄) ──
SET(365,TEAM); SET(369,TITLE); SET(373,"4"); SET(377,MEMBERS[0][0])
TXT(536,"4")
# ── 양식2 과제계획서 ──
TXT(637,"2026-2 캡스톤디자인(Capstone Design) 과제계획서")
SET(686,TEAM); SET(715,TITLE)
for (nm,_),(c1,c4,c6) in zip(MEMBERS,[(853,857,865),(882,886,894),(906,910,918),(930,934,942)]):
    SET(c1,DEPT); SET(c4,"3"); SET(c6,nm)
SET(1112,MEMBERS[0][0])
# ── 양식3 과제수행 계획서 ──
APP(1173,TEAM_INTRO); APP(1178,MOTIVE); APP(1183,NEED)
APP(1193,SIMILAR); APP(1198,GOAL)
for (nm,dam,role),(c1,c2,c3) in zip(ROLES,[(1233,1237,1241),(1250,1254,1258),(1267,1271,1275),(1284,1288,1292)]):
    SET(c1,nm); SET(c2,dam); SET(c3,role)
rows=[{0:1370,3:1375,5:1380,6:1384,7:1388},{0:1392,3:1397,4:1401,6:1406,7:1410},{0:1414,3:1419,4:1423,7:1428}]
for r in range(21,26):
    b=1432+(r-21)*24; rows.append({0:b,3:b+4,4:b+8,5:b+12,6:b+16,7:b+20})
for cells,(what,marks,note) in zip(rows,SCHEDULE):
    for c,lh in cells.items():
        v={0:what,7:note}.get(c, marks.get(c,""))
        SET(lh,v)
APP(1557,EFFECT)
# ── 양식4-1 서약서 ──
SET(1603,TEAM); SET(1612,TITLE)
for nm,lh in zip([m[0] for m in MEMBERS],[1749,1762,1775,1788]): SET(lh,nm)

# 뒤에서부터 적용
for lh,kind,lines,opt in sorted(EDITS,key=lambda e:-e[0]):
    if kind=='txt':
        p=R[lh][2]; t=text_of(p)
        if '{3:%fmu}' in t:   # 필드 안 숫자만 바꿈
            i=p.index(b'0\x00',16); R[lh][2]=p[:i]+utf(lines)+p[i+2:]
        else: R[lh][2]=utf(lines)+b'\r\x00'
        n=len(R[lh][2])//2
        h=bytearray(R[lh-1][2]); old=struct.unpack_from('<I',h,0)[0]; struct.pack_into('<I',h,0,n|(old&LAST)); R[lh-1][2]=bytes(h)
        continue
    lvl,hdr,cs,ls=tpl(lh); end=cell_end(lh)
    if kind=='set':
        VP[0]=0
        new=[]
        for k,s in enumerate(lines): new+=mkpara(lvl,s,hdr,cs,ls,k==len(lines)-1)
        R[lh+1:end]=new; cnt=len(lines)
    else:
        ps,ccs=opt
        # 기존 마지막 문단의 last 표시 해제
        for j in range(end-1,lh,-1):
            if R[j][0]==66 and R[j][1]==lvl:
                h=bytearray(R[j][2]); v=struct.unpack_from('<I',h,0)[0]; struct.pack_into('<I',h,0,v&~LAST); R[j][2]=bytes(h); break
        h2=bytearray(hdr); struct.pack_into('<H',h2,8,ps); h2[10]=0
        VP[0]=0
        for j in range(lh+1,end):
            if R[j][0]==69:
                b=R[j][2]; VP[0]=max(VP[0],struct.unpack_from('<i',b,4)[0]+struct.unpack_from('<i',b,8)[0]+struct.unpack_from('<i',b,20)[0])
        new=[]
        for k,s in enumerate(lines): new+=mkpara(lvl,s,bytes(h2),ccs,ls,k==len(lines)-1)
        R[end:end]=new
        cnt=struct.unpack_from('<H',R[lh][2],0)[0]+len(lines)
    lp=bytearray(R[lh][2]); struct.pack_into('<H',lp,0,cnt); R[lh][2]=bytes(lp)

# 양식4-2부터(개인 동의서, 협정서, 결과보고 등)는 뺌
cut=next(i for i,(t,l,p) in enumerate(R) if t==67 and l==1 and '[양식4-2]' in text_of(p))-1
assert R[cut][0]==66 and R[cut][1]==0
R=R[:cut]
lastp=max(i for i,(t,l,p) in enumerate(R) if t==66 and l==0)
h=bytearray(R[lastp][2]); v=struct.unpack_from('<I',h,0)[0]; struct.pack_into('<I',h,0,v|LAST); R[lastp][2]=bytes(h)

open('fSection0.bin','wb').write(comp(write_records(R)))
open('fDocInfo.bin','wb').write(o.openstream('DocInfo').read())
print('ok',len(R))
