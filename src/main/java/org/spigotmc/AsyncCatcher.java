package org.spigotmc;

import com.mohistmc.LunamuraMC;
import net.minecraft.server.MinecraftServer;

public class AsyncCatcher
{

    public static boolean enabled = true;

    public static void catchOp(String reason)
    {
        if ( enabled && Thread.currentThread() != MinecraftServer.getServer().serverThread )
        {
            throw new IllegalStateException(LunamuraMC.i18n.as("lunamura.i18n.63", reason));
        }
    }

    public static boolean catchAsync()
    {
        if ( enabled && Thread.currentThread() != MinecraftServer.getServer().serverThread )
        {
            return true;
        }
        return false;
    }
}
