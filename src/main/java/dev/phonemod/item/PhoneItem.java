package dev.phonemod.item;

import dev.phonemod.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class PhoneItem extends Item {

    public PhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openPhoneScreen);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** True if the player carries a phone anywhere in their inventory or offhand. */
    public static boolean hasPhone(Player player) {
        Item phone = PhoneItems.PHONE.get();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(phone)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(phone)) return true;
        }
        return false;
    }
}
