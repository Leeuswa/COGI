import sys, os, struct, random, copy
from hwplib import *
from content import *
o=olefile.OleFileIO('양식9-1_원본.hwp')
R=read_records(decomp(o.openstream('BodyText/Section0').read()))
DI=read_records(decomp(o.openstream('DocInfo').read()))

LAST=0x80000000
def utf(s): return s.encode('utf-16le')
def para(level, text, ps, cs, ls, last, style=0, brk=0):
    n=len(text)+1
    hdr=struct.pack('<IIHBBHHHI',n|(LAST if last else 0),0,ps,style,brk,1,0,1,0)+b'\x00\x00'
    return [[66,level,hdr],[67,level+1,utf(text)+b'\r\x00'],[68,level+1,struct.pack('<II',0,cs)],[69,level+1,ls]]
def set_nchars(hdr,n,keep_last=True):
    old=struct.unpack_from('<I',hdr,0)[0]
    return struct.pack('<I',n|(old&LAST))+hdr[4:]

def fill_empty_cell(page, idx, text):
    # idx: PARA_HEADER of empty cell para, followed by CHAR_SHAPE, LINE_SEG
    h,cs,ls=page[idx],page[idx+1],page[idx+2]
    lvl=h[1]; csid=struct.unpack_from('<I',cs[2],4)[0]
    ps=struct.unpack_from('<H',h[2],8)[0]
    new=para(lvl,text,ps,csid,ls[2],True)
    page[idx:idx+3]=new

def replace_between_fields(rec_text, new):
    p=rec_text[2]
    # layout: 16 bytes ctrl(3) ... text ... 16 bytes ctrl(4) + \r
    body=p[16:-18]
    p2=p[:16]+utf(new)+p[-18:]
    rec_text[2]=p2
    return len(p2)//2

BINS=[]   # (id, path)
def bin_id(path):
    for i,p in BINS:
        if p==path: return i
    i=4+len(BINS); BINS.append((i,path)); return i
def png_size(path):
    b=open(path,'rb').read(24); return struct.unpack('>II',b[16:24])
