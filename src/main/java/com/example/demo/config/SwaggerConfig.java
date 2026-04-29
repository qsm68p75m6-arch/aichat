package com.example.demo.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Swagger / OpenAPI 3 配置类
 *
 * 作用：自定义 API 文档的元信息（标题、描述、版本等）
 * 访问地址：启动项目后打开 http://localhost:端口/swagger-ui.html
 */
@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI(){
        return  new OpenAPI()
                .info(new Info()
                        .title("AI chat API")
                        .description("基于智谱GLM的智能聊天系统接口文档")
                        .version("1.0.0.0")
                        .contact(new Contact()
                                .name("anothersuset")
                                .email("anothersuset@outlook.com"))
                                .license(new License()
                                        .name("Apache 2.0")
                                        .url("https://www.apache.org/licenses/LICENSE-2.0")
                                )
                        );


    }


}
