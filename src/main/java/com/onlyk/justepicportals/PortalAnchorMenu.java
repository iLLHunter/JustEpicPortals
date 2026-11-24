package com.onlyk.justepicportals;

import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Vanilla menu packets provide range, window identity, and server-side button validation. */
public final class PortalAnchorMenu extends AbstractContainerMenu {
    public static final int NEARBY_BUTTON = 3;
    public static final int SHOW_ALL_BUTTON = 4;
    public static final int TEAM_BUTTON_BASE = 16;
    /** Operator-only advancement requirement controls. Removal and addition carry a row number above their base. */
    public static final int REQUIREMENT_MODE_BUTTON = 5;
    public static final int REQUIREMENT_REMOVE_BASE = 100_000;
    public static final int REQUIREMENT_ADD_BASE = 200_000;
    /** Advancements, groups and JED rules an operator can pick from, bounded like the team list. */
    public static final int MAX_LISTED_OPTIONS = 20_000;
    public static final int MAX_OPTION_ID_LENGTH = 256, MAX_OPTION_NAME_LENGTH = 96;
    private static final int OPTION_BYTE_BUDGET = 256 * 1024;
    /** One requirement entry or pickable option as the screen shows it. */
    public record RequirementRow(PortalRequirements.Kind kind, String id, String name) {}
    /** Team lists are bounded by the 16-bit data slot that carries the selected row and by the 1 MiB limit on the
     * menu-opening packet. Both sides trim a list the same way (see {@link #listable}), so row numbers always agree. */
    public static final int MAX_LISTED_TEAMS = 8000;
    public static final int MAX_SYSTEM_LENGTH = 32, MAX_ID_LENGTH = 128, MAX_NAME_LENGTH = 64;
    private static final int LIST_BYTE_BUDGET = 512 * 1024;
    private final PortalAnchorBlockEntity anchor;
    private final String ownerName;
    private final List<PortalTeams.Choice> ownerChoices;
    private final List<PortalTeams.Choice> allChoices;
    private final int omittedTeams;
    private final ContainerData data;
    private boolean showingAll;
    private boolean requirementAny;
    private List<RequirementRow> requirementEntries;
    private final List<RequirementRow> requirementOptions;
    private final int omittedOptions;

