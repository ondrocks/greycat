package org.mwg.core.task;

import org.mwg.task.Action;
import org.mwg.task.TaskContext;

class ActionDeclareGlobalVar implements Action {

    private final String _name;

    ActionDeclareGlobalVar(final String p_name) {
        if (p_name == null) {
            throw new RuntimeException("name should not be null");
        }
        this._name = p_name;
    }

    @Override
    public void eval(final TaskContext context) {
        context.setGlobalVariable(context.template(_name), Actions.emptyResult());
        context.continueTask();
    }

    @Override
    public String toString() {
        return "defineGlobalVar(\'" + _name + "\')";
    }

}