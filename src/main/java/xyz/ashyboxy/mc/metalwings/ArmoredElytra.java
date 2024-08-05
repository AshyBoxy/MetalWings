package xyz.ashyboxy.mc.metalwings;

import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class ArmoredElytra {
    public static final ResourceLocation ELYTRA_DATA = MetalWings.id("elytra");
    public static final ResourceLocation CHESTPLATE_DATA = MetalWings.id("chestplate");

    public static ItemStack createChestplateElytra(ItemStack chestplate, ItemStack elytra, LocalIntRef cost, MinecraftServer server) {
        if (!(chestplate.is(ItemTags.CHEST_ARMOR) && chestplate.getItem() instanceof ArmorItem && elytra.is(Items.ELYTRA)))
            return chestplate;

        // switcheroo - chestplate + elytra is the logical order,
        // but vanilla needs it to actually be an elytra for flight to work
        ItemStack output = elytra.copy();
        // item component types are server and client synced
        // (it gets angy if the server has component types the client doesn't)
        switch (WorldConfig.getConfig(server).storageMode) {
            case BUNDLE_CONTENTS: {
                List<ItemStack> bundleContents = new ArrayList<>();
                bundleContents.add(chestplate);
                bundleContents.add(elytra);
                output.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(bundleContents));
                break;
            }
            case CUSTOM_DATA: {
                CompoundTag customData = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

                customData.put(ELYTRA_DATA.toString(),
                        ItemStack.SINGLE_ITEM_CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                                elytra).getOrThrow());
                customData.put(CHESTPLATE_DATA.toString(),
                        ItemStack.SINGLE_ITEM_CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                                chestplate).getOrThrow());

                output.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
                break;
            }
            default: {
                throw new RuntimeException("Unknown storage mode: " + WorldConfig.getConfig(server).storageMode);
            }
        }

        List<Component> lore = new ArrayList<>(elytra.getOrDefault(DataComponents.LORE, new ItemLore(Collections.emptyList())).lines());

        MutableComponent chestplateName = chestplate.getHoverName().copy();

        if (chestplate.has(DataComponents.CUSTOM_NAME)) chestplateName.withStyle(ChatFormatting.ITALIC);
        lore.add(Component.translatableWithFallback(MetalWings.MOD_ID + ".elytra.chestplate", "+ %s",
                chestplateName).setStyle(Style.EMPTY.withItalic(false).applyFormat(ChatFormatting.GOLD)));

        if (chestplate.has(DataComponents.TRIM))
            chestplate.get(DataComponents.TRIM).addToTooltip(null,
                    a -> lore.add(a.copy().withStyle(a.getStyle().withItalic(false))),
                    null);
        lore.addAll(chestplate.getOrDefault(DataComponents.LORE, new ItemLore(Collections.emptyList())).lines());

        output.set(DataComponents.LORE, new ItemLore(lore));
        cost.set(cost.get() + 1);

        output.set(DataComponents.ATTRIBUTE_MODIFIERS, mergeAttributeModifiers(chestplate, elytra));

        return output;
    }

    public static ItemAttributeModifiers mergeAttributeModifiers(ItemStack... itemStacks) {
        // vanilla's behaviour is for custom attributes to completely replace the default ones
        // this allows later item's default attributes to replace earlier's custom ones, but from what i remember
        // vanilla doesn't do anything like this, so i'm gonna say it's fine
        LinkedHashMap<Attribute, ItemAttributeModifiers.Entry> attributes = new LinkedHashMap<>();
        for (ItemStack itemStack : itemStacks) {
            ItemAttributeModifiers attributeModifiers = itemStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (attributeModifiers == null) continue;
            if (attributeModifiers.modifiers().isEmpty())
                attributeModifiers = itemStack.getItem().getDefaultAttributeModifiers();
            attributeModifiers.modifiers().forEach(a -> attributes.put(a.attribute().value(), a));
        }

        return new ItemAttributeModifiers(attributes.values().stream().toList(), true);
    }
}
