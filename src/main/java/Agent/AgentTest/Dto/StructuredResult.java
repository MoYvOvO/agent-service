package Agent.AgentTest.Dto;

import lombok.Data;

@Data
public class StructuredResult {
    private String type;        // "product_list" / "price" / "text"
    private Object data;        // 具体数据，可以是 List<Product> 或 Price 等
    private String summary;     // 简短说明
}