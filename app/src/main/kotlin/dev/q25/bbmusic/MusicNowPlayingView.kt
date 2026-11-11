package dev.q25.bbmusic

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.q25.bbos6.ui.Bbos6FocusPolicy
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingControlKind
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingControlSelectionView
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingPanel
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingSeekRailView
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingSurfaceView
import dev.q25.bbos6.ui.compose.Bbos6NowPlayingTextAlignment

/**
 * Kotlin presentation and input controller for the BBOS6 Now Playing page.
 *
 * All four chrome surfaces, selection faces, metadata plates, text and cover
 * are canonical shared Kotlin Compose widgets. The native SeekBar and
 * ImageViews retain the already-tested Q25 touch/DPAD gesture and image APIs.
 * This class does not own Media3 state or a service.
 */
class MusicNowPlayingView(
    context: Context,
    private val client: MusicPlaybackClient,
    private val metadataNavigation: MetadataNavigation
) : LinearLayout(context) {
    interface MetadataNavigation {
        fun albumFor(item: MediaItem): CharSequence?
        fun openArtist()
        fun openAlbum()
    }

    private companion object {
        const val ZONE_MODE = 0
        const val ZONE_ARTIST = 1
        const val ZONE_ALBUM = 2
        const val ZONE_SEEK = 3
        const val ZONE_TRANSPORT = 4
    }

    private val artworkLoader = MusicArtworkLoader(context)
    private val modeRepeatFace = Bbos6NowPlayingControlSelectionView(
        context, Bbos6NowPlayingControlKind.MODE
    )
    private val modeShuffleFace = Bbos6NowPlayingControlSelectionView(
        context, Bbos6NowPlayingControlKind.MODE
    )
    private val modeRepeat = ImageView(context)
    private val modeShuffle = ImageView(context)
    private val queuePosition = MusicPlayerTextView(
        context, "0 of 0", 14f, Color.WHITE,
        Bbos6NowPlayingTextAlignment.CENTER, 1, 0, true, true
    )
    private val artworkImage = MusicNowPlayingArtworkView(context)
    private val trackTitle = MusicPlayerTextView(
        context, "Now playing", 18f, Color.WHITE,
        Bbos6NowPlayingTextAlignment.CENTER, 3, 0, true, false
    )
    private val trackArtist = MusicBeveledTextLabel(context, 13f)
    private val trackAlbum = MusicBeveledTextLabel(context, 12f)
    private val elapsed = MusicPlayerTextView(
        context, "0:00", 14f, 0xff99aab8.toInt(),
        Bbos6NowPlayingTextAlignment.START, 1, 34, false, true
    )
    private val remaining = MusicPlayerTextView(
        context, "--:--", 14f, 0xff99aab8.toInt(),
        Bbos6NowPlayingTextAlignment.END, 1, 42, false, true
    )
    private val seekBar = SeekBar(context)
    private val seekRail = Bbos6NowPlayingSeekRailView(
        context, MediaSeekPolicy.PROGRESS_MAX,
        MusicPlayerChrome.SEEK_END_GUARD_DP,
        MusicPlayerChrome.SEEK_TRACK_HEIGHT_DP
    )
    private val transportRow = LinearLayout(context)
    private val transportButtons = Array(4) { ImageView(context) }
    private val transportSlots = Array(4) { FrameLayout(context) }
    private val transportFaces = Array(4) {
        Bbos6NowPlayingControlSelectionView(
            context, Bbos6NowPlayingControlKind.TRANSPORT
        )
    }
    private val ticker = Handler(Looper.getMainLooper())
    private var zone = ZONE_TRANSPORT
    private var selectedButton = 1
    private var selectedMode = 0
    private var seekEditing = false
    private var seekDragging = false
    private var shownArtworkUri: String? = null
    private var durationMs = 0L
    private var active = true
    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!active) return
            renderProgress()
            ticker.postDelayed(this, 1000L)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true

        val modePanel = panel(context, Bbos6NowPlayingPanel.MODE)
        val modeHeader = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }
        modePanel.addView(modeHeader, frameMatch())
        addView(modePanel, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, dp(MusicPlayerChrome.MODE_HEADER_HEIGHT_DP)
        ))

        val repeatSlot = FrameLayout(context)
        repeatSlot.addView(modeRepeatFace, frameMatch())
        modeRepeat.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 0)
            contentDescription = "Repeat unavailable"
            setOnClickListener {
                zone = ZONE_MODE
                selectedMode = 0
                requestFocus()
                client.toggleRepeat()
                render()
            }
        }
        repeatSlot.addView(modeRepeat, frameMatch())
        modeHeader.addView(repeatSlot, LinearLayout.LayoutParams(
            dp(48), LayoutParams.MATCH_PARENT
        ))
        modeHeader.addView(queuePosition, LinearLayout.LayoutParams(
            0, LayoutParams.MATCH_PARENT, 1f
        ))

        val shuffleSlot = FrameLayout(context)
        shuffleSlot.addView(modeShuffleFace, frameMatch())
        modeShuffle.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 0)
            contentDescription = "Shuffle unavailable"
            setOnClickListener {
                zone = ZONE_MODE
                selectedMode = 1
                requestFocus()
                client.toggleShuffle()
                render()
            }
        }
        shuffleSlot.addView(modeShuffle, frameMatch())
        modeHeader.addView(shuffleSlot, LinearLayout.LayoutParams(
            dp(48), LayoutParams.MATCH_PARENT
        ))

        val artStage = panel(context, Bbos6NowPlayingPanel.ARTWORK)
        addView(artStage, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val artInfo = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            clipChildren = false
        }
        artStage.addView(artInfo, frameMatch())
        val artArea = FrameLayout(context)
        artInfo.addView(artArea, LinearLayout.LayoutParams(
            0, LayoutParams.MATCH_PARENT, 1f
        ))
        artworkImage.contentDescription = "Album artwork"
        artArea.addView(artworkImage, frameMatch())
        artworkImage.setImageResource(R.drawable.bbos6_album_no_art)

        val trackInfo = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            clipChildren = false
            clipToPadding = false
        }
        artInfo.addView(trackInfo, LinearLayout.LayoutParams(
            0, LayoutParams.MATCH_PARENT, 1f
        ))
        trackInfo.addView(trackTitle, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ))
        trackArtist.isFocusable = false
        trackArtist.setOnClickListener { activateMetadata(ZONE_ARTIST) }
        trackInfo.addView(trackArtist, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6)
        })
        trackAlbum.isFocusable = false
        trackAlbum.setOnClickListener { activateMetadata(ZONE_ALBUM) }
        trackInfo.addView(trackAlbum, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
        })

        val seekRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        val seekPanel = panel(context, Bbos6NowPlayingPanel.SEEK)
        seekPanel.addView(seekRow, frameMatch())
        addView(seekPanel, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, dp(MusicPlayerChrome.SEEK_PANEL_HEIGHT_DP)
        ))
        seekRow.addView(elapsed, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT
        ))

        seekBar.apply {
            max = MediaSeekPolicy.PROGRESS_MAX
            background = null
            isFocusable = false
            isFocusableInTouchMode = false
            val thumbHalf = dp(MusicPlayerChrome.SEEK_THUMB_WIDTH_DP) / 2
            val thumbInset = thumbHalf + dp(MusicPlayerChrome.SEEK_END_GUARD_DP)
            setPadding(thumbInset, 0, thumbInset, 0)
            thumbOffset = thumbHalf
            progressTintList = null
            progressBackgroundTintList = null
            secondaryProgressTintList = null
            progressDrawable = ColorDrawable(Color.TRANSPARENT)
            thumbTintList = null
            thumb = MusicPlayerChrome.seekThumb(context, false, false)
            contentDescription = "Playback position"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {
                    seekDragging = true
                    zone = ZONE_SEEK
                    seekEditing = false
                    requestFocus()
                    renderFocus()
                }
                override fun onProgressChanged(
                    bar: SeekBar, progress: Int, fromUser: Boolean
                ) {
                    bar.invalidate()
                    seekRail.updateProgress(progress)
                    if (fromUser && seekDragging) {
                        val position = MediaSeekPolicy.fromProgress(progress, durationMs)
                        elapsed.setText(MediaSeekPolicy.formatTime(position))
                        remaining.setText(MediaSeekPolicy.formatRemainingTime(
                            durationMs, position
                        ))
                    }
                }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    seekDragging = false
                    client.seek(MediaSeekPolicy.fromProgress(bar.progress, durationMs))
                    renderProgress()
                }
            })
        }

        val seekTrack = FrameLayout(context).apply {
            addView(seekRail, frameMatch())
            addView(seekBar, frameMatch())
        }
        seekRow.addView(seekTrack, LinearLayout.LayoutParams(
            0, LayoutParams.MATCH_PARENT, 1f
        ).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
        })
        seekRow.addView(remaining, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT
        ))

        transportRow.gravity = Gravity.CENTER_VERTICAL
        transportRow.orientation = HORIZONTAL
        val transportPanel = panel(context, Bbos6NowPlayingPanel.TRANSPORT)
        transportPanel.addView(transportRow, frameMatch())
        addView(transportPanel, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, dp(MusicPlayerChrome.TRANSPORT_HEIGHT_DP)
        ))
        val labels = arrayOf("Previous", "Play or pause", "Stop", "Next")
        for (i in transportButtons.indices) {
            val slot = transportSlots[i]
            val face = transportFaces[i]
            slot.addView(face, frameMatch())
            val button = transportButtons[i]
            button.apply {
                scaleType = ImageView.ScaleType.CENTER
                setImageDrawable(MusicPlayerChrome.transportIcon(context, i, false))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                contentDescription = labels[i]
                setOnClickListener {
                    zone = ZONE_TRANSPORT
                    selectedButton = i
                    seekEditing = false
                    requestFocus()
                    activate()
                }
            }
            slot.addView(button, frameMatch())
            transportRow.addView(slot, LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f
            ))
        }
        transportRow.foreground = MusicPlayerChrome.transportSeparators(
            context, Array<View>(transportSlots.size) { transportSlots[it] }
        )
        // Only canonical BBOS6 selection faces own custom focus appearance.
        Bbos6FocusPolicy.forManagedContainers(this)
        render()
        ticker.postDelayed(tick, 1000L)
    }

    private fun panel(context: Context, kind: Bbos6NowPlayingPanel): FrameLayout =
        FrameLayout(context).apply {
            addView(Bbos6NowPlayingSurfaceView(context, kind), frameMatch())
        }

    private fun frameMatch(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )

    private fun dp(value: Float): Int = MusicPlayerChrome.dp(context, value)

    fun render() {
        if (!active) return
        val controller = client.controller()
        val item = controller?.currentMediaItem
        if (item == null) {
            trackTitle.setText("Now playing")
            trackArtist.setText("")
            trackArtist.visibility = View.GONE
            trackAlbum.setText("")
            trackAlbum.visibility = View.GONE
            updateArtwork(null)
        } else {
            val title = item.mediaMetadata.title
            val artist = item.mediaMetadata.artist
            val album = metadataNavigation.albumFor(item)
            trackTitle.setText(if (title.isNullOrEmpty()) "Now playing" else title)
            trackArtist.setText(artist ?: "")
            trackArtist.visibility =
                if (artist.isNullOrEmpty()) View.GONE else View.VISIBLE
            trackAlbum.setText(album ?: "")
            trackAlbum.visibility =
                if (album.isNullOrEmpty()) View.GONE else View.VISIBLE
            var artworkSource: Uri? = item.localConfiguration?.uri
            if (artworkSource == null && item.mediaId.startsWith("content://")) {
                artworkSource = Uri.parse(item.mediaId)
            }
            updateArtwork(artworkSource)
        }

        val queueSize = controller?.mediaItemCount ?: 0
        val index = if (controller == null || item == null) C.INDEX_UNSET
            else controller.currentMediaItemIndex
        val position = if (index >= 0 && index < queueSize) index + 1 else 0
        queuePosition.setText("$position of $queueSize")
        val playing = controller?.playWhenReady == true
        transportButtons[1].setImageDrawable(
            MusicPlayerChrome.transportIcon(context, 1, playing)
        )
        transportButtons[1].contentDescription = if (playing) "Pause" else "Play"
        renderProgress()
        renderFocus()
    }

    private fun updateArtwork(uri: Uri?) {
        val key = uri?.toString() ?: ""
        if (key == shownArtworkUri) return
        shownArtworkUri = key
        artworkImage.setImageResource(R.drawable.bbos6_album_no_art)
        artworkLoader.load(uri) { bitmap ->
            if (!active || key != shownArtworkUri) return@load
            if (bitmap != null) artworkImage.setImageBitmap(bitmap)
        }
    }

    private fun renderProgress() {
        if (!active || seekDragging) return
        val controller = client.controller()
        if (controller == null) {
            durationMs = 0L
            elapsed.setText("0:00")
            remaining.setText("--:--")
            seekBar.progress = 0
            seekRail.updateProgress(0)
            seekBar.isEnabled = false
            seekRail.alpha = seekBar.alpha
            return
        }
        val duration = controller.duration
        durationMs = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
        val position = maxOf(0L, controller.currentPosition)
        elapsed.setText(MediaSeekPolicy.formatTime(position))
        remaining.setText(MediaSeekPolicy.formatRemainingTime(durationMs, position))
        seekBar.progress = MediaSeekPolicy.toProgress(position, durationMs)
        seekRail.updateProgress(seekBar.progress)
        val canSeek = durationMs > 0L && controller.isCurrentMediaItemSeekable
        seekBar.isEnabled = canSeek
        seekBar.alpha = if (canSeek) 1f else 0.4f
        seekRail.alpha = if (canSeek) 1f else 0.4f
        if (!canSeek && seekEditing) {
            seekEditing = false
            renderFocus()
        }
    }

    private fun renderFocus() {
        if (!zoneAvailable(zone)) zone = ZONE_SEEK
        val focused = hasFocus()
        trackArtist.setMetadataFocused(focused && zone == ZONE_ARTIST)
        trackAlbum.setMetadataFocused(focused && zone == ZONE_ALBUM)
        val controller = client.controller()
        val shuffleAvailable = controller?.isCommandAvailable(
            Player.COMMAND_SET_SHUFFLE_MODE
        ) == true
        val shuffle = shuffleAvailable && controller?.shuffleModeEnabled == true
        val shuffleFocused = shuffleAvailable && focused &&
            zone == ZONE_MODE && selectedMode == 1
        modeShuffle.setImageResource(
            if (!shuffleAvailable) R.drawable.bbos6_shuffle_unavailable
            else if (shuffle) R.drawable.bbos6_shuffle_on
            else R.drawable.bbos6_shuffle_off
        )
        modeShuffle.contentDescription =
            if (!shuffleAvailable) "Shuffle unavailable"
            else if (shuffle) "Shuffle on" else "Shuffle off"
        modeShuffle.isEnabled = shuffleAvailable
        modeShuffleFace.setHighlighted(shuffleFocused)
        modeShuffle.alpha = 1f

        val repeatAvailable = controller?.isCommandAvailable(
            Player.COMMAND_SET_REPEAT_MODE
        ) == true
        val repeatMode = if (repeatAvailable)
            controller?.repeatMode ?: Player.REPEAT_MODE_OFF
        else Player.REPEAT_MODE_OFF
        modeRepeat.setImageResource(
            if (!repeatAvailable) R.drawable.bbos6_repeat_unavailable
            else if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.bbos6_repeat_one
            else if (repeatMode == Player.REPEAT_MODE_ALL) R.drawable.bbos6_repeat_all
            else R.drawable.bbos6_repeat_off
        )
        modeRepeat.contentDescription =
            if (!repeatAvailable) "Repeat unavailable"
            else if (repeatMode == Player.REPEAT_MODE_ONE) "Repeat one"
            else if (repeatMode == Player.REPEAT_MODE_ALL) "Repeat all"
            else "Repeat off"
        modeRepeat.isEnabled = repeatAvailable
        val repeatFocused = repeatAvailable && focused &&
            zone == ZONE_MODE && selectedMode == 0
        modeRepeatFace.setHighlighted(repeatFocused)
        modeRepeat.alpha = 1f
        seekBar.thumb = MusicPlayerChrome.seekThumb(
            context, focused && zone == ZONE_SEEK,
            focused && zone == ZONE_SEEK && seekEditing
        )
        for (i in transportButtons.indices) {
            val selected = focused && zone == ZONE_TRANSPORT && selectedButton == i
            transportFaces[i].setHighlighted(selected)
            val enabled = when (i) {
                0 -> controller?.hasPreviousMediaItem() == true
                3 -> controller?.hasNextMediaItem() == true
                else -> controller?.currentMediaItem != null
            }
            transportButtons[i].alpha = if (enabled) 1f else 0.35f
        }
    }

    private fun zoneAvailable(candidate: Int): Boolean = when (candidate) {
        ZONE_ARTIST -> trackArtist.visibility == View.VISIBLE
        ZONE_ALBUM -> trackAlbum.visibility == View.VISIBLE
        else -> candidate in ZONE_MODE..ZONE_TRANSPORT
    }

    private fun activateMetadata(targetZone: Int) {
        if (!active || !zoneAvailable(targetZone)) return
        zone = targetZone
        seekEditing = false
        requestFocus()
        renderFocus()
        if (targetZone == ZONE_ARTIST) metadataNavigation.openArtist()
        else metadataNavigation.openAlbum()
    }

    fun directional(code: Int) {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (seekEditing) return
                val delta = if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                var next = zone + delta
                while (next in ZONE_MODE..ZONE_TRANSPORT) {
                    if (zoneAvailable(next)) {
                        zone = next
                        break
                    }
                    next += delta
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val step = if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                when (zone) {
                    ZONE_SEEK -> {
                        if (seekEditing) {
                            val controller = client.controller()
                            if (controller != null && durationMs > 0L) {
                                client.seek(MediaSeekPolicy.seekStep(
                                    controller.currentPosition, durationMs, step
                                ))
                            }
                        }
                    }
                    ZONE_MODE -> selectedMode = (selectedMode + step).coerceIn(0, 1)
                    ZONE_TRANSPORT -> selectedButton =
                        (selectedButton + step).coerceIn(0, transportButtons.lastIndex)
                }
            }
        }
        renderFocus()
    }

    fun activate() {
        if (zone == ZONE_ARTIST || zone == ZONE_ALBUM) {
            activateMetadata(zone)
            return
        }
        when (zone) {
            ZONE_MODE -> {
                val controller = client.controller()
                if (selectedMode == 0) {
                    if (controller?.isCommandAvailable(
                            Player.COMMAND_SET_REPEAT_MODE
                        ) == true) client.toggleRepeat()
                } else if (controller?.isCommandAvailable(
                        Player.COMMAND_SET_SHUFFLE_MODE
                    ) == true) client.toggleShuffle()
            }
            ZONE_SEEK -> {
                val controller = client.controller()
                val canSeek = durationMs > 0L &&
                    controller?.isCurrentMediaItemSeekable == true
                seekEditing = MediaSeekPolicy.toggleTrackpadEditing(
                    true, seekEditing, canSeek
                )
            }
            else -> when (selectedButton) {
                0 -> client.previous()
                1 -> client.playPause()
                2 -> client.stop()
                else -> client.next()
            }
        }
        render()
    }

    override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        renderFocus()
    }

    fun dispose() {
        active = false
        ticker.removeCallbacks(tick)
        artworkLoader.close()
        artworkImage.clear()
    }
}
