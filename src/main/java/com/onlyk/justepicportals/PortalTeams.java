package com.onlyk.justepicportals;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Reads native membership only; a selected provider never falls through to another provider. */
public final class PortalTeams {
    public record Key(String system, String id) {}
    public record Choice(Key key, String name) {}
    private static final String FTB = "dev.ftb.mods.ftbteams.api.";
    private static final String XAERO = "xaero.pac.common.";
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    public static List<Choice> choices(MinecraftServer server, UUID owner, String ownerName) {
        if (owner == null) return List.of();
        var result = new ArrayList<Choice>();
        var vanilla = server.getScoreboard().getPlayersTeam(profileName(server,owner,ownerName));
        if (vanilla != null) result.add(new Choice(new Key("minecraft",vanilla.getName()),vanilla.getDisplayName().getString()));
        if (ModList.get().isLoaded("ftbteams")) {
            try {
                var manager = ftbManager();
                if (manager != null) for (var team : (Collection<?>)call(FTB+"TeamManager",manager,"getTeams")) {
                    if (ftbMember(team,owner)) result.add(new Choice(new Key("ftbteams",call(FTB+"Team",team,"getId").toString()),
                            ((net.minecraft.network.chat.Component)call(FTB+"Team",team,"getName")).getString()));
                }
            } catch (ReflectiveOperationException | LinkageError error) { warn("ftbteams",error); }
        }
        if (ModList.get().isLoaded("openpartiesandclaims")) {
            try {
                var api = xaeroApi(server);
                var manager = call(XAERO+"server.api.OpenPACServerAPI",api,"getPartyManager");
                var party = call(XAERO+"server.parties.party.api.IPartyManagerAPI",manager,"getPartyByMember",UUID.class,owner);
                if (party != null) result.add(new Choice(new Key("openpartiesandclaims",call(XAERO+"parties.party.api.IPartyAPI",party,"getId").toString()),xaeroName(api,party)));
            } catch (ReflectiveOperationException | LinkageError error) { warn("openpartiesandclaims",error); }
        }
        return List.copyOf(result);
    }

    public static List<Choice> allChoices(MinecraftServer server) {
        var result=new ArrayList<Choice>();
        for(var team:server.getScoreboard().getPlayerTeams())
            result.add(new Choice(new Key("minecraft",team.getName()),team.getDisplayName().getString()));
        if(ModList.get().isLoaded("ftbteams"))try {
            var manager=ftbManager();
            if(manager!=null)for(var team:(Collection<?>)call(FTB+"TeamManager",manager,"getTeams"))
                result.add(new Choice(new Key("ftbteams",call(FTB+"Team",team,"getId").toString()),
                        ((net.minecraft.network.chat.Component)call(FTB+"Team",team,"getName")).getString()));
        }catch(ReflectiveOperationException|LinkageError error){warn("ftbteams",error);}
        if(ModList.get().isLoaded("openpartiesandclaims"))try {
            var api=xaeroApi(server);
            var manager=call(XAERO+"server.api.OpenPACServerAPI",api,"getPartyManager");
            try(var stream=(java.util.stream.Stream<?>)call(XAERO+"server.parties.party.api.IPartyManagerAPI",manager,"getAllStream")) {
                for(var party:stream.toList())result.add(new Choice(new Key("openpartiesandclaims",
                        call(XAERO+"parties.party.api.IPartyAPI",party,"getId").toString()),xaeroName(api,party)));
            }
        }catch(ReflectiveOperationException|LinkageError error){warn("openpartiesandclaims",error);}
        result.sort(Comparator.comparing((Choice entry)->entry.key().system()).thenComparing(Choice::name,String.CASE_INSENSITIVE_ORDER).thenComparing(entry->entry.key().id()));
        return List.copyOf(result);
    }

