package net.preoccupied.bukkit.dispenser;


import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Boat;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Minecart;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.material.Directional;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import net.preoccupied.bukkit.PluginConfiguration;


/**
   Improved Dispensers plugin for Bukkit.

   @author Christopher O'Brien <cobrien@gmail.com>
 */
public class DispenserPlugin extends JavaPlugin {


    private Map<Block,Block> flowingDispensers = null;


    private Material waterType = Material.WATER_BUCKET;
    private Material lavaType = Material.LAVA_BUCKET;

    private Material fireType = Material.FLINT_AND_STEEL;
    private Material lightningType = Material.GOLD_BOOTS;
    private Material slimeType = Material.SLIME_BALL;
    private Material boatType = Material.BOAT;
    private Material cartType = Material.MINECART;


    private boolean powerTest = false;


    public void onLoad() {
	flowingDispensers = new HashMap<Block,Block>();

	// if you're not using the bukkit-utils module, you can
	// comment the rest of this method out, but you won't be able
	// to override the default item types via config.yml

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

	ee = new EventExecutor() {
		public void execute(Listener ignored, Event e) {
		    onBlockCollide((VehicleBlockCollisionEvent) e);
		}
	    };
	pm.registerEvent(Event.Type.VEHICLE_COLLISION_BLOCK, null, ee, Priority.Low, this);
    }



    public void onDisable() {
	for(Block flow : flowingDispensers.values()) {
	    flow.setTypeId(0, true);
	}
	flowingDispensers.clear();
    }



    private synchronized void finishPowerTest() {
	powerTest = false;
    }



    private synchronized void schedulePowerTest() {
	powerTest = true;

	Runnable task = new Runnable() {
		public void run() {
		    Set<Map.Entry<Block,Block>> entries = flowingDispensers.entrySet();
		    for(Iterator<Map.Entry<Block,Block>> i = entries.iterator(); i.hasNext(); ){ 
			Map.Entry<Block,Block> entry = i.next();
			Block dispenser = entry.getKey();
			if(! dispenser.isBlockIndirectlyPowered()) {
			    Block flow = entry.getValue();
			    flow.setData((byte) 2, true);
			    i.remove();
			}
		    }
		    finishPowerTest();
		}
	    };
	
	getServer().getScheduler().scheduleSyncDelayedTask(this, task);
    }



    private void onBlockRedstoneChange(BlockRedstoneEvent e) {
	// if the change is from powered to unpowered
	if((! powerTest) && (e.getOldCurrent() > 0 && e.getNewCurrent() == 0)) {
	    schedulePowerTest();
	}
    }



    /**
       check that the spew type is enabled (by not being AIR) and
       matches the material being spewed
     */
    private static final boolean spewCheck(Material spewed, Material check) {
	return (check != Material.AIR && spewed == check);
    }



    private void onBlockDispense(BlockDispenseEvent e) {
	if(e.isCancelled())
	    return;

	Dispenser d = (Dispenser) e.getBlock().getState();
	Material spewType = e.getItem().getType();
	boolean cancel = true;

	if(spewCheck(spewType, slimeType)) {
	    spewSlimeFrom(d);
	    
	} else if(spewCheck(spewType, lightningType)) {
	    spewLightningFrom(d);

	} else if(spewCheck(spewType, fireType)) {
	    spewFireFrom(d);

	} else if(spewCheck(spewType, boatType)) {
	    spewBoatFrom(d);

	} else if(spewCheck(spewType, cartType)) {
	    spewMinecartFrom(d);

	} else if(spewCheck(spewType, waterType)) {
	    spewWaterFrom(d);
	    
	} else if(spewCheck(spewType, lavaType)) {
	    spewLavaFrom(d);

	} else {
	    cancel = false;
	}

	e.setCancelled(cancel);
    }



    /**
       If a vehicle collides with a dispenser, put the vehicle in
       the dispenser
    */
    private void onBlockCollide(VehicleBlockCollisionEvent e) {
	
	Block b = e.getBlock();
	
	if (b.getType() != Material.DISPENSER) {
	    
	    // The collision detection often (always?) picks the wrong block,
	    // choosing the block beneath the one we visibly collide with.
	    // So, check the block above.
	    b = b.getWorld().getBlockAt(b.getX(), b.getY() + 1, b.getZ());
	    if (b.getType() != Material.DISPENSER) return;
	}
	
	Dispenser d;
	try {
	    d = (Dispenser) b.getState();
	} catch (ClassCastException ex) {
	    return;
	}
	
	Vehicle v = e.getVehicle();
	Material m;
	
	if (v instanceof Minecart) m = Material.MINECART;
	else if (v instanceof Boat) m = Material.BOAT;
	else return;
	
	safeAddInventory(d, m);
	safeRemoveVehicle(e.getVehicle());
    }



    /**
       Schedules the block change to happen at the next free tick.
    */
    private void safeSetBlockType(final Block block, final Material material) {
	Runnable task = new Runnable() {
		public void run() {
		    block.setType(material);
		    block.setData((byte) 0, true);
		}
	    };
	
	getServer().getScheduler().scheduleSyncDelayedTask(this, task);
    }
    


    /**
       Schedules an inventory depletion by one of the given material type.
    */
    private void safeConsumeInventory(final Dispenser d, final Material mat) {
	Runnable task = new Runnable() {
		public void run(){ 
		    Inventory inv = d.getInventory();
		    int index = inv.first(mat);
		    if(index >= 0) {
			ItemStack stack = inv.getItem(index);
			int count = stack.getAmount() - 1;
			if(count < 1) {
			    inv.setItem(index, null);
			} else {
			    stack.setAmount(count);
			    inv.setItem(index, stack);
			}
		    }
		}
	    };
	
	getServer().getScheduler().scheduleSyncDelayedTask(this, task);
    }



