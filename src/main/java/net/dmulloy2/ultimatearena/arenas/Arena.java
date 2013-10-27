package net.dmulloy2.ultimatearena.arenas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lombok.Getter;
import lombok.Setter;
import net.dmulloy2.ultimatearena.UltimateArena;
import net.dmulloy2.ultimatearena.flags.ArenaFlag;
import net.dmulloy2.ultimatearena.tasks.ArenaFinalizeTask;
import net.dmulloy2.ultimatearena.tasks.EntityClearTask;
import net.dmulloy2.ultimatearena.types.ArenaClass;
import net.dmulloy2.ultimatearena.types.ArenaPlayer;
import net.dmulloy2.ultimatearena.types.ArenaZone;
import net.dmulloy2.ultimatearena.types.FieldType;
import net.dmulloy2.ultimatearena.types.Permission;
import net.dmulloy2.ultimatearena.types.PotionType;
import net.dmulloy2.ultimatearena.util.FormatUtil;
import net.dmulloy2.ultimatearena.util.InventoryHelper;
import net.dmulloy2.ultimatearena.util.Util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;

import com.earth2me.essentials.User;

/**
 * Base Data Container for an arena. 
 * <p>
 * This can be extended for specific arena types.
 * 
 * @author dmulloy2
 */

@Getter
@Setter
public abstract class Arena
{
	public static enum Mode
	{
		LOBBY, INGAME, STOPPING, IDLE, DISABLED;
	}

	protected List<ArenaPlayer> active;
	protected List<ArenaPlayer> inactive;

	protected List<ArenaFlag> flags;
	protected List<Location> spawns;

	private List<String> blacklistedClasses;
	private List<String> whitelistedClasses;
	
	protected int broadcastTimer = 45;
	protected int winningTeam = 999;
	protected int maxPoints = 60;
	protected int maxDeaths = 1;
	protected int maxWave = 15;

	protected int startingAmount;
	protected int maxGameTime;
	protected int startTimer;
	protected int gameTimer;
	protected int team1size;
	protected int team2size;
	protected int announced;
	protected int wave;

	protected boolean allowTeamKilling;
	protected boolean rewardBasedOnXp;
	protected boolean pauseStartTimer;
	protected boolean countMobKills;
	protected boolean configLoaded;
	protected boolean forceStop;
	protected boolean stopped;
	protected boolean start;

	protected boolean updatedTeams;
	protected boolean disabled;

	protected Mode gameMode = Mode.DISABLED;

	protected final World world;
	protected FieldType type;
	protected String name;

	protected final UltimateArena plugin;
	protected final ArenaZone az;

	/**
	 * Creates a new Arena based around an {@link ArenaZone}
	 * 
	 * @param az
	 *            - {@link ArenaZone} to base the {@link Arena} around
	 */
	public Arena(ArenaZone az)
	{
		this.az = az;
		this.plugin = az.getPlugin();
		this.name = az.getArenaName();
		this.type = az.getType();
		this.world = az.getWorld();
		this.az.setTimesPlayed(az.getTimesPlayed() + 1);

		this.active = new ArrayList<ArenaPlayer>();
		this.inactive = new ArrayList<ArenaPlayer>();

		this.flags = new ArrayList<ArenaFlag>();
		this.spawns = new ArrayList<Location>();

		this.gameMode = Mode.LOBBY;
		
		plugin.getSpectatingHandler().registerArena(this);
	}
	
	/**
	 * Loads / reloads the config
	 */
	public final void reloadConfig()
	{
		this.configLoaded = true;

		this.maxGameTime = az.getGameTime();
		this.gameTimer = az.getGameTime();
		this.startTimer = az.getLobbyTime();
		this.maxDeaths = az.getMaxDeaths();
		this.allowTeamKilling = az.isAllowTeamKilling();
		this.maxWave = az.getMaxWave();
		this.maxPoints = az.getMaxPoints();
		this.countMobKills = az.isCountMobKills();
		this.rewardBasedOnXp = az.isRewardBasedOnXp();
		
		this.blacklistedClasses = az.getBlacklistedClasses();
		this.whitelistedClasses = az.getWhitelistedClasses();

		if (maxDeaths < 1)
		{
			this.maxDeaths = 1;
		}
	}

