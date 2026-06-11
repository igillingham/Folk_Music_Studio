# Folk Music Studio - User Manual

## 1. Introduction
Folk Music Studio is an open-source Android application designed for musicians who use ABC notation. It allows you to store, manage, view, and play music directly from your Android device, making it an ideal companion for sessions, gigs, and practice.

---

## 2. Getting Started

### Installation
Since Folk Music Studio is open-source and distributed via GitHub:
1.  Download the `.apk` file from the [Releases](https://github.com/iangillingham/Music_ABC/releases) tab.
2.  Open the downloaded file.
3.  If prompted, go to **Settings** and toggle on **"Allow from this source"** for your browser or file manager.
4.  Tap **Install**.

### Initial Setup
When you first open the app:
1.  Tap the **Menu** icon (three horizontal lines) in the top-left corner.
2.  Tap **Setup Storage** (the little gear icon).
3.  Choose **Add Folder** to select a directory containing your `.abc` files, or **Add Files** to select individual files.
4.  Grant the necessary permissions for the app to access your files.

---

## 3. The Interface

### Main Screen
- **Toolbar**: Contains navigation, playback, and editing controls.
- **Visualizer**: The large central area where the sheet music is rendered.
- **Editor**: A text area (visible in Edit mode) for modifying the ABC notation.

### Side Drawer (Tune Library)
Swipe from the left edge or tap the **Menu** icon to see:
- **Tune List**: All tunes found in your selected files.
- **Search/Refresh**: Refresh the library to detect new files.
- **Actions**: Create a **New Tune**, access **Setup Storage**, or view **About**.

---

## 4. Managing Your Library

### Adding Music
You can sync folders from your device or cloud storage (like Google Drive). The app will automatically parse all `.abc` files in the selected locations.

### Organizing Tunes
- **Multi-select**: Long-press a tune in the list to enter selection mode.
- **Bulk Copy**: Select multiple tunes and copy them to a new or existing `.abc` file.
- **Bulk Delete**: Remove multiple tunes from your library at once.

---

## 5. Viewing and Playing Tunes

### Rendering
Tap any tune in the library to display the sheet music. The app automatically renders the dots based on the ABC notation.

### Playback
- **Play/Pause/Stop**: Use the controls in the toolbar to hear a midi representation of the tune.
- **Tempo**: Use the slider to adjust the playback speed (BPM). If the tune has a `Q:` field, it will be used as the default.

---

## 6. Editing Tunes

### Live Editor
Tap the **Edit** (Pencil) icon to open the editor.
- **Adaptive Layout**: On phones, the screen automatically adjusts. In portrait, the notation is on top and the editor below. In landscape, they appear side-by-side for better use of space.
- Changes made in the text editor are updated in the notation in real-time.
- Tap the **Save** icon to commit changes to the source file.

### Creating New Tunes
1.  Open the Side Drawer.
2.  Tap **New Tune**.
3.  A template will be provided. Once you've written your tune, tap **Save New** or **Append** to add it to a file.

---

## 7. Tips & Troubleshooting

- **Playback stops**: This is a known issue being addressed. If playback hangs, try pressing Stop and then Play again.
- **Google Drive**: For the best experience with cloud storage, ensure the files are marked as "Available offline" in the Google Drive app.
- **Feedback**: If you find a bug or have a feature request, contact `music@iangillingham.net`.

---

## 8. License
Folk Music Studio is open-source software under the **GPL-3.0 License**. No ads, no fees.
