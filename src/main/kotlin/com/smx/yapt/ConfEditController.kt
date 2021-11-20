package com.smx.yapt

import com.smx.yapt.services.YapsSessionManager
import com.smx.yapt.ssh.*
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.util.Callback
import java.net.URL
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread

typealias DomLevel = HashMap<String, Any>

data class CmModelItem(val path: String, val model: CmModel){
    val fullName: String get() = path.trimEnd('.')

    val baseName: String get(){
        val tmp = fullName
        val lastDot = tmp.lastIndexOf('.')
        return if(lastDot == -1)
            tmp
        else
            tmp.substring(lastDot + 1)
    }


    val isReadOnly: Boolean get() = model["access"]?.equals("ro") ?: false
    val isObject: Boolean get() = path.endsWith('.')
    val isProperty: Boolean get() = !path.endsWith('.')
}

typealias CmModelTreeNode = MutableMap.MutableEntry<String, CmModel>

class CmModelTree : HashMap<String, CmModel>() {
    val isLeaf get() = this.none { it.key.endsWith(".") }
    val objects get() = this.entries.filter { it.key.endsWith(".") }
    val properties get() = this.entries.filter { !it.key.endsWith(".") }

    private fun filterByDepth(
        items: List<CmModelTreeNode>,
        root: String,
        depth: Int = 0
    ): List<CmModelTreeNode> {
        val wantedNumDots = depth + 1
        return items
            .filter { it.key.indexOf(root) == 0}
            .map {
                // remove the object marker at the end
                val name = it.key.let {
                    when(it.lastOrNull()) {
                        '.' -> it.dropLast(1)
                        else -> it
                    }
                }

                // Device.Hosts[.Host.5.X_ADB_LastUp]
                val subPath = name.substring(root.length)
                val numDots = subPath.count { it == '.' }
                Pair(numDots, it)
            }
            // skip nested properties (if any)
            .filter { (numDots, _) -> numDots == wantedNumDots }
            .map { (_, e) -> e }
    }

    fun getObjects(root: String, depth: Int = 0) : List<CmModelTreeNode> {
        return filterByDepth(properties, root, depth)
    }

    fun getProperties(root:String, depth:Int = 0): List<CmModelTreeNode> {
        return filterByDepth(properties, root, depth)
    }
}

data class ConfigCellViewModel(
    val displayName: String?,
    val displayValue: String?,
    val modelItem: CmModelItem?
){
    //val isObject:Boolean get()

    var childrenModel: CmModelTree? = null
    var expandListener:ChangeListener<Boolean>? = null
}

class ConfigTreeCell(private val sessMan:YapsSessionManager) : TreeCell<ConfigCellViewModel>(), Initializable {
    @FXML
    lateinit var name:Label

    @FXML
    lateinit var valueBox:HBox

    @FXML
    lateinit var value:TextField

    @FXML
    lateinit var updateValue:Button

    private val cm get() = sessMan.currentSession.cm

    fun getNodePath(itm: TreeItem<ConfigCellViewModel>): String {
        val hierarchy = ArrayList<String>()

        var node: TreeItem<ConfigCellViewModel> = itm
        while (true) {
            val model = node.value
            hierarchy.add(model.displayName ?: break)
            node = node.parent ?: break
        }

        return hierarchy.asReversed().joinToString(".")
    }

    fun onUpdateValue(ev: ActionEvent){
        val path = getNodePath(this.treeItem)
        val value = value.text
        println("Set ${path} to \"${value}\"")
        cm.setValue(path, value)
    }

    /**
     * Render the Tree view model (in this case just a string)
     */
    override fun updateItem(item: ConfigCellViewModel?, empty: Boolean) {
        super.updateItem(item, empty)

        if(item == null || empty){
            styleClass.putIfAbsent("c-hidden")
            contentDisplay = ContentDisplay.TEXT_ONLY
            return
        }
        styleClass.removeAll("c-hidden")

        name.text = item.displayName
        if(item.displayValue == null){
            valueBox.styleClass.putIfAbsent("c-hidden")
        } else {
            if(item.modelItem != null){
                val m = item.modelItem
                valueBox.isDisable = m.isReadOnly
            }


            valueBox.styleClass.removeAll("c-hidden")
            value.text = item.displayValue
        }

        contentDisplay = ContentDisplay.GRAPHIC_ONLY
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        treeItemProperty().addListener { _, prevItem, item ->
            if(item == null || prevItem == item || item.value.expandListener != null) {
                return@addListener
            }

            val expandProp = item.expandedProperty() ?: return@addListener

            item.value.expandListener = ChangeListener<Boolean> { _, previousState, expanding ->
                if(previousState == expanding || !expanding) return@ChangeListener
                // this node is being expanded, check for leaf children

                thread {
                    val rootNodePath = getNodePath(item)

                    // have we already fetched the model for this node?
                    if(item.value.childrenModel == null){
                        // look for this child's descendants
                        println("GETMDP $rootNodePath")
                        item.value.childrenModel = cm.getModelForParams("${rootNodePath}.", 0)
                    }
                    val model = item.value.childrenModel ?: return@thread

                    // fetch all properties (skip objects)
                    val nodes = model
                        .getProperties(rootNodePath, 0)
                        .map { (k, m) ->
                            println("$k -> $m")

                            val modelItem = CmModelItem(k, m)
                            val item = cm.get(k).entries.first()
                                .let { ConfigCellViewModel(modelItem.baseName, it.value, modelItem) }
                                .let { TreeItem(it) }

                            item
                        }

                    // remove and re-create property nodes
                    Platform.runLater {
                        // remove property nodes
                        item.children
                            .filter {
                                val isDummy = it.value.displayName == null
                                // remove if property or dummy node
                                it.value.modelItem?.isProperty ?: isDummy
                            }
                            .let { item.children.removeAll(it) }
                        // add new nodes
                        item.children.addAll(nodes)
                    }
                }
            }
            expandProp.addListener(item.value.expandListener)
        }
    }
}

private fun <E> MutableList<E>.putIfAbsent(e: E) {
    if(!this.contains(e)) this.add(e)
}

class ConfEditController : Initializable {
    @FXML
    private lateinit var tree: TreeView<ConfigCellViewModel>

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

    private fun createTree(topLevel: DomLevel) : List<TreeItem<ConfigCellViewModel>> {
        val nodes = topLevel.map { e ->
            // key is the node name, value are the child nodes
            val node = TreeItem(ConfigCellViewModel(e.key, null, null))
            val childNodes = createTree(e.value as DomLevel)
            if(childNodes.isEmpty()){
                // we don't have child objects, but we have properties
                // since we don't know them yet (we lazily expand)
                // we add a dummy node so that the user can expand this node
                node.children.add(TreeItem(ConfigCellViewModel(null, null, null)))
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