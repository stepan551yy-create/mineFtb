package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** First-run installer for the official FTB Infinity Evolved 3.1.0 client files. */
public final class MineFtbBootstrap {
    private static final String PACK_API = "https://api.modpacks.ch/public/ftb/23/99";
    private static final String PROFILE_KEY = "MineFTB_Infinity_Evolved_3.1.0";
    private static final String INSTANCE_NAME = "FTB_Infinity_Evolved_3.1.0";
    private static final String MC_VERSION = "1.7.10";
    private static final String FORGE_VERSION = "10.13.4.1614";
    private static final String VERSION_ID = MC_VERSION + "-forge-" + FORGE_VERSION;
    private static final String JAVA_ARGS = "-XX:+UseG1GC -XX:MaxGCPauseMillis=75 -XX:+DisableExplicitGC -Dfile.encoding=UTF-8";

    private MineFtbBootstrap() {}

    public static void maybeStart(LauncherActivity activity) {
        File instance = getInstanceDirectory();
        File marker = new File(instance, ".mineftb-3.1.0-ready");

        if (marker.isFile()) {
            try {
                finishLocalSetup(activity, instance);
            } catch (IOException e) {
                showFailure(activity, e);
                return;
            }
            if (!isForgeInstalled()) {
                new AlertDialog.Builder(activity)
                        .setTitle("MineFTB")
                        .setMessage("Файлы FTB Infinity Evolved уже готовы, но Forge 10.13.4.1614 ещё не установлен. Завершить установку?")
                        .setPositiveButton("Установить Forge", (dialog, which) ->
                                PojavApplication.sExecutorService.execute(() -> downloadForge(activity)))
                        .setNegativeButton("Позже", null)
                        .show();
            }
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("MineFTB: первый запуск")
                .setMessage("Скачать и установить официальный FTB Infinity Evolved 3.1.0 для Minecraft 1.7.10? Файлы сборки будут загружены из публичного API FTB/Modpacks.ch. Нужен интернет и свободное место.")
                .setPositiveButton("Установить", (dialog, which) ->
                        PojavApplication.sExecutorService.execute(() -> installPack(activity)))
                .setNegativeButton("Позже", null)
                .show();
    }

    private static void installPack(LauncherActivity activity) {
        try {
            JsonObject pack = JsonParser.parseString(DownloadUtils.downloadString(PACK_API)).getAsJsonObject();
            if (!"success".equalsIgnoreCase(stringOrNull(pack, "status"))) {
                throw new IOException("FTB API returned an error response");
            }
            verifyTargets(pack);

            JsonArray files = pack.getAsJsonArray("files");
            if (files == null || files.size() == 0) {
                throw new IOException("FTB API returned no client files");
            }

            File instance = getInstanceDirectory();
            if (!instance.exists() && !instance.mkdirs()) {
                throw new IOException("Cannot create instance directory: " + instance);
            }
            String instanceRoot = instance.getCanonicalPath() + File.separator;

            int total = 0;
            for (JsonElement item : files) {
                if (item.isJsonObject() && !bool(item.getAsJsonObject(), "serveronly")) total++;
            }

            int completed = 0;
            for (JsonElement item : files) {
                if (!item.isJsonObject()) continue;
                JsonObject file = item.getAsJsonObject();
                if (bool(file, "serveronly")) continue;

                String name = stringOrNull(file, "name");
                String path = stringOrNull(file, "path");
                String url = firstDownloadUrl(file);
                String sha1 = stringOrNull(file, "sha1");
                String type = stringOrNull(file, "type");

                if (name == null || name.isBlank()) continue;
                if (path == null) path = "";
                if (url == null || url.isBlank()) {
                    if ("folder".equalsIgnoreCase(type) || "directory".equalsIgnoreCase(type)) continue;
                    throw new IOException("No download URL for required file: " + path + name);
                }

                File destination = new File(instance, path + name);
                String destinationPath = destination.getCanonicalPath();
                if (!destinationPath.startsWith(instanceRoot)) {
                    throw new IOException("Unsafe path returned by pack API: " + path + name);
                }

                final String fileUrl = url;
                final File output = destination;
                final String expectedSha1 = sha1 == null || sha1.isBlank() ? null : sha1;
                DownloadUtils.ensureSha1(output, expectedSha1, () -> {
                    DownloadUtils.downloadFile(fileUrl, output);
                    return null;
                });

                completed++;
                ProgressKeeper.submitProgress(
                        ProgressLayout.INSTALL_MODPACK,
                        Math.max(1, completed * 100 / Math.max(total, 1)),
                        R.string.modpack_download_downloading_mods_fc,
                        completed,
                        total
                );
            }

            finishLocalSetup(activity, instance);
            writeText(new File(instance, ".mineftb-3.1.0-ready"), "FTB Infinity Evolved 3.1.0\n");
            Tools.runOnUiThread(() -> Toast.makeText(activity,
                    "FTB Infinity Evolved загружен. Теперь устанавливаю Forge 10.13.4.1614…",
                    Toast.LENGTH_LONG).show());
            downloadForge(activity);
        } catch (Exception e) {
            showFailure(activity, e);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private static void verifyTargets(JsonObject pack) throws IOException {
        JsonArray targets = pack.getAsJsonArray("targets");
        if (targets == null) throw new IOException("FTB API response has no target metadata");

        boolean minecraftOkay = false;
        boolean forgeOkay = false;
        for (JsonElement element : targets) {
            if (!element.isJsonObject()) continue;
            JsonObject target = element.getAsJsonObject();
            String name = stringOrNull(target, "name");
            String version = stringOrNull(target, "version");
            if ("minecraft".equalsIgnoreCase(name) && MC_VERSION.equals(version)) minecraftOkay = true;
            if ("forge".equalsIgnoreCase(name) && FORGE_VERSION.equals(version)) forgeOkay = true;
        }
        if (!minecraftOkay || !forgeOkay) {
            throw new IOException("Unexpected pack target. Expected Minecraft " + MC_VERSION + " / Forge " + FORGE_VERSION);
        }
    }

    private static void finishLocalSetup(LauncherActivity activity, File instance) throws IOException {
        File configDir = new File(instance, "config");
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new IOException("Cannot create config directory");
        }
        copyAsset(activity, "mineftb/splash.properties", new File(configDir, "splash.properties"));

        File controlDir = new File(Tools.CTRLMAP_PATH);
        if (!controlDir.exists() && !controlDir.mkdirs()) {
            throw new IOException("Cannot create controlmap directory");
        }
        File controls = new File(controlDir, "MineFTB-Xiaomi14Ultra.json");
        copyAsset(activity, "mineftb/FTB_IE_Xiaomi14Ultra_controls.json", controls);

        if (LauncherProfiles.mainProfileJson == null) {
            throw new IOException("launcher_profiles.json is not ready yet");
        }

        MinecraftProfile profile = MinecraftProfile.getDefaultProfile();
        profile.name = "FTB Infinity Evolved 3.1.0";
        profile.gameDir = "./custom_instances/" + INSTANCE_NAME;
        profile.lastVersionId = VERSION_ID;
        profile.javaArgs = JAVA_ARGS;
        profile.controlFile = controls.getAbsolutePath();
        LauncherProfiles.mainProfileJson.profiles.put(PROFILE_KEY, profile);
        LauncherProfiles.write();

        SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
        editor.putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, PROFILE_KEY);
        editor.putInt("allocation", 5120);
        editor.putInt("resolutionRatio", 75);
        editor.putString("javaArgs", JAVA_ARGS);
        editor.putString("defaultCtrl", controls.getAbsolutePath());
        editor.apply();
        LauncherPreferences.loadPreferences(activity);
    }

    private static void downloadForge(LauncherActivity activity) {
        try {
            if (isForgeInstalled()) {
                Tools.runOnUiThread(() -> Toast.makeText(activity,
                        "MineFTB готов к запуску.", Toast.LENGTH_LONG).show());
                return;
            }
            ModLoader forge = new ModLoader(ModLoader.MOD_LOADER_FORGE, FORGE_VERSION, MC_VERSION);
            Runnable task = forge.getDownloadTask(new NotificationDownloadListener(activity, forge));
            if (task == null) throw new IOException("Could not create Forge download task");
            task.run();
        } catch (Exception e) {
            showFailure(activity, e);
        }
    }

    private static boolean isForgeInstalled() {
        File versionFolder = new File(Tools.DIR_HOME_VERSION, VERSION_ID);
        return new File(versionFolder, VERSION_ID + ".json").isFile();
    }

    private static File getInstanceDirectory() {
        return new File(Tools.DIR_GAME_HOME, "custom_instances/" + INSTANCE_NAME);
    }

    private static String firstDownloadUrl(JsonObject file) {
        String primary = stringOrNull(file, "url");
        if (primary != null && !primary.isBlank()) return primary;
        JsonArray mirrors = file.getAsJsonArray("mirrors");
        if (mirrors == null) return null;
        for (JsonElement mirror : mirrors) {
            if (mirror.isJsonPrimitive() && mirror.getAsJsonPrimitive().isString()) {
                String value = mirror.getAsString();
                if (!value.isBlank()) return value;
            } else if (mirror.isJsonObject()) {
                String value = stringOrNull(mirror.getAsJsonObject(), "url");
                if (value != null && !value.isBlank()) return value;
            }
        }
        return null;
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            JsonElement value = object.get(key);
            return value != null && !value.isJsonNull() && value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String stringOrNull(JsonObject object, String key) {
        try {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) return null;
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void copyAsset(Context context, String assetPath, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        try (InputStream input = context.getAssets().open(assetPath);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static void writeText(File destination, String content) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void showFailure(LauncherActivity activity, Exception error) {
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        Tools.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle("MineFTB: ошибка установки")
                .setMessage(error.getClass().getSimpleName() + ": " + error.getMessage() +
                        "\n\nУже скачанные файлы сохранятся. При следующей попытке загрузка продолжится.")
                .setPositiveButton("OK", null)
                .show());
    }
}
