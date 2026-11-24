package com.onlyk.justepicportals.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.onlyk.justepicportals.PortalAnchorBlockEntity;
import com.onlyk.justepicportals.PortalFrame;
import com.onlyk.justepicportals.PortalEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The Astral Relic veil: drifting depth, broad luminous ribbons and living constellations. */
public final class PortalRenderer implements BlockEntityRenderer<PortalAnchorBlockEntity> {
    private static final double TAU=Math.PI*2;
    private record Color(float r,float g,float b) {
        Color light(float amount) { return new Color(r+(1-r)*amount,g+(1-g)*amount,b+(1-b)*amount); }
        Color dark(float amount) { return new Color(r*amount,g*amount,b*amount); }
    }
    public PortalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(PortalAnchorBlockEntity anchor,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var frame=anchor.activeFrame();
        if(frame==null||anchor.getLevel()==null)return;
        double time=anchor.getLevel().getGameTime()+partialTick;
        Vec3 origin=frame.point(0,0,0).subtract(Vec3.atLowerCornerOf(anchor.getBlockPos()));
        Vec3 eye=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(frame.point(frame.width()/2d,frame.height()/2d,0));
        // The base, veil and light layers only composite correctly in that order; see DrawOrderGroup for batching renderers.
        boolean grouped=DrawOrderGroup.begin(buffers);
        try {
            renderSurface(frame,origin,anchor.color().getTextureDiffuseColor(),anchor.effect(),anchor.identity().hashCode(),time,eye,pose,buffers);
        } finally {
            if(grouped)DrawOrderGroup.end(buffers);
        }
    }

    /** Uses the actual portal artwork and animation without a world or fabricated block entity. */
    public static void renderPreview(PoseStack pose,MultiBufferSource buffers,int rgb,PortalEffect effect,double time) {
        renderSurface(new PortalFrame(BlockPos.ZERO,Direction.SOUTH,3,4),Vec3.ZERO,rgb,effect,
                effect.id().hashCode(),time,new Vec3(0,0,4),pose,buffers);
    }

    private static void renderSurface(PortalFrame frame,Vec3 origin,int rgb,PortalEffect effect,int seed,double time,
                                      Vec3 eye,PoseStack pose,MultiBufferSource buffers) {
        var color=new Color(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);
        double width=frame.width(),height=frame.height();
        var transform=pose.last().pose();
        var field=new Canvas(buffers.getBuffer(PortalRenderType.SURFACE),transform,frame,origin);
        field.quad(0,0,width,height,0,color.dark(.17f),.58f);

        // Neutral wisps take the dye tint. Viewing-angle offsets add depth from either side.
        var veil=new Canvas(buffers.getBuffer(PortalRenderType.VEIL),transform,frame,origin);
        double distance=Math.max(2,Math.abs(eye.dot(vector(frame.normal()))));
        double parallaxU=Math.clamp(eye.dot(vector(frame.u()))/distance,-2,2)*.025;
        double parallaxV=Math.clamp(eye.dot(vector(frame.v()))/distance,-2,2)*.025;
        float mistStrength=switch(effect) { case ARCANE,CLIMAX,SIGIL_RAIN,CRYSTAL_DRIFT,SPELLMIST -> .6f; default -> 1; };
        mist(veil,width,height,time,0,parallaxU,parallaxV,color.light(.11f),.32f*mistStrength);
        mist(veil,width,height,time,1,-parallaxU*.7,-parallaxV*.7,color.light(.28f),.17f*mistStrength);

        var energy=new Canvas(buffers.getBuffer(PortalRenderType.LIGHT),transform,frame,origin);
        switch(effect) {
            case ASTRAL -> {
                ribbons(energy,width,height,time,color);
                edgeLight(energy,width,height,time,color);
                constellations(energy,width,height,time,seed,color);
            }
            case ENDER -> { border(energy,width,height,color); ender(energy,width,height,time,seed,color); }
            case ARCANE -> { border(energy,width,height,color); arcane(energy,width,height,time,color); }
            case CLIMAX -> { border(energy,width,height,color); fireworks(energy,width,height,time,seed,color); }
            case SUPERNOVA -> { border(energy,width,height,color); supernova(energy,width,height,time,seed,color); }
            case STELLAR_CONVERGENCE -> { border(energy,width,height,color); convergence(energy,width,height,time,color); }
            case COSMIC_CRESCENDO -> { border(energy,width,height,color); crescendo(energy,width,height,time,seed,color); }
            case SIGIL_RAIN -> { border(energy,width,height,color); sigilRain(energy,width,height,time,seed,color); }
            case CRYSTAL_DRIFT -> { border(energy,width,height,color); crystalDrift(energy,width,height,time,seed,color); }
            case DRAGONWAKE -> {
                border(energy,width,height,color);
                dragonwake(buffers,transform,frame,origin,width,height,time,seed,color);
            }
            case SPELLMIST -> {
                border(energy,width,height,color);
                spellmist(buffers,transform,frame,origin,width,height,time,seed,color);
            }
        }
    }

