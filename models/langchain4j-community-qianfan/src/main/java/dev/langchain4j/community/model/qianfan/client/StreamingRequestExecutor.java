package dev.langchain4j.community.model.qianfan.client;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.function.Consumer;
import java.util.function.Function;

public class StreamingRequestExecutor<Request, Response, ResponseContent> {

    private final Class<Response> responseClass;
    private final Function<Response, ResponseContent> streamEventContentExtractor;

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;

    StreamingRequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            Class<Response> responseClass,
            Function<Response, ResponseContent> streamEventContentExtractor) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.responseClass = responseClass;
        this.streamEventContentExtractor = streamEventContentExtractor;
    }

    StreamingResponseHandling onPartialResponse(final Consumer<ResponseContent> partialResponseHandler) {
        return new StreamingResponseHandling() {
            public StreamingCompletionHandling onComplete(final Runnable runnable) {
                return new StreamingCompletionHandling() {
                    public ErrorHandling onError(final Consumer<Throwable> errorHandler) {
                        return () ->
                                StreamingRequestExecutor.this.stream(partialResponseHandler, runnable, errorHandler);
                    }

                    public ErrorHandling ignoreErrors() {
                        return () -> StreamingRequestExecutor.this.stream(partialResponseHandler, runnable, e -> {});
                    }
                };
            }

            public ErrorHandling onError(final Consumer<Throwable> errorHandler) {
                return new ErrorHandling() {
                    public void execute() {
                        StreamingRequestExecutor.this.stream(partialResponseHandler, () -> {}, errorHandler);
                    }
                };
            }

            public ErrorHandling ignoreErrors() {
                return new ErrorHandling() {
                    public void execute() {
                        StreamingRequestExecutor.this.stream(partialResponseHandler, () -> {}, (e) -> {});
                    }
                };
            }
        };
    }

    private void stream(
            final Consumer<ResponseContent> partialResponseHandler,
            final Runnable streamingCompletionCallback,
            final Consumer<Throwable> errorHandler) {
        ServerSentEventListener eventSourceListener = new ServerSentEventListener() {
            @Override
            public void onEvent(final ServerSentEvent event) {
                String data = event.data();
                if ("[DONE]".equals(data)) {
                    streamingCompletionCallback.run();
                } else {
                    try {
                        Response response = Json.fromJson(data, StreamingRequestExecutor.this.responseClass);
                        ResponseContent responseContent =
                                StreamingRequestExecutor.this.streamEventContentExtractor.apply(response);
                        if (responseContent != null) {
                            partialResponseHandler.accept(responseContent);
                        }
                    } catch (Exception var7) {
                        errorHandler.accept(var7);
                    }
                }
            }

            @Override
            public void onError(final Throwable throwable) {
                errorHandler.accept(throwable);
            }

            @Override
            public void onClose() {
                streamingCompletionCallback.run();
            }
        };

        httpClient.execute(httpRequest, new QianfanServerSentEventParser(), eventSourceListener);
    }
}
