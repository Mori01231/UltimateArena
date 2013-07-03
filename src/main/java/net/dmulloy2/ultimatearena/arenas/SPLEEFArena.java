package net.dmulloy2.ultimatearena.arenas;

import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.dmulloy2.ultimatearena.Field3D;
import net.dmulloy2.ultimatearena.arenas.objects.ArenaPlayer;
import net.dmulloy2.ultimatearena.arenas.objects.ArenaZone;
import net.dmulloy2.ultimatearena.util.Util;

public class SPLEEFArena extends FFAArena 
{
	public Field3D spleefGround;
	public Field3D outZone;
	
	public SPLEEFArena(ArenaZone az)
	{
		super(az);
		
		setType("Spleef");
		setMaxDeaths(2);

		spleefGround = new Field3D(az.getPlugin(), this.getWorld());
		Location pos1 = az.getFlags().get(0);
		Location pos2 = az.getFlags().get(1);
		spleefGround.setParam(pos1.getWorld(), pos1.getX(), pos1.getZ(), pos1.getY()-1, pos2.getX(), pos2.getZ(), pos2.getY()-1);
		spleefGround.setType(this.getArenaZone().getSpecialType());
			
		outZone = new Field3D(az.getPlugin(), this.getWorld());
		Location pos3 = az.getFlags().get(2);
		Location pos4 = az.getFlags().get(3);
		outZone.setParam(pos3.getWorld(), pos3.getX(), pos3.getZ(), pos3.getY()-1, pos4.getX(), pos4.getZ(), pos4.getY()-1);
	}
	
	@Override
	public void spawn(String name, boolean alreadySpawned)
	{
		super.spawn(name, false);
		Player p = Util.matchPlayer(name);
		if (p != null)
		{
			Location loc = getBlockInSpleefArena(0);
			if (loc != null) 
			{
				teleport(p, loc.clone().add(0,2,0));
			}
		}
	}

	public Location getBlockInSpleefArena(int repeat)
	{
		Random rand = new Random();
		Location ret = null;
		int checkx = rand.nextInt(spleefGround.width-1);
		int checkz = rand.nextInt(spleefGround.length-1);
		Block b = spleefGround.getBlockAt(checkx + 1, 0, checkz + 1);
		Material mat = b.getType();
		if (mat.getId() == getArenaZone().getSpecialType())
		{
			ret = b.getLocation();
		}
		else
		{
			if (repeat < (spleefGround.width*spleefGround.height) / 2) 
			{
				ret = getBlockInSpleefArena(repeat+1);
			}
		}
		
		return ret;
	}

	@Override
	public void check() 
	{
		if (this.getAmtPlayersInArena() == 1)
		{
			if (this.getAmtPlayersStartingInArena() > 1)
			{
				this.setWinningTeam(-1);
			}
		}
		if (!checkEmpty())
		{
			for (int i = 0; i < getArenaplayers().size(); i++)
			{
				ArenaPlayer apl = getArenaplayers().get(i);
				Player pl = apl.getPlayer();
				Location ploc = pl.getLocation();
				if (pl.getHealth() > 0) {
					if (outZone.isUnder(ploc))
					{
						pl.setHealth(0);
					}
				}
			}
		}
		else
		{
			rewardTeam(-1, ChatColor.BLUE + "You won!", false);
			stop();
		}
	}
}