package local.codex.wallpapers;

import java.io.IOException;
import java.io.OutputStream;

/** Original GIF89a encoder: opaque RGB332 palette and bounded 9-bit LZW blocks.
 * Full frames and disposal-to-background keep exported loops deterministic.
 * No Android dependency, external library, credential, or network access.
 */
final class PortableGif {
    private final OutputStream out;
    private final int width,height;
    private boolean finished;
    PortableGif(OutputStream output,int w,int h)throws IOException {
        if(output==null||w<1||h<1||w>65535||h>65535)throw new IllegalArgumentException("GIF dimensions");
        out=output;width=w;height=h;
        bytes("GIF89a".getBytes("US-ASCII"));word(w);word(h);out.write(0xf7);out.write(0);out.write(0);
        for(int i=0;i<256;i++){out.write(((i>>5)&7)*255/7);out.write(((i>>2)&7)*255/7);out.write((i&3)*255/3);}
        // NETSCAPE2.0 repeat count 0 means loop forever.
        bytes(new byte[]{0x21,(byte)0xff,11});bytes("NETSCAPE2.0".getBytes("US-ASCII"));bytes(new byte[]{3,1,0,0,0});
    }
    void frame(int[] argb,int delayCentiseconds)throws IOException {
        if(finished||argb==null||argb.length!=width*height||delayCentiseconds<1||delayCentiseconds>65535)throw new IllegalArgumentException("GIF frame");
        bytes(new byte[]{0x21,(byte)0xf9,4,8});word(delayCentiseconds);out.write(0);out.write(0);
        out.write(0x2c);word(0);word(0);word(width);word(height);out.write(0);out.write(8);
        Codes codes=new Codes(out);codes.code(256);int literals=0;
        for(int pixel:argb){
            // Reset well before dictionary growth would require 10-bit codes.
            if(literals==200){codes.code(256);literals=0;}
            int rgb=((pixel>>16)&0xe0)|((pixel>>11)&0x1c)|((pixel>>6)&3);
            codes.code(rgb);literals++;
        }
        codes.code(257);codes.finish();out.write(0);
    }
    void finish()throws IOException{if(!finished){out.write(0x3b);out.flush();finished=true;}}
    private void bytes(byte[] b)throws IOException{out.write(b);}
    private void word(int n)throws IOException{out.write(n&255);out.write((n>>8)&255);}
    private static final class Codes {
        final OutputStream out;final byte[] block=new byte[255];int used,bits,count;
        Codes(OutputStream o){out=o;}
        void code(int c)throws IOException{bits|=c<<count;count+=9;while(count>=8){put(bits&255);bits>>>=8;count-=8;}}
        void put(int b)throws IOException{block[used++]=(byte)b;if(used==255)flush();}
        void flush()throws IOException{if(used>0){out.write(used);out.write(block,0,used);used=0;}}
        void finish()throws IOException{if(count>0)put(bits&255);flush();}
    }
}