    private static void mist(Canvas canvas,double width,double height,double time,int layer,double parallaxU,double parallaxV,Color color,float alpha) {
        int columns=Math.clamp((int)(width*4),8,24),rows=Math.clamp((int)(height*4),8,24);
        for(int row=0;row<rows;row++)for(int column=0;column<columns;column++) {
            double x1=width*column/columns,x2=width*(column+1)/columns;
            double y1=height*row/rows,y2=height*(row+1)/rows;
            mistVertex(canvas,x1,y1,width,height,time,layer,parallaxU,parallaxV,color,alpha);
            mistVertex(canvas,x1,y2,width,height,time,layer,parallaxU,parallaxV,color,alpha);
            mistVertex(canvas,x2,y2,width,height,time,layer,parallaxU,parallaxV,color,alpha);
            mistVertex(canvas,x2,y1,width,height,time,layer,parallaxU,parallaxV,color,alpha);
        }
    }
    private static void mistVertex(Canvas canvas,double x,double y,double width,double height,double time,int layer,
                                   double parallaxU,double parallaxV,Color color,float alpha) {
        double fx=x/width,fy=y/height;
        double u=fx*(layer==0?.95:1.17)+time*(layer==0?.00048:-.00031)+layer*.37+parallaxU
                +.045*Math.sin(fy*TAU*1.7+time*.012+layer*2);
        double v=fy*(layer==0?1.18:.89)+time*(layer==0?-.00064:.00043)+layer*.23+parallaxV
                +.035*Math.cos(fx*TAU*1.3-time*.009+layer);
        canvas.vertex(x,y,.002+layer*.003,color,alpha).setUv((float)u,(float)v);
    }

    private static void ribbons(Canvas canvas,double width,double height,double time,Color color) {
        int segments=Math.clamp((int)(height*22),44,176),bands=8;
        var bright=color.light(.37f);
        var core=color.light(.67f);
        for(int ribbon=0;ribbon<2;ribbon++) {
            double phase=ribbon*Math.PI+time*.006;
            for(int segment=0;segment<segments;segment++) {
                double v1=segment/(double)segments,v2=(segment+1d)/segments;
                double y1=.025+v1*(height-.05),y2=.025+v2*(height-.05);
                double x1=width*(.5+.28*Math.sin(v1*TAU+phase));
                double x2=width*(.5+.28*Math.sin(v2*TAU+phase));
                double half1=Math.min(width*.16,1.2)*(.7+.3*Math.sin(v1*Math.PI));
                double half2=Math.min(width*.16,1.2)*(.7+.3*Math.sin(v2*Math.PI));
                float pulse1=(float)(.7+.3*Math.sin(v1*TAU*2-time*.045+ribbon));
                float pulse2=(float)(.7+.3*Math.sin(v2*TAU*2-time*.045+ribbon));
                for(int band=0;band<bands;band++) {
                    double q1=-1+2d*band/bands,q2=-1+2d*(band+1)/bands;
                    float a1=(float)(Math.pow(1-Math.abs(q1),1.25)*.39);
                    float a2=(float)(Math.pow(1-Math.abs(q2),1.25)*.39);
                    canvas.vertex(x1+q1*half1,y1,.012,bright,a1*pulse1);
                    canvas.vertex(x2+q1*half2,y2,.012,bright,a1*pulse2);
                    canvas.vertex(x2+q2*half2,y2,.012,bright,a2*pulse2);
                    canvas.vertex(x1+q2*half1,y1,.012,bright,a2*pulse1);
                }
                canvas.line(x1+half1*.65,y1,x2+half2*.65,y2,.008,.014,core,.5f*pulse1);
                canvas.line(x1-half1*.35,y1,x2-half2*.35,y2,.004,.014,bright,.24f*pulse1);
            }
        }
    }

    private static void edgeLight(Canvas canvas,double width,double height,double time,Color color) {
        var highlight=color.light(.38f);
        border(canvas,width,height,color);
        double radius=Math.min(width,height)*.29;
        for(int corner=0;corner<2;corner++) {
            double cx=corner==0?.028:width-.028,cy=corner==0?height-.028:.028;
            double sign=corner==0?1:-1;
            for(double scale:new double[]{1,.72}) {
                for(int segment=0;segment<28;segment++) {
                    double a1=segment/28d*Math.PI/2,a2=(segment+1d)/28*Math.PI/2;
                    canvas.line(cx+sign*radius*scale*Math.cos(a1),cy-sign*radius*scale*Math.sin(a1),
                            cx+sign*radius*scale*Math.cos(a2),cy-sign*radius*scale*Math.sin(a2),.006,.018,highlight,.4f);
                }
            }
            for(int rune=0;rune<3;rune++) {
                double angle=.26+rune*.52;
                double x=cx+sign*radius*.86*Math.cos(angle),y=cy-sign*radius*.86*Math.sin(angle);
                glyph(canvas,x,y,Math.min(width,height)*.026,rune+corner,highlight,.64f);
            }
        }
        double perimeter=2*(width+height);
        int motes=Math.clamp((int)((width+height)*2),8,40);
        for(int index=0;index<motes;index++) {
            double p=(index*perimeter/motes+time*.012)%perimeter;
            double x,y;
            if(p<width){x=p;y=.04;}
            else if(p<width+height){x=width-.04;y=p-width;}
            else if(p<width*2+height){x=width*2+height-p;y=height-.04;}
            else{x=.04;y=perimeter-p;}
            canvas.star(Math.clamp(x,.045,width-.045),Math.clamp(y,.045,height-.045),.028,.019,highlight,.56f);
        }
    }
    private static void border(Canvas canvas,double width,double height,Color color) {
        var highlight=color.light(.38f);
        for(int band=5;band>=0;band--) {
            double inset=.025+band*.013;
            float alpha=band==0?.75f:.035f;
            double thickness=band==0?.008:.017;
            canvas.line(inset,inset,width-inset,inset,thickness,.016,highlight,alpha);
            canvas.line(width-inset,inset,width-inset,height-inset,thickness,.016,highlight,alpha);
            canvas.line(width-inset,height-inset,inset,height-inset,thickness,.016,highlight,alpha);
            canvas.line(inset,height-inset,inset,inset,thickness,.016,highlight,alpha);
        }
    }

