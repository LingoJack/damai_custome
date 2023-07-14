package com.example.swagger;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import springfox.documentation.RequestHandler;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.List;

/**
 * @program: toolkit
 * @description:
 * @author: kuan
 * @create: 2023-07-03
 **/
@EnableSwagger2
@EnableKnife4j
public class SwaggerConfiguration {

    @Bean
    @Order(value = 1)
    public Docket groupRestApi() {
        
        Predicate<RequestHandler> predicate = (requestHandler) -> {
            boolean controllerStandardDocument = requestHandler.findControllerAnnotation(Api.class).isPresent();
            boolean methodStandardDocument = requestHandler.findAnnotation(ApiOperation.class).isPresent();
            if (controllerStandardDocument || methodStandardDocument) {
                return true;
            }else {
                return false;
            }
        };
        
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(groupApiInfo())
                .useDefaultResponseMessages(false)
                .forCodeGeneration(true)
                .select()
                .apis(predicate)
                .build();
    }

    private ApiInfo groupApiInfo(){
        return new ApiInfoBuilder()
                .title("swagger文档")
                .description("<div style='font-size:14px;color:red;'>前端开发人员使用</div>")
                .termsOfServiceUrl("http://www.group.com/")
                .contact("kuan")
                .version("1.0")
                .build();
    }

    private ApiKey apiKey() {
        return new ApiKey("BearerToken", "Authorization", "header");
    }
    private ApiKey apiKey1() {
        return new ApiKey("BearerToken1", "Authorization-x", "header");
    }

    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(defaultAuth())
                .forPaths(PathSelectors.regex("/.*"))
                .build();
    }
    private SecurityContext securityContext1() {
        return SecurityContext.builder()
                .securityReferences(defaultAuth1())
                .forPaths(PathSelectors.regex("/.*"))
                .build();
    }

    List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Lists.newArrayList(new SecurityReference("BearerToken", authorizationScopes));
    }
    List<SecurityReference> defaultAuth1() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Lists.newArrayList(new SecurityReference("BearerToken1", authorizationScopes));
    }
}
