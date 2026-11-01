lebHTE 1.0 — Linux + Java 21 // JAR
==================================
Installation (Debian family):

  0.(if java do not exist or old) sudo apt install openjdk-21-jre
  1. run plebhte_1.0.0-1_all.deb

Installation (java):
  0.(if java do not exist or old) sudo apt install openjdk-21-jre
  1. java -jar PlebHTE.jar




Requirements:
  A desktop session (Cinnamon/MATE/Xfce etc.) and Java 21 or newer ( use "java -version" to check)
  On Linux: sudo apt install openjdk-21-jre

Data:
  New documents are stored at /notes/
  Existing .html and .txt notes can be opened using Load.
  Existing notes in your old project's notes/ directory are NOT migrated.
  Copy them manually to the above path if you want them visible to links.
  Preferences are stored via Java Preferences, in the user profile.

Uninstall:
  sudo apt remove plebhte (for debian installers)
  The uninstall script deliberately keeps all your notes and preferences.

Features:
  HTML visual editor; 
  [[Wiki Links]]; 
  Ctrl+S/F5, Ctrl+B/I/U; unsaved changes confirmation.
