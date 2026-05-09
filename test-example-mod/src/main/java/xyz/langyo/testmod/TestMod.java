package xyz.langyo.testmod;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

@Mod("mcp_test_example")
public class TestMod {
    public static final String MOD_ID = "mcp_test_example";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredBlock<TestBlock> TEST_BLOCK = BLOCKS.register("test_block", TestBlock::new);
    public static final DeferredItem<TestItem> TEST_ITEM = ITEMS.register("test_wand", TestItem::new);

    public TestMod(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("[MCP Test Example] Mod loaded - ready for MCP testing");
        });
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static class ServerEvents {
        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("mctest")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "[MCP Test] Commands available: /mctest, /mcgive <item>, /mcteleport, /mcinfo"), false);
                        return 1;
                    })
            );

            event.getDispatcher().register(Commands.literal("mcgive")
                    .then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((ctx, builder) -> builder.suggest("diamond").suggest("test_wand")
                                    .suggest("test_block").suggest("apple").buildFuture())
                            .executes(ctx -> {
                                String item = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item");
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                switch (item.toLowerCase()) {
                                    case "diamond" -> player.getInventory().add(new ItemStack(Items.DIAMOND, 64));
                                    case "test_wand" -> player.getInventory().add(new ItemStack(TestMod.TEST_ITEM.get(), 1));
                                    case "test_block" -> player.getInventory().add(new ItemStack(TestMod.TEST_BLOCK.get().asItem(), 64));
                                    default -> player.getInventory().add(new ItemStack(Items.APPLE, 16));
                                }
                                ctx.getSource().sendSuccess(() -> Component.literal("[MCP Test] Gave: " + item), true);
                                return 1;
                            }))
            );

            event.getDispatcher().register(Commands.literal("mcteleport")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        BlockPos pos = player.blockPosition().above(5);
                        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        ctx.getSource().sendSuccess(() -> Component.literal("[MCP Test] Teleported up 5 blocks"), true);
                        return 1;
                    })
            );

            event.getDispatcher().register(Commands.literal("mcinfo")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                                "[MCP Test Info] Pos: %s | Health: %.1f | Dim: %s | Gamemode: %s",
                                player.blockPosition().toShortString(),
                                player.getHealth(),
                                player.level().dimension().location(),
                                player.gameMode.getGameModeForPlayer().getName()
                        )), false);
                        return 1;
                    })
            );
        }

        @SubscribeEvent
        public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal(
                        "[MCP Test] Welcome! Use /mctest, /mcgive, /mcteleport, /mcinfo for testing"));
                player.getInventory().add(new ItemStack(TestMod.TEST_ITEM.get()));
                player.getInventory().add(new ItemStack(TestMod.TEST_BLOCK.get().asItem(), 64));
            }
        }
    }
}
