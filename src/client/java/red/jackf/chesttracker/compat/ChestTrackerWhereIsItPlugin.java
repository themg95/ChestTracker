package red.jackf.chesttracker.compat;

import net.minecraft.client.Minecraft;
import red.jackf.chesttracker.memory.MemoryBank;
import red.jackf.whereisit.client.api.WhereIsItClientPlugin;
import red.jackf.whereisit.client.api.events.SearchInvoker;

public class ChestTrackerWhereIsItPlugin implements WhereIsItClientPlugin {
    @Override
    public void load() {
        // add our memories as a handler for where is it
        SearchInvoker.EVENT.register((request, resultConsumer) -> {
            if (MemoryBank.INSTANCE == null) return false;
            var level = Minecraft.getInstance().level;
            if (level == null) return false;
            var results = MemoryBank.INSTANCE.getPositions(level.dimension().location(), request);
            if (!results.isEmpty()) resultConsumer.accept(results);
            return true;
        });
    }
}
