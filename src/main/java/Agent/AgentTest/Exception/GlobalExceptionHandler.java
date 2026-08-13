package Agent.AgentTest.Exception;
import Agent.AgentTest.Dto.Result;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BlockException.class)
    public Result<String> handleBlockException(BlockException e) {
        // 可以打印日志记录限流事件
        System.out.println("⚠️ 请求被限流：" + e.getMessage());
        return Result.error("请求过于频繁，请稍后再试。");
    }

    // 如果你还想兜底其他异常，可以再加
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();
        return Result.error("系统繁忙，请稍后再试");
    }
}