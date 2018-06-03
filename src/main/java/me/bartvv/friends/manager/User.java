package me.bartvv.friends.manager;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.Getter;
import lombok.Setter;
import me.bartvv.friends.Friends;
import me.bartvv.friends.glowapi.GlowAPI;
import me.bartvv.friends.glowapi.GlowAPI.Color;
import me.bartvv.friends.page.Pagination;

@Getter
@Setter
public class User {

	private UUID uuid;
	private Friends friend;
	private String openInventorySetting;
	private CommandSender sender;
	private Map<String, UUID> friends;
	private Map<String, UUID> requests;
	private int page = 0;
	private volatile boolean glow;
	private volatile boolean isWritingFriendRequest;

	public User(UUID uuid, CommandSender sender, Friends friend) {
		this.uuid = uuid;
		this.sender = sender;
		this.friend = friend;
		this.openInventorySetting = "N/A";
		try {
			Map<String, UUID> friends = Maps.newTreeMap();
			for (String list : friend.getData().getStringList("players." + uuid.toString() + ".friends")) {
				String[] arg = list.split(":::");
				String name = arg[0];
				UUID targetUUID = UUID.fromString(arg[1]);
				friends.put(name, targetUUID);
			}
			this.friends = friends;
		} catch (Exception exc) {
			this.friends = Maps.newTreeMap();
		}

		glow = friend.getData().getBoolean("players." + uuid.toString() + ".glow", true, false);

		try {
			Map<String, UUID> requests = Maps.newTreeMap();
			for (String list : friend.getData().getStringList("players." + uuid.toString() + ".requests")) {
				String[] arg = list.split(":::");
				String name = arg[0];
				UUID targetUUID = UUID.fromString(arg[1]);
				requests.put(name, targetUUID);
			}
			this.requests = requests;
		} catch (Exception exc) {
			this.requests = Maps.newTreeMap();
		}

		this.isWritingFriendRequest = false;
	}

	public void sendMessage(String message) {
		sender.sendMessage(message);
	}