    private static void constellations(Canvas canvas,double width,double height,double time,int identity,Color color) {
        int count=Math.clamp((int)(width*height*.45)+7,8,32);
        double seed=(identity&65535)/65535d;
        double previousX=0,previousY=0;
        var lineColor=color.light(.28f);
        var stars=color.light(.72f);
        for(int index=0;index<count;index++) {
            double x=.13+fraction(index*.61803398875+seed+time*.00004)*(width-.26);
            double y=.13+fraction(index*.41421356237+seed*.37+time*.00006)*(height-.26);
            if(index>0&&Math.hypot(x-previousX,y-previousY)<Math.min(width,height)*.86)
                canvas.line(previousX,previousY,x,y,.004,.023,lineColor,.31f);
            float pulse=(float)(.56+.24*Math.sin(time*.028+index*1.9));
            double size=Math.min(.06,Math.min(width,height)*.026)*(.85+.15*Math.sin(time*.04+index));
            canvas.star(x,y,size*2.2,.024,color,.07f);
            canvas.star(x,y,size,.025,stars,pulse);
            if(index%4==1)glyph(canvas,x,y,size*1.6,index,lineColor,.52f);
            previousX=x;previousY=y;
        }
        int sparks=Math.clamp((int)(width*height*2.3)+10,12,96);
        for(int index=0;index<sparks;index++) {
            double x=.08+fraction(index*.754877666+seed+time*.00014)*(width-.16);
            double y=.08+fraction(index*.569840296+seed*.19+time*.00043)*(height-.16);
            float pulse=(float)Math.pow(.5+.5*Math.sin(time*.038+index*2.4),3);
            canvas.star(x,y,.008+pulse*.012,.027,stars,.16f+pulse*.48f);
        }
    }
    private static void glyph(Canvas canvas,double x,double y,double size,int variant,Color color,float alpha) {
        size=Math.min(size,.12);
        canvas.line(x-size,y,x,y+size,.004,.026,color,alpha);
        canvas.line(x,y+size,x+size,y,.004,.026,color,alpha);
        canvas.line(x+size,y,x,y-size,.004,.026,color,alpha);
        canvas.line(x,y-size,x-size,y,.004,.026,color,alpha);
        if((variant&1)==0)canvas.line(x-size*.6,y,x+size*.6,y,.003,.026,color,alpha);
        else canvas.line(x,y-size*1.3,x,y+size*1.3,.003,.026,color,alpha);
    }

    /** The former Fae design: broad streams and drifting leaf-shaped lights. */
    private static void ender(Canvas canvas,double width,double height,double time,int seed,Color color) {
        var glow=color.light(.38f);
        int segments=Math.clamp((int)(height*20),40,150);
        for(int stream=0;stream<3;stream++)for(int segment=0;segment<segments;segment++) {
            double v1=segment/(double)segments,v2=(segment+1d)/segments;
            double x1=width*(.5+(stream-1)*.09+.2*Math.sin(v1*TAU*.8-time*.006+stream*.36));
            double x2=width*(.5+(stream-1)*.09+.2*Math.sin(v2*TAU*.8-time*.006+stream*.36));
            double half=width*(stream==1?.12:.075);
            for(int band=0;band<8;band++) {
                double q1=-1+band/4d,q2=-1+(band+1)/4d;
                float a1=(float)Math.pow(1-Math.abs(q1),1.5)*.28f;
                float a2=(float)Math.pow(1-Math.abs(q2),1.5)*.28f;
                canvas.vertex(x1+half*q1,.03+v1*(height-.06),.012,glow,a1);
                canvas.vertex(x2+half*q1,.03+v2*(height-.06),.012,glow,a1);
                canvas.vertex(x2+half*q2,.03+v2*(height-.06),.012,glow,a2);
                canvas.vertex(x1+half*q2,.03+v1*(height-.06),.012,glow,a2);
            }
            canvas.line(x1+half*.5,.03+v1*(height-.06),x2+half*.5,.03+v2*(height-.06),.004,.018,color.light(.7f),.36f);
        }
        int count=Math.clamp((int)(width*height*.7)+4,5,24);
        for(int i=0;i<count;i++) {
            double x=.13+fraction(i*.618+(seed&255)*.003+Math.sin(time*.006+i)*.035)*(width-.26);
            double y=.13+fraction(i*.413+time*.0007)*(height-.26);
            leaf(canvas,x,y,Math.min(.12,Math.min(width,height)*.07),time*.003+i*1.7,glow,.6f);
        }
        motes(canvas,width,height,time,seed,color,16);
    }

