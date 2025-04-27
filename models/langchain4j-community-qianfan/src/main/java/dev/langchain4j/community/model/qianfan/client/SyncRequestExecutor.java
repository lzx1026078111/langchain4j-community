package dev.langchain4j.community.model.qianfan.client;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.function.Function;

public class SyncRequestExecutor<Response, ResponseContent> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Class<Response> responseClass;
    private final Function<Response, ResponseContent> responseContentExtractor;

    public SyncRequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            Class<Response> responseClass,
            Function<Response, ResponseContent> responseContentExtractor) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.responseClass = responseClass;
        this.responseContentExtractor = responseContentExtractor;
    }

    public ResponseContent execute() {
        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        Response response = Json.fromJson(successfulHttpResponse.body(), responseClass);
        return this.responseContentExtractor.apply(response);
    }
}
