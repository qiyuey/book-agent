package top.qiyuey.book.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThreadInfo implements Serializable {
    private String id;
    private String title;
    private long updatedAt;
    private String modelId;
    private String bookName;
}