    private static void leaf(Canvas canvas,double x,double y,double size,double angle,Color color,float alpha) {
        double dx=Math.cos(angle)*size,dy=Math.sin(angle)*size;
        double px=-dy*.44,py=dx*.44;
        canvas.line(x-dx,y-dy,x+px,y+py,.004,.024,color,alpha);
        canvas.line(x+px,y+py,x+dx,y+dy,.004,.024,color,alpha);
        canvas.line(x+dx,y+dy,x-px,y-py,.004,.024,color,alpha);
        canvas.line(x-px,y-py,x-dx,y-dy,.004,.024,color,alpha);
        canvas.line(x-dx,y-dy,x+dx,y+dy,.003,.024,color,alpha*.7f);
    }

    /** The Arcane concept's turning circles, rune tracks and central geometric seal. */
    private static void arcane(Canvas canvas,double width,double height,double time,Color color) {
        double cx=width/2,cy=height/2,r=Math.min(width,height)*.43;
        var bright=color.light(.55f);
        for(int band=0;band<4;band++) {
            double radius=r*(1-band*.18);
            ring(canvas,cx,cy,radius,radius,time*.002*(band%2==0?1:-1),bright,.42f,.004);
        }
        int runes=12;
        for(int i=0;i<runes;i++) {
            double angle=i*TAU/runes-time*.003;
            glyph(canvas,cx+Math.cos(angle)*r*.83,cy+Math.sin(angle)*r*.83,r*.065,i,bright,.68f);
        }
        for(int i=0;i<8;i++) {
            double a=i*TAU/8+time*.0014,b=a+TAU*3/8;
            canvas.line(cx+Math.cos(a)*r*.57,cy+Math.sin(a)*r*.57,
                    cx+Math.cos(b)*r*.57,cy+Math.sin(b)*r*.57,.005,.023,bright,.5f);
        }
        canvas.star(cx,cy,r*.19,.026,color.light(.85f),.8f);
        for(int i=0;i<7;i++) {
            double y=.12+fraction(i/7d+time*.0004)*(height-.24);
            glyph(canvas,width*.13,y,Math.min(.07,width*.04),i,bright,.36f);
            glyph(canvas,width*.87,height-y,Math.min(.07,width*.04),i+1,bright,.36f);
        }
    }

    /** Repeating visual fireworks only: no firework entities, explosions or travel effects. */
    private static void fireworks(Canvas canvas,double width,double height,double time,int seed,Color color) {
        int bursts=Math.clamp((int)(width*height/6)+3,3,9);
        var bright=color.light(.65f);
        for(int burst=0;burst<bursts;burst++) {
            double clock=time/(54+burst*9d)+burst*.271;
            int cycle=(int)Math.floor(clock);
            double age=fraction(clock),key=seed*.000013+cycle*7.17+burst*11.37;
            double cx=.15+noise(key+1)*(width-.3),cy=.15+noise(key+2)*(height-.3);
            double room=Math.min(Math.min(cx,width-cx),Math.min(cy,height-cy));
            double radius=Math.min(Math.min(width,height)*.38,room*.75)*Math.pow(age,.45);
            float fade=(float)Math.pow(Math.sin(age*Math.PI),.65);
            int rays=20+burst%3*4;
            for(int ray=0;ray<rays;ray++) {
                double angle=ray*TAU/rays+noise(key+3)*TAU;
                double length=radius*(.7+.3*noise(key+ray*2.13));
                double dx=Math.cos(angle)*length,dy=Math.sin(angle)*length;
                double gravity=radius*.25*age*age;
                double x=cx+dx,y=cy+dy-gravity;
                canvas.line(cx+dx*.69,cy+dy*.69-gravity*.5,x,y,.02,.021,color,.065f*fade);
                canvas.line(cx+dx*.81,cy+dy*.81-gravity*.7,x,y,.004,.024,bright,.7f*fade);
                canvas.star(x,y,Math.min(.028,room*.07),.025,bright,.9f*fade);
            }
            if(age<.23)canvas.star(cx,cy,room*.1*(1-age/.23),.027,bright,(float)(1-age/.23));
        }
    }

    /** The most elaborate pattern: a radiant star inside a turning celestial orrery. */
    private static void supernova(Canvas canvas,double width,double height,double time,int seed,Color color) {
        ribbons(canvas,width,height,time*.65,color);
        double cx=width/2,cy=height/2,r=Math.min(width,height)*.44;
        var bright=color.light(.68f);
        for(int i=0;i<4;i++) {
            double angle=time*.002*(i%2==0?1:-1)+i*Math.PI/4;
            ring(canvas,cx,cy,r*(1-i*.11),r*(.36+i*.07),angle,bright,.5f,.005);
            for(int star=0;star<3;star++) {
                double a=time*.012*(i%2==0?1:-1)+star*TAU/3+i;
                double x=Math.cos(a)*r*(1-i*.11),y=Math.sin(a)*r*(.36+i*.07);
                canvas.star(cx+x*Math.cos(angle)-y*Math.sin(angle),cy+x*Math.sin(angle)+y*Math.cos(angle),
                        Math.min(.085,r*.065),.027,bright,.78f);
            }
        }
        radiance(canvas,cx,cy,r*.71,time,color,.9f);
        ring(canvas,cx,cy,r*.27,r*.27,-time*.003,bright,.56f,.009);
        motes(canvas,width,height,time,seed,color,36);
    }

