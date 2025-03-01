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
package io.micronaut.web.router;

import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.HttpFilter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * <p>The default {@link Router} implementation. This implementation does not perform any additional caching of
 * route discovery.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultRouter implements Router {

    private final Map<String, List<UriRoute>> routesByMethod = new HashMap();
    private final Set<StatusRoute> statusRoutes = new HashSet<>();
    private final Collection<FilterRoute> filterRoutes = new ArrayList<>();
    private final Set<ErrorRoute> errorRoutes = new HashSet<>();

    /**
     * Construct a new router for the given route builders.
     *
     * @param builders The builders
     */
    @Inject
    public DefaultRouter(Collection<RouteBuilder> builders) {
        for (RouteBuilder builder : builders) {
            List<UriRoute> constructedRoutes = builder.getUriRoutes();
            for (UriRoute route : constructedRoutes) {
                String methodName = route.getHttpMethodName();
                routesByMethod.computeIfAbsent(methodName, x -> new ArrayList()).add(route);
            }

            this.statusRoutes.addAll(builder.getStatusRoutes());
            this.errorRoutes.addAll(builder.getErrorRoutes());
            this.filterRoutes.addAll(builder.getFilterRoutes());
        }

        routesByMethod.values().forEach(this::finalizeRoutes);
    }

    /**
     * Construct a new router for the given route builders.
     *
     * @param builders The builders
     */
    public DefaultRouter(RouteBuilder... builders) {
        this(Arrays.asList(builders));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri) {
        return find(httpMethod.name(), uri);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest request, CharSequence uri) {
        return find(request.getMethodName(), uri);
    }

    private <T, R> Stream<UriRouteMatch<T, R>> find(String httpMethodName, CharSequence uri) {
        List<UriRoute> routes = routesByMethod.getOrDefault(httpMethodName, new ArrayList<>());
        return routes.stream()
                .map((route -> route.match(uri.toString())))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Override
    public Stream<UriRoute> uriRoutes() {
        return routesByMethod.values().stream().flatMap(List::stream);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        List<UriRoute> routes = routesByMethod.get(httpMethod.name());
        Optional<UriRouteMatch> result = routes.stream()
            .map((route -> route.match(uri.toString())))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

        UriRouteMatch match = result.orElse(null);
        return Optional.ofNullable(match);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(HttpStatus status) {
        for (StatusRoute statusRoute : statusRoutes) {
            if (statusRoute.originatingType() == null) {
                Optional<RouteMatch<R>> match = statusRoute.match(status);
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class originatingClass, HttpStatus status) {
        for (StatusRoute statusRoute : statusRoutes) {
            Optional<RouteMatch<R>> match = statusRoute.match(originatingClass, status);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class originatingClass, Throwable error) {
        Map<ErrorRoute, RouteMatch<R>> matchedRoutes = new LinkedHashMap<>();
        for (ErrorRoute errorRoute : errorRoutes) {
            Optional<RouteMatch<R>> match = errorRoute.match(originatingClass, error);
            match.ifPresent((m) -> {
                matchedRoutes.put(errorRoute, m);
            });
        }
        return findRouteMatch(matchedRoutes, error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Throwable error) {
        Map<ErrorRoute, RouteMatch<R>> matchedRoutes = new LinkedHashMap<>();
        for (ErrorRoute errorRoute : errorRoutes) {
            if (errorRoute.originatingType() == null) {
                Optional<RouteMatch<R>> match = errorRoute.match(error);
                match.ifPresent((m) -> matchedRoutes.put(errorRoute, m));
            }
        }

        return findRouteMatch(matchedRoutes, error);
    }

    @Override
    public List<HttpFilter> findFilters(HttpRequest<?> request) {
        List<HttpFilter> httpFilters = new ArrayList<>();
        HttpMethod method = request.getMethod();
        URI uri = request.getUri();
        for (FilterRoute filterRoute : filterRoutes) {
            Optional<HttpFilter> match = filterRoute.match(method, uri);
            match.ifPresent(httpFilters::add);
        }
        if (!httpFilters.isEmpty()) {
            OrderUtil.sort(httpFilters);
            return Collections.unmodifiableList(httpFilters);
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri) {
        return uriRoutes()
            .filter(Objects::nonNull)
            .map(route -> route.match(uri.toString()))
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    private void finalizeRoutes(List<UriRoute> routes) {
        Collections.sort(routes);
        Collections.reverse(routes);
    }

    private <T> Optional<RouteMatch<T>> findRouteMatch(Map<ErrorRoute, RouteMatch<T>> matchedRoutes, Throwable error) {
        if (matchedRoutes.size() == 1) {
            return matchedRoutes.values().stream().findFirst();
        } else if (matchedRoutes.size() > 1) {
            int minCount = Integer.MAX_VALUE;

            Supplier<List<Class>> hierarchySupplier = () -> ClassUtils.resolveHierarchy(error.getClass());
            Optional<RouteMatch<T>> match = Optional.empty();
            Class errorClass = error.getClass();

            for (Map.Entry<ErrorRoute, RouteMatch<T>> entry: matchedRoutes.entrySet()) {
                Class exceptionType = entry.getKey().exceptionType();
                if (exceptionType.equals(errorClass)) {
                    match = Optional.of(entry.getValue());
                    break;
                } else {
                    List<Class> hierarchy = hierarchySupplier.get();
                    //measures the distance in the hierarchy from the error and the route error type
                    int index = hierarchy.indexOf(exceptionType);
                    //the class closest in the hierarchy should be chosen
                    if (index > -1 && index < minCount) {
                        minCount = index;
                        match = Optional.of(entry.getValue());
                    }
                }
            }

            return match;
        }
        return Optional.empty();
    }
}
