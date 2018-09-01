package com.therandomlabs.randomconfigs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = RandomConfigs.MODID)
public final class DefaultGamerules {
	public static class DefaultGamerule {
		public String key;
		public String value;
		public boolean forced;

		public DefaultGamerule(String key, String value, boolean forced) {
			this.key = key;
			this.value = value;
			this.forced = forced;
		}
	}

	public static class DGGameRules extends GameRules {
		private final Set<String> forced;

		public DGGameRules(GameRules rules, Set<String> forced) {
			this.rules = rules.rules;
			this.forced = forced;
		}

		@Override
		public void setOrCreateGameRule(String key, String ruleValue) {
			if(!forced.contains(key)) {
				super.setOrCreateGameRule(key, ruleValue);
			}
		}
	}

	public static final String MODE_OR_WORLD_TYPE_SPECIFIC = "MODE_OR_WORLD_TYPE_SPECIFIC";
	public static final String WORLD_BORDER_SIZE = "WORLD_BORDER_SIZE";
	public static final Path JSON = RandomConfigs.getJson("defaultgamerules");
	public static final List<String> DEFAULT = RandomConfigs.readLines(
			DefaultGamerules.class.getResourceAsStream(
					"/assets/randomconfigs/defaultgamerules.json"
			)
	);

	private static List<DefaultGamerule> cachedDefaultGamerules;

	@SubscribeEvent
	public static void onCreateSpawn(WorldEvent.CreateSpawnPosition event) {
		final World world = event.getWorld();

		if(world.provider.getDimensionType() != DimensionType.OVERWORLD) {
			return;
		}

		final WorldInfo worldInfo = world.getWorldInfo();
		final int gamemode = worldInfo.getGameType().getID();
		final String type = world.getWorldType().getName();

		List<DefaultGamerule> defaultGamerules = null;

		try {
			defaultGamerules = get(gamemode, type);
		} catch(Exception ex) {
			RandomConfigs.handleException("Failed to read default gamerules", ex);
		}

		cachedDefaultGamerules = defaultGamerules;

		for(DefaultGamerule rule : defaultGamerules) {
			if(rule.key.equals(WORLD_BORDER_SIZE)) {
				world.getWorldBorder().setSize(Integer.parseInt(rule.value));
			} else {
				worldInfo.gameRules.setOrCreateGameRule(rule.key, rule.value);
			}
		}
	}

	@SubscribeEvent
	public static void onWorldLoad(WorldEvent.Load event) {
		final World world = event.getWorld();

		if(world.isRemote) {
			return;
		}

		final WorldInfo worldInfo = world.getWorldInfo();
		List<DefaultGamerule> defaultGamerules = null;

		if(cachedDefaultGamerules != null) {
			defaultGamerules = cachedDefaultGamerules;
			cachedDefaultGamerules = null;
		} else {
			final int gamemode = worldInfo.getGameType().getID();
			final String type = world.getWorldType().getName();

			try {
				defaultGamerules = get(gamemode, type);
			} catch(Exception ex) {
				RandomConfigs.handleException("Failed to read default gamerules", ex);
			}
		}

		final Set<String> forced = new HashSet<>();

		for(DefaultGamerule rule : defaultGamerules) {
			if(!rule.key.equals(WORLD_BORDER_SIZE)) {
				if(rule.forced) {
					forced.add(rule.key);
				}

				if(!worldInfo.gameRules.hasRule(rule.key)) {
					worldInfo.gameRules.setOrCreateGameRule(rule.key, rule.value);
				}
			}
		}

		worldInfo.gameRules = new DGGameRules(worldInfo.gameRules, forced);
	}

	public static void create() throws IOException {
		Files.write(JSON, DEFAULT);
	}

	public static boolean exists() {
		return Files.exists(JSON);
	}

	public static void ensureExists() throws IOException {
		if(!exists()) {
			create();
		}
	}

	public static List<DefaultGamerule> get(int gamemode, String worldType) throws IOException {
		if(!exists()) {
			create();
			return Collections.emptyList();
		}

		final JsonObject json = RandomConfigs.readJson(JSON);

		final List<DefaultGamerule> gamerules = new ArrayList<>();

		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			final String key = entry.getKey();
			final JsonElement value = entry.getValue();

			if(key.equals(WORLD_BORDER_SIZE)) {
				gamerules.add(new DefaultGamerule(WORLD_BORDER_SIZE, value.toString(), false));
				continue;
			}

			if(!value.isJsonObject()) {
				continue;
			}

			final JsonObject object = value.getAsJsonObject();

			if(key.equals(MODE_OR_WORLD_TYPE_SPECIFIC)) {
				getSpecific(gamerules, object, gamemode, worldType);
				continue;
			}

			final DefaultGamerule gamerule = get(key, object);

			if(gamerule != null) {
				gamerules.add(gamerule);
			}
		}

		return gamerules;
	}

	private static void getSpecific(List<DefaultGamerule> gamerules, JsonObject json, int gamemode,
			String worldType) {
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			final String key = entry.getKey();

			if(!matches(key, gamemode, worldType)) {
				continue;
			}

			final JsonElement value = entry.getValue();

			if(!value.isJsonObject()) {
				continue;
			}

			final JsonObject object = value.getAsJsonObject();
			get(gamerules, object);
		}
	}

	private static boolean matches(String key, int gamemode, String worldType) {
		final GameType gameType = GameType.getByID(gamemode);
		final String[] split = key.split(":");

		if(!split[0].isEmpty()) {
			final String[] gamemodes = split[0].split(",");
			boolean gamemodeFound = false;

			for(String mode : gamemodes) {
				if(GameType.parseGameTypeWithDefault(mode, null) == gameType) {
					gamemodeFound = true;
					break;
				}
			}

			if(!gamemodeFound) {
				return false;
			}
		}

		if(split.length > 1) {
			for(String type : split[1].split(",")) {
				if(type.equals(worldType)) {
					return true;
				}
			}

			return false;
		}

		return true;
	}

	private static void get(List<DefaultGamerule> gamerules, JsonObject json) {
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			final String key = entry.getKey();
			final JsonElement value = entry.getValue();

			if(key.equals(WORLD_BORDER_SIZE)) {
				gamerules.add(new DefaultGamerule(WORLD_BORDER_SIZE, value.toString(), false));
				continue;
			}

			if(!value.isJsonObject()) {
				continue;
			}

			final DefaultGamerule gamerule = get(key, value.getAsJsonObject());

			if(gamerule != null) {
				gamerules.add(gamerule);
			}
		}
	}

	private static DefaultGamerule get(String key, JsonObject value) {
		if(!value.has("value") || !value.has("forced")) {
			return null;
		}

		final JsonElement forced = value.get("forced");

		if(!forced.isJsonPrimitive()) {
			return null;
		}

		final JsonPrimitive primitive = forced.getAsJsonPrimitive();

		if(!primitive.isBoolean()) {
			return null;
		}

		return new DefaultGamerule(key, value.get("value").toString(), primitive.getAsBoolean());
	}
}