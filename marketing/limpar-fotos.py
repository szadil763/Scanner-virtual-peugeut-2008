# Tira reflexo e brilho das fotos do multimídia e deixa o texto mais nítido (OpenCV).
# Uso: python3 marketing/limpar-fotos.py <pasta-das-fotos-originais> <pasta-de-saida>
import sys, cv2, numpy as np
src, out = sys.argv[1], sys.argv[2]
sc=4080/1200
crops={"saude":("46791ba4",(200,98,1160,698)),"midias":("6fad2ef0",(130,120,1150,758)),"manut":("0c4e05ab",(160,95,1080,670))}
# força da remoção do reflexo e limiar dos botões claros por foto
PAR={"saude":(1.0,150,True),"manut":(1.0,150,True),"midias":(0.55,105,False)}
for k,(n,b) in crops.items():
    alpha,bth,has_lamp=PAR[k]
    im=cv2.imread(f"{src}/{n}-image.jpg")
    x0,y0,x1,y1=[int(v*sc) for v in b]
    c=im[y0:y1,x0:x1]
    c=cv2.resize(c,(1600,round(1600*c.shape[0]/c.shape[1])),interpolation=cv2.INTER_AREA)
    H,W=c.shape[:2]
    f=c.astype(np.float32)
    gray=f.mean(axis=2)
    # 1) lâmpada refletida: mancha muito clara e grande no topo (barra do multimídia) -> preenche com o fundo
    top=((gray>160)&(np.arange(H)[:,None]<H*0.13)).astype(np.uint8)
    nlab,lab,stats,_=cv2.connectedComponentsWithStats(top)
    lamp=np.zeros_like(top)
    for i in range(1,nlab):
        if stats[i,cv2.CC_STAT_AREA]>3500: lamp[lab==i]=1   # ícones são pequenos; a lâmpada é uma mancha grande
    lamp=cv2.dilate(lamp,np.ones((45,45),np.uint8))
    if has_lamp and lamp.sum():
        c=cv2.inpaint(c,lamp*255,9,cv2.INPAINT_TELEA); f=c.astype(np.float32); gray=f.mean(axis=2)
    # 2) reflexo largo: camada suave acima do nível dos cartões
    op=cv2.morphologyEx(f,cv2.MORPH_OPEN,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(41,41)))
    floor=np.percentile(op.reshape(-1,3),3,axis=0)
    refl=cv2.GaussianBlur(op,(0,0),25)
    R=np.clip(refl-(floor+np.array([10,8,6],np.float32)),0,None)
    # botões claros da interface não são reflexo: protege
    btn=(cv2.morphologyEx(gray,cv2.MORPH_OPEN,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(25,25)))>bth).astype(np.float32)
    btn=cv2.GaussianBlur(cv2.dilate(btn,np.ones((15,15))),(0,0),6)[...,None]
    o=np.clip(f-alpha*R*(1-btn),0,255)
    # 3) cor: tira o tom amarelado da luz ambiente dos tons quase neutros (texto branco, fundo);
    #    vermelho, amarelo e verde da interface têm cor forte e ficam como estão
    labi=cv2.cvtColor(np.clip(o,0,255).astype(np.uint8),cv2.COLOR_BGR2LAB).astype(np.float32)
    a,bb=labi[...,1]-128,labi[...,2]-128
    chroma=np.sqrt(a*a+bb*bb)
    thr=np.where(labi[...,0]>165,42,30)   # texto claro: só vermelho/amarelo/verde de verdade mantêm a cor
    keep=np.clip((chroma-thr)/15,0,1)       # 0 = quase neutro, 1 = cor forte
    fac=keep+(1-keep)*0.25
    labi[...,1]=128+a*fac; labi[...,2]=128+bb*fac
    o=cv2.cvtColor(labi.astype(np.uint8),cv2.COLOR_LAB2BGR).astype(np.float32)
    # leve contraste: fundo escuro de verdade
    o=np.clip((o-np.percentile(o,2))*1.08,0,255)
    # 4) nitidez
    bl=cv2.GaussianBlur(o,(0,0),1.6)
    o=np.clip(o*1.7-bl*0.7,0,255).astype(np.uint8)
    cv2.imwrite(f"{out}/{k}_2.png",o)
