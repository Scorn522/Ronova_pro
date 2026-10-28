package dev.ronova.pro;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Four button-only panes. All target identities originate in the authenticated server view. */
final class WeaponScreen extends Screen {
    private static final UUID NONE=new UUID(0,0);
    private static final String[] TABS={"Mod 管控","领域与世界","实体与字段","历史与宿主"};
    private ProNetwork.WeaponPanel panel;private final Connection connection;
    private long sequence;private boolean pending;private int left,body,content,contentWidth,listTop,capacity,receivedPage,receivedPages;
    private String output="";private final StringBuilder received=new StringBuilder();
    WeaponScreen(ProNetwork.WeaponPanel panel,Connection connection){super(Component.literal("Ronova Pro · 终律"));this.connection=connection;apply(panel);}
    UUID session(){return panel.session();}
    boolean showing(Screen screen){return screen==this||screen instanceof Details details&&details.parent==this;}
    void apply(ProNetwork.WeaponPanel panel){this.panel=panel;sequence=panel.sequence();pending=false;if(output.isEmpty())output=panel.message();if(minecraft!=null)init();}
    private Button button(String text,int x,int y,int w,Runnable action){Button result=addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,19).build());result.active=!pending;return result;}
    @Override protected void init(){
        clearWidgets();body=Math.min(760,width-16);left=(width-body)/2;content=left+88;contentWidth=body-88;
        button("攻击连带停 Mod："+(panel.linkedStop()?"开":"关"),left,28,body/2-3,()->send(WeaponAction.LINK_STOP,NONE,panel.tab(),panel.page()));
        button("全维度时停："+(panel.clock()?"开":"关"),left+body/2+3,28,body/2-3,()->send(WeaponAction.TIME_STOP,NONE,panel.tab(),panel.page()));
        for(int tab=0;tab<TABS.length;tab++){int selected=tab;button((tab==panel.tab()?"› ":"")+TABS[tab],left,57+tab*25,82,()->send(WeaponAction.REFRESH,NONE,selected,0));}
        listTop=58;
        if(panel.tab()==1){WeaponAction[] actions={WeaponAction.FREEZE,WeaponAction.SPAWN_BAN,WeaponAction.UNLOAD,WeaponAction.SNAPSHOT,WeaponAction.REGENERATE,WeaponAction.REGENERATE_TWO};for(int i=0;i<actions.length;i++){WeaponAction action=actions[i];button(action.title,content+(i%2)*(contentWidth/2+2),58+(i/2)*22,contentWidth/2-2,()->send(action,NONE,panel.tab(),panel.page()));}listTop=126;}
        if(panel.tab()==3){WeaponAction[] actions={WeaponAction.HOST,WeaponAction.REPLAY,WeaponAction.DIAGNOSTICS};for(int i=0;i<actions.length;i++){WeaponAction action=actions[i];button(action==WeaponAction.HOST?"宿主保护："+(panel.host()?"开":"关"):action.title,content+i*(contentWidth/3),58,contentWidth/3-3,()->send(action,NONE,panel.tab(),panel.page()));}listTop=82;}
        int rowHeight=panel.tab()==2||panel.tab()==3?65:48;capacity=Math.max(1,Math.min(8,(height-54-listTop)/rowHeight));int rowY=listTop;
        for(ProNetwork.WeaponRow row:panel.rows()){
            if(rowY+rowHeight>height-54)break;
            int columns=4;int buttonWidth=contentWidth/columns;
            for(int i=0;i<row.actions().length;i++){WeaponAction action=WeaponAction.at(row.actions()[i]);int x=content+(i%columns)*buttonWidth,y=rowY+23+(i/columns)*20;button(action.title,x,y,buttonWidth-2,()->send(action,row.key(),panel.tab(),panel.page()));}
            rowY+=rowHeight;
        }
        int footer=height-43;
        button("‹",content,footer,25,()->send(WeaponAction.REFRESH,NONE,panel.tab(),Math.max(0,panel.page()-1)));
        button("›",content+29,footer,25,()->send(WeaponAction.REFRESH,NONE,panel.tab(),panel.page()+1));
        button("刷新",content+60,footer,46,()->send(WeaponAction.REFRESH,NONE,panel.tab(),panel.page()));
        button("结果",content+110,footer,46,()->minecraft.setScreen(new Details(this,output)));
        button("关闭",left+body-45,footer,45,this::onClose);
        if(!pending&&sequence==0&&panel.rows().size()>capacity)send(WeaponAction.REFRESH,NONE,panel.tab(),panel.page());
    }
    private void send(WeaponAction action,UUID target,int tab,int page){
        if(pending||minecraft.getConnection()==null||minecraft.getConnection().getConnection()!=connection)return;
        int targetTop=tab==1?126:tab==3?82:58,targetHeight=tab>=2?65:48;
        int desired=Math.max(1,Math.min(8,(height-54-targetTop)/targetHeight));
        pending=true;sequence++;ProNetwork.weaponRequest(new ProNetwork.WeaponRequest(panel.session(),sequence,action.ordinal(),tab,page,desired,target));init();
    }
    @Override public void resize(net.minecraft.client.Minecraft minecraft,int width,int height){super.resize(minecraft,width,height);if(!pending)send(WeaponAction.REFRESH,NONE,panel.tab(),panel.page());}
    void result(ProNetwork.WeaponResult result,Connection source){
        if(source!=connection||!result.session().equals(panel.session())||result.sequence()!=sequence)return;
        if(result.page()==0){received.setLength(0);receivedPage=0;receivedPages=result.pages();}
        if(result.pages()<1||result.pages()!=receivedPages||result.page()!=receivedPage)return;
        received.append(result.text());receivedPage++;if(receivedPage==receivedPages){output=received.toString();pending=false;init();}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){
        renderBackground(graphics);graphics.fill(left-4,6,left+body+4,height-5,0xEE111B28);graphics.drawString(font,title,left,12,0x8CDEFF);
        int rowHeight=panel.tab()==2||panel.tab()==3?65:48;
        int rowY=listTop;for(ProNetwork.WeaponRow row:panel.rows()){if(rowY+rowHeight>height-54)break;graphics.drawString(font,font.plainSubstrByWidth(row.title(),contentWidth),content,rowY,0xFFFFFF);graphics.drawString(font,font.plainSubstrByWidth(row.state(),contentWidth),content,rowY+11,0xA8BCD2);rowY+=rowHeight;}
        if(panel.rows().isEmpty())graphics.drawString(font,"当前没有列表目标",content,listTop+5,0xADBCCF);
        graphics.drawString(font,(panel.page()+1)+" / "+panel.pages(),left,height-39,0xADBCCF);
        String line=pending?"正在执行…":output.lines().findFirst().orElse(panel.message());graphics.drawString(font,font.plainSubstrByWidth(line,body),left,height-16,0xB8DCB4);super.render(graphics,x,y,partial);
    }
    private static final class Details extends Screen {
        private final WeaponScreen parent;private final String text;private List<FormattedCharSequence> lines=List.of();private int first;
        Details(WeaponScreen parent,String text){super(Component.literal("执行结果"));this.parent=parent;this.text=text;}
        @Override protected void init(){lines=font.split(Component.literal(text),width-24);addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(width/2-65,height-25,60,20).build());addRenderableWidget(Button.builder(Component.literal("复制"),b->minecraft.keyboardHandler.setClipboard(text)).bounds(width/2+5,height-25,60,20).build());}
        @Override public void onClose(){minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean mouseScrolled(double x,double y,double delta){first=Math.max(0,Math.min(Math.max(0,lines.size()-(height-45)/10),first-(int)(delta*3)));return true;}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){renderBackground(graphics);int count=(height-45)/10;for(int i=0;i<count&&first+i<lines.size();i++)graphics.drawString(font,lines.get(first+i),12,12+i*10,0xE5EBF0);super.render(graphics,x,y,partial);}
    }
}
