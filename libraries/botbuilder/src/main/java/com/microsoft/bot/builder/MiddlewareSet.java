// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.builder;



import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;


public class MiddlewareSet implements Middleware
{
    public NextDelegate Next;

    private final ArrayList<Middleware> _middleware = new ArrayList<Middleware>();

    public MiddlewareSet Use(Middleware middleware)
    {
        BotAssert.MiddlewareNotNull(middleware);
        _middleware.add(middleware);
        return this;
    }

    public CompletableFuture ReceiveActivity(TurnContextImpl context)
            throws Exception {
        // await ReceiveActivityInternal(context, null).ConfigureAwait(false);
        return ReceiveActivityInternal(context, null);
    }

    @Override
    public CompletableFuture OnTurn(TurnContext context, NextDelegate next) throws Exception {
        return completedFuture(ReceiveActivityInternal((TurnContextImpl)context, null)
                .thenRun(() -> {
                    try {
                        next.next();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(String.format("MiddlewareSet::OnTurn next delegate: %s", e.toString()));
                    }
                })
                .get());
    }


    public CompletableFuture OnTurn(TurnContextImpl context, CompletableFuture next)
            throws ExecutionException, InterruptedException {
        return null;
    }

    /**
     * Intended to be called from Bot, this method performs exactly the same as the
     * standard ReceiveActivity, except that it runs a user-defined delegate returns 
     * if all Middleware in the receive pipeline was run.
     */
    public CompletableFuture ReceiveActivityWithStatus(TurnContext context, Function<TurnContext, CompletableFuture> callback)
            throws Exception {
        return ReceiveActivityInternal(context, callback);
    }

    private CompletableFuture ReceiveActivityInternal(TurnContext context, Function<TurnContext, CompletableFuture> callback)
            throws Exception {
        return ReceiveActivityInternal(context, callback, 0);
    }
    private CompletableFuture ReceiveActivityInternal(TurnContext context, Function<TurnContext, CompletableFuture> callback, int nextMiddlewareIndex )
            throws Exception {
        // Check if we're at the end of the middleware list yet
        if(nextMiddlewareIndex == _middleware.size())
        {
            // If all the Middlware ran, the "leading edge" of the tree is now complete. 
            // This means it's time to run any developer specified callback. 
            // Once this callback is done, the "trailing edge" calls are then completed. This
            // allows code that looks like:
            //      Trace.TraceInformation("before");
            //      await next();
            //      Trace.TraceInformation("after"); 
            // to run as expected.

            // If a callback was provided invoke it now and return its task, otherwise just return the completed task
            if (callback == null)
                return completedFuture(null);
            else
                return callback.apply(context);
        }

        // Get the next piece of middleware 
        Middleware nextMiddleware = _middleware.get(nextMiddlewareIndex);
        NextDelegate next = new NextDelegate() {
            public CompletableFuture next() throws Exception {
                return ReceiveActivityInternal(context, callback, nextMiddlewareIndex + 1);
            }
        };

        // Execute the next middleware passing a closure that will recurse back into this method at the next piece of middlware as the NextDelegate
        return nextMiddleware.OnTurn(
            context,
            next);
    }


}
