package com.chesssmp.plugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.*;
import java.util.*;

public final class ChessSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Piece> pieces = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Long> combatTags = new HashMap<>();
    private final Map<UUID, Side> eventSides = new HashMap<>();
    private final Map<UUID, Integer> eventKills = new HashMap<>();
    private File dataFile; private org.bukkit.configuration.file.YamlConfiguration data;
    private boolean eventRunning = false;
    private int whiteScore = 0, blackScore = 0;
    private static final String GOLD = ChatColor.GOLD.toString(), GRAY = ChatColor.GRAY.toString(), WHITE = ChatColor.WHITE.toString();
    private static final String TRADER_TITLE = ChatColor.DARK_PURPLE + "♟ Piece & Supply Trader";
    private enum Side { WHITE, BLACK }

    enum Piece {
        PAWN("Pawn", "♟", ChatColor.WHITE, "Forward March", "Second Wind", 30, 45, Material.IRON_SWORD),
        KNIGHT("Knight", "♞", ChatColor.AQUA, "L-Jump", "Knight's Charge", 40, 55, Material.IRON_HORSE_ARMOR),
        BISHOP("Bishop", "♝", ChatColor.LIGHT_PURPLE, "Diagonal Ward", "Sanctuary", 45, 65, Material.AMETHYST_SHARD),
        ROOK("Rook", "♜", ChatColor.GRAY, "Castle", "Iron Resolve", 50, 65, Material.SHIELD),
        QUEEN("Queen", "♛", ChatColor.LIGHT_PURPLE, "Royal Tempo", "Royal Decree", 65, 85, Material.GOLDEN_SWORD),
        KING("King", "♚", ChatColor.GOLD, "Royal Guard", "King's Rally", 60, 80, Material.GOLDEN_HELMET);
        final String label, emoji, ability1, ability2; final ChatColor color; final int cd1, cd2; final Material icon;
        Piece(String l,String em,ChatColor co,String a1,String a2,int c1,int c2,Material i){label=l;emoji=em;color=co;ability1=a1;ability2=a2;cd1=c1;cd2=c2;icon=i;}
        static Piece parse(String s){try{return valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception e){return null;}}
    }

    @Override public void onEnable(){loadData();getServer().getPluginManager().registerEvents(this,this);Objects.requireNonNull(getCommand("chess")).setExecutor(this);getCommand("chess").setTabCompleter(this);Objects.requireNonNull(getCommand("chessinfo")).setExecutor(this);for(Player p:Bukkit.getOnlinePlayers())applyName(p);getLogger().info("ChessSMP enabled.");}
    @Override public void onDisable(){saveData();clearEventTeams();}
    private void loadData(){dataFile=new File(getDataFolder(),"players.yml");if(!dataFile.exists())try{getDataFolder().mkdirs();dataFile.createNewFile();}catch(IOException e){getLogger().warning("Could not create players.yml");}data=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);for(String id:data.getKeys(false))try{UUID u=UUID.fromString(id);Piece p=Piece.parse(data.getString(id+".piece","PAWN"));pieces.put(u,p==null?Piece.PAWN:p);points.put(u,data.getInt(id+".points",0));}catch(IllegalArgumentException ignored){}}
    private void saveData(){if(data==null)return;for(UUID u:pieces.keySet()){data.set(u+".piece",pieces.get(u).name());data.set(u+".points",points.getOrDefault(u,0));}try{data.save(dataFile);}catch(IOException e){getLogger().severe("Could not save player data: "+e.getMessage());}}
    private Piece getPiece(Player p){return pieces.getOrDefault(p.getUniqueId(),Piece.PAWN);}
    private void msg(CommandSender s,String t){s.sendMessage(GOLD+"ChessSMP "+ChatColor.DARK_GRAY+"» "+WHITE+t);}
    private void applyName(Player p){Piece pc=getPiece(p);Scoreboard board=Bukkit.getScoreboardManager().getMainScoreboard();String teamName="chess_"+pc.name().toLowerCase();Team team=board.getTeam(teamName);if(team==null)team=board.registerNewTeam(teamName);team.setPrefix(pc.color+pc.emoji+" ");team.setColor(pc.color);team.addEntry(p.getName());}
    private void changePiece(Player p,Piece pc){for(Team t:Bukkit.getScoreboardManager().getMainScoreboard().getTeams())t.removeEntry(p.getName());pieces.put(p.getUniqueId(),pc);applyName(p);saveData();p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1.2f);msg(p,"Your role is now "+pc.emoji+" "+pc.label+".");}

    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(c.getName().equalsIgnoreCase("chessinfo")){
            if(!(s instanceof Player p)){msg(s,"This command can only be used in-game.");return true;}
            showChessInfo(p);
            return true;
        }
        if(a.length==0){msg(s,"/chess choose <piece>, /chess ability <1|2>, /chess trader, /chess event <start|stop|status>, /chess info, /chess top, /chessinfo");return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "choose"->{if(!(s instanceof Player p)){msg(s,"Players only.");return true;}if(a.length<2){msg(p,"Choose pawn, knight, bishop, rook, queen or king.");return true;}Piece pc=Piece.parse(a[1]);if(pc==null){msg(p,"Unknown chess piece.");return true;}if(eventRunning&&eventSides.containsKey(p.getUniqueId())){msg(p,"You cannot change roles during a Chess War event. Use the trader instead.");return true;}changePiece(p,pc);}
            case "ability"->{if(!(s instanceof Player p)){msg(s,"Players only.");return true;}int slot=a.length>1&&a[1].equals("2")?2:1;useAbility(p,slot);}
            case "trader"->{if(!(s instanceof Player p)){msg(s,"Players only.");return true;}giveTrader(p);openTrader(p);}
            case "event"->{if(!s.hasPermission("chesssmp.admin")){msg(s,"You need chesssmp.admin.");return true;}if(a.length<2){msg(s,"Use /chess event start|stop|status");return true;}if(a[1].equalsIgnoreCase("start"))startEvent(s);else if(a[1].equalsIgnoreCase("stop"))endEvent("Event stopped by an administrator.");else if(a[1].equalsIgnoreCase("status"))msg(s,eventRunning?"Chess War is live! White "+whiteScore+" - "+blackScore+" Black.":"No Chess War event is running.");else msg(s,"Use start, stop or status.");}
            case "info"->{if(!(s instanceof Player p)){msg(s,"In-game only.");return true;}Piece pc=getPiece(p);msg(p,"Role: "+pc.emoji+" "+pc.label+" | Abilities: "+pc.ability1+" / "+pc.ability2+" | Points: "+points.getOrDefault(p.getUniqueId(),0));if(eventRunning)msg(p,"Chess War: "+(eventSides.get(p.getUniqueId())==Side.WHITE?"White team":eventSides.get(p.getUniqueId())==Side.BLACK?"Black team":"Spectator"));}
            case "top"->{List<Map.Entry<UUID,Integer>> l=new ArrayList<>(points.entrySet());l.sort((x,y)->Integer.compare(y.getValue(),x.getValue()));msg(s,"Chess Points leaderboard:");for(int i=0;i<Math.min(5,l.size());i++){String n=Bukkit.getOfflinePlayer(l.get(i).getKey()).getName();msg(s,(i+1)+". "+(n==null?"Unknown":n)+" — "+l.get(i).getValue());}}
            default->msg(s,"Unknown command. Try /chess.");
        }return true;
    }

    private void showChessInfo(Player p){
        Piece pc=getPiece(p);
        int pts=points.getOrDefault(p.getUniqueId(),0);
        p.sendMessage(ChatColor.DARK_GRAY+"━━━━━━━━━━ "+ChatColor.GOLD+ChatColor.BOLD+"CHESS PROFILE "+ChatColor.DARK_GRAY+"━━━━━━━━━━");
        p.sendMessage(ChatColor.GRAY+"Your piece: "+pc.color+pc.emoji+" "+ChatColor.BOLD+pc.label);
        p.sendMessage(ChatColor.GRAY+"Chess Points: "+ChatColor.YELLOW+pts);
        p.sendMessage(ChatColor.GRAY+"Role stats (out of 10): "+ChatColor.WHITE+pieceStats(pc));
        p.sendMessage(ChatColor.DARK_GRAY+"────────────────────────────────");
        p.sendMessage(ChatColor.GOLD+"ABILITY 1 — "+ChatColor.WHITE+pc.ability1+ChatColor.GRAY+" ("+pc.cd1+"s cooldown)");
        p.sendMessage(ChatColor.GRAY+abilityDescription(pc,1));
        p.sendMessage(ChatColor.YELLOW+"Trigger: "+ChatColor.WHITE+"/chess ability 1");
        p.sendMessage(ChatColor.GOLD+"ABILITY 2 — "+ChatColor.WHITE+pc.ability2+ChatColor.GRAY+" ("+pc.cd2+"s cooldown)");
        p.sendMessage(ChatColor.GRAY+abilityDescription(pc,2));
        p.sendMessage(ChatColor.YELLOW+"Trigger: "+ChatColor.WHITE+"/chess ability 2");
        p.sendMessage(ChatColor.DARK_GRAY+"────────────────────────────────");
        p.sendMessage(ChatColor.GRAY+"Both abilities can only be activated while your 10-second PvP combat tag is active.");
        if(eventRunning){Side side=eventSides.get(p.getUniqueId());p.sendMessage(ChatColor.GRAY+"Chess War team: "+(side==Side.WHITE?ChatColor.WHITE+"WHITE":side==Side.BLACK?ChatColor.DARK_GRAY+"BLACK":ChatColor.RED+"Not assigned"));}
        p.sendMessage(ChatColor.DARK_GRAY+"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String pieceStats(Piece pc){
        return switch(pc){
            case PAWN -> "Offense 5/10 | Defense 5/10 | Mobility 6/10 | Support 3/10";
            case KNIGHT -> "Offense 7/10 | Defense 4/10 | Mobility 10/10 | Support 3/10";
            case BISHOP -> "Offense 4/10 | Defense 7/10 | Mobility 5/10 | Support 9/10";
            case ROOK -> "Offense 5/10 | Defense 10/10 | Mobility 2/10 | Support 4/10";
            case QUEEN -> "Offense 10/10 | Defense 5/10 | Mobility 7/10 | Support 6/10";
            case KING -> "Offense 6/10 | Defense 9/10 | Mobility 3/10 | Support 10/10";
        };
    }

    private String abilityDescription(Piece pc,int slot){
        return switch(pc){
            case PAWN -> slot==1?"Gain Speed II for 4 seconds to push forward.":"Gain a short burst of regeneration and restore a little hunger.";
            case KNIGHT -> slot==1?"Leap in the direction you face and gain brief slow falling.":"Gain Speed II and Jump Boost II for 3 seconds.";
            case BISHOP -> slot==1?"Gain Resistance I briefly, but move slower while warded.":"You and nearby teammates receive brief regeneration.";
            case ROOK -> slot==1?"Gain extra absorption hearts, with a short movement penalty.":"Gain strong short-duration resistance, but become very slow.";
            case QUEEN -> slot==1?"Gain Strength I and Speed I for a short offensive window.":"Slow nearby opposing players for a short time.";
            case KING -> slot==1?"Gain Resistance I and extra absorption hearts.":"Nearby teammates receive a small absorption shield.";
        };
    }

    private void useAbility(Player p,int slot){Piece pc=getPiece(p);UUID id=p.getUniqueId();String key=id+":"+slot;long now=System.currentTimeMillis();long ready=cooldowns.getOrDefault(key,0L);if(ready>now){msg(p,"Recharging: "+((ready-now+999)/1000)+"s.");return;}if(combatTags.getOrDefault(id,0L)<now){msg(p,"Abilities can only be used during PvP combat.");return;}int cd=slot==1?pc.cd1:pc.cd2;
        switch(pc){
            case PAWN->{if(slot==1){p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,80,1));}else{p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,50,0));p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION,1,0));}}
            case KNIGHT->{if(slot==1){p.setVelocity(p.getLocation().getDirection().multiply(0.9).setY(0.62));p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,45,0));}else{p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,60,1));p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,60,1));}}
            case BISHOP->{if(slot==1){p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,60,0));p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,0));}else{for(Player ally:Bukkit.getOnlinePlayers())if(ally!=p&&ally.getLocation().distanceSquared(p.getLocation())<=100&&sameSide(p,ally))ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,60,0));p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,60,0));}}
            case ROOK->{if(slot==1){p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,120,1));p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,0));}else{p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,50,1));p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,50,1));}}
            case QUEEN->{if(slot==1){p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,55,0));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,55,0));}else{for(Player enemy:Bukkit.getOnlinePlayers())if(enemy!=p&&enemy.getLocation().distanceSquared(p.getLocation())<=36&&(!eventRunning||!sameSide(p,enemy)))enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,50,1));}}
            case KING->{if(slot==1){p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,65,0));p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,100,1));}else{for(Player ally:Bukkit.getOnlinePlayers())if(ally.getLocation().distanceSquared(p.getLocation())<=100&&sameSide(p,ally))ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,80,0));}}
        }
        cooldowns.put(key,now+cd*1000L);p.getWorld().spawnParticle(Particle.ENCHANT,p.getLocation().add(0,1,0),18,.5,.6,.5,.2);p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.6f,1.4f);msg(p,(slot==1?pc.ability1:pc.ability2)+" activated!");
    }
    private boolean sameSide(Player a,Player b){Side x=eventSides.get(a.getUniqueId());return x==null||x==eventSides.get(b.getUniqueId());}

    private void startEvent(CommandSender sender){if(eventRunning){msg(sender,"A Chess War is already running.");return;}List<Player> players=new ArrayList<>(Bukkit.getOnlinePlayers());if(players.size()<2){msg(sender,"At least two online players are needed.");return;}eventRunning=true;whiteScore=0;blackScore=0;eventSides.clear();eventKills.clear();int i=0;for(Player p:players){Side side=(i++%2==0)?Side.WHITE:Side.BLACK;eventSides.put(p.getUniqueId(),side);eventKills.put(p.getUniqueId(),0);p.sendTitle(ChatColor.GOLD+"♟ CHESS WAR ♟",(side==Side.WHITE?ChatColor.WHITE+"WHITE TEAM":ChatColor.DARK_GRAY+"BLACK TEAM"),10,60,15);p.playSound(p.getLocation(),Sound.EVENT_RAID_HORN,1,1);}
        Bukkit.broadcastMessage(GOLD+"♟ CHESS WAR HAS BEGUN! ♟");Bukkit.broadcastMessage(WHITE+"All online players have been drafted into White and Black. First team to 15 captures wins!");
    }
    private void endEvent(String reason){if(!eventRunning)return;eventRunning=false;Bukkit.broadcastMessage(GOLD+"Chess War ended: "+WHITE+reason);Bukkit.broadcastMessage(ChatColor.WHITE+"Final score — White "+whiteScore+" : "+blackScore+" Black");eventSides.clear();eventKills.clear();clearEventTeams();}
    private void clearEventTeams(){Scoreboard b=Bukkit.getScoreboardManager().getMainScoreboard();for(String n:List.of("chess_event_white","chess_event_black")){Team t=b.getTeam(n);if(t!=null)t.unregister();}}
    private void giveTrader(Player p){for(ItemStack it:p.getInventory().getContents())if(it!=null&&it.hasItemMeta()&&it.getItemMeta().hasDisplayName()&&it.getItemMeta().getDisplayName().equals(ChatColor.LIGHT_PURPLE+"♟ Piece Trader")){msg(p,"You already have a Piece Trader.");return;}ItemStack item=new ItemStack(Material.EMERALD);ItemMeta m=item.getItemMeta();m.setDisplayName(ChatColor.LIGHT_PURPLE+"♟ Piece Trader");m.setLore(List.of(ChatColor.GRAY+"Right-click to trade your role",ChatColor.GRAY+"or buy mid-game supplies."));item.setItemMeta(m);p.getInventory().addItem(item);msg(p,"Piece Trader added to your inventory.");}
    private void openTrader(Player p){Inventory inv=Bukkit.createInventory(null,27,TRADER_TITLE);int[] slots={10,11,12,13,14,15};Piece[] pcs=Piece.values();for(int i=0;i<pcs.length;i++){Piece pc=pcs[i];ItemStack it=new ItemStack(pc.icon);ItemMeta m=it.getItemMeta();m.setDisplayName(pc.color+pc.emoji+" "+pc.label);m.setLore(List.of(ChatColor.GRAY+"Trade cost: 6 Diamonds + 8 Gold Ingots",ChatColor.YELLOW+"Click to become this piece"));it.setItemMeta(m);inv.setItem(slots[i],it);}inv.setItem(20,tradeItem(Material.ENDER_PEARL,"Ender Pearl","Cost: 4 Emeralds"));inv.setItem(21,tradeItem(Material.GOLDEN_APPLE,"Golden Apple","Cost: 6 Gold Ingots"));inv.setItem(22,tradeItem(Material.SHIELD,"Shield","Cost: 8 Iron Ingots"));inv.setItem(23,tradeItem(Material.WIND_CHARGE,"Wind Charges x4","Cost: 5 Emeralds"));p.openInventory(inv);}
    private ItemStack tradeItem(Material mat,String name,String lore){ItemStack it=new ItemStack(mat,mat==Material.WIND_CHARGE?4:1);ItemMeta m=it.getItemMeta();m.setDisplayName(ChatColor.AQUA+name);m.setLore(List.of(ChatColor.GRAY+lore,ChatColor.YELLOW+"Click to purchase"));it.setItemMeta(m);return it;}
    private boolean pay(Player p,Material mat,int amount){if(!p.getInventory().containsAtLeast(new ItemStack(mat),amount))return false;p.getInventory().removeItem(new ItemStack(mat,amount));return true;}
    @EventHandler public void onTraderClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!TRADER_TITLE.equals(e.getView().getTitle()))return;e.setCancelled(true);ItemStack it=e.getCurrentItem();if(it==null||it.getType()==Material.AIR)return;int slot=e.getRawSlot();if(slot>=10&&slot<=15){if(!p.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND),6)||!p.getInventory().containsAtLeast(new ItemStack(Material.GOLD_INGOT),8)){msg(p,"Trade requires 6 diamonds and 8 gold ingots.");return;}pay(p,Material.DIAMOND,6);pay(p,Material.GOLD_INGOT,8);Piece pc=Piece.values()[slot-10];changePiece(p,pc);p.closeInventory();return;}Material cost=null;int amount=0;switch(slot){case 20->{cost=Material.EMERALD;amount=4;}case 21->{cost=Material.GOLD_INGOT;amount=6;}case 22->{cost=Material.IRON_INGOT;amount=8;}case 23->{cost=Material.EMERALD;amount=5;}}if(cost==null)return;if(!pay(p,cost,amount)){msg(p,"You do not have enough materials.");return;}ItemStack reward=it.clone();reward.setAmount(slot==23?4:1);ItemMeta rm=reward.getItemMeta();rm.setLore(null);reward.setItemMeta(rm);p.getInventory().addItem(reward);p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_YES,1,1.2f);msg(p,"Trade successful!");}
    @EventHandler public void onInteract(PlayerInteractEvent e){if(e.getItem()==null||e.getItem().getType()!=Material.EMERALD||!e.getItem().hasItemMeta())return;ItemMeta m=e.getItem().getItemMeta();if(m.hasDisplayName()&&(m.getDisplayName().equals(ChatColor.LIGHT_PURPLE+"♟ Piece Trader"))){e.setCancelled(true);openTrader(e.getPlayer());}}
    @EventHandler public void onDamage(EntityDamageByEntityEvent e){if(e.getEntity() instanceof Player v){combatTags.put(v.getUniqueId(),System.currentTimeMillis()+10000);Player attacker=null;if(e.getDamager() instanceof Player a)attacker=a;else if(e.getDamager() instanceof org.bukkit.entity.Projectile pr&&pr.getShooter() instanceof Player a)attacker=a;if(attacker!=null){combatTags.put(attacker.getUniqueId(),System.currentTimeMillis()+10000);if(eventRunning&&eventSides.containsKey(v.getUniqueId())&&eventSides.containsKey(attacker.getUniqueId())&&sameSide(v,attacker))e.setCancelled(true);}}}
    @EventHandler public void onDeath(PlayerDeathEvent e){Player dead=e.getEntity(),killer=dead.getKiller();if(killer!=null&&!killer.equals(dead)){int earned=getPiece(dead)==Piece.KING?3:1;points.merge(killer.getUniqueId(),earned,Integer::sum);msg(killer,"Captured "+dead.getName()+"! +"+earned+" Chess Point(s).");if(eventRunning&&eventSides.containsKey(killer.getUniqueId())&&eventSides.containsKey(dead.getUniqueId())&&!sameSide(killer,dead)){if(eventSides.get(killer.getUniqueId())==Side.WHITE)whiteScore++;else blackScore++;Bukkit.broadcastMessage(GOLD+"♟ "+killer.getName()+" captured "+dead.getName()+"! White "+whiteScore+" - "+blackScore+" Black");if(whiteScore>=15||blackScore>=15)endEvent((whiteScore>=15?"WHITE":"BLACK")+" TEAM WINS!");}saveData();}combatTags.remove(dead.getUniqueId());}
    @EventHandler public void onJoin(PlayerJoinEvent e){Player p=e.getPlayer();pieces.putIfAbsent(p.getUniqueId(),Piece.PAWN);points.putIfAbsent(p.getUniqueId(),0);applyName(p);if(eventRunning){Side side=whiteScore<=blackScore?Side.WHITE:Side.BLACK;eventSides.put(p.getUniqueId(),side);Bukkit.broadcastMessage(GOLD+p.getName()+" has joined the Chess War for "+side+"!");}}
    @EventHandler public void onQuit(PlayerQuitEvent e){combatTags.remove(e.getPlayer().getUniqueId());eventSides.remove(e.getPlayer().getUniqueId());}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return Arrays.asList("choose","ability","trader","event","info","top").stream().filter(x->x.startsWith(args[0].toLowerCase())).toList();if(args.length==2&&args[0].equalsIgnoreCase("choose"))return Arrays.stream(Piece.values()).map(x->x.name().toLowerCase()).filter(x->x.startsWith(args[1].toLowerCase())).toList();if(args.length==2&&args[0].equalsIgnoreCase("event"))return List.of("start","stop","status");if(args.length==2&&args[0].equalsIgnoreCase("ability"))return List.of("1","2");return Collections.emptyList();}
}
