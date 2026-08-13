package Agent.AgentTest.Dto;

public class Result<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    // 无参构造
    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    // 全参构造
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ========== 静态工厂方法（推荐使用） ==========
    // 成功返回（带数据）
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    // 成功返回（带自定义消息）
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // 失败返回（带消息）
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    // 失败返回（自定义错误码）
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ========== Getter 和 Setter（必须要有！） ==========
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
