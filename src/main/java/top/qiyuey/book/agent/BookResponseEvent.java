package top.qiyuey.book.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 读书问答响应事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookResponseEvent {

    private Status status;
    private String content;

    // 构造辅助方法
    public BookResponseEvent(String content) {
        this.status = Status.ERROR;
        this.content = content;
    }

    public enum Status {
        START,
        PROGRESS,
        RESULT,
        ERROR,
        DEBUG
    }
}
