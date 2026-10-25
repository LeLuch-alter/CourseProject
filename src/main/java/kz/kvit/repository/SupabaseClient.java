package kz.kvit.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kz.kvit.config.AppConfig;
import kz.kvit.i18n.I18n;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Talks to Supabase over HTTPS: Auth (login) and the REST API of the database.
 *
 * Security model: the app only has the publishable key, which by itself gives no access
 * to the tables. Every database request also carries the logged-in user's access token,
 * and Row Level Security in Postgres lets that user see only their own rows.
 */
public class SupabaseClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    // Refresh the access token a little before it actually expires.
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String publishableKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    // Tokens live only in memory and are never written to disk: the user logs in on every start.
    private Session session;

    private record Session(String accessToken, String refreshToken, Instant expiresAt, String email) {
    }

    public SupabaseClient(AppConfig config) {
        this.baseUrl = config.getSupabaseUrl();
        this.publishableKey = config.getPublishableKey();
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.mapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .build();
    }

    public ObjectNode createObject() {
        return mapper.createObjectNode();
    }

    // ----------------------------------------------------------------- Auth

    public synchronized void signIn(String email, String password) {
        ObjectNode body = createObject().put("email", email).put("password", password);
        session = toSession(send(authRequest("/auth/v1/token?grant_type=password", body)));
    }

    /** @return true if the user is logged in right away, false if email confirmation is required first */
    public synchronized boolean signUp(String email, String password) {
        ObjectNode body = createObject().put("email", email).put("password", password);
        JsonNode response = send(authRequest("/auth/v1/signup", body));
        if (response.hasNonNull("access_token")) {
            session = toSession(response);
            return true;
        }
        return false;
    }

    public synchronized void signOut() {
        if (session == null) {
            return;
        }
        try {
            HttpRequest request = baseRequest("/auth/v1/logout")
                    .header("Authorization", "Bearer " + session.accessToken())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            send(request);
        } catch (DataAccessException e) {
            // The local session is cleared anyway; the server token then simply expires on its own.
            System.err.println("Logout request failed: " + e.getMessage());
        } finally {
            session = null;
        }
    }

    public synchronized boolean isSignedIn() {
        return session != null;
    }

    public synchronized String getCurrentUserEmail() {
        return session == null ? null : session.email();
    }

    // ------------------------------------------------------------ REST API

    public JsonNode get(String path) {
        return send(restRequest(path).GET().build());
    }

    public JsonNode post(String path, JsonNode body) {
        return send(restRequest(path)
                .header("Prefer", "return=representation")
                .POST(jsonBody(body))
                .build());
    }

    public JsonNode patch(String path, JsonNode body) {
        return send(restRequest(path)
                .header("Prefer", "return=representation")
                .method("PATCH", jsonBody(body))
                .build());
    }

    public void delete(String path) {
        send(restRequest(path).DELETE().build());
    }

    // ------------------------------------------------------------- Helpers

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("apikey", publishableKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private HttpRequest authRequest(String path, JsonNode body) {
        return baseRequest(path).POST(jsonBody(body)).build();
    }

    private HttpRequest.Builder restRequest(String path) {
        return baseRequest(path).header("Authorization", "Bearer " + currentAccessToken());
    }

    private synchronized String currentAccessToken() {
        if (session == null) {
            throw new DataAccessException(I18n.t("error.db.notSignedIn"));
        }
        if (Instant.now().plus(REFRESH_MARGIN).isAfter(session.expiresAt())) {
            refreshSession();
        }
        return session.accessToken();
    }

    private void refreshSession() {
        ObjectNode body = createObject().put("refresh_token", session.refreshToken());
        try {
            session = toSession(send(authRequest("/auth/v1/token?grant_type=refresh_token", body)));
        } catch (DataAccessException e) {
            session = null;
            throw new DataAccessException(I18n.t("error.db.sessionExpired"), e);
        }
    }

    private Session toSession(JsonNode response) {
        if (!response.hasNonNull("access_token") || !response.hasNonNull("refresh_token")) {
            throw new DataAccessException(I18n.t("error.db.badAuthResponse"));
        }
        long expiresInSeconds = response.path("expires_in").asLong(3600);
        return new Session(
                response.get("access_token").asText(),
                response.get("refresh_token").asText(),
                Instant.now().plusSeconds(expiresInSeconds),
                response.path("user").path("email").asText(""));
    }

    private HttpRequest.BodyPublisher jsonBody(JsonNode body) {
        try {
            return HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new DataAccessException(I18n.t("error.db.serialize"), e);
        }
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DataAccessException(I18n.t("error.db.offline"), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException(I18n.t("error.db.interrupted"), e);
        }

        String body = response.body();
        if (response.statusCode() >= 400) {
            throw toUserFriendlyError(response.statusCode(), body);
        }
        if (body == null || body.isBlank()) {
            return mapper.missingNode();
        }
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new DataAccessException(I18n.t("error.db.badResponse"), e);
        }
    }

    private DataAccessException toUserFriendlyError(int status, String body) {
        String errorCode = "";
        String code = "";
        String message = body == null ? "" : body;
        try {
            JsonNode json = mapper.readTree(body);
            errorCode = json.path("error_code").asText("");
            code = json.path("code").asText("");
            for (String field : new String[]{"msg", "message", "error_description"}) {
                if (json.hasNonNull(field)) {
                    message = json.get(field).asText();
                    break;
                }
            }
        } catch (JsonProcessingException e) {
            // Body is not JSON (e.g. a proxy error page): keep the raw text as the message.
            message = body;
        }

        String text = switch (errorCode) {
            case "invalid_credentials" -> I18n.t("error.auth.invalidCredentials");
            case "email_not_confirmed" -> I18n.t("error.auth.emailNotConfirmed");
            case "user_already_exists" -> I18n.t("error.auth.userExists");
            case "weak_password" -> I18n.t("error.auth.weakPassword");
            case "validation_failed" -> I18n.t("error.auth.validation");
            default -> null;
        };
        if (text == null) {
            text = switch (code) {
                case "PGRST205" -> I18n.t("error.db.noTables");
                case "PGRST301", "PGRST303" -> I18n.t("error.db.sessionExpired");
                case "42501" -> I18n.t("error.db.forbidden");
                case "23503" -> I18n.t("error.db.foreignKey");
                case "23514" -> I18n.t("error.db.check");
                case "P0001" -> I18n.t("error.db.rejected", message);
                default -> null;
            };
        }
        if (text == null) {
            text = switch (status) {
                case 401, 403 -> I18n.t("error.db.unauthorized");
                case 429 -> I18n.t("error.db.rateLimit");
                default -> status >= 500
                        ? I18n.t("error.db.serverDown", status)
                        : I18n.t("error.db.requestFailed", status, message);
            };
        }
        return new DataAccessException(text);
    }
}
