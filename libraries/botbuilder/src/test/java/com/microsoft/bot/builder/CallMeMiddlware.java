package com.microsoft.bot.builder;

import java.util.concurrent.CompletableFuture;

import static com.ea.async.Async.await;

public class CallMeMiddlware implements Middleware
{
    private ActionDel callMe;

    public CallMeMiddlware(ActionDel callme) {
        this.callMe = callme;
    }

    @Override
    public CompletableFuture OnTurn(TurnContext context, NextDelegate next) throws Exception {
        return CompletableFuture.runAsync(() -> {
            this.callMe.CallMe();
            try {
                await(next.next());
            } catch (Exception e) {
                e.printStackTrace();

            }
        });
    }
}