	/**
	 * Adds a player to an {@link Arena}.
	 * <p>
	 * Should not be overriden.
	 * 
	 * @param player
	 *            - {@link Player} to add to an arena
	 */
	public final void addPlayer(Player player)
	{
		player.sendMessage(plugin.getPrefix() + FormatUtil.format("&3Joining arena &e{0}&3... Please wait!", name));

		ArenaPlayer pl = new ArenaPlayer(player, this, plugin);

		// Update Teams
		pl.setTeam(getTeam());
		
		this.updatedTeams = true;

		// Teleport the player to the lobby spawn
		spawnLobby(pl);

		// Inventory
		pl.saveInventory();
		pl.clearInventory();

		// XP
		pl.setBaseLevel(player.getLevel());

		// Make sure the player is in survival
		player.setGameMode(GameMode.SURVIVAL);

		// Heal up the Player
		player.setFoodLevel(20);
		player.setFireTicks(0);
		player.setHealth(20);

		// Don't allow flight
		player.setAllowFlight(false);
		player.setFlySpeed(0.1F);
		player.setFlying(false);

		// If essentials is found, remove god mode.
		if (plugin.isUseEssentials())
		{
			User user = plugin.getEssentials().getUser(player);
			
			// Disable GodMode in the arena
			user.setGodModeEnabled(false);
		}

		// Clear potion effects
		pl.clearPotionEffects();
		
		// Decide Hat
		pl.decideHat();

		// Finally add the player
		active.add(pl);

		tellPlayers("&a{0} has joined the arena! ({1}/{2})", pl.getName(), active.size(), az.getMaxPlayers());
	}

	/**
	 * Returns which team a new player should be on.
	 * <p>
	 * Can be overriden in certain cases.
	 */
	public int getTeam()
	{
		return 1;
	}

	/**
	 * Announces the arena's existance and reminds players to join.
	 */
	public final void announce()
	{
		for (Player player : plugin.getServer().getOnlinePlayers())
		{
			if (! plugin.isInArena(player))
			{
				if (plugin.getPermissionHandler().hasPermission(player, Permission.JOIN))
				{
					if (announced == 0)
					{
						player.sendMessage(plugin.getPrefix() + 
								FormatUtil.format("&e{0} &3arena has been created!", type.stylize()));
					}
					else
					{
						player.sendMessage(plugin.getPrefix() + 
								FormatUtil.format("&3Hurry up and join the &e{0} &3arena!", type.stylize()));
					}

					player.sendMessage(plugin.getPrefix() + 
							FormatUtil.format("&3Type &e/ua join {0} &3to join!", az.getArenaName()));
				}
			}
		}

		announced++;
	}

	/**
	 * Returns the team with the least number of players on it.
	 */
	public final int getBalancedTeam()
	{
		// Refresh team size
		updateTeams();

		return team1size > team2size ? 2 : 1;
	}

	/**
	 * A simple team check.
	 * 
	 * @param stopifEmpty
	 *            - Stops the arena if empty
	 */
	public boolean simpleTeamCheck(boolean stopifEmpty) 
	{
		if (team1size == 0 || team2size == 0) 
		{
			if (stopifEmpty)
			{
				stop();
			}
            
			if (startingAmount > 1)
			{
				return false;
			}
                 
			return true;
		}

		return true;
	}

	/**
	 * Returns a {@link Player}'s {@link ArenaPlayer} instance.
	 * <p>
	 * Every player who has joined this arena will have an ArenaPlayer instance.
	 * It is important to note, however, that players who are out will still have
	 * arena player instances until the arena concludes.
	 * 
	 * @param p
	 *            - Player instance
	 * @param checkInactive
	 *            - Whether or not to check the inactive list as well
	 */
	public final ArenaPlayer getArenaPlayer(Player p, boolean checkInactive)
	{
		for (ArenaPlayer ap : active)
		{
			if (ap.getName().equalsIgnoreCase(p.getName()))
				return ap;
		}
		
		if (checkInactive)
		{
			for (ArenaPlayer ap : inactive)
			{
				if (ap.getName().equalsIgnoreCase(p.getName()))
					return ap;
			}
		}

		return null;
	}
	
