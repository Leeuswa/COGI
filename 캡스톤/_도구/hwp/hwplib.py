import olefile, zlib, struct
TAGS={16:'DOCUMENT_PROPERTIES',66:'PARA_HEADER',67:'PARA_TEXT',68:'PARA_CHAR_SHAPE',69:'PARA_LINE_SEG',70:'PARA_RANGE_TAG',71:'CTRL_HEADER',72:'LIST_HEADER',73:'PAGE_DEF',74:'FOOTNOTE_SHAPE',75:'PAGE_BORDER_FILL',76:'SHAPE_COMPONENT',77:'TABLE',85:'SHAPE_COMPONENT_PICTURE',87:'CTRL_DATA'}
def read_records(data):
    recs=[];i=0
    while i<len(data):
        h=struct.unpack_from('<I',data,i);h=h[0];i+=4
        tag=h&0x3ff;lvl=(h>>10)&0x3ff;size=h>>20
        if size==0xfff: size=struct.unpack_from('<I',data,i)[0];i+=4
        recs.append([tag,lvl,data[i:i+size]]);i+=size
    return recs
def write_records(recs):
    out=bytearray()
    for tag,lvl,p in recs:
        size=len(p)
        if size>=0xfff:
            out+=struct.pack('<I',tag|(lvl<<10)|(0xfff<<20))+struct.pack('<I',size)
        else: out+=struct.pack('<I',tag|(lvl<<10)|(size<<20))
        out+=p
    return bytes(out)
def decomp(b): return zlib.decompress(b,-15)
def comp(b):
    c=zlib.compressobj(9,zlib.DEFLATED,-15); return c.compress(b)+c.flush()
def text_of(p):
    # decode para text, showing controls
    s='';i=0
    while i<len(p):
        c=struct.unpack_from('<H',p,i)[0]
        if c<32:
            if c in (0,10,13,24,25,26,27,28,29,30,31): s+={13:'\\r',10:'\\n'}.get(c,'<%d>'%c);i+=2
            else: s+='{%d:%s}'%(c,p[i+2:i+6][::-1].decode('latin1'));i+=16
        else: s+=chr(c);i+=2
    return s
