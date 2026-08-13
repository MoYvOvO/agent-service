package Agent.AgentTest.Feign;

import Agent.AgentTest.Dto.Product;
import Agent.AgentTest.Dto.ProductListResult;
import Agent.AgentTest.Dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "product-service")
public interface StockFeignClient {
    // 这里的路径必须和秒杀库存服务暴露的接口路径一致
    // 假设库存服务有 @GetMapping("/stock/{productId}")
    @GetMapping("/api/products/{productId}/stock")
    String getStock(@PathVariable("productId") String productId);
    @GetMapping("/api/products")
    ProductListResult getAllProducts();

    @GetMapping("/api/products/{productId}")
    Result<Product> getProductById(@PathVariable("productId") String productId);
}

