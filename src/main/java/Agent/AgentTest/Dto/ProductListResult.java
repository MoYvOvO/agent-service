package Agent.AgentTest.Dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductListResult {
    private Integer code;
    private ProductDataWrapper data;   // ← 注意：data 是一个包装对象
    private String message;

    @Data
    public static class ProductDataWrapper {
        private List<Product> data;    // ← 真正的商品列表在这里
    }
}