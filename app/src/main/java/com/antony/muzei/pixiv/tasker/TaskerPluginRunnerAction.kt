package com.antony.muzei.pixiv.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutputOrInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess

class BasicActionHelper(config: TaskerPluginConfig<Unit>) :
    TaskerPluginConfigHelperNoOutputOrInput<BasicActionRunner>(config) {
    override val runnerClass: Class<BasicActionRunner> get() = BasicActionRunner::class.java
    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("Will show a toast saying 'Basic'")
    }
}

class ActivityConfigBasicAction : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext
    private val taskerHelper by lazy { BasicActionHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskerHelper.finishForTasker()
    }
}

class BasicActionRunner : TaskerPluginRunnerActionNoOutputOrInput() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Basic", Toast.LENGTH_LONG).show() }
        return TaskerPluginResultSucess()
    }
}
