package me.desht.sensibletoolbox;

/*
    This file is part of SensibleToolbox

    Foobar is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Foobar is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SensibleToolbox.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.comphenix.protocol.ProtocolLibrary;
import me.desht.dhutils.*;
import me.desht.dhutils.commands.CommandManager;
import me.desht.dhutils.nms.NMSHelper;
import me.desht.sensibletoolbox.blocks.BaseSTBBlock;
import me.desht.sensibletoolbox.commands.*;
import me.desht.sensibletoolbox.items.BagOfHolding;
import me.desht.sensibletoolbox.items.BaseSTBItem;
import me.desht.sensibletoolbox.listeners.FloodlightListener;
import me.desht.sensibletoolbox.listeners.*;
import me.desht.sensibletoolbox.storage.LocationManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class SensibleToolboxPlugin extends JavaPlugin implements ConfigurationListener {

	public static final UUID UNIQUE_ID = UUID.fromString("60884913-70bb-48b3-a81a-54952dec2e31");
	private static SensibleToolboxPlugin instance = null;
	private final CommandManager cmds = new CommandManager(this);
	private ConfigurationManager configManager;
	private boolean protocolLibEnabled = false;
	private boolean isNMSenabled = false;
	private SoundMufflerListener soundMufflerListener;
	private FloodlightListener floodlightListener;

	public static SensibleToolboxPlugin getInstance() {
		return instance;
	}

	public boolean isNMSenabled() {
		return isNMSenabled;
	}

	public SoundMufflerListener getSoundMufflerListener() {
		return soundMufflerListener;
	}

	public FloodlightListener getFloodlightListener() {
		return floodlightListener;
	}

	@Override
	public void onEnable() {
		instance = this;

		LogUtils.init(this);

		configManager = new ConfigurationManager(this, this);

		MiscUtil.init(this);
		MiscUtil.setColouredConsole(getConfig().getBoolean("coloured_console"));

		Debugger.getInstance().setPrefix("[STB] ");
		setupNMS();
		if (!isNMSenabled) {
			LogUtils.warning("Unable to initialize NMS abstraction API - looks like this version of CraftBukkit isn't supported.");
			LogUtils.warning("Sensible Toolbox will continue to run with reduced functionality:");
			LogUtils.warning("  Floodlight, Interdiction Lamp items disabled");
		}

		LogUtils.setLogLevel(getConfig().getString("log_level", "INFO"));

		setupProtocolLib();
		if (protocolLibEnabled) {
			ItemGlow.init(this);
		} else {
			LogUtils.warning("ProtocolLib not detected - some functionality is reduced:");
			LogUtils.warning("  No glowing items, reduced particle effects, Sound Muffler item disabled");
		}

		BaseSTBItem.registerItems(this);
		registerEventListeners();
		registerCommands();

		MessagePager.setPageCmd("/stb page [#|n|p]");
		MessagePager.setDefaultPageSize(getConfig().getInt("pager.lines", 0));

		LocationManager.getManager().load();

		BaseSTBItem.setupRecipes();
		BagOfHolding.createSaveDirectory(this);

		Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
			@Override
			public void run() {
				LocationManager.getManager().tick();
			}
		}, 1L, 1L);
	}

	private void setupNMS() {
		try {
			NMSHelper.init(this);
			isNMSenabled = true;
		} catch (Exception e) {
			e.printStackTrace();
			// do nothing
		}
	}

	private void registerEventListeners() {
		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvents(new GeneralListener(this), this);
		pm.registerEvents(new WorldListener(this), this);
		pm.registerEvents(new BagOfHoldingListener(this), this);
		pm.registerEvents(new CombineHoeListener(this), this);
		pm.registerEvents(new TrashCanListener(this), this);
		pm.registerEvents(new PaintCanListener(this), this);
		pm.registerEvents(new ElevatorListener(this), this);
		pm.registerEvents(new AnvilListener(this), this);
//		pm.registerEvents(new MachineListener(this), this);
		pm.registerEvents(new ItemFilterListener(this), this);
		pm.registerEvents(new ItemRouterListener(this), this);
		pm.registerEvents(new ItemRouterModuleListener(this), this);
		if (isProtocolLibEnabled()) {
			soundMufflerListener = new SoundMufflerListener(this);
			soundMufflerListener.start();
		}
		floodlightListener = new FloodlightListener(this);
		pm.registerEvents(floodlightListener, this);
	}

	public void onDisable() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getOpenInventory().getTopInventory().getHolder() instanceof BaseSTBBlock) {
				p.closeInventory();
			}
		}
		if (soundMufflerListener != null) {
			soundMufflerListener.clear();
		}
		LocationManager.getManager().save();

		Bukkit.getScheduler().cancelTasks(this);

		instance = null;
	}

	private void setupProtocolLib() {
		Plugin pLib = getServer().getPluginManager().getPlugin("ProtocolLib");
		if (pLib != null && pLib instanceof ProtocolLibrary && pLib.isEnabled()) {
			protocolLibEnabled = true;
			LogUtils.fine("Hooked ProtocolLib v" + pLib.getDescription().getVersion());
		}
	}

	public boolean isProtocolLibEnabled() {
		return protocolLibEnabled;
	}

	private void registerCommands() {
		cmds.registerCommand(new SaveCommand());
		cmds.registerCommand(new RenameCommand());
		cmds.registerCommand(new GiveCommand());
		cmds.registerCommand(new ShowCommand());
		cmds.registerCommand(new ChargeCommand());
		cmds.registerCommand(new GetcfgCommand());
		cmds.registerCommand(new SetcfgCommand());
		cmds.registerCommand(new DebugCommand());
		cmds.registerCommand(new ParticleCommand());
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		try {
			return cmds.dispatch(sender, command, label, args);
		} catch (DHUtilsException e) {
			MiscUtil.errorMessage(sender, e.getMessage());
			return true;
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		return cmds.onTabComplete(sender, command, label, args);
	}

	@Override
	public void onConfigurationValidate(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onConfigurationChanged(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	public ConfigurationManager getConfigManager() {
		return configManager;
	}

}
