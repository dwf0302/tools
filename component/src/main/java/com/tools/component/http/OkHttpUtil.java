package com.tools.component.http;//package com.imss.data.forward.server.common.config.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OkHttp 的 HTTP 调用工具类
 * 支持忽略 SSL、JSON 自动序列化/反序列化
 */
@Slf4j
public class OkHttpUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType FORM_MEDIA_TYPE = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");

    // 默认 OkHttpClient
    private static final OkHttpClient DEFAULT_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build();

    // 忽略 SSL 验证的 OkHttpClient
    private static final OkHttpClient UNCHECKED_SSL_CLIENT = createUnsafeClient();

    // 创建忽略 SSL 的 OkHttpClient
    private static OkHttpClient createUnsafeClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                    .retryOnConnectionFailure(true)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("创建忽略SSL的OkHttpClient失败", e);
        }
    }

    public static <T> Builder<T> builder(String url, Class<T> responseType) {
        return new Builder<>(url, responseType);
    }

    public static class Builder<T> {
        private final String url;
        private final Class<T> responseType;

        private final Map<String, String> queryParams = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private Object requestBody = null;
        private boolean ignoreSSL = false;
        private int connectTimeout = 15;
        private int readTimeout = 15;
        private int writeTimeout = 15;
        private MediaType contentType = JSON_MEDIA_TYPE;

        public Builder(String url, Class<T> responseType) {
            this.url = url;
            this.responseType = responseType;
        }

        public Builder<T> queryParam(String key, String value) {
            this.queryParams.put(key, value);
            return this;
        }

        public Builder<T> queryParams(Map<String, String> params) {
            if (params != null) {
                this.queryParams.putAll(params);
            }
            return this;
        }

        public Builder<T> header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder<T> headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Builder<T> authorization(String token) {
            return header("Authorization", "Bearer " + token);
        }

        public Builder<T> basicAuth(String username, String password) {
            String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            return header("Authorization", "Basic " + credentials);
        }

        public Builder<T> body(Object body) {
            this.requestBody = body;
            return this;
        }

        public Builder<T> jsonBody(Object body) {
            this.requestBody = body;
            this.contentType = JSON_MEDIA_TYPE;
            return this;
        }

        public Builder<T> formBody(Map<String, String> formData) {
            this.requestBody = formData;
            this.contentType = FORM_MEDIA_TYPE;
            return this;
        }

        public Builder<T> ignoreSSL(boolean ignoreSSL) {
            this.ignoreSSL = ignoreSSL;
            return this;
        }

        public Builder<T> connectTimeout(int seconds) {
            this.connectTimeout = seconds;
            return this;
        }

        public Builder<T> readTimeout(int seconds) {
            this.readTimeout = seconds;
            return this;
        }

        public Builder<T> writeTimeout(int seconds) {
            this.writeTimeout = seconds;
            return this;
        }

        // 执行请求
        private T execute(String method) {
            OkHttpClient baseClient = ignoreSSL ? UNCHECKED_SSL_CLIENT : DEFAULT_CLIENT;

            // 如果需要自定义超时，创建新的客户端
            OkHttpClient client = baseClient;
            if (connectTimeout != 15 || readTimeout != 15 || writeTimeout != 15) {
                client = baseClient.newBuilder()
                        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                        .readTimeout(readTimeout, TimeUnit.SECONDS)
                        .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                        .build();
            }

            try {
                HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
                queryParams.forEach(urlBuilder::addQueryParameter);
                HttpUrl finalUrl = urlBuilder.build();

                Request.Builder requestBuilder = new Request.Builder().url(finalUrl);

                // 设置请求头
                headers.forEach(requestBuilder::addHeader);

                // 构造请求体
                RequestBody body = buildRequestBody();

                // 根据 method 设置请求类型
                switch (method.toUpperCase()) {
                    case "GET":
                        requestBuilder.get();
                        break;
                    case "POST":
                        requestBuilder.post(body != null ? body : RequestBody.create("", null));
                        break;
                    case "PUT":
                        requestBuilder.put(body != null ? body : RequestBody.create("", null));
                        break;
                    case "DELETE":
                        if (body != null) {
                            requestBuilder.delete(body);
                        } else {
                            requestBuilder.delete();
                        }
                        break;
                    case "PATCH":
                        requestBuilder.patch(body != null ? body : RequestBody.create("", null));
                        break;
                    default:
                        throw new IllegalArgumentException("不支持的HTTP方法: " + method);
                }

                Request request = requestBuilder.build();

                log.info("OkHttp请求: method={}, url={}, headers={}", method, finalUrl, headers);
                if (requestBody != null) {
                    log.debug("请求体: {}", requestBody);
                }

                try (Response response = client.newCall(request).execute()) {
                    return handleResponse(response);
                }

            } catch (Exception e) {
                log.error("HTTP请求失败: method={}, url={}, error={}", method, url, e.getMessage(), e);
                throw new RuntimeException("HTTP请求执行失败", e);
            }
        }

        private RequestBody buildRequestBody() throws Exception {
            if (requestBody == null) {
                return null;
            }

            if (requestBody instanceof String) {
                return RequestBody.create((String) requestBody, contentType);
            } else if (requestBody instanceof Map && contentType == FORM_MEDIA_TYPE) {
                // 表单数据
                FormBody.Builder formBuilder = new FormBody.Builder();
                @SuppressWarnings("unchecked")
                Map<String, String> formData = (Map<String, String>) requestBody;
                formData.forEach(formBuilder::add);
                return formBuilder.build();
            } else {
                // JSON序列化
                String json = OBJECT_MAPPER.writeValueAsString(requestBody);
                return RequestBody.create(json, JSON_MEDIA_TYPE);
            }
        }

        private T handleResponse(Response response) throws Exception {
            if (!response.isSuccessful()) {
                String errorBody = "";
                if (response.body() != null) {
                    errorBody = response.body().string();
                }
                throw new RuntimeException(String.format("HTTP请求失败: code=%d, message=%s, body=%s",
                        response.code(), response.message(), errorBody));
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return null;
            }

            String respString = responseBody.string();
            log.debug("响应内容: {}", respString);

            if (responseType == Void.class || responseType == void.class) {
                return null;
            } else if (responseType == String.class) {
                return responseType.cast(respString);
            } else if (responseType == byte[].class) {
                return responseType.cast(respString.getBytes());
            } else {
                return OBJECT_MAPPER.readValue(respString, responseType);
            }
        }

        // 快捷调用方法
        public T get() {
            return execute("GET");
        }

        public T post() {
            return execute("POST");
        }

        public T put() {
            return execute("PUT");
        }

        public T delete() {
            return execute("DELETE");
        }

        public T patch() {
            return execute("PATCH");
        }
    }
}