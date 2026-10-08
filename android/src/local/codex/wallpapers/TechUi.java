package local.codex.wallpapers;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

final class TechUi {
    static final int BG=0xff090b14, PANEL=0xff151a2b, TEXT=0xffecf1ff, MUTED=0xffa0aac4, ACCENT=0xff6caaff;
    static int dp(android.content.Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable box(int color,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);g.setStroke(1,stroke);return g;}
    static void header(LinearLayout root,String eyebrow,String title,String subtitle){
        android.content.Context c=root.getContext();
        LinearLayout brand=new LinearLayout(c);brand.setGravity(Gravity.CENTER_VERTICAL);root.addView(brand);
        TextView emblem=new TextView(c);emblem.setText("✦");emblem.setTextSize(25);emblem.setTextColor(0xffc3b2ff);emblem.setGravity(Gravity.CENTER);emblem.setTag("custom");
        emblem.setBackground(box(0xff292344,0xff665799,dp(c,12)));brand.addView(emblem,new LinearLayout.LayoutParams(dp(c,42),dp(c,42)));
        TextView name=new TextView(c);name.setText("  CodeX");name.setTextSize(23);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setTextColor(TEXT);name.setTag("custom");brand.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView help=new TextView(c);help.setText(c instanceof GuideActivity?"返回  ↗":"使用说明  ↗");help.setTextColor(ACCENT);help.setTextSize(12);help.setGravity(Gravity.CENTER);help.setTag("custom");help.setMinHeight(dp(c,48));help.setPadding(dp(c,8),0,dp(c,8),0);help.setOnClickListener(v->{if(c instanceof GuideActivity)((Activity)c).finish();else c.startActivity(new Intent(c,GuideActivity.class));});brand.addView(help);
        TextView h=new TextView(c);h.setText(title.equals("CodeX")?"主题空间":title);h.setTextSize(26);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setTextColor(TEXT);h.setPadding(0,dp(c,22),0,dp(c,4));h.setTag("custom");root.addView(h);
        TextView sub=new TextView(c);sub.setText(subtitle);sub.setTextSize(12);sub.setTextColor(MUTED);root.addView(sub);
        if(!(c instanceof GuideActivity)){
            LinearLayout nav=new LinearLayout(c);nav.setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4));nav.setBackground(box(0xff0f1320,0xff293148,dp(c,12)));root.addView(nav);
            String[] labels={"主题壁纸","桌面组件","云端同步"};Class<?>[] pages={MainActivity.class,UsageWidgetConfigActivity.class,UsageCloudActivity.class};
            for(int i=0;i<3;i++){final Class<?> page=pages[i];Button b=new Button(c);b.setText(labels[i]);b.setTag("segment");b.setSelected(c.getClass()==page);b.setOnClickListener(v->{if(c.getClass()!=page){Intent intent=new Intent(c,page);intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);c.startActivity(intent);}});nav.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        }
    }
    static void section(LinearLayout root,String text){TextView t=new TextView(root.getContext());String[] parts=text.split("/",2);t.setText(parts.length==2?parts[1].trim():text);t.setTextSize(15);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setTextColor(TEXT);t.setTag("eyebrow");t.setPadding(0,dp(root.getContext(),22),0,dp(root.getContext(),12));root.addView(t);}
    static void panel(View v){v.setBackground(box(PANEL,0xff303953,dp(v.getContext(),16)));v.setPadding(dp(v.getContext(),14),dp(v.getContext(),12),dp(v.getContext(),14),dp(v.getContext(),12));}
    static void apply(Activity a,LinearLayout root){
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().getWindowInsetsController().setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        View parent=(View)root.getParent();parent.setBackground(new Backdrop());
        root.setPadding(dp(a,18),dp(a,12),dp(a,18),dp(a,28));
        parent.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});parent.requestApplyInsets();style(root);
    }
    static void style(View v){
        android.content.Context c=v.getContext();
        if(v instanceof Button){
            Button b=(Button)v;b.setAllCaps(false);b.setTextSize(13);b.setTextColor(TEXT);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,8));b.setElevation(0);
            String text=b.getText().toString();boolean primary=text.contains("添加到桌面")||text.contains("设置桌面和锁屏")||text.contains("保存人物")||text.contains("立即强制");
            if("segment".equals(v.getTag())||"theme".equals(v.getTag())){
                StateListDrawable states=new StateListDrawable();GradientDrawable selected=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff285bba,0xff5740a0});selected.setCornerRadius(dp(c,10));selected.setStroke(dp(c,1),0xff7b9cfa);states.addState(new int[]{android.R.attr.state_selected},selected);states.addState(new int[]{},box(0xff111625,0xff30384e,dp(c,10)));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337da7ff),states,null));
            }else{
                GradientDrawable fill=box(PANEL,0xff343e58,dp(c,10));if(primary){fill.setColors(new int[]{0xff2564d8,0xff6947ce});fill.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);fill.setStroke(1,0xff7795ff);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);}
                b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337da7ff),fill,null));
            }
            if(text.contains("停止自动")){b.setTextColor(0xffff8996);b.setBackground(box(0xff2b1724,0xff653048,dp(c,10)));}
        }else if(v instanceof EditText){EditText t=(EditText)v;t.setTextColor(TEXT);t.setHintTextColor(MUTED);t.setTextSize(14);t.setPadding(dp(c,14),dp(c,14),dp(c,14),dp(c,14));t.setBackground(box(0xff101522,0xff414c69,dp(c,10)));
        }else if(v instanceof TextView){TextView t=(TextView)v;if(!"custom".equals(t.getTag())&&!"eyebrow".equals(t.getTag())){t.setTextColor(MUTED);t.setLineSpacing(dp(c,3),1f);t.setPadding(0,dp(c,6),0,dp(c,10));}}
        if(v.getLayoutParams() instanceof LinearLayout.LayoutParams&&(v instanceof Button||v instanceof EditText)){
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();lp.topMargin=dp(c,5);lp.bottomMargin=dp(c,5);if(lp.weight>0){lp.leftMargin=dp(c,3);lp.rightMargin=dp(c,3);}v.setLayoutParams(lp);
        }
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)style(group.getChildAt(i));}
    }
    static class Backdrop extends Drawable {
        final Paint p=new Paint(3);
        public void draw(Canvas c){Rect r=getBounds();p.setShader(new LinearGradient(0,0,r.width(),r.height(),new int[]{0xff14192c,BG,0xff141020},null,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);p.setColor(0x0b93aaff);p.setStrokeWidth(1);int step=Math.max(40,r.width()/12);for(int x=0;x<r.right;x+=step)c.drawLine(x,0,x,r.bottom,p);for(int y=0;y<r.bottom;y+=step)c.drawLine(0,y,r.right,y,p);
            p.setShader(new RadialGradient(r.width()*.9f,r.height()*.15f,Math.max(1,r.width()*.65f),0x304969c7,0x004969c7,Shader.TileMode.CLAMP));c.drawRect(r,p);p.setShader(null);
        }
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
