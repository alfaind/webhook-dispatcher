package com.rbkmoney.webhook.dispatcher.service;

import com.rbkmoney.kafka.common.exception.RetryableException;
import com.rbkmoney.webhook.dispatcher.WebhookMessage;
import com.rbkmoney.webhook.dispatcher.exception.CantRetryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebHookDispatcherServiceImpl implements WebHookDispatcherService {

    public static final int RESPONSE_MAX_LENGTH = 4096;

    private final CloseableHttpClient client;

    @Override
    public int dispatch(WebhookMessage webhookMessage) throws IOException {
        log.info("WebHookDispatcherServiceImpl dispatch hook: {} ", webhookMessage);
        HttpPost post = new HttpPost(webhookMessage.getUrl());
        post.setEntity(new ByteArrayEntity(webhookMessage.getRequestBody()));
        post.setHeader(CONTENT_TYPE, webhookMessage.getContentType());
        try (CloseableHttpResponse response = client.execute(post)) {
            webhookMessage.getAdditionalHeaders()
                    .forEach(post::addHeader);
            int statusCode = response.getStatusLine().getStatusCode();
            byte[] bufferAsByteArray = new byte[RESPONSE_MAX_LENGTH];
            IOUtils.read(response.getEntity().getContent(), bufferAsByteArray, 0, RESPONSE_MAX_LENGTH);
            log.info("Response from hook: messageId: {}, code: {}; body: {}", webhookMessage.getSourceId(),
                    statusCode, Arrays.toString(bufferAsByteArray));
            if (HttpStatus.valueOf(statusCode).is2xxSuccessful()) {
                return statusCode;
            } else if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()) {
                log.warn("Timeout error when send webhook: {}", webhookMessage);
                throw new RetryableException(HttpStatus.REQUEST_TIMEOUT.getReasonPhrase());
            } else {
                log.warn("Cant retry error when send webhook: {}", webhookMessage);
                throw new CantRetryException("Cant retry webhook!");
            }
        } catch (SocketTimeoutException e) {
            log.warn("Timeout error when send webhook: {}", webhookMessage);
            throw new RetryableException(e);
        }
    }

}
