package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VgramUpdateActivity extends VgramBaseActivity {
    private TextView status;
    private TextView notes;
    private TextView action;
    private ProgressBar progress;
    private String downloadUrl;
    private String checksumUrl;
    private String remoteVersion;
    private volatile File pendingFile;
    private boolean waitingForInstallPermission;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupScreen("UPDATE CENTER", "Обновления VGRAM", "Проверка релиза, загрузка APK и установка — внутри приложения.");
        LinearLayout release = card("Центр обновлений", "Проверьте версию и список изменений без перехода в браузер.");
        status = label("Подключаемся к серверу релизов…", 14, 0xfff0f0ec, true);
        status.setLineSpacing(dp(3), 1f);
        release.addView(status, margin(0, dp(13), 0, 0));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        release.addView(progress, margin(0, dp(14), 0, 0));
        notes = label("", 12, 0xffa3a49f, false);
        release.addView(notes, margin(0, dp(12), 0, 0));
        action = addButton(release, "Проверить обновления", this::checkRelease);
        card("Как проходит установка", "1. VGRAM проверяет последний релиз.\n2. APK скачивается с отображением прогресса.\n3. Android открывает системную установку. Пользователь подтверждает установку.");
        checkRelease();
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingForInstallPermission && Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) {
  waitingForInstallPermission = false;
  File file = pendingFile;
  pendingFile = null;
  if (file != null) installApk(file);
        }
    }

    private void checkRelease() {
        action.setEnabled(false);
        action.setText("Проверяем…");
        status.setText("Подключение к GitHub Releases…");
        notes.setText("");
        new Thread(() -> {
  HttpURLConnection connection = null;
  try {
      connection = (HttpURLConnection) new URL("https://api.github.com/repos/ramzilzokirov-lgtm/VGRAM-Main/releases/latest").openConnection();
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(20000);
      connection.setRequestProperty("Accept", "application/vnd.github+json");
      connection.setRequestProperty("User-Agent", "VGRAM-Android-Update");
      int code = connection.getResponseCode();
      if (code != 200) throw new IllegalStateException("Сервер релизов ответил HTTP " + code);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream in = connection.getInputStream()) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
      }
      JSONObject release = new JSONObject(bytes.toString("UTF-8"));
      remoteVersion = release.optString("tag_name", "").replaceFirst("^[vV]", "");
      String changelog = release.optString("body", "").trim();
      JSONArray assets = release.optJSONArray("assets");
      downloadUrl = null;
      checksumUrl = null;
      if (assets != null) {
          for (int i = 0; i < assets.length(); i++) {
              JSONObject asset = assets.optJSONObject(i);
              if (asset == null) continue;
              String name = asset.optString("name", "");
              if ("VGRAM-debug.apk.sha256".equalsIgnoreCase(name)) {
                  checksumUrl = asset.optString("browser_download_url", null);
              } else if ("VGRAM-debug.apk".equalsIgnoreCase(name)) {
                  downloadUrl = asset.optString("browser_download_url", null);
              }
          }
      }
      if (downloadUrl == null || downloadUrl.isEmpty()) throw new IllegalStateException("В последнем релизе пока нет APK-файла.");
      if (checksumUrl == null || checksumUrl.isEmpty()) throw new IllegalStateException("У релиза нет SHA-256 контрольной суммы.");
      String current;
      try { current = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
      catch (Exception e) { current = "0.0.0"; }
      final String installed = current == null ? "0.0.0" : current.replaceFirst("^[vV]", "");
      final String releaseNotes = changelog.isEmpty() ? "Для этого релиза не добавлено описание изменений." : changelog;
      final boolean newer = compareVersions(remoteVersion, installed) > 0;
      runOnUiThread(() -> {
          notes.setText("Текущая версия: " + installed + "\nПоследний релиз: " + remoteVersion + "\n\n" + releaseNotes);
          if (newer) {
              status.setText("Доступна новая версия VGRAM.");
              action.setText("Скачать и установить  •  " + remoteVersion);
              action.setOnClickListener(v -> downloadAndInstall());
          } else {
              status.setText("Установлена актуальная версия VGRAM.");
              action.setText("Проверить ещё раз");
              action.setOnClickListener(v -> checkRelease());
          }
          action.setEnabled(true);
      });
  } catch (Exception error) {
      String message = error.getMessage() == null ? "Не удалось проверить релизы." : error.getMessage();
      runOnUiThread(() -> {
          status.setText(message);
          notes.setText("Проверьте интернет-соединение и повторите попытку.");
          action.setText("Повторить проверку");
          action.setOnClickListener(v -> checkRelease());
          action.setEnabled(true);
      });
  } finally {
      if (connection != null) connection.disconnect();
  }
        }, "VGRAM-Release-Check").start();
    }

    private void downloadAndInstall() {
        if (downloadUrl == null || downloadUrl.isEmpty()) { checkRelease(); return; }
        action.setEnabled(false);
        action.setText("Подготовка загрузки…");
        status.setText("Скачиваем APK…");
        notes.setText("");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgress(0);
        new Thread(() -> {
  HttpURLConnection connection = null;
  File partial = null;
  try {
      connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
      connection.setConnectTimeout(20000);
      connection.setReadTimeout(30000);
      connection.setInstanceFollowRedirects(true);
      connection.setRequestProperty("User-Agent", "VGRAM-Android-Update");
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) throw new IllegalStateException("Ошибка загрузки APK: HTTP " + code);
      File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
      if (directory == null) throw new IllegalStateException("Android не предоставил папку загрузки.");
      if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Не удалось создать папку загрузки.");
      partial = new File(directory, "VGRAM-update.apk.part");
      File target = new File(directory, "VGRAM-update.apk");
      int total = connection.getContentLength();
      long written = 0;
      try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(partial)) {
          byte[] buffer = new byte[16384];
          int count;
          while ((count = in.read(buffer)) != -1) {
              out.write(buffer, 0, count);
              written += count;
              if (total > 0) {
                  int percent = (int) Math.min(100, written * 100 / total);
                  runOnUiThread(() -> {
                      progress.setProgress(percent);
                      action.setText("Загрузка… " + percent + "%");
                  });
              }
          }
          out.getFD().sync();
      }
      if (written < 1024 * 1024) throw new IllegalStateException("Файл загрузился не полностью или не является APK.");
          String expectedChecksum = fetchExpectedChecksum();
          String actualChecksum = sha256(partial);
          if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) throw new IllegalStateException("Контрольная сумма APK не совпала. Файл удалён, установка отменена.");
      if (target.exists() && !target.delete()) throw new IllegalStateException("Не удалось заменить старый APK.");
      if (!partial.renameTo(target)) throw new IllegalStateException("Не удалось сохранить APK.");
      runOnUiThread(() -> {
          progress.setProgress(100);
          status.setText("Загрузка завершена. Открываем установщик Android…");
          action.setText("Открыть установку");
          action.setEnabled(true);
          installApk(target);
      });
  } catch (Exception error) {
      if (partial != null) try { partial.delete(); } catch (Exception ignored) { }
      String message = error.getMessage() == null ? "Ошибка загрузки APK." : error.getMessage();
      runOnUiThread(() -> {
          status.setText(message);
          action.setText("Повторить загрузку");
          action.setEnabled(true);
          action.setOnClickListener(v -> downloadAndInstall());
      });
  } finally {
      if (connection != null) connection.disconnect();
  }
        }, "VGRAM-APK-Download").start();
    }

                  private String fetchExpectedChecksum() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(checksumUrl).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "VGRAM-Android-Update");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Не удалось получить SHA-256 контрольную сумму.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            String[] parts = bytes.toString("UTF-8").trim().split("\\s+");
            if (parts.length == 0 || !parts[0].matches("(?i)[0-9a-f]{64}")) throw new IllegalStateException("Некорректная SHA-256 контрольная сумма.");
            return parts[0];
        } finally {
            connection.disconnect();
        }
    }

    private String sha256(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        return result.toString();
    }

    private void installApk(File file) {
        if (file == null || !file.isFile() || file.length() < 1024 * 1024) {
  status.setText("APK не найден или повреждён. Скачайте обновление повторно.");
  return;
        }
        try {
  if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
      pendingFile = file;
      waitingForInstallPermission = true;
      startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
      status.setText("Разрешите VGRAM устанавливать обновления, затем вернитесь в приложение.");
      return;
  }
  Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
  Intent install = new Intent(Intent.ACTION_VIEW);
  install.setDataAndType(uri, "application/vnd.android.package-archive");
  install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
  startActivity(install);
        } catch (Exception error) {
  status.setText("Не удалось открыть установщик Android: " + (error.getMessage() == null ? "ошибка доступа к APK" : error.getMessage()));
  Toast.makeText(this, "Не удалось запустить установку APK", Toast.LENGTH_LONG).show();
        }
    }

    private int compareVersions(String left, String right) {
        String[] a = left.split("[.+-]");
        String[] b = right.split("[.+-]");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
  int av = i < a.length ? number(a[i]) : 0;
  int bv = i < b.length ? number(b[i]) : 0;
  if (av != bv) return av > bv ? 1 : -1;
        }
        return 0;
    }

    private int number(String part) {
        try { return Integer.parseInt(part.replaceAll("[^0-9]", "")); } catch (Exception ignored) { return 0; }
    }
}
