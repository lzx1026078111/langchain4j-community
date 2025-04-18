package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.toTools;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.toZhipuAiMessages;
import static dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel.GLM_4_FLASH;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.zhipu.chat.ToolChoiceMode;
import dev.langchain4j.community.model.zhipu.spi.ZhipuAiStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZhipuAiStreamingChatModel implements StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(ZhipuAiStreamingChatModel.class);

    private final Double temperature;
    private final Double topP;
    private final String model;
    private final List<String> stops;
    private final Integer maxToken;
    private final ZhipuAiClient client;
    private final List<ChatModelListener> listeners;

    public ZhipuAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            List<String> stops,
            String model,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses,
            List<ChatModelListener> listeners,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout) {
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.stops = stops;
        this.model = getOrDefault(model, GLM_4_FLASH.toString());
        this.maxToken = getOrDefault(maxToken, 512);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = request.messages();
        List<ToolSpecification> toolSpecifications = request.toolSpecifications();

        ChatCompletionRequest.Builder requestBuilder =
                ChatCompletionRequest.builder().model(this.model).maxTokens(this.maxToken).stream(true)
                        .stop(stops)
                        .topP(this.topP)
                        .temperature(this.temperature)
                        .toolChoice(ToolChoiceMode.AUTO)
                        .messages(toZhipuAiMessages(messages));

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        ChatCompletionRequest completionRequest = requestBuilder.build();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, provider(), attributes);
        for (ChatModelListener chatModelListener : listeners) {
            try {
                chatModelListener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        }

        client.streamingChatCompletion(completionRequest, handler, listeners, requestContext, provider());
    }

    public static ZhipuAiStreamingChatModelBuilder builder() {
        for (ZhipuAiStreamingChatModelBuilderFactory factories :
                loadFactories(ZhipuAiStreamingChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiStreamingChatModelBuilder();
    }

    public static class ZhipuAiStreamingChatModelBuilder {

        private String baseUrl;
        private String apiKey;
        private Double temperature;
        private Double topP;
        private List<String> stops;
        private String model;
        private Integer maxToken;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;

        public ZhipuAiStreamingChatModelBuilder model(ChatCompletionModel model) {
            this.model = model.toString();
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder model(String model) {
            ValidationUtils.ensureNotBlank(model, "model");
            this.model = model;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder maxToken(Integer maxToken) {
            this.maxToken = maxToken;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public ZhipuAiStreamingChatModel build() {
            return new ZhipuAiStreamingChatModel(
                    this.baseUrl,
                    this.apiKey,
                    this.temperature,
                    this.topP,
                    this.stops,
                    this.model,
                    this.maxToken,
                    this.logRequests,
                    this.logResponses,
                    this.listeners,
                    this.callTimeout,
                    this.connectTimeout,
                    this.readTimeout,
                    this.writeTimeout);
        }
    }
}
