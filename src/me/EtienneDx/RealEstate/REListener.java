package me.EtienneDx.RealEstate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.PluginManager;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

public class REListener implements Listener, CommandExecutor
{
	void registerEvents()
	{
		PluginManager pm = RealEstate.instance.getServer().getPluginManager();
		
		pm.registerEvents(this, RealEstate.instance);
		RealEstate.instance.getCommand("re").setExecutor(this);
	}
	
	@EventHandler
	public void onSignChange(SignChangeEvent event)
	{
		if(RealEstate.instance.dataStore.cfgSellKeywords.contains(event.getLine(0).toLowerCase()) || 
				RealEstate.instance.dataStore.cfgLeaseKeywords.contains(event.getLine(0).toLowerCase()) || 
				RealEstate.instance.dataStore.cfgRentKeywords.contains(event.getLine(0).toLowerCase()))
		{
			Player player = event.getPlayer();
			Location loc = event.getBlock().getLocation();
			
			Claim claim = GriefPrevention.instance.dataStore.getClaimAt(loc, false, null);
			if(claim == null)// must have something to sell
			{
				player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The sign you placed is not inside a claim!");
                event.setCancelled(true);
                event.getBlock().breakNaturally();
                return;
			}
			if(RealEstate.transactionsStore.anyTransaction(claim))
			{
				player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "This claim already has an ongoing transaction!");
                event.setCancelled(true);
                event.getBlock().breakNaturally();
                return;
			}
			if(RealEstate.transactionsStore.anyTransaction(claim.parent))
			{
                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The parent claim already has an ongoing transaction!");
                event.setCancelled(true);
                event.getBlock().breakNaturally();
                return;
			}
			for(Claim c : claim.children)
			{
				if(RealEstate.transactionsStore.anyTransaction(c))
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + 
	                		"A subclaim of this claim already has an ongoing transaction!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
			}
			
			// empty is considered a wish to sell
			if(RealEstate.instance.dataStore.cfgSellKeywords.contains(event.getLine(0).toLowerCase()))
			{
				if(!RealEstate.instance.dataStore.cfgEnableSell)
				{
					player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "Selling is disabled!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				String type = claim.parent == null ? "claim" : "subclaim";
				if(!RealEstate.perms.has(player, "realestate." + type + ".sell"))
				{
					player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You don't have the permission to sell " + type + "s!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}

				// check for a valid price
				double price;
				try
				{
					price = getDouble(event, 1, RealEstate.instance.dataStore.cfgPriceSellPerBlock * claim.getArea());
				}
				catch (NumberFormatException e)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The price you entered is not a valid number!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				if(price <= 0)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The price must be greater than 0!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				if(claim.isAdminClaim() && !RealEstate.perms.has(player, "realestate.admin"))// admin may sell admin claims
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You don't have the permission to sell admin claims!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				else if(type.equals("claim") && !player.getUniqueId().equals(claim.ownerID))// only the owner may sell his claim
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You can only sell claims you own!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				// we should be good to sell it now
				event.setCancelled(true);// need to cancel the event, so we can update the sign elsewhere
				RealEstate.transactionsStore.sell(claim, player, price, event.getBlock().getLocation());
			}
			else if(RealEstate.instance.dataStore.cfgRentKeywords.contains(event.getLine(0).toLowerCase()))// we want to rent it
			{
				if(!RealEstate.instance.dataStore.cfgEnableRent)
				{
					player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "Renting is disabled!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				String type = claim.parent == null ? "claim" : "subclaim";
				if(!RealEstate.perms.has(player, "realestate." + type + ".rent"))
				{
					player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You don't have the permission to rent " + type + "s!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}

				// check for a valid price
				double price;
				try
				{
					price = getDouble(event, 1, RealEstate.instance.dataStore.cfgPriceRentPerBlock * claim.getArea());
				}
				catch (NumberFormatException e)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The price you entered is not a valid number!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				if(price <= 0)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The price must be greater than 0!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				if(event.getLine(2).isEmpty())
				{
					event.setLine(2, RealEstate.instance.dataStore.cfgRentTime);
				}
				int duration = parseDuration(event.getLine(2));
				if(duration == 0)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "Couldn't read the date!\n" + 
	                		"Date must be formatted as follow" + ChatColor.GREEN + "10 weeks" + ChatColor.RED + " or " + 
	                		ChatColor.GREEN + "3 days" + ChatColor.RED + " or " +  ChatColor.GREEN + "1 week 3 days");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				if(claim.isAdminClaim() && !RealEstate.perms.has(player, "realestate.admin"))// admin may rent admin claims
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You don't have the permission to rent admin claims!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				else if(type.equals("claim") && !player.getUniqueId().equals(claim.ownerID))// only the owner may sell his claim
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You can only rent claims you own!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				// all should be good, we can create the rent
				event.setCancelled(true);
				RealEstate.transactionsStore.rent(claim, player, price, event.getBlock().getLocation(), duration);
			}
			else if(RealEstate.instance.dataStore.cfgLeaseKeywords.contains(event.getLine(0).toLowerCase()))// we want to rent it
			{
				if(!RealEstate.instance.dataStore.cfgEnableLease)
				{
					player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "Leasing is disabled!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				String type = claim.parent == null ? "claim" : "subclaim";
				if(!RealEstate.perms.has(player, "realestate." + type + ".lease"))
				{
					player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You don't have the permission to lease " + type + "s!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}

				// check for a valid price
				double price;
				try
				{
					price = getDouble(event, 1, RealEstate.instance.dataStore.cfgPriceLeasePerBlock * claim.getArea());
				}
				catch (NumberFormatException e)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The price you entered is not a valid number!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				if(price <= 0)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "The price must be greater than 0!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				if(event.getLine(2).isEmpty())
				{
					event.setLine(2, "" + RealEstate.instance.dataStore.cfgLeasePayments);
				}
				int paymentsCount;
				try
				{
					paymentsCount = Integer.parseInt(event.getLine(2));
				}
				catch(Exception e)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + 
	                		"The number of payments you enterred is not a valid number!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				if(event.getLine(3).isEmpty())
				{
					event.setLine(3, RealEstate.instance.dataStore.cfgLeaseTime);
				}
				int frequency = parseDuration(event.getLine(3));
				if(frequency == 0)
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "Couldn't read the date!\n" + 
	                		"Date must be formatted as follow" + ChatColor.GREEN + "10 weeks" + ChatColor.RED + " or " + 
	                		ChatColor.GREEN + "3 days" + ChatColor.RED + " or " +  ChatColor.GREEN + "1 week 3 days");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				if(claim.isAdminClaim() && !RealEstate.perms.has(player, "realestate.admin"))// admin may rent admin claims
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You don't have the permission to lease admin claims!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				else if(type.equals("claim") && !player.getUniqueId().equals(claim.ownerID))// only the owner may sell his claim
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You can only lease claims you own!");
	                event.setCancelled(true);
	                event.getBlock().breakNaturally();
	                return;
				}
				
				// all should be good, we can create the rent
				event.setCancelled(true);
				RealEstate.transactionsStore.lease(claim, player, price, event.getBlock().getLocation(), frequency, paymentsCount);
			}
		}
	}
	
	private int parseDuration(String line)
	{
		Pattern p = Pattern.compile("^(?:(?<weeks>\\d{1,2}) ?w(?:eeks?)?)? ?(?:(?<days>\\d{1,2}) ?d(?:ays?)?)?$", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(line);
		if(!line.isEmpty() && m.matches()) 
		{
			int ret = 0;
			if(m.group("weeks") != null)
				ret += 7 * Integer.parseInt(m.group("weeks"));
			if(m.group("days") != null)
				ret += Integer.parseInt(m.group("days"));
			return ret;
		}
		return 0;
	}

	private double getDouble(SignChangeEvent event, int line, double defaultValue) throws NumberFormatException
	{
		if(event.getLine(line).isEmpty())// if no price precised, make it the default one
		{
			event.setLine(line, Double.toString(defaultValue));
		}
		return Double.parseDouble(event.getLine(line));
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		if(event.getHand().equals(EquipmentSlot.HAND) && event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && event.getClickedBlock().getState() instanceof Sign)
		{
			Sign sign = (Sign)event.getClickedBlock().getState();
			if(ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(ChatColor.stripColor(RealEstate.instance.dataStore.cfgSignsHeader)))// it is a real estate sign
			{
				Player player = event.getPlayer();
				Claim claim = GriefPrevention.instance.dataStore.getClaimAt(event.getClickedBlock().getLocation(), false, null);
				
				if(!RealEstate.transactionsStore.anyTransaction(claim))
				{
	                player.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + 
	                		"This claim is no longer for rent or for sell, sorry...");
	                event.getClickedBlock().breakNaturally();
	                event.setCancelled(true);
	                return;
				}
				
				Transaction tr = RealEstate.transactionsStore.getTransaction(claim);
				if(player.isSneaking())
					tr.preview(player);
				else
					tr.interact(player);
			}
		}
	}
	
	@EventHandler
	public void onBreakBlock(BlockBreakEvent event)
	{
		if(event.getBlock().getState() instanceof Sign)
		{
			Claim claim = GriefPrevention.instance.dataStore.getClaimAt(event.getBlock().getLocation(), false, null);
			if(claim != null)
			{
				Transaction tr = RealEstate.transactionsStore.getTransaction(claim);
				if(tr != null && event.getBlock().equals(tr.getHolder()))
				{
					if(event.getPlayer() != null && !tr.getOwner().equals(event.getPlayer().getUniqueId()) && 
							!RealEstate.perms.has(event.getPlayer(), "realestate.destroysigns"))
					{
						event.getPlayer().sendMessage(RealEstate.instance.dataStore.chatPrefix + 
								ChatColor.RED + "Only the author of the sell/rent sign is allowed to destroy it");
						event.setCancelled(true);
						return;
					}
					// the sign has been destroy, we can try to cancel the transaction
					if(!tr.tryCancelTransaction(event.getPlayer()))
					{
						event.setCancelled(true);
					}
				}
			}
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if(args.length >= 1 && !args[0].equalsIgnoreCase("help"))
		{
			if(args[0].equalsIgnoreCase("info"))
			{
				if(sender.hasPermission("realestate.info"))
				{
					if(sender instanceof Player && RealEstate.transactionsStore.anyTransaction(
							GriefPrevention.instance.dataStore.getClaimAt(((Player)sender).getLocation(), false, null)))
					{
						Transaction tr = RealEstate.transactionsStore.getTransaction(
								GriefPrevention.instance.dataStore.getClaimAt(((Player)sender).getLocation(), false, null));
						tr.preview((Player)sender);
					}
					else
					{
						sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "No transaction found at your location!");
					}
				}
				else
				{
					sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You do not have the permission to view claim infos!");
				}
				return true;
			}
			else if(args[0].equalsIgnoreCase("renewRent"))
			{
				if(!RealEstate.instance.dataStore.cfgEnableAutoRenew)
				{
					sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "Automatic renew is disabled!");
					return true;
				}
				if(!(sender instanceof Player))
					return false;
				Claim claim = GriefPrevention.instance.dataStore.getClaimAt(((Player)sender).getLocation(), false, null);
				if(claim == null)
				{
					sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "You are not standing inside of a claim!");
					return true;
				}
				String claimType = claim.parent == null ? "claim" : "subclaim";
				Transaction tr = RealEstate.transactionsStore.getTransaction(claim);
				if(!(tr instanceof ClaimRent))
				{
					sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "This claim is not for rent!");
					return true;
				}
				ClaimRent cr = (ClaimRent)tr;
				if(!((Player)sender).getUniqueId().equals(cr.rentedBy) && !((Player)sender).getUniqueId().equals(cr.owner))
				{
					sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + 
							"You are not the person renting this " + claimType + "!");
					return true;
				}
				if(args.length == 1 || ((Player)sender).getUniqueId().equals(cr.owner))
				{
					sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.AQUA + "Automatic renew is currently " + 
							ChatColor.GREEN + (cr.autoRenew ? "enabled" : "disabled") + ChatColor.AQUA + " for this " + claimType + "!");
					return true;
				}
				else if(args.length > 2 || (!args[1].equalsIgnoreCase("enable") && !args[1].equalsIgnoreCase("disable")))
				{
					sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.RED + "Usage : /" + label + " renewRent [enable|disable]!");
					return true;
				}
				cr.autoRenew = args[1].equalsIgnoreCase("enable");
				RealEstate.transactionsStore.saveData();
				sender.sendMessage(RealEstate.instance.dataStore.chatPrefix + ChatColor.AQUA + "Automatic renew is now " + 
						ChatColor.GREEN + (cr.autoRenew ? "enabled" : "disabled") + ChatColor.AQUA + " for this " + claimType + "!");
				return true;
			}
		}
		else// plugin infos
		{
			String msg = ChatColor.BLUE + "---------= [" + ChatColor.GOLD + RealEstate.instance.getDescription().getName() + ChatColor.BLUE + "] =---------\n";

			msg += ChatColor.AQUA + "/" + label + ChatColor.LIGHT_PURPLE + " info" + ChatColor.AQUA + 
					" : Gets the informations about the transactions going on in the claim you're standing in.\n";
			if(sender.hasPermission("realestate.autorenew") && RealEstate.instance.dataStore.cfgEnableAutoRenew)
				msg += ChatColor.AQUA + "/" + label + ChatColor.LIGHT_PURPLE + " renewRent" + ChatColor.AQUA +
					" : Allow you to enable or disable the automatic renewal of rents\n";
			
			sender.sendMessage(msg);
			return true;
		}
		return false;
	}
}