TPL=R[247:254]   # '산학협력처장 귀하' 그림 문단 (글자처럼 취급) 을 틀로 씀
S1=struct.pack('<d',1.0)
def photo_paras(level, path, caption):
    pw,ph=png_size(path)
    H=12000; W=int(H*pw/ph)
    if W>22800: W=22800; H=int(W*ph/pw)
    bid=bin_id(path)
    t=copy.deepcopy(TPL)
    base=t[0][1]; d=level-base
    for r in t: r[1]+=d
    # 문단 헤더: 가운데(100%) 문단모양 29, 마지막 문단 아님
    h=bytearray(t[0][2]); struct.pack_into('<I',h,0,9); struct.pack_into('<H',h,8,29); t[0][2]=bytes(h)
    # CTRL_HEADER (글자처럼 취급 bit0 유지)
    c=bytearray(t[4][2]); assert struct.unpack_from('<I',c,4)[0]&1
    struct.pack_into('<II',c,16,W,H); struct.pack_into('<I',c,36,random.randint(1,0x7fffffff))
    desc='그림'.encode('utf-16le'); c=c[:44]+struct.pack('<H',2)+desc; t[4][2]=bytes(c)
    # SHAPE_COMPONENT: 원래 크기 = 현재 크기, 배율 1
    sc=bytearray(t[5][2])
    struct.pack_into('<IIII',sc,20,W,H,W,H)
    struct.pack_into('<ii',sc,42,W//2,H//2)
    for old in (bytes.fromhex('07b5d8820c0fdb3f'),bytes.fromhex('f4c48c23ea0edb3f')):
        i=sc.find(old); assert i>0; sc[i:i+8]=S1
    t[5][2]=bytes(sc)
    # PICTURE
    pc=bytearray(t[6][2])
    struct.pack_into('<8i',pc,12,0,0,W,0,W,H,0,H)
    struct.pack_into('<4i',pc,44,0,0,pw*75,ph*75)
    struct.pack_into('<H',pc,71,bid)
    struct.pack_into('<I',pc,74,random.randint(1,0x7fffffff))
    struct.pack_into('<II',pc,82,pw*75,ph*75)
    t[6][2]=bytes(pc)
    cap=para(level,caption,1,3,t[3][2],True)
    return t+cap

def build_page(lines, first, last, photos):
    page=copy.deepcopy(R[36:254])
    off=36
    g=lambda i: i-off
    # do edits from the bottom up so earlier indices stay valid
    # bottom date (221)
    t=page[g(221)]; t[2]=utf(DATE_BOTTOM)+b'\r\x00'
    page[g(220)][2]=set_nchars(page[g(220)][2],len(DATE_BOTTOM)+1)
    # 사진 칸 2개 (208 오른쪽 먼저, 뒤에서부터 바꿔야 인덱스 안 밀림)
    for lh_i,(path,cap) in ((208,photos[1]),(204,photos[0])):
        lh=page[g(lh_i)]; lp=bytearray(lh[2]); struct.pack_into('<H',lp,0,2); lh[2]=bytes(lp)
        lvl=page[g(lh_i)+1][1]
        page[g(lh_i)+1:g(lh_i)+4]=photo_paras(lvl,path,cap)
    # content cell 171: replace paras 172..198
    lh=page[g(171)]
    lp=bytearray(lh[2]); struct.pack_into('<H',lp,0,len(lines))
    attr=struct.unpack_from('<I',lp,2)[0] & ~(3<<21)   # top align
    struct.pack_into('<I',lp,2,attr); lh[2]=bytes(lp)
    ls=page[g(180)][2]
    newp=[]
    for k,s in enumerate(lines):
        newp+=para(lh[1],s,42,4,ls,k==len(lines)-1)
    page[g(172):g(199)]=newp
    # date field (155/156)
    n=replace_between_fields(page[g(156)],DATE_FIELD)
    page[g(155)][2]=set_nchars(page[g(155)][2],n)
    fill_empty_cell(page,g(131),TITLE)
    fill_empty_cell(page,g(122),LEADER)
    fill_empty_cell(page,g(113),TEAM)
    # main para header
    h=bytearray(page[0][2])
    n=struct.unpack_from('<I',h,0)[0]&~LAST
    struct.pack_into('<I',h,0,n|(LAST if last else 0))
    page[0][2]=bytes(h)
    if not first:
        for rec in page:
            if rec[0]==71 and rec[2][:4][::-1] in (b'tbl ',b'gso ') and len(rec[2])>=40:
                b=bytearray(rec[2]); struct.pack_into('<I',b,36,random.randint(1,0x7fffffff)); rec[2]=bytes(b)
    return page

out=copy.deepcopy(R[0:36])
for i,lines in enumerate(PAGES):
    first=i==0; last=i==len(PAGES)-1
    if not first:
        # "[양식9-1]" label paragraph, starts a new page
        csid=struct.unpack_from('<I',R[2][2],4)[0]
        out+=para(0,'[양식9-1]',39,csid,R[3][2],False,brk=0x04)
    out+=build_page(lines,first,last,PHOTOS[i])

# parashape 42 line spacing 160 -> 130
ps=[r for r in DI if r[0]==25]
b=bytearray(ps[42][2]); struct.pack_into('<I',b,24,130); struct.pack_into('<I',b,len(b)-4,130); ps[42][2]=bytes(b)

idm=bytearray(DI[1][2]); struct.pack_into('<I',idm,0,3+len(BINS)); DI[1][2]=bytes(idm)
newbins=[[18,1,struct.pack('<HH',0x0001,i)+struct.pack('<H',3)+'png'.encode('utf-16le')] for i,_ in BINS]
DI[5:5]=newbins
import json; json.dump(BINS,open('bins.json','w'))
open('Section0.bin','wb').write(comp(write_records(out)))
open('DocInfo.bin','wb').write(comp(write_records(DI)))
print('records',len(out))
# BinData 스트림은 문서 압축 설정 따라 deflate해서 저장
import os
os.makedirs('binz',exist_ok=True)
json.dump([[i,'binz/%d.bin'%i] for i,_ in BINS],open('bins.json','w'))
for i,p in BINS: open('binz/%d.bin'%i,'wb').write(comp(open(p,'rb').read()))
