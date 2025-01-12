package com.smx.yapt

import com.smx.yapt.common.putIfAbsent
import com.smx.yapt.services.YapsSessionManager
import com.smx.yapt.ssh.CmAccess
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

class ConfigTreeCell(private val sessMan: YapsSessionManager) : TreeCell<ConfigCellContext>(), Initializable {
    @FXML
    lateinit var name: Label

    @FXML
    lateinit var valueBox: HBox

    @FXML
    lateinit var updateValue: Button

    @FXML
    lateinit var valueContainer: Pane

    private val cm get() = sessMan.currentSession.cm

    fun getNodePath(itm: TreeItem<ConfigCellContext>): String {
        val hierarchy = ArrayList<String>()

        var node: TreeItem<ConfigCellContext> = itm
        while (true) {
            val model = node.value
            hierarchy.add(model.displayName ?: break)
            node = node.parent ?: break
        }

        return hierarchy.asReversed().joinToString(".")
    }

    private fun getControlValue(control: Control): String {
        return when(control){
            is CheckBox -> if(control.isSelected) "true" else "false"
            is TextField -> control.text
            else -> throw NotImplementedError(control.javaClass.toString())
        }
    }

    fun onUpdateValue(ev: ActionEvent){
        val path = getNodePath(this.treeItem)
        val value = getControlValue(valueContainer.children.first() as Control)
        println("Set ${path} to \"${value}\"")
        cm.setValue(path, value)
    }



    /**
     * Render the Tree view model (in this case just a string)
     */
    override fun updateItem(item: ConfigCellContext?, empty: Boolean) {
        super.updateItem(item, empty)

        if(item == null || empty || item.displayName == null){
            valueBox.styleClass.putIfAbsent("c-hidden")
            contentDisplay = ContentDisplay.TEXT_ONLY
        } else {
            name.text = item.displayName
            valueBox.styleClass.removeAll("c-hidden")

            valueContainer.children.clear()
            if(item.node != null){
                val node = item.node
                valueBox.isDisable = node.props.access == CmAccess.READ_ONLY
                valueContainer.children.addAll(item.controls)
            }

            updateValue.isVisible = item.node != null && valueContainer.children.size > 0
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
        }
    }

    private fun createContextMenu(): ContextMenu {
        val cell = this
        val copyPathMenuItem = MenuItem("Copy Path").apply {
            onAction = EventHandler { event ->
                val nodePath = cell.getNodePath(cell.treeItem)
                val clip = Clipboard.getSystemClipboard()
                val content = ClipboardContent().apply {
                    putString(nodePath)
                }
                clip.setContent(content)
            }
        }

        return ContextMenu().apply {
            items.add(copyPathMenuItem)
        }
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        contextMenu = createContextMenu()
        val parent = item
        treeItemProperty().addListener { _, prevItem, item ->
            if(item == null || prevItem == item || item.value.expandListener != null) {
                return@addListener
            }

            val expandProp = item.expandedProperty() ?: return@addListener

            item.value.expandListener = ChangeListener<Boolean> { _, previousState, expanding ->
                if(previousState == expanding || !expanding) return@ChangeListener
                // this node is being expanded, check for leaf children

                val rootNodePath = getNodePath(item)

                // have we already fetched the model for this node?
                if (item.value.childrenModel == null) {
                    // look for this child's descendants
                    println("GETMDP $rootNodePath")
                    item.value.childrenModel = cm.getModelForParams("${rootNodePath}.", 0)
                }

                val model = item.value.childrenModel

                // create all properties (skip objects)
                val nodes = model
                    ?.getProperties(rootNodePath, 0)
                    ?.map { (k, node) ->
                        println("$k -> $node")

                        val item = cm.get(k).entries.first()
                            .let { ConfigCellContext(node.baseName, it.value, node) }
                            .let { TreeItem(it) }

                        item
                    } ?: emptyList()

                // remove old property nodes
                item.children
                    .filter {
                        val isDummy = it.value.displayName == null
                        // remove if property or dummy node
                        it.value.node?.isProperty ?: isDummy
                    }
                    .let { item.children.removeAll(it) }

                // add new nodes
                item.children.addAll(nodes)
            }
            expandProp.addListener(item.value.expandListener)
        }
    }
}