package net.preoccupied.bukkit.dispenser;


import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.CreatureType;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import net.preoccupied.bukkit.PluginConfiguration;


public class DispenserPlugin extends JavaPlugin {

    private Set<Block> flowingDispensers = null;

    private Material waterType = Material.WATER_BUCKET;
    private Material lavaType = Material.LAVA_BUCKET;

    private Material fireType = Material.FLINT_AND_STEEL;
    private Material lightningType = Material.GOLD_BOOTS;
    private Material slimeType = Material.SLIME_BALL;
    private Material boatType = Material.BOAT;
    private Material cartType = Material.MINECART;


    public void onLoad() {
	flowingDispensers = new HashSet<Block>();

	try {
	    Configuration conf = PluginConfiguration.load(this, this.getFile(), "config.yml");

	    // sustained effects
	    waterType = Material.getMaterial(conf.getInt("dispenser.water-type", waterType.getId()));
	    lavaType = Material.getMaterial(conf.getInt("dispenser.lava-type", lavaType.getId()));

	    // instant effects
	    fireType = Material.getMaterial(conf.getInt("dispenser.fire-type", fireType.getId()));
	    lightningType = Material.getMaterial(conf.getInt("dispenser.lightning-type", lightningType.getId()));
	    slimeType = Material.getMaterial(conf.getInt("dispenser.slime-type", slimeType.getId()));
	    boatType = Material.getMaterial(conf.getInt("dispenser.boat-type", boatType.getId()));
	    cartType = Material.getMaterial(conf.getInt("dispenser.minecart-type", cartType.getId()));

	} catch(IOException ioe) {
	    System.out.println(ioe);
	    ioe.printStackTrace();
	    return;
	}
    }


    public void onEnable() {
	PluginManager pm = getServer().getPluginManager();

	EventExecutor ee;
	ee = new EventExecutor() {
		public void execute(Listener ignored, Event e) {
		    onBlockRedstoneChange((BlockRedstoneEvent) e);
		}
	    };
	pm.registerEvent(Event.Type.REDSTONE_CHANGE, null, ee, Priority.Low, this);

	ee = new EventExecutor() {
		public void execute(Listener ignored, Event e) {
		    onBlockDispense((BlockDispenseEvent) e);
		}
	    };
	pm.registerEvent(Event.Type.BLOCK_DISPENSE, null, ee, Priority.Low, this);
    }


    public void onDisable() {
	;
    }


    private void onBlockRedstoneChange(BlockRedstoneEvent e) {
	if(e.getOldCurrent() > 0 && e.getNewCurrent() == 0) {
	    for(Block b : flowingDispensers) {
		if(! b.isBlockIndirectlyPowered()) {
		    Block t = getFacingBlock(b);
		    t.setTypeId(0, true);
		    flowingDispensers.remove(b);
		}
	    }
	}
    }


    private static boolean spewCheck(Material spewed, Material check) {
	return (check != Material.AIR && spewed == check);
    }


    private void onBlockDispense(BlockDispenseEvent e) {
	Material spewType = e.getItem().getType();

	if(spewCheck(spewType, slimeType)) {
	    if(spewSlime(e.getBlock())) {
		e.setItem(new ItemStack(Material.SNOW_BALL, 1));
	    }
	    return;
	    
	} else if(spewCheck(spewType, lightningType)) {
	    if(! spewLightning(e.getBlock()))
		return;

	} else if(spewCheck(spewType, fireType)) {
	    if(! spewFire(e.getBlock()))
		return;

	} else if(spewCheck(spewType, boatType)) {
	    if(spewBoat(e.getBlock())) {
		e.setItem(new ItemStack(Material.SNOW_BALL, 1));
	    }
	    return;

	} else if(spewCheck(spewType, cartType)) {
	    if(spewMinecart(e.getBlock())) {
		e.setItem(new ItemStack(Material.SNOW_BALL, 1));
	    }
	    return;

	} else if(spewCheck(spewType, waterType)) {
	    if(! spewWater(e.getBlock()))
		return;
	    
	} else if(spewCheck(spewType, lavaType)) {
	    if(! spewLava(e.getBlock()))
		return;
	    
	} else {
	    return;
	}
	
	e.setCancelled(true);
    }


    private static Block getFacingBlock(Block b, int count) {
	switch(b.getData()) {
	case 2:
	    return b.getFace(BlockFace.EAST, count);
	case 3:
	    return b.getFace(BlockFace.WEST, count);
	case 4:
	    return b.getFace(BlockFace.NORTH, count);
	case 5:
	    return b.getFace(BlockFace.SOUTH, count);
	default:
	    return b.getFace(BlockFace.UP, count);
	}
    }
    

    private static Block getFacingBlock(Block b) {
	return getFacingBlock(b, 1);
    }
    
    
    public boolean spewSlime(Block b) {
	b = getFacingBlock(b, 2);

	switch(b.getType()) {
	case AIR:
	case WATER:
	    b.getWorld().spawnCreature(b.getLocation(), CreatureType.SLIME);
	    return true;

	default:
	    return false;
	}
    }


    public boolean spewLightning(Block b) {
	b.getWorld().strikeLightning(b.getLocation());
	return true;
    }


    public boolean spewFire(Block b) {
	b = getFacingBlock(b);

	switch(b.getType()) {
	case AIR:
	    IgniteCause cause = BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL;
            BlockIgniteEvent event = new BlockIgniteEvent(b, cause, null);
            getServer().getPluginManager().callEvent(event);

	    if(! event.isCancelled()) {
		b.setType(Material.FIRE);
	    }

	case FIRE:
	    return true;

	default:
	    return false;
	}
    }


    public boolean spewBoat(Block b) {
	b = getFacingBlock(b);

	switch(b.getType()) {
	case AIR:
	case WATER:
	    b.getWorld().spawnBoat(b.getLocation());
	    return true;

	default:
	    return false;
	}
    }


    public boolean spewMinecart(Block b) {
	b = getFacingBlock(b);
	
	switch(b.getType()) {
	case RAILS:
	case POWERED_RAIL:
	case DETECTOR_RAIL:
	    b.getWorld().spawnMinecart(b.getLocation());
	    return true;

	default:
	    return false;
	}
    }


    public boolean spewWater(Block b) {
	Block t = getFacingBlock(b);

	if(t.getType() == Material.WATER) {
	    return true;

	} else if(t.getType() != Material.AIR) {
	    return false;
	}
	
	
	t.setTypeIdAndData(Material.WATER.getId(), (byte) 0, false);
	flowingDispensers.add(b);
	return true;
    }


    public boolean spewLava(Block b) {
	Block t = getFacingBlock(b);

	if(t.getType() == Material.LAVA) {
	    return true;

	} else if(t.getType() != Material.AIR) {
	    return false;
	}
	
	t.setTypeIdAndData(Material.LAVA.getId(), (byte) 0, false);
	flowingDispensers.add(b);
	return true;
    }
	
}


/* The end. */
