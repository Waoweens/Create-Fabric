package com.simibubi.create.content.curiosities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.curiosities.tools.BlueprintEntity.BlueprintCraftingInventory;
import com.simibubi.create.content.curiosities.tools.BlueprintEntity.BlueprintSection;
import com.simibubi.create.content.logistics.item.filter.AttributeFilterContainer.WhitelistMode;
import com.simibubi.create.content.logistics.item.filter.FilterItem;
import com.simibubi.create.content.logistics.item.filter.ItemAttribute;
import com.simibubi.create.content.logistics.trains.track.TrackPlacement.PlacementInfo;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.element.GuiGameElement;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import com.simibubi.create.foundation.utility.Pair;

import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import io.github.fabricators_of_create.porting_lib.transfer.item.ItemHandlerHelper;
import io.github.fabricators_of_create.porting_lib.transfer.item.ItemStackHandler;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ResourceAmount;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class BlueprintOverlayRenderer {

	static boolean active;
	static boolean empty;
	static boolean noOutput;
	static boolean lastSneakState;
	static BlueprintSection lastTargetedSection;

	static Map<ItemStack, ItemStack[]> cachedRenderedFilters = new IdentityHashMap<>();
	static List<Pair<ItemStack, Boolean>> ingredients = new ArrayList<>();
	static ItemStack result = ItemStack.EMPTY;
	static boolean resultCraftable = false;

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();

		BlueprintSection last = lastTargetedSection;
		lastTargetedSection = null;
		active = false;
		noOutput = false;

		if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR)
			return;

		HitResult mouseOver = mc.hitResult;
		if (mouseOver == null)
			return;
		if (mouseOver.getType() != Type.ENTITY)
			return;

		EntityHitResult entityRay = (EntityHitResult) mouseOver;
		if (!(entityRay.getEntity() instanceof BlueprintEntity))
			return;

		BlueprintEntity blueprintEntity = (BlueprintEntity) entityRay.getEntity();
		BlueprintSection sectionAt = blueprintEntity.getSectionAt(entityRay.getLocation()
			.subtract(blueprintEntity.position()));

		lastTargetedSection = last;
		active = true;

		boolean sneak = mc.player.isShiftKeyDown();
		if (sectionAt != lastTargetedSection || AnimationTickHolder.getTicks() % 10 == 0 || lastSneakState != sneak)
			rebuild(sectionAt, sneak);

		lastTargetedSection = sectionAt;
		lastSneakState = sneak;
	}

	public static void displayTrackRequirements(PlacementInfo info, ItemStack pavementItem) {
		if (active)
			return;

		active = true;
		empty = false;
		noOutput = true;
		ingredients.clear();

		int tracks = info.requiredTracks;
		while (tracks > 0) {
			ingredients.add(Pair.of(AllBlocks.TRACK.asStack(Math.min(64, tracks)), info.hasRequiredTracks));
			tracks -= 64;
		}

		int pavement = info.requiredPavement;
		while (pavement > 0) {
			ingredients.add(Pair.of(ItemHandlerHelper.copyStackWithSize(pavementItem, Math.min(64, pavement)),
				info.hasRequiredPavement));
			pavement -= 64;
		}
	}

	public static void rebuild(BlueprintSection sectionAt, boolean sneak) {
		cachedRenderedFilters.clear();
		ItemStackHandler items = sectionAt.getItems();
		boolean empty = true;
		for (int i = 0; i < 9; i++) {
			if (!items.getStackInSlot(i)
				.isEmpty()) {
				empty = false;
				break;
			}
		}

		BlueprintOverlayRenderer.empty = empty;
		BlueprintOverlayRenderer.result = ItemStack.EMPTY;

		if (empty)
			return;

		boolean firstPass = true;
		boolean success = true;
		Minecraft mc = Minecraft.getInstance();
		PlayerInventoryStorage playerInv = PlayerInventoryStorage.of(mc.player);

		int amountCrafted = 0;
		Optional<CraftingRecipe> recipe = Optional.empty();
		Map<Integer, ItemStack> craftingGrid = new HashMap<>();
		ingredients.clear();
		ItemStackHandler missingItems = new ItemStackHandler(64);
		ItemStackHandler availableItems = new ItemStackHandler(64);
		List<ItemStack> newlyAdded = new ArrayList<>();
		List<ItemStack> newlyMissing = new ArrayList<>();
		boolean invalid = false;

		try (Transaction t = TransferUtil.getTransaction()) {
			do {
				craftingGrid.clear();
				newlyAdded.clear();
				newlyMissing.clear();

				Search:
				for (int i = 0; i < 9; i++) {
					ItemStack requestedItem = items.getStackInSlot(i);
					if (requestedItem.isEmpty()) {
						craftingGrid.put(i, ItemStack.EMPTY);
						continue;
					}

					ResourceAmount<ItemVariant> resource = StorageUtil.findExtractableContent(
							playerInv, v -> FilterItem.test(mc.level, v.toStack(), requestedItem), t);
					if (resource != null) {
						ItemStack currentItem = resource.resource().toStack(1);
						craftingGrid.put(i, currentItem);
						newlyAdded.add(currentItem);
						continue Search;
					}

					success = false;
					newlyMissing.add(requestedItem);
				}

				if (success) {
					CraftingContainer craftingInventory = new BlueprintCraftingInventory(craftingGrid);
					if (!recipe.isPresent())
						recipe = mc.level.getRecipeManager()
								.getRecipeFor(RecipeType.CRAFTING, craftingInventory, mc.level);
					ItemStack resultFromRecipe = recipe.filter(r -> r.matches(craftingInventory, mc.level))
							.map(r -> r.assemble(craftingInventory))
							.orElse(ItemStack.EMPTY);

					if (resultFromRecipe.isEmpty()) {
						if (!recipe.isPresent())
							invalid = true;
						success = false;
					} else if (resultFromRecipe.getCount() + amountCrafted > 64) {
						success = false;
					} else {
						amountCrafted += resultFromRecipe.getCount();
						if (result.isEmpty())
							result = resultFromRecipe.copy();
						else
							result.grow(resultFromRecipe.getCount());
						resultCraftable = true;
						firstPass = false;
					}
				}

				if (success || firstPass) {
					newlyAdded.forEach(s -> availableItems.insert(ItemVariant.of(s), s.getCount(), t));
					newlyMissing.forEach(s -> missingItems.insert(ItemVariant.of(s), s.getCount(), t));
				}

				if (!success) {
					if (firstPass) {
						result = invalid ? ItemStack.EMPTY : items.getStackInSlot(9);
						resultCraftable = false;
					}
					break;
				}

				if (!sneak)
					break;

			} while (success);
			t.commit();
		}

		for (int i = 0; i < 9; i++) {
			ItemStack available = availableItems.getStackInSlot(i);
			if (available.isEmpty())
				continue;
			ingredients.add(Pair.of(available, true));
		}
		for (int i = 0; i < 9; i++) {
			ItemStack missing = missingItems.getStackInSlot(i);
			if (missing.isEmpty())
				continue;
			ingredients.add(Pair.of(missing, false));
		}
	}

	public static void renderOverlay(PoseStack poseStack, float partialTicks, Window window) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui)
			return;
		if (!active || empty)
			return;

		int w = 21 * ingredients.size();

		if (!noOutput)
			w += 51;

		int x = (window.getGuiScaledWidth() - w) / 2;
		int y = (int) (window.getGuiScaledHeight() - 100);

		for (Pair<ItemStack, Boolean> pair : ingredients) {
			RenderSystem.enableBlend();
			(pair.getSecond() ? AllGuiTextures.HOTSLOT_ACTIVE : AllGuiTextures.HOTSLOT).render(poseStack, x, y);
			ItemStack itemStack = pair.getFirst();
			String count = pair.getSecond() ? null : ChatFormatting.GOLD.toString() + itemStack.getCount();
			drawItemStack(poseStack, mc, x, y, itemStack, count);
			x += 21;
		}

		if (noOutput)
			return;

		x += 5;
		RenderSystem.enableBlend();
		AllGuiTextures.HOTSLOT_ARROW.render(poseStack, x, y + 4);
		x += 25;

		if (result.isEmpty()) {
			AllGuiTextures.HOTSLOT.render(poseStack, x, y);
			GuiGameElement.of(Items.BARRIER)
				.at(x + 3, y + 3)
				.render(poseStack);
		} else {
			(resultCraftable ? AllGuiTextures.HOTSLOT_SUPER_ACTIVE : AllGuiTextures.HOTSLOT).render(poseStack,
				resultCraftable ? x - 1 : x, resultCraftable ? y - 1 : y);
			drawItemStack(poseStack, mc, x, y, result, null);
		}
		RenderSystem.disableBlend();
	}

	public static void drawItemStack(PoseStack ms, Minecraft mc, int x, int y, ItemStack itemStack, String count) {
		if (itemStack.getItem() instanceof FilterItem) {
			int step = AnimationTickHolder.getTicks(mc.level) / 10;
			ItemStack[] itemsMatchingFilter = getItemsMatchingFilter(itemStack);
			if (itemsMatchingFilter.length > 0)
				itemStack = itemsMatchingFilter[step % itemsMatchingFilter.length];
		}

		GuiGameElement.of(itemStack)
			.at(x + 3, y + 3)
			.render(ms);
		mc.getItemRenderer()
			.renderGuiItemDecorations(mc.font, itemStack, x + 3, y + 3, count);
	}

	private static ItemStack[] getItemsMatchingFilter(ItemStack filter) {
		return cachedRenderedFilters.computeIfAbsent(filter, itemStack -> {
			CompoundTag tag = itemStack.getOrCreateTag();

			if (AllItems.FILTER.isIn(itemStack) && !tag.getBoolean("Blacklist")) {
				ItemStackHandler filterItems = FilterItem.getFilterItems(itemStack);
				List<ItemStack> list = new ArrayList<>();
				for (int slot = 0; slot < filterItems.getSlots(); slot++) {
					ItemStack stackInSlot = filterItems.getStackInSlot(slot);
					if (!stackInSlot.isEmpty())
						list.add(stackInSlot);
				}
				return list.toArray(new ItemStack[list.size()]);
			}

			if (AllItems.ATTRIBUTE_FILTER.isIn(itemStack)) {
				WhitelistMode whitelistMode = WhitelistMode.values()[tag.getInt("WhitelistMode")];
				ListTag attributes = tag.getList("MatchedAttributes", net.minecraft.nbt.Tag.TAG_COMPOUND);
				if (whitelistMode == WhitelistMode.WHITELIST_DISJ && attributes.size() == 1) {
					ItemAttribute fromNBT = ItemAttribute.fromNBT((CompoundTag) attributes.get(0));
					if (fromNBT instanceof ItemAttribute.InTag inTag) {
						if (Registry.ITEM.isKnownTagName(inTag.tag)) {
							List<ItemStack> stacks = new ArrayList<>();
							for (Holder<Item> holder : Registry.ITEM.getTagOrEmpty(inTag.tag)) {
								stacks.add(new ItemStack(holder.value()));
							}
							return stacks.toArray(ItemStack[]::new);
						}
					}
				}
			}

			return new ItemStack[0];
		});
	}

}
