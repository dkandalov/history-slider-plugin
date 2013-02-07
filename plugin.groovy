import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.DiffContent
import com.intellij.openapi.diff.DiffRequest
import com.intellij.openapi.diff.SimpleContent
import com.intellij.openapi.diff.impl.DiffPanelImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePathImpl
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod

import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import java.awt.*
import java.text.SimpleDateFormat
import java.util.List

import static intellijeval.PluginUtil.*
import static infer.MatchUtil.*

registerAction("showHistorySliderAction", "alt shift H", "ToolsMenu") { AnActionEvent event ->
	VirtualFile file = currentFileIn(event.project)
	if (file == null) {
		show("There are no open file editors")
		return
	}

	AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(event.project).getVcsFor(file)
	if (activeVcs == null) {
		show("There is no history for open file '${file.name}'")
		return
	}

	def historySession = activeVcs.vcsHistoryProvider.createSessionFor(new FilePathImpl(file))
	def revisions = historySession.revisionList.sort{ it.revisionDate }
	if (revisions.size() < 2) {
		show("There is only one revision for '${file.name}'")
		return
	}

	def editor = currentEditorIn(event.project)
	def psiElement = currentPsiFileIn(event.project)?.findElementAt(editor.caretModel.offset)
	def psiMethod = findParent(psiElement, {it instanceof PsiMethod})

	def extractDiffContentFrom = { text ->
		if (psiMethod == null) return text

		def psiFile = PsiFileFactory.getInstance(event.project).createFileFromText(file.name, file.fileType, text)
		def psiMethods = psiFile.children.findAll{it instanceof PsiClass}.collect { psiClass ->
			psiClass.children.findAll{it instanceof PsiMethod}
		}.flatten()

		def methodText = psiMethods.find{it.name == psiMethod.name}?.text
		methodText != null ? methodText : ""
	}

	def diffPanel = new DiffPanelImpl((Window) null, event.project, (boolean) true, (boolean) true, (int) 0)
	diffPanel.enableToolbar(false)
	def leftContent = new MySimpleContent(extractDiffContentFrom(new String(revisions[0].content)), file)
	def rightContent = new MySimpleContent(extractDiffContentFrom(new String(revisions[1].content)), file)
	diffPanel.diffRequest = new MyDiffRequest(leftContent, rightContent)

	def sliderPanel = new JPanel().with {
		layout = new BorderLayout()
		add(createSliderPanel(revisions, 0, { VcsFileRevision revision ->
			catchingAlll {
				leftContent = new MySimpleContent(extractDiffContentFrom(new String(revision.content)), file)
				diffPanel.diffRequest = withKeepingScrollPositionIn(diffPanel.editor1){ new MyDiffRequest(leftContent, rightContent) }
			}
		}), BorderLayout.NORTH)
		add(createSliderPanel(revisions, 1, { VcsFileRevision revision ->
			catchingAlll {
				rightContent = new MySimpleContent(extractDiffContentFrom(new String(revision.content)), file)
				diffPanel.diffRequest = withKeepingScrollPositionIn(diffPanel.editor2){ new MyDiffRequest(leftContent, rightContent) }
			}
		}), BorderLayout.SOUTH)
		it
	}

	new JFrame().with {
		add(new JPanel().with {
			layout = new BorderLayout()
			add(diffPanel.component, BorderLayout.CENTER)
			add(sliderPanel, BorderLayout.SOUTH)
			it
		})
		preferredSize = new Dimension(1600, 800)
		pack()
		visible = true
	}
}
show("reloaded")


def <T> T findParent(PsiElement element, Closure<T> matches) {
	if (element == null) null
	else if (matches(element)) element
	else findParent(element.parent, matches)
}

class MyDiffRequest extends DiffRequest {
	private final leftContent
	private final rightContent

	MyDiffRequest(leftContent, rightContent) {
		super(null)
		this.leftContent = leftContent
		this.rightContent = rightContent
	}

	@Override DiffContent[] getContents() { [leftContent, rightContent] }
	@Override String[] getContentTitles() { ["left", "right"] }
	@Override String getWindowTitle() { "" }
}

class MySimpleContent extends SimpleContent {
	private final VirtualFile virtualFile

	MySimpleContent(String text, VirtualFile virtualFile) {
		super(text, virtualFile.fileType)
		this.virtualFile = virtualFile
	}

	@Override VirtualFile getFile() { virtualFile }
}

DiffRequest withKeepingScrollPositionIn(Editor editor, Closure<DiffRequest> closure) {
	// TODO this was a desparate attemp to make vertical scroll stay in the position/line whil diff panel content
	// problems:
	//  - diff "flickers" when changing date (something like scroll to 1st line, scroll to last position)
	//  - scrolling to last position is not stable; need to check algorithm for DiffNavigationContext positioning or fail back to line number

	if (false) {
		def visibleArea = editor.scrollingModel.visibleArea
		// "height / 3" ugly hack was copied from com.intellij.openapi.diff.impl.util.SyncScrollSupport#syncVerticalScroll
		def lineNumber = editor.xyToLogicalPosition(new Point((int) visibleArea.x, (int) visibleArea.y + visibleArea.height / 3)).line
		def lines = editor.document.text.split("\n").toList()
		def fromLine = (lineNumber - 10 < 0) ? 0 : lineNumber - 10
		def prevLines = lines[fromLine..<lineNumber]
		def line = lines[lineNumber]
//	show(line)
//	show(prevLines.join("\n'"))
//	show(line)
	}

	def diffRequest = closure.call()
//	diffRequest.passForDataContext(DiffTool.SCROLL_TO_LINE, new DiffNavigationContext(prevLines, line))
	diffRequest
}

def createSliderPanel(List<VcsFileRevision> revisions, int sliderPosition, Closure sliderCallback) {
	def dateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm")

	def revisionToString = { int revisionIndex ->
		def revision = revisions[revisionIndex]
		"Date: " + (revision == null ? "unknown" : dateFormat.format(revision.revisionDate))
	}

	new JPanel().with{
		def slider = new JSlider(JScrollBar.HORIZONTAL, 0, revisions.size() - 1, sliderPosition)
		def label = new JLabel()
		label.text = revisionToString(slider.value)

		layout = new BorderLayout()
		add(label, BorderLayout.WEST)
		add(slider, BorderLayout.CENTER)
		slider.addChangeListener(new ChangeListener() {
			@Override void stateChanged(ChangeEvent event) {
				label.text = revisionToString(slider.value)
				sliderCallback.call(revisions[slider.value])
			}
		})
		it
	}
}

def catchingAlll(Closure closure) {
	try {

		closure.call()

	} catch (Exception e) {
		ProjectManager.instance.openProjects.each { Project project ->
			showInConsole(e, e.class.simpleName, project)
		}
	}
}