	/**
	 * Alias for {@link #getArenaPlayer(Player, boolean)}
	 * <p>
	 * Has the same effect as <code>getArenaPlayer(p, true)</code>
	 * 
	 * @param p
	 *            - Player instance
	 */
	public final ArenaPlayer getArenaPlayer(Player p)
	{
		return getArenaPlayer(p, false);
	}

	/**
	 * Spawns all players in an arena.
	 */
	public final void spawnAll()
	{
		plugin.debug("Spawning players for Arena {0}", name);
		
		for (ArenaPlayer ap : active)
		{
			spawn(ap.getPlayer());
		}
	}

	/**
	 * Gets the spawn for an {@link ArenaPlayer}.
	 * <p>
	 * Can be overriden under certain circumstances
	 * 
	 * @param ap
	 *            - {@link ArenaPlayer} instance
	 */
	public Location getSpawn(ArenaPlayer ap)
	{
		Location loc = null;
		if (isInLobby())
		{
			loc = az.getLobbyREDspawn();
			if (ap.getTeam() == 2)
				loc = az.getLobbyBLUspawn();
		}
		else
		{
			loc = az.getTeam1spawn();
			if (ap.getTeam() == 2)
				loc = az.getTeam2spawn();
		}

		return loc;
	}

	/**
	 * Spawns a player in an {@link Arena}.
	 * <p>
	 * This should not be overriden.
	 * 
	 * @param name
	 *            - Player to spawn
	 * @param alreadySpawned
	 *            - Whether or not they've already spawned
	 */
	public final void spawn(Player player, boolean alreadySpawned)
	{
		plugin.debug("Attempting to spawn player: {0}", player.getName());

		if (! stopped)
		{
			ArenaPlayer ap = getArenaPlayer(player);
			if (ap.getDeaths() < getMaxDeaths())
			{
				plugin.debug("Spawning player: {0}", player.getName());

				Location loc = getSpawn(ap);
				if (loc != null)
				{
					teleport(player, loc);
				}

				ap.spawn();

				if (!alreadySpawned)
				{
					onSpawn(ap);
				}
			}
		}
	}
	
	/**
	 * Alias for {@link #spawn(Player, Boolean)}
	 * <p>
	 * Has the same effect of <code>spawn(player, false)</code>
	 * 
	 * @param player
	 *            - Player to spawn
	 */
	public final void spawn(Player player)
	{
		spawn(player, false);
	}
	
	/**
	 * Spawns an {@link ArenaPlayer} into the lobby
	 * 
	 * @param ap 
	 *            - {@link ArenaPlayer} to spawn
	 */
	public final void spawnLobby(ArenaPlayer ap)
	{
		Location loc = getSpawn(ap);
		if (loc != null)
		{
			plugin.debug("Spawning player {0} in the lobby", ap.getName());

			teleport(ap.getPlayer(), loc);
		}
	}

	/**
	 * Called when a player is spawned.
	 * 
	 * @param apl
	 *            - {@link ArenaPlayer} who was spawned
	 */
	public void onSpawn(ArenaPlayer apl)
	{
	}

	/**
	 * Called when a player dies.
	 * 
	 * @param pl
	 *            - {@link ArenaPlayer} who died
	 */
	public void onPlayerDeath(ArenaPlayer pl)
	{
		pl.setAmtKicked(0);
	}

	/**
	 * Default rewarding system. May be overriden in some cases.
	 * 
	 * @param ap
	 *            - {@link ArenaPlayer} to reward
	 */
	public void reward(ArenaPlayer ap)
	{
		az.giveRewards(ap);
	}

	/**
	 * Rewards an entire team.
	 * 
	 * @param team
	 *            - Team to reward
	 */
	public final void rewardTeam(int team)
	{
		for (ArenaPlayer ap : active)
		{
			if (ap.isCanReward())
			{
				if (ap.getTeam() == team || team == -1)
				{
					reward(ap);
				}	
			}
		}
	}

