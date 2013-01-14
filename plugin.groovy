import com.intellij.openapi.diff.SimpleContent
import com.intellij.openapi.diff.impl.DiffPanelImpl
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePathImpl
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile

import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import java.awt.*
import java.text.SimpleDateFormat
import java.util.List


VirtualFile file = FileEditorManagerEx.getInstance(event.project).currentFile
AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(event.project).getVcsFor(file)
def filePath = new FilePathImpl(file)
def historySession = activeVcs.vcsHistoryProvider.createSessionFor(filePath)

//show(historySession.revisionList.join(", "))
def revisions = historySession.revisionList.sort { it.revisionDate }
if (revisions.size() < 2) return

def panel = new DiffPanelImpl((Window) null, event.project, (boolean) true, (boolean) true, (int) 0)
//DiffManager.getInstance().createDiffPanel(null, event.project, new Disposable() { @Override void dispose() {} })

panel.enableToolbar(false)
//panel.title1 = "left title"
//panel.title2 = "right title"

class MySimpleContent extends SimpleContent {
	VirtualFile virtualFile

	MySimpleContent(String text, VirtualFile virtualFile) {
		super(text, virtualFile.fileType)
		this.virtualFile = virtualFile
	}

	@Override VirtualFile getFile() {
		virtualFile
	}
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

def sliderPanel = new JPanel().with {
	def leftContent = new MySimpleContent(new String(revisions[0].content), file)
	def rightContent = new MySimpleContent(new String(revisions[1].content), file)
	panel.setContents(leftContent, rightContent)

	layout = new BorderLayout()
	add(createSliderPanel(revisions, 0, { VcsFileRevision revision ->
		leftContent = new MySimpleContent(new String(revision.content), file)
		panel.setContents(leftContent, rightContent)
	}), BorderLayout.NORTH)
	add(createSliderPanel(revisions, 1, { VcsFileRevision revision ->
		rightContent = new MySimpleContent(new String(revision.content), file)
		panel.setContents(leftContent, rightContent)
	}), BorderLayout.SOUTH)
	it
}

def frame = new JFrame()
def rootPanel = new JPanel()
rootPanel.layout = new BorderLayout()
rootPanel.add(panel.component, BorderLayout.CENTER)
rootPanel.add(sliderPanel, BorderLayout.SOUTH)
frame.add(rootPanel)
frame.pack()
frame.visible = true

