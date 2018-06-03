package me.bartvv.friends.listener;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import lombok.AllArgsConstructor;
import me.bartvv.friends.Friends;
import me.bartvv.friends.manager.FileManager;
import me.bartvv.friends.manager.User;

@AllArgsConstructor
public class InventoryListener implements Listener {

	private Friends friends;

	@EventHandler
	public void on(InventoryClickEvent e) {
		Inventory inv = e.getClickedInventory();

		if (!(e.getWhoClicked() instanceof Player))
			return;

		if (inv == null)
			return;

		if (inv instanceof PlayerInventory)
			return;

		if (e.getSlot() == -999)
			return;

		Player player = (Player) e.getWhoClicked();
		User user = friends.getUsers().get(player.getUniqueId());

		ItemStack click = inv.getItem(e.getSlot());

		if (click == null)
			return;

		if (inv.getTitle().equalsIgnoreCase(this.friends.getInventory().getString("friends.title"))) {
			if (click.getType() == Material.SKULL_ITEM) {
				if (click.getItemMeta() instanceof SkullMeta) {
					e.setCancelled(true);
					SkullMeta skullMeta = (SkullMeta) click.getItemMeta();
					String name = skullMeta.getDisplayName();
					user.openFriendGUI(name);
				}
			} else if (e.getSlot() == 49) {
				e.setCancelled(true);
				user.setGlow(!user.isGlow());
				ItemStack glow = friends.getInventory().getItemStackCustom("friends.glowToggle");
				ItemMeta glowIm = glow.getItemMeta();
				List<String> lore = glowIm.getLore();
				for (int i = 0; i < lore.size(); i++) {
					String mode;
					if (user.isGlow()) {
						mode = friends.getInventory().getString("glow.enabled");
					} else {
						mode = friends.getInventory().getString("glow.disabled");
					}
					lore.set(i, lore.get(i).replace("{MODE}", mode));
				}
				glowIm.setLore(lore);
				glow.setItemMeta(glowIm);
				inv.setItem(49, glow);
			} else if (e.getSlot() == 50) {
				e.setCancelled(true);
				player.sendMessage(this.friends.getMessages().getString("friends.typeName"));
				player.closeInventory();
				user.setWritingFriendRequest(true);
			} else if (e.getSlot() == 48) {
				e.setCancelled(true);
				Inventory requestInv = Bukkit.createInventory(null, 54,
						this.friends.getInventory().getString("requestsGUI.title"));

				user.addFriendRequests(requestInv, 0);
				ItemStack back = this.friends.getInventory().getItemStackCustom("requestsGUI.back");
				requestInv.setItem(45, back);
				player.closeInventory();
				player.openInventory(requestInv);
			} else if (e.getSlot() == 53) {
				if (e.getCurrentItem() == null)
					return;
				if (!e.getCurrentItem().hasItemMeta())
					return;
				if (!e.getCurrentItem().getItemMeta().hasDisplayName())
					return;

				if (e.getCurrentItem().getItemMeta().getDisplayName().equals(friends.getInventory()
						.getItemStackCustom("perFriendGUI.next").getItemMeta().getDisplayName())) {
					e.setCancelled(true);
					user.addSkulls(inv, user.getPage() + 1);
					user.setPage(user.getPage() + 1);
					player.updateInventory();
				}
			} else if (e.getSlot() == 45) {
				if (e.getCurrentItem() == null)
					return;
				if (!e.getCurrentItem().hasItemMeta())
					return;
				if (!e.getCurrentItem().getItemMeta().hasDisplayName())
					return;

				if (e.getCurrentItem().getItemMeta().getDisplayName().equals(friends.getInventory()
						.getItemStackCustom("perFriendGUI.back").getItemMeta().getDisplayName())) {
					e.setCancelled(true);
					user.addSkulls(inv, user.getPage() - 1);
					user.setPage(user.getPage() - 1);
					player.updateInventory();
				}
			}
		}

		else if (inv.getTitle().equalsIgnoreCase(this.friends.getInventory().getString("perFriendGUI.title"))) {
			if (e.getSlot() == 0) {
				user.openGUI();
				return;
			}

			else if (e.getSlot() == 4) {
				e.setCancelled(true);
				UUID uuid = user.getFriends().get(user.getOpenInventorySetting());

				if (uuid == null)
					return;

				user.getFriends().remove(user.getOpenInventorySetting(), uuid);

				User target = this.friends.getUser(uuid);
				if (target != null) {
					target.getFriends().remove(player.getName(), player.getUniqueId());
				} else {
					FileManager data = this.friends.getData();
					List<String> friends = data.getStringList("players." + uuid + ".friends");
					friends.remove(player.getName() + ":::" + player.getUniqueId().toString());
					data.set("players." + uuid + ".friends", friends);
					data.save();
				}
				player.closeInventory();
			}
		} else if (inv.getTitle().equalsIgnoreCase(this.friends.getInventory().getString("requestsGUI.title"))) {
			e.setCancelled(true);
			if (e.getSlot() == 45) {
				player.closeInventory();
				user.openGUI();
				return;
			}
			if (click.getItemMeta() instanceof SkullMeta) {
				SkullMeta skullMeta = (SkullMeta) click.getItemMeta();
				String name = skullMeta.getDisplayName();
				Player target = Bukkit.getPlayer(name);
				if (e.getClick() == ClickType.LEFT) {
					// DECLINE
					player.closeInventory();
					if (target == null) {

					} else {
						User targetUser = friends.getUser(target.getUniqueId());
						targetUser.getFriends().remove(player.getName(), player.getUniqueId());
						user.getFriends().remove(target.getName(), target.getUniqueId());
						target.sendMessage(friends.getMessages().getString("friends.declinedTarget").replace("{PLAYER}",
								player.getName()));
					}
					user.getRequests().remove(name);
					player.sendMessage(
							friends.getMessages().getString("friends.declined").replace("{PLAYER}", target.getName()));
				} else if (e.getClick() == ClickType.RIGHT) {
					// ACCEPT
					player.closeInventory();
					if (target == null) {
						FileManager data = this.friends.getData();
						List<String> friends = data
								.getStringList("players." + user.getRequests().get(name) + ".friends");
						friends.add(player.getName() + ":::" + player.getUniqueId().toString());
						data.set("players." + user.getRequests().get(name) + ".friends", friends);
						data.save();
					} else {
						User targetUser = friends.getUser(target.getUniqueId());
						targetUser.getFriends().put(player.getName(), player.getUniqueId());
						target.sendMessage(friends.getMessages().getString("friends.acceptedTarget").replace("{PLAYER}",
								player.getName()));
					}
					user.getRequests().remove(name);
					user.getFriends().put(target.getName(), target.getUniqueId());
					player.sendMessage(
							friends.getMessages().getString("friends.accepted").replace("{PLAYER}", target.getName()));
				}
			}
		}
	}

	@EventHandler
	public void on(InventoryCloseEvent e) {
		User user = friends.getUser(e.getPlayer().getUniqueId());
		if (user != null) {
			user.setPage(1);
		}
	}

}
