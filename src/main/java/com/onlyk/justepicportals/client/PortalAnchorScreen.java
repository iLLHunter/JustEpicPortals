package com.onlyk.justepicportals.client;

import com.onlyk.justepicportals.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class PortalAnchorScreen extends AbstractContainerScreen<PortalAnchorMenu> {
    private final List<Button> accessButtons = new ArrayList<>();
    private Button teamButton;
    private Button nearbyButton;
    private boolean expanded;
    private int firstRow;
    private int focusedRow;
    private Component headerTooltip;
    private static final int ROW_HEIGHT = 18;
    // Operator-only advancement requirement page.
    private static final int REQ_ROW = 12, REQ_ENTRY_ROWS = 3, REQ_OPTION_ROWS = 4;
    private boolean requirementPage;
    private Button requirementButton, modeButton, backButton, doneButton;
    private EditBox search;
    private List<Integer> matches = List.of();
    private String lastQuery;
    private int firstEntry, firstMatch;

    public PortalAnchorScreen(PortalAnchorMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title);
        imageWidth=280; imageHeight=176;
    }
    @Override protected void init() {
        super.init();
        accessButtons.clear(); expanded=false;
        for (var access : PortalAccess.values()) {
            int x=leftPos+8+access.ordinal()*88;
            accessButtons.add(addRenderableWidget(Button.builder(accessName(access),button -> send(access.ordinal())).bounds(x,topPos+34,86,20).build()));
        }
        teamButton=addRenderableWidget(Button.builder(Component.empty(),button -> {
            expanded=!expanded;
            if(expanded){teamButton.setTooltip(null);nearbyButton.setTooltip(null);}
            focusedRow=menu.canShowAll()?0:Math.max(0,menu.selectedIndex());
            firstRow=Math.clamp(focusedRow,0,Math.max(0,rowCount()-visibleRows()));
        }).bounds(leftPos+8,topPos+68,264,20).build());
        nearbyButton=addRenderableWidget(Button.builder(Component.empty(),button -> send(PortalAnchorMenu.NEARBY_BUTTON))
                .bounds(leftPos+8,topPos+92,264,20).tooltip(Tooltip.create(Component.translatable("gui.justepicportals.nearby_help"))).build());
        doneButton=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button -> onClose()).bounds(leftPos+90,topPos+136,100,20).build());
        requirementButton=addRenderableWidget(Button.builder(Component.translatable("gui.justepicportals.requirement.open"),button -> page(true))
                .bounds(leftPos+196,topPos+136,76,20).build());
        modeButton=addRenderableWidget(Button.builder(Component.empty(),button -> send(PortalAnchorMenu.REQUIREMENT_MODE_BUTTON)).bounds(leftPos+8,topPos+18,180,20).build());
        backButton=addRenderableWidget(Button.builder(Component.translatable("gui.back"),button -> page(false)).bounds(leftPos+196,topPos+18,76,20).build());
        search=addRenderableWidget(new EditBox(font,leftPos+9,topPos+98,262,14,Component.translatable("gui.justepicportals.requirement.search")));
        search.setHint(Component.translatable("gui.justepicportals.requirement.search"));
        search.setMaxLength(128);
        lastQuery=null;
        page(requirementPage);
        updateControls();
    }
    private void page(boolean requirements) {
        requirementPage=requirements&&menu.canShowAll();
        expanded=false;
        for(var button:accessButtons)button.visible=!requirementPage;
        teamButton.visible=nearbyButton.visible=doneButton.visible=!requirementPage;
        requirementButton.visible=!requirementPage&&menu.canShowAll();
        modeButton.visible=backButton.visible=search.visible=requirementPage;
        if(requirementPage)setFocused(search); else search.setFocused(false);
    }
    private List<Integer> matches() {
        String query=search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if(!query.equals(lastQuery)) {
            lastQuery=query; firstMatch=0;
            var result=new ArrayList<Integer>();
            var options=menu.requirementOptions();
            for(int i=0;i<options.size();i++) {
                var option=options.get(i);
                if(query.isEmpty()||option.id().contains(query)||option.name().toLowerCase(java.util.Locale.ROOT).contains(query))result.add(i);
            }
            matches=List.copyOf(result);
        }
        return matches;
    }
    private Component optionLabel(PortalAnchorMenu.RequirementRow row) {
        return switch(row.kind()) {
            case ADVANCEMENT -> row.name().equals(row.id())?Component.literal(row.id()):Component.translatable("gui.justepicportals.requirement.entry.advancement",row.name(),row.id());
            case GROUP -> Component.translatable("gui.justepicportals.requirement.entry.group",row.id());
            case JED_RULE -> Component.translatable("gui.justepicportals.requirement.entry.jed_rule",row.id());
        };
    }
    private void send(int button) {
        if (minecraft!=null && minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);
    }
    private Component accessName(PortalAccess access) { return Component.translatable("gui.justepicportals.access."+access.name().toLowerCase(java.util.Locale.ROOT)); }
    private Component choiceName(int index) {
        var choice=menu.choices().get(index);
        return Component.translatable("gui.justepicportals.team_entry",Component.translatable("gui.justepicportals.system."+choice.key().system()),choice.name());
    }
    private int teamOffset() { return menu.canShowAll()?1:0; }
    /** On servers with more teams than one menu can carry, the full list ends with a row saying how many are not shown. */
    private boolean hasOmittedRow() { return menu.showingAll()&&menu.omittedTeams()>0; }
    private boolean isOmittedRow(int row) { return hasOmittedRow()&&row==rowCount()-1; }
    private int rowCount() { return menu.choices().size()+teamOffset()+(hasOmittedRow()?1:0); }
    private Component rowName(int row) {
        if(isOmittedRow(row))return Component.translatable("gui.justepicportals.teams_omitted",menu.omittedTeams());
        return menu.canShowAll()&&row==0?Component.translatable("gui.justepicportals.show_all"):choiceName(row-teamOffset());
    }
    private void activateRow(int row) {
        if(isOmittedRow(row))return;
        if(menu.canShowAll()&&row==0) {send(PortalAnchorMenu.SHOW_ALL_BUTTON);firstRow=0;focusedRow=0;return;}
        send(PortalAnchorMenu.TEAM_BUTTON_BASE+row-teamOffset());expanded=false;
    }
    private Component clipped(Component label,int width) {
        if(font.width(label)<=width)return label;
        return Component.literal(font.plainSubstrByWidth(label.getString(),width-font.width("..."))+"...");
    }
    private void updateControls() {
        if(teamButton==null)return;
        if(requirementPage) {
            modeButton.setMessage(Component.translatable(menu.requirementAny()?"gui.justepicportals.requirement.mode_any":"gui.justepicportals.requirement.mode_all"));
            firstEntry=Math.clamp(firstEntry,0,Math.max(0,menu.requirementEntries().size()-REQ_ENTRY_ROWS));
            return;
        }
        for(var access:PortalAccess.values())accessButtons.get(access.ordinal()).active=menu.access()!=access;
        teamButton.active=menu.access()==PortalAccess.TEAM&&rowCount()>0;
        if(!teamButton.active)expanded=false;
        focusedRow=Math.clamp(focusedRow,0,Math.max(0,rowCount()-1));
        firstRow=Math.clamp(firstRow,0,Math.max(0,rowCount()-visibleRows()));
        int selected=menu.selectedIndex();
        Component label=selected>=0&&selected<menu.choices().size()?choiceName(selected)
                :Component.translatable(menu.hasSelectedTeam()?"gui.justepicportals.team_unavailable":menu.choices().isEmpty()?(menu.showingAll()?"gui.justepicportals.no_teams_found":"gui.justepicportals.no_teams"):"gui.justepicportals.choose_team");
        teamButton.setMessage(clipped(label,236));
        teamButton.setTooltip(expanded?null:Tooltip.create(label));
        nearbyButton.setTooltip(expanded?null:Tooltip.create(Component.translatable("gui.justepicportals.nearby_help")));
        nearbyButton.setMessage(Component.translatable(menu.nearby()?"gui.justepicportals.nearby_on":"gui.justepicportals.nearby_off"));
    }
    @Override protected void containerTick() { super.containerTick(); updateControls(); }
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY) {
        graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF555555);
        graphics.fill(leftPos,topPos,leftPos+imageWidth-2,topPos+imageHeight-2,0xFFFFFFFF);
        graphics.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFFC6C6C6);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {
        headerTooltip=null;
        if(requirementPage){renderRequirementLabels(graphics,mouseX,mouseY);return;}
        boolean active=!menu.disabled()&&menu.status()==2;
        var badge=Component.translatable(active?"gui.justepicportals.badge.active":"gui.justepicportals.badge.inactive");
        int badgeWidth=Math.max(font.width(Component.translatable("gui.justepicportals.badge.active")),font.width(Component.translatable("gui.justepicportals.badge.inactive")))+8;
        int badgeLeft=(imageWidth-badgeWidth)/2,badgeRight=badgeLeft+badgeWidth;
        int tint=active?0xFF55FF55:0xFFFF5555;
        graphics.fill(badgeLeft,5,badgeRight,5+font.lineHeight+4,tint);
        graphics.fill(badgeLeft+1,6,badgeRight-1,4+font.lineHeight+4,0xFF202020);
        graphics.drawString(font,badge,imageWidth/2-font.width(badge)/2,7,tint,false);
        headerLabel(graphics,title,8,7,badgeLeft-11,false,mouseX,mouseY);
        headerLabel(graphics,Component.translatable("gui.justepicportals.owner",menu.ownerName()),badgeRight+3,7,imageWidth-badgeRight-11,true,mouseX,mouseY);
        var access=Component.translatable("gui.justepicportals.access_label",accessName(menu.access()));
        int accessWidth=font.width(access);
        headerLabel(graphics,Component.translatable(menu.containsCore()?"gui.justepicportals.core_present":"gui.justepicportals.core_absent"),8,21,imageWidth-24-accessWidth,false,mouseX,mouseY);
        headerLabel(graphics,access,imageWidth-8-accessWidth,21,accessWidth,true,mouseX,mouseY);
        String status=menu.disabled()?"disabled":menu.status()==2?"active":menu.status()==1?"linked":"unlinked";
        if(mouseX>=leftPos+badgeLeft&&mouseX<leftPos+badgeRight&&mouseY>=topPos+5&&mouseY<topPos+5+font.lineHeight+4) {
            headerTooltip=Component.translatable("gui.justepicportals.status."+status);
        }
        graphics.drawString(font,Component.translatable("gui.justepicportals.team_label"),8,57,0x404040,false);
        graphics.drawWordWrap(font,Component.translatable("gui.justepicportals.remove_help"),8,116,264,0x404040);
        if(menu.gated())graphics.drawString(font,clipped(Component.translatable("gui.justepicportals.requirement.gated"),264),8,162,0x8B4513,false);
    }
    private void renderRequirementLabels(GuiGraphics graphics,int mouseX,int mouseY) {
        graphics.drawString(font,clipped(Component.translatable("gui.justepicportals.requirement.title"),264),8,6,0x404040,false);
        var entries=menu.requirementEntries();
        graphics.drawString(font,Component.translatable(entries.isEmpty()?"gui.justepicportals.requirement.none":"gui.justepicportals.requirement.entries"),8,42,0x404040,false);
        for(int row=0;row<REQ_ENTRY_ROWS&&firstEntry+row<entries.size();row++) {
            int y=53+row*REQ_ROW;
            boolean hovered=mouseX>=leftPos+8&&mouseX<leftPos+272&&mouseY>=topPos+y&&mouseY<topPos+y+REQ_ROW;
            if(hovered)graphics.fill(8,y,272,y+REQ_ROW,0xFFA8A8A8);
            var label=Component.literal("x ").append(entries.get(firstEntry+row).name());
            graphics.drawString(font,clipped(label,262),10,y+2,hovered?0xAA0000:0x202020,false);
            if(hovered)headerTooltip=Component.translatable("gui.justepicportals.requirement.remove",entries.get(firstEntry+row).name());
        }
        if(entries.size()>REQ_ENTRY_ROWS)graphics.drawString(font,(firstEntry+1)+"-"+Math.min(entries.size(),firstEntry+REQ_ENTRY_ROWS)+"/"+entries.size(),230,42,0x404040,false);
        graphics.drawString(font,Component.translatable("gui.justepicportals.requirement.add"),8,88,0x404040,false);
        var found=matches();
        var options=menu.requirementOptions();
        for(int row=0;row<REQ_OPTION_ROWS&&firstMatch+row<found.size();row++) {
            int y=115+row*REQ_ROW;
            boolean hovered=mouseX>=leftPos+8&&mouseX<leftPos+272&&mouseY>=topPos+y&&mouseY<topPos+y+REQ_ROW;
            if(hovered)graphics.fill(8,y,272,y+REQ_ROW,0xFFA8A8A8);
            var option=options.get(found.get(firstMatch+row));
            var label=optionLabel(option);
            graphics.drawString(font,clipped(Component.literal("+ ").append(label),262),10,y+2,0x202020,false);
            if(hovered&&font.width(label)>250)headerTooltip=label;
        }
        if(found.isEmpty())graphics.drawString(font,Component.translatable("gui.justepicportals.requirement.no_matches"),10,117,0x606060,false);
        if(menu.omittedOptions()>0&&found.size()<=REQ_OPTION_ROWS)graphics.drawString(font,clipped(Component.translatable("gui.justepicportals.requirement.options_omitted",menu.omittedOptions()),262),10,117+REQ_OPTION_ROWS*REQ_ROW-REQ_ROW,0x606060,false);
    }
    private void headerLabel(GuiGraphics graphics,Component label,int x,int y,int width,boolean rightAligned,int mouseX,int mouseY) {
        var visible=clipped(label,width);
        int drawX=rightAligned?x+width-font.width(visible):x;
        graphics.drawString(font,visible,drawX,y,0x404040,false);
        if(font.width(label)>width&&mouseX>=leftPos+x&&mouseX<leftPos+x+width&&mouseY>=topPos+y&&mouseY<topPos+y+font.lineHeight)headerTooltip=label;
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        super.render(graphics,mouseX,mouseY,partialTick);
        if(expanded)renderDropdown(graphics,mouseX,mouseY);
        else {
            renderTooltip(graphics,mouseX,mouseY);
            if(headerTooltip!=null)graphics.renderTooltip(font,Tooltip.splitTooltip(minecraft,headerTooltip),mouseX,mouseY);
        }
    }
    private int visibleRows() { return Math.min(4,rowCount()); }
    private int popupX() { return leftPos+8; }
    private int popupY() { return topPos+88; }
    private boolean insidePopup(double x,double y) { return x>=popupX()&&x<popupX()+264&&y>=popupY()&&y<popupY()+visibleRows()*ROW_HEIGHT; }
    private void renderDropdown(GuiGraphics graphics,int mouseX,int mouseY) {
        int x=popupX(),y=popupY(),height=visibleRows()*ROW_HEIGHT;
        graphics.pose().pushPose(); graphics.pose().translate(0,0,400);
        graphics.fill(x-1,y-1,x+265,y+height+1,0xFFFFFFFF);
        graphics.fill(x,y,x+264,y+height,0xFF202020);
        for(int row=0;row<visibleRows();row++) {
            int index=firstRow+row;
            boolean hovered=insidePopup(mouseX,mouseY)&&(mouseY-y)/ROW_HEIGHT==row;
            if(hovered||index==focusedRow)graphics.fill(x,y+row*ROW_HEIGHT,x+264,y+(row+1)*ROW_HEIGHT,0xFF505050);
            var label=rowName(index);
            int color=isOmittedRow(index)?0xFFA0A0A0:index-teamOffset()==menu.selectedIndex()&&index>=teamOffset()?0xFFFFFF55:0xFFFFFFFF;
            graphics.drawString(font,clipped(label,244),x+4,y+row*ROW_HEIGHT+5,color,false);
            if(hovered&&font.width(label)>244)graphics.renderTooltip(font,Tooltip.splitTooltip(minecraft,label),mouseX,mouseY);
        }
        if(firstRow>0)graphics.drawString(font,"^",x+254,y+2,0xFFFFFFFF,false);
        if(firstRow+visibleRows()<rowCount())graphics.drawString(font,"v",x+254,y+height-10,0xFFFFFFFF,false);
        graphics.pose().popPose();
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(requirementPage&&button==0&&x>=leftPos+8&&x<leftPos+272) {
            int relative=(int)(y-topPos);
            if(relative>=53&&relative<53+REQ_ENTRY_ROWS*REQ_ROW) {
                int index=firstEntry+(relative-53)/REQ_ROW;
                if(index<menu.requirementEntries().size()){send(PortalAnchorMenu.REQUIREMENT_REMOVE_BASE+index);return true;}
            }
            if(relative>=115&&relative<115+REQ_OPTION_ROWS*REQ_ROW) {
                int index=firstMatch+(relative-115)/REQ_ROW;
                var found=matches();
                if(index<found.size()){send(PortalAnchorMenu.REQUIREMENT_ADD_BASE+found.get(index));return true;}
            }
        }
        if(expanded) {
            if(button==0&&insidePopup(x,y))activateRow(firstRow+(int)(y-popupY())/ROW_HEIGHT);
            else expanded=false;
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(requirementPage) {
            int step=-(int)Math.signum(vertical);
            if(y<topPos+90)firstEntry=Math.clamp(firstEntry+step,0,Math.max(0,menu.requirementEntries().size()-REQ_ENTRY_ROWS));
            else firstMatch=Math.clamp(firstMatch+step,0,Math.max(0,matches().size()-REQ_OPTION_ROWS));
            return true;
        }
        if(expanded) { firstRow=Math.clamp(firstRow-(int)Math.signum(vertical),0,Math.max(0,rowCount()-visibleRows())); return true; }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scanCode,int modifiers) {
        // Typing in the search box must not close the screen through the inventory key.
        if(requirementPage&&search.isFocused()&&key!=GLFW.GLFW_KEY_ESCAPE)return search.keyPressed(key,scanCode,modifiers)||search.canConsumeInput()||super.keyPressed(key,scanCode,modifiers);
        if(requirementPage&&key==GLFW.GLFW_KEY_ESCAPE){page(false);return true;}
        if(expanded) {
            if(key==GLFW.GLFW_KEY_ESCAPE){expanded=false;return true;}
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){activateRow(focusedRow);return true;}
            if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){
                focusedRow=Math.clamp(focusedRow+(key==GLFW.GLFW_KEY_UP?-1:1),0,rowCount()-1);
                if(focusedRow<firstRow)firstRow=focusedRow;
                if(focusedRow>=firstRow+visibleRows())firstRow=focusedRow-visibleRows()+1;
                return true;
            }
        }
        return super.keyPressed(key,scanCode,modifiers);
    }
}
