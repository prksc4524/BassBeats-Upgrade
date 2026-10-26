package com.bassbeats.music

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*

private data class Song(val title: String, val artist: String, val uri: Uri)

class MainActivity : Activity() {
    private val blue = Color.rgb(0, 190, 255)
    private val bg = Color.rgb(5, 9, 17)
    private val panel = Color.rgb(15, 23, 38)
    private val white = Color.rgb(239, 247, 255)
    private val muted = Color.rgb(142, 161, 184)
    private val songs = mutableListOf<Song>()
    private var player: MediaPlayer? = null
    private var current = -1
    private var bass: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var seeking = false
    private var bassEnabled = true
    private var bassStrength = 700
    private val eqValues = IntArray(10) { 50 }
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var playButton: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var visualBars: LinearLayout
    private lateinit var songList: LinearLayout
    private lateinit var musicHeader: LinearLayout
    private lateinit var musicChevron: TextView
    private var musicExpanded = false
    private var currentTab = "player"
    private val bandLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        volumeControlStream = AudioManager.STREAM_MUSIC
        buildShell()
        showTab("player")
        if (hasAudioPermission()) scanSongs() else requestPermissions(
            arrayOf(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO), 100
        )
    }

    private fun hasAudioPermission(): Boolean {
        val libraryGranted = if (Build.VERSION.SDK_INT >= 33)
            checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val captureGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return libraryGranted && captureGranted
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) scanSongs()
        else Toast.makeText(this, "Allow audio permission to find songs", Toast.LENGTH_LONG).show()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = TextView(this).apply {
            text = "BASS BEATS"; textSize = 25f; setTextColor(blue); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD); letterSpacing = .12f
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(20)); setBackgroundColor(bg)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7)); background = rounded(Color.rgb(8, 15, 26), 20, false)
        }
        nav.addView(navItem("♫", "Player") { showTab("player") }, LinearLayout.LayoutParams(0, dp(56), 1f))
        nav.addView(navItem("☷", "Music") { showTab("music") }, LinearLayout.LayoutParams(0, dp(56), 1f))
        nav.addView(navItem("☷", "Equalizer") { showTab("equalizer") }, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(4), dp(12), dp(6)) })
        setContentView(root)
    }

    private fun navItem(symbol: String, name: String, action: () -> Unit): TextView = TextView(this).apply {
        text = "$symbol\n$name"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(muted)
        setOnClickListener { action() }
    }

    private fun showTab(tab: String) {
        currentTab = tab
        content.removeAllViews()
        when (tab) {
            "player" -> buildPlayerScreen()
            "music" -> buildMusicScreen()
            else -> buildEqualizerScreen()
        }
    }

    private fun buildPlayerScreen() {
        val playerCard = card()
        content.addView(playerCard, matchWrap())
        visualBars = LinearLayout(this).apply {
            gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(10), dp(4), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(7, 34, 55), Color.rgb(7, 14, 25))).apply {
                cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.rgb(0, 92, 137))
            }
        }
        repeat(32) { i ->
            val bar = View(this).apply { background = rounded(blue, 4, false) }
            visualBars.addView(bar, LinearLayout.LayoutParams(dp(4), dp(8 + (i * 13 % 40))).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        playerCard.addView(visualBars, LinearLayout.LayoutParams(-1, dp(175)))
        titleText = TextView(this).apply {
            text = "Choose a song"; textSize = 21f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(10), 0, dp(2))
        }
        playerCard.addView(titleText, matchWrap())
        artistText = TextView(this).apply {
            text = "Your local music library"; textSize = 14f; setTextColor(muted); gravity = Gravity.CENTER; maxLines = 1
        }
        playerCard.addView(artistText, matchWrap())
        seek = SeekBar(this).apply { progressTintList = tint(blue); thumbTintList = tint(blue) }
        playerCard.addView(seek, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        timeText = TextView(this).apply { text = "00:00                         00:00"; textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER }
        playerCard.addView(timeText, matchWrap())
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(4)) }
        controls.addView(controlButton("|◀") { previous() }, LinearLayout.LayoutParams(dp(66), dp(56)))
        playButton = controlButton("▶") { togglePlay() }.apply {
            textSize = 26f; setTextColor(Color.BLACK); background = rounded(blue, 26, false)
        }
        controls.addView(playButton, LinearLayout.LayoutParams(dp(88), dp(66)).apply { marginStart = dp(14); marginEnd = dp(14) })
        controls.addView(controlButton("▶|") { next() }, LinearLayout.LayoutParams(dp(66), dp(56)))
        playerCard.addView(controls, matchWrap())
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) updateTime(progress, player?.duration ?: 0) }
            override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) { try { player?.seekTo(s?.progress ?: 0) } catch (_: Exception) {}; seeking = false }
        })
        content.addView(space(20))
        val libraryCard = card().apply { setPadding(dp(14), dp(14), dp(14), dp(14)) }
        musicHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true }
        musicHeader.addView(TextView(this).apply { text = "♫"; textSize = 23f; setTextColor(blue); gravity = Gravity.CENTER; background = rounded(Color.rgb(8, 43, 65), 12, false) }, LinearLayout.LayoutParams(dp(46), dp(46)))
        musicHeader.addView(TextView(this).apply { text = "YOUR MUSIC"; textSize = 15f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        musicChevron = TextView(this).apply { text = "⌄"; textSize = 26f; setTextColor(muted) }
        musicHeader.addView(musicChevron)
        musicHeader.setOnClickListener { musicExpanded = !musicExpanded; updateMusicExpansion(libraryCard) }
        libraryCard.addView(musicHeader, matchWrap())
        songList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        libraryCard.addView(songList, matchWrap())
        content.addView(libraryCard, matchWrap())
        content.addView(space(12))
        val eqShortcut = TextView(this).apply {
            text = "☷     EQUALIZER                                      ›"; textSize = 15f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, dp(16), 0); background = rounded(panel, 20, true); isClickable = true
            setOnClickListener { showTab("equalizer") }
        }
        content.addView(eqShortcut, LinearLayout.LayoutParams(-1, dp(70)))
        renderSongs()
    }

    private fun updateMusicExpansion(card: LinearLayout) {
        songList.visibility = if (musicExpanded) View.VISIBLE else View.GONE
        musicChevron.text = if (musicExpanded) "⌃" else "⌄"
    }

    private fun buildMusicScreen() {
        content.addView(sectionTitle("YOUR MUSIC"))
        val search = EditText(this).apply {
            hint = "Search songs..."; setHintTextColor(muted); setTextColor(white); textSize = 15f
            setSingleLine(true); setPadding(dp(14), dp(10), dp(14), dp(10)); background = rounded(panel, 16, true)
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(52)))
        content.addView(space(12))
        songList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(songList, matchWrap())
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderSongs(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        renderSongs()
    }

    private fun buildEqualizerScreen() {
        val panelCard = card()
        content.addView(panelCard, matchWrap())
        panelCard.addView(label("EQUALIZER"), matchWrap())
        panelCard.addView(label("PRESETS"), matchWrap())
        val presets = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Custom", "Normal", "Bass Boost", "Rock", "Pop", "Jazz", "Classical", "Dance", "Vocal"))
        }
        panelCard.addView(presets, matchWrap())
        panelCard.addView(space(12))
        val bands = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) }
        panelCard.addView(bands, LinearLayout.LayoutParams(-1, dp(270)))
        for (i in bandLabels.indices) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            col.addView(TextView(this).apply { text = bandLabels[i]; textSize = 9f; setTextColor(muted); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(24)))
            val slider = SeekBar(this).apply {
                max = 100; progress = eqValues[i]; rotation = -90f
                progressTintList = tint(blue); thumbTintList = tint(blue)
            }
            col.addView(slider, LinearLayout.LayoutParams(dp(190), dp(42)))
            col.addView(TextView(this).apply { text = if (eqValues[i] == 50) "0" else if (eqValues[i] > 50) "+${(eqValues[i]-50)/5}" else "-${(50-eqValues[i])/5}"; textSize = 10f; setTextColor(muted); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(20)))
            bands.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    eqValues[i] = p
                    if (user) applyEqBand(i, p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        presets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) return
                val target = when (position) { 2 -> intArrayOf(85,80,72,65,58,53,50,48,45,43); 3 -> intArrayOf(70,65,55,45,60,70,75,70,65,60); 4 -> intArrayOf(45,50,60,70,75,70,60,55,50,45); 5 -> intArrayOf(55,60,65,50,45,50,60,70,70,65); 6 -> intArrayOf(40,45,50,55,60,60,55,50,45,40); 7 -> intArrayOf(80,75,60,50,60,70,75,75,70,65); 8 -> intArrayOf(40,45,55,65,70,65,60,55,50,45); else -> IntArray(10) { 50 } }
                for (i in target.indices) { eqValues[i] = target[i]; if (bands.childCount > i) { val col = bands.getChildAt(i) as LinearLayout; (col.getChildAt(1) as SeekBar).progress = target[i] } }
            }
        }
        panelCard.addView(space(10))
        val bassRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bassRow.addView(label("BASS BOOST"), LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply { text = ""; isChecked = bassEnabled; buttonTintList = tint(blue) }
        bassRow.addView(toggle)
        panelCard.addView(bassRow, matchWrap())
        val bassValue = TextView(this).apply { text = "Intensity     ${bassStrength / 10}%"; textSize = 12f; setTextColor(muted) }
        panelCard.addView(bassValue, matchWrap())
        val bassSeek = SeekBar(this).apply { max = 1000; progress = bassStrength; progressTintList = tint(blue); thumbTintList = tint(blue) }
        panelCard.addView(bassSeek, matchWrap())
        toggle.setOnCheckedChangeListener { _, checked -> bassEnabled = checked; try { bass?.enabled = checked } catch (_: Exception) {} }
        bassSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { bassStrength = p; bassValue.text = "Intensity     ${p / 10}%"; if (user) applyBass() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        content.addView(space(10))
        content.addView(TextView(this).apply {
            text = "Effects apply while a song is playing. Available bands and effect strength depend on the Android device."; textSize = 11f; setTextColor(muted)
        }, matchWrap())
    }

    private fun scanSongs() {
        songs.clear()
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        try {
            contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c: Cursor ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    songs.add(Song(c.getString(titleCol) ?: "Unknown title", c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist", ContentUris.withAppendedId(collection, id)))
                }
            }
        } catch (_: Exception) {}
        renderSongs()
    }

    private fun renderSongs(filter: String = "") {
        if (!::songList.isInitialized) return
        songList.removeAllViews()
        val visible = songs.withIndex().filter { it.value.title.contains(filter, true) || it.value.artist.contains(filter, true) }
        if (visible.isEmpty()) {
            songList.addView(TextView(this).apply { text = if (songs.isEmpty()) "No music found. Add MP3 files and allow audio access." else "No matching songs"; setTextColor(muted); setPadding(0, dp(16), 0, dp(16)) })
            return
        }
        visible.forEach { indexed ->
            val index = indexed.index; val song = indexed.value
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)); background = rounded(panel, 16, true); isClickable = true; isFocusable = true }
            row.addView(TextView(this).apply { text = "♫"; textSize = 23f; setTextColor(blue); gravity = Gravity.CENTER; background = rounded(Color.rgb(8, 43, 65), 11, false) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(TextView(this).apply { text = song.title; textSize = 14f; setTextColor(white); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            info.addView(TextView(this).apply { text = song.artist; textSize = 11f; setTextColor(muted); maxLines = 1 })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); row.setOnClickListener { play(index); showTab("player") }
            songList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun play(index: Int) {
        if (index !in songs.indices) return
        releaseEffects(); try { player?.release() } catch (_: Exception) {}; player = null; current = index
        try {
            player = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC); setDataSource(this@MainActivity, songs[index].uri)
                setOnPreparedListener { mp ->
                    if (!::titleText.isInitialized) showTab("player")
                    titleText.text = songs[index].title; artistText.text = songs[index].artist
                    seek.max = mp.duration; updateTime(0, mp.duration); initEffects(); mp.start(); playButton.text = "Ⅱ"; startTicker()
                }
                setOnCompletionListener { next() }; setOnErrorListener { _, _, _ -> Toast.makeText(this@MainActivity, "Could not play this song", Toast.LENGTH_SHORT).show(); true }
                prepareAsync()
            }
        } catch (_: Exception) { Toast.makeText(this, "Could not play this song", Toast.LENGTH_SHORT).show() }
    }

    private fun togglePlay() {
        val p = player
        if (p == null) { if (songs.isNotEmpty()) play(if (current >= 0) current else 0); return }
        try { if (p.isPlaying) { p.pause(); playButton.text = "▶" } else { p.start(); playButton.text = "Ⅱ"; startTicker() } } catch (_: Exception) {}
    }
    private fun next() { if (songs.isNotEmpty()) play(if (current + 1 >= songs.size) 0 else current + 1) }
    private fun previous() {
        val p = player; val restart = try { p != null && p.currentPosition > 3000 } catch (_: Exception) { false }
        if (restart) { try { p?.seekTo(0); return } catch (_: Exception) {} }
        if (songs.isNotEmpty()) play(if (current <= 0) songs.lastIndex else current - 1)
    }

    private fun initEffects() {
        val p = player ?: return
        try { if (bass == null) bass = BassBoost(0, p.audioSessionId).apply { setStrength(bassStrength.toShort()); enabled = bassEnabled } } catch (_: Exception) {}
        try { if (equalizer == null) equalizer = Equalizer(0, p.audioSessionId).apply { enabled = true } } catch (_: Exception) {}
        try {
            if (visualizer == null) visualizer = Visualizer(p.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        val data = fft ?: return
                        runOnUiThread {
                            if (!::visualBars.isInitialized || currentTab != "player") return@runOnUiThread
                            val count = visualBars.childCount
                            for (i in 0 until count) {
                                val bin = (1 + i * (data.size / 2 - 2) / maxOf(1, count)).coerceIn(1, data.size - 2)
                                val real = data[bin].toInt()
                                val imag = data[bin + 1].toInt()
                                val magnitude = kotlin.math.sqrt((real * real + imag * imag).toDouble()).toInt().coerceIn(0, 255)
                                val h = 8 + magnitude * 115 / 255
                                val child = visualBars.getChildAt(i)
                                child.layoutParams = child.layoutParams.apply { height = dp(h) }
                                child.requestLayout()
                            }
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (_: Exception) {}
    }

    private fun applyBass() { try { if (bass == null) initEffects(); bass?.setStrength(bassStrength.toShort()); bass?.enabled = bassEnabled } catch (_: Exception) {} }
    private fun applyEqBand(index: Int, value: Int) {
        try {
            if (equalizer == null) initEffects()
            val eq = equalizer ?: return
            val actualCount = eq.numberOfBands.toInt(); if (actualCount <= 0) return
            val actualBand = ((index.toFloat() / 9f) * (actualCount - 1)).toInt().coerceIn(0, actualCount - 1)
            val range = eq.bandLevelRange; val level = range[0] + (range[1] - range[0]) * value / 100
            eq.setBandLevel(actualBand.toShort(), level.toShort())
        } catch (_: Exception) {}
    }
    private fun startTicker() {
        if (!::seek.isInitialized) return
        seek.post(object : Runnable {
            override fun run() {
                val p = player ?: return
                try { if (!seeking && p.isPlaying) { seek.progress = p.currentPosition; updateTime(p.currentPosition, p.duration) }; if (p.isPlaying) seek.postDelayed(this, 400) } catch (_: Exception) {}
            }
        })
    }
    private fun updateTime(position: Int, duration: Int) { if (::timeText.isInitialized) timeText.text = "${fmt(position)}                         ${fmt(duration)}" }
    private fun releaseEffects() { try { visualizer?.release() } catch (_: Exception) {}; try { bass?.release() } catch (_: Exception) {}; try { equalizer?.release() } catch (_: Exception) {}; visualizer = null; bass = null; equalizer = null }

    private fun controlButton(textLabel: String, action: () -> Unit) = TextView(this).apply {
        text = textLabel; textSize = 22f; gravity = Gravity.CENTER; setTextColor(blue); background = rounded(Color.TRANSPARENT, 24, false); isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    private fun label(value: String) = TextView(this).apply { text = value; setTextColor(white); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(6)) }
    private fun sectionTitle(value: String) = TextView(this).apply { text = value; textSize = 15f; letterSpacing = .08f; setTextColor(blue); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(12)) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(panel, 24, true) }
    private fun rounded(color: Int, radius: Int, border: Boolean) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (border) setStroke(dp(1), Color.rgb(31, 53, 75)) }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun tint(color: Int) = android.content.res.ColorStateList.valueOf(color)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun fmt(ms: Int) = "%02d:%02d".format(ms.coerceAtLeast(0) / 60000, (ms.coerceAtLeast(0) / 1000) % 60)

    override fun onDestroy() { releaseEffects(); try { player?.release() } catch (_: Exception) {}; player = null; super.onDestroy() }
}
