/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.micronaut.http.RequestBodyRequired.CUSTOM;
import static io.micronaut.http.RequestBodyRequired.NONE;
import static io.micronaut.http.RequestBodyRequired.PERMITTED;
import static io.micronaut.http.RequestBodyRequired.REQUIRED;

/**
 * An enum containing the valid HTTP methods. See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class HttpMethod implements CharSequence {
    private final String name;
    private final RequestBodyRequired requestBodyRequired;

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.2.
     */
    public static final HttpMethod OPTIONS = new HttpMethod("OPTIONS", PERMITTED);

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3.
     */
    public static final HttpMethod GET = new HttpMethod("GET", NONE);

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4.
     */
    public static final HttpMethod HEAD = new HttpMethod("HEAD", NONE);

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5.
     */
    public static final HttpMethod POST = new HttpMethod("POST", RequestBodyRequired.REQUIRED);

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6.
     */
    public static final HttpMethod PUT = new HttpMethod("PUT", RequestBodyRequired.REQUIRED);

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7.
     */
    public static final HttpMethod DELETE = new HttpMethod("DELETE", PERMITTED);

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.8.
     */
    public static final HttpMethod TRACE = new HttpMethod("NONE", NONE);

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.9.
     */
    public static final HttpMethod CONNECT = new HttpMethod("CONNECT", NONE);

    /**
     * See https://tools.ietf.org/html/rfc5789.
     */
    public static final HttpMethod PATCH = new HttpMethod("PATCH", RequestBodyRequired.REQUIRED);

    private static final Map<String, HttpMethod> methodMap;

    static {
        methodMap = new HashMap<>();
        Stream.of(OPTIONS, GET, HEAD, POST, PUT, PATCH, DELETE, TRACE, CONNECT)
                .forEach(method -> methodMap.put(method.name, method));
    }

    public static HttpMethod valueOf(String method) {
        if (method == null) {
            throw new NullPointerException("method cannot be null");
        }

        method = method.toUpperCase();

        if (methodMap.containsKey(method)) {
            return methodMap.get(method);
        }

        // So this is a custom method not included in HTTP protocol, perhaps a WEBDAV
        return new HttpMethod(method, CUSTOM);
    }


    private HttpMethod(String name, RequestBodyRequired requestBodyRequired) {
        this.name = name.toUpperCase();
        this.requestBodyRequired = requestBodyRequired;
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return name.subSequence(start, end);
    }

    /**
     * Whether the given method requires a request body.
     *
     * @param method The {@link HttpMethod}
     * @return True if it does
     */
    public static boolean requiresRequestBody(HttpMethod method) {
        return method != null && method.requestBodyRequired == REQUIRED;
    }

    /**
     * Whether the given method allows a request body.
     *
     * @param method The {@link HttpMethod}
     * @return True if it does
     */
    public static boolean permitsRequestBody(HttpMethod method) {
        return method != null && (requiresRequestBody(method) || method.requestBodyRequired == PERMITTED);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HttpMethod && name.equals(((HttpMethod) obj).name);
    }

    public String name() {
        return name;
    }
}