	/**
	 * Sets the winning team.
	 * 
	 * @param team
	 *            - Winning team
	 */
	public final void setWinningTeam(int team)
	{
		for (ArenaPlayer ap : active)
		{
			ap.setCanReward(false);
			if (ap.getTeam() == team || team == -1)
			{
				ap.setCanReward(true);
			}
		}

		this.winningTeam = team;
	}

	/**
	 * Checks if a player has enough points to win.
	 * 
	 * @param max
	 *            - Max points for an arena
	 */
	public final void checkPlayerPoints(int max)
	{
		for (ArenaPlayer ap : active)
		{
			if (ap.getPoints() >= max)
			{
				tellAllPlayers("&3Player &e{0} &3has won!", ap.getName());

				stop();

				reward(ap);
			}
		}
	}

	/**
	 * Stops the arena if empty.
	 * 
	 * @return Whether or not the arena is empty
	 */
	public final boolean checkEmpty()
	{
		boolean ret = isEmpty();
		if (ret)
			stop();

		return ret;
	}

	/**
	 * Checks if the arena is empty.
	 * 
	 * @return Whether or not the arena is empty
	 */
	public final boolean isEmpty()
	{
		return isInGame() && active.size() <= 1;
	}

	/**
	 * Tells all active players in the arena a message.
	 * 
	 * @param string
	 *            - Base message
	 * @param objects
	 *            - Objects to format in
	 */
	public final void tellPlayers(String string, Object... objects)
	{
		for (ArenaPlayer ap : active)
		{
			ap.sendMessage(string, objects);
		}
	}
	
	/**
	 * Tells all players a message.
	 * <p>
	 * Includes inactive players
	 * 
	 * @param string
	 *            - Base message
	 * @param objects
	 *            - Objects to format in
	 */
	public final void tellAllPlayers(String string, Object... objects)
	{
		tellPlayers(string, objects);

		for (ArenaPlayer ap : inactive)
		{
			if (ap != null && ap.getPlayer().isOnline())
			{
				ap.sendMessage(string, objects);
			}
		}
	}

	/**
	 * Kills all players within a certain radius of a {@link Location}
	 * 
	 * @param loc
	 *            - Center {@link Location}
	 * @param rad
	 *            - Radius to kill within
	 */
	public final void killAllNear(Location loc, int rad)
	{
		plugin.debug("Killing all players near {0} in a radius of {1}", Util.locationToString(loc), rad);

		for (ArenaPlayer ap : active)
		{
			Location ploc = ap.getPlayer().getLocation();
			if (Util.pointDistance(loc, ploc) < rad)
				ap.getPlayer().setHealth(0.0D);
		}
	}

	/**
	 * Returns a random spawn for an {@link ArenaPlayer}.
	 * 
	 * @param ap
	 *            - {@link ArenaPlayer} to get spawn for
	 */
	public Location getRandomSpawn(ArenaPlayer ap)
	{
		plugin.debug("Getting a random spawn for {0}", ap.getName());

		if (! spawns.isEmpty())
		{
			return spawns.get(Util.random(spawns.size()));

		}

		return null;
	}
	
	/**
	 * Gives a {@link Player} an item
	 * 
	 * @param player
	 *            - {@link Player} to give items to
	 * @param mat
	 *            - {@link Material} of the item to give
	 * @param dat
	 *            - Data value of the item
	 * @param amt
	 *            - Amount of the item to give
	 * @param message
	 *            - Message to send the {@link Player}
	 */
	public final void giveItem(Player player, Material mat, int amt, short dat, String message)
	{
		if (! message.isEmpty())
		{
			player.sendMessage(plugin.getPrefix() + FormatUtil.format(message));
		}
		
		ItemStack item = new ItemStack(mat, amt, dat);
		
		InventoryHelper.addItem(player, item);
	}

