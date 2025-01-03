package com.damai.jwt;

import com.alibaba.fastjson.JSONObject;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

@Slf4j
public class TokenUtil {

	/**
	 * 指定签名的时候使用的签名算法，也就是header那部分。
	 */
	private static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS256;

	/**
	 * 用户登录成功后生成Jwt
	 * 使用Hs256算法  私匙使用用户密码
	 *
	 * @param id          标识
	 * @param info        登录成功的user对象
	 * @param ttlMillis   jwt过期时间
	 * @param tokenSecret 私钥
	 * @return
	 */
	public static String createToken(String id, String info, long ttlMillis, String tokenSecret) {
		//生成JWT的时间
		long nowMillis = System.currentTimeMillis();

		//创建一个JwtBuilder，设置jwt的body
		JwtBuilder builder = Jwts.builder()
				//如果有私有声明，一定要先设置这个自己创建的私有的声明，这个是给builder的claim赋值，一旦写在标准的声明赋值之后，就是覆盖了那些标准的声明的
//                .setClaims(claims)
				//设置jti(JWT ID)：是JWT的唯一标识，根据业务需要，这个可以设置为一个不重复的值，主要用来作为一次性token,从而回避重放攻击。
				.setId(id)
				//iat: jwt的签发时间
				.setIssuedAt(new Date(nowMillis))
				//代表这个JWT的主体，即它的所有人，这个是一个json格式的字符串。
				.setSubject(info)
				//设置签名使用的签名算法和签名使用的秘钥
				.signWith(SIGNATURE_ALGORITHM, tokenSecret);
		if (ttlMillis >= 0) {
			//设置过期时间
			builder.setExpiration(new Date(nowMillis + ttlMillis));
		}
		// 压缩加密
		return builder.compact();
	}


	/**
	 * Token的解密
	 *
	 * @param token       加密后的token
	 * @param tokenSecret 私钥
	 * @return
	 */
	public static String parseToken(String token, String tokenSecret) {
		try {
			return Jwts.parser()
					//设置签名的秘钥
					.setSigningKey(tokenSecret)
					//设置需要解析的jwt
					.parseClaimsJws(token)
					.getBody()
					.getSubject();
		}
		catch (ExpiredJwtException jwtException) {
			log.error("parseToken error", jwtException);
			throw new DaMaiFrameException(BaseCode.TOKEN_EXPIRE);
		}

	}

	public static void main(String[] args) {

		String tokenSecret = "CSYZWECHAT";
		//生成token的实力
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("001key", "001value");
		jsonObject.put("002key", "001value");

		String token1 = TokenUtil.createToken("1", jsonObject.toJSONString(), 10000, tokenSecret);
		System.out.println("token:" + token1);

		//解析token的示例
		String token2 = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIxIiwiaWF0IjoxNjg4NTQyODM3LCJzdWIiOiJ7XCIwMDJrZXlcIjpcIjAwMXZhbHVlXCIsXCIwMDFrZXlcIjpcIjAwMXZhbHVlXCJ9IiwiZXhwIjoxNjg4NTQyODQ3fQ.vIKcAilTn_CR3VYssNE7rBpfuCSCH_RrkmsadLWf664";
		String subject = TokenUtil.parseToken(token2, tokenSecret);
		System.out.println("解析token后的值:" + subject);
	}
}
