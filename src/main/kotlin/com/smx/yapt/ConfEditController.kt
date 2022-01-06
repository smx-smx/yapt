package com.smx.yapt

import com.smx.yapt.services.YapsSessionManager
import com.smx.yapt.ssh.IYapsSession
import com.smx.yapt.ssh.SshSessionFactory
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.util.Callback
import java.net.URL
import java.util.*
import javax.inject.Inject

typealias DomLevel = HashMap<String, Any>

interface CmValueFactory<T> {
    fun parse(data: String): T
}


class ConfEditController : Initializable {
    @FXML
    private lateinit var tree: TreeView<ConfigCellContext>

    @FXML
    private lateinit var ipAddr: TextField

    @FXML
    private lateinit var password: PasswordField

    @Inject
    private lateinit var sessMan:YapsSessionManager
    private val session:IYapsSession get() = sessMan.currentSession

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        val fxmlResource = javaClass.getResource("config_node.fxml")
        tree.cellFactory = Callback { _ ->
            val obj = FXMLLoader(fxmlResource).also {
                val root = ConfigTreeCell(sessMan)
                it.setRoot(root)
                it.controllerFactory = Callback { root }
            }.load<ConfigTreeCell>()
            obj
        }
    }

    private fun populateTreeView(){
        var currentLevel = DomLevel()
        val rootLevel = currentLevel

        val cm = session.cm
        cm.getObject("Device.")
            .forEach { node ->
                val path = node.split('.')
                // convert path to linked list
                // e.g. Device.Services.VoiceServices
                // Device -> Services -> VoiceServices
                //   ^    --> .....
                path.forEach { k ->
                    // check if the current root is already e.g "Device"
                    currentLevel = currentLevel.getOrPut(k){ DomLevel() } as DomLevel
                }
                // reset the pointer to the start
                currentLevel = rootLevel
            }

        val root = createTree(rootLevel)
        tree.root = root.first()
    }

    private fun createTree(topLevel: DomLevel) : List<TreeItem<ConfigCellContext>> {
        val nodes = topLevel.map { e ->
            // key is the node name, value are the child nodes
            val node = TreeItem(ConfigCellContext(e.key, null, null))
            val childNodes = createTree(e.value as DomLevel)
            if(childNodes.isEmpty()){
                // we don't have child objects, but we have properties
                // since we don't know them yet (we lazily expand)
                // we add a dummy node so that the user can expand this node
                node.children.add(TreeItem(ConfigCellContext(null, null, null)))
            }
            node.children.addAll(childNodes)
            node
        }
        return nodes
    }

    fun onConnect(ev: ActionEvent) {
        sessMan.currentSession = SshSessionFactory().also {
            it.hostname = ipAddr.text
            it.password = password.text
        }.connect()

        populateTreeView()
    }
}