	/**
	 * Gives a player a {@link Potion}
	 * 
	 * @param pl
	 *            - {@link Player} to give the {@link Potion}
	 * @param s
	 *            - Name of the {@link Potion} to give. Must be a valid
	 *            {@link PotionType}
	 * @param amt
	 *            - Amount of the {@link Potion} to give
	 * @param level
	 *            - Level of the {@link Potion} to give
	 * @param splash
	 *            - Whether or not it is a splash {@link Potion}
	 * @param message
	 *            - Message to send to the {@link Player}
	 */
	public void givePotion(Player pl, String s, int amt, int level, boolean splash, String message)
	{
		if (! message.isEmpty())
		{
			pl.sendMessage(plugin.getPrefix() + FormatUtil.format(message));
		}

		org.bukkit.potion.PotionType type = PotionType.toType(s);
		if (type != null)
		{
			Potion potion = new Potion(1);
			potion.setType(type);
			potion.setLevel(level);
			potion.setSplash(splash);

			InventoryHelper.addItem(pl, potion.toItemStack(amt));
		}
	}

	/**
	 * Basic killstreak system.
	 * <p>
	 * Can be overridden in certain circumstances
	 * <p>
	 * TODO: Rewrite this
	 * 
	 * @param ap
	 *            - {@link ArenaPlayer} to do killstreak for
	 */
	public void doKillStreak(ArenaPlayer ap)
	{
		/** Hunger Arena check **/
		if (ap.getArena().getType().equals("Hunger"))
			return;

		Player pl = ap.getPlayer();

		if (ap.getKillStreak() == 2)
			givePotion(pl, "strength", 1, 1, false, "&e2 &3kills! Unlocked strength potion!");

		if (ap.getKillStreak() == 4)
		{
			givePotion(pl, "heal", 1, 1, false, "&e4 &3kills! Unlocked health potion!");
			giveItem(pl, Material.GRILLED_PORK, 2, (short) 0, "&e4 &3kills! Unlocked Food!");
		}

		if (ap.getKillStreak() == 5)
		{
			if (! type.getName().equalsIgnoreCase("cq"))
			{
				ap.sendMessage("&e5 &3kills! Unlocked Zombies!");
				for (int i = 0; i < 4; i++)
					pl.getLocation().getWorld().spawnEntity(pl.getLocation(), EntityType.ZOMBIE);
			}
		}

		if (ap.getKillStreak() == 8)
		{
			ap.sendMessage("&e8 &3kills! Unlocked attackdogs!");
			for (int i = 0; i < 2; i++)
			{
				Wolf wolf = (Wolf) pl.getLocation().getWorld().spawnEntity(pl.getLocation(), EntityType.WOLF);
				wolf.setOwner(pl);
			}
		}

		if (ap.getKillStreak() == 12)
		{
			givePotion(pl, "regen", 1, 1, false, "&e12 &3kills! Unlocked regen potion!");
			giveItem(pl, Material.GRILLED_PORK, 2, (short) 0, "&e12 &3kills! Unlocked Food!");
		}
	}

	/**
	 * Disables this arena
	 */
	public final void onDisable()
	{
		tellPlayers("&cThis arena has been disabled!");

		this.gameTimer = -1;

		stop();

		this.disabled = true;
		this.gameMode = Mode.DISABLED;

		updateSigns();
	}

	/**
	 * Ends the arena
	 */
	public final void stop()
	{
		if (stopped)
			return; // No need to stop multiple times

		plugin.outConsole("Stopping arena {0}!", name);

		this.gameMode = Mode.STOPPING;
		this.stopped = true;

		updateSigns();

		onStop();

		announceWinner();
		
		for (ArenaPlayer ap : getActivePlayers())
		{
			endPlayer(ap, false);
		}

		active.clear();
		inactive.clear();
		
		plugin.getSpectatingHandler().unregisterArena(this);

		this.gameMode = Mode.IDLE;

		clearEntities();

		new ArenaFinalizeTask(this).runTaskLater(plugin, 120L);
	}

	/**
	 * Called when an arena is stopped
	 */
	public void onStop()
	{
	}

