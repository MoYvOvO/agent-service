package Agent.AgentTest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient      // 让 Agent 注册到 Nacos
@EnableFeignClients         // 让 Agent 能使用 Feign 调用其他服务

public class TestApplication {

	public static void main(String[] args) {

		SpringApplication.run(TestApplication.class, args);
	}

}
