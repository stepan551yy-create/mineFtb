package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import java.io.File;
import java.util.regex.Pattern;

/** First-run local account chooser for MineFTB. */
public final class MineFtbLocalAccount {
    private static final String PREFS_NAME = "mineftb";
    private static final String PREF_USERNAME = "local_username";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private MineFtbLocalAccount() {}

    public static void ensure(LauncherActivity activity, Runnable onReady) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String configuredName = prefs.getString(PREF_USERNAME, "");
        if (isLocalAccountPresent(configuredName)) {
            PojavProfile.setCurrentProfile(activity, configuredName);
            onReady.run();
            return;
        }

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Например: Stepan");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("MineFTB: имя игрока")
                .setMessage("Выбери имя локальной учётной записи. Microsoft-аккаунт для одиночной игры не нужен.\n\nДопустимо: 3–16 символов, латинские буквы, цифры и _. ")
                .setView(input)
                .setPositiveButton("Создать", null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!USERNAME.matcher(name).matches()) {
                input.setError("Нужно 3–16 символов: A-Z, a-z, 0-9 или _");
                return;
            }

            File accountFile = new File(Tools.DIR_ACCOUNT_NEW, name + ".json");
            if (accountFile.exists()) {
                input.setError("Учётная запись с таким именем уже существует");
                return;
            }

            // Amethyst's own local-account path. The account spinner listener creates,
            // saves and immediately selects the account when password is empty.
            ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{name, ""});
            prefs.edit().putString(PREF_USERNAME, name).apply();
            PojavProfile.setCurrentProfile(activity, name);
            dialog.dismiss();
            onReady.run();
        }));

        dialog.show();
        input.requestFocus();
    }

    private static boolean isLocalAccountPresent(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) return false;
        File accountFile = new File(Tools.DIR_ACCOUNT_NEW, username + ".json");
        if (!accountFile.isFile()) return false;
        net.kdt.pojavlaunch.value.MinecraftAccount account =
                net.kdt.pojavlaunch.value.MinecraftAccount.load(username);
        return account != null && account.isLocal();
    }
}
