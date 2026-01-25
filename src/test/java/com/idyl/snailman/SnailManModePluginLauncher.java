package com.idyl.snailman;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SnailManModePluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SnailManModePlugin.class);
		RuneLite.main(args);
	}
}