	/**
	 * Teleports a player to the most ideal location
	 * 
	 * @param p
	 *            - Player to teleport
	 * @param loc
	 *            - Raw location
	 */
	public final void teleport(Player p, Location loc)
	{
		p.teleport(loc.clone().add(0.5D, 1.0D, 0.5D));
	}

	/**
	 * Called when an arena is updated
	 */
	public void check()
	{
	}

	/**
	 * Ends an {@link ArenaPlayer}
	 * 
	 * @param ap
	 *            - {@link ArenaPlayer} to end
	 * @param dead
	 *            - Whether or not a player died
	 */
	public void endPlayer(ArenaPlayer ap, boolean dead)
	{
		plugin.debug("Ending Player: {0} Dead: {1}", ap.getName(), dead);

		ap.setOut(true);

		this.updatedTeams = true;

		returnXP(ap);

		ap.clearInventory();
		ap.returnInventory();

		ap.clearPotionEffects();

		teleport(ap.getPlayer(), ap.getSpawnBack());

		active.remove(ap);
		inactive.add(ap);

		if (dead)
		{
			ap.sendMessage("&3You have exceeded the death limit!");
			tellPlayers("&e{0} &3has been eliminated!", ap.getName());

			if (active.size() > 1)
			{
				tellPlayers("&3There are &e{0} &3players remaining!", active.size());
			}
		}
	}

	/**
	 * Called when an arena starts
	 */
	public void onStart()
	{
	}

	/**
	 * Called when an arena runs out of time
	 */
	public void onOutOfTime()
	{
	}

	/**
	 * Called right before an arena runs out of time
	 */
	public void onPreOutOfTime()
	{
	}

	/**
	 * Basic timer checker.
	 * <p>
	 * Should not be overriden.
	 */
	public final void checkTimers()
	{
		if (stopped)
			return;

		// Load config if not already loaded
		if (! configLoaded)
		{
			reloadConfig();
		}

		if (! pauseStartTimer)
		{
			startTimer--;
			broadcastTimer--;
		}

		if (startTimer <= 0)
		{
			start();
			gameTimer--;
		}
		else
		{
			if (broadcastTimer < 0)
			{
				broadcastTimer = 45;
				announce();
			}
		}

		// End the game
		if (gameTimer <= 0)
		{
			onPreOutOfTime();
			stop();
			onOutOfTime();
		}
	}

	/**
	 * Starts the arena. 
	 * <p>
	 * Should not be overriden.
	 */
	public final void start()
	{
		if (! start)
		{
			plugin.outConsole("Starting arena {0} with {1} players", name, active.size());

			this.start = true;
			this.gameMode = Mode.INGAME;

			this.startingAmount = active.size();

			this.gameTimer = maxGameTime;
			this.startTimer = -1;

			onStart();
			spawnAll();
		}
	}

