package com.smx.yapt

import com.airhacks.afterburner.injection.Injector
import com.smx.yapt.services.YapsSessionManager
import javafx.application.Application
import javafx.application.HostServices
import javafx.scene.Scene
import javafx.stage.Stage

class YaptApplication : Application() {
    private val sessMan = YapsSessionManager()

    override fun start(primaryStage: Stage) {
        Injector.setModelOrService(Stage::class.java, primaryStage)
        Injector.setModelOrService(HostServices::class.java, hostServices)
        Injector.setModelOrService(YapsSessionManager::class.java, sessMan)

        val root = ConfEditView()

        val stage = Stage().apply {
            title = "yapt"
            scene = Scene(root.view)
        }
        stage.show()
    }

    override fun stop() {
        sessMan.currentSession.close()
    }

    companion object {
        @JvmStatic
        fun main(args:Array<String>){
            launch(YaptApplication::class.java, *args)
        }
    }
}

class EntryPoint {
    companion object {
        @JvmStatic
        fun main(args:Array<String>){
            YaptApplication.main(args)
        }
    }
}