package red.jackf.chesttracker;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.cloth.api.client.events.v0.ClothClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import red.jackf.chesttracker.config.ChestTrackerConfig;
import red.jackf.chesttracker.gui.ChestTrackerButtonWidget;
import red.jackf.chesttracker.gui.ItemListScreen;
import red.jackf.chesttracker.memory.Memory;
import red.jackf.chesttracker.memory.MemoryDatabase;
import red.jackf.chesttracker.memory.MemoryUtils;
import red.jackf.chesttracker.render.TextRenderUtils;
import red.jackf.chesttracker.resource.ButtonPositionManager;
import red.jackf.whereisit.WhereIsItClient;
import red.jackf.whereisit.client.PositionData;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ChestTracker implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("ChestTracker");
    public static final String MODID = "chesttracker";
    public static final KeyBinding GUI_KEY = new KeyBinding("key." + MODID + ".opengui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories." + MODID);
    public static final ChestTrackerConfig CONFIG = AutoConfig.register(ChestTrackerConfig.class, JanksonConfigSerializer::new).getConfig();

    public static Identifier id(String path) {
        return new Identifier(MODID, path);
    }

    public static void sendDebugMessage(Text text) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null)
            player.sendSystemMessage(new LiteralText("[ChestTracker] ").formatted(Formatting.YELLOW).append(text), Util.NIL_UUID);
    }

    public static void searchForItem(@NotNull ItemStack stack, @NotNull World world) {
        MemoryDatabase database = MemoryDatabase.getCurrent();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (database != null && player != null) {
            List<Memory> found = database.findItems(stack, world.getRegistryKey().getValue());
            if (found.size() >= 1) {
                float r = ((ChestTracker.CONFIG.visualOptions.borderColour >> 16) & 0xff) / 255f;
                float g = ((ChestTracker.CONFIG.visualOptions.borderColour >> 8) & 0xff) / 255f;
                float b = ((ChestTracker.CONFIG.visualOptions.borderColour) & 0xff) / 255f;
                WhereIsItClient.handleFoundItems(found.stream()
                    .map(memory -> new PositionData(memory.getPosition(), world.getTime(), VoxelShapes.fullCube(), r, g, b))
                    .toList());
                if (MinecraftClient.getInstance().player != null)
                    MinecraftClient.getInstance().player.closeHandledScreen();
            }
        }
    }

    public static int getSquareSearchRange() {
        int blockValue = sliderValueToRange(ChestTracker.CONFIG.miscOptions.searchRange);
        if (blockValue == Integer.MAX_VALUE) return blockValue;
        return blockValue * blockValue;
    }

    public static int sliderValueToRange(int sliderValue) {
        if (sliderValue <= 16) {
            return 15 + sliderValue;
        } else if (sliderValue <= 32) {
            return 30 + ((sliderValue - 16) * 2);
        } else if (sliderValue <= 48) {
            return 60 + ((sliderValue - 32) * 4);
        } else if (sliderValue <= 64) {
            return 120 + ((sliderValue - 48) * 8);
        } else if (sliderValue <= 80) {
            return 240 + ((sliderValue - 64) * 16);
        } else if (sliderValue <= 97) {
            return 480 + ((sliderValue - 80) * 32);
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(GUI_KEY);

        // Save if someone just decides to X out of craft
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MemoryDatabase database = MemoryDatabase.getCurrent();
            if (database != null) database.save();
        });

        WorldRenderEvents.AFTER_ENTITIES.register(TextRenderUtils::drawLabels);

        WhereIsItClient.SEARCH_FOR_ITEM.register((item, matchNbt, compoundTag) -> {
            if (MinecraftClient.getInstance().world != null) {
                ItemStack stack = new ItemStack(item);
                if (matchNbt) stack.setTag(compoundTag);
                searchForItem(stack, MinecraftClient.getInstance().world);
            }
        });

        // Opening GUI
        ClientTickEvents.START_CLIENT_TICK.register((client) -> {
            if (GUI_KEY.wasPressed() && client.world != null) {
                if (client.currentScreen != null) client.currentScreen.onClose();
                client.openScreen(new ItemListScreen());
            }
        });

        // Checking for memories that are still alive
        ClientTickEvents.END_WORLD_TICK.register(MemoryUtils::checkValidCycle);

        // JSON Button Positions
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ButtonPositionManager());

        // Find hotkeys
        ClothClientHooks.SCREEN_KEY_RELEASED.register((mc, currentScreen, keyCode, scanCode, modifiers) -> {
            if (GUI_KEY.matchesKey(keyCode, scanCode)) {
                if (currentScreen instanceof HandledScreen && !(currentScreen instanceof CreativeInventoryScreen && ((CreativeInventoryScreen) currentScreen).getSelectedTab() == ItemGroup.SEARCH.getIndex())) {
                    currentScreen.onClose();
                    mc.openScreen(new ItemListScreen());
                }
            }

            return ActionResult.PASS;
        });

        // ChestTracker GUI button
        ClothClientHooks.SCREEN_INIT_POST.register((minecraftClient, screen, screenHooks) -> {
            if (screen instanceof HandledScreen) {
                if (ChestTracker.CONFIG.visualOptions.enableButton) {
                    screenHooks.cloth$addDrawableChild(new ChestTrackerButtonWidget((HandledScreen<?>) screen, shouldDeleteBeEnabled()));
                }
            }
        });

        UseBlockCallback.EVENT.register((playerEntity, world, hand, blockHitResult) -> {
            if (world.isClient) {
                Block hit = world.getBlockState(blockHitResult.getBlockPos()).getBlock();
                if (MemoryUtils.isValidInventoryHolder(hit, world, blockHitResult.getBlockPos())) {
                    MemoryUtils.setLatestPos(blockHitResult.getBlockPos());
                    MemoryUtils.setWasEnderchest(hit == Blocks.ENDER_CHEST);
                } else {
                    MemoryUtils.setLatestPos(null);
                    MemoryUtils.setWasEnderchest(false);
                }
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) {
                MemoryUtils.setLatestPos(null);
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) {
                MemoryUtils.setLatestPos(null);
            }
            return TypedActionResult.pass(ItemStack.EMPTY);
        });
    }

    private boolean shouldDeleteBeEnabled() {
        return MemoryUtils.getLatestPos() != null
            && !(MinecraftClient.getInstance().currentScreen instanceof AbstractInventoryScreen);
    }
}