	/**
	 * Arena Updater
	 */
	public final void update()
	{
		checkTimers();
		updateTeams();
		check();

		for (ArenaPlayer ap : getActivePlayers())
		{
			// Check players in the Arena
			if (isInLobby())
			{
				ap.decideHat();
			}

			ap.setHealTimer(ap.getHealTimer() - 1);

			ArenaClass ac = ap.getArenaClass();
			if (ac != null)
			{
				if (ac.getName().equalsIgnoreCase("healer") && ap.getHealTimer() <= 0)
				{
					if (ap.getPlayer().getHealth() + 1 <= 20)
					{
						if (ap.getPlayer().getHealth() < 0)
							ap.getPlayer().setHealth(1);
						ap.getPlayer().setHealth(ap.getPlayer().getHealth() + 1);
						ap.setHealTimer(2);
					}
				}

				// Class based potion effects
				if (ac.isHasPotionEffects())
				{
					if (ac.getPotionEffects().size() > 0)
					{
						for (PotionEffect effect : ac.getPotionEffects())
						{
							if (! ap.getPlayer().hasPotionEffect(effect.getType()))
								ap.getPlayer().addPotionEffect(effect);
						}
					}
				}
			}

			// Timer Stuff
			if (! pauseStartTimer)
			{
				if (startTimer == 120)
				{
					ap.sendMessage("&e120 &3seconds until start!");
				}
				if (startTimer == 60)
				{
					ap.sendMessage("&e60 &3seconds until start!");
				}
				if (startTimer == 45)
				{
					ap.sendMessage("&e45 &3seconds until start!");
				}
				if (startTimer == 30)
				{
					ap.sendMessage("&e30 &3seconds until start!");
				}
				if (startTimer == 15)
				{
					ap.sendMessage("&e15 &3seconds until start!");
				}
				if (startTimer > 0 && startTimer < 11)
				{
					ap.sendMessage("&e{0} &3second(s) until start!", startTimer);
				}
			}

			if (gameTimer > 0 && gameTimer < 21)
			{
				ap.sendMessage("&e{0} &3second(s) until end!", gameTimer);
			}
			if (gameTimer == 60 && maxGameTime > 60)
			{
				ap.sendMessage("&e{0} &3minute(s) until end!", gameTimer / 60);
			}
			if (gameTimer == maxGameTime / 2)
			{
				ap.sendMessage("&e{0} &3second(s) until end!", maxGameTime / 2);
			}

			// XP Bar
			decideXPBar(ap);

			// End dead players
			if (! stopped)
			{
				if (ap.getDeaths() >= getMaxDeaths())
				{
					if (ap.getPlayer().getHealth() > 0)
					{
						endPlayer(ap, true);
					}
				}
			}
		}

		if (active.size() <= 0)
			stop();

		// Update signs
		updateSigns();
	}

	/**
	 * Decides the timer xp bar for an {@link ArenaPlayer}
	 * 
	 * @param ap
	 *            - {@link ArenaPlayer} to decide xp bar for
	 */
	public final void decideXPBar(ArenaPlayer ap)
	{
		if (plugin.getConfig().getBoolean("timerXPBar", true))
		{
			if (isInGame())
			{
				ap.getPlayer().setLevel(gameTimer);
			}

			if (isInLobby())
			{
				ap.getPlayer().setLevel(startTimer);
			}
		}
	}

	/**
	 * Returns a player's xp when they leave the game
	 * 
	 * @param ap
	 *            - {@link ArenaPlayer} to return xp
	 */
	public final void returnXP(ArenaPlayer ap)
	{
		plugin.debug("Returning {0} levels of xp for {1}", ap.getBaseLevel(), ap.getName());

		// Clear XP
		ap.getPlayer().setExp(0.0F);
		ap.getPlayer().setLevel(0);

		// Give Base XP
		ap.getPlayer().setLevel(ap.getBaseLevel());
	}

	/**
	 * Forces the start of an arena
	 * 
	 * @param player
	 *            - {@link Player} forcing the start of the arena
	 */
	public final void forceStart(Player player)
	{
		if (isInGame())
		{
			player.sendMessage(plugin.getPrefix() + 
					FormatUtil.format("&cThis arena is already in progress!"));
			return;
		}

		plugin.outConsole("Forcefully starting arena {0}", name);

		start();

		gameTimer--;

		player.sendMessage(plugin.getPrefix() + 
				FormatUtil.format("&3You have forcefully started &e{0}&3!", name));
	}

	/**
	 * Clears the entities inside this arena
	 */
	public final void clearEntities()
	{
		plugin.debug("Clearing entities in arena {0}", name);
		
		if (plugin.isStopping())
		{
			new EntityClearTask(this).run();
		}
		else
		{
			new EntityClearTask(this).runTaskLater(plugin, 2L);
		}
	}

	/**
	 * Returns whether or not an arena is ingame
	 */
	public final boolean isInGame()
	{
		return startTimer < 1 && gameTimer > 0;
	}

	/**
	 * Returns whether or not an arena is in the lobby
	 */
	public final boolean isInLobby()
	{
		return startTimer > 1;
	}
	
