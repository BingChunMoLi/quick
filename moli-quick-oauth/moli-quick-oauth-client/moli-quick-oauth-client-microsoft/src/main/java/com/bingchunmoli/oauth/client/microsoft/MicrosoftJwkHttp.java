package com.bingchunmoli.oauth.client.microsoft;

import java.net.http.HttpClient;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Dedicated transport so unrelated RestClient factory settings cannot remove JWKS timeouts. */
final class MicrosoftJwkHttp {
    private MicrosoftJwkHttp() { }
    static ClientHttpRequestFactory requestFactory(MicrosoftOAuth2Properties properties) {
        properties.validateTimeouts();
        var client = HttpClient.newBuilder().connectTimeout(properties.getJwkConnectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getJwkReadTimeout());
        return factory;
    }
}
