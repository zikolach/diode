package example

import diode.ActionResult.ModelUpdate
import diode._

import scala.annotation.tailrec

// define a recursive file/directory structure
sealed trait FileNode {
  def id: String
  def name: String
  def children: IndexedSeq[FileNode]
  def isDirectory: Boolean
}

final case class Directory(id: String, name: String, children: IndexedSeq[FileNode] = IndexedSeq.empty) extends FileNode {
  override def isDirectory = true
}

final case class File(id: String, name: String) extends FileNode {
  val children = IndexedSeq.empty[FileNode]
  override def isDirectory = false
}

case class Tree(root: Directory, selected: Seq[String])

// Define the root of our application model
case class RootModel(tree: Tree)

// Define actions
case class ReplaceTree(newTree: Directory)

// path is defined by a sequence of identifiers
case class AddNode(path: Seq[String], node: FileNode)

case class RemoveNode(path: Seq[String])

case class ReplaceNode(path: Seq[String], node: FileNode)

case class Select(selected: Seq[String])

/**
  * AppCircuit provides the actual instance of the `RootModel` and all the action
  * handlers we need. Everything else comes from the `Circuit`
  */
object AppCircuit extends Circuit[RootModel] {
  // define initial value for the application model
  var model = RootModel(Tree(Directory("", "", Vector.empty), Seq.empty))

  // zoom into the model, providing access only to the `root` directory of the tree
  val treeHandler = new DirectoryTreeHandler(
    zoomRW(_.tree)((m, v) => m.copy(tree = v))
      .zoomRW(_.root)((m, v) => m.copy(root = v)))

  // define an inline action handler for selections
  val selectionHandler = new ActionHandler(
    zoomRW(_.tree)((m, v) => m.copy(tree = v))
      .zoomRW(_.selected)((m, v) => m.copy(selected = v))) {
    override def handle = {
      case Select(sel) => updated(sel)
    }
  }

  override val actionHandler = combineHandlers(treeHandler, selectionHandler)
}

class DirectoryTreeHandler[M](modelRW: ModelRW[M, Directory]) extends ActionHandler(modelRW) {

  /**
    * Helper function to zoom into the directory hierarchy, delivering the `children` of the last directory.
    *
    * @param path Sequence of directory identifiers
    * @param rw Reader/Writer for current directory
    * @return
      * `Some(childrenRW)` if the directory was found or
    * `None` if something went wrong
    */
  @tailrec private def zoomToChildren(path: Seq[String], rw: ModelRW[M, Directory]): Option[ModelRW[M, IndexedSeq[FileNode]]] = {
    if (path.isEmpty) {
      Some(rw.zoomRW(_.children)((m, v) => m.copy(children = v)))
    } else {
      // find the index for the next directory in the path and make sure it's a directory
      rw.value.children.indexWhere(n => n.id == path.head && n.isDirectory) match {
        case -1 =>
          // should not happen!
          None
        case idx =>
          // zoom into the directory position given by `idx` and continue recursion
          zoomToChildren(path.tail, rw.zoomRW(_.children(idx).asInstanceOf[Directory])((m, v) =>
            m.copy(children = (m.children.take(idx) :+ v) ++ m.children.drop(idx + 1))
          ))
      }
    }
  }

  /**
    * Handle directory tree actions
    */
  override def handle = {
    case ReplaceTree(newTree) =>
      updated(newTree)
    case AddNode(path, node) =>
      // zoom to parent directory and add new node at the end of its children list
      zoomToChildren(path.tail, modelRW) match {
        case Some(rw) => ModelUpdate(rw.updated(rw.value :+ node))
        case None => noChange
      }
    case RemoveNode(path) =>
      if (path.init.nonEmpty) {
        // zoom to parent directory and remove node from its children list
        val nodeId = path.last
        zoomToChildren(path.init.tail, modelRW) match {
          case Some(rw) => ModelUpdate(rw.updated(rw.value.filterNot(_.id == nodeId)))
          case None => noChange
        }
      } else {
        // cannot remove root
        noChange
      }
    case ReplaceNode(path, node) =>
      if (path.init.nonEmpty) {
        // zoom to parent directory and replace node in its children list with a new one
        val nodeId = path.last
        zoomToChildren(path.init.tail, modelRW) match {
          case Some(rw) => ModelUpdate(rw.updated(rw.value.map(n => if (n.id == nodeId) node else n)))
          case None => noChange
        }
      } else {
        // cannot replace root
        noChange
      }
  }
}
