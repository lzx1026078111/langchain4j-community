package dev.langchain4j.community.model.qianfan.client;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 *
 * For normal situations, will return {@code "data: xxx"}".
 * For exception,it will not start with "data:", so need to process this situation.
 * <p>
 * expamle: {@code {"error_code":17,"error_msg":"Open api daily request limit reached"}}
 */
class QianfanServerSentEventParser implements ServerSentEventParser {
    @Override
    public void parse(final InputStream httpResponseBody, final ServerSentEventListener listener) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponseBody))) {

            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        ServerSentEvent sse = new ServerSentEvent(null, data.toString());
                        ignoringExceptions(() -> listener.onEvent(sse));
                        data.setLength(0);
                    }
                    continue;
                }

                String content;
                if (line.startsWith("data:")) {
                    content = line.substring("data:".length());
                } else {
                    content = line;
                }
                if (!data.isEmpty()) {
                    data.append("\n");
                }
                data.append(content.trim());
            }

            if (!data.isEmpty()) {
                ServerSentEvent sse = new ServerSentEvent(null, data.toString());
                ignoringExceptions(() -> listener.onEvent(sse));
            }
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }
}