	/**
	 * Returns a customized leaderboard for a {@link Player}
	 * <p>
	 * TODO: Store leaderboard entries then customize?
	 * 
	 * @param player - Player to get leaderboard for
	 */
	public List<String> getLeaderboard(Player player)
	{
		List<String> leaderboard = new ArrayList<String>();

		// Build kills map
		HashMap<String, Double> kdrMap = new HashMap<String, Double>();
		
		for (ArenaPlayer ap : active)
		{
			kdrMap.put(ap.getName(), ap.getKDR());
		}

		final List<Map.Entry<String, Double>> sortedEntries = new ArrayList<Map.Entry<String, Double>>(kdrMap.entrySet());
		Collections.sort(sortedEntries, new Comparator<Map.Entry<String, Double>>()
		{
			@Override
			public int compare(final Entry<String, Double> entry1, final Entry<String, Double> entry2)
			{
				return -entry1.getValue().compareTo(entry2.getValue());
			}
		});

		int pos = 1;
		for (Map.Entry<String, Double> entry : sortedEntries)
		{
			String string = entry.getKey();
			ArenaPlayer apl = plugin.getArenaPlayer(Util.matchPlayer(string));
			if (apl != null)
			{
				StringBuilder line = new StringBuilder();
				line.append(FormatUtil.format("&3#{0}. ", pos));
				line.append(FormatUtil.format(decideColor(apl)));
				line.append(FormatUtil.format(apl.getName().equals(player.getName()) ? "&l" : ""));
				line.append(FormatUtil.format(apl.getName() + "&r"));
				line.append(FormatUtil.format("  &3Kills: &e{0}", apl.getKills()));
				line.append(FormatUtil.format("  &3Deaths: &e{0}", apl.getDeaths()));
				line.append(FormatUtil.format("  &3KDR: &e{0}", entry.getValue()));
				leaderboard.add(line.toString());
				pos++;
			}
		}

		return leaderboard;
	}

	/**
	 * Decides a player's team color
	 * 
	 * @param pl - Player to decide team color for
	 */
	protected String decideColor(ArenaPlayer pl)
	{
		if (pl.getTeam() == 1)
		{
			return "&c";
		}
		else if (pl.getTeam() == 2)
		{
			return "&9";
		}
		else
		{
			return "&d";
		}
	}

	/**
	 * Updates the signs for the arena
	 */
	protected final void updateSigns()
	{
		plugin.getSignHandler().updateSigns(az);
	}

	/**
	 * Announces the winner of the arena
	 */
	protected void announceWinner()
	{
		if (winningTeam == 2)
		{
			tellAllPlayers("&eBlue &3team won!");
		}
		else if (winningTeam == 1)
		{
			tellAllPlayers("&eRed &3team won!");
		}
		else if (winningTeam == -1)
		{
			tellAllPlayers("&3Game ended in a tie!");
		}
	}

	/**
	 * Returns whether or not a class can be used in this arena
	 * 
	 * @param ac - Class to check
	 */
	public final boolean isValidClass(ArenaClass ac)
	{
		if (! whitelistedClasses.isEmpty())
		{
			return whitelistedClasses.contains(ac.getName());
		}
		
		if (! blacklistedClasses.isEmpty())
		{
			return ! blacklistedClasses.contains(ac.getName());
		}
		
		return true;
	}
	
	@Override
	public final void finalize()
	{
		try
		{
			super.finalize();
		}
		catch (Throwable e)
		{
			//
		}
	}

	/**
	 * Workaround for concurrency issues
	 * <p>
	 * Should not be used for removing or adding
	 */
	public final List<ArenaPlayer> getActivePlayers()
	{
		return Util.newList(active);
	}

	/**
	 * Workaround for concurrency issues
	 * <p>
	 * Should not be used for removing or adding
	 */
	public final List<ArenaPlayer> getInactivePlayers()
	{
		return Util.newList(inactive);
	}

	/**
	 * Returns the amount of players currently in the arena
	 */
	public final int getPlayerCount()
	{
		return active.size();
	}

	/**
	 * Updates teams
	 */
	private final void updateTeams()
	{
		this.team1size = 0;
		this.team2size = 0;

		for (ArenaPlayer ap : getActivePlayers())
		{
			if (ap.getTeam() == 1)
				team1size++;
			else
				team2size++;
		}
	}
}