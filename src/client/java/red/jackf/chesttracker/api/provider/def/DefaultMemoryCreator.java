package red.jackf.chesttracker.api.provider.def;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import red.jackf.chesttracker.api.EventPhases;
import red.jackf.chesttracker.api.provider.MemoryEntry;
import red.jackf.jackfredlib.api.base.ResultHolder;

public interface DefaultMemoryCreator {
    Event<DefaultMemoryCreator> EVENT = EventFactory.createWithPhases(DefaultMemoryCreator.class, invokers -> (screen, level) -> {
        for (DefaultMemoryCreator invoker : invokers) {
            var result = invoker.get(screen, level);
            if (result.shouldTerminate()) return result;
        }
        return ResultHolder.empty();

    }, EventPhases.PRIORITY_PHASE, EventPhases.DEFAULT_PHASE, EventPhases.FALLBACK_PHASE);

    ResultHolder<MemoryEntry> get(AbstractContainerScreen<?> screen, ClientLevel level);
}