package top.qiyuey.book.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.qiyuey.book.config.ModelConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class ThreadService {

    private static final String THREAD_MAP_KEY = "book-agent:threads:v2";
    private final RedissonClient redissonClient;
    private final ChatClient chatClient;

    public ThreadService(RedissonClient redissonClient, BookAgentFactory agentFactory, ModelConfig modelConfig) {
        this.redissonClient = redissonClient;
        ChatModel chatModel = agentFactory.createChatModel(modelConfig.getDefaultModel());
        this.chatClient = ChatClient.create(chatModel);
    }

    public List<ThreadInfo> getAllThreads() {
        RMap<String, ThreadInfo> map = redissonClient.getMap(THREAD_MAP_KEY);
        List<ThreadInfo> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparingLong(ThreadInfo::getUpdatedAt).reversed());
        log.info("Loaded {} threads from history", list.size());
        return list;
    }

    public void updateThread(String threadId, String title, String modelId, String bookName) {
        RMap<String, ThreadInfo> map = redissonClient.getMap(THREAD_MAP_KEY);
        ThreadInfo info = map.get(threadId);
        if (info == null) {
            info = new ThreadInfo(threadId, title != null ? title : "New Chat", System.currentTimeMillis(), modelId, bookName);
            log.info("Created new thread: {}", threadId);
        } else {
             if (title != null) info.setTitle(title);
             info.setUpdatedAt(System.currentTimeMillis());
             if (modelId != null) info.setModelId(modelId);
             if (bookName != null) info.setBookName(bookName);
             log.info("Updated thread: {}", threadId);
        }
        map.put(threadId, info);
    }
    
    public void deleteThread(String threadId) {
        redissonClient.getMap(THREAD_MAP_KEY).remove(threadId);
        log.info("Deleted thread: {}", threadId);
    }

    public void generateTitleAsync(String threadId, String question, String modelId) {
        RMap<String, ThreadInfo> map = redissonClient.getMap(THREAD_MAP_KEY);
        ThreadInfo info = map.get(threadId);
        
        if (info == null || info.getTitle() == null || "New Chat".equals(info.getTitle())) {
             Mono.fromCallable(() -> {
                 String prompt = "请为以下内容生成一个极简标题（10字以内），只返回标题文字：\n" + question;
                 return chatClient.prompt(prompt).call().content();
             })
             .subscribeOn(Schedulers.boundedElastic())
             .subscribe(title -> {
                 if (title != null && !title.isBlank()) {
                     updateThread(threadId, title.replace("\"", "").trim(), modelId, null);
                 }
             }, error -> log.error("Failed to generate title for thread {}", threadId, error));
        }
    }

    public void addMessage(String threadId, String role, String content) {
        String key = "book-agent:messages:" + threadId;
        List<ChatMessage> messages = redissonClient.getList(key);
        messages.add(new ChatMessage(role, content, System.currentTimeMillis()));
        // Also update thread timestamp
        updateThread(threadId, null, null, null);
    }

    public List<ChatMessage> getMessages(String threadId) {
        String key = "book-agent:messages:" + threadId;
        return new ArrayList<>(redissonClient.getList(key));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage implements java.io.Serializable {
        private String role;
        private String content;
        private long timestamp;
    }
}