    private static void convergence(Canvas canvas,double width,double height,double time,Color color) {
        double cx=width/2,cy=height/2,r=Math.min(width,height);
        double cycle=.5+.5*Math.sin(time*.012);
        var bright=color.light(.64f);
        for(int i=0;i<3;i++) {
            double angle=i*TAU/3+time*.0025;
            double x=cx+Math.cos(angle)*r*.12*cycle,y=cy+Math.sin(angle)*r*.12*cycle;
            ring(canvas,x,y,r*.29,r*.21,angle,bright,.48f,.005);
            double orbit=time*.015+i*TAU/3;
            canvas.star(x+Math.cos(orbit)*r*.25,y+Math.sin(orbit)*r*.17,Math.min(.08,r*.045),.026,bright,.8f);
            canvas.line(x,y,cx,cy,.006,.023,bright,(float)(.2+.4*(1-cycle)));
        }
        radiance(canvas,cx,cy,r*.15,time,color,(float)(.45+.35*(1-cycle)));
    }

    /** Curved wavefronts brighten, sweep up the veil and dissolve into the next phrase. */
    private static void crescendo(Canvas canvas,double width,double height,double time,int seed,Color color) {
        var bright=color.light(.55f);
        int segments=Math.clamp((int)(width*14),28,100);
        for(int wave=0;wave<4;wave++) {
            double phase=fraction(time*.004+wave*.25);
            float pulse=(float)Math.pow(Math.sin(phase*Math.PI),1.3);
            for(int segment=0;segment<segments;segment++) {
                double u1=segment/(double)segments,u2=(segment+1d)/segments;
                double y1=height*(.16+.68*phase+.09*Math.sin(u1*Math.PI*1.6-time*.008+wave*.3));
                double y2=height*(.16+.68*phase+.09*Math.sin(u2*Math.PI*1.6-time*.008+wave*.3));
                double x1=.03+u1*(width-.06),x2=.03+u2*(width-.06);
                for(int band=0;band<8;band++) {
                    double q1=-1+band/4d,q2=-1+(band+1)/4d;
                    float a1=(float)Math.pow(1-Math.abs(q1),1.4)*pulse*.36f;
                    float a2=(float)Math.pow(1-Math.abs(q2),1.4)*pulse*.36f;
                    canvas.vertex(x1,y1+q1*height*.055,.014,bright,a1);
                    canvas.vertex(x2,y2+q1*height*.055,.014,bright,a1);
                    canvas.vertex(x2,y2+q2*height*.055,.014,bright,a2);
                    canvas.vertex(x1,y1+q2*height*.055,.014,bright,a2);
                }
                canvas.line(x1,y1,x2,y2,.005,.022,bright,.56f*pulse);
            }
        }
        motes(canvas,width,height,time,seed,color,20);
    }

    /** Staggered falling rune chains dissolve into small sparks near the bottom edge. */
    private static void sigilRain(Canvas canvas,double width,double height,double time,int seed,Color color) {
        int columns=Math.clamp((int)(width*2.2),3,14);
        var bright=color.light(.65f);
        for(int column=0;column<columns;column++) {
            double period=110+noise(seed+column*3.17)*95;
            double phase=fraction(time/period+noise(seed+column*9.71));
            double x=(column+.5)*width/columns;
            double size=Math.min(.15,width/columns*.21);
            for(int row=0;row<4;row++) {
                double age=fraction(phase+row*.25),y=.16+(1-age)*(height-.32);
                float alpha=(float)(Math.sin(Math.PI*age)*(.62+.25*(1-age)));
                var shape=new Shape(canvas,x+size*.16*Math.sin(time*.015+row+column),y,size,.08*Math.sin(time*.01+column));
                int variant=(column*3+row)%6;
                shape.line(-.65,-.7,0,.85,.035,.026,bright,alpha);
                shape.line(0,.85,.65,-.7,.035,.026,bright,alpha);
                if(variant%2==0)shape.line(-.48,-.18,.48,-.18,.03,.026,bright,alpha);
                if(variant%3==0)shape.line(-.75,.55,.75,.55,.03,.026,bright,alpha);
                if(variant%3==1)shape.line(0,-.85,0,.32,.03,.026,bright,alpha);
                if(variant%3==2)shape.line(-.62,.2,.62,-.55,.03,.026,bright,alpha);
                double trail=Math.min(.36,height*.12)*(1-age);
                canvas.line(x,y+size,x,y+size+Math.min(trail,height-y-size-.03),.012,.02,color,alpha*.12f);
                if(age>.65)for(int spark=0;spark<3;spark++) {
                    double sx=x+(noise(column*8+row*13+spark)-.5)*size*2;
                    double sy=Math.clamp(y+(noise(row*3+spark)-.5)*size*2,.04,height-.04);
                    canvas.star(sx,sy,.012,.027,bright,alpha*(float)((age-.65)/.35));
                }
            }
        }
    }

