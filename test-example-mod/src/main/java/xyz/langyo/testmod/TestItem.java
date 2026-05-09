package xyz.langyo.testmod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public class TestItem extends Item {
    public TestItem() {
        super(new Item.Properties()
                .stacksTo(16)
                .fireResistant());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (player != null && !level.isClientSide) {
            player.sendSystemMessage(Component.literal(
                    "[MCP Test] Right-clicked at " + pos.toShortString() +
                    " on side " + context.getClickedFace().getName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("MCP Test Item - right-click blocks to test"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
