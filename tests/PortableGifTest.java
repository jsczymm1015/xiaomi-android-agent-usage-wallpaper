package local.codex.wallpapers;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.metadata.IIOMetadata;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.w3c.dom.Node;

public final class PortableGifTest {
    static int checked;
    static void check(boolean value,String label){checked++;if(!value)throw new AssertionError(label);}
    static Node find(Node n,String name){if(name.equals(n.getNodeName()))return n;for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling()){Node result=find(c,name);if(result!=null)return result;}return null;}
    public static void main(String[] args)throws Exception{
        int w=320,h=240;ByteArrayOutputStream buffer=new ByteArrayOutputStream();PortableGif gif=new PortableGif(buffer,w,h);
        int[] pixels=new int[w*h];
        for(int frame=0;frame<3;frame++){
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=0xff000000|((x/5+frame)%2==0?0xff0000:0x00ff00);
            gif.frame(pixels,12);
        }
        gif.finish();byte[] bytes=buffer.toByteArray();check(bytes[bytes.length-1]==0x3b,"GIF trailer");
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            ImageReader reader=ImageIO.getImageReadersByFormatName("gif").next();reader.setInput(input);
            check(reader.getNumImages(true)==3,"Three frames decode");
            BufferedImage first=reader.read(0),second=reader.read(1),third=reader.read(2);
            check(first.getWidth()==w&&first.getHeight()==h,"Dimensions decode");
            check((first.getRGB(1,1)&0xffffff)==0xff0000,"Palette red");check((first.getRGB(6,1)&0xffffff)==0x00ff00,"Palette green");
            check(first.getRGB(1,1)!=second.getRGB(1,1),"Consecutive frames differ");check(first.getRGB(1,1)==third.getRGB(1,1),"Later frame decodes after clear blocks");
            IIOMetadata metadata=reader.getImageMetadata(0);Node root=metadata.getAsTree("javax_imageio_gif_image_1.0");
            Node control=find(root,"GraphicControlExtension");check("12".equals(control.getAttributes().getNamedItem("delayTime").getNodeValue()),"120 ms frame delay");
            Node application=find(root,"ApplicationExtension");check(application!=null&&"NETSCAPE".equals(application.getAttributes().getNamedItem("applicationID").getNodeValue()),"Loop extension exists");
            byte[] repeat=(byte[])((javax.imageio.metadata.IIOMetadataNode)application).getUserObject();check(repeat.length==3&&repeat[0]==1&&repeat[1]==0&&repeat[2]==0,"Infinite loop metadata");reader.dispose();
        }
        boolean rejected=false;try{gif.frame(pixels,12);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Finished encoder rejects frame");
        System.out.println("PortableGifTest: "+checked+" assertions passed");
    }
}
