# Introduction
In my endeavours to find an android app to read ABC notation files, list the tunes in those files and 
render the dots, it became clear that there is very little of note out there.
So, I decided to sit down and write my own android app to provide all the functionality that I'm
looking for.

This app is primarily targeted at, but not limited to folk musicians, with a need to store, 
retrieve and share music with others at sessions and gigs. 

The application and all source code is open source under the GPL-3.0 licence. 
There will never be a fee or ads, just a quality app which I hope will be helpful to fellow musicians.


# Functionality
This includes:
- Allow the use to select ABC files from a directory, including cloud storage, such as Google Drive.
- The tunes within those files will then be listed and selectable.
- Other ABC files can be added to populate the tunes list further
- Selecting a tune will render it as music notation (sheet music)
- The ABC text can be edited and changes updating the rendered music
- The tune can be played, using a rudimentary midi player
- The playback tempo cam be adjusted

Known issues to be addressed:
- Playback sometimes stops after a minute or so.
- It is possible for multiple playback threads to startup under certain circumstances. Just wait for everything to finish or try pressing the 'stop' button.

Features for future releases:
- Facilitate playing back from any given position in the score.
- Provide an option to not play repeats
- Facilitate printing from within the app
- Facilitate sharing either the ABC file or the rendered dots (as PDF or JPG)

I want future development to be community driven. What I have provided here is just the foundation 
for something for every folk (or other) musician's needs.
Please contribute change requests and ideas - what would really be useful to you.

Anyone visiting my GitHub repository can click on the Releases tab, download the .apk file, and install it. However, because it isn't coming from the Google Play Store, you will need to follow these steps on your Android device:

1) Download: Open the GitHub release link on their Android device and download the .apk file.

2) Enable Unknown Sources: When they tap the downloaded file to install it, Android will pop up a security warning stating that the browser isn't allowed to install unknown apps.

3) Grant Permission: They must tap Settings on that popup and toggle on "Allow from this source".

4) Install: Go back and tap Install to finish the process.

# Contributing
If you have any requests for features. changes or bug repports, please feel free to contact me:
music@iangillingham.net