    public PortalAnchorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(JustEpicPortals.ANCHOR_MENU.get(),id);
        anchor = null;
        ownerName = buffer.readUtf();
        ownerChoices=readChoices(buffer);
        allChoices=readChoices(buffer);
        data = new SimpleContainerData(10);
        data.set(7,buffer.readBoolean()?1:0);
        omittedTeams = buffer.readVarInt();
        if (omittedTeams < 0) throw new DecoderException("Portal anchor menu reports " + omittedTeams + " omitted teams");
        requirementAny = buffer.readBoolean();
        requirementEntries = readRows(buffer,PortalRequirements.MAX_ANCHOR_ENTRIES);
        requirementOptions = readRows(buffer,MAX_LISTED_OPTIONS);
        omittedOptions = buffer.readVarInt();
        if (omittedOptions < 0) throw new DecoderException("Portal anchor menu reports " + omittedOptions + " omitted requirement options");
        addDataSlots(data);
    }
    public PortalAnchorMenu(int id, Inventory inventory, PortalAnchorBlockEntity anchor, List<PortalTeams.Choice> choices) {
        this(id,inventory,anchor,choices,PortalPermissions.canAdminister(inventory.player)?PortalTeams.allChoices(inventory.player.getServer()):List.of(),PortalPermissions.canAdminister(inventory.player));
    }
    public PortalAnchorMenu(int id, Inventory inventory, PortalAnchorBlockEntity anchor, List<PortalTeams.Choice> choices,List<PortalTeams.Choice> allChoices,boolean administrator) {
        super(JustEpicPortals.ANCHOR_MENU.get(),id);
        this.anchor = anchor;
        this.ownerName = anchor.ownerName();
        this.ownerChoices = listable(choices);
        this.allChoices = administrator?listable(allChoices):List.of();
        this.omittedTeams = administrator?allChoices.size()-this.allChoices.size():0;
        var server = inventory.player.getServer();
        var options = administrator&&server!=null?requirementOptions(server):List.<RequirementRow>of();
        this.requirementOptions = listableRows(options);
        this.omittedOptions = options.size()-this.requirementOptions.size();
        this.requirementAny = anchor.requirement().any();
        this.requirementEntries = administrator&&server!=null?entryRows(server,anchor.requirement()):List.of();
        data = new ContainerData() {
            @Override public int get(int index) {
                return switch(index) {
                    case 0 -> anchor.access().ordinal();
                    case 1 -> anchor.nearby() ? 1 : 0;
                    case 2 -> anchor.containsCore() ? 1 : 0;
                    case 3 -> anchor.activeFrame()!=null ? 2 : PortalLinks.get(inventory.player.getServer()).destination(anchor.key()).isPresent() ? 1 : 0;
                    case 4 -> {
                        int selected = -1;
                        var current=choices();
                        for (int i=0;i<current.size();i++) if(current.get(i).key().equals(anchor.selectedTeam())) selected=i;
                        yield selected;
                    }
                    case 5 -> anchor.material().disabled() ? 1 : 0;
                    case 6 -> anchor.selectedTeam()!=null ? 1 : 0;
                    case 7 -> administrator&&PortalPermissions.canAdminister(inventory.player)?1:0;
                    case 8 -> showingAll?1:0;
                    case 9 -> PortalRequirements.gated(anchor)?1:0;
                    default -> 0;
                };
            }
            @Override public void set(int index,int value) {}
            @Override public int getCount() { return 10; }
        };
        addDataSlots(data);
    }
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, PortalAnchorBlockEntity anchor,List<PortalTeams.Choice> choices) {
        writeOpeningData(buffer,anchor,choices,List.of(),false);
    }
    public static void writeOpeningData(RegistryFriendlyByteBuf buffer,PortalAnchorBlockEntity anchor,List<PortalTeams.Choice> choices,List<PortalTeams.Choice> allChoices,boolean administrator) {
        var listed=administrator?listable(allChoices):List.<PortalTeams.Choice>of();
        buffer.writeUtf(anchor.ownerName());writeChoices(buffer,listable(choices));
        writeChoices(buffer,listed);buffer.writeBoolean(administrator);
        buffer.writeVarInt(administrator?allChoices.size()-listed.size():0);
        var server=anchor.getLevel()==null?null:anchor.getLevel().getServer();
        boolean operator=administrator&&server!=null;
        buffer.writeBoolean(anchor.requirement().any());
        writeRows(buffer,operator?entryRows(server,anchor.requirement()):List.of());
        var options=operator?requirementOptions(server):List.<RequirementRow>of();
        var sent=listableRows(options);
        writeRows(buffer,sent);
        buffer.writeVarInt(options.size()-sent.size());
    }
    /** Every advancement (those with a title first), then requirement groups, then JED rules, each sorted by id. */
    public static List<RequirementRow> requirementOptions(net.minecraft.server.MinecraftServer server) {
        var titled=new ArrayList<RequirementRow>(); var untitled=new ArrayList<RequirementRow>();
        for (var holder : server.getAdvancements().getAllAdvancements()) {
            var display=holder.value().display();
            (display.isPresent()?titled:untitled).add(new RequirementRow(PortalRequirements.Kind.ADVANCEMENT,holder.id().toString(),
                    display.map(info -> info.getTitle().getString()).orElse(holder.id().toString())));
        }
        Comparator<RequirementRow> byId=Comparator.comparing(RequirementRow::id);
        titled.sort(byId); untitled.sort(byId);
        var result=new ArrayList<RequirementRow>(titled); result.addAll(untitled);
        PortalRequirements.groupIds().stream().map(Object::toString).sorted().forEach(id -> result.add(new RequirementRow(PortalRequirements.Kind.GROUP,id,id)));
        PortalJed.ruleIds(server).stream().map(Object::toString).sorted().forEach(id -> result.add(new RequirementRow(PortalRequirements.Kind.JED_RULE,id,id)));
        return result;
    }
    public static List<RequirementRow> entryRows(net.minecraft.server.MinecraftServer server,PortalRequirements.AnchorRequirement requirement) {
        return requirement.entries().stream().map(entry -> new RequirementRow(entry.kind(),entry.id().toString(),
                shortened(PortalCommands.describe(server,entry).getString(),MAX_OPTION_NAME_LENGTH))).toList();
    }
    /** The part of the option list that fits in one opening packet next to the team lists. */
    public static List<RequirementRow> listableRows(List<RequirementRow> rows) {
        var result=new ArrayList<RequirementRow>(); int bytes=0;
        for (var row : rows) {
            if (row.id().length()>MAX_OPTION_ID_LENGTH) continue;
            var name=shortened(row.name(),MAX_OPTION_NAME_LENGTH);
            bytes+=12+ByteBufUtil.utf8Bytes(row.id())+ByteBufUtil.utf8Bytes(name);
            if (result.size()==MAX_LISTED_OPTIONS || bytes>OPTION_BYTE_BUDGET) break;
            result.add(name.equals(row.name())?row:new RequirementRow(row.kind(),row.id(),name));
        }
        return List.copyOf(result);
    }
    public static void writeRows(net.minecraft.network.FriendlyByteBuf buffer,List<RequirementRow> rows) {
        buffer.writeVarInt(rows.size());
        for (var row : rows) { buffer.writeByte(row.kind().ordinal()); buffer.writeUtf(row.id(),MAX_OPTION_ID_LENGTH); buffer.writeUtf(row.name(),MAX_OPTION_NAME_LENGTH); }
    }
    public static List<RequirementRow> readRows(net.minecraft.network.FriendlyByteBuf buffer,int limit) {
        int count=buffer.readVarInt();
        if (count<0 || count>limit) throw new DecoderException("Portal anchor requirement list has "+count+" entries; at most "+limit+" are sent");
        var rows=new ArrayList<RequirementRow>(Math.min(count,64));
        for (int i=0;i<count;i++) {
            int kind=buffer.readByte();
            if (kind<0 || kind>=PortalRequirements.Kind.values().length) throw new DecoderException("Unknown requirement kind "+kind);
            rows.add(new RequirementRow(PortalRequirements.Kind.values()[kind],buffer.readUtf(MAX_OPTION_ID_LENGTH),buffer.readUtf(MAX_OPTION_NAME_LENGTH)));
        }
        return List.copyOf(rows);
    }
    /** The part of a team list that can be sent: names are shortened for display, ids too long to send are left out (they
     * could not be matched anyway), and entries stop at {@link #MAX_LISTED_TEAMS} or the byte budget. */
    public static List<PortalTeams.Choice> listable(List<PortalTeams.Choice> choices) {
        var result=new ArrayList<PortalTeams.Choice>();
        int bytes=0;
        for (var choice : choices) {
            var key=choice.key();
            if (key.system().length()>MAX_SYSTEM_LENGTH || key.id().length()>MAX_ID_LENGTH) continue;
            var name=shortened(choice.name());
            bytes+=15+ByteBufUtil.utf8Bytes(key.system())+ByteBufUtil.utf8Bytes(key.id())+ByteBufUtil.utf8Bytes(name);
            if (result.size()==MAX_LISTED_TEAMS || bytes>LIST_BYTE_BUDGET) break;
            result.add(name.equals(choice.name())?choice:new PortalTeams.Choice(key,name));
        }
        return List.copyOf(result);
    }
    private static String shortened(String name) { return shortened(name,MAX_NAME_LENGTH); }
    private static String shortened(String name,int limit) {
        if (name.length()<=limit) return name;
        int end=limit-1;
        if (Character.isHighSurrogate(name.charAt(end-1))) end--; // never split a character in two
        return name.substring(0,end)+"…";
    }
    private static void writeChoices(RegistryFriendlyByteBuf buffer,List<PortalTeams.Choice> choices) {
        buffer.writeVarInt(choices.size());
        for (var choice : choices) {
            buffer.writeUtf(choice.key().system(),MAX_SYSTEM_LENGTH); buffer.writeUtf(choice.key().id(),MAX_ID_LENGTH); buffer.writeUtf(choice.name(),MAX_NAME_LENGTH);
        }
    }
    private static List<PortalTeams.Choice> readChoices(RegistryFriendlyByteBuf buffer) {
        int count=buffer.readVarInt();
        if (count<0 || count>MAX_LISTED_TEAMS) throw new DecoderException("Portal anchor team list has "+count+" entries; at most "+MAX_LISTED_TEAMS+" are sent");
        var entries=new ArrayList<PortalTeams.Choice>(Math.min(count,64));
        for(int i=0;i<count;i++)entries.add(new PortalTeams.Choice(new PortalTeams.Key(buffer.readUtf(MAX_SYSTEM_LENGTH),buffer.readUtf(MAX_ID_LENGTH)),buffer.readUtf(MAX_NAME_LENGTH)));
        return List.copyOf(entries);
    }
    public String ownerName() { return ownerName; }
    /** Teams that exist but did not fit in the administrator list. */
    public int omittedTeams() { return omittedTeams; }
    public List<PortalTeams.Choice> choices() { return showingAll()?allChoices:ownerChoices; }
    public boolean canShowAll() { return data.get(7)!=0; }
    public boolean showingAll() { return canShowAll()&&data.get(8)!=0; }
    public PortalAccess access() { return PortalAccess.values()[Math.clamp(data.get(0),0,2)]; }
    public boolean nearby() { return data.get(1)!=0; }
    public boolean containsCore() { return data.get(2)!=0; }
    public int status() { return data.get(3); }
    public int selectedIndex() { return data.get(4); }
    public boolean disabled() { return data.get(5)!=0; }
    public boolean hasSelectedTeam() { return data.get(6)!=0; }
    /** Whether any advancement requirement (the anchor's own or a material rule) applies. Every viewer sees this. */
    public boolean gated() { return data.get(9)!=0; }
    public boolean requirementAny() { return requirementAny; }
    public List<RequirementRow> requirementEntries() { return requirementEntries; }
    public List<RequirementRow> requirementOptions() { return requirementOptions; }
    public int omittedOptions() { return omittedOptions; }
    /** The server's view after an operator changed the requirement; see {@link PortalNetwork.RequirementPayload}. */
    public void requirementSynced(boolean any,List<RequirementRow> entries) { requirementAny=any; requirementEntries=List.copyOf(entries); }
    @Override public boolean stillValid(Player player) {
        return anchor == null || !anchor.isRemoved() && player.level()==anchor.getLevel()
                && player.level().getBlockEntity(anchor.getBlockPos())==anchor && PortalPermissions.canManage(anchor,player)
                && player.canInteractWithBlock(anchor.getBlockPos(),4);
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if (anchor == null || !stillValid(player)) return false;
        if (button>=0 && button<3) anchor.access(PortalAccess.values()[button]);
        else if (button==NEARBY_BUTTON) anchor.nearby(!anchor.nearby());
        else if(button==SHOW_ALL_BUTTON) {
            if(!canShowAll()||!PortalPermissions.canAdminister(player))return false;
            showingAll=true;
        }
        else if(button==REQUIREMENT_MODE_BUTTON||button>=REQUIREMENT_REMOVE_BASE) {
            if(!canShowAll()||!PortalPermissions.canAdminister(player))return false;
            var current=anchor.requirement();
            if(button==REQUIREMENT_MODE_BUTTON) anchor.requirement(current.any(!current.any()));
            else if(button<REQUIREMENT_ADD_BASE) {
                int index=button-REQUIREMENT_REMOVE_BASE;
                if(index>=current.entries().size())return false;
                anchor.requirement(current.remove(current.entries().get(index)));
            } else {
                int index=button-REQUIREMENT_ADD_BASE;
                if(index>=requirementOptions.size())return false;
                var row=requirementOptions.get(index);
                var id=net.minecraft.resources.ResourceLocation.tryParse(row.id());
                if(id==null||!PortalRequirements.exists(player.getServer(),row.kind(),id))return false;
                var entry=new PortalRequirements.Entry(row.kind(),id);
                if(current.entries().contains(entry)||current.entries().size()>=PortalRequirements.MAX_ANCHOR_ENTRIES)return false;
                anchor.requirement(current.add(entry));
            }
            requirementAny=anchor.requirement().any();
            requirementEntries=entryRows(player.getServer(),anchor.requirement());
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,new PortalNetwork.RequirementPayload(containerId,requirementAny,requirementEntries));
        }
        else {
            int index = button-TEAM_BUTTON_BASE;
            var choices=choices();
            if (index<0 || index>=choices.size()) return false;
            var chosen = choices.get(index);
            var current=showingAll()&&PortalPermissions.canAdminister(player)?PortalTeams.allChoices(player.getServer()):PortalTeams.choices(player.getServer(),anchor.owner(),anchor.ownerName());
            if (current.stream().noneMatch(entry -> entry.key().equals(chosen.key()))) return false;
            anchor.selectedTeam(chosen.key());
        }
        broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
}
