package me.bartvv.friends;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.inventivetalent.apihelper.APIManager;

import com.google.common.collect.Maps;

import lombok.Getter;
import me.bartvv.friends.commands.ICommand;
import me.bartvv.friends.glowapi.GlowAPI;
import me.bartvv.friends.listener.InventoryListener;
import me.bartvv.friends.listener.PlayerListener;
import me.bartvv.friends.manager.FileManager;
import me.bartvv.friends.manager.User;

@Getter
public class Friends extends JavaPlugin implements Listener {

	private transient FileManager messages, inventory, data, config;
	private transient final UUID consoleUUID;
	private transient Map<Command, ICommand> commandCache;
	private transient Map<UUID, User> users;
	private GlowAPI glowAPI;

	public Friends() {
		this.consoleUUID = UUID.randomUUID();
	}

	@Override
	public void onLoad() {
		this.glowAPI = new GlowAPI();
		this.glowAPI.friends = this;
		APIManager.registerAPI(this.glowAPI, this);
	}

	@Override
	public void onEnable() {
		APIManager.initAPI(this.glowAPI.getClass());
		messages = new FileManager(this, "messages.yml", 10);
		inventory = new FileManager(this, "inventory.yml", 10);
		config = new FileManager(this, "config.yml", 10);
		data = new FileManager(this, "data.yml", 10);
		this.commandCache = Maps.newHashMap();
		users = Maps.newHashMap();
		getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
		getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
		users.put(consoleUUID, new User(consoleUUID, Bukkit.getConsoleSender(), this));

		for (Player player : Bukkit.getOnlinePlayers()) {
			users.put(player.getUniqueId(), new User(player.getUniqueId(), player, this));
		}

		new BukkitRunnable() {

			@Override
			public void run() {
				for (User user : getUsers().values()) {
					user.calculateTeams();
				}
			}
		}.runTaskTimerAsynchronously(this, 0, 20);
	}

	@Override
	public void onDisable() {
		for (User user : this.users.values()) {
			if (user.getUuid() == consoleUUID)
				continue;
			user.save();
		}
		data.save();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		ICommand iCommand = commandCache.get(command);

		if (iCommand == null) {
			try {
				iCommand = (ICommand) Friends.class.getClassLoader()
						.loadClass("me.bartvv.friends.commands.Command" + command.getName()).newInstance();
				iCommand.setFriends(this);
				commandCache.put(command, iCommand);
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException exc) {
				sender.sendMessage("Something went wrong! " + exc.getMessage());
				exc.printStackTrace();
				return true;
			}

		}

		UUID uuid;
		if (sender instanceof Player) {
			uuid = ((Player) sender).getUniqueId();
		} else {
			uuid = consoleUUID;
		}

		User user = getUser(uuid);
		user.setSender(sender);

		try {
			iCommand.onCommand(user, command, label, args);
		} catch (Exception e) {
			try {
				e.printStackTrace();
			} catch (Exception exc) {
				e.printStackTrace();
			}
		}
		return true;
	}

	public User getUser(UUID uuid) {
		return users.get(uuid);
	}

}
