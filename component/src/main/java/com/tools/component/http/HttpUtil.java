package com.tools.component.http;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * @description:
 * @author: Dwf
 * @date: 2025-07-15
 *
 * 依赖：
 * 	<dependency>
 * 	    <groupId>org.apache.httpcomponents.client5</groupId>
 * 		<artifactId>httpclient5</artifactId>
 * 		<version>5.2.1</version>
 * 	</dependency>
 *
 * 	<dependency>
 * 		<groupId>org.apache.httpcomponents.core5</groupId>
 * 		<artifactId>httpcore5</artifactId>
 * 		<version>5.2.1</version>
 * 	</dependency>
 **/
@Slf4j
public class HttpUtil {

    private static final RestTemplate restTemplate = new RestTemplate();

    static {
        // 设置默认的 RequestFactory
        restTemplate.setRequestFactory(simpleClientHttpRequestFactory());
    }

    public static ClientHttpRequestFactory simpleClientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setConnectionRequestTimeout(5000);
        return factory;
    }

    public static HttpComponentsClientHttpRequestFactory generateHttpRequestFactory() {
        try {
            CloseableHttpClient httpclient = HttpClients.custom()
                    .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                                    .setSslContext(SSLContextBuilder.create()
                                            .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                                            .build())
                                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                    .build())
                            .build())
                    .build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
            factory.setHttpClient(httpclient);
            return factory;
        } catch (Exception e) {
            log.error("获取忽略证书的Http调用工厂失败", e);
            throw new RuntimeException(e);
        }
    }

    public static <T> Builder<T> builder(String url, Class<T> responseType) {
        return new Builder<>(url, responseType);
    }

    public static abstract class BaseRequestBuilder<T, B extends BaseRequestBuilder<T, B>> {
        protected final String url;
        protected final Class<T> responseType;
        protected final HttpHeaders headers;
        protected Object requestBody;
        protected Map<String, String> queryParams;
        protected int connectTimeout = 15000;
        protected int readTimeout = 15000;
        protected boolean ignoreSSL = false;

        protected BaseRequestBuilder(String url, Class<T> responseType) {
            this.url = url;
            this.responseType = responseType;
            this.headers = new HttpHeaders();
            this.headers.setContentType(MediaType.APPLICATION_JSON);
        }

        @SuppressWarnings("unchecked")
        public B body(Object body) {
            this.requestBody = body;
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B header(String name, String value) {
            this.headers.set(name, value);
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B contentType(MediaType contentType) {
            this.headers.setContentType(contentType);
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B accept(MediaType... acceptableMediaTypes) {
            this.headers.setAccept(Collections.singletonList(acceptableMediaTypes[0]));
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B queryParams(Map<String, String> params) {
            this.queryParams = params;
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B connectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B ignoreSSL(boolean ignoreSSL) {
            this.ignoreSSL = ignoreSSL;
            return (B) this;
        }

        public abstract T execute(HttpMethod method);

        protected String buildUrl() {
            if (queryParams == null || queryParams.isEmpty()) {
                return url;
            }

            StringBuilder urlBuilder = new StringBuilder(url);
            if (!url.contains("?")) {
                urlBuilder.append("?");
            } else if (!url.endsWith("&") && !url.endsWith("?")) {
                urlBuilder.append("&");
            }

            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                String encodedKey = UriUtils.encode(entry.getKey(), StandardCharsets.UTF_8);
                String encodedValue = UriUtils.encode(entry.getValue(), StandardCharsets.UTF_8);
                urlBuilder.append(encodedKey).append("=").append(encodedValue).append("&");
            }

            return urlBuilder.substring(0, urlBuilder.length() - 1);
        }
    }

    public static class Builder<T> extends BaseRequestBuilder<T, Builder<T>> {

        private Builder(String url, Class<T> responseType) {
            super(url, responseType);
        }

        public T post() {
            return execute(HttpMethod.POST);
        }

        public T get() {
            return execute(HttpMethod.GET);
        }

        public T put() {
            return execute(HttpMethod.PUT);
        }

        public T delete() {
            return execute(HttpMethod.DELETE);
        }

        @Override
        public T execute(HttpMethod method) {
            try {
                // 创建一个临时的 RestTemplate 实例来处理不同的配置
                RestTemplate currentRestTemplate = new RestTemplate();

                // 根据是否忽略SSL设置不同的RequestFactory
                if (ignoreSSL) {
                    currentRestTemplate.setRequestFactory(generateHttpRequestFactory());
                } else {
                    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                    factory.setConnectTimeout(connectTimeout);
                    factory.setConnectionRequestTimeout(readTimeout);
                    currentRestTemplate.setRequestFactory(factory);
                }

                String finalUrl = buildUrl();

                log.info("发送HTTP请求: method={}, url={}, headers={}, body={}",
                        method, finalUrl, headers, requestBody);

                ResponseEntity<T> response = currentRestTemplate.exchange(
                        finalUrl,
                        method,
                        new HttpEntity<>(requestBody, headers),
                        responseType
                );

                return responseType == Void.class ? null : response.getBody();
            } catch (RestClientException e) {
                log.error("HTTP request failed: method={}, url={}, error={}", method, url, e.getMessage(), e);
                throw e;
            }
        }
    }
}
