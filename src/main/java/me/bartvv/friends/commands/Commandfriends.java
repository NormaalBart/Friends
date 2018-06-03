package me.bartvv.friends.commands;

import org.bukkit.command.Command;

import me.bartvv.friends.manager.User;

public class Commandfriends extends ICommand {

	@Override
	public void onCommand(User user, Command command, String commandLabel, String[] args) throws Exception {
		user.openGUI();
	}
}
