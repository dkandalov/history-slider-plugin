What is this?
=============
This is a proof-of-concept plugin for IntelliJ.
It used to show diff between two VCS revisions using sliders to choose revision.<br/><br/>
The idea was to make diffing files a bit easier than in "Show History" panel.
The problem is that it's important to keep diff scrollers at the same vertical position while changing file revisions.
I didn't find how to implement this.
(At the moment this plugin shows history for a method. Note that there is already "Method History" action in IntelliJ.)

History with sliders used to look like this (see two sliders at the bottom):
<img src="https://raw.github.com/dkandalov/history-slider-plugin/master/date_diff.png" alt="auto-revert screenshot" title="screenshot" align="left" />

(Note that this plugin will only work within [intellij-eval plugin](https://github.com/dkandalov/intellij_eval).)<br/>