    public static boolean member(MinecraftServer server, Key selected, ServerPlayer player) {
        if (selected == null) return false;
        try {
            return switch (selected.system()) {
                case "minecraft" -> {
                    var team = server.getScoreboard().getPlayerTeam(selected.id());
                    yield team != null && team == server.getScoreboard().getPlayersTeam(player.getScoreboardName());
                }
                case "ftbteams" -> {
                    if (!ModList.get().isLoaded("ftbteams")) yield false;
                    var manager = ftbManager();
                    if (manager == null) yield false;
                    var team = (Optional<?>)call(FTB+"TeamManager",manager,"getTeamByID",UUID.class,UUID.fromString(selected.id()));
                    yield team.isPresent() && ftbMember(team.get(),player.getUUID());
                }
                case "openpartiesandclaims" -> {
                    if (!ModList.get().isLoaded("openpartiesandclaims")) yield false;
                    var manager = call(XAERO+"server.api.OpenPACServerAPI",xaeroApi(server),"getPartyManager");
                    var party = call(XAERO+"server.parties.party.api.IPartyManagerAPI",manager,"getPartyById",UUID.class,UUID.fromString(selected.id()));
                    yield party != null && call(XAERO+"parties.party.api.IPartyAPI",party,"getMemberInfo",UUID.class,player.getUUID()) != null;
                }
                default -> false;
            };
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException error) {
            warn(selected.system(),error);
            return false;
        }
    }

    private static boolean ftbMember(Object team, UUID player) throws ReflectiveOperationException {
        if ((boolean)call(FTB+"Team",team,"isPlayerTeam")) return player.equals(call(FTB+"Team",team,"getOwner"));
        return ((Set<?>)call(FTB+"Team",team,"getMembers")).contains(player);
    }
    private static Object ftbManager() throws ReflectiveOperationException {
        var api = call(FTB+"FTBTeamsAPI",null,"api");
        return (boolean)call(FTB+"FTBTeamsAPI$API",api,"isManagerLoaded") ? call(FTB+"FTBTeamsAPI$API",api,"getManager") : null;
    }
    private static Object xaeroApi(MinecraftServer server) throws ReflectiveOperationException {
        return call(XAERO+"server.api.OpenPACServerAPI",null,"get",MinecraftServer.class,server);
    }
    private static String xaeroName(Object api, Object party) throws ReflectiveOperationException {
        var owner = call(XAERO+"parties.party.api.IPartyAPI",party,"getOwner");
        var ownerId = (UUID)call(XAERO+"parties.party.member.api.IPartyMemberAPI",owner,"getUUID");
        var manager = call(XAERO+"server.api.OpenPACServerAPI",api,"getPlayerConfigManager");
        String configPackage = XAERO+"server.player.config.api.v2.";
        var config = call(configPackage+"IPlayerConfigManagerAPI",manager,"getLoadedConfig",UUID.class,ownerId);
        if (config != null) {
            var option = type(configPackage+"PlayerConfigOptions").getField("PARTY_NAME").get(null);
            var name = call(configPackage+"IPlayerConfigAPI",config,"getEffective",type(configPackage+"IPlayerConfigOptionSpecAPI"),option);
            if (name instanceof String text && !text.isEmpty()) return text;
        }
        return (String)call(XAERO+"parties.party.api.IPartyAPI",party,"getDefaultName");
    }
    private static String profileName(MinecraftServer server, UUID id, String savedName) {
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getScoreboardName();
        var cache = server.getProfileCache();
        return cache == null ? savedName : cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(savedName);
    }
    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name,true,PortalTeams.class.getClassLoader());
    }
    private static Object call(String api, Object target, String name) throws ReflectiveOperationException {
        return method(api,name).invoke(target);
    }
    private static Object call(String api, Object target, String name, Class<?> parameter, Object value) throws ReflectiveOperationException {
        return method(api,name,parameter).invoke(target,value);
    }
    private static Method method(String api, String name, Class<?>... parameters) throws ReflectiveOperationException {
        String key = api+"#"+name+Arrays.toString(parameters);
        var method = METHODS.get(key);
        if (method == null) { method = type(api).getMethod(name,parameters); METHODS.put(key,method); }
        return method;
    }
    private static void warn(String system, Throwable error) {
        if (WARNED.add(system)) LogUtils.getLogger().warn("Cannot read {} portal team membership; access from that provider is denied",system,error);
    }
    private PortalTeams() {}
}
