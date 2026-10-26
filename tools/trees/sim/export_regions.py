"""Cuts one 192x192 window of the real map per biome (the most typical one) for the forest simulation and writes
sim.cfg (species mixes, densities) from the generator data, so the simulation always uses the same numbers as the game.

usage: python3 tools/trees/sim/export_regions.py OUT_DIR
"""
import os, sys, json, struct
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "..")
sys.path.insert(0, os.path.join(ROOT, "tools", "map_factory")); sys.path.insert(0, os.path.join(HERE, ".."))
import numpy as np, clm
import gen_worldgen as G
d=os.path.join(ROOT,'src','main','resources','cursed_lands_map')+'/'
meta=json.load(open(d+'meta.json')); names=meta['biomes']
bio=clm.read_layer(d+'biomes.bin'); sur=clm.read_layer(d+'surface.bin'); wat=clm.read_layer(d+'water.bin'); h=clm.read_layer(d+'heights.bin')
WIN=192; CELLS=WIN//4
out=sys.argv[1]
want=['dead_forest','cursed_lands','necropolis','blood_canyon','spire_peaks','ash_wastes','bone_valley','blood_fen']
rng=np.random.default_rng(5)
regions=[]
for name in want:
    bi=names.index(name)
    m=(bio==bi).astype(np.float32)
    ks=sur[::4,::4]
    soil=np.isin(ks,[0,1,6,7,8]).astype(np.float32)
    rock=np.isin(ks,[0,1,2,3,4,6,7,8,9]).astype(np.float32)
    good=rock if name in ('spire_peaks','ash_wastes','blood_canyon','bone_valley') else soil
    def integral(a): return np.pad(a.cumsum(0).cumsum(1),((1,0),(1,0)))
    I=integral(m); GI=integral(good*m)
    best=None
    for cz in range(24,1536-CELLS-24,6):
        for cx in range(24,1536-CELLS-24,6):
            f=(I[cz+CELLS,cx+CELLS]-I[cz,cx+CELLS]-I[cz+CELLS,cx]+I[cz,cx])/(CELLS*CELLS)
            g=(GI[cz+CELLS,cx+CELLS]-GI[cz,cx+CELLS]-GI[cz+CELLS,cx]+GI[cz,cx])/(CELLS*CELLS)
            sc=f+g
            if best is None or sc>best[0]: best=(sc,cx,cz,f)
    sc,cx,cz,f=best
    x0=cx*4; z0=cz*4
    H=h[z0:z0+WIN,x0:x0+WIN].astype(np.int16); S=sur[z0:z0+WIN,x0:x0+WIN].astype(np.uint8); Wt=wat[z0:z0+WIN,x0:x0+WIN].astype(np.int16)
    B=np.kron(bio[cz:cz+CELLS,cx:cx+CELLS],np.ones((4,4),dtype=np.uint8))
    path=f'{out}/region_{name}.bin'
    with open(path,'wb') as fo:
        fo.write(struct.pack('>ii',WIN,WIN)); fo.write(H.astype('>i2').tobytes()); fo.write(S.tobytes()); fo.write(Wt.astype('>i2').tobytes()); fo.write(B.astype(np.uint8).tobytes())
    print(name,'frac',round(float(f),2),'origin',x0+meta['origin_x'],z0+meta['origin_z'],'h range',H.min(),H.max(),'water cols',(Wt>H).mean().round(3))
    regions.append(name)
# sim config from the generator data
with open(out+'/sim.cfg','w') as fo:
    fo.write('biomes '+' '.join(names)+'\n')
    for mix,shares in G.MIXES.items():
        sel=G.selector(shares)
        def nm(f): return f.split('/')[-1] if f.startswith('cursed_lands:tree/') else f.split(':')[1]
        fo.write('mix %s %s %s\n'%(mix,nm(sel['default']['feature']),' '.join('%s:%s'%(nm(e['feature']['feature']),e['chance']) for e in sel['features'])))
    for pf,(mix,lo,hi,tag,water) in G.PLACED.items():
        fo.write('placed %s %s %d %d %s %d\n'%(pf,mix,lo,hi,tag,water))
    for biome,trees in G.BIOME_TREES.items():
        fo.write('biome %s %s\n'%(biome,' '.join(trees)))
print(open(out+'/sim.cfg').read())
