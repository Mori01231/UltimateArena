package net.dmulloy2.ultimatearena.commands;

import net.dmulloy2.ultimatearena.UltimateArena;
import net.dmulloy2.ultimatearena.permissions.Permission;

public class CmdReload extends UltimateArenaCommand
{
	public CmdReload(UltimateArena plugin) 
	{
		super(plugin);
		this.name = "reload";
		this.aliases.add("rl");
		this.description = "reload UltimateArena";
		this.permission = Permission.CMD_RELOAD;
		
		this.mustBePlayer = false;
	}
	
	@Override
	public void perform()
	{
		sendMessage("&aReloading UltimateArena...");
		
		long start = System.currentTimeMillis();
			
		plugin.onDisable();
		plugin.onEnable();
		
		long finish = System.currentTimeMillis();
			
		sendMessage("&aReload Complete! Took {0} ms!", finish - start);
	}
}