    /** Faceted crystals turn, separate into shards, and reform around their original hearts. */
    private static void crystalDrift(Canvas canvas,double width,double height,double time,int seed,Color color) {
        int count=Math.clamp((int)(width*height*.65)+4,5,22);
        for(int i=0;i<count;i++) {
            double cx=width*(.18+.64*noise(seed+i*13.7))+.025*Math.sin(time*.01+i);
            double cy=height*(.16+.68*noise(seed+i*7.3))+.04*Math.cos(time*.012+i*2);
            double room=Math.min(Math.min(cx,width-cx),Math.min(cy,height-cy));
            double size=Math.min(.3,room*.32),age=fraction(time/(150+i*7d)+i*.381);
            double fracture=smooth(.50,.72,age)*(1-smooth(.80,1,age));
            double rotation=time*.005*(i%2==0?1:-1)+i*1.3;
            float pulse=(float)(.64+.22*Math.sin(time*.025+i));
            crystal(new Shape(canvas,cx,cy,size,rotation),time*.018+i,color,pulse*(float)(1-fracture*.88));
            for(int shard=0;shard<3;shard++) {
                double angle=rotation+shard*TAU/3;
                double distance=size*fracture*1.1;
                var fragment=new Shape(canvas,cx+Math.cos(angle)*distance,cy+Math.sin(angle)*distance,
                        size*.43,rotation+fracture*(shard-1)*1.5);
                crystal(fragment,time*.024+shard,color,(float)(fracture*.8));
            }
            canvas.star(cx,cy,size*.28,.028,color.light(.9f),pulse*.65f);
        }
    }
    private static void crystal(Shape shape,double turn,Color color,float alpha) {
        double waist=.42+.16*Math.cos(turn);
        shape.triangle(0,1.35,-waist,.35,0,-1.35,.023,color.light(.18f),alpha*.35f);
        shape.triangle(0,1.35,0,-1.35,waist,.35,.023,color.light(.68f),alpha*.48f);
        shape.line(0,1.35,-waist,.35,.02,.026,color.light(.8f),alpha);
        shape.line(-waist,.35,0,-1.35,.02,.026,color.light(.5f),alpha);
        shape.line(0,-1.35,waist,.35,.02,.026,color.light(.65f),alpha);
        shape.line(waist,.35,0,1.35,.02,.026,color.light(.9f),alpha);
        shape.line(0,1.35,0,-1.35,.012,.027,color.light(.8f),alpha*.65f);
    }

    /** Approved spectral dragon artwork drifts and softly undulates through its ember wake. */
    private static void dragonwake(MultiBufferSource buffers,Matrix4f transform,PortalFrame frame,Vec3 origin,
                                   double width,double height,double time,int seed,Color color) {
        int count=width*height>=24?3:2;
        double size=Math.min(1.8,Math.min(width*.42,height/(count*2.35)));
        double margin=size*1.1+.035;
        // Finish each render type before requesting another buffer: the fallback source
        // flushes its previous builder when a different texture or vertex format is selected.
        for(int pass=0;pass<2;pass++) {
            var canvas=new Canvas(buffers.getBuffer(pass==0?PortalRenderType.DRAGON:PortalRenderType.EMBERS),transform,frame,origin);
            for(int dragon=0;dragon<count;dragon++) {
            double phase=fraction(time/(220+dragon*61d)+dragon*.413+noise(seed)*.2);
            double cx=margin+phase*(width-margin*2);
            double cy=(dragon+.5)*height/count+Math.sin(time*.009+dragon*2)*size*.04;
            double angle=.055*Math.sin(time*.007+dragon*2);
            float alpha=(float)Math.pow(Math.sin(Math.PI*phase),.8)*.76f;
            if(pass==0) {spectralDragon(canvas,cx,cy,size,angle,time+dragon*37,color.light(.3f),alpha);continue;}
            for(int ember=0;ember<14;ember++) {
                double age=fraction(time*.009+ember/14d),x=cx-size*(.2+age*.85);
                double y=cy+Math.sin(age*TAU+time*.018+dragon)*size*.24;
                canvas.star(Math.clamp(x,.035,width-.035),Math.clamp(y,.035,height-.035),
                        size*(.009+.007*noise(seed+ember)),.034,color.light(.65f),alpha*(float)(1-age)*.52f);
            }
            }
        }
    }
    private static void spectralDragon(Canvas canvas,double cx,double cy,double size,double angle,double time,Color color,float alpha) {
        int divisions=8;
        double ca=Math.cos(angle),sa=Math.sin(angle);
        for(int row=0;row<divisions;row++)for(int column=0;column<divisions;column++) {
            double u1=column/(double)divisions,u2=(column+1d)/divisions;
            double v1=row/(double)divisions,v2=(row+1d)/divisions;
            dragonVertex(canvas,cx,cy,size,ca,sa,u1,v1,time,color,alpha);
            dragonVertex(canvas,cx,cy,size,ca,sa,u1,v2,time,color,alpha);
            dragonVertex(canvas,cx,cy,size,ca,sa,u2,v2,time,color,alpha);
            dragonVertex(canvas,cx,cy,size,ca,sa,u2,v1,time,color,alpha);
        }
    }
    private static void dragonVertex(Canvas canvas,double cx,double cy,double size,double ca,double sa,
                                     double u,double v,double time,Color color,float alpha) {
        double x=(u*2-1)*size+Math.sin(v*TAU+time*.025)*Math.sin(u*Math.PI)*size*.022;
        double y=(v*2-1)*size+Math.sin(u*TAU-time*.018)*Math.sin(v*Math.PI)*size*.016;
        canvas.vertex(cx+x*ca-y*sa,cy+x*sa+y*ca,.03,color,alpha).setUv((float)u,(float)(1-v));
    }

