package org.mwg.core.task;

import org.mwg.Callback;
import org.mwg.plugin.SchedulerAffinity;
import org.mwg.task.Action;
import org.mwg.task.TaskContext;
import org.mwg.task.ConditionalFunction;
import org.mwg.task.TaskResult;

class CF_ActionIfThenElse implements Action {

    private ConditionalFunction _condition;
    private org.mwg.task.Task _thenSub;
    private org.mwg.task.Task _elseSub;

    CF_ActionIfThenElse(final ConditionalFunction cond, final org.mwg.task.Task p_thenSub, final org.mwg.task.Task p_elseSub) {
        if (cond == null) {
            throw new RuntimeException("condition should not be null");
        }
        if (p_thenSub == null) {
            throw new RuntimeException("thenSub should not be null");
        }
        if (p_elseSub == null) {
            throw new RuntimeException("elseSub should not be null");
        }
        this._condition = cond;
        this._thenSub = p_thenSub;
        this._elseSub = p_elseSub;
    }

    @Override
    public void eval(final TaskContext context) {
        if (_condition.eval(context)) {
            _thenSub.executeFrom(context, context.result(), SchedulerAffinity.SAME_THREAD, new Callback<TaskResult>() {
                @Override
                public void on(TaskResult res) {
                    context.continueWith(res);
                }
            });
        } else {
            _elseSub.executeFrom(context, context.result(), SchedulerAffinity.SAME_THREAD, new Callback<TaskResult>() {
                @Override
                public void on(TaskResult res) {
                    context.continueWith(res);
                }
            });
        }
    }

    @Override
    public String toString() {
        return "ifThen()";
    }

}