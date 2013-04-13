// Bukkit Plugin "VirtualPack" by Siguza
// The license under which this software is released can be accessed at:
// http://creativecommons.org/licenses/by-nc-sa/3.0/

package net.drgnome.virtualpack.components;

import java.util.*;
import net.minecraft.server.v#MC_VERSION#.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.craftbukkit.v#MC_VERSION#.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.*;
import net.drgnome.virtualpack.VPack;
import net.drgnome.virtualpack.util.*;

// VirtualTileEntityFurnace is way too long, therefore VTE
public class VTEFurnace extends TileEntityFurnace
{
    // To access the chests
    private VPack vpack;
    private ItemStack[] contents = new ItemStack[3];
    public int link = 0;
    // For custom stuff
    private double burnSpeed = 2D;
    private double meltSpeed = 2D;
    // I'm internally using "myCookTime" to not lose any precision, but for displaying the progress I still have to use "cookTime"
    private double myCookTime = 0D;
    // Call me paranoid, but this has to be checked
    private int lastID = 0;
    // Increases performance (or should at least)
    private long lastCheck = 0L;
    
    // New VTE
    public VTEFurnace(VPack vpack)
    {
        this.vpack = vpack;
        cookTime = 0;
        burnTime = 0;
        ticksForCurrentFuel = 0;
        contents[1] = new ItemStack(Material.COAL, 1);
    }
    
    // Read from save
    public VTEFurnace(VPack vpack, String data[])
    {
      this.vpack = vpack;
      cookTime = 0;
      burnTime = 0;
      ticksForCurrentFuel = 0;
      contents[1] = new ItemStack(Material.COAL, 1);
    }
    
    public String[] save()
    {
//        ArrayList<String> list = new ArrayList<String>();
//        for(int i = 0; i < 3; i++)
//        {
//            list.add(Util.itemStackToString(contents[i]));
//        }
//        list.add(Integer.toString(burnTime));
//        list.add(Integer.toString(ticksForCurrentFuel));
//        list.add(Double.toString(myCookTime));
//        list.add(Integer.toString(link));
//        // I save this now, because you could lose burn speed if it's the last fuel item and the server gets restartet
//        list.add(Double.toString(burnSpeed));
//        return list.toArray(new String[0]);
    }
    
    // For compatibility
    public void #FIELD_TILEENTITY_1#() // Derpnote
    {
        tick(1);
    }
    
    public void tick(int ticks)
    {
        checkLink();
        int newID = contents[0] == null ? 0 : contents[0].id;
        // Has the item been changed?
        if(newID != lastID)
        {
            // Then reset the progress!
            myCookTime = 0.0D;
            lastID = newID;
            // And, most important: change the melt speed
            meltSpeed = getMeltSpeed(contents[0]);
        }
        // So, can we now finally burn?
        if(canBurn() && !isBurning() && (getFuelTime(contents[1]) > 0))
        {
            // I have no idea what "ticksForCurrentFuel" is good for, but it works fine like this
            burnTime = ticksForCurrentFuel = getFuelTime(contents[1]);
            // Before we remove the item: how fast does it burn?
            burnSpeed = getBurnSpeed(contents[1]);
            // If it's a container item (lava bucket), we only consume its contents (not like evil Notch!)
//            if(Item.byId[contents[1].id].#FIELD_ITEM_1#()) // Derpnote
//            {
//                contents[1] = new ItemStack(Item.byId[contents[1].id].#FIELD_ITEM_2#());  // Derpnote
//            }
//            // If it's not a container, consume it! Om nom nom nom!
//            else
//            {
//                contents[1].count--;
//                // Let 0 be null
//                if(contents[1].count <= 0)
//                {
//                    contents[1] = null;
//                }
//            }
        }
        // Now, burning?
        if(isBurning())
        {
            // Then move on
            burnTime -= ticks;
            // I'm using a double here because of the custom recipes.
            // The faster this fuel burns and the faster the recipe melts, the faster we're done
            myCookTime += burnSpeed * meltSpeed * ((double)ticks);
            // Finished burning?
            while(myCookTime >= 200.0D)
            {
                myCookTime -= 200.0D;
                burn();
            }
        }
        // If it's not burning, we reset the burning progress!
        else if(!canBurn())
        {
            myCookTime = 0.0D;
        }
        // And for the display (I'm using floor rather than round to not cause the client to do shit when we not really reached 200):
        cookTime = Util.floor(myCookTime);
    }
    
