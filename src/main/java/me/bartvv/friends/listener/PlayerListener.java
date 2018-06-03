package me.bartvv.friends.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import lombok.AllArgsConstructor;
import me.bartvv.friends.Friends;
import me.bartvv.friends.manager.User;

@AllArgsConstructor
public class PlayerListener implements Listener {

	private Friends friends;

	@EventHandler
	public void on(PlayerJoinEvent e) {
		this.friends.getUsers().put(e.getPlayer().getUniqueId(),
				new User(e.getPlayer().getUniqueId(), e.getPlayer(), friends));
	}

	@EventHandler
	public void on(PlayerQuitEvent e) {
		User user = friends.getUser(e.getPlayer().getUniqueId());
		user.save();
		friends.getUsers().remove(e.getPlayer().getUniqueId());
	}

	@EventHandler
	public void on(AsyncPlayerChatEvent e) {
		Player player = e.getPlayer();
		User user = friends.getUser(player.getUniqueId());
		if (user.isWritingFriendRequest()) {
			String msg = e.getMessage();
			Player target = Bukkit.getPlayer(msg);
			if (target == null) {
				player.sendMessage(friends.getMessages().getString("friends.noPlayer"));
				user.setWritingFriendRequest(false);
				e.setCancelled(true);
				return;
			}
			User receiver = friends.getUser(target.getUniqueId());
			receiver.getRequests().put(player.getName(), player.getUniqueId());
			player.sendMessage(friends.getMessages().getString("friends.send"));
			target.sendMessage(
					friends.getMessages().getString("friends.received").replace("{PLAYER}", player.getName()));
			e.setCancelled(true);
			user.setWritingFriendRequest(false);
			return;
		}
	}
}