	@SuppressWarnings("deprecation")
	public void calculateTeams() {
		if (sender instanceof Player) {
			Player player = (Player) sender;
			Scoreboard board = player.getScoreboard();
			if (board == null) {
				board = Bukkit.getScoreboardManager().getNewScoreboard();
				player.setScoreboard(board);
			}
			Team noFriend;
			Team friend;
			if ((noFriend = board.getTeam("noFriend")) == null) {
				noFriend = board.registerNewTeam("noFriend");
				noFriend.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.FOR_OWN_TEAM);
			}
			if ((friend = board.getTeam("friend")) == null) {
				friend = board.registerNewTeam("friend");
			}

			for (Player players : Bukkit.getOnlinePlayers()) {
				if (this.friends.containsValue(players.getUniqueId())) {
					if (this.glow) {
						if (!GlowAPI.isGlowing(players, player)) {
							Color color = Color.valueOf(this.friend.getConfig().getString("color").toUpperCase());
							GlowAPI.setGlowing(players, color, "hideForOtherTeams", "always", player);
						}
					} else { 
						GlowAPI.setGlowing(players, null, player);
					}
					friend.addPlayer(players);
					noFriend.removePlayer(players);
				} else {
					if (GlowAPI.isGlowing(players, player)) {
						GlowAPI.setGlowing(players, null, player);
					}
					noFriend.addPlayer(players);
					friend.removePlayer(players);
				}
			}
		}
	}

	public void openGUI() {
		if (sender instanceof Player) {
			this.openInventorySetting = null;
			Inventory inv = Bukkit.createInventory(null, 54, this.friend.getInventory().getString("friends.title"));

			addSkulls(inv, 0);
			page = 0;

			ItemStack requests = getFriend().getInventory().getItemStackCustom("friends.friendRequests");
			ItemMeta im = requests.getItemMeta();
			im.setDisplayName(im.getDisplayName().replace("{AMOUNT}", "" + this.requests.size()));
			requests.setItemMeta(im);
			inv.setItem(48, requests);

			ItemStack anvil = getFriend().getInventory().getItemStackCustom("friends.addFriend");
			inv.setItem(50, anvil);

			ItemStack glow = getFriend().getInventory().getItemStackCustom("friends.glowToggle");
			ItemMeta glowIm = glow.getItemMeta();
			List<String> lore = glowIm.getLore();
			for (int i = 0; i < lore.size(); i++) {
				String mode;
				if (this.glow) {
					mode = this.friend.getInventory().getString("glow.enabled");
				} else {
					mode = this.friend.getInventory().getString("glow.disabled");
				}
				lore.set(i, lore.get(i).replace("{MODE}", mode));
			}
			glowIm.setLore(lore);
			glow.setItemMeta(glowIm);
			inv.setItem(49, glow);

			Player player = (Player) sender;
			player.openInventory(inv);
			return;
		}
		throw new UnsupportedOperationException("Console cannot do /friends");
	}

	public void addSkulls(Inventory inv, int page) {
		int start = 10;
		Pagination<String> list = new Pagination<String>(28);
		list.addAll(friends.keySet());
		for (String name : list.getPage(page)) {
			ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
			ItemMeta itemMeta = head.getItemMeta();
			itemMeta.setDisplayName(name);
			head.setItemMeta(itemMeta);
			inv.setItem(start, head);
			start++;
			if ((start + 1) % 9 == 0) {
				start = start + 2;
			}
		}

		if (start < 47) {
			ItemStack empty = new ItemStack(Material.AIR);
			for (int i = start; i < 48; i++) {
				inv.setItem(i, empty);
			}
		}

		if (page > 0) {
			ItemStack back = friend.getInventory().getItemStackCustom("perFriendGUI.back");
			inv.setItem(45, back);
		} else {
			inv.setItem(45, new ItemStack(Material.AIR));
		}

		if (list.size() > (page == 0 ? 1 : page + 1) * 28) {
			ItemStack next = friend.getInventory().getItemStackCustom("perFriendGUI.next");
			inv.setItem(53, next);
		} else {
			inv.setItem(53, new ItemStack(Material.AIR));
		}

		new BukkitRunnable() {
			@Override
			public void run() {
				if (sender instanceof Player) {
					Player player = (Player) sender;
					InventoryView invView = player.getOpenInventory();
					Inventory inv = invView.getTopInventory();
					for (int i = 0; i < inv.getSize(); i++) {
						ItemStack itemStack = inv.getItem(i);
						if (itemStack != null && itemStack.getType() != Material.AIR) {
							if (itemStack.getItemMeta() instanceof SkullMeta) {
								SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
								skullMeta.setOwner(skullMeta.getDisplayName());
								itemStack.setItemMeta(skullMeta);
								inv.setItem(i, itemStack);
								player.updateInventory();
							}
						}
					}
				}
			}
		}.runTaskAsynchronously(JavaPlugin.getPlugin(Friends.class));
	}

	public void addFriendRequests(Inventory inv, int page) {
		int start = 10;
		Pagination<String> list = new Pagination<String>(28);
		list.addAll(requests.keySet());
		for (String name : list.getPage(page)) {
			ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
			ItemMeta itemMeta = head.getItemMeta();
			itemMeta.setLore(getFriend().getInventory().getStringList("requestsGUI.lore"));
			itemMeta.setDisplayName(name);
			head.setItemMeta(itemMeta);
			inv.setItem(start, head);
			start++;
			if ((start + 1) % 9 == 0) {
				start = start + 2;
			}
		}

		if (start < 47) {
			ItemStack empty = new ItemStack(Material.AIR);
			for (int i = start; i < 48; i++) {
				inv.setItem(i, empty);
			}
		}

		if (page > 0) {
			ItemStack back = friend.getInventory().getItemStackCustom("perFriendGUI.back");
			inv.setItem(45, back);
		} else {
			inv.setItem(45, new ItemStack(Material.AIR));
		}

		if (list.size() > (page == 0 ? 1 : page + 1) * 28) {
			ItemStack next = friend.getInventory().getItemStackCustom("perFriendGUI.next");
			inv.setItem(53, next);
		} else {
			inv.setItem(53, new ItemStack(Material.AIR));
		}

		new BukkitRunnable() {
			@Override
			public void run() {
				if (sender instanceof Player) {
					Player player = (Player) sender;
					InventoryView invView = player.getOpenInventory();
					Inventory inv = invView.getTopInventory();
					for (int i = 0; i < inv.getSize(); i++) {
						ItemStack itemStack = inv.getItem(i);
						if (itemStack != null && itemStack.getType() != Material.AIR) {
							if (itemStack.getItemMeta() instanceof SkullMeta) {
								SkullMeta skullMeta = (SkullMeta) itemStack.getItemMeta();
								skullMeta.setOwner(skullMeta.getDisplayName());
								itemStack.setItemMeta(skullMeta);
								inv.setItem(i, itemStack);
								player.updateInventory();
							}
						}
					}
				}
			}
		}.runTaskAsynchronously(JavaPlugin.getPlugin(Friends.class));
	}

	public void openFriendGUI(String name) {
		Inventory inventory = Bukkit.createInventory(null, 9, friend.getInventory().getString("perFriendGUI.title"));
		ItemStack back = friend.getInventory().getItemStackCustom("perFriendGUI.back");
		ItemStack delete = friend.getInventory().getItemStackCustom("perFriendGUI.delete");
		ItemMeta itemMeta = delete.getItemMeta();
		itemMeta.setDisplayName(itemMeta.getDisplayName().replace("{0}", name));
		delete.setItemMeta(itemMeta);
		inventory.setItem(0, back);
		inventory.setItem(4, delete);
		if (sender instanceof Player) {
			((Player) sender).openInventory(inventory);
			this.openInventorySetting = name;
		}
	}

	public void save() {
		FileManager data = friend.getData();
		List<String> friends = Lists.newArrayList();
		for (Entry<String, UUID> entries : this.friends.entrySet()) {
			friends.add(entries.getKey() + ":::" + entries.getValue().toString());
		}
		data.set("players." + getUuid() + ".friends", friends);
		List<String> requests = Lists.newArrayList();
		for (Entry<String, UUID> entries : this.requests.entrySet()) {
			requests.add(entries.getKey() + ":::" + entries.getValue().toString());
		}
		data.set("players." + getUuid() + ".requests", requests);
		data.set("players." + getUuid() + ".glow", this.glow);
		data.save();
	}
}
