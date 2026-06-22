package com.idlefamiliar;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class IdleFamiliarPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(IdleFamiliarPlugin.class);
		RuneLite.main(args);
	}
}
