package org.mwg.core.task;

import org.mwg.*;
import org.mwg.plugin.AbstractNode;
import org.mwg.plugin.Job;
import org.mwg.task.TaskAction;
import org.mwg.task.TaskContext;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class ActionTraverse implements TaskAction {

    private final String _name;

    ActionTraverse(final String p_name) {
        this._name = p_name;
    }

    @Override
    public final void eval(final TaskContext context) {
        Object previousResult = context.getPreviousResult();
        if (previousResult != null) {
            //dry execute to count waiter
            Set<Long> toLoad = new HashSet<Long>();
            if (previousResult instanceof Object[]) {
                collectArray((Object[]) previousResult, toLoad);
            } else if (previousResult instanceof AbstractNode) {
                Node loop = (Node) previousResult;
                Object rel = loop.get(_name);
                if (rel != null && rel instanceof long[]) {
                    long[] interResult = (long[]) rel;
                    for (int j = 0; j < interResult.length; j++) {
                        toLoad.add(interResult[j]);
                    }
                }
            }
            DeferCounter deferCounter = context.graph().newCounter(toLoad.size());
            final Node[] resultNodes = new Node[toLoad.size()];
            final AtomicInteger cursor = new AtomicInteger(0);
            for (Long idNode : toLoad) {
                context.graph().lookup(context.getWorld(), context.getTime(), idNode, new Callback<Node>() {
                    @Override
                    public void on(Node result) {
                        resultNodes[cursor.getAndIncrement()] = result;
                        deferCounter.count();
                    }
                });
            }
            deferCounter.then(new Job() {
                @Override
                public void run() {
                    context.setResult(resultNodes);
                    context.next();
                }
            });
        } else {
            context.next();
        }
    }

    private void collectArray(Object[] current, Set<Long> toLoad) {
        for (int i = 0; i < current.length; i++) {
            if (current[i] instanceof Object[]) {
                collectArray((Object[]) current[i], toLoad);
            } else if (current[i] instanceof AbstractNode) {
                Node loop = (Node) current[i];
                Object rel = loop.get(_name);
                if (rel != null && rel instanceof long[]) {
                    long[] interResult = (long[]) rel;
                    for (int j = 0; j < interResult.length; j++) {
                        toLoad.add(interResult[j]);
                    }
                }
            }
        }
    }

}