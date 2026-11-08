package dev.phonemod.item;

import dev.phonemod.PhoneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PhoneItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PhoneMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PhoneMod.MOD_ID);

    public static final RegistryObject<Item> PHONE =
            ITEMS.register("phone", () -> new PhoneItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("phone", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + PhoneMod.MOD_ID))
                    .icon(() -> PHONE.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(PHONE.get()))
                    .build());

    private PhoneItems() {
    }
}
