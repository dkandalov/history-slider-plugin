import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.DiffContent
import com.intellij.openapi.diff.DiffRequest
import com.intellij.openapi.diff.SimpleContent
import com.intellij.openapi.diff.impl.DiffPanelImpl
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePathImpl
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile

import static intellijeval.PluginUtil.*

import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import java.awt.*
import java.text.SimpleDateFormat
import java.util.List

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

	//show(historySession.revisionList.join(", "))
	def revisions = historySession.revisionList.sort{ it.revisionDate }
	if (revisions.size() < 2) {
		show("There is only one revision for '${file.name}'")
		return
	}

	def diffPanel = new DiffPanelImpl((Window) null, event.project, (boolean) true, (boolean) true, (int) 0)
	diffPanel.enableToolbar(false)
	//diffPanel.title1 = "left title"
	//diffPanel.title2 = "right title"
	def leftContent = new MySimpleContent(new String(revisions[0].content), file)
	def rightContent = new MySimpleContent(new String(revisions[1].content), file)
	def diffRequest = new DiffRequest(event.project) {
		@Override DiffContent[] getContents() { [leftContent, rightContent] }
		@Override String[] getContentTitles() { ["left", "right"] }
		@Override String getWindowTitle() { "" }
	}
	diffPanel.diffRequest = diffRequest
	// TODO use "generic data" with "DiffTool.SCROLL_TO_LINE" (see com.intellij.openapi.diff.impl.DiffPanelImpl.MyScrollingPanel#scrollEditors)

	def sliderPanel = new JPanel().with {
		layout = new BorderLayout()
		add(createSliderPanel(revisions, 0, { VcsFileRevision revision ->
			leftContent = new MySimpleContent(new String(revision.content), file)
			diffPanel.setContents(leftContent, rightContent)
		}), BorderLayout.NORTH)
		add(createSliderPanel(revisions, 1, { VcsFileRevision revision ->
			rightContent = new MySimpleContent(new String(revision.content), file)
			diffPanel.setContents(leftContent, rightContent)
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
		pack()
		visible = true
	}
}
show("reloaded")

class MySimpleContent extends SimpleContent {
	private final VirtualFile virtualFile

	MySimpleContent(String text, VirtualFile virtualFile) {
		super(text, virtualFile.fileType)
		this.virtualFile = virtualFile
	}

	@Override VirtualFile getFile() { virtualFile }
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


