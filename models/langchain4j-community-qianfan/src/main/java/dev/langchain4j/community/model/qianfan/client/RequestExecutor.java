package dev.langchain4j.community.model.qianfan.client;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import java.util.function.Consumer;
import java.util.function.Function;

public class RequestExecutor<Request, Response, ResponseContent> implements SyncOrAsyncOrStreaming<ResponseContent> {

    private final Function<Response, ResponseContent> responseContentExtractor;

    private final Class<Response> responseClass;
    private final Function<Response, ResponseContent> streamEventContentExtractor;

    private final HttpClient httpClient;

    private final HttpRequest httpRequest;

    private final HttpRequest streamingHttpRequest;

    public RequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            Class<Response> responseClass,
            Function<Response, ResponseContent> responseContentExtractor) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.streamingHttpRequest = null;
        this.responseClass = responseClass;
        this.responseContentExtractor = responseContentExtractor;
        this.streamEventContentExtractor = null;
    }

    public RequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            HttpRequest streamingHttpRequest,
            Class<Response> responseClass,
            Function<Response, ResponseContent> responseContentExtractor,
            Function<Response, ResponseContent> streamEventContentExtractor) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.streamingHttpRequest = streamingHttpRequest;
        this.responseClass = responseClass;
        this.responseContentExtractor = responseContentExtractor;
        this.streamEventContentExtractor = streamEventContentExtractor;
    }

    public ResponseContent execute() {
        SyncRequestExecutor<Response, ResponseContent> executor = new SyncRequestExecutor<>(
                this.httpClient, this.httpRequest, this.responseClass, this.responseContentExtractor);
        return executor.execute();
    }

    public AsyncResponseHandling onResponse(Consumer<ResponseContent> responseContentConsumer) {
        return new AsyncRequestExecutor<>(
                        this.httpClient, this.httpRequest, this.responseClass, this.responseContentExtractor)
                .onResponse(responseContentConsumer);
    }

    public StreamingResponseHandling onPartialResponse(Consumer<ResponseContent> responseContentConsumer) {
        return new StreamingRequestExecutor<>(
                        this.httpClient,
                        this.streamingHttpRequest,
                        this.responseClass,
                        this.streamEventContentExtractor)
                .onPartialResponse(responseContentConsumer);
    }
}
