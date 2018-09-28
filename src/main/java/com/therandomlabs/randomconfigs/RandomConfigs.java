package com.therandomlabs.randomconfigs;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.impl.SyntaxError;
import com.google.gson.GsonBuilder;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import org.apache.commons.lang3.StringUtils;
import org.dimdev.riftloader.RiftLoader;
import org.dimdev.riftloader.Side;
import org.dimdev.riftloader.listener.InitializationListener;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

public final class RandomConfigs implements InitializationListener {
	public static final String MODID = "randomconfigs";

	public static final boolean IS_CLIENT = RiftLoader.instance.getSide() == Side.CLIENT;

	public static final Path MC_DIR = Paths.get(".").toAbsolutePath().normalize();
	public static final Path CONFIG_DIR = MC_DIR.resolve("config").resolve(MODID);

	public static final String NEWLINE_REGEX = "(\r\n|\r|\n)";
	public static final Pattern NEWLINE = Pattern.compile(NEWLINE_REGEX);

	private static Field modifiers;

	@Override
	public void onInitialization() {
		MixinBootstrap.init();
		Mixins.addConfiguration("mixins." + MODID + ".json");

		try {
			DefaultConfigs.handle();
		} catch(IOException ex) {
			handleException("Failed to handle default configs", ex);
		}

		try {
			DefaultGameRules.ensureExists();
		} catch(IOException ex) {
			handleException("Failed to handle default gamerules", ex);
		}
	}

	public static Path getFile(String file) {
		final Path path = MC_DIR.resolve(file);

		if(isParent(path, MC_DIR)) {
			throw new IllegalArgumentException("Invalid path: " + file);
		}

		return path;
	}

	public static Path getConfig(String fileName) {
		try {
			Files.createDirectories(CONFIG_DIR);
		} catch(IOException ex) {
			handleException("Failed to create: " + CONFIG_DIR, ex);
		}

		final Path path = CONFIG_DIR.resolve(fileName).normalize();

		if(isParent(path, CONFIG_DIR)) {
			throw new IllegalArgumentException("Invalid config path: " + fileName);
		}

		return path;
	}

	public static String read(Path path) {
		try {
			return StringUtils.join(Files.readAllLines(path), System.lineSeparator());
		} catch(IOException ex) {
			if(!(ex instanceof NoSuchFileException)) {
				handleException("Failed to read file: " + path, ex);
			}
		}

		return null;
	}

	public static Path getJson(String jsonName) {
		return getConfig(jsonName + ".json");
	}

	public static JsonObject readJson(Path json) {
		final String raw = read(json);

		if(raw != null) {
			try {
				return Jankson.builder().build().load(raw);
			} catch(SyntaxError ex) {
				handleException("Failed to read JSON: " + json, ex);
			}
		}

		return null;
	}

	public static <T> T readJson(Path json, Class<T> clazz) {
		final String raw = read(json);

		if(raw != null) {
			try {
				return Jankson.builder().build().fromJson(raw, clazz);
			} catch(SyntaxError ex) {
				handleException("Failed to read JSON: " + json, ex);
			}
		}

		return null;
	}

	public static void writeJson(Path json, Object object) {
		final String raw = new GsonBuilder().setPrettyPrinting().create().toJson(object).
				replaceAll(" {2}", "\t");

		try {
			Files.write(json, (raw + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
		} catch(IOException ex) {
			handleException("Failed to write to: " + json, ex);
		}
	}

	public static boolean isParent(Path parent, Path path) {
		while((path = path.getParent()) != null) {
			if(path.equals(parent)) {
				return true;
			}
		}

		return false;
	}

	public static String readString(InputStream stream) {
		@SuppressWarnings("resource")
		final Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A");
		final String string = scanner.hasNext() ? scanner.next() : "";
		scanner.close();
		return string;
	}

	public static List<String> readLines(InputStream stream) {
		return Arrays.asList(NEWLINE.split(readString(stream)));
	}

	public static void handleException(String message, Exception ex) {
		throw new ReportedException(new CrashReport(message, ex));
	}

	public static Field findField(Class<?> clazz, String name, String obfName) {
		for(Field field : clazz.getDeclaredFields()) {
			if(name.equals(field.getName()) || obfName.equals(field.getName())) {
				field.setAccessible(true);
				return field;
			}
		}

		return null;
	}

	public static Field removeFinalModifier(Field field) {
		try {
			if(modifiers == null) {
				modifiers = Field.class.getDeclaredField("modifiers");
				modifiers.setAccessible(true);
			}

			modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
		} catch(Exception ex) {
			handleException("Failed to make " + field.getName() + " non-final", ex);
		}

		return field;
	}
}