    protected void checkLink()
    {
        // If this furnace is linked, then we should see if there's a reason to interact
        if(isFine() || (link == 0) || (vpack == null) || (vpack.getInv(link) == null) || (lastCheck >= vpack.getInv(link).getLastUpdate()))
        {
            return;
        }
        VInv inv = vpack.getInv(link);
        // If we can't burn at the moment, we need different stuff
        if(!canBurn())
        {
            // Do we need a different ingredient?
            boolean get0 = false;
            // If there is none, then of course
            if(contents[0] == null)
            {
                get0 = true;
            }
            else
            {
                // Or if it can't be molten
                if(getBurnResult(contents[0]) == null)
                {
                    get0 = true;
                }
            }
            // So do we need a different ingredient?
            if(get0)
            {
                // Lets search for a meltable item
                ItemStack item;
                for(int i = 0; i < inv.getSize(); i++)
                {
                    item = inv.getItem(i);
                    if(getBurnResult(item) != null)
                    {
                        // We have to exchange the items, but we can't do it directly without messing everything up
                        item = Util.copy_old(item);
                        ItemStack item1 = Util.copy_old(contents[0]);
                        contents[0] = item;
                        inv.setItem(i, item1);
                        // And leave the loop
                        break;
                    }
                }
            }
            // Now, if there is any reason we can't burn, we're done and put the output item away (if there is any)
            if(!canBurn() && (contents[2] != null))
            {
                // Lets search for a place we can put our stuff
                ItemStack item;
                for(int i = 0; i < inv.getSize(); i++)
                {
                    item = inv.getItem(i);
                    // If there's no item: Lol, too easy ^^
                    if(item == null)
                    {
                        inv.setItem(i, Util.copy_old(contents[2]));
                        contents[2] = null;
                        // And we can leave the loop
                        break;
                    }
                    // If there an item, then the materials have to match
                    else if(contents[2].doMaterialsMatch(item))
                    {
                        // Put away as much as possible
                        int max = Util.min(contents[2].count, Util.min(Item.byId[item.id].getMaxStackSize(), getMaxStackSize()) - item.count);
                        item.count += max;
                        contents[2].count -= max;
                        // If we've put everything away
                        if(contents[2].count <= 0)
                        {
                            contents[2] = null;
                            // Then let's go away from here
                            break;
                        }
                    }
                }
            }
        }
        // Now, if we finally can burn, but we don't have fuel, then go and get some!
        if(canBurn() && !isBurning() && (getFuelTime(contents[1]) <= 0))
        {
            // Search for fuel
            ItemStack item;
            for(int i = 0; i < inv.getSize(); i++)
            {
                item = inv.getItem(i);
                // Is it fuel?
                if(getFuelTime(item) > 0)
                {
                    // Then take it!
                    item = Util.copy_old(item);
                    ItemStack item1 = Util.copy_old(contents[1]);
                    contents[1] = item;
                    inv.setItem(i, item1);
                    // And goodbye
                    break;
                }
            }
        }
        // If we couldn't do anything, don't check again until the chest contents are changed
        if(isFine())
        {
            lastCheck = 0;
        }
        else
        {
            lastCheck = inv.getLastUpdate();
        }
    }
    
    public boolean isFine()
    {
        return ((myCookTime > 0.0D) || (getFuelTime(contents[1]) > 0)) && canBurn();
    }
    
    // This needs a little addition
    public boolean isBurning()
    {
        return (burnTime > 0) && (burnSpeed > 0.0D) && canBurn();
    }
    
    private ItemStack getBurnResult(ItemStack item)
    {
        if(item == null)
        {
            return null;
        }
        int i = item.id;
        // CUSTOM RECIPE HERE
        return #FIELD_RECIPESFURNACE_1#.getInstance().getResult(i); // Derpnote
    }
    