    /**
       Schedules an inventory increment of the given material type.
    */
    private void safeAddInventory(final Dispenser d, final Material mat) {
	Runnable task = new Runnable() {
		public void run(){ 
		    Inventory inv = d.getInventory();
		    int index = inv.first(mat);
		    
		    // We may be the first object of this type in the
		    // dispenser
		    if (index == -1) {
			index = inv.firstEmpty();
			
			// If we have no room, return. We'll make this more
			// graceful later. At *least* give the user a message
			// about losing the minecart forever :)
			if (index == -1) return;
			
			inv.setItem(index, new ItemStack(mat, 1));
		    }
		    else {
			ItemStack stack = inv.getItem(index);
			stack.setAmount(stack.getAmount() + 1);
		    }
		}
	    };
	
	getServer().getScheduler().scheduleSyncDelayedTask(this, task);
    }



    /**
       Schedules the vehicle to eject the player and disappear
     */
    private void safeRemoveVehicle(final Vehicle v) {
	Runnable task = new Runnable() {
		public void run(){ 
		    v.eject();
		    v.remove();
		}
	    };
	
	getServer().getScheduler().scheduleSyncDelayedTask(this, task);
    }



    /**
       Determine the Block that is count away from the face of the
       Dispenser b.
     */
    private static final Block getFacingBlock(Dispenser dispenser, int count) {
	Directional dir = (Directional) dispenser.getData();
	return dispenser.getBlock().getFace(dir.getFacing(), count);
    }
    

    
    /**
       Spawns a slime creature
    */
    private final void spewSlimeFrom(Dispenser d) {
	Block t = getFacingBlock(d, 2);

	if(isMaterialOpen(t.getType())) {
	    t.getWorld().spawnCreature(t.getLocation(), CreatureType.SLIME);
	    safeConsumeInventory(d, slimeType);

	} else {
	    System.out.println("Cannot spawn slime in " + t.getType());
	}
    }
    

    
    /**
       Causes a lightning strike
    */
    private static final void spewLightningFrom(Dispenser d) {
	Block b = d.getBlock();
	b.getWorld().strikeLightning(b.getLocation());
    }



    /**
       Lights a fire. Uses the BlockIgniteEvent check to make sure
       fire is permitted.
    */
    private final void spewFireFrom(Dispenser d) {
	Block t = getFacingBlock(d, 1);
	
	if(isMaterialOpen(t.getType())) {
	    IgniteCause cause = BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL;
	    BlockIgniteEvent event = new BlockIgniteEvent(t, cause, null);
	    Bukkit.getServer().getPluginManager().callEvent(event);
	
	    if(! event.isCancelled()) {
		safeSetBlockType(t, Material.FIRE);
	    }

	} else {
	    System.out.println("Cannot set fire to " + t.getType());
	}
    }



    /**
       Spawns a boat entity
     */
    private final void spewBoatFrom(Dispenser d) {
	Block t0 = getFacingBlock(d, 1);
	Block t1 = getFacingBlock(d, 2);

	if(isMaterialOpen(t0.getType()) && isMaterialOpen(t1.getType())) {
	    t1.getWorld().spawn(t1.getLocation(), Boat.class);
	    safeConsumeInventory(d, boatType);

	} else {
	    System.out.println("Cannot spew a boat into " + t1.getType());
	}
    }



    /**
       Spawns a minecart entity
     */
    private final void spewMinecartFrom(Dispenser d) {
	Block t0 = getFacingBlock(d, 1);
	Block t1 = getFacingBlock(d, 2);

	if(isMaterialOpen(t0.getType()) && isMaterialOpen(t1.getType())) {
	    t1.getWorld().spawn(t1.getLocation(), Minecart.class);
	    safeConsumeInventory(d, cartType);

	} else {
	    System.out.println("Cannot spew a minecart into " + t1.getType());
	}
    }



    /**
       Starts a water flow
     */
    private final void spewWaterFrom(Dispenser d) {
	Block t = getFacingBlock(d, 1);

	if(isMaterialOpen(t.getType())) {
	    safeSetBlockType(t, Material.WATER);
	    flowingDispensers.put(d.getBlock(), t);

	} else {
	    System.out.println("Cannot spew water into " + t.getType());
	}
    }



    /**
       Starts a lava flow
    */
    private final void spewLavaFrom(Dispenser d) {
	Block t = getFacingBlock(d, 1);

	if(isMaterialOpen(t.getType())) {
	    safeSetBlockType(t, Material.LAVA);
	    flowingDispensers.put(d.getBlock(), t);
	    
	} else {
	    System.out.println("Cannot spew lava into " + t.getType());
	}
    }

    
    
    /**
       Check if the material is considered "open" such that you could
       reasonably expect something to spawn there. Non obtrusive blocks.
     */
    private static final boolean isMaterialOpen(Material m) {
	switch(m) {
	case AIR:
	case BROWN_MUSHROOM:
	case CAKE:
	case DETECTOR_RAIL:
	case FIRE:
	case GRASS:
	case LAVA:
	case POWERED_RAIL:
	case RAILS:
	case RED_MUSHROOM:
	case RED_ROSE:
	case REDSTONE_WIRE:
	case REDSTONE_TORCH_OFF:
	case REDSTONE_TORCH_ON:
	case SNOW:
	case STATIONARY_LAVA:
	case STATIONARY_WATER:
	case TORCH:
	case WATER:
	case YELLOW_FLOWER:
	    return true;
	    
	default:
	    return false;
	}
    }

}


/* The end. */
