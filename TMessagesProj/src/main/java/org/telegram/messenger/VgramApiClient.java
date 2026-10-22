package org.telegram.messenger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small HTTP client for the independent VGRAM backend.
 * It intentionally does not use Telegram's MTProto/authentication stack.
 * The caller must supply the deployed HTTPS API base URL.
 */
public final class VgramApiClient {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    private final String baseUrl;
    private volatile String accessToken;

    public VgramApiClient(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("VGRAM API base URL is required");
        }
        String normalized = baseUrl.trim();
        if (!normalized.startsWith("https://")) {
            throw new IllegalArgumentException("VGRAM API must use HTTPS");
        }
        this.baseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    public void setAccessToken(String token) {
        accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void register(String username, String password, String displayName, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            body.put("display_name", displayName);
        } catch (JSONException e) {
            callback.onError("Не удалось подготовить данные регистрации");
            return;
        }
        request("POST", "/v1/auth/register", body, false, callback);
    }

    public void login(String username, String password, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
        } catch (JSONException e) {
            callback.onError("Не удалось подготовить данные входа");
            return;
        }
        request("POST", "/v1/auth/login", body, false, callback);
    }

    public void getCurrentUser(Callback callback) {
        request("GET", "/v1/users/me", null, true, callback);
    }

    public void getChats(Callback callback) {
        request("GET", "/v1/chats", null, true, callback);
    }

    public void startChat(String internalNumber, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("number", internalNumber);
        } catch (JSONException e) {
            callback.onError("Не удалось подготовить номер пользователя");
            return;
        }
        request("POST", "/v1/chats", body, true, callback);
    }

    public void getMessages(String conversationId, Callback callback) {
        request("GET", "/v1/chats/" + conversationId + "/messages", null, true, callback);
    }

    public void sendMessage(String conversationId, String message, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("body", message);
        } catch (JSONException e) {
            callback.onError("Не удалось подготовить сообщение");
            return;
        }
        request("POST", "/v1/chats/" + conversationId + "/messages", body, true, callback);
    }

    public void getStarsBalance(Callback callback) {
        request("GET", "/v1/stars/balance", null, true, callback);
    }

    private void request(String method, String path, JSONObject body, boolean authenticated, Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback is required");
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/json");
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(bytes);
                    }
                }
                if (authenticated) {
                    String token = accessToken;
                    if (token == null || token.trim().isEmpty()) {
                        callback.onError("Сначала войдите в VGRAM");
                        return;
                    }
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String responseText = readFully(stream);
                JSONObject response = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
                if (status >= 200 && status < 300) {
                    if ("/v1/auth/login".equals(path) || "/v1/auth/register".equals(path)) {
                        String token = response.optString("access_token", "");
                        if (!token.isEmpty()) {
                            accessToken = token;
                        }
                    }
                    callback.onSuccess(response);
                } else {
                    String detail = response.optString("detail", "HTTP " + status);
                    callback.onError(detail);
                }
            } catch (IOException | JSONException e) {
                callback.onError("Не удалось подключиться к серверу VGRAM: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }
}