    private double getMeltSpeed(ItemStack item)
    {
        if(item == null)
        {
            return 0.0D;
        }
        // CUSTOM RECIPE HERE
        return 1.0D;
    }
    
    private int getFuelTime(ItemStack item)
    {
        if(item == null)
        {
            return 0;
        }
        int i = item.id;
        // CUSTOM FUEL HERE
        // Lava should melt 128 items, not 100
        if(i == Item.LAVA_BUCKET.id)
        {
            return 25600;
        }
        else
        {
            return fuelTime(item);
        }
    }
    
    private double getBurnSpeed(ItemStack item)
    {
        if(item == null)
        {
            return 0.0D;
        }
        // CUSTOM FUEL HERE
        return 1.0D;
    }
    
    private boolean canBurn()
    {
        // No ingredient, no recipe
        if(contents[0] == null)
        {
            return false;
        }
        ItemStack itemstack = getBurnResult(contents[0]);
        // No recipe, no burning
        if(itemstack == null)
        {
            return false;
        }
        // Free space? Let's burn!
        else if(contents[2] == null)
        {
            return true;
        }
        // Materials don't match? Too bad.
        else if(!contents[2].doMaterialsMatch(itemstack))
        {
            return false;
        }
        // As long as there is space, we can burn
        else if((contents[2].count + itemstack.count <= getMaxStackSize()) && (contents[2].count + itemstack.count <= contents[2].getMaxStackSize()))
        {
            return true;
        }
        return false;
    }
    
    public void burn()
    {
        // Can't burn? Goodbye
        if(!canBurn())
        {
            return;
        }
        ItemStack itemstack = getBurnResult(contents[0]);
        // Nothing in there? Then put something there.
        if(contents[2] == null)
        {
            contents[2] = Util.copy_old(itemstack);
        }
        // Burn ahead
        else if(contents[2].doMaterialsMatch(itemstack))
        {
            contents[2].count += itemstack.count;
        }
        // And consume the ingredient item
        // Goddamn, you have container functions, use them! Notch!
        if(Item.byId[contents[0].id].#FIELD_ITEM_1#()) // Derpnote
        {
            contents[0] = new ItemStack(Item.byId[contents[0].id].#FIELD_ITEM_2#()); // Derpnote
        }
        else
        {
            contents[0].count--;
            // Let 0 be null
            if(contents[0].count <= 0)
            {
                contents[0] = null;
            }
        }
    }
    
    /***** The following methods are only here because they interact with the contents array, which is private *****/
    
    public ItemStack[] getContents()
    {
        return contents;
    }
    
    public int getSize()
    {
        return contents.length;
    }
    
    public ItemStack getItem(int i)
    {
        return contents[i];
    }
    
    public ItemStack splitStack(int i, int j)
    {
        if(contents[i] != null)
        {
            ItemStack itemstack;
            if(contents[i].count <= j)
            {
                itemstack = contents[i];
                contents[i] = null;
                return itemstack;
            }
            else
            {
                itemstack = contents[i].#FIELD_ITEMSTACK_3#(j); // Derpnote
                if(contents[i].count == 0)
                {
                    contents[i] = null;
                }
                return itemstack;
            }
        }
        else
        {
            return null;
        }
    }
    
    public ItemStack splitWithoutUpdate(int i)
    {
        if(contents[i] != null)
        {
            ItemStack itemstack = contents[i];
            contents[i] = null;
            return itemstack;
        }
        else
        {
            return null;
        }
    }
    
    public void setItem(int i, ItemStack itemstack)
    {
        contents[i] = itemstack;
        if(itemstack != null && itemstack.count > getMaxStackSize())
        {
            itemstack.count = getMaxStackSize();
        }
    }
    
    // Compatibility
    public InventoryHolder getOwner()
    {
        return null;
    }
    
    public boolean #FIELD_IINVENTORY_1#(EntityHuman entityhuman) // Derpnote
    {
        return true;
    }
    
    public void onOpen(CraftHumanEntity who)
    {
    }
    
    public void onClose(CraftHumanEntity who)
    {
    }
    
    public List<HumanEntity> getViewers()
    {
        return new ArrayList<HumanEntity>();
    }
}