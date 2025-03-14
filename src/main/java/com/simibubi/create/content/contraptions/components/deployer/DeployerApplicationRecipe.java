package com.simibubi.create.content.contraptions.components.deployer;

import java.util.List;
import java.util.Set;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.compat.recipeViewerCommon.SequencedAssemblySubCategoryType;
import com.simibubi.create.content.contraptions.itemAssembly.IAssemblyRecipe;
import com.simibubi.create.content.contraptions.processing.ItemApplicationRecipe;
import com.simibubi.create.content.contraptions.processing.ProcessingRecipeBuilder;
import com.simibubi.create.content.contraptions.processing.ProcessingRecipeBuilder.ProcessingRecipeParams;
import com.simibubi.create.foundation.utility.Components;
import com.simibubi.create.foundation.utility.Lang;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;

public class DeployerApplicationRecipe extends ItemApplicationRecipe implements IAssemblyRecipe {

	public DeployerApplicationRecipe(ProcessingRecipeParams params) {
		super(AllRecipeTypes.DEPLOYING, params);
	}

	@Override
	protected int getMaxOutputCount() {
		return 2;
	}

	public static DeployerApplicationRecipe convert(Recipe<?> sandpaperRecipe) {
		return new ProcessingRecipeBuilder<>(DeployerApplicationRecipe::new,
			new ResourceLocation(sandpaperRecipe.getId()
				.getNamespace(),
				sandpaperRecipe.getId()
					.getPath() + "_using_deployer")).require(sandpaperRecipe.getIngredients()
						.get(0))
						.require(AllItemTags.SANDPAPER.tag)
						.output(sandpaperRecipe.getResultItem())
						.build();
	}

	@Override
	public void addAssemblyIngredients(List<Ingredient> list) {
		list.add(ingredients.get(1));
	}

	@Override
	@Environment(EnvType.CLIENT)
	public Component getDescriptionForAssembly() {
		ItemStack[] matchingStacks = ingredients.get(1)
			.getItems();
		if (matchingStacks.length == 0)
			return Components.literal("Invalid");
		return Lang.translateDirect("recipe.assembly.deploying_item",
			Components.translatable(matchingStacks[0].getDescriptionId()).getString());
	}

	@Override
	public void addRequiredMachines(Set<ItemLike> list) {
		list.add(AllBlocks.DEPLOYER.get());
	}

	@Override
	public SequencedAssemblySubCategoryType getJEISubCategory() {
		return SequencedAssemblySubCategoryType.DEPLOYING;
	}

}
