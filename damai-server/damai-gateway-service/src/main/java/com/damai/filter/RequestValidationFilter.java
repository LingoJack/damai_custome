package com.damai.filter;


import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.conf.RequestTemporaryWrapper;
import com.damai.enums.BaseCode;
import com.damai.exception.ArgumentError;
import com.damai.exception.ArgumentException;
import com.damai.exception.DaMaiFrameException;
import com.damai.pro.limit.RateLimiter;
import com.damai.pro.limit.RateLimiterProperty;
import com.damai.property.GatewayProperty;
import com.damai.service.ApiRestrictService;
import com.damai.service.ChannelDataService;
import com.damai.service.TokenService;
import com.damai.threadlocal.BaseParameterHolder;
import com.damai.util.RsaSignTool;
import com.damai.util.RsaTool;
import com.damai.util.StringUtil;
import com.damai.vo.GetChannelDataVo;
import com.damai.vo.UserVo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.damai.constant.Constant.GRAY_PARAMETER;
import static com.damai.constant.Constant.TRACE_ID;
import static com.damai.constant.GatewayConstant.BUSINESS_BODY;
import static com.damai.constant.GatewayConstant.CODE;
import static com.damai.constant.GatewayConstant.ENCRYPT;
import static com.damai.constant.GatewayConstant.NO_VERIFY;
import static com.damai.constant.GatewayConstant.REQUEST_BODY;
import static com.damai.constant.GatewayConstant.TOKEN;
import static com.damai.constant.GatewayConstant.USER_ID;
import static com.damai.constant.GatewayConstant.V2;
import static com.damai.constant.GatewayConstant.VERIFY_VALUE;

@Component
@Slf4j
public class RequestValidationFilter implements GlobalFilter, Ordered {

    /**
     * 用于配置服务器编解码器，以便在数据传输时进行编码和解码
     */
    @Resource
    private ServerCodecConfigurer serverCodecConfigurer;

    /**
     * 管理频道数据的服务，提供与频道相关的数据操作
     */
    @Autowired
    private ChannelDataService channelDataService;

    /**
     * 提供API访问限制的服务，用于防止未经授权的API调用
     */
    @Autowired
    private ApiRestrictService apiRestrictService;

    /**
     * 负责生成和验证令牌的服务，通常用于身份验证和授权
     */
    @Autowired
    private TokenService tokenService;

    /**
     * 网关属性配置，包含网关运行所需的各种配置信息
     */
    @Autowired
    private GatewayProperty gatewayProperty;

    /**
     * 全局唯一标识符生成器，用于生成唯一的UID
     */
    @Autowired
    private UidGenerator uidGenerator;

    /**
     * 速率限制属性配置，定义了速率限制的规则和参数
     */
    @Autowired
    private RateLimiterProperty rateLimiterProperty;

    /**
     * 速率限制器，用于根据配置的规则限制请求的速率，防止过载
     */
    @Autowired
    private RateLimiter rateLimiter;

