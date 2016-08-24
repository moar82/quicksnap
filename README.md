This app was refactored using EARMO (Energy-aware Refactoring approach for MObile apps)
For More information visit http://swatlab.polymtl.ca/EARMO/

QuickSnap
=========

Android camera implementation based off the official Android Gingerbread camera app source code.

Initially the Android Gingerbread camera source was forked, and then modified to become backwards compatible down to API level 7 (Eclair). The video functionality has also been removed.

The code was then further modified to support the following devices that did not work correctly:
* HTC Evo
* HTC Desire S
* Samsung Captivate
* LG G2X
* LG Optimus 2X
These were primarily devices that had trouble with their front facing cameras.

In the original Gingerbread stock camera app there was a second confirmation step after each photo was taken. This has been removed to make it easier and quicker to take several photos in quick succession.

Additional work has been done to de-activiate the shutter sound and animate the preview, which can be selected via the settings.

## License
Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.html)
