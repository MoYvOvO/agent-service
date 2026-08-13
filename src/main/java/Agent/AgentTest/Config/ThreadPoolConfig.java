package Agent.AgentTest.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean("aiExecutor")
    public ExecutorService aiExecutor() {
        return new ThreadPoolExecutor(
                5,                               // 核心线程数
                10,                              // 最大线程数
                60L, TimeUnit.SECONDS,           // 空闲存活时间
                new LinkedBlockingQueue<>(100),  // 等待队列
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行
        );
    }
}