    /**
     * 自定义网关过滤器的过滤方法
     * 该方法用于处理进入网关的请求，可以在此处添加跨切面的逻辑，如日志记录、权限校验等
     *
     * @param exchange 服务器Web交换对象，包含请求和响应的信息
     * @param chain    网关过滤链，用于将请求传递到下一个过滤器或最终的目标服务
     * @return Mono<Void> 表示异步操作的完成
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        // 判断是否开启速率限制功能
        if (rateLimiterProperty.getRateSwitch()) {
            try {
                // 尝试获取速率限制许可，如果获取成功则继续执行过滤逻辑
                rateLimiter.acquire();
                return doFilter(exchange, chain);
            }
            catch (InterruptedException e) {
                // 如果获取许可时被中断，则记录错误日志并抛出自定义异常
                log.error("interrupted error", e);
                throw new DaMaiFrameException(BaseCode.THREAD_INTERRUPTED);
            }
            finally {
                // 释放速率限制许可，确保公平性
                rateLimiter.release();
            }
        }
        else {
            // 如果未开启速率限制，则直接执行过滤逻辑
            return doFilter(exchange, chain);
        }
    }

    /**
     * 实际执行过滤逻辑的方法
     * 该方法主要负责处理请求头的传递和请求体的读取
     *
     * @param exchange 服务器Web交换对象，包含请求和响应的信息
     * @param chain    网关过滤链，用于将请求传递到下一个过滤器或最终的目标服务
     * @return Mono<Void> 表示异步操作的完成
     */
    public Mono<Void> doFilter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        // 获取请求对象
        ServerHttpRequest request = exchange.getRequest();
        // 从请求头中获取跟踪ID、灰度参数和免验证参数
        String traceId = request.getHeaders().getFirst(TRACE_ID);
        String gray = request.getHeaders().getFirst(GRAY_PARAMETER);
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        // 如果跟踪ID为空，则生成新的跟踪ID
        if (StringUtil.isEmpty(traceId)) {
            traceId = String.valueOf(uidGenerator.getUid());
        }
        // 将跟踪ID放入日志上下文中，便于后续的日志追踪
        MDC.put(TRACE_ID, traceId);
        // 创建一个映射，用于存储需要传递的请求头信息
        Map<String, String> headMap = new HashMap<>(8);
        headMap.put(TRACE_ID, traceId);
        headMap.put(GRAY_PARAMETER, gray);
        if (StringUtil.isNotEmpty(noVerify)) {
            headMap.put(NO_VERIFY, noVerify);
        }
        // 将跟踪ID和灰度参数存入基础参数持有者中，以便在后续处理中使用
        BaseParameterHolder.setParameter(TRACE_ID, traceId);
        BaseParameterHolder.setParameter(GRAY_PARAMETER, gray);
        // 获取请求的内容类型
        MediaType contentType = request.getHeaders().getContentType();
        // 如果是application/json类型的请求，则需要读取请求体
        if (Objects.nonNull(contentType) && contentType.toString().toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE.toLowerCase())) {
            return readBody(exchange, chain, headMap);
        }
        else {
            // 对于非JSON请求，执行请求，并将之前保存的头部信息添加到请求中
            Map<String, String> map = doExecute("", exchange);
            map.remove(REQUEST_BODY);
            map.putAll(headMap);
            request.mutate().headers(httpHeaders -> {
                map.forEach(httpHeaders::add);
            });
            return chain.filter(exchange);
        }
    }

    private Mono<Void> readBody(ServerWebExchange exchange, GatewayFilterChain chain, Map<String, String> headMap) {
        log.info("current thread readBody : {}", Thread.currentThread().getName());
        RequestTemporaryWrapper requestTemporaryWrapper = new RequestTemporaryWrapper();

        ServerRequest serverRequest = ServerRequest.create(exchange, serverCodecConfigurer.getReaders());
        Mono<String> modifiedBody = serverRequest
                .bodyToMono(String.class)
                .flatMap(originalBody -> Mono.just(execute(requestTemporaryWrapper, originalBody, exchange)))
                .switchIfEmpty(Mono.defer(() -> Mono.just(execute(requestTemporaryWrapper, "", exchange))));

        BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
        return bodyInserter
                .insert(outputMessage, new BodyInserterContext())
                .then(Mono.defer(() -> chain.filter(
                        exchange.mutate().request(decorateHead(exchange, headers, outputMessage, requestTemporaryWrapper, headMap)).build()
                )))
                .onErrorResume((Function<Throwable, Mono<Void>>) throwable -> Mono.error(throwable));
    }

    public String execute(RequestTemporaryWrapper requestTemporaryWrapper, String requestBody, ServerWebExchange exchange) {
        //进行业务验证，并将相关参数放入map
        Map<String, String> map = doExecute(requestBody, exchange);
        String body = map.get(REQUEST_BODY);
        map.remove(REQUEST_BODY);
        requestTemporaryWrapper.setMap(map);
        return body;
    }

    private Map<String, String> doExecute(String originalBody, ServerWebExchange exchange) {
        log.info("current thread verify: {}", Thread.currentThread().getName());
        ServerHttpRequest request = exchange.getRequest();
        String requestBody = originalBody;
        Map<String, String> bodyContent = new HashMap<>(32);
        if (StringUtil.isNotEmpty(originalBody)) {
            bodyContent = JSON.parseObject(originalBody, Map.class);
        }
        String code = null;
        String token;
        String userId = null;
        String url = request.getPath().value();
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        boolean allowNormalAccess = gatewayProperty.isAllowNormalAccess();
        if ((!allowNormalAccess) && (VERIFY_VALUE.equals(noVerify))) {
            throw new DaMaiFrameException(BaseCode.ONLY_SIGNATURE_ACCESS_IS_ALLOWED);
        }
        if (checkParameter(originalBody, noVerify) && !skipCheckParameter(url)) {

            String encrypt = request.getHeaders().getFirst(ENCRYPT);
            //应用渠道
            code = bodyContent.get(CODE);
            //token
            token = request.getHeaders().getFirst(TOKEN);

            GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);

            if (StringUtil.isNotEmpty(encrypt) && V2.equals(encrypt)) {
                String decrypt = RsaTool.decrypt(bodyContent.get(BUSINESS_BODY), channelDataVo.getDataSecretKey());
                bodyContent.put(BUSINESS_BODY, decrypt);
            }
            boolean checkFlag = RsaSignTool.verifyRsaSign256(bodyContent, channelDataVo.getSignPublicKey());
            if (!checkFlag) {
                throw new DaMaiFrameException(BaseCode.RSA_SIGN_ERROR);
            }

            boolean skipCheckTokenResult = skipCheckToken(url);
            if (!skipCheckTokenResult && StringUtil.isEmpty(token)) {
                ArgumentError argumentError = new ArgumentError();
                argumentError.setArgumentName(token);
                argumentError.setMessage("token参数为空");
                List<ArgumentError> argumentErrorList = new ArrayList<>();
                argumentErrorList.add(argumentError);
                throw new ArgumentException(BaseCode.ARGUMENT_EMPTY.getCode(), argumentErrorList);
            }

            if (!skipCheckTokenResult) {
                UserVo userVo = tokenService.getUser(token, code, channelDataVo.getTokenSecret());
                userId = userVo.getId();
            }

            requestBody = bodyContent.get(BUSINESS_BODY);
        }
        apiRestrictService.apiRestrict(userId, url, request);
        Map<String, String> map = new HashMap<>(4);
        map.put(REQUEST_BODY, requestBody);
        if (StringUtil.isNotEmpty(code)) {
            map.put(CODE, code);
        }
        if (StringUtil.isNotEmpty(userId)) {
            map.put(USER_ID, userId);
        }
        return map;
    }

    /**
     * 将网关层request请求头中的重要参数传递给后续的微服务中
     */
    private ServerHttpRequestDecorator decorateHead(ServerWebExchange exchange, HttpHeaders headers, CachedBodyOutputMessage outputMessage, RequestTemporaryWrapper requestTemporaryWrapper, Map<String, String> headMap) {
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                log.info("current thread getHeaders: {}", Thread.currentThread().getName());
                long contentLength = headers.getContentLength();
                HttpHeaders newHeaders = new HttpHeaders();
                newHeaders.putAll(headers);
                Map<String, String> map = requestTemporaryWrapper.getMap();
                if (CollectionUtil.isNotEmpty(map)) {
                    newHeaders.setAll(map);
                }
                if (CollectionUtil.isNotEmpty(headMap)) {
                    newHeaders.setAll(headMap);
                }
                if (contentLength > 0) {
                    newHeaders.setContentLength(contentLength);
                }
                else {
                    newHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                }
                if (CollectionUtil.isNotEmpty(headMap) && StringUtil.isNotEmpty(headMap.get(TRACE_ID))) {
                    MDC.put(TRACE_ID, headMap.get(TRACE_ID));
                }
                return newHeaders;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return outputMessage.getBody();
            }
        };
    }

    @Override
    public int getOrder() {
        return -2;
    }

    public boolean skipCheckToken(String url) {
        for (String skipCheckTokenPath : gatewayProperty.getCheckTokenPaths()) {
            PathMatcher matcher = new AntPathMatcher();
            if (matcher.match(skipCheckTokenPath, url)) {
                return false;
            }
        }
        return true;
    }

    public boolean skipCheckParameter(String url) {
        for (String skipCheckTokenPath : gatewayProperty.getCheckSkipParmeterPaths()) {
            PathMatcher matcher = new AntPathMatcher();
            if (matcher.match(skipCheckTokenPath, url)) {
                return true;
            }
        }
        return false;
    }

    public boolean checkParameter(String originalBody, String noVerify) {
        return (!(VERIFY_VALUE.equals(noVerify))) && StringUtil.isNotEmpty(originalBody);
    }
}
