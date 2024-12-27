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

import java.util.*;
import java.util.function.Function;

import static com.damai.constant.Constant.GRAY_PARAMETER;
import static com.damai.constant.Constant.TRACE_ID;
import static com.damai.constant.GatewayConstant.*;


/**
 * 请求验证过滤器
 * 用于对所有经过的请求进行验证和处理，包含请求体参数验证、链路追踪ID的设置、签名验证、登录验证等功能。
 * 此类基于 Spring WebFlux 框架的响应式编程模型，可以满足高并发、高可用需求
 */
@Component
@Slf4j
public class RequestValidationFilter implements GlobalFilter, Ordered {

	@Resource
	private ServerCodecConfigurer serverCodecConfigurer;

	@Autowired
	private ChannelDataService channelDataService;

	@Autowired
	private ApiRestrictService apiRestrictService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private GatewayProperty gatewayProperty;

	@Autowired
	private UidGenerator uidGenerator;

	@Autowired
	private RateLimiterProperty rateLimiterProperty;

	@Autowired
	private RateLimiter rateLimiter;


	/**
	 * 核心过滤方法，根据限流开关决定是否启用限流，并调用过滤逻辑。
	 *
	 * @param exchange 服务器交换对象，包含请求和响应的所有信息
	 * @param chain    过滤器链对象，用于执行过滤器链中的下一个过滤器
	 * @return 返回一个空的Mono对象，表示异步操作的完成
	 */
	@Override
	public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
		if (rateLimiterProperty.getRateSwitch()) {
			try {
				rateLimiter.acquire();
				return doFilter(exchange, chain);
			}
			catch (InterruptedException e) {
				log.error("interrupted error", e);
				throw new DaMaiFrameException(BaseCode.THREAD_INTERRUPTED);
			}
			finally {
				rateLimiter.release();
			}
		}
		else {
			return doFilter(exchange, chain);
		}
	}

	/**
	 * 执行过滤器逻辑
	 * 此方法主要用于处理请求的过滤逻辑，包括获取链路ID、灰度标识、是否验证参数等信息，并根据请求类型决定是否进行参数验证。
	 * 1.生成链路调用id
	 * 2.如果是json请求则进行参数验证
	 * 3.判断是否跳过参数验证
	 * 4.根据渠道code获得秘钥相关参数
	 * 5.如果是v2加密版本则要根据RSA的私钥进行解密
	 * 6.进行签名验证
	 * 7。判断是否需要验证登录
	 * 8.根据规则对api接口进行防刷限制
	 * 9.将修改后的请求体和生成要添加请求头的数据传递给业务服务
	 *
	 * @param exchange 服务器交换对象，包含请求和响应的所有信息
	 * @param chain    过滤器链对象，用于执行过滤器链中的下一个过滤器
	 * @return 返回一个空的Mono对象，表示异步操作的完成
	 */
	public Mono<Void> doFilter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		// 链路id
		String traceId = request.getHeaders().getFirst(TRACE_ID);
		// 灰度标识
		String gray = request.getHeaders().getFirst(GRAY_PARAMETER);
		// 是否验证参数
		String noVerify = request.getHeaders().getFirst(NO_VERIFY);
		// 如果链路id不存在，那么在这里生成
		if (StringUtil.isEmpty(traceId)) {
			traceId = String.valueOf(uidGenerator.getUid());
		}
		// 将链路id放到日志的MDC中便于日志输出
		MDC.put(TRACE_ID, traceId);
		Map<String, String> headMap = new HashMap<>(8);
		headMap.put(TRACE_ID, traceId);
		headMap.put(GRAY_PARAMETER, gray);
		if (StringUtil.isNotEmpty(noVerify)) {
			headMap.put(NO_VERIFY, noVerify);
		}
		// 将链路id放到ThreadLocal中
		BaseParameterHolder.setParameter(TRACE_ID, traceId);
		// 将灰度标识放到ThreadLocal中
		BaseParameterHolder.setParameter(GRAY_PARAMETER, gray);
		// 获取请求类型
		MediaType contentType = request.getHeaders().getContentType();

		// 如果是json格式
		if (Objects.nonNull(contentType) && contentType.toString().toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE.toLowerCase())) {
			// 进行参数验证
			return readBody(exchange, chain, headMap);
		}

		// 将生成要添加请求头的数据传递给业务服务
		Map<String, String> map = doExecute("", exchange);
		map.remove(REQUEST_BODY);
		map.putAll(headMap);
		request.mutate().headers(httpHeaders -> {
			map.forEach(httpHeaders::add);
		});
		return chain.filter(exchange);
	}

	/**
	 * 读取并处理请求体
	 * 此方法主要用于拦截和修改传入的请求体，记录日志，处理请求体为空的情况，并设置相应的HTTP头
	 *
	 * @param exchange 服务器交换对象，包含请求和响应的所有信息
	 * @param chain    过滤器链对象，用于执行过滤器链中的下一个过滤器
	 * @param headMap  头部映射，用于存储和传递请求头信息
	 * @return 返回一个空的Mono对象，表示异步操作的完成
	 */
	private Mono<Void> readBody(ServerWebExchange exchange, GatewayFilterChain chain, Map<String, String> headMap) {
		// 记录当前线程名称，便于日志跟踪
		log.info("current thread readBody : {}", Thread.currentThread().getName());

		// 创建临时包装器对象，用于存储请求体内容
		RequestTemporaryWrapper requestTemporaryWrapper = new RequestTemporaryWrapper();

		// 构建一个 ServerRequest 对象以便读取请求体内容
		ServerRequest serverRequest = ServerRequest.create(exchange, serverCodecConfigurer.getReaders());

		// 异步读取请求体并进行修改
		Mono<String> modifiedBody = serverRequest.bodyToMono(String.class)
				// 通过 execute 方法对请求体进行处理或验证
				.flatMap(originalBody -> Mono.just(execute(requestTemporaryWrapper, originalBody, exchange)))
				// 如果请求体为空，则以空字符串作为默认值继续处理
				.switchIfEmpty(Mono.defer(() -> Mono.just(execute(requestTemporaryWrapper, "", exchange))));

		// 创建 BodyInserter，用于将修改后的请求体重新插入
		BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);

		// 复制原始请求头并更新，删除 "Content-Length" 避免因修改内容长度引发不一致
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(exchange.getRequest().getHeaders());
		headers.remove(HttpHeaders.CONTENT_LENGTH);

		// 创建 CachedBodyOutputMessage，用于缓存并传递修改后的请求体
		CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);

		// 将修改后的请求体插入输出消息，并继续执行过滤器链
		return bodyInserter.insert(outputMessage, new BodyInserterContext())
				.then(Mono.defer(() ->
						chain.filter(
								exchange.mutate()
										.request(decorateHead(exchange, headers, outputMessage, requestTemporaryWrapper, headMap))
										.build()
						)
				))
				// 发生错误时抛出异常
				.onErrorResume((Function<Throwable, Mono<Void>>) throwable -> Mono.error(throwable));
	}

	public String execute(RequestTemporaryWrapper requestTemporaryWrapper, String requestBody, ServerWebExchange exchange) {
		// 进行业务验证，并将相关参数放入map
		Map<String, String> map = doExecute(requestBody, exchange);
		// 这里的map中的数据在doExecute中放入的，有修改后的请求体和要放在请求头中的数据，先拿出请求体用来返回，然后从map中移除，
		// 这样map剩下的数据就都是要放入请求头中的了
		String body = map.get(REQUEST_BODY);
		map.remove(REQUEST_BODY);
		requestTemporaryWrapper.setMap(map);
		return body;
	}

	/**
	 * 具体进行参数验证的逻辑
	 */
	private Map<String, String> doExecute(String originalBody, ServerWebExchange exchange) {
		log.info("current thread verify: {}", Thread.currentThread().getName());
		ServerHttpRequest request = exchange.getRequest();
		// 得到请求体
		String requestBody = originalBody;
		Map<String, String> bodyContent = new HashMap<>(32);
		if (StringUtil.isNotEmpty(originalBody)) {
			// 请求体转为map结构
			bodyContent = JSON.parseObject(originalBody, Map.class);
		}
		//基础参数code渠道
		String code = null;
		//用户的token
		String token;
		//用户的userId
		String userId = null;
		//请求的路径
		String url = request.getPath().value();
		//是否跳过参数验证的标识
		String noVerify = request.getHeaders().getFirst(NO_VERIFY);

		//是否允许跳过参数验证
		boolean allowNormalAccess = gatewayProperty.isAllowNormalAccess();
		if ((!allowNormalAccess) && (VERIFY_VALUE.equals(noVerify))) {
			throw new DaMaiFrameException(BaseCode.ONLY_SIGNATURE_ACCESS_IS_ALLOWED);
		}

		//是否跳过参数验证
		if (checkParameter(originalBody, noVerify) && !skipCheckParameter(url)) {
			String encrypt = request.getHeaders().getFirst(ENCRYPT);

			// 应用渠道
			code = bodyContent.get(CODE);
			// token
			token = request.getHeaders().getFirst(TOKEN);

			// 验证code参数并获取基础参数
			GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);
			// 如果v2版本就要先对参数进行解密
			if (StringUtil.isNotEmpty(encrypt) && V2.equals(encrypt)) {
				// 使用rsa私钥进行解密
				String decrypt = RsaTool.decrypt(bodyContent.get(BUSINESS_BODY), channelDataVo.getDataSecretKey());
				// 将解密后的请求体替换加密的请求体
				bodyContent.put(BUSINESS_BODY, decrypt);
			}

			// 进行签名验证
			boolean checkFlag = RsaSignTool.verifyRsaSign256(bodyContent, channelDataVo.getSignPublicKey());
			if (!checkFlag) {
				throw new DaMaiFrameException(BaseCode.RSA_SIGN_ERROR);
			}
			// 判断是否跳过验证登录的token
			// 默认注册和登录接口跳过验证
			boolean skipCheckTokenResult = skipCheckToken(url);
			if (!skipCheckTokenResult && StringUtil.isEmpty(token)) {
				ArgumentError argumentError = new ArgumentError();
				argumentError.setArgumentName(token);
				argumentError.setMessage("token参数为空");
				List<ArgumentError> argumentErrorList = new ArrayList<>();
				argumentErrorList.add(argumentError);
				throw new ArgumentException(BaseCode.ARGUMENT_EMPTY.getCode(), argumentErrorList);
			}
			// 获取用户id
			if (!skipCheckTokenResult) {
				UserVo userVo = tokenService.getUser(token, code, channelDataVo.getTokenSecret());
				userId = userVo.getId();
			}

			requestBody = bodyContent.get(BUSINESS_BODY);
		}
		// 根据规则对api接口进行防刷限制
		apiRestrictService.apiRestrict(userId, url, request);
		// 将修改后的请求体和要传递的请求头参数放入map
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
	 * 此方法为Gateway源码部分，可忽略
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

	/**
	 * 指定执行顺序
	 */
	@Override
	public int getOrder() {
		return -2;
	}

	/**
	 * 验证是否跳过token验证
	 */
	public boolean skipCheckToken(String url) {
		for (String skipCheckTokenPath : gatewayProperty.getCheckTokenPaths()) {
			PathMatcher matcher = new AntPathMatcher();
			if (matcher.match(skipCheckTokenPath, url)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 验证是否跳过参数验证
	 */
	public boolean skipCheckParameter(String url) {
		for (String skipCheckTokenPath : gatewayProperty.getCheckSkipParmeterPaths()) {
			PathMatcher matcher = new AntPathMatcher();
			if (matcher.match(skipCheckTokenPath, url)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 验证请求头的参数noVerify = true
	 */
	public boolean checkParameter(String originalBody, String noVerify) {
		return (!(VERIFY_VALUE.equals(noVerify))) && StringUtil.isNotEmpty(originalBody);
	}
}
