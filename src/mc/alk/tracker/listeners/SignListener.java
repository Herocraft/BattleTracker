package mc.alk.tracker.listeners;

import mc.alk.tracker.Defaults;
import mc.alk.tracker.Tracker;
import mc.alk.tracker.controllers.MessageController;
import mc.alk.tracker.controllers.SignController;
import mc.alk.tracker.objects.SignType;
import mc.alk.tracker.objects.StatSign;
import mc.alk.tracker.objects.StatType;
import mc.alk.tracker.objects.exceptions.InvalidSignException;
import mc.alk.util.AutoClearingTimer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SignListener implements Listener{
	SignController signController;
	AutoClearingTimer<String> timer = new AutoClearingTimer<String>();
	public static final int SECONDS = 5;
	public SignListener(SignController signController){
		this.signController = signController;
		timer.setSaveEvery(61050); /// will auto clear records every minute, and 1sec
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.isCancelled()) return;
		final Block block = event.getClickedBlock();
		final Material type = block.getType();
		if (!(type.equals(Material.SIGN) || type.equals(Material.SIGN_POST) || type.equals(Material.WALL_SIGN))) {
			return ;}
		StatSign ss = signController.getSign(event.getClickedBlock().getLocation());
		if (ss == null)
			return;
		if (timer.withinTime(event.getPlayer().getName(),SECONDS*1000L)){
			event.getPlayer().sendMessage(ChatColor.RED+"wait");
			return;
		} else {
			timer.put(event.getPlayer().getName());
			final Location l = block.getLocation();
			Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
				@Override
				public void run(){
					l.getWorld().getBlockAt(l).getState().update(true);
				}
			},SECONDS*20);
		}
		signController.clickedSign(event.getPlayer(), (Sign) block.getState(), ss);
		return;
	}

	@EventHandler
	public void onSignChange(SignChangeEvent event){
		final Block block = event.getBlock();
		final Material type = block.getType();
		if (!(type.equals(Material.SIGN) || type.equals(Material.SIGN_POST) || type.equals(Material.WALL_SIGN))) {
			return;}
		StatSign ss = null;
		try {
			ss = getStatSign(block.getLocation(), event.getLines());
		} catch (InvalidSignException e) {
			MessageController.sendMessage(event.getPlayer(), e.getMessage());
			return;
		}
		if (ss == null){
			return;}
		if (!event.getPlayer().hasPermission(Defaults.ADMIN_PERM)){
			MessageController.sendMessage(event.getPlayer(), "&cYou don't have perms to create top signs");
			cancelSignPlace(event, block);
			return;
		}
		String lines[] = event.getLines();
		lines[0] = "[&e"+ss.getDBName()+"&0]";
		lines[1] = "[&e"+ss.getStatType()+"&0]";
		lines[2] = "&2Updating";
		for (int i=0;i<lines.length;i++){
			lines[i] = MessageController.colorChat(lines[i]);
		}
		signController.addSign(ss);
		Bukkit.getScheduler().scheduleSyncDelayedTask(Tracker.getSelf(), new Runnable(){
			@Override
			public void run() {
				signController.updateSigns();
			}
		},40);
		Tracker.getSelf().saveConfig();
	}

	private StatSign getStatSign(Location l, String lines[]) throws InvalidSignException {
		/// Quick check to make sure this is even a stat sign
		if (lines[0].isEmpty() || lines[0].charAt(0) != '['){
			return null;}

		/// find the Sign Type, like top, personal
		String strType = lines[1].replace('[', ' ').replace(']', ' ').trim();
		SignType signType = SignType.fromName(strType);
		if (signType == null)
			return null;

		/// find the database
		String db = lines[0].replace('[', ' ').replace(']', ' ').trim();
		if (!Tracker.hasInterface(db)){
			throw new InvalidSignException("Tracker Database " + db +" not found");}
		StatType st = StatType.fromName(strType);
		if (st == null){
			return null;}
		StatSign ss = new StatSign(db, l, SignType.TOP); /// TODO change when we have more than 1 type
		ss.setStatType(st);
		return ss;
	}

	public static void cancelSignPlace(SignChangeEvent event, Block block){
		event.setCancelled(true);
		block.setType(Material.AIR);
		block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SIGN, 1));
	}
}