    /** Native potion-particle curls, staggered in size, lifetime, location and animation age. */
    private static void spellmist(MultiBufferSource buffers,Matrix4f transform,PortalFrame frame,Vec3 origin,
                                  double width,double height,double time,int seed,Color color) {
        int count=Math.clamp((int)Math.round(width*height*.55)+3,4,22);
        double[] xs=new double[count],ys=new double[count],radii=new double[count];
        // Best-candidate scattering keeps the placement irregular. It favors space between
        // centers without imposing a grid or forbidding the small overlaps of drifting curls.
        for(int i=0;i<count;i++) {
            double radius=Math.min(.7,Math.min(width,height)*(.09+.045*noise(seed+i*3.71)));
            double margin=radius*1.25+.025,best=-1;
            radii[i]=radius;
            for(int candidate=0;candidate<24;candidate++) {
                double key=seed*.00017+i*53.7+candidate*19.31;
                double x=margin+noise(key)*(width-margin*2),y=margin+noise(key+2.1)*(height-margin*2);
                double nearest=Double.MAX_VALUE;
                for(int previous=0;previous<i;previous++) {
                    double dx=x-xs[previous],dy=y-ys[previous],spacing=radius+radii[previous];
                    nearest=Math.min(nearest,(dx*dx+dy*dy)/(spacing*spacing));
                }
                if(nearest>best){best=nearest;xs[i]=x;ys[i]=y;}
            }
        }
        for(int texture=0;texture<8;texture++) {
            Canvas canvas=null;
            for(int i=0;i<count;i++) {
                double period=65+noise(seed+i*7.31)*100;
                double clock=time/period+noise(seed+i*11.9),age=fraction(clock);
                if(7-Math.min(7,(int)(age*8))!=texture)continue;
                if(canvas==null)canvas=new Canvas(buffers.getBuffer(PortalRenderType.SPELLMIST[texture]),transform,frame,origin);
                double cycle=Math.floor(clock),key=seed*.00017+i*17.1+cycle*5.3;
                double cx=xs[i]+(noise(key)-.5)*radii[i]*.24;
                double cy=ys[i]+(noise(key+1)-.5)*radii[i]*.24+(age-.5)*radii[i]*.2;
                float alpha=(float)Math.pow(Math.sin(Math.PI*age),.85)*.85f;
                double angle=(noise(key+3)-.5)*.8+age*.25;
                double size=radii[i]*(.75+.25*noise(key+2))/(Math.abs(Math.cos(angle))+Math.abs(Math.sin(angle)));
                sprite(canvas,cx,cy,size,angle,color.light(.3f),alpha);
            }
        }
    }
    private static void sprite(Canvas canvas,double cx,double cy,double size,double angle,Color color,float alpha) {
        double ca=Math.cos(angle)*size,sa=Math.sin(angle)*size;
        canvas.vertex(cx-ca+sa,cy-sa-ca,.03,color,alpha).setUv(0,1);
        canvas.vertex(cx-ca-sa,cy-sa+ca,.03,color,alpha).setUv(0,0);
        canvas.vertex(cx+ca-sa,cy+sa+ca,.03,color,alpha).setUv(1,0);
        canvas.vertex(cx+ca+sa,cy+sa-ca,.03,color,alpha).setUv(1,1);
    }
    private static double smooth(double low,double high,double value) {
        double t=Math.clamp((value-low)/(high-low),0,1);return t*t*(3-2*t);
    }

    private static void ring(Canvas canvas,double cx,double cy,double rx,double ry,double rotation,Color color,float alpha,double thickness) {
        int segments=96;
        double ca=Math.cos(rotation),sa=Math.sin(rotation);
        for(int i=0;i<segments;i++) {
            double a=i*TAU/segments,b=(i+1d)*TAU/segments;
            double x1=Math.cos(a)*rx,y1=Math.sin(a)*ry,x2=Math.cos(b)*rx,y2=Math.sin(b)*ry;
            canvas.line(cx+x1*ca-y1*sa,cy+x1*sa+y1*ca,cx+x2*ca-y2*sa,cy+x2*sa+y2*ca,
                    thickness*3,.021,color,alpha*.1f);
            canvas.line(cx+x1*ca-y1*sa,cy+x1*sa+y1*ca,cx+x2*ca-y2*sa,cy+x2*sa+y2*ca,
                    thickness,.024,color,alpha);
        }
    }

    private static void radiance(Canvas canvas,double cx,double cy,double radius,double time,Color color,float alpha) {
        var center=color.light(.9f);
        for(int i=0;i<16;i++) {
            double angle=i*TAU/16+time*.001;
            double dx=Math.cos(angle),dy=Math.sin(angle);
            double length=radius*(i%2==0?1:.6)*(.94+.06*Math.sin(time*.025));
            double half=radius*.047;
            canvas.vertex(cx-dy*half,cy+dx*half,.025,center,alpha*.8f);
            canvas.vertex(cx+dx*length,cy+dy*length,.025,color,.02f);
            canvas.vertex(cx+dx*length,cy+dy*length,.025,color,.02f);
            canvas.vertex(cx+dy*half,cy-dx*half,.025,center,alpha*.8f);
        }
        canvas.star(cx,cy,radius*.29,.027,center,alpha);
    }

