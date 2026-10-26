package com.bingchunmoli.aspect;

import org.springframework.scheduling.annotation.Async;

/** Separate proxy boundary lets the advice handle executor rejection before it reaches business code. */
class AsyncLogDispatcher {
    @Async
    public void execute(Runnable action) {
        action.run();
    }
}
