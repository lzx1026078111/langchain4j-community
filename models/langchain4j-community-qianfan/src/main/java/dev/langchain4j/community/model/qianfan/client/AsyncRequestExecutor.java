package dev.langchain4j.community.model.qianfan.client;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.function.Consumer;
import java.util.function.Function;

public class AsyncRequestExecutor<Response, ResponseContent> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Class<Response> responseClass;
    private final Function<Response, ResponseContent> responseContentExtractor;

    AsyncRequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            Class<Response> responseClass,
            Function<Response, ResponseContent> responseContentExtractor) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.responseClass = responseClass;
        this.responseContentExtractor = responseContentExtractor;
    }

    AsyncResponseHandling onResponse(final Consumer<ResponseContent> responseHandler) {
        return new AsyncResponseHandling() {
            public ErrorHandling onError(final Consumer<Throwable> errorHandler) {
                return () -> {
                    try {
                        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
                        Response response = Json.fromJson(successfulHttpResponse.body(), responseClass);
                        ResponseContent responseContent = responseContentExtractor.apply(response);
                        responseHandler.accept(responseContent);
                    } catch (Exception e) {
                        errorHandler.accept(e);
                    }
                };
            }

            public ErrorHandling ignoreErrors() {
                return () -> {
                    try {
                        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
                        Response response = Json.fromJson(successfulHttpResponse.body(), responseClass);
                        ResponseContent responseContent = responseContentExtractor.apply(response);
                        responseHandler.accept(responseContent);
                    } catch (Exception e) {
                        // ignored
                    }
                };
            }
        };
    }
}
