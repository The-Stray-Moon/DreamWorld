package cc.sighs.dreamworld.platform;

import net.minecraft.client.Minecraft;

/** Client-only task runner, isolated so the dedicated server never loads client classes. */
final class ForgeClientTasks {
    private ForgeClientTasks() {
    }

    static void execute(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(task);
        } else {
            task.run();
        }
    }
}
