package com.damai.filter;

import com.alibaba.fastjson.JSON;
import com.damai.common.ApiResponse;
import com.damai.util.StringUtil;
import com.damai.service.ChannelDataService;
import com.damai.util.RsaTool;
import com.damai.vo.GetChannelDataVo;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.function.BiFunction;

import static com.damai.constant.GatewayConstant.CODE;
import static com.damai.constant.GatewayConstant.ENCRYPT;
import static com.damai.constant.GatewayConstant.NO_VERIFY;
import static com.damai.constant.GatewayConstant.V2;
import static com.damai.constant.GatewayConstant.VERIFY_VALUE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;


/**
 * 返回过滤器 参考 {@link org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory}
 **/
@Component
@Slf4j
public class ResponseValidationFilter implements GlobalFilter, Ordered {

	// AES向量值，用于加密解密
	@Value("${aes.vector:default}")
	private String aesVector;

	// 通道数据服务，用于获取通道数据
	@Autowired
	private ChannelDataService channelDataService;

	/**
	 * 返回过滤器的顺序
	 *
	 * @return 过滤器的顺序值
	 */
	@Override
	public int getOrder() {
		return -2;
	}

	/**
	 * 过滤方法，用于处理服务器网页交换
	 *
	 * @param exchange 服务器网页交换对象
	 * @param chain    网关过滤链
	 * @return Mono<Void> 表示异步处理
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return chain.filter(exchange.mutate().response(decorate(exchange)).build());
	}

	/**
	 * 装饰响应对象，以实现对响应体的修改
	 *
	 * @param exchange 服务器网页交换对象
	 * @return 装饰后的服务器HTTP响应对象
	 */
	private ServerHttpResponse decorate(ServerWebExchange exchange) {
		ServerHttpResponseDecorator serverHttpResponseDecorator = new ServerHttpResponseDecorator(
				exchange.getResponse()) {

			/**
			 * 使用修改后的响应体写入响应
			 *
			 * @param body 响应体发布者
			 * @return Mono<Void> 表示异步处理
			 */
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

				String originalResponseContentType = exchange
						.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
				HttpHeaders httpHeaders = new HttpHeaders();
				httpHeaders.add(HttpHeaders.CONTENT_TYPE,
						originalResponseContentType);

				ClientResponse clientResponse = ClientResponse
						.create(Objects.requireNonNull(exchange.getResponse().getStatusCode()))
						.headers(headers -> headers.putAll(httpHeaders))
						.body(Flux.from(body)).build();

				Mono<String> modifiedBody = clientResponse
						.bodyToMono(String.class)
						.flatMap(originalBody -> modifyResponseBody().apply(exchange, originalBody));

				BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody,
						String.class);
				CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(
						exchange, exchange.getResponse().getHeaders());
				return bodyInserter.insert(outputMessage, new BodyInserterContext())
						.then(Mono.defer(() -> {
							Flux<DataBuffer> messageBody = outputMessage.getBody();
							HttpHeaders headers = getDelegate().getHeaders();
							if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
								messageBody = messageBody.doOnNext(data -> headers
										.setContentLength(data.readableByteCount()));
							}
							return getDelegate().writeWith(messageBody);
						}));
			}

			/**
			 * 修改响应体的方法生成器
			 *
			 * @return BiFunction<ServerWebExchange, String, Mono < String>> 用于修改响应体的函数
			 */
			private BiFunction<ServerWebExchange, String, Mono<String>> modifyResponseBody() {
				return (serverWebExchange, responseBody) -> {
					String modifyResponseBody = checkResponseBody(serverWebExchange, responseBody);
					return Mono.just(modifyResponseBody);
				};
			}

			/**
			 * 写入并刷新响应体
			 *
			 * @param body 响应体发布者的发布者
			 * @return Mono<Void> 表示异步处理
			 */
			@Override
			public Mono<Void> writeAndFlushWith(
					Publisher<? extends Publisher<? extends DataBuffer>> body) {
				return writeWith(Flux.from(body).flatMapSequential(p -> p));
			}
		};
		return serverHttpResponseDecorator;
	}

	/**
	 * 检查并修改响应体
	 *
	 * @param serverWebExchange 服务器网页交换对象
	 * @param responseBody      原始响应体
	 * @return 修改后的响应体
	 */
	private String checkResponseBody(final ServerWebExchange serverWebExchange, final String responseBody) {
		String modifyResponseBody = responseBody;
		ServerHttpRequest request = serverWebExchange.getRequest();
		String noVerify = request.getHeaders().getFirst(NO_VERIFY);
		String encrypt = request.getHeaders().getFirst(ENCRYPT);
		if ((!VERIFY_VALUE.equals(noVerify)) && V2.equals(encrypt) && StringUtil.isNotEmpty(responseBody)) {
			ApiResponse apiResponse = JSON.parseObject(responseBody, ApiResponse.class);
			Object data = apiResponse.getData();
			if (data != null) {
				String code = request.getHeaders().getFirst(CODE);
				GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);
				String rsaEncrypt = RsaTool.encrypt(JSON.toJSONString(data), channelDataVo.getDataPublicKey());
				apiResponse.setData(rsaEncrypt);
				modifyResponseBody = JSON.toJSONString(apiResponse);
			}
		}
		return modifyResponseBody;
	}
}

