package me.bartvv.friends.commands;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;

import lombok.Getter;
import lombok.Setter;
import me.bartvv.friends.Friends;
import me.bartvv.friends.manager.User;

@Getter
@Setter
public class ICommand {
	
	private Friends friends;

	public void onCommand(User user, Command command, String commandLabel, String[] args) throws Exception {
		throw new Exception("Not-Supported");
	}

	public List<String> onTabComplete(User user, String commandLabel, String[] args) throws Exception {
		return Collections.emptyList();
	}
}