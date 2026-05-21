package com.lightbot.task;

import com.lightbot.entity.Task;

/**
 * 任务执行器接口，每个 TaskType 对应一个 @Component 实现
 *
 * @author finch
 * @since 2026-05-21
 */
public interface TaskExecutor {

    /**
     * 执行任务
     *
     * @param task 任务记录
     */
    void execute(Task task) throws Exception;
}
