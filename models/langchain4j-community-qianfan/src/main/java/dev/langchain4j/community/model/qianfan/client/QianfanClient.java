package dev.langchain4j.community.model.qianfan.client;

import static dev.langchain4j.community.model.qianfan.client.Utils.pathWithQuery;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.qianfan.client.chat.ChatTokenResponse;
import dev.langchain4j.community.model.qianfan.client.completion.CompletionRequest;
import dev.langchain4j.community.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.community.model.qianfan.client.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.qianfan.client.embedding.EmbeddingResponse;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QianfanClient {

    private static final Logger log = LoggerFactory.getLogger(QianfanClient.class);
    private final String baseUrl;
    private String token;
    private final String apiKey;
    private final String secretKey;

    public static final String ACCESS_TOKEN = "access_token";
    public static final String GRANT_TYPE = "client_credentials";

    private static final Map<String, String> DEFAULT_JSON_HEADER = Map.of("Content-Type", "application/json");

    private final HttpClient httpClient;

    public QianfanClient(String apiKey, String secretKey) {
        this(builder().apiKey(apiKey).secretKey(secretKey));
    }

    private QianfanClient(Builder serviceBuilder) {
        ensureNotNull(serviceBuilder.apiKey, "apiKey");
        ensureNotNull(serviceBuilder.secretKey, "secretKey");
        ensureNotBlank(serviceBuilder.baseUrl, "baseUrl");
        this.apiKey = serviceBuilder.apiKey;
        this.baseUrl = serviceBuilder.baseUrl;
        this.secretKey = serviceBuilder.secretKey;

        HttpClientBuilder httpClientBuilder =
                getOrDefault(serviceBuilder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        if (serviceBuilder.proxy != null) {
            if (httpClientBuilder instanceof JdkHttpClientBuilder jdkHttpClientBuilder) {
                httpClientBuilder = jdkHttpClientBuilder.httpClientBuilder(
                        java.net.http.HttpClient.newBuilder().proxy(new ProxySelector() {
                            @Override
                            public List<Proxy> select(final URI uri) {
                                return List.of(serviceBuilder.proxy);
                            }

                            @Override
                            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
                                // ignored
                            }
                        }));
            } else {
                log.warn(
                        "not support proxy for {}", httpClientBuilder.getClass().getName());
            }
        }
        HttpClient client = httpClientBuilder
                .readTimeout(serviceBuilder.callTimeout)
                .connectTimeout(serviceBuilder.connectTimeout)
                .build();
        if (serviceBuilder.logRequests || serviceBuilder.logResponses || serviceBuilder.logStreamingResponses) {
            this.httpClient = new LoggingHttpClient(
                    client,
                    serviceBuilder.logRequests,
                    serviceBuilder.logResponses || serviceBuilder.logStreamingResponses);
        } else {
            this.httpClient = client;
        }
    }

    public void shutdown() {
        // close okhttp3 before 1.0.0
        // migrate okhttp3 to http-client, so nothing to close.
    }

    public static Builder builder() {
        return new Builder();
    }

    public SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletion(
            ChatCompletionRequest request, String endpoint) {
        refreshToken();
        Map<String, Object> queryParams = Map.of(ACCESS_TOKEN, this.token);
        HttpRequest httpRequest = HttpRequest.builder()
                .url(this.baseUrl, pathWithQuery("rpc/2.0/ai_custom/v1/wenxinworkshop/chat/" + endpoint, queryParams))
                .method(POST)
                .addHeaders(DEFAULT_JSON_HEADER)
                .body(Json.toJson(ChatCompletionRequest.builder().from(request).stream(false)
                        .build()))
                .build();
        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .url(this.baseUrl, pathWithQuery("rpc/2.0/ai_custom/v1/wenxinworkshop/chat/" + endpoint, queryParams))
                .method(POST)
                .addHeaders(DEFAULT_JSON_HEADER)
                .body(Json.toJson(ChatCompletionRequest.builder().from(request).stream(true)
                        .build()))
                .build();

        return new RequestExecutor<>(
                httpClient,
                httpRequest,
                streamingHttpRequest,
                ChatCompletionResponse.class,
                chatCompletionResponseExtractor(),
                chatCompletionResponseExtractor());
    }

    private Function<ChatCompletionResponse, ChatCompletionResponse> chatCompletionResponseExtractor() {
        return r -> {
            if (r.getErrorCode() != null) {
                throw new QianfanApiException(r.getErrorCode(), r.getErrorMsg());
            }
            return r;
        };
    }

    public SyncOrAsyncOrStreaming<CompletionResponse> completion(
            CompletionRequest request, boolean stream, String endpoint) {
        refreshToken();
        Map<String, Object> queryParams = Map.of(ACCESS_TOKEN, this.token);
        HttpRequest httpRequest = HttpRequest.builder()
                .url(
                        this.baseUrl,
                        pathWithQuery("rpc/2.0/ai_custom/v1/wenxinworkshop/completions/" + endpoint, queryParams))
                .method(POST)
                .addHeaders(DEFAULT_JSON_HEADER)
                .body(Json.toJson(
                        CompletionRequest.builder().from(request).stream(false).build()))
                .build();
        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .url(
                        this.baseUrl,
                        pathWithQuery("rpc/2.0/ai_custom/v1/wenxinworkshop/completions/" + endpoint, queryParams))
                .method(POST)
                .addHeaders(DEFAULT_JSON_HEADER)
                .body(Json.toJson(
                        CompletionRequest.builder().from(request).stream(true).build()))
                .build();
        return new RequestExecutor<>(
                httpClient,
                httpRequest,
                streamingHttpRequest,
                CompletionResponse.class,
                completionResponseExtractor(),
                completionResponseExtractor());
    }

    private Function<CompletionResponse, CompletionResponse> completionResponseExtractor() {
        return r -> {
            if (r.getErrorCode() != null) {
                throw new QianfanApiException(r.getErrorCode(), r.getErrorMsg());
            }
            return r;
        };
    }

    public SyncOrAsync<EmbeddingResponse> embedding(EmbeddingRequest request, String serviceName) {
        refreshToken();
        Map<String, Object> queryParams = Map.of(ACCESS_TOKEN, this.token);
        String pathWithQuery =
                pathWithQuery("rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/" + serviceName, queryParams);
        HttpRequest httpRequest = HttpRequest.builder()
                .url(this.baseUrl, pathWithQuery)
                .method(POST)
                .addHeaders(DEFAULT_JSON_HEADER)
                .body(Json.toJson(request))
                .build();
        return new RequestExecutor<>(this.httpClient, httpRequest, EmbeddingResponse.class, r -> r);
    }

    private void refreshToken() {
        Map<String, Object> queryParams =
                Map.of("grant_type", GRANT_TYPE, "client_id", this.apiKey, "client_secret", this.secretKey);
        String pathWithQuery = pathWithQuery("oauth/2.0/token", queryParams);
        HttpRequest httpRequest = HttpRequest.builder()
                .url(this.baseUrl, pathWithQuery)
                .method(GET)
                .addHeaders(DEFAULT_JSON_HEADER)
                .build();
        RequestExecutor<String, ChatTokenResponse, String> executor = new RequestExecutor<>(
                httpClient, httpRequest, ChatTokenResponse.class, ChatTokenResponse::getAccessToken);
        this.token = executor.execute();
    }

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String secretKey;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;
        private Proxy proxy;
        private boolean logRequests;
        private boolean logResponses;
        private boolean logStreamingResponses;

        private Builder() {
            this.baseUrl = "https://aip.baidubce.com/";
            this.callTimeout = Duration.ofSeconds(60L);
            this.connectTimeout = Duration.ofSeconds(60L);
            this.readTimeout = Duration.ofSeconds(60L);
            this.writeTimeout = Duration.ofSeconds(60L);
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                return this;
            } else {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                this.apiKey = apiKey;
                return this;
            } else {
                throw new IllegalArgumentException("apiKey cannot be null or empty. ");
            }
        }

        public Builder secretKey(String secretKey) {
            if (secretKey != null && !secretKey.trim().isEmpty()) {
                this.secretKey = secretKey;
                return this;
            } else {
                throw new IllegalArgumentException("secretKey cannot be null or empty. ");
            }
        }

        public Builder callTimeout(Duration callTimeout) {
            if (callTimeout == null) {
                throw new IllegalArgumentException("callTimeout cannot be null");
            } else {
                this.callTimeout = callTimeout;
                return this;
            }
        }

        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null) {
                throw new IllegalArgumentException("connectTimeout cannot be null");
            } else {
                this.connectTimeout = connectTimeout;
                return this;
            }
        }

        public Builder readTimeout(Duration readTimeout) {
            if (readTimeout == null) {
                throw new IllegalArgumentException("readTimeout cannot be null");
            } else {
                this.readTimeout = readTimeout;
                return this;
            }
        }

        public Builder writeTimeout(Duration writeTimeout) {
            if (writeTimeout == null) {
                throw new IllegalArgumentException("writeTimeout cannot be null");
            } else {
                this.writeTimeout = writeTimeout;
                return this;
            }
        }

        public Builder proxy(Proxy.Type type, String ip, int port) {
            this.proxy = new Proxy(type, new InetSocketAddress(ip, port));
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder logRequests() {
            return this.logRequests(true);
        }

        public Builder logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }

            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses() {
            return this.logResponses(true);
        }

        public Builder logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }

            this.logResponses = logResponses;
            return this;
        }

        public Builder logStreamingResponses() {
            return this.logStreamingResponses(true);
        }

        public Builder logStreamingResponses(Boolean logStreamingResponses) {
            if (logStreamingResponses == null) {
                logStreamingResponses = false;
            }

            this.logStreamingResponses = logStreamingResponses;
            return this;
        }

        public QianfanClient build() {
            return new QianfanClient(this);
        }
    }
}
