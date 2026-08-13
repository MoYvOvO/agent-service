package Agent.AgentTest.Dto;

import lombok.Data;

@Data
public class Product {
    private String id;
    private String name;
    private Double price;
    private Integer stock;
}