    private static void motes(Canvas canvas,double width,double height,double time,int seed,Color color,int count) {
        var bright=color.light(.65f);
        for(int i=0;i<count;i++) {
            double x=.08+fraction(noise(seed+i*1.7)+time*.00013)*(width-.16);
            double y=.08+fraction(noise(seed+i*2.3)+time*.00051)*(height-.16);
            float alpha=(float)(.2+.45*Math.pow(.5+.5*Math.sin(time*.04+i),2));
            canvas.star(x,y,.012+alpha*.025,.028,bright,alpha);
        }
    }
    private static double noise(double key) { return fraction(Math.sin(key*12.9898+78.233)*43758.5453); }
    private static double fraction(double value) { return value-Math.floor(value); }
    private static Vec3 vector(Direction direction) { return Vec3.atLowerCornerOf(direction.getNormal()); }

    /** Small plane-relative silhouettes share the same orientation transform as the portal. */
    private static final class Shape {
        private final Canvas canvas;
        private final double cx,cy,scale,ca,sa;
        Shape(Canvas canvas,double cx,double cy,double scale,double angle) {
            this.canvas=canvas;this.cx=cx;this.cy=cy;this.scale=scale;ca=Math.cos(angle);sa=Math.sin(angle);
        }
        void vertex(double x,double y,double depth,Color color,float alpha) {
            canvas.vertex(cx+scale*(x*ca-y*sa),cy+scale*(x*sa+y*ca),depth,color,alpha);
        }
        void triangle(double x1,double y1,double x2,double y2,double x3,double y3,double depth,Color color,float alpha) {
            vertex(x1,y1,depth,color,alpha);vertex(x2,y2,depth,color,alpha);
            vertex(x3,y3,depth,color,alpha);vertex(x3,y3,depth,color,alpha);
        }
        void line(double x1,double y1,double x2,double y2,double halfWidth,double depth,Color color,float alpha) {
            canvas.line(cx+scale*(x1*ca-y1*sa),cy+scale*(x1*sa+y1*ca),
                    cx+scale*(x2*ca-y2*sa),cy+scale*(x2*sa+y2*ca),halfWidth*scale,depth,color,alpha);
        }
    }

    /** Opening-relative coordinates avoid a Vec3 allocation per vertex in every orientation. */
    private static final class Canvas {
        private final VertexConsumer out;
        private final Matrix4f transform;
        private final Vec3 origin;
        private final Direction u,v,normal;
        Canvas(VertexConsumer out,Matrix4f transform,PortalFrame frame,Vec3 origin) {
            this.out=out;this.transform=transform;this.origin=origin;
            u=frame.u();v=frame.v();normal=frame.normal();
        }
        VertexConsumer vertex(double x,double y,double depth,Color color,float alpha) {
            return out.addVertex(transform,(float)(origin.x+u.getStepX()*x+v.getStepX()*y+normal.getStepX()*depth),
                    (float)(origin.y+u.getStepY()*x+v.getStepY()*y+normal.getStepY()*depth),
                    (float)(origin.z+u.getStepZ()*x+v.getStepZ()*y+normal.getStepZ()*depth))
                    .setColor(color.r,color.g,color.b,Math.clamp(alpha,0,1));
        }
        void quad(double x1,double y1,double x2,double y2,double depth,Color color,float alpha) {
            vertex(x1,y1,depth,color,alpha);vertex(x1,y2,depth,color,alpha);
            vertex(x2,y2,depth,color,alpha);vertex(x2,y1,depth,color,alpha);
        }
        void line(double x1,double y1,double x2,double y2,double halfWidth,double depth,Color color,float alpha) {
            double length=Math.hypot(x2-x1,y2-y1);
            if(length<.000001)return;
            double dx=-(y2-y1)/length*halfWidth,dy=(x2-x1)/length*halfWidth;
            vertex(x1+dx,y1+dy,depth,color,alpha);vertex(x2+dx,y2+dy,depth,color,alpha);
            vertex(x2-dx,y2-dy,depth,color,alpha);vertex(x1-dx,y1-dy,depth,color,alpha);
        }
        void star(double x,double y,double size,double depth,Color color,float alpha) {
            vertex(x-size,y,depth,color,alpha);vertex(x,y+size*.22,depth,color,alpha);
            vertex(x+size,y,depth,color,alpha);vertex(x,y-size*.22,depth,color,alpha);
            vertex(x-size*.22,y,depth,color,alpha);vertex(x,y+size,depth,color,alpha);
            vertex(x+size*.22,y,depth,color,alpha);vertex(x,y-size,depth,color,alpha);
        }
    }
    @Override public boolean shouldRenderOffScreen(PortalAnchorBlockEntity anchor) { return true; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(PortalAnchorBlockEntity anchor) { return anchor.renderBounds(); }
    @Override public int getViewDistance() { return 128